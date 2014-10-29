(ns ovation-rest.test.links
  (:use midje.sweet)
  (:import (us.physion.ovation.domain DtoBuilder URIs)
           (java.util UUID))
  (:require [ovation-rest.links :as links]
            [ovation-rest.util :as util]
            [ovation-rest.entity :as entity]))


(facts "About links"
  (fact "GET /links/:rel returns links from entity"
    (links/get-link ...apikey... ...entity... ...rel...) => ...links...
    (provided
      (util/get-entity ...apikey... ...entity...) => ...entity...
      (links/get-entities ...entity... ...rel...) => ...links...
      (util/into-seq (into [] ...links...)) => ...links...))

  (fact "POST /links adds a link with inverse"
    (links/create-link ...api... ...id... {:target_id   ...target...
                                           :rel         ...rel...
                                           :inverse_rel ...inverse...}) => [...entity...]
    (provided
      (util/get-entity ...api... ...id...) => ...entity...
      (links/add-link ...entity... ...rel... ...target... :inverse ...inverse...) => true))

  (fact "POST /links adds a link without inverse"
    (links/create-link ...api... ...id... {:target_id ...target...
                                           :rel       ...rel...}) => [...entity...]
    (provided
      (util/get-entity ...api... ...id...) => ...entity...
      (links/add-link ...entity... ...rel... ...target... :inverse nil) => true))


  (fact "DELETE /links/:rel/:target deletes a link"
    (links/delete-link ...api... ...id... ...rel... ...target...) => {:success true}
    (provided
      (util/get-entity ...api... ...id...) => ...entity...
      (links/remove-link ...entity... ...rel... ...target...) => true)))


(facts "About named_links"
  (fact "GET /named_links/:rel/:name returns named links from entity"
    (links/get-named-link ...api... ...id... ...rel... ...named...) => ...entities...
    (provided
      (util/get-entity ...api... ...id...) => ...entity...
      (links/get-named-entities ...entity... ...rel... ...named...) => ...links...
      (util/into-seq (into [] ...links...)) => ...entities...))

  (fact "POST /named_links adds a link with inverse"
    (links/create-named-link ...api... ...id... {:target_id   ...target...
                                                 :inverse_rel ...inverse...
                                                 :rel         ...rel...
                                                 :name        ...name...}) => [...entity...]
    (provided
      (util/get-entity ...api... ...id...) => ...entity...
      (links/add-named-link ...entity... ...rel... ...name... ...target... :inverse ...inverse...) => true))

  (fact "DELETE /named_links/:rel/:name/:target deletes a link"
    (links/delete-named-link ...api... ...id... ...rel... ...named... ...target...) => {:success true}
    (provided
      (util/get-entity ...api... ...id...) => ...entity...
      (links/remove-named-link ...entity... ...rel... ...named... ...target...) => true)))
