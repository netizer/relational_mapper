(ns relational-mapper
  (:require [honeysql.helpers :as h]
            [honeysql.core :as hsql]
            [clojure.java.jdbc :as j]
            [relational-mapper.data-model :refer :all]))

(defn- db-query [db-config sql]
  (let [formatted-sql (hsql/format sql)]
    (j/query db-config formatted-sql)))

(defn- sql-all [table condition]
  (-> (h/select :*) (h/from table) (h/where condition)))

(defn- qualify
  ([relation field]
    (keyword (str (name relation) "." field)))
  ([relation other-relation sufix]
    (keyword (str (name relation) "." (name other-relation) sufix))))

(defn- through-relation? [relation-options]
  (:through relation-options))

(defn- direct-relation? [relation-options]
  (not (through-relation? relation-options)))

(defn- referring-relation? [relation-options]
  (= :belongs-to (:type relation-options)))

(defn- collection-association? [relation-options initial-through-type]
  (or (= :has-many (:type relation-options))
      (= :has-many (:type initial-through-type))))

(defn- through-subject [[-relation-key relation-options]]
  (:through relation-options))

(defn- build-relation-definition [resource [relation relation-options] initial-through-relation]
  (let [initial-through-type (:through initial-through-relation)
        local-key (referring-relation? relation-options)
        resource-key (if local-key (str (name relation) "_id") "id")
        relation-key (if local-key "id" (str (name resource) "_id"))]
    {:relation-options relation-options
     :initial-through-type initial-through-type
     :known {:resource resource
             :id (keyword resource-key)
             :qualified-id (qualify resource resource-key)}
     :being-added {:resource relation
                   :id (keyword relation-key)
                   :qualified-id (qualify relation relation-key)}
     :collection-association? (collection-association? relation-options initial-through-relation)}))

(defn- select-relations-data [all-relations-data relations selector]
  (let [filtered-relations-keys (into {} (filter #(selector (second %)) all-relations-data))]
    (select-keys filtered-relations-keys relations)))

(defn- expand-direct-relations [resource resource-relations-data direct-relations-data through-relations-data initial-through-relation]
  (let [through-subjects (map #(through-subject %) through-relations-data)
        through-subjects-data (into {} (select-keys resource-relations-data through-subjects))
        extended-direct-relations-data (merge direct-relations-data through-subjects-data)]
    (map #(build-relation-definition resource % initial-through-relation) extended-direct-relations-data)))

(declare expand-relations)

(defn- expand-through-relations [db-state initial-through-relation through-relations-data]
  (let [definition-from-through-relations (map #(expand-relations #{(first %)} (:through (second %)) db-state (or initial-through-relation (second %))) through-relations-data)]
    (apply merge definition-from-through-relations)))

(defn- expand-relations
  ([relations resource db-state]
    (expand-relations relations resource db-state nil))
  ([relations resource db-state initial-through-relation]
    (let [data-model (:data-model db-state)
          resource-relations-data (-> data-model resource :associations)
          direct-relations-data (select-relations-data resource-relations-data relations direct-relation?)
          through-relations-data (select-relations-data resource-relations-data relations through-relation?)
          definition-from-direct-relations (expand-direct-relations resource resource-relations-data direct-relations-data through-relations-data initial-through-relation)
          definition-from-through-relations (expand-through-relations db-state initial-through-relation through-relations-data)]
      (concat definition-from-direct-relations definition-from-through-relations))))

(defn- join-with [query expanded-relation]
  (let [table (-> expanded-relation :being-added :resource)
        local-key (-> expanded-relation :known :qualified-id)
        remote-key (-> expanded-relation :being-added :qualified-id)]
    (h/merge-left-join query table [:= local-key remote-key])))

(defn- conditions? [argument]
  (or (empty? argument)
      (and (vector? (first argument))
           (> (count (first argument)) 2))))

(defn- add-conditions [query-init conditions]
  (if (seq conditions) (reduce #(h/merge-where %1 %2) query-init conditions) query-init))

(defn- association-records [collection resource-known]
  (let [association-collections (map resource-known collection)
        association-collection (reduce #(concat %1 (if (vector? %2) %2 [%2])) [] association-collections)]
    (distinct association-collection)))

(defn- fetch-data-by-ids [db-config new-resource new-resource-key ids]
  (if (seq ids)
    (db-query db-config (sql-all new-resource [:in new-resource-key ids]))
    []))

(defn- item-with-association [record expanded-relation data]
  (let [data-container (if (:collection-association? expanded-relation) (into [] data) (first data))
        resource-being-added (-> expanded-relation :being-added :resource)]
    (assoc record resource-being-added data-container)))

(defn- item-with-association-of-resource [record expanded-relation relation-collection]
  (let [resource-known-key-name (-> expanded-relation :known :id)
        resource-being-added-key-name (-> expanded-relation :being-added :id)
        resource-known-key-value (resource-known-key-name record)
        data (filter #(= resource-known-key-value (resource-being-added-key-name %)) relation-collection)]
    (item-with-association record expanded-relation data)))

(defn- item-with-association-of-association [record expanded-relation relation-collection resource-known-collection]
  (let [resource-known-key-name (-> expanded-relation :known :id)
        resource-being-added-key-name (-> expanded-relation :being-added :id)
        resource-known-key-values-set (into #{} (map resource-known-key-name resource-known-collection))
        data (filter #(resource-known-key-values-set (resource-being-added-key-name %)) relation-collection)]
    (item-with-association record expanded-relation data)))

(defn- collection-with-association-of-resource [db-config collection expanded-relation resource resource-known-id resource-being-added qualified-resource-being-added-id]
  (let [ids (map resource-known-id collection)
        results (fetch-data-by-ids db-config resource-being-added qualified-resource-being-added-id ids)]
    (map #(item-with-association-of-resource % expanded-relation results) collection)))

(defn- collection-with-association-of-association [db-config collection expanded-relation resource resource-known-id resource-being-added qualified-resource-being-added-id resource-known]
  (let [known-association-collection (association-records collection resource-known)
        ids (map resource-known-id known-association-collection)
        results (fetch-data-by-ids db-config resource-being-added qualified-resource-being-added-id ids)]
    (map #(item-with-association-of-association % expanded-relation results known-association-collection) collection)))

(defn- collection-with-association [collection expanded-relation db-state resource]
  (let [db-config (:config db-state)
        resource-known (-> expanded-relation :known :resource)
        resource-known-id (-> expanded-relation :known :id)
        resource-being-added (-> expanded-relation :being-added :resource)
        qualified-resource-being-added-id (-> expanded-relation :being-added :qualified-id)]
    (if (= resource resource-known)
      (collection-with-association-of-resource db-config collection expanded-relation resource resource-known-id resource-being-added qualified-resource-being-added-id)
      (collection-with-association-of-association db-config collection expanded-relation resource resource-known-id resource-being-added qualified-resource-being-added-id resource-known))))

(defn- collection-with-associations [collection resource expanded-relations db-state]
  (into [] (reduce #(collection-with-association %1 %2 db-state resource) collection expanded-relations)))

(defn- build-query [resource conditions expanded-relations db-state]
  (let [initial-query (-> (h/select (qualify resource "*")) (h/from resource))
        query-with-order (h/order-by initial-query [(qualify resource "id") :asc])
        query-with-conditions (add-conditions query-with-order conditions)]
    (reduce #(join-with %1 %2) query-with-conditions expanded-relations)))

(defn find-all
  "Finds all records matching conditions
  and includes associated records, e.g.:
  (find-all :posts #{:users :comments} {:posts.id 1})
  will return array of posts with all the fields,
  plus additionaly :users and :comments"
  [db-state resource relations conditions]
  {:pre [(conditions? conditions)]}
  (let [db-config (:config db-state)
        expanded-relations (expand-relations relations resource db-state)
        query (build-query resource conditions expanded-relations db-state)
        collection (db-query (:config db-state) query)
        collection (distinct collection)]
    (collection-with-associations collection resource expanded-relations db-state)))

(defn find-one
  "Works like find-all, but will return
  only the first record"
  [db-state resource relations conditions]
  (first (find-all db-state resource relations conditions)))
