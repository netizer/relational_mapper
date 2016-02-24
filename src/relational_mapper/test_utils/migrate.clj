(ns relational-mapper.test-utils.migrate
  (:require [clojure.java.jdbc :as sql]
            [relational-mapper.model-processing :refer :all]))

;; ********************************************************************
;; database manipulation functions
;; ********************************************************************

(defn- sql-count-by [table, field]
  (str "SELECT count(*) FROM " table " WHERE " field " = ?"))

(defn db-exists [db-config db-table db-key value]
  (let [db-table-name (name db-table)
        db-key-name (name db-key)]
    (-> (sql/query db-config [(sql-count-by db-table-name db-key-name) value]) first :count pos?)))

(defn db-drop-table [db-config table]
  (sql/db-do-commands db-config
      (sql/drop-table-ddl table)))

(defn db-create-table [db-config table columns]
  (let [default-columns [[:id "SERIAL" :primary :key]]
        all-columns (concat default-columns columns)
        arguments (concat [table] all-columns)]
    (sql/db-do-commands db-config
      (apply sql/create-table-ddl arguments))))

;; ********************************************************************
;; migration functions
;; ********************************************************************

(defn db-table-check [db-config table]
  (db-exists db-config :information_schema.tables :table_name (name table)))

(defn db-table-reset [db-config table]
  (when (db-table-check db-config table)
    (db-drop-table db-config table)))

(defn db-migrate-table [db-state table-name]
  (db-table-reset (:config db-state) table-name)
  (db-create-table (:config db-state) table-name (into [] (db-data-types (db-state :data-model) table-name))))

(defn db-migrate [db-state data-model]
  (doall (map #(db-migrate-table db-state %) (keys data-model))))

