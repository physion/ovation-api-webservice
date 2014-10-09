(ns ovation-rest.test.entity
  (:use midje.sweet)
  (:import (us.physion.ovation.domain DtoBuilder URIs)
           (java.util UUID))
  (:require [ovation-rest.util :as util]
            [ovation-rest.interop :refer [clojurify]]
            [ovation-rest.entity :as entity]
            [ovation-rest.context :as context]
            [ovation-rest.links :as links]))



(facts "About entities"
  (fact "inserts a new entity without links"
    (entity/create-entity ...api... {:type       "Project"
                                     :attributes {}}) => ...result...
    (provided
      (util/ctx ...api...) => ...ctx...
      (context/begin-transaction ...ctx...) => true
      (entity/insert-entity ...ctx... {"type"       "Project"
                                       "attributes" {}}) => ...entity...
      (context/commit-transaction ...ctx...) => true
      (util/into-seq '(...entity...)) => ...result...))

  (fact "inserts a new entity with links inside transaction"
    (entity/create-entity ...api... {:type       "Project"
                                     :attributes {}
                                     :links      {:my-rel [{:target_id   ...target...
                                                            :inverse_rel ...inverse...}]}}) => ...result...
    (provided
      (util/ctx ...api...) => ...ctx...
      (context/begin-transaction ...ctx...) => true
      (entity/insert-entity ...ctx... {"type"       "Project"
                                       "attributes" {}}) => ...entity...
      (util/create-uri ...target...) => ...uri...
      (links/add-link ...entity... "my-rel" ...uri... :inverse ...inverse...) => true
      (context/commit-transaction ...ctx...) => true
      (util/into-seq '(...entity...)) => ...result...))

  (fact "inserts a new entity with named links inside transaction"
    (entity/create-entity ...api... {:type        "Project"
                                     :attributes  {}
                                     :named_links {:my-rel {:my-name [{:target_id   ...target...
                                                                       :inverse_rel ...inverse...}]}}}) => ...result...
    (provided
      (util/ctx ...api...) => ...ctx...
      (context/begin-transaction ...ctx...) => true
      (entity/insert-entity ...ctx... {"type"       "Project"
                                       "attributes" {}}) => ...entity...
      (util/create-uri ...target...) => ...uri...
      (links/add-named-link ...entity... "my-rel" "my-name" ...uri... :inverse ...inverse...) => true
      (context/commit-transaction ...ctx...) => true
      (util/into-seq '(...entity...)) => ...result...))
  )

;(facts "about entity handlers"
;       (facts "index-resource"
;              (fact "gets projects"
;                    (entity/index-resource ...apikey... "projects" ...hosturl...) => ...result...
;                    (provided
;                      (util/ctx ...apikey...) => ...ctx...
;                      (#'ovation-rest.entity/get-projects ...ctx...) => ...entities...
;                      (util/into-seq ...entities... ...hosturl...) => ...result...)))
;       (facts "get-view"
;              (fact "gets view results"
;                    (entity/get-view ...apikey... ...requesturl... ...hosturl...) => ...result...
;                    (provided
;                      (util/ctx ...apikey...) => ...ctx...
;                      (util/to-ovation-uri ...requesturl... ...hosturl...) => ...viewuri...
;                      (#'ovation-rest.entity/get-view-results ...ctx... ...viewuri...) => ...entities...
;                      (util/into-seq ...entities... ...hosturl...) => ...result...))))
