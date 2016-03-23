(ns relational-mapper.data-model)

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
  (map db-data-type-field (-> fields resource :fields not-virtual-fields)))

(defn association-modifier [association]
  association)

(defn copy-value [v]
  v)

;; runs specific-modifier for each value of the hash for witch
;; this function was run
(defn hash-modifier [specific-modifier new-key db-state k v]
  (assoc-in db-state [:data-model k new-key] (specific-modifier v)))

(defn update-for-model [db-state data new-key modifier]
  (reduce-kv (partial modifier new-key) db-state data))

(defn set-fields [db-state fields]
  (update-for-model db-state fields :fields (partial hash-modifier copy-value)))

(defn set-associations [db-state associations]
  (update-for-model db-state associations :associations (partial hash-modifier association-modifier)))
