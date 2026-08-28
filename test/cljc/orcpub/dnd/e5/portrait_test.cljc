(ns orcpub.dnd.e5.portrait-test
  "Pure-fn tests for the paper-doll compositor. Everything here runs on
   both JVM (via lein test) and cljs — no DOM, no re-frame."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.portrait-assets :as pa]))

;; ---------- portrait-assets registry ----------

(deftest layer-order-matches-taxonomy
  (testing "10 layers in illustrator's real z-order"
    (is (= [:hair-bits :hair-back :head :shirt :hair-front
            :ears :eyes :nose :mouth :bangs]
           pa/layer-order))
    (is (= (count pa/layer-order) (count pa/layer-labels)))
    (is (= (count pa/layer-order) (count pa/layer-colors)))))

(deftest every-layer-has-at-least-one-placeholder-asset
  (testing "the MVP feature is functional out of the box"
    (doseq [layer-key pa/layer-order]
      (is (pos? (pa/asset-count-for-layer layer-key))
          (str "layer " layer-key " has no placeholder assets")))))

(deftest asset-by-id-round-trips
  (testing "every placeholder asset can be looked up by id"
    (doseq [layer-key pa/layer-order
            asset (pa/assets-for-layer layer-key)]
      (is (= asset (pa/asset-by-id layer-key (:asset/id asset)))
          (str "round-trip failed for " layer-key "/" (:asset/id asset))))))

(deftest asset-by-id-nil-for-unknown
  (is (nil? (pa/asset-by-id :head :no-such-id)))
  (is (nil? (pa/asset-by-id :not-a-real-layer :head-oval))))

(deftest artist-attribution-includes-only-selected
  (let [head-asset (first (pa/assets-for-layer :head))
        selection {:head {:artist/id (:artist/id pa/placeholder-pack)
                          :asset/id  (:asset/id head-asset)}}]
    (is (= [(:artist/id pa/placeholder-pack)]
           (pa/all-artists-for-layers selection)))))

(deftest artist-attribution-empty-when-nothing-selected
  (is (= [] (pa/all-artists-for-layers {})))
  (is (= [] (pa/all-artists-for-layers nil))))

(deftest artist-for-asset-finds-owner
  (let [head-asset (first (pa/assets-for-layer :head))]
    (is (= (:artist/id pa/placeholder-pack)
           (pa/artist-for-asset :head (:asset/id head-asset)))))
  (is (nil? (pa/artist-for-asset :head :no-such-asset))))

;; ---------- compositor pure helpers (portrait.cljs) ----------
;; portrait.cljs is cljs-only, but the pure fns (seed hashing, mulberry,
;; compose-for-seed) are written to work cljc-shape. Test the ones we can
;; reach without pulling in the reagent-facing components.

;; Pure helpers live in portrait-assets (cljc), reachable from both platforms.

(deftest seed->int-is-deterministic
  (is (= (pa/seed->int "abc") (pa/seed->int "abc")))
  (is (not= (pa/seed->int "abc") (pa/seed->int "abd")))
  (is (integer? (pa/seed->int "abc"))))

(deftest mulberry32-yields-numbers-in-range
  (let [rand-fn (pa/mulberry32 42)]
    (dotimes [_ 20]
      (let [x (rand-fn)]
        (is (<= 0 x))
        (is (< x 1))))))

(deftest mulberry32-is-deterministic
  (let [seq1 (let [r (pa/mulberry32 12345)] (vec (repeatedly 8 r)))
        seq2 (let [r (pa/mulberry32 12345)] (vec (repeatedly 8 r)))
        seq3 (let [r (pa/mulberry32 12346)] (vec (repeatedly 8 r)))]
    (is (= seq1 seq2))
    (is (not= seq1 seq3))))

(deftest compose-for-seed-is-deterministic
  (let [c1 (pa/compose-for-seed "test-seed")
        c2 (pa/compose-for-seed "test-seed")
        c3 (pa/compose-for-seed "different-seed")]
    (is (= c1 c2) "same seed → same composition")
    (is (not= c1 c3) "different seed → different composition")))

(deftest compose-for-seed-covers-every-populated-layer
  (let [composition (pa/compose-for-seed "coverage-check")]
    (doseq [layer-key pa/layer-order]
      (when (pos? (pa/asset-count-for-layer layer-key))
        (is (contains? composition layer-key)
            (str layer-key " missing from random composition"))))))

(deftest compose-for-seed-picks-valid-assets
  (let [composition (pa/compose-for-seed "validity-check")]
    (doseq [[layer-key {:keys [:asset/id]}] composition]
      (is (pa/asset-by-id layer-key id)
          (str "compose picked unknown asset " id " for " layer-key)))))
