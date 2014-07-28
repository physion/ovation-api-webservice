(ns ovation-api-webservice.util
  (:import (java.net URI))
  (:require [clojure.pprint]
            [ovation-api-webservice.context :as context]
  )
)

(defn ctx [api_key]
  (context/cached-context api_key)
)

(defn get-body-from-request [request]
  (slurp (:body request))
)

(defn object-to-json [obj]
  (->
    (new com.fasterxml.jackson.databind.ObjectMapper)
    (.registerModule (com.fasterxml.jackson.datatype.guava.GuavaModule.))
    (.registerModule (com.fasterxml.jackson.datatype.joda.JodaModule.))
    (.configure com.fasterxml.jackson.databind.SerializationFeature/WRITE_DATES_AS_TIMESTAMPS false)
    (.writeValueAsString obj)
  )
)

(defn json-to-object [json]
  (->
    (new com.fasterxml.jackson.databind.ObjectMapper)
    (.registerModule (com.fasterxml.jackson.datatype.guava.GuavaModule.))
    (.registerModule (com.fasterxml.jackson.datatype.joda.JodaModule.))
    (.readValue json java.util.Map)
  )
)

(defn entities-to-json [entity_seq]
  (let [
         array (into-array (map (fn [p] (entity-to-map p)) entity_seq))
       ]
    (object-to-json array)
  )
)

(defn entity-to-map [entity]
  (.toMap entity))

(defn host-from-request [request]
  (let [
         scheme (clojure.string/join "" [(name (get request :scheme)) "://"])
         host (get (get request :headers) "host")
         ]
    (clojure.string/join "" [scheme host "/"])
  )
)

(defn munge-strings [s host]
  (.replaceAll (new String s) "ovation://" host)
)

(defn unmunge-strings [s host]
  (clojure.pprint/pprint (.getClass s))
  (.replaceAll (new String s) host "ovation://")
)

(defn auth-filter [request f]
  (let [
         params (get request :query-params)
         status (if-not (contains? params "api-key")
                  (num 401)
                  (num 200)
                )
         body (if (= 200 status)
                (str (f (get params "api-key")))
                (str "Please log in to access resource")
              )
       ]
    {:status status
     :body   (munge-strings body (host-from-request request))
     :content-type "application/json"}
  )
)

(defn parse-uuid [s]
  (if (nil? (re-find #"\w{8}-\w{4}-\w{4}-\w{4}-\w{12}" s))
    (let [buffer (java.nio.ByteBuffer/wrap
                   (javax.xml.bind.DatatypeConverter/parseHexBinary s))]
      (java.util.UUID. (.getLong buffer) (.getLong buffer))
    )
    (java.util.UUID/fromString s)
  )
)
