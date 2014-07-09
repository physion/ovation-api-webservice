(ns ovation-api-webservice.entity-view
  (:use ovation-api-webservice.util)
)

(defn get-entity-helper [uuid api_key]
  (entities-to-json
    (seq [(-> (ctx api_key) (.getObjectWithUuid (parse-uuid uuid)))])
  )
)

(defn get-entity [id request]
  (auth-filter request (partial get-entity-helper id))
)

(defn get-entity-rel-helper [uuid rel api_key]
  (let [
         entity (-> (ctx api_key) (. getObjectWithUuid (parse-uuid uuid)))
         relation (-> entity (. getEntities rel))
       ]
    (entities-to-json
      (seq relation)
    )
  )
)

(defn get-entity-rel [id rel request]
  (auth-filter request (partial get-entity-rel-helper id rel))
)

(defn update-entity-helper [uuid request api_key]
  (let [
         body (unmunge-strings (get-body-from-request request) (host-from-request request))
         in_json (json-to-object body)
         ]
    (do
      (-> (ctx api_key) (.getObjectWithUuid (parse-uuid uuid)) (.update in_json))
      (get-entity-helper uuid api_key)
    )
  )
)

(defn update-entity [id request]
  (auth-filter request (partial update-entity-helper id))
)

(defn create-entity-helper [request api_key]
  (let [
         body (get-body-from-request request)
         json_map (json-to-object body)
         entity (-> (ctx api_key) (.insertEntity json_map))
       ]
    (entities-to-json (seq [entity]))
  )
)

(defn create-entity [request]
  (auth-filter request (partial create-entity-helper request))
)

(defn delete-entity-helper [uuid request api_key]
  (let [
         entity (-> (ctx api_key) (. getObjectWithUuid (parse-uuid uuid)))
         trash_resp (-> (ctx api_key) (. trash entity) (.get))
       ]
    (str "{\"success\": 1}")
  )
)

(defn delete-entity [id request]
  (auth-filter request (partial delete-entity-helper id request))
)

(defn index-resource-helper [resource api_key]
  (let [
         resources (case resource
                     "project" (-> (ctx api_key) (.getProjects))
                     "source" (-> (ctx api_key) (.getSources))
                     "protocol" (-> (ctx api_key) (.getProtocols))
                   )
       ]
    (entities-to-json
      (seq resources)
    )
  )
)

(defn index-resource [resource request]
  (auth-filter request (partial index-resource-helper resource))
)

