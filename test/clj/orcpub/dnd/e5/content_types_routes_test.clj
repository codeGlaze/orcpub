(ns orcpub.dnd.e5.content-types-routes-test
  "Drift guard for the routes cycle-break: content_types now stores :route-kw as a plain
   keyword literal (so the registry is a pure-data leaf route_map can read to generate the
   bidi tree). This test — clj-only, since it resolves route_map vars — asserts each literal
   still equals the corresponding route_map var, so they can't silently diverge."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.content-types :as ct]
            [orcpub.route-map :as route-map]
            [orcpub.routes :as routes]
            [orcpub.dnd.e5.page-map]))

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

;; The page-map is now GENERATED from this registry by orcpub.dnd.e5.page-map/builder-pages, a
;; compile-time macro (see that ns for why "a view fn can't be derived from data in cljs" was only
;; half true). A registry entry whose view fn is missing is a COMPILE error in core.cljs, which is
;; a stronger guard than any test here — this just pins the shape the macro emits.
(deftest builder-pages-macro-covers-every-registered-type
  (let [m (macroexpand-1 '(orcpub.dnd.e5.page-map/builder-pages))]
    (testing "one binding per registry entry, keyed by :route-kw"
      (is (= (set (map :route-kw ct/content-types)) (set (keys m))))
      (is (= (count ct/content-types) (count m))))
    (testing "each value is the conventionally-named view fn for that :route-seg"
      (doseq [{:keys [route-kw route-seg]} ct/content-types]
        (is (= (symbol "orcpub.dnd.e5.views" (str route-seg "-page")) (get m route-kw))
            (str route-kw " must bind to views/" route-seg "-page"))))))

;; The macro emits `views/<route-seg>-page` whether or not that fn exists — cljs reports an
;; undeclared var as a WARNING, and the build still succeeds (verified by adding a bogus registry
;; entry and compiling). So the hard guard lives here: read views.cljs as text from the JVM, the
;; same tactic builder-items-match-the-subs uses for a cljs loop we can't run in CI.
(deftest every-registered-type-has-a-builder-page-view
  (let [views (slurp "src/cljs/orcpub/dnd/e5/views.cljs")]
    (doseq [{:keys [id route-seg]} ct/content-types]
      (let [fn-name (str route-seg "-page")
            ;; boolean, not the match: an `is` on re-find prints the ENTIRE file as `actual`
            found?  (boolean (re-find (re-pattern (str "\\(defn\\s+" fn-name "\\s")) views))]
        (is found?
            (str id ": views.cljs must define (defn " fn-name " ...) — the page-map macro emits a"
                 " reference to it, and a missing one is only a cljs warning, not an error"))))))
