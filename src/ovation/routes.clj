(ns ovation.routes
  (:require [compojure.api.routes :refer [path-for*]]
            [ovation.util :as util]))

(defn router
  [request]
  (fn [name & [params]]
    (path-for* name request params)))

(defn relationship-route
  [rt doc name]
  (let [type (util/entity-type-name doc)
        id (:_id doc)]
    (rt (keyword (format "get-%s-links" type)) {:id id :rel name})))

(defn targets-route
  [rt doc name]
  (let [type (util/entity-type-name doc)
        id (:_id doc)]
    (rt (keyword (format "get-%s-link-targets" type)) {:id id :rel name})))

(defn self-route
  [rt doc]
  (let [type (util/entity-type-name doc)
        id (:_id doc)]
    (case type
      util/RELATION_TYPE (rt (keyword (format "get-%s-links" type)) {:id id})
      (rt (keyword (format "get-%s" type)) {:id id}))))

(defn annotations-route
  [rt doc]
  (rt :annotations {:id (:_id doc)}))
