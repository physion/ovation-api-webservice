(ns ovation.test.authz
  (:use midje.sweet)
  (:require [ovation.authz :as authz]
            [ovation.util :as util]
            [ovation.request-context :as request-context]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.core.async :as async :refer [go >!]]
            [ovation.groups :as groups]
            [ovation.auth :as auth]
            [buddy.auth.accessrules :as accessrules]))

(facts "About has-scope?"
  (fact "with scope"
    (authz/has-scope? {::auth/scopes ["read:global foo:bar"]} "read:global") => true)
  (fact "without scope"
    (authz/has-scope? {::auth/scopes ["read:global foo:bar"]} "write:global") => false)
  (fact "with wildcard scope"
    (authz/has-scope? {::auth/scopes ["read:global foo:bar"]} "read:*") => true)
  (fact "without wildcard scope"
    (authz/has-scope? {::auth/scopes ["foo:bar"]} "read:*") => false))

(facts "About require-scope"
  (fact "builds fn that requires scope"
    ((authz/require-scope ..scope..) ..request..) => accessrules/success
    (provided
      (authz/has-scope? ..identity.. ..scope..) => true
      (auth/identity ..request..) => ..identity..))
  (fact "builds fn that rejects if missing scope"
    ((authz/require-scope ..scope..) ..request..) => (accessrules/error (str ..scope.. "scope required"))
    (provided
      (authz/has-scope? ..identity.. ..scope..) => false
      (auth/identity ..request..) => ..identity..)))

(facts "About AuthzService"
  (fact "proxies authorizations"
    (let [z (authz/new-authz-service ..url..)]
      (authz/get-authorization z ..ctx..) => {:authorization ..result..}
      (provided
        ..ctx.. =contains=> {::request-context/org ..org..}
        (async/promise-chan) => ..ch..
        (authz/get-authorizations ..ctx.. ..url.. ..ch..) => ..go..
        (util/<?? ..ch..) => ..result..))))

