(ns ovation-rest.entity
  (:import (us.physion.ovation.domain URIs))
  (:require [clojure.walk :refer [stringify-keys]]
            [ovation-rest.util :refer :all]))

(defn- api-key
  "Extracts the API key from request query parameters"
  [request]
  ("api-key" (:query-params request)))

;(defn json-to-map [json]
;  (->
;    (new com.fasterxml.jackson.databind.ObjectMapper)
;    (.registerModule (com.fasterxml.jackson.datatype.guava.GuavaModule.))
;    (.registerModule (com.fasterxml.jackson.datatype.joda.JodaModule.))
;    (.readValue json java.util.Map)
;    )
;  )


(defn get-entity
  "Gets a single entity by ID (uuid)"
  [api-key uuid host-url]
  (into-seq
    (seq [(-> (ctx api-key) (.getObjectWithUuid (parse-uuid uuid)))])
    host-url))

;(defn get-entity-rel
;  "Helper to return the json array of the target of an entity relation by entity [UU]ID and relation name"
;  [uuid rel api_key]
;  (let [
;         entity (-> (ctx api_key) (. getObjectWithUuid (parse-uuid uuid)))
;         relation (-> entity (. getEntities rel))
;         ]
;
;    (into-seq (seq relation))
;    )
;  )


(defn create-multimap [m]
  (us.physion.ovation.util.MultimapUtils/createMultimap m))

(defn create-entity [api-key new-dto host-url]
  "Creates a new Entity from a DTO map"
  (let [entity (-> (ctx api-key)
                   (.insertEntity
                     (stringify-keys (update-in new-dto [:links] create-multimap))))]

    (into-seq (seq [entity]) host-url)))


(defn update-entity [api-key id dto host-url]
  (let [entity     (-> (ctx api-key) (.getObjectWithUUID (parse-uuid id)))]
    (.update entity (stringify-keys (update-in dto [:links] create-multimap)))
    (into-seq [entity] host-url)
    ))

(defn delete-entity [api_key id]
  (let [entity (-> (ctx api_key) (. getObjectWithUuid (parse-uuid id)))
        trash_resp (-> (ctx api_key) (. trash entity) (.get))]

    {:success (not (empty? trash_resp))}))

(defn- ^{:testable true} get-projects [ctx]
  (.getProjects ctx))

(defn index-resource [api-key resource host-url]
  (let [resources (case resource
                    "projects" (get-projects (ctx api-key))
                    "sources" (-> (ctx api-key) (.getTopLevelSources))
                    "protocols" (-> (ctx api-key) (.getProtocols))
                    )]

    (into-seq resources host-url)))

(defn- get-view-results [ctx uri]
  :unfinished)

(defn get-view [api-key full-url host-url]
  (clojure.pprint/pprint [full-url host-url])
  (into-seq (get-view-results (ctx api-key) (to-ovation-uri full-url host-url)) host-url))

