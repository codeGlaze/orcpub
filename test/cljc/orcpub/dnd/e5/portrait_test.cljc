(ns orcpub.dnd.e5.portrait-test
  "Pure-fn tests for the paper-doll compositor. Everything here runs on
   both JVM (via lein test) and cljs — no DOM, no re-frame."
  (:require [clojure.test :refer [deftest testing is are]]
            #?(:clj [clojure.java.io :as cio])
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.portrait-assets :as pa]))

;; ---------- registry ----------

(deftest layer-order-matches-taxonomy
  (testing "the illustrator's real z-order, plus :scalp -- a blob between the
            head and the hair that takes the hair colour, because some
            hair-front and bangs pairs do not meet and leave an island of skin
            on the crown. It is not one of her pieces, so it is not in the
            taxonomy; it is in the stack."
    (is (= [:hair-bits :hair-back :head :scalp :shirt :hair-front
            :ears :eyes :nose :mouth :bangs]
           pa/layer-order))
    (is (= [:hair-bits :hair-back :head :shirt :hair-front
            :ears :eyes :nose :mouth :bangs]
           pa/pickable-layers)
        "and it is not offered in the picker: one option that changes nothing")
    (is (= #{:scalp} pa/hidden-layers))
    (testing "it sits ABOVE the head and BELOW every hair layer, or it cannot
              do its job"
      (let [idx #(.indexOf ^java.util.List pa/layer-order %)]
        (is (< (idx :head) (idx :scalp)))
        (doseq [hair [:hair-front :bangs]]
          (is (< (idx :scalp) (idx hair)) (str "scalp must be under " hair)))))
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
  (is (= [:hair-bits :hair-back :hair-front :bangs] (pa/layers-in-slot :hair))
      "the scalp draws from the hair slot but is not LISTED as a hair piece --
       the list drives a per-piece tweak panel, and there is nothing to tweak")
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

(deftest artist-is-resolved-from-the-asset-not-the-saved-claim
  (testing "a stored portrait is client-supplied EDN; dropping :artist/id from
            it must not detach the credit from art that is in the registry"
    (let [asset (first (pa/assets-for-layer :head))
          honest {:head {:artist/id (:artist/id pa/house-pack)
                         :asset/id (:asset/id asset)}}
          stripped {:head {:asset/id (:asset/id asset)}}]
      (is (= (pa/all-artists-for-layers honest)
             (pa/all-artists-for-layers stripped))
          "same artists either way")
      (is (seq (pa/all-artists-for-layers stripped))))))

(deftest a-forged-artist-claim-does-not-win
  (testing "naming somebody else in the saved portrait does not reassign the
            work; the registry decides who drew an asset"
    (let [asset (first (pa/assets-for-layer :head))
          forged {:head {:artist/id :somebody-else
                         :asset/id (:asset/id asset)}}]
      (is (= [(:artist/id pa/house-pack)] (pa/all-artists-for-layers forged))))))

;; ---------- gaps: pieces marked planned-but-not-drawn ----------

(deftest gaps-default-to-nothing-rather-than-blowing-up
  (doseq [layer-key pa/layer-order]
    (is (vector? (pa/gaps-for-layer layer-key))
        (str layer-key " must answer with a collection, not nil")))
  (is (= [] (pa/gaps-for-layer :no-such-layer))))

(deftest any-gaps?-tracks-the-inventory
  (is (= (boolean (some (comp seq val) pa/gap-inventory)) (pa/any-gaps?))))

(deftest a-gap-never-collides-with-a-real-asset
  (testing "a gap id matching a drawn asset would put a 'coming' swatch next
            to the finished piece it refers to"
    (doseq [[layer-key gaps] pa/gap-inventory
            {:keys [:gap/id]} gaps]
      (is (nil? (pa/asset-by-id layer-key id))
          (str id " is listed as not-yet-drawn but exists in " layer-key)))))

(deftest gaps-are-shaped-the-way-the-picker-reads-them
  (doseq [[layer-key gaps] pa/gap-inventory
          gap gaps]
    (is (some #{layer-key} pa/layer-order)
        (str layer-key " is not a known layer"))
    (is (keyword? (:gap/id gap)) (str "gap needs an id: " (pr-str gap)))
    (is (or (nil? (:gap/label gap)) (string? (:gap/label gap)))
        (str "gap label must be a string when present: " (pr-str gap)))))

;; ---------- malformed stored portraits (PR #36 review, P1) ----------
;;
;; A portrait is client-supplied EDN. parse-portrait checked only that the
;; OUTER value was a map, so `{:layers "abc"}` passed and the non-map :layers
;; reached credit-line, which walks it as a selection. Three of four malformed
;; shapes threw, and character-page calls credit-line unguarded, so a crafted
;; save took the PUBLIC character page down with a 500.

(deftest malformed-sub-values-are-dropped-not-passed-through
  (are [stored kept] (= kept (:layers (char5e/parse-portrait stored)))
    "{:layers \"abc\"}"      nil
    "{:layers [1 2 3]}"     nil
    "{:layers 7}"           nil
    "{:layers nil}"         nil
    "{:layers {:head {}}}"  {:head {}})
  (testing ":colors and :tweaks get the same treatment"
    (is (nil? (:colors (char5e/parse-portrait "{:colors \"red\"}"))))
    (is (nil? (:tweaks (char5e/parse-portrait "{:tweaks 3}"))))))

(deftest credit-line-never-throws-on-a-malformed-portrait
  (doseq [stored ["{:layers \"abc\"}" "{:layers [1 2 3]}" "{:layers 7}"
                  "{:layers {:head 42}}" "{}" "not-edn-at-all"]]
    (is (nil? (pa/credit-line (char5e/parse-portrait stored)))
        (str "threw or credited something for " stored))))

(deftest all-artists-tolerates-a-non-map-selection
  (testing "public, and reached from cljs too, so it guards independently of
            parse-portrait"
    (are [sel] (= [] (pa/all-artists-for-layers sel))
      "abc" [1 2 3] 7 nil)))

;; ---------- drawable vs merely non-empty (PR #36 review, P2) ----------

(deftest drawable?-needs-an-asset-that-actually-resolves
  (let [real (first (pa/assets-for-layer :head))]
    (is (true? (pa/drawable? {:layers {:head {:asset/id (:asset/id real)}}})))
    (testing "a selection naming only unknown ids is non-empty and draws nothing --
              `seq` cannot tell the difference, which is what advertised a share
              image the renderer then 404ed on"
      (is (seq {:head {:asset/id :no-such-asset}}) "non-empty, to be clear")
      (is (false? (pa/drawable? {:layers {:head {:asset/id :no-such-asset}}}))))
    (is (false? (pa/drawable? {:layers {}})))
    (is (false? (pa/drawable? nil)))
    (testing "one good layer among unknowns is still drawable"
      (is (true? (pa/drawable? {:layers {:head {:asset/id (:asset/id real)}
                                         :shirt {:asset/id :nope}}}))))))

;; ---------------------------------------------------------------------------
;; The lips slot, which is the first slot that belongs to an ASSET rather than
;; to a layer. The mouth layer holds a closed line, a pair of lips and a set of
;; teeth; they want three different things, and a per-layer rule can only say
;; one. All three are drawn in greys, so the art cannot be asked either.
;; ---------------------------------------------------------------------------

(deftest the-slot-comes-from-the-asset-not-the-layer
  (testing "the lipped mouth names :lips; its siblings in the same layer name
            nothing, so the layer itself cannot be the unit"
    (let [lips (pa/asset-by-id :mouth :l8-mouth-02)
          smile (pa/asset-by-id :mouth :l8-mouth-03)
          plain (pa/asset-by-id :mouth :l8-mouth-01)]
      (is (= :lips (pa/slot-for-asset :mouth lips)))
      (is (nil? (pa/slot-for-asset :mouth smile)) "teeth are not lips")
      (is (nil? (pa/slot-for-asset :mouth plain)))
      (is (nil? (pa/color-slots :mouth))
          "and the LAYER still names nothing, which is the point")))
  (testing "a layer's own slot is still used when the asset is silent"
    (is (= :hair (pa/slot-for-asset :bangs (pa/asset-by-id :bangs
                                                           (:asset/id (first (pa/assets-for-layer :bangs)))))))))

(deftest the-lips-slot-only-appears-when-lips-do
  (let [with-lips {:layers {:mouth {:asset/id :l8-mouth-02}} :colors {} :tweaks {}}
        with-smile {:layers {:mouth {:asset/id :l8-mouth-03}} :colors {} :tweaks {}}
        empty-p {:layers {} :colors {} :tweaks {}}]
    (testing "a Lips swatch against a closed mouth would be a control that
              tints nothing, so it is not shown"
      (is (contains? (set (pa/active-color-slots with-lips)) :lips))
      (is (not (contains? (set (pa/active-color-slots with-smile)) :lips)))
      (is (not (contains? (set (pa/active-color-slots empty-p)) :lips))))
    (testing "and the unconditional slots are always there"
      (doseq [p [with-lips with-smile empty-p]]
        (is (= [:hair :skin :eyes :shirt]
               (vec (remove #{:lips} (pa/active-color-slots p)))))))
    (testing "the mouth shows up as a Lips piece only while the lips are on"
      (is (= [:mouth] (pa/layers-in-slot with-lips :lips)))
      (is (empty? (pa/layers-in-slot with-smile :lips))))))

(deftest lips-take-the-lip-colour-and-teeth-do-not
  (let [with-lips {:layers {:mouth {:asset/id :l8-mouth-02}}
                   :colors {:lips "#9b3c55" :skin "#ff0000"} :tweaks {}}
        with-smile {:layers {:mouth {:asset/id :l8-mouth-03}}
                    :colors {:lips "#9b3c55" :skin "#ff0000"} :tweaks {}}]
    (is (= "#9b3c55" (pa/tint-for with-lips :mouth)))
    (is (= (pa/layer-colors :mouth) (pa/tint-for with-smile :mouth))
        "the smile ignores the lip colour entirely")
    (testing "and a lipped mouth with nothing picked still gets a lip colour
              rather than rendering in the greys it was drawn in"
      (is (= (pa/default-slot-colors :lips)
             (pa/tint-for (assoc with-lips :colors {}) :mouth))))))

(deftest how-each-asset-wants-to-be-tinted
  (testing "three modes, because masking flattens line art, colorizing is for
            art drawn as a hueless ramp, and teeth want neither"
    (is (= :colorize (pa/render-mode :mouth (pa/asset-by-id :mouth :l8-mouth-02))))
    (is (= :as-drawn (pa/render-mode :mouth (pa/asset-by-id :mouth :l8-mouth-03))))
    (is (= :as-drawn (pa/render-mode :mouth (pa/asset-by-id :mouth :l8-mouth-01))))
    (is (= :colorize (pa/render-mode :eyes (first (pa/assets-for-layer :eyes))))
        "the irises are a hueless ramp too")
    (is (= :multiply (pa/render-mode :bangs (first (pa/assets-for-layer :bangs))))
        "ordinary line art multiplies")))

(deftest the-gamma-runs-opposite-ways-for-lips-and-eyes
  (testing "not a preference: the irises are drawn near-black and the lips
            light, so a gamma that lifts one pushes the other past white.
            Measured on the real art in portrait-real-art-compare-test."
    (let [lips-g (pa/tint-gamma :mouth (pa/asset-by-id :mouth :l8-mouth-02))
          eye-g (pa/tint-gamma :eyes (first (pa/assets-for-layer :eyes)))]
      (is (> lips-g 1.0) "lips are pulled DOWN into the colour")
      (is (< eye-g 1.0) "irises are lifted UP off the floor")))
  (testing "and an asset can override it, which is what a style with unusually
            small irises needs"
    (is (= 0.35 (pa/tint-gamma :eyes {:asset/slot :eyes :asset/gamma 0.35})))))

;; ---------------------------------------------------------------------------
;; Attribution
;; ---------------------------------------------------------------------------

(deftest the-artist-is-credited
  (let [p {:layers (pa/compose-for-seed "credit-check") :colors {} :tweaks {}}]
    (is (= "Art: Fusspot" (pa/credit-line p))
        "a composed portrait names the illustrator who drew it")
    (is (= "https://fusspot.rip/"
           (:artist/link (first (pa/artists-for-layers (:layers p)))))
        "and carries her link, so a surface can make the credit clickable")
    (is (nil? (pa/credit-line {:layers {}}))
        "nothing composed, nothing to credit")))

(deftest a-deployment-can-restate-the-credit-but-not-reassign-the-art
  (try
    (pa/set-artist-overrides! {:house-pack {:artist/name "Someone Else"
                                            :artist/link "https://example.test/"
                                            :artist/id   :not-this-one
                                            :artist/layers {}}})
    (let [p {:layers (pa/compose-for-seed "credit-check")}]
      (is (= "Art: Someone Else" (pa/credit-line p))
          "the name a deployment supplies is the one that renders")
      (let [a (first (pa/artists-for-layers (:layers p)))]
        (is (= "https://example.test/" (:artist/link a)))
        (testing "but the structure is not overridable -- only name, link and
                  licence are honoured, so a config cannot quietly re-attribute
                  someone's art to a different pack or empty their asset list"
          (is (= :house-pack (:artist/id a)))
          (is (seq (get-in a [:artist/layers :head]))))))
    (finally
      (pa/set-artist-overrides! {})))
  (testing "and clearing the overrides restores the public default"
    (is (= "Art: Fusspot" (pa/credit-line {:layers (pa/compose-for-seed "credit-check")})))))

(deftest every-artist-on-canvas-is-credited
  (testing "attribution resolves per ASSET, so a portrait mixing two artists'
            pieces names both -- which is what lets more illustrators join"
    (let [layers (pa/compose-for-seed "credit-check")]
      (is (= [:house-pack] (pa/all-artists-for-layers layers)))
      (is (= "Art: A, B" (pa/format-credit ["A" "B"]))
          "several names join into one line"))))

(deftest the-artist-links-are-drawer-only-and-overridable
  (let [a (pa/artist-info :house-pack)]
    (is (= "https://fusspot.rip/" (:artist/link a))
        "one link for surfaces that hold one -- her homepage, the list she
         maintains, so it cannot go stale the way a copied handle can")
    (is (= ["Site" "Twitch"] (mapv :link/label (:artist/links a)))
        "the two she asked for. Twitch is second rather than last because it
         is where she actually works")
    (is (every? #(re-find #"^https://" (:link/url %)) (:artist/links a))
        "every link is https")
    (is (not-any? #(re-find #"(?i)m\.twitch\.tv" (:link/url %)) (:artist/links a))
        "the canonical twitch host, not the mobile one she happened to paste")
    (testing "each link names a mark that exists. An icon-only link with a
              missing file renders as a blank gap -- the link still works and
              nothing errors, so only a check like this notices."
      (doseq [{:link/keys [icon label]} (:artist/links a)]
        (is (some? icon) (str label " has no icon"))
        (is (.exists (java.io.File.
                      (str "resources/public/image/social/" icon ".svg")))
            (str "/image/social/" icon ".svg is missing"))))
    (testing "and every link keeps its label, which is the accessible name --
              an icon with no text is unreadable to a screen reader"
      (is (every? (comp seq :link/label) (:artist/links a)))))
  (testing "the credit LINE carries no links -- it is the string burned into
            share cards and PDFs, where a URL cannot be followed anyway"
    (is (= "Art: Fusspot"
           (pa/credit-line {:layers (pa/compose-for-seed "links-check")}))))
  (testing "a deployment can change them, or drop them, without touching code"
    (try
      (pa/set-artist-overrides!
       {:house-pack {:artist/links [{:link/label "Shop" :link/url "https://example.test/"}]}})
      (is (= ["Shop"] (mapv :link/label (:artist/links (pa/artist-info :house-pack)))))
      (pa/set-artist-overrides! {:house-pack {:artist/links nil}})
      (is (nil? (:artist/links (pa/artist-info :house-pack)))
          "nil removes them rather than falling back to the default")
      (finally (pa/set-artist-overrides! {})))))

(deftest credit-marks-flank-the-name-only-when-they-can-balance
  (let [ls (fn [n] (mapv #(hash-map :link/url (str "https://example.test/" %)) (range n)))
        shape (fn [n] (let [{:keys [left right below]} (pa/credit-mark-layout (ls n))]
                        [(count left) (count right) (count below)]))]
    (testing "an even count up to four splits evenly either side of the name"
      (is (= [1 1 0] (shape 2)))
      (is (= [2 2 0] (shape 4))))
    (testing "anything that cannot balance goes on a centred line below instead
              of leaving a lone mark on one side of a symmetric rule"
      (is (= [0 0 1] (shape 1)))
      (is (= [0 0 3] (shape 3)))
      (is (= [0 0 6] (shape 6)) "more than four would crowd the column"))
    (testing "no links, nothing anywhere"
      (is (= [0 0 0] (shape 0))))
    (testing "links keep their given order, left side first"
      (let [{:keys [left right]} (pa/credit-mark-layout (ls 4))]
        (is (= (ls 4) (into left right)))))))
