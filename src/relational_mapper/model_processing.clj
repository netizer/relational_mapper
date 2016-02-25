(ns relational-mapper.model-processing
  (:require [honeysql.helpers :as h]
            [honeysql.core :as hsql]
            [clojure.java.jdbc :as j]))

(defn- db-data-type [description]
  (case (:type description)
    :string "varchar(255)"
    :text "text"
    :integer "integer"
    :date "date"
    :boolean "boolean"))

(defn has-flag [field-definition flag]
  (flag (:flags field-definition)))

(defn- not-virtual-fields [resource-definition]
  (filterv #(not (has-flag (second %) :form-only)) resource-definition))

(defn db-data-type-field [[field-name field-definition]]
  [field-name (db-data-type field-definition)])

(defn db-data-types [fields resource]
  (map db-data-type-field (-> fields resource not-virtual-fields)))
