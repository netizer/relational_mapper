# Relational Mapper

A relational mapper in Clojure. If you're using relational database in Clojure then this library is for you.

## Leiningen Coordinates

[![Clojars Project](https://img.shields.io/clojars/v/netizer/relational-mapper.svg?style=flat-square)](http://clojars.org/netizer/relational-mapper)

## Project health

[![Continuous Integration status](http://img.shields.io/travis/netizer/relational_mapper.svg?style=flat-square)](http://travis-ci.org/netizer/relational_mapper)

[![Dependencies Status](https://jarkeeper.com/netizer/relational_mapper/status.svg)](https://jarkeeper.com/netizer/relational_mapper)

## Usage

Make calls like:
```clojure
(find-all db-state :posts #{:authors :attachments} [:= post.id 1])
```
and get results like:
```clojure
{:posts 
    {:title "Christmas"
     :body "Merry Christmas!"
     :id 1
     :authors_id 10
     :authors {:name "Rudolf" :id 10}
     :attachments [{:name "rudolf.png" :id 100 :posts_id 1}
                   {:name "santa.png" :id 101 :posts_id 1}]
```

to achieve that though you have to first tell 'relational-mapper' what's the structure of your data and how to connect to the database, so the full, working example would look like this:

```clojure
(ns your-project
  (:require [relational-mapper :refer :all]
            [relational-mapper.data-model :as data-model]))

(def associations {:authors {:posts {:type :has-many}
                             :attachments {:type :has-many :through :posts}}
                   :posts {:authors {:type :belongs-to}
                           :attachments {:type :has-many}}
                   :attachments {:authors {:type :belongs-to :through :posts}
                                 :posts {:type :belongs-to}}})

(def db-config {:classname "org.postgresql.Driver"
                :subprotocol "postgresql"
                :subname (str "//" "localhost" ":" 5432 "/" "testdb")
                :user "postgres-user"
                :password "postgres-password"})

(def initial-db-state {:config db-config
                       :data-model {})

(def db-state (data-model/set-associations initial-db-state associations {})

(find-all db-state :posts #{:authors :attachments} [:= post.id 1])
```

## How to define associations

`relational-mapper` uses the same relations naming as 'Ruby On Rails`' 'ActiveRecord', which means:

* `posts` `has-many` `attachments` means that `attachments` has `posts_id` column that refers to `id` column of `posts` table and there might be more than one  attachment for one user (hence in response of `find-all` function, `attachments` is an array of hashes)

* `attachments` `has-one` `file` means that `file` has `attachments_id` column that refers to `id` column in `attachments` table. There can be only one file per attachment though (so `find-all` will return `files` as a hash and not an array)

* `attachments` `belongs-to` `posts` means that `attachments` has `posts_id` column that refers to `id` column in `posts` table (it doesn't say anything about how many attachments per post is allowed)

* `through` relation is used in case of indirect relations, so for example if `users` `has-many` `posts`, and `posts` `has-many` `attachments`, we can make a call to `find-all` that will give us `users` with all the `attachments` of their `posts`.

Have in mind that unlike ActiveRecord, here associations are always plural (`:posts {:authors :belongs-to}` and not `:posts {:author :belongs-to`). The same applies to key names (`users_id`, not `user_id`). This is by design, and it's not likely to change.

Also, unlike 'ActiveRecord', here you can define `through` association referring to `belongs-to` association (the lack of this feature in 'ActiveRecord' is described for example here: https://www.ruby-forum.com/topic/74219)

### Different name of an association than a table name

Sometimes you need to set an association that is named differently than the target table name, for example `posts` may have association `authors` which refers to table `users` (or another case: you need associations: `created_by` and `updated_by`, both referring to the same `users` table). In such case you can use `inverse-of` and `model` in associations hash, e.g.:

```clojure
(def associations {:users {:posts {:type :has-many :inverse-of :authors}}
                   :posts {:authors {:type :belongs-to :model :users}}})
```
### Unusual naming for keys/foreign keys

By default keys of tables are assumed to be called `id` and foreign keys are assumed to match the format `association_id` (so, for example foreign key for table `users` is called `users_id`). If you want to change that, you can define key patterns in last attribute of the function `set-associations`, e.g.

```clojure
(def db-state (data-model/set-associations initial-db-state associations {
    :foreign-key-format #(str % "_key")}))
```

With above settings `relational_mapper` will expect foreign keys to match the pattern `assocation_key`.

## Dependencies

`relational-mapper` uses [Honey SQL](https://github.com/jkk/honeysql) for defining SQL conditions.

## TODO

* test it with MySQL (I used it only with PostgreSQL, it should work with MySQL too, but I haven't checked that),

* improve performance (right now a database call is made per each requested table, while in some cases only one call could be made).

* consider posibility of result having a deeper structure (result might be array of :posts with :authors and :attachments but then each attachment can have data from :files included)

## License

Copyright © 2016 Krzysztof Herod

Distributed under the Eclipse Public License, the same as Clojure.
