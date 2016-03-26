(ns relational-mapper.test-utils.seed-with-uncommon-keys
  (:require [clojure.pprint :as pprint]
            [relational-mapper.database :as database]
            [relational-mapper.data-model :refer :all]))

(defn users-data [first-name]
  {:first_name first-name
   :last_name "Test" })

(defn posts-data [title, user-id]
  {:title title
   :body "body"
   :author_key user-id})

(defn attachments-data [name, post-id]
  {:name name
   :post_key post-id})


(defn db-add-data [db-state]
  (let [user1-id (database/create db-state :person
                                  (users-data "John"))
        user2-id (database/create db-state :person
                                  (users-data "Maria"))
        post1-id (database/create db-state :post
                                  (posts-data "Post 1" user1-id))
        post2-id (database/create db-state :post
                                  (posts-data "Post 2" user1-id))
        post3-id (database/create db-state :post
                                  (posts-data "Post 3" user2-id))]
    (database/create db-state :attachment
                     (attachments-data "Attachment 1 (Post 1, John)" post1-id))
    (database/create db-state :attachment
                     (attachments-data "Attachment 2 (Post 3, Maria)" post3-id))))

(defn db-seed [db-state]
  (database/delete-all db-state (doall (keys (:data-model db-state))))
  (db-add-data db-state))
