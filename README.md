# Relational Mapper

A relational mapper in Clojure. If you're using relational database in Clojure then this library is for you.

## Leiningen Coordinates

[![Clojars Project](http://clojars.org/netizer/relational-mapper/latest-version.svg)](http://clojars.org/netizer/relational-mapper)

## Usage

Make calls like:

    (find-all db-state :posts #{:authors :attachments} [:= post.id 1])

and get results like:

    {:posts {:title "Christmas"
             :body "Merry Christmas!"
             :id 1
             :authors_id 10
             :authors {:name "Rudolf" :id 10}
             :attachments [{:name "rudolf.png" :id 100 :posts_id 1}
                           {:name "santa.png" :id 101 :posts_id 1}]

to achieve that though you have to first tell 'relational-mapper' what's the structure of your data and how to connect to the database, so the full, working example would look like this:

    (def associations {:authors {:posts :has-many
                                 :attachments [:through :posts :has-many]}
                       :posts {:authors :belongs-to
                               :attachments :has-many}
                       :attachments {:authors [:through :posts :belongs-to]
                                     :posts :belongs-to}})

    (def db-config {:classname "org.postgresql.Driver"
                    :subprotocol "postgresql"
                    :subname (str "//" "localhost" ":" 5432 "/" "testdb")
                    :user "postgres-user"
                    :password "postgres-password"})

    (def db-state {:config db-config
                   :associations associations)

    (find-all db-state :posts #{:authors :attachments} [:= post.id 1])

## How to define associations

'relational-mapper' uses the same relations naming as 'Ruby On Rails`' 'ActiveRecord', which means:

* `posts` `has-many` `attachments` means that `attachments` has `posts_id` column that refers to `id` column of `posts` table and there might be more than one  attachment for one user (hence in response of `find-all` function, `attachments` is an array of hashes)

* `attachments` `has-one` `file` means that `file` has `attachments_id` column that refers to `id` column in `attachments` table. There can be only one file per attachment though (so `find-all` will return `files` as a hash and not an array)

* `attachments` `belongs-to` `posts` means that `attachments` has `posts_id` column that refers to `id` column in `posts` table (it doesn't say anything about how many attachments per post is allowed)

* `through` relation is used in case of indirect relations, so for example if `users` `has-many` `posts`, and `posts` `has-many` `attachments`, we can make a call to `find-all` that will give us `users` with all the `attachments` of their `posts`.

Have in mind that unlike ActiveRecord here associations are always plural (`:posts {:authors :belongs-to}` and not `:posts {:author :belongs-to`). The same applies to key names (`users_id`, not `user_id`). This is by design, and it's not likely to change.

Also, unlike 'ActiveRecord' here you can define `through` association referring to `belongs-to` association (the lack of this feature in 'ActiveRecord' is described for example here: https://www.ruby-forum.com/topic/74219)

## Dependencies

'relational-mapper' uses [Honey SQL](https://github.com/jkk/honeysql) for defining SQL conditions.

## TODO

* test it with MySQL (I used it only with PostgreSQL, it should work with MySQL too, but I haven't checked that),

* make is possible to define associations with different names than tables, which would make it possible to use databases with parent-child relations or multiple relations to the same table (e.g. 'author', 'publisher' might both refer to table 'users'),

* improve performance (right now a database call is made per each requested table, while in some cases only one call could be made).

* consider posibility of result having a deeper structure (result might be array of :posts with :authors and :attachments but then each attachment can have data from :files included)

## License

Copyright © 2016 Krzysztof Herod

Distributed under the Eclipse Public License, the same as Clojure.
