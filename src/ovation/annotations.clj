(ns ovation.annotations
  (:require [ovation.version :refer [version-path]]
            [ovation.couch :as couch]
            [ovation.auth :as auth]
            [ovation.core :as core]
            [ovation.links :as links]
            [ovation.constants :as k]))


;; READ
(defn get-annotations
  [auth ids annotation-type]
  (let [db (couch/db auth)
        opts {:keys         (vec (map #(vec [% annotation-type]) ids))
              :include_docs true
              :reduce       false}]

    (couch/get-view db k/ANNOTATIONS-VIEW opts)))


;; WRITE
(defn- make-annotation
  [user-id entity t record]

  (let [entity-id (:_id entity)]
    {:user            user-id
     :entity          entity-id
     :annotation_type t
     :annotation      record
     :type            k/ANNOTATION-TYPE
     :links           {:_collaboration_roots (links/collaboration-roots entity)}}))

(defn create-annotations
  [auth routes ids annotation-type records]
  (let [auth-user-id (auth/authenticated-user-id auth)
        entities (core/get-entities auth ids routes)
        docs (flatten (map (fn [entity]
                            (map #(make-annotation auth-user-id entity annotation-type %) records))
                        entities))]

    (core/create-values auth routes docs)))

(defn delete-annotations
  [auth annotation-ids routes]
  (core/delete-values auth annotation-ids routes))
