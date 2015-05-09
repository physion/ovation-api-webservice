(ns ovation.test.transform
  (:use midje.sweet)
  (:require [ovation.transform :as t]
            [ovation.version :refer [version]]
            [ovation.util :as util]))


(facts "About annotation links"
  (fact "adds annotation links to entity"
    (t/add-annotation-links {:_id   "123"
                             :links {:foo "bar"}}) => {:_id   "123"
                                                       :links {:foo             "bar"
                                                               :properties      "/api/v1/entities/123/annotations/properties"
                                                               :tags            "/api/v1/entities/123/annotations/tags"
                                                               :notes           "/api/v1/entities/123/annotations/notes"
                                                               :timeline-events "/api/v1/entities/123/annotations/timeline-events"}}))

(facts "About DTO link modifications"
  (fact "`remove-hidden-links` removes '_...' links"
    (t/remove-private-links {:_id   ...id...
                             :links {"_collaboration_links" #{...hidden...}
                                     :link1                 ...link1...}}) => {:_id   ...id...
                                                                               :links {:link1 ...link1...}})

  (fact "`links-to-rel-path` updates links to API relative path"
    (let [couch {:_id   ..id..
                 :links {:link1 "ovation://blahblah"
                         :link2 "ovation://blablah/blah"}}]
      (t/links-to-rel-path couch) => {:_id   ..id..
                                      :named_links {}
                                      :links {:link1 (util/join-path ["/api" version "entities" ..id.. "links" "link1"])
                                              :link2 (util/join-path ["/api" version "entities" ..id.. "links" "link2"])}}))
  (fact "`add-self-link` adds self link"
    (let [couch {:_id   ..id..
                 :links {}}]
      (t/add-self-link couch) => {:_id   ..id..
                                  :links {:self (util/join-path ["/api" version "entities" ..id..])}}))

  (fact "`links-to-rel-path` updates named links to relative path"
    (let [couch {:_id         ..id..
                 :named_links {:link1 {:name1 "ovation://blahblah"
                                       :name2 "ovation://blablah/blah"}}}
          ]
      (t/links-to-rel-path couch) => {:_id         ..id..
                                      :links {}
                                      :named_links {:link1 {:name1 (str (util/join-path ["/api" version "entities" ..id.. "links" "link1"]) "?name=name1")
                                                            :name2 (str (util/join-path ["/api" version "entities" ..id.. "links" "link1"]) "?name=name2")}}})))

(facts "About doc-to-couch"
  (fact "adds collaboration roots"
    (let [doc {:type ..type.. :attributes {:label ..label..}}]
      (t/doc-to-couch ..owner.. ..roots.. doc) =contains=> (assoc-in doc [:links :_collaboration_roots] ..roots..)))

  (fact "adds owner element"
    (let [doc {:type ..type.. :attributes {:label ..label..}}]
      (t/add-owner doc ..owner..) => (assoc doc :owner ..owner..))))

