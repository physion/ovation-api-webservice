(ns ovation.test.transform
  (:use midje.sweet)
  (:require [ovation.transform.read :as tr]
            [ovation.transform.write :as tw]
            [ovation.version :refer [version]]
            [ovation.routes :as r]
            [ovation.schema :as s]
            [ovation.util :as util]
            [ovation.constants :as c]
            [ovation.constants :as k]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [ovation.auth :as auth])
  (:import (clojure.lang ExceptionInfo)))


(facts "About annotation links"
  (fact "adds annotation links to entity"
    (tr/add-annotation-links {:_id   "123"
                              :links {:foo "bar"}} ..rt..) => {:_id   "123"
                                                               :links {:foo             "bar"
                                                                       :properties      "/api/v1/entities/123/annotations/properties"
                                                                       :tags            "/api/v1/entities/123/annotations/tags"
                                                                       :notes           "/api/v1/entities/123/annotations/notes"
                                                                       :timeline-events "/api/v1/entities/123/annotations/timeline_events"}}
    (provided
      (r/annotations-route ..rt.. {:_id   "123"
                                   :links {:foo "bar"}} "tags") => "/api/v1/entities/123/annotations/tags"
      (r/annotations-route ..rt.. {:_id   "123"
                                   :links {:foo "bar"}} "properties") => "/api/v1/entities/123/annotations/properties"
      (r/annotations-route ..rt.. {:_id   "123"
                                   :links {:foo "bar"}} "notes") => "/api/v1/entities/123/annotations/notes"
      (r/annotations-route ..rt.. {:_id   "123"
                                   :links {:foo "bar"}} "timeline_events") => "/api/v1/entities/123/annotations/timeline_events")))

(facts "About error handling"
  (facts "in couch-to-entity"
    (fact "throws conflict! if any doc has {:error 'conflict'}"
      ((tr/couch-to-entity ..auth..  ..rt..) {:_id ..id.. :error "conflict"}) => (throws ExceptionInfo))
    (fact "throws forbidden! if any doc has {:error 'forbidden'}"
      ((tr/couch-to-entity ..auth.. ..rt..) {:_id ..id.. :error "forbidden"}) => (throws ExceptionInfo))
    (fact "throws unauthorized! if any doc has {:error 'unauthorized'}"
      ((tr/couch-to-entity ..auth.. ..rt..) {:_id ..id.. :error "unauthorized"}) => (throws ExceptionInfo))))

(facts "About DTO link modifications"
  (fact "`remove-hidden-links` removes '_...' links"
    (tr/remove-private-links {:_id   ...id...
                              :links {"_collaboration_links" #{...hidden...}
                                      :link1                 ...link1...}}) => {:_id   ...id...
                                                                                :links {:link1 ...link1...}})

  (fact "`add-relationship-links adds rel links for entity type"
    (let [type-rel {:relA {}
                    :relB {}}
          dto {:type ..type.. :links {:_collaboration_roots [..collab..]}}]
      (tr/add-relationship-links dto ..rt..) => (-> dto
                                                  (assoc-in [:links] {:_collaboration_roots (get-in dto [:links :_collaboration_roots])})
                                                  (assoc-in [:relationships] {:relA {:self    ..relA-self..
                                                                                     :related ..relA-related..}
                                                                              :relB {:self    ..relB-self..
                                                                                     :related ..relB-related..}}))
      (provided
        (util/entity-type-keyword dto) => ..type..
        (s/EntityRelationships ..type..) => type-rel
        (r/targets-route ..rt.. dto :relA) => ..relA-related..
        (r/targets-route ..rt.. dto :relB) => ..relB-related..
        (r/relationship-route ..rt.. dto :relA) => ..relA-self..
        (r/relationship-route ..rt.. dto :relB) => ..relB-self..)))


  (fact "`add-relationship-links` adds heads to File entity links"
    (let [type-rel {}
          type k/FILE-TYPE
          typekw (util/entity-type-name-keyword type)
          dto {:type type :links {:_collaboration_roots [..collab..]}}]
      (tr/add-heads-link dto ..rt..) => (-> dto
                                                  (assoc-in [:links] {:_collaboration_roots (get-in dto [:links :_collaboration_roots])
                                                                      :heads ..headrt..}))
      (provided
        (r/heads-route ..rt.. dto) => ..headrt..)))

  (fact "`add-self-link` adds self link to entity"
    (let [couch {:_id   ..id..
                 :type  ..type..
                 :links {}}]
      (tr/add-self-link couch ..router..) => {:_id   ..id..
                                              :type  ..type..
                                              :links {:self ..route..}}
      (provided
        (r/self-route ..router.. couch) => ..route..)))
  ;
  (fact "`couch-to-value` adds self link to LinkInfo"
    (let [couch {:_id ..id..
                 :type util/RELATION_TYPE}]
      ((tr/couch-to-value ..auth.. ..rt..) couch) => (assoc-in couch [:links :self] ..url..)
      (provided
        (util/entity-type-name couch) => c/RELATION-TYPE-NAME
        (r/self-route ..rt.. couch) => ..url..))))

(facts "About doc-to-couch"
  (fact "skips docs without :type"
    (let [other {:_id "bar" :rel "some-rel"}]
      (tw/doc-to-couch ..owner.. ..roots.. other) => other))

  (fact "skips docs of type Relation"
    (let [other {:_id "bar" :rel "some-rel" :type "Relation"}]
      (tw/doc-to-couch ..owner.. ..roots.. other) => other))

  (fact "adds updated_at date"
    (let [doc {:type ..type.. :attributes {:label ..label..}}]
      (:attributes (tw/doc-to-couch ..owner.. ..roots.. doc)) => (contains {:updated-at ..time..})
      (provided
        (f/unparse (f/formatters :date-time) anything) => ..time..)))

  (fact "adds created_at date"
    (let [doc {:type ..type.. :attributes {:label ..label..}}]
      (:attributes (tw/doc-to-couch ..owner.. ..roots.. doc)) => (contains {:created-at ..time..})
      (provided
        (f/unparse (f/formatters :date-time) (t/now)) => ..time..)))

  (fact "does not add created_at date if already present"
    (let [doc {:type ..type.. :attributes {:label ..label.. :created-at ..old..}}]
      (get-in (tw/doc-to-couch ..owner.. ..roots.. doc) [:attributes :created-at]) => ..old..
      (provided
        (f/unparse (f/formatters :date-time) (t/now)) => ..time..)))

  (fact "adds collaboration roots"
    (let [doc {:type ..type.. :attributes {:label ..label..}}]
      (get-in (tw/doc-to-couch ..owner.. ..roots.. doc) [:links :_collaboration_roots]) => ..roots..)))


(facts "About `add-owner`"
  (fact "`add-owner` adds owner element"
    (let [doc {:type ..type.. :attributes {:label ..label..}}]
      (tw/add-owner doc ..owner..) => (assoc doc :owner ..owner..))))

(facts "About `remove-user-attributes`"
  (fact "Removes User entity attributes"
    (let [user {:type "User" :attributes {:password ..secret..}}]
      (tr/remove-user-attributes user) => (assoc user :attributes {})))

  (fact "Retains User :name attribute"
    (let [user {:type "User" :attributes {:password ..secret.. :name ..name..}}]
      (tr/remove-user-attributes user) => (assoc user :attributes {:name ..name..})))

  (fact "Does not remove other entity attributes"
    (let [doc {:type "MyEntity" :attributes {:label ..label..}}]
      (tr/remove-user-attributes doc) => doc)))

(facts "About permissions"
  (facts "for entities"
    (let [doc {:owner ..id..}]
      (fact "add-entity-permissions sets {update: (can? :update) delete: (can? :delete)}"
        (tr/add-entity-permissions doc ..id..) => (assoc doc :permissions {:update ..update..
                                                                           :delete ..delete..})
        (provided
          (auth/can? ..id.. :auth/update doc) => ..update..
          (auth/can? ..id.. :auth/delete doc) => ..delete..))))
  (facts "for values"))

