(ns ovation.handler
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.routes :refer [path-for*]]
            [ring.util.http-response :refer [created ok accepted not-found unauthorized bad-request]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ovation.schema :refer :all]
            [ovation.logging]
            [ovation.routes :refer [router]]
            [ovation.route-helpers :refer [annotation get-resources post-resources get-resource post-resource put-resource delete-resource rel-related relationships]]
            [clojure.tools.logging :as logging]
            [ovation.config :as config]
            [ovation.core :as core]
            [slingshot.slingshot :refer [try+ throw+]]
            [ovation.middleware.token-auth :refer [wrap-token-auth]]
            [ovation.links :as links]
            [ring.middleware.conditional :refer [if-url-starts-with]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.middleware.raygun :refer [wrap-raygun-handler]]
            [clojure.string :refer [lower-case capitalize join]]
            [schema.core :as s]
            [ovation.routes :as r]
            [ovation.auth :as auth]))

(ovation.logging/setup!)





;;; --- Routes --- ;;;
(defapi app

  (middlewares [
                (wrap-cors
                  :access-control-allow-origin #".+"        ;; Allow from any origin
                  :access-control-allow-methods [:get :put :post :delete :options]
                  :access-control-allow-headers ["Content-Type" "Accept"])

                ;; Require authorization (via header token auth) for all paths starting with /api
                (wrap-token-auth
                  :authserver config/AUTH_SERVER
                  :required-auth-url-prefix #{"/api"})


                (wrap-with-logger                           ;;TODO can we make the middleware conditional rather than testing for each logging call?
                  :info (fn [x] (when config/LOGGING_HOST (logging/info x)))
                  :debug (fn [x] (when config/LOGGING_HOST (logging/debug x)))
                  :error (fn [x] (when config/LOGGING_HOST (logging/error x)))
                  :warn (fn [x] (when config/LOGGING_HOST (logging/warn x))))

                (wrap-raygun-handler (System/getenv "RAYGUN_API_KEY"))
                ]

    (swagger-ui)
    (swagger-docs
      {:info {
              :version        "2.0.0"
              :title          "Ovation"
              :description    "Ovation Web API"
              :contact        {:name "Ovation"
                               :url  "https://ovation.io"}
              :termsOfService "https://ovation.io/terms_of_service"}}
      :tags [{:name "entities" :description "Generic entity operations"}
             {:name "projects" :description "Projects"}
             {:name "folders" :description "Folders"}
             {:name "files" :description "Files"}
             {:name "protocols" :description "Protocols"}
             {:name "sources" :description "Sources"}
             {:name "users" :description "Users"}
             {:name "analyses" :description "Analysis Records"}
             {:name "annotations" :description "Per-user annotations"}
             {:name "links" :description "Relationships between entities"}
             {:name "provenance" :description "Provenance graph"}])


    (context* "/api" []
      (context* "/v1" []
        (context* "/entities" []
          :tags ["entities"]
          (context* "/:id" [id]

            (GET* "/" request
              :name :get-entity
              :return {:entity Entity}
              :responses {404 {:schema JsonApiError :description "Not found"}}
              :summary "Returns entity with :id"
              (let [auth (:auth/auth-info request)]
                (if-let [entities (core/get-entities auth [id] (router request))]
                  (ok {:entity (first entities)})
                  (not-found {:errors {:detail "Not found"}}))))

            (context* "/annotations" []
              :tags ["annotations"]
              :name :annotations
              (annotation id "keywords" "tags" TagRecord TagAnnotation)
              (annotation id "properties" "properties" PropertyRecord PropertyAnnotation)
              (annotation id "timeline events" "timeline_events" TimelineEventRecord TimelineEventAnnotation)
              (annotation id "notes" "notes" NoteRecord NoteAnnotation))))

        (context* "/relationships" []
          :tags ["links"]
          (context* "/:id" [id]
            (GET* "/" request
              :name :get-relation
              :return {:relationship LinkInfo}
              :summary "Relationship document"
              (let [auth (:auth/auth-info request)]
                (ok {:relationship (first (core/get-values auth [id] :routes (r/router request)))})))
            (DELETE* "/" request
              :name :delete-relation
              :return {:relationship LinkInfo}
              :summary "Removes relationship"
              (let [auth (:auth/auth-info)
                    relationship (first (core/get-values auth [id]))]
                (if relationship
                  (let [source (first (core/get-entities auth [(:source_id relationship)] (r/router request)))]
                    (accepted {:relationships (links/delete-links auth (r/router request)
                                                source
                                                (auth/authenticated-user-id auth)
                                                (:_id relationship))}))
                  (not-found {:errors {:detail "Not found"}}))))))

        (context* "/projects" []
          :tags ["projects"]
          (get-resources "Project")
          (post-resources "Project" [NewProject])
          (context* "/:id" [id]
            (get-resource "Project" id)
            (post-resource "Project" id [NewEntity])
            (put-resource "Project" id)
            (delete-resource "Project" id)

            (context* "/links/:rel" [rel]
              (rel-related "Project" id rel)
              (relationships "Project" id rel))))


        (context* "/sources" []
          :tags ["sources"]
          (get-resources "Source")
          (post-resources "Source" [NewSource])
          (context* "/:id" [id]
            (get-resource "Source" id)
            (post-resource "Source" id [NewSource])
            (put-resource "Source" id)
            (delete-resource "Source" id)

            (context* "/links/:rel" [rel]
              (rel-related "Source" id rel)
              (relationships "Source" id rel))))


        (context* "/folders" []
          :tags ["folders"]
          (get-resources "Folder")
          (post-resources "Folder" [NewFolder])
          (context* "/:id" [id]
            (get-resource "Folder" id)
            (post-resource "Folder" id [NewFolder NewFile NewRevision])
            (put-resource "Folder" id)
            (delete-resource "Folder" id)

            (context* "/links/:rel" [rel]
              (rel-related "Folder" id rel)
              (relationships "Folder" id rel))))


        (context* "/files" []
          :tags ["files"]
          (get-resources "File")
          (post-resources "File" [NewFile])
          (context* "/:id" [id]
            (get-resource "File" id)
            (post-resource "File" id [NewRevision])
            (put-resource "File" id)
            (delete-resource "File" id)

            ;(context* "/upload" request
            ;  (POST*
            ;    "/" []
            ;    :multipart-params [file :- upload/TempFileUpload]
            ;    :middlewares [upload/wrap-multipart-params]
            ;    (ok (dissoc file :tempfile))))

            (context* "/links/:rel" [rel]
              (rel-related "File" id rel)
              (relationships "File" id rel))))




        (context* "/users" []
          :tags ["users"]
          (get-resources "User")
          (context* "/:id" [id]
            (get-resource "User" id)))


        (context* "/provenance" []
          :tags ["provenance"]
          (POST* "/" request
            :name :get-provenance
            ;:return {:provenance ProvGraph}
            :summary "Returns the provenance graph expanding from the POSTed entity IDs"
            (let [auth (:auth/auth-info request)]
              nil)))))))

