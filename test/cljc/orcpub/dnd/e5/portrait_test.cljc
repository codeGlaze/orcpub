(ns orcpub.dnd.e5.portrait-test
  "Pure-fn tests for the paper-doll compositor. Everything here runs on
   both JVM (via lein test) and cljs — no DOM, no re-frame."
  (:require [clojure.test :refer [deftest testing is]]
            #?(:clj [clojure.java.io :as cio])
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.portrait-assets :as pa]))

;; ---------- registry ----------

(deftest layer-order-matches-taxonomy
  (testing "10 layers in illustrator's real z-order"
    (is (= [:hair-bits :hair-back :head :shirt :hair-front
            :ears :eyes :nose :mouth :bangs]
           pa/layer-order))
    (is (= (count pa/layer-order) (count pa/layer-labels)))
    (is (= (count pa/layer-order) (count pa/layer-colors)))
    (is (= (set pa/layer-order) (set (keys pa/color-slots)))
        "every layer has a color-slot mapping (nil allowed)")))

(deftest every-layer-has-at-least-one-asset
  (testing "the MVP feature is functional out of the box"
    (doseq [layer-key pa/layer-order]
      (is (pos? (pa/asset-count-for-layer layer-key))
          (str "layer " layer-key " has no assets")))))

(deftest asset-urls-point-into-the-portrait-tree
  (doseq [layer-key pa/layer-order
          asset (pa/assets-for-layer layer-key)]
    (is (re-find (re-pattern (str "^/image/portraits/" (name layer-key) "/[a-z0-9_]+\\.png$"))
                 (:asset/url asset))
        (str (:asset/id asset) " is not served from its layer directory: "
             (:asset/url asset)))))

(deftest every-asset-file-is-actually-on-disk
  (testing "a registry entry pointing at a missing file is an invisible layer"
    (doseq [layer-key pa/layer-order
            asset (pa/assets-for-layer layer-key)]
      (is (some? #?(:clj (cio/resource (str "public" (:asset/url asset)))
                    :cljs :skipped))
          (str "missing file for " (:asset/id asset) ": " (:asset/url asset))))))

(deftest asset-by-id-round-trips
  (doseq [layer-key pa/layer-order
          asset (pa/assets-for-layer layer-key)]
    (is (= asset (pa/asset-by-id layer-key (:asset/id asset)))
        (str "round-trip failed for " layer-key "/" (:asset/id asset)))))

(deftest asset-by-id-nil-for-unknown
  (is (nil? (pa/asset-by-id :head :no-such-id)))
  (is (nil? (pa/asset-by-id :not-a-real-layer :head-oval))))

(deftest artist-attribution-includes-only-selected
  (let [head-asset (first (pa/assets-for-layer :head))
        selection {:head {:artist/id (:artist/id pa/house-pack)
                          :asset/id  (:asset/id head-asset)}}]
    (is (= [(:artist/id pa/house-pack)]
           (pa/all-artists-for-layers selection)))))

(deftest artist-attribution-empty-when-nothing-selected
  (is (= [] (pa/all-artists-for-layers {})))
  (is (= [] (pa/all-artists-for-layers nil))))

(deftest artist-for-asset-finds-owner
  (let [head-asset (first (pa/assets-for-layer :head))]
    (is (= (:artist/id pa/house-pack)
           (pa/artist-for-asset :head (:asset/id head-asset)))))
  (is (nil? (pa/artist-for-asset :head :no-such-asset))))

;; ---------- seeded randomize ----------

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

;; ---------- character colors ----------

(deftest shade-hex-mixes-and-clamps
  (is (= "#ffffff" (pa/shade-hex "#000000" 100)) "full lighten → white")
  (is (= "#000000" (pa/shade-hex "#ffffff" -100)) "full darken → black")
  (is (= "#808080" (pa/shade-hex "#808080" 0)) "zero is identity")
  (is (= "#808080" (pa/shade-hex "#808080" nil)) "nil is identity")
  (is (= "#ffffff" (pa/shade-hex "#000000" 250)) "clamped to 100")
  (is (= "#808080" (pa/shade-hex "#000000" 50)) "50% toward white")
  (is (= "nope" (pa/shade-hex "nope" 20)) "non-hex passthrough")
  (is (nil? (pa/shade-hex nil 20)) "nil passthrough")
  (is (re-matches #"#[0-9a-f]{6}" (pa/shade-hex "#5c3a1e" 15)) "output stays #rrggbb"))

(deftest layers-in-slot-groups-by-slot
  (is (= [:hair-bits :hair-back :hair-front :bangs] (pa/layers-in-slot :hair)))
  (is (= [:head :ears :nose] (pa/layers-in-slot :skin)))
  (is (= [:eyes] (pa/layers-in-slot :eyes)))
  (is (= [:shirt] (pa/layers-in-slot :shirt)))
  (is (= [] (pa/layers-in-slot :no-such-slot))))

(deftest tint-for-precedence
  (let [base pa/empty-portrait]
    (testing "nothing set → category tint"
      (is (= (pa/layer-colors :bangs) (pa/tint-for base :bangs)))
      (is (= (pa/layer-colors :bangs) (pa/tint-for nil :bangs)) "nil portrait tolerated"))
    (testing "slot color paints every layer in the slot, nothing else"
      (let [p (assoc-in base [:colors :hair] "#112233")]
        (doseq [k (pa/layers-in-slot :hair)]
          (is (= "#112233" (pa/tint-for p k)) (str k)))
        (is (= (pa/layer-colors :head) (pa/tint-for p :head)))))
    (testing "shade applies to one piece against the slot base"
      (let [p (-> base
                  (assoc-in [:colors :hair] "#000000")
                  (assoc-in [:tweaks :bangs :shade] 100))]
        (is (= "#ffffff" (pa/tint-for p :bangs)))
        (is (= "#000000" (pa/tint-for p :hair-back)) "sibling piece keeps base")))
    (testing "shade with no slot color shades the category tint"
      (let [p (assoc-in base [:tweaks :bangs :shade] -100)]
        (is (= "#000000" (pa/tint-for p :bangs)))))
    (testing "override beats shade and base"
      (let [p (-> base
                  (assoc-in [:colors :hair] "#000000")
                  (assoc-in [:tweaks :bangs] {:shade 100 :override "#123456"}))]
        (is (= "#123456" (pa/tint-for p :bangs)))))
    (testing "mouth has no slot — always its category tint"
      (let [p (assoc-in base [:colors :skin] "#ff0000")]
        (is (= (pa/layer-colors :mouth) (pa/tint-for p :mouth)))))))

(deftest tweaked-layers-in-slot-counts-only-real-tweaks
  (let [p {:layers {} :colors {}
           :tweaks {:bangs     {:shade 10}
                    :hair-back {:shade 0}
                    :head      {:override "#abcdef"}
                    :ears      {}}}]
    (is (= [:bangs] (pa/tweaked-layers-in-slot p :hair)) "zero shade doesn't count")
    (is (= [:head] (pa/tweaked-layers-in-slot p :skin)) "empty tweak map doesn't count")
    (is (= [] (pa/tweaked-layers-in-slot p :eyes)))
    (is (= [] (pa/tweaked-layers-in-slot pa/empty-portrait :hair)))))

;; ---------- persistence (EDN string on the character) ----------

(deftest portrait-round-trips-through-edn-string
  (let [portrait {:layers {:head  {:artist/id :house-pack :asset/id :l2-head-01}
                           :bangs {:artist/id :house-pack :asset/id :l9-bangs-01}}
                  :colors {:hair "#5c3a1e"}
                  :tweaks {:bangs {:shade 20}}}
        stored (pr-str portrait)]
    (is (string? stored) "what Datomic receives is a plain string")
    (is (= portrait (char5e/parse-portrait stored)))
    ;; #5c3a1e shaded +20% toward white: 92→125, 58→97, 30→75
    (is (= "#7d614b" (pa/tint-for (char5e/parse-portrait stored) :bangs))
        "colors survive the round trip and still tint")))

(deftest parse-portrait-tolerates-maps-nil-and-garbage
  (is (nil? (char5e/parse-portrait nil)) "unset")
  (is (nil? (char5e/parse-portrait "")) "blank")
  (is (nil? (char5e/parse-portrait "   ")) "whitespace")
  (is (nil? (char5e/parse-portrait "{not edn")) "garbage")
  (is (nil? (char5e/parse-portrait "42")) "non-map edn")
  (is (nil? (char5e/parse-portrait 42)) "non-string non-map")
  (is (= {:layers {}} (char5e/parse-portrait {:layers {}}))
      "already-parsed map passes through"))
