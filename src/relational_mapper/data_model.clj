(ns relational-mapper.data-model)

(def default-associations-options {:key-format (fn [_resource] "id")
                                   :foreign-key-format (fn [resource] (str resource "_id"))})

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

(defn xor [value1 value2]
  (or (and value1 (not value2)) (and (not value1) value2)))

(defn key-maker [base-name association-data options subject?]
  (let [has-foreign-key (= :belongs-to (:type association-data))
        key-name ((:key-format options) base-name)
        foreign-key-name ((:foreign-key-format options) base-name)]
    (if (xor has-foreign-key subject?) key-name foreign-key-name)))

(defn subject-key-maker [association association-data options]
  (let [base-name (name association)]
    (key-maker base-name association-data options true)))

(defn object-key-maker [resource association-data options]
  (let [inverse-of (:inverse-of association-data)
        base-name (name (or inverse-of resource))]
    (key-maker base-name association-data options false)))

(defn association-modifier [resource options result association association-data]
  (let [inverse-of (:inverse-of association-data)
        has-foreign-key (= :belongs-to (:type association-data))
        object-table (or (:model association-data) association)
        subject-key (subject-key-maker association association-data options)
        object-key (object-key-maker resource association-data options)
        new-association-data {:type (:type association-data)
                              :through (:through association-data)
                              :subject-key subject-key
                              :object-key object-key
                              :object-table object-table}]
    (assoc result association new-association-data)))

(defn associations-modifier [resource associations options]
  (reduce-kv (partial association-modifier resource options) {} associations))

(defn copy-value [_resource v _options]
  v)

;; runs specific-modifier for each value of the hash for which
;; this function was run
(defn hash-modifier [specific-modifier options new-key db-state k v]
  (assoc-in db-state [:data-model k new-key] (specific-modifier k v options)))

(defn update-for-model [db-state data new-key modifier]
  (reduce-kv (partial modifier new-key) db-state data))

(defn set-fields [db-state fields]
  (let [modifier (partial hash-modifier copy-value {})]
    (update-for-model db-state fields :fields modifier)))

(defn set-associations [db-state associations options]
  (let [options (merge default-associations-options options)
        modifier (partial hash-modifier associations-modifier options)]
    (update-for-model db-state associations :associations modifier)))
