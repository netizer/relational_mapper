(ns relational-mapper.database
  (:require [clojure.string :as string]
            [clojure.java.jdbc :as j]
            [honeysql.core :as hsql]
            [honeysql.helpers :as h]))

(defn db-query [db-state sql]
  (let [formatted-sql (hsql/format sql)]
    (j/query (:config db-state) formatted-sql)))

(defn db-insert [db-state table params]
  (j/insert! (:config db-state) table params))

(defn db-execute [db-state sql]
  (let [formatted-sql (hsql/format sql)]
    (j/execute! (:config db-state) formatted-sql)))

(defn sql-all [table condition]
  (-> (h/select :*) (h/from table) (h/where condition)))

(defn count-by [table field value]
  (let [query (-> (h/select [:%count.* :cnt]) (h/from table) (h/where [:= field value]))
        result (db-query query)]
  (:cnt (first result))))

(defn exist? [table conditions]
  (let [query (reduce #(h/merge-where %1 %2) (-> (h/select :id) (h/from table)) conditions)
        result (db-query query)]
    (seq result)))

(defn create-and-return-result [db-state table params]
  (first (db-insert db-state table params)))

(defn create [db-state table params]
  (:id (create-and-return-result db-state table params)))

(defn update [table id params]
  (first (db-execute (-> (h/update table) (h/sset params) (h/where [:= :id id])))))

(defn delete [table conditions]
  (let [query (reduce #(h/merge-where %1 %2) (h/delete-from table) conditions)]
    (db-execute query)))

(defn delete-all [db-state tables]
  (doall (map #(db-execute db-state (h/delete-from %)) tables)))
