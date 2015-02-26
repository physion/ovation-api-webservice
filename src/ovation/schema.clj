(ns ovation.schema
  (:import (us.physion.ovation.domain OvationEntity$AnnotationKeys)
           (us.physion.ovation.values Relation))
  (:require [ring.swagger.schema :refer [field describe]]
            [schema.core :as s]))

;;; --- Schema Definitions --- ;;;

(s/defschema Success {:success s/Bool})

(def AnnotationBase {:_id                    s/Str
                     :_rev                   s/Str
                     :user                   s/Str
                     :entity                 s/Str
                     (s/optional-key :links) s/Any})

(s/defschema AnnotationTypes (s/enum OvationEntity$AnnotationKeys/TAGS
                               OvationEntity$AnnotationKeys/PROPERTIES
                               OvationEntity$AnnotationKeys/NOTES
                               OvationEntity$AnnotationKeys/TIMELINE_EVENTS))

(s/defschema TagRecord {:tag s/Str})
(s/defschema TagAnnotation (conj AnnotationBase {:annotation_type OvationEntity$AnnotationKeys/TAGS
                                                 :annotation      TagRecord}))

(s/defschema PropertyRecord {:key   s/Str
                             :value (describe s/Str "(may be any JSON type)")})
(s/defschema PropertyAnnotation (conj AnnotationBase {:annotation_type OvationEntity$AnnotationKeys/PROPERTIES
                                                      :annotation      PropertyRecord}))

(s/defschema NoteRecord {:text      s/Str
                         :timestamp s/Str})
(s/defschema NoteAnnotation (conj AnnotationBase {:annotation_type OvationEntity$AnnotationKeys/NOTES
                                                  :annotation      NoteRecord}))

(s/defschema TimelineEventRecord {:name                 s/Str
                                  :notes                s/Str
                                  :start                s/Str
                                  (s/optional-key :end) s/Str})
(s/defschema TimelineEventAnnotation (conj AnnotationBase {:annotation_type OvationEntity$AnnotationKeys/TIMELINE_EVENTS
                                                           :annotation      TimelineEventRecord}))

(s/defschema NewAnnotation (describe (s/either TagRecord PropertyRecord NoteRecord TimelineEventRecord) "A new annotation record"))
(s/defschema Annotation (describe (s/either TagAnnotation PropertyAnnotation NoteAnnotation TimelineEventAnnotation) "An annotation"))
(s/defschema AnnotationCollection {s/Str                    ;; User URI
                                    [Annotation]})
(s/defschema AnnotationsMap {s/Str                          ;; Annotation Type
                              AnnotationCollection})
(s/defschema Link {:target_id                    s/Uuid
                   :rel                          s/Str
                   (s/optional-key :inverse_rel) s/Str})

(s/defschema NamedLink (assoc Link :name s/Str))

(s/defschema NewEntityLink {:target_id                    s/Str
                            (s/optional-key :inverse_rel) s/Str})

(s/defschema BaseEntity {:type                         s/Str    ;(s/enum :Project :Protocol :User :Source)
                         :_rev                         s/Str
                         :_id                          s/Uuid
                         :attributes                   {s/Keyword s/Any}
                         (s/optional-key :api_version) s/Int})


(s/defschema Entity (assoc BaseEntity
                      :links                        {s/Keyword s/Str}
                      (s/optional-key :named_links) {s/Keyword {s/Keyword s/Str}}
                      (s/optional-key :annotations) s/Any))

(s/defschema EntityUpdate BaseEntity)

(s/defschema NewEntity (assoc (dissoc Entity :_id :_rev :links :named_links)
                         (s/optional-key :links) {s/Keyword [NewEntityLink]}
                         (s/optional-key :named_links) {s/Keyword {s/Keyword [NewEntityLink]}}))



