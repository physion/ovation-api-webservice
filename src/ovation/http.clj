(ns ovation.http
  (:require [ovation.util :as util]
            [clojure.tools.logging :as logging]
            [org.httpkit.client :as httpkit.client]
            [ring.util.http-response :refer [throw! bad-request! not-found! unprocessable-entity!]]
            [clojure.core.async :refer [chan go >! >!! pipeline]]
            [ring.util.http-predicates :as hp]
            [slingshot.slingshot :refer [try+]]
            [clojure.tools.logging :as logging]
            [clojure.walk :as walk])
  (:import (java.io EOFException)))

(defn call-http
  "Makes http-client async request onto the provided channel.

  Conveys response body if success or a throw! in case of failure"
  [ch method url opts success-fn & {:keys [log-response?] :or [log-response? true]}]
  (logging/debug "Sending HTTP:" method url (:query-params opts))
  (httpkit.client/request (merge {:method method :url url} opts)
    (fn [resp]
      (logging/info "Received HTTP response:" method url (:query-params opts) "-" (:status resp))
      (if log-response?
        (logging/debug "Raw:" (:body resp)))
      (if (success-fn resp)
        (try+
          (if (hp/no-content? resp)
            (>!! ch {})
            (let [body (util/from-json (:body resp))]
              (if log-response?
                (logging/debug "Response:" body))
              (>!! ch body)))
          (catch EOFException _
            (logging/info "Response is empty")
            (let [err {:type :ring.util.http-response/response :response resp}]
              (logging/debug "Conveying HTTP response error " err)
              (>!! ch err))))

        (let [err {:type :ring.util.http-response/response :response (-> (select-keys resp [:status :body])
                                                                       (assoc :headers (walk/stringify-keys (select-keys (:headers resp) [:content-type :access-control-allow-credentials :access-control-allow-methods :access-control-allow-origin]))))}]
          (logging/debug "Conveying HTTP response error " err)
          (>!! ch err))))))


(defn request-opts
  [ctx]
  {:timeout     10000                                       ; ms
   :oauth-token (if (string? ctx) ctx (get-in ctx [:ovation.request-context/identity :ovation.auth/token]))
   :headers     {"Content-Type" "application/json; charset=utf-8"
                 "Accept"       "application/json"}})

(defn make-url
  [base & comps]
  (util/join-path (conj comps base)))

(defn read-collection-tf
  [ctx key make-tf]
  (fn
    [response]
    (if (util/exception? response)
      response
      (let [entities (key response)]
        (if make-tf
          (map (make-tf ctx) entities)
          entities)))))

(defn read-single-tf
  [ctx key make-tf]
  (let [tf (make-tf ctx)]
    (fn [response]
      (if (util/exception? response)
        response
        (let [obj    (if key (key response) response)]
          (tf obj))))))

(defn index-resource
  [ctx api-url rsrc ch & {:keys [close? response-key make-tf query-params] :or {close?       true
                                                                                query-params nil
                                                                                make-tf      (fn [_] identity)}}]
  (let [raw-ch (chan)
        url    (make-url api-url rsrc)
        opts   (assoc (request-opts ctx)
                 :query-params query-params)]
    (go
      (try+
        (call-http raw-ch :get url opts hp/ok?)
        (pipeline 1 ch (map (read-collection-tf ctx response-key make-tf)) raw-ch close?)
        (catch Object ex
          (logging/error ex "Exception in go block")
          (>! ch ex))))))

(defn show-resource
  [ctx api-url rsrc id ch & {:keys [close? response-key make-tf query-params] :or {close?       true
                                                                                   query-params nil
                                                                                   make-tf      (fn [_] identity)}}]
  (let [raw-ch (chan)
        url    (make-url api-url rsrc id)
        opts   (request-opts ctx)]
    (go
      (try+
        (call-http raw-ch :get url opts hp/ok?)
        (pipeline 1 ch (map (read-single-tf ctx response-key make-tf)) raw-ch close?)
        (catch Object ex
          (logging/error ex "Exception in go block")
          (>! ch ex))))))

(defn create-resource
  [ctx api-url rsrc body ch & {:keys [close? response-key make-tf] :or {close? true}}]
  (let [raw-ch (chan)
        url    (make-url api-url rsrc)
        opts   (assoc (request-opts ctx)
                 :body (util/to-json body))]
    (go
      (try+
        (if-let [body-org (:organization_id body)]
          (when (not (= body-org (:ovation.request-context/org ctx)))
            (do
              (logging/info "Organization in POST body" body-org "doesn't match URL param" (:ovation.request-context/org ctx))
              (unprocessable-entity! {:error "Organization ID mismatch"}))))

        (call-http raw-ch :post url opts hp/created?)
        (pipeline 1 ch (map (read-single-tf ctx response-key make-tf)) raw-ch close?)
        (catch Object ex
          (logging/error ex "Exception in go block")
          (>! ch ex))))))

(defn update-resource
  [ctx api-url rsrc body id ch & {:keys [close? response-key make-tf] :or {close? true}}]

  (let [raw-ch (chan)
        url    (make-url api-url rsrc id)
        opts   (assoc (request-opts ctx)
                 :body (util/to-json body))]
    (go
      (try+
        (if-let [body-org (:organization_id body)]
          (when (not (= body-org (:ovation.request-context/org ctx)))
            (unprocessable-entity! {:error "Organization ID mismatch"})))

        (call-http raw-ch :put url opts hp/ok?)
        (pipeline 1 ch (map (read-single-tf ctx response-key make-tf)) raw-ch close?)
        (catch Object ex
          (logging/error ex "Exception in go block")
          (>! ch ex))))))

(defn destroy-resource
  [ctx api-url rsrc id ch & {:keys [close?] :or {close? true}}]
  (let [url    (make-url api-url rsrc id)
        opts   (request-opts ctx)]
    (go
      (try+
        (call-http ch :delete url opts (fn [response]
                                           (or (hp/ok? response)
                                             (hp/no-content? response))))
        (catch Object ex
          (logging/error ex "Exception in go block")
          (>! ch ex))))))
