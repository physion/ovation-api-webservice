(ns ovation.test.transform.read
  (:use midje.sweet)
  (:require [ovation.transform.read :as tr]
            [ovation.test.db.notes :as tnotes]
            [ovation.test.db.properties :as tproperties]
            [ovation.test.db.relations :as trelations]
            [ovation.test.db.tags :as ttags]
            [ovation.test.db.timeline_events :as ttimeline_events]
            [ovation.version :refer [version]]
            [ovation.routes :as r]
            [ovation.schema :as s]
            [ovation.util :as util]
            [ovation.constants :as c]
            [ovation.request-context :as request-context]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [ovation.auth :as auth])
  (:import (clojure.lang ExceptionInfo)))


(against-background [..ctx.. =contains=> {::request-context/identity      ..auth..
                                          :ovation.request-context/routes ..rt..
                                          :ovation.request-context/org    ..org..}
                     (request-context/user-id ..ctx..) => ..owner-id..]

  (facts "About annotation links"
    (fact "adds annotation links to entity"
      (tr/add-annotation-links {:_id   "123"
                                :links {:foo "bar"}} ..ctx..) => {:_id  "123"
                                                                  :links {:foo             "bar"
                                                                          :properties      "/api/v1/entities/123/annotations/properties"
                                                                          :tags            "/api/v1/entities/123/annotations/tags"
                                                                          :notes           "/api/v1/entities/123/annotations/notes"
                                                                          :timeline-events "/api/v1/entities/123/annotations/timeline_events"}}
      (provided
        (r/annotations-route ..ctx.. {:_id  "123"
                                      :links {:foo "bar"}} "tags") => "/api/v1/entities/123/annotations/tags"
        (r/annotations-route ..ctx.. {:_id  "123"
                                      :links {:foo "bar"}} "properties") => "/api/v1/entities/123/annotations/properties"
        (r/annotations-route ..ctx.. {:_id  "123"
                                      :links {:foo "bar"}} "notes") => "/api/v1/entities/123/annotations/notes"
        (r/annotations-route ..ctx.. {:_id  "123"
                                      :links {:foo "bar"}} "timeline_events") => "/api/v1/entities/123/annotations/timeline_events")))

  (facts "About error handling"
    (facts "in couch-to-entity"
      (fact "throws conflict! if any doc has {:error 'conflict'}"
        ((tr/couch-to-entity ..ctx..) {:_id ..id.. :error "conflict"}) => (throws ExceptionInfo))
      (fact "throws forbidden! if any doc has {:error 'forbidden'}"
        ((tr/couch-to-entity ..ctx..) {:_id ..id.. :error "forbidden"}) => (throws ExceptionInfo))
      (fact "throws unauthorized! if any doc has {:error 'unauthorized'}"
        ((tr/couch-to-entity ..ctx..) {:_id ..id.. :error "unauthorized"}) => (throws ExceptionInfo))))

  (facts "About DTO link modifications"
    (fact "`remove-private-links` removes '_...' links"
      (tr/remove-private-links {:_id   ...id...
                                :links {"_collaboration_links" #{...hidden...}
                                        :link1                 ...link1...}}) => {:_id   ...id...
                                                                                  :links {:link1 ...link1...}})

    (fact "`add-relationship-links adds rel links for entity type"
      (let [type-rel {:relA {}
                      :relB {}}
            dto      {:type ..type.. :links {:_collaboration_roots [..collab..]}}]
        (tr/add-relationship-links dto ..ctx..) => (-> dto
                                                     (assoc-in [:links] {:_collaboration_roots (get-in dto [:links :_collaboration_roots])})
                                                     (assoc-in [:relationships] {:relA {:self    ..relA-self..
                                                                                        :related ..relA-related..}
                                                                                 :relB {:self    ..relB-self..
                                                                                        :related ..relB-related..}}))
        (provided
          (util/entity-type-keyword dto) => ..type..
          (s/EntityRelationships ..type..) => type-rel
          (r/targets-route ..ctx.. dto :relA) => ..relA-related..
          (r/targets-route ..ctx.. dto :relB) => ..relB-related..
          (r/relationship-route ..ctx.. dto :relA) => ..relA-self..
          (r/relationship-route ..ctx.. dto :relB) => ..relB-self..)))


    (fact "`add-heads-link` adds heads to File entity links"
      (let [type c/FILE-TYPE
            dto  {:type type :links {:_collaboration_roots [..collab..]}}]
        (tr/add-heads-link dto ..ctx..) => (-> dto
                                            (assoc-in [:links] {:_collaboration_roots (get-in dto [:links :_collaboration_roots])
                                                                :heads                ..headrt..}))
        (provided
          (r/heads-route ..ctx.. dto) => ..headrt..)))

    (facts "add-zip-link"
      (fact "adds zip link for Activity"
        (let [type c/ACTIVITY-TYPE
              dto  {:type type :links {:_collaboration_roots [..roots..]}}]
          (tr/add-zip-link dto ..ctx..) => (assoc-in dto [:links :zip] ..zip..)
          (provided
            (r/zip-activity-route ..ctx.. dto) => ..zip..)))

      (fact "adds zip link for Folder"
        (let [type c/FOLDER-TYPE
              dto  {:type type :links {:_collaboration_roots [..roots..]}}]
          (tr/add-zip-link dto ..ctx..) => (assoc-in dto [:links :zip] ..zip..)
          (provided
            (r/zip-folder-route ..ctx.. dto) => ..zip..)))

      (fact "does not add zip link for Project"
        (let [type c/PROJECT-TYPE
              dto  {:type type :links {:_collaboration_roots [..roots..]}}]
          (tr/add-zip-link dto ..rt..) => dto)))

    (facts "add-upload-links")

    (facts "`add-team-link`"
      (fact "adds link for Project"
        (let [couch {:_id ..id..
                     :type c/PROJECT-TYPE
                     :links {}}]
          (tr/add-team-link couch ..ctx..) => (assoc-in couch [:links :team] ..team..)
          (provided
            (r/team-route ..ctx.. ..id..) => ..team..)))
      (fact "does not add team link for non-project"
        (let [couch {:_id ..id..
                     :type c/SOURCE-TYPE
                     :links {}}]
          (tr/add-team-link couch ..ctx..) => couch)))

    (fact "`add-self-link` adds self link to entity"
      (let [couch {:_id   ..id..
                   :type  ..type..
                   :links {}}]
        (tr/add-self-link couch ..ctx..) => {:_id      ..id..
                                                :type  ..type..
                                                :links {:self ..route..}}
        (provided
          (r/self-route ..ctx.. couch) => ..route..)))

    (facts "add-annotation-self-link"))

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
    (against-background [..ctx.. =contains=> {::request-context/identity ..auth..
                                              ::request-context/org      ..org..}])
    (facts "for entities"
      (let [doc {:owner ..id..}]
        (fact "add-entity-permissions sets {update: (can? :update) delete: (can? :delete)}"
          (tr/add-entity-permissions doc ..id..) => (assoc doc :permissions {:update ..update..
                                                                                       :delete ..delete..
                                                                                       :create true})
          (provided
            (auth/can? ..id.. :ovation.auth/update doc) => ..update..
            (auth/can? ..id.. :ovation.auth/delete doc) => ..delete..))))
    (facts "for annotations"
      (let [doc {:user ..id..
                 :type "Annotation"}]
        (fact "add-value-permissions sets {update: (can? :update) delete: (can? :delete)"
          (tr/add-value-permissions doc ..ctx..) => (assoc doc :permissions {:update true
                                                                             :delete true})
          (provided
            ;; TODO What changed here?
            ;;(auth/authenticated-user-uuid ..auth..) => ..id..
            (auth/organization-ids ..auth..) => [..org..]
            (auth/has-scope? ..auth.. c/WRITE-GLOBAL-SCOPE) => true
            (#'auth/can-update? ..auth.. doc) => true
            (#'auth/can-delete? ..auth.. doc) => true)))))

  (facts "About entities-from-db"
    (fact "removes unauthorized documents"
      (tr/entities-from-db [..doc.. ..bad..] ..ctx..) => [..doc..]
      (provided
        (tr/db-to-entity ..ctx..) => (fn [doc] doc)
        (auth/authenticated-teams ..auth..) => ..teams..
        (auth/can? ..ctx.. ::auth/read ..doc.. :teams ..teams..) => true
        (auth/can? ..ctx.. ::auth/read ..bad.. :teams ..teams..) => false)))

  (facts "db-to-value"
    (facts "for relations"
      (let [record {:_id ..id..
                    :type c/RELATION-TYPE
                    :source_id ..source..}
            transformed-record {:_id ..id..
                                :type c/RELATION-TYPE
                                :source_id ..source..
                                :links {:_collaboration_roots [..source..]
                                        :self {:id ..id..
                                               :org ..org..}}}]
        (fact "it adds collaboration roots and self link"
          ((tr/db-to-value ..ctx..) record) => transformed-record)))
    (facts "for annotations"
      (let [record {:_id ..id..
                    :organization_id ..org..
                    :project ..project..
                    :user ..user..
                    :entity ..entity..
                    :text ..text..
                    :timestamp ..timestamp..
                    :edited_at ..edited-at..
                    :annotation_type c/NOTES
                    :type c/ANNOTATION-TYPE}
            transformed-record {:_id ..id..
                                :organization_id ..org..
                                :user ..user..
                                :entity ..entity..
                                :annotation {:text ..text..
                                             :timestamp ..timestamp..}
                                :permissions {:update true
                                              :delete true}
                                :edited_at ..edited-at..
                                :annotation_type c/NOTES
                                :type c/ANNOTATION-TYPE
                                :links {:_collaboration_roots [..project..]}}]
        (fact "it adds annotation, links and permissions"
          ((tr/db-to-value ..ctx..) record) => transformed-record
          (provided
            (auth/can? ..ctx.. anything anything) => true)))))

  (facts "values-from-db"
    (fact "it transforms and authorizes record"
      (tr/values-from-db [..record..] ..ctx..) => [..transformed-record..]
      (provided
        (tr/db-to-value ..ctx..) => (fn [record] ..transformed-record..)
        (auth/can? ..ctx.. ::auth/read ..transformed-record.. :teams anything) => true))))
