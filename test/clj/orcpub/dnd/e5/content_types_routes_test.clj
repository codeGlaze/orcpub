(ns orcpub.dnd.e5.content-types-routes-test
  "Drift guard for the routes cycle-break: content_types now stores :route-kw as a plain
   keyword literal (so the registry is a pure-data leaf route_map can read to generate the
   bidi tree). This test — clj-only, since it resolves route_map vars — asserts each literal
   still equals the corresponding route_map var, so they can't silently diverge."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.content-types :as ct]
            [orcpub.route-map :as route-map]
            [orcpub.routes :as routes]))

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

(deftest my-content-route-set-is-generated-correctly
  (testing "my-content holds every builder EXCEPT spell/monster/encounter"
    (let [s route-map/dnd-e5-my-content-routes]
      (is (contains? s route-map/dnd-e5-my-content-route))
      (doseq [{:keys [id route-kw]} ct/content-types]
        (if (#{:spell :monster :encounter} id)
          (is (not (contains? s route-kw)) (str id " must NOT be in my-content"))
          (is (contains? s route-kw) (str id " must be in my-content")))))))

(deftest every-builder-is-allow-listed-to-serve-the-spa
  (testing "index-page-paths (the SPA allowlist) includes every registry builder route"
    (let [allowed (set (map first routes/index-page-paths))]
      (doseq [{:keys [id route-kw]} ct/content-types]
        (is (contains? allowed route-kw)
            (str id " (" route-kw ") must be allow-listed in index-page-paths"))))))

;; The page-map (web/cljs/orcpub/core.cljs) binds a route to a VIEW FN, and it is the one part of
;; the wiring the registry cannot generate — a view fn is not derivable from data in cljs (D-note in
;; the framework doc). So a type can be fully registered, route resolvably, and still land on a
;; blank page. Read as TEXT because this is clj and the page-map is cljs; same tactic as
;; builder-items-match-the-subs, which guards a cljs loop from the JVM.
(deftest every-registered-builder-route-is-bound-to-a-view
  (let [core (slurp "web/cljs/orcpub/core.cljs")]
    (doseq [{:keys [id route-seg]} ct/content-types]
      (let [var-name (str "dnd-e5-" route-seg "-page-route")]
        (is (re-find (re-pattern (str "routes/" var-name "\\s+views")) core)
            (str id ": web/cljs/orcpub/core.cljs must bind routes/" var-name
                 " to a view fn, or the builder page renders blank"))))))
