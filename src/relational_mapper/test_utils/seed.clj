(ns relational-mapper.test-utils.seed
  (:require [clojure.pprint :as pprint]
            [relational-mapper.database :as database]
            [relational-mapper.data-model :refer :all]))

(defn sql-date [year month day]
  (java.sql.Date. (- year 1900) month day))

(defn users-data [first-name]
  {:first_name first-name
   :last_name "Test" })

(defn posts-data [title, user-id]
  {:title title
   :body "body"
   :authors_id user-id})

(defn attachments-data [name, post-id]
  {:name name
   :posts_id post-id})

(defn files-data [name, attachment-id]
  {:name name
   :attachments_id attachment-id})

(defn db-add-data [db-state]
  (let [user1-id (database/create db-state :users
                                  (users-data "John"))
        user2-id (database/create db-state :users
                                  (users-data "Maria"))
        post1-id (database/create db-state :posts
                                  (posts-data "Post 1" user1-id))
        post2-id (database/create db-state :posts
                                  (posts-data "Post 2" user1-id))
        post3-id (database/create db-state :posts
                                  (posts-data "Post 3" user2-id))
        attachment1-id (database/create db-state :attachments
                                        (attachments-data "Attachment 1 (Post 1, John)" post1-id))
        attachment2-id (database/create db-state :attachments
                                        (attachments-data "Attachment 2 (Post 3, Maria)" post3-id))]
    (database/create db-state :files
                     (files-data "File 1 (Attachment 1)", attachment1-id))
    (database/create db-state :files
                     (files-data "File 2 (Attachment 2)", attachment2-id))))

(defn db-seed [db-state]
  (database/delete-all db-state (doall (keys (:data-model db-state))))
  (db-add-data db-state))
