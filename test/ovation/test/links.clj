(ns ovation.test.links
  (:use midje.sweet)
  (:require [ovation.links :as links]
            [ovation.couch :as couch]
            [ovation.util :as util]
            [ovation.auth :as auth]
            [ovation.core :as core]
            [ovation.version :as ver]
            [ovation.constants :as k]
            [ovation.routes :as r])
  (:import (java.util UUID)))

(facts "About links"
  (facts "`get-link-targets`"
    (let [doc1 {:attributes {:label ..label1..}}
          doc2 {:attributes {:label ..label2..}}
          doc3 {:attributes {}}]
      (against-background [(couch/get-view ..db.. k/LINKS-VIEW {:startkey      [..id.. ..rel..]
                                                                         :endkey        [..id.. ..rel..]
                                                                         :inclusive_end true
                                                                         :reduce        false
                                                                         :include_docs  true}) => [doc1 doc2 doc3]
                           (couch/get-view ..db.. k/LINKS-VIEW {:startkey      [..id.. ..rel.. ..name..]
                                                                         :endkey        [..id.. ..rel.. ..name..]
                                                                         :inclusive_end true
                                                                         :reduce        false
                                                                         :include_docs  true}) => [doc1]
                           (auth/can? anything :auth/update anything) => true
                           (couch/db ..auth..) => ..db..]

        (fact "gets entity rel targets"
          (links/get-link-targets ..auth.. ..id.. ..rel.. ..rt..) => [doc1 doc2 doc3])

        (fact "gets named entity rel targets"
          (links/get-link-targets ..auth.. ..id.. ..rel.. ..rt.. :name ..name..) => [doc1])

        (fact "filters by label"
          (links/get-link-targets ..auth.. ..id.. ..rel.. ..rt.. :label ..label1..) => [doc1]))))


  (facts "`add-link`"
    (let [doc {:_id   (str (UUID/randomUUID))
               :type  "MyEntity" `:attrbutes {}
               :links {:_collaboration_roots [..sourceroot..]}}
          target-id (str (UUID/randomUUID))
          target-id2 (str (UUID/randomUUID))]

      (against-background [(couch/db ..auth..) => ..db..
                           (core/get-entities ..auth.. #{target-id} ..rt..) => [{:_id target-id
                                                                          :type  "not-a-root"
                                                                          :links {:_collaboration_roots [..targetroot..]}}]
                           (auth/authenticated-user-id ..auth..) => ..id..]


        (fact "creates link document"
          (:links (links/add-links ..auth.. doc ..rel.. [target-id] ..rt.. :inverse-rel ..inverse..)) => (contains {:_id  (format "%s--%s-->%s" (:_id doc) ..rel.. target-id)
                                                                                                             :user_id     ..id..
                                                                                                             :type        "Relation"
                                                                                                             :source_id   (:_id doc)
                                                                                                             :target_id   target-id
                                                                                                             :rel         ..rel..
                                                                                                             :inverse_rel ..inverse..
                                                                                                             :links       {:_collaboration_roots [..sourceroot.. ..targetroot..]
                                                                                                                           :self ..url..}})
          (provided
            (r/relationship-route ..rt.. doc ..rel..) => ..url..))

        (fact "creates link document without inverse"
          (:links (links/add-links ..auth.. doc ..rel.. [target-id] ..rt..)) => (contains {:_id       (format "%s--%s-->%s" (:_id doc) ..rel.. target-id)
                                                                                           :user_id   ..id..
                                                                                           `:type     "Relation"
                                                                                           :source_id (:_id doc)
                                                                                           :target_id target-id
                                                                                           :rel       ..rel..
                                                                                           :links     {:self                 ..url..
                                                                                                       :_collaboration_roots [..sourceroot.. ..targetroot..]}})
          (provided
            (r/relationship-route ..rt.. doc ..rel..) => ..url..))
        (fact "creates named link document"
          (:links (links/add-links ..auth.. doc ..rel.. [target-id] ..rt.. :inverse-rel ..inverse.. :name ..name..)) => (contains {:_id  (format "%s--%s>%s-->%s" (:_id doc) ..rel.. ..name.. target-id)
                                                                                                                            :user_id     ..id..
                                                                                                                            :type        "Relation"
                                                                                                                            :source_id   (:_id doc)
                                                                                                                            :target_id   target-id
                                                                                                                            :name        ..name..
                                                                                                                            :inverse_rel ..inverse..
                                                                                                                            :rel         ..rel..
                                                                                                                            :links       {:_collaboration_roots [..sourceroot.. ..targetroot..]
                                                                                                                                          :self ..url..}})
          (provided
            (r/relationship-route ..rt.. doc ..rel..) => ..url..))



        (fact "updates source _collaboration_roots by aggregating multiple target collaboration roots"
          (let [rel "some-rel"
                source {:_id "src" :type "Entity" :links {:_collaboration_roots [..roots1..]}}
                target1 {:_id "target1" :type "Project" :links {:_collaboration_roots [..roots2..]}}
                target2 {:_id "target2" :type "Project" :links {:_collaboration_roots [..roots3..]}}
                updated-source (assoc-in source [:links :_collaboration_roots] #{..roots3.. ..roots2.. ..roots1..})]
            (:updates (links/add-links ..auth.. source rel [target-id target-id2] ..rt..)) => (contains updated-source)
            (provided
              (core/get-entities ..auth.. #{target-id target-id2} ..rt..) => [target1 target2])))


        (fact "fails if not can? :update"
          (links/add-links ..auth.. doc ..rel.. [target-id] ..rt..) => (throws Exception)
          (provided
            (auth/can? ..id.. :auth/update anything) => false))



        (fact "updates target _collaboration_roots for source:=Project"
          (let [rel "some-rel"
                source {:type "Project" :links {:_collaboration_roots [..roots1..]}}
                target {:type "Entity" :links {:_collaboration_roots [..roots2..]}}
                expected (assoc-in target [:links :_collaboration_roots] #{..roots2.. ..roots1..})]
            (:updates (links/add-links ..auth.. source rel [target-id] ..rt..)) => (contains expected)
            (provided
              (core/get-entities ..auth.. #{target-id} ..rt..) => [target])))

        (fact "updates target _collaboration_roots for source:=Folder"
          (let [rel "some-rel"
                source {:type "Folder" :links {:_collaboration_roots [..roots1..]}}
                target {:type "Entity" :links {:_collaboration_roots [..roots2..]}}
                expected (assoc-in target [:links :_collaboration_roots] #{..roots2.. ..roots1..})]
            (:updates (links/add-links ..auth.. source rel [target-id] ..rt..)) => (contains expected)
            (provided
              (core/get-entities ..auth.. #{target-id} ..rt..) => [target])))

        (fact "updates source _collaboration_roots for target:=Project"
          (let [rel "some-rel"
                source {:_id (str (UUID/randomUUID)) :type "Entity" :links {:_collaboration_roots [..roots1..]}}
                target {:_id target-id :type "Project" :links {:_collaboration_roots [..roots2..]}}
                link-path (util/join-path ["" "api" ver/version "entities" (:_id source) "links" rel])
                expected (assoc-in source [:links :_collaboration_roots] #{..roots1.. ..roots2..})]
            (:updates (links/add-links ..auth.. source rel [target-id] ..rt..)) => (contains expected)
            (provided
              (core/get-entities ..auth.. #{target-id} ..rt..) => [target])))

        (fact "updates source _collaboration_roots for target:=Folder"
          (let [rel "some-rel"
                source {:_id (str (UUID/randomUUID)) :type "Entity" :links {:_collaboration_roots [..roots1..]}}
                target {:_id target-id :type "Folder" :links {:_collaboration_roots [..roots2..]}}
                link-path (util/join-path ["" "api" ver/version "entities" (:_id source) "links" rel])
                expected (assoc-in source [:links :_collaboration_roots] #{..roots1.. ..roots2..})]
            (:updates (links/add-links ..auth.. source rel [target-id] ..rt..)) => (contains expected)
            (provided
              (core/get-entities ..auth.. #{target-id} ..rt..) => [target])))

        )))

  (facts "`delete-link`"

    (let [target-id (str (UUID/randomUUID))
          doc {:_id   (str (UUID/randomUUID))
               :type  "MyEntity"
               :attrbutes {}
               :links {:_collaboration_roots [target-id]}}]

      (against-background [(couch/db ..auth..) => ..db..]

        (fact "removes link"
          (let [source-id (:_id doc)
                link-id (format "%s--%s-->%s" source-id ..rel.. ..target..)]
            (links/delete-link ..auth.. doc ..id.. ..rel.. ..target..) => ..deleted..
            (provided
              (couch/all-docs ..db.. [link-id]) => [..link..]
              (couch/delete-docs ..db.. [..link..]) => ..deleted..)))

        (fact "fails if not can? :update source"
          (links/delete-link ..auth.. ..doc.. ..id.. ..rel.. ..target..) => (throws Exception)
          (provided
            (auth/can? ..id.. :auth/update ..doc..) => false))


        (fact "updates entity _collaboration_roots"
          (let [source-id (:_id doc)
                link-id (format "%s--%s-->%s" source-id ..rel.. ..target..)]
            (links/delete-link ..auth.. doc ..id.. ..rel.. ..target..) => ..docs..
            (provided
              (couch/all-docs ..db.. [link-id]) => [..link..]
              (couch/delete-docs ..db.. [..link..]) => ..docs..)))))))
