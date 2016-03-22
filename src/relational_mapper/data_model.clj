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

(defn- update-per-model [db-state [key data] category]
  (assoc-in db-state [:data-model key category] data))

(defn- update-per-models [db-state data category]
  (reduce #(update-per-model %1 %2 category) db-state data))

(defn set-fields [db-state fields]
  (update-per-models db-state fields :fields))

(defn set-associations [db-state associations]
  (update-per-models db-state associations :associations))
