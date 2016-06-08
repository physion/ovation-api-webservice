(ns ovation.test.search
  (:require [midje.sweet :refer :all]
            [ovation.search :as search]
            [ovation.couch :as couch]
            [ovation.constants :as k]
            [ovation.core :as core]))


(facts "About search"
  (fact "transforms Cloudant search"
    (search/search ..auth.. ..rt.. ..q..) => {:search_results [..result1.. ..result2..]
                                              :metadata       {:bookmark ..bookmark..
                                                               :total_rows ..total..}}
    (provided
      (couch/db ..auth..) => ..db..
      (search/get-results ..auth.. ..rt.. [{:id     ..id1..
                                            :order  [3.9 107]
                                            :fields {:id   ..id1..
                                                     :type k/PROJECT-TYPE}}
                                           {:id     ..id2..
                                            :order  [3.9 107]
                                            :fields {:id   ..id2..
                                                     :type k/REVISION-TYPE}}]) => [..result1.. ..result2..]
      (couch/search ..db.. ..q.. :bookmark nil) => {:total_rows ..total..
                                                    :bookmark   ..bookmark..
                                                    :rows       [{:id     ..id1..
                                                                  :order  [3.9 107]
                                                                  :fields {:id   ..id1..
                                                                           :type k/PROJECT-TYPE}}
                                                                 {:id     ..id2..
                                                                  :order  [3.9 107]
                                                                  :fields {:id   ..id2..
                                                                           :type k/REVISION-TYPE}}]}))

  (fact "Extracts entity ids"
    (let [rows [{:id     ..id1..
                 :order  [3.9 107]
                 :fields {:id   ..id1..
                          :type k/PROJECT-TYPE}}
                {:id     ..id2..
                 :order  [3.9 107]
                 :fields {:id     ..id2..
                          :entity ..eid..
                          :type   k/ANNOTATION-TYPE}}]]
      (search/get-results ..auth.. ..rt.. rows) => [{:id ..eid.. :entity_type k/PROJECT-TYPE :name ..project.. :breadcrumbs ..bc1..}
                                                    {:id ..id1.. :entity_type k/FILE-TYPE :name ..file.. :breadcrumbs ..bc2..}]
      (provided
        (ovation.breadcrumbs/get-breadcrumbs ..auth.. ..rt.. [..eid.. ..id1..]) => {..eid.. ..bc1..
                                                                                    ..id1.. ..bc2..}
        (search/entity-ids rows) => [..eid.. ..id1..]
        (core/get-entities ..auth.. [..eid.. ..id1..] ..rt..) => [{:_id ..eid..
                                                                   :type k/PROJECT-TYPE
                                                                   :attributes {:name ..project..}}
                                                                  {:_id ..id1..
                                                                   :type k/FILE-TYPE
                                                                   :attributes {:name ..file..}}])))

  (fact "Gets entity ID from annotations"
    (search/entity-ids [{:id     ..id1..
                         :order  [3.9 107]
                         :fields {:id   ..id1..
                                  :type k/PROJECT-TYPE}}
                        {:id     ..id2..
                         :order  [3.9 107]
                         :fields {:id     ..eid..
                                  :type   k/ANNOTATION-TYPE}}]) => [..id1.. ..eid..]))

