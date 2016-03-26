(ns relational-mapper-with-uncommon-keys-test
  (:require [clojure.test :refer :all]
            [relational-mapper :refer :all]
            [relational-mapper.test-utils.migrate :refer [db-migrate]]
            [relational-mapper.test-utils.seed-with-uncommon-keys :refer [db-seed]]
            [relational-mapper.data-model :as data-model]))

(def associations {:person {:post {:type :has-many :inverse-of :author}
                            :attachment {:type :has-many :through :post}}
                   :post {:author {:type :belongs-to :model :person}
                          :attachment {:type :has-many}}
                   :attachment {:author {:type :belongs-to :through :post}
                                :post {:type :belongs-to}}})

(def fields {:person {:first_name {:type :string}
                      :last_name {:type :string}}
             :post {:title {:type :string}
                    :body {:type :text}
                    :author_key {:type :integer}}
             :attachment {:name {:type :string}
                          :post_key {:type :integer}}})

(def db-config
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (str "//" "localhost" ":" 5432 "/" "relational_mapper_test")
   :user "postgres"
   :password "postgres"})

(def db-initial-state
  {:config db-config
   :db-created false
   :db-migrated false
   :db-seeded false
   :data-model {}})

(defn foreign-key-generator [model]
  (str model "_key"))

(defn create-model-with-uncommon-keys [db-state]
  (-> db-state
    (data-model/set-associations associations {:foreign-key-format foreign-key-generator})
    (data-model/set-fields fields)))

(defn create-database [db-state]
  "I assume the database is already created:
   In psql run:
   CREATE ROLE postgres LOGIN;
   then
   [backslash]password postgres
   and
   CREATE database relational_mapper_test"
  (assoc db-state :db-created true))

(defn migrate-database [db-state]
  (db-migrate db-state)
  (assoc db-state :db-migrated true))

(defn seed-database-with-uncommon-keys [db-state]
  (db-seed db-state)
  (assoc db-state :db-seeded true))

(defn setup-database [db-state]
  (-> db-state
      create-model-with-uncommon-keys
      create-database
      migrate-database
      seed-database-with-uncommon-keys))

(def db-state (setup-database db-initial-state))

(deftest find-all-with-has-many-association
  (let [response (find-all db-state :person #{:post} [[:= :person.first-name "John"]])]
    (testing "returns record with associated records as an array of hashes"
      (and (is (= (-> response first :first_name) "John"))
           (is (= (-> response first :post first :title) "Post 1"))
           (is (= (-> response first :post second :title) "Post 2"))))))

(deftest find-all-with-belongs-to-through-association
  (let [response (find-all db-state :attachment #{:author} [[:= :attachment.name "Attachment 1 (Post 1, John)"]])]
    (testing "returns record with associated record as a hash"
      (and (is (= (-> response first :name) "Attachment 1 (Post 1, John)"))
           (is (= (-> response first :author :first_name) "John"))))
    (testing "includes intermediate association record as a hash"
      (is (= (-> response first :post :title) "Post 1")))))
