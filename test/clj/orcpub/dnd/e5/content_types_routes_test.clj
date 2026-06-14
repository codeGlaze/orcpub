(ns orcpub.dnd.e5.content-types-routes-test
  "Drift guard for the routes cycle-break: content_types now stores :route-kw as a plain
   keyword literal (so the registry is a pure-data leaf route_map can read to generate the
   bidi tree). This test — clj-only, since it resolves route_map vars — asserts each literal
   still equals the corresponding route_map var, so they can't silently diverge."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.content-types :as ct]
            [orcpub.route-map :as route-map]))

(deftest route-kw-literals-match-route-map-vars
  (testing "each registry :route-kw equals route_map's dnd-e5-<route-seg>-page-route var"
    (doseq [{:keys [id route-seg route-kw]} ct/content-types]
      (let [var-sym (symbol "orcpub.route-map" (str "dnd-e5-" route-seg "-page-route"))
            v       (ns-resolve 'orcpub.route-map (symbol (str "dnd-e5-" route-seg "-page-route")))]
        (is (some? v) (str id ": route_map var " var-sym " must exist"))
        (when v
          (is (= route-kw (var-get v))
              (str id ": :route-kw " route-kw " must equal " var-sym)))))))

(deftest builder-routes-resolve-through-the-generated-bidi-tree
  (testing "each registry builder URL resolves to its route-kw (generated bidi segments work)"
    (doseq [{:keys [id route-seg route-kw]} ct/content-types]
      (let [m (route-map/match-route (str "/pages/dnd/5e/" route-seg))]
        (is (= route-kw (:handler m))
            (str id ": /pages/dnd/5e/" route-seg " must resolve to " route-kw))))))
