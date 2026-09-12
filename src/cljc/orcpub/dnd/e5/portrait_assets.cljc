(ns orcpub.dnd.e5.portrait-assets
  "Data-only registry of paper-doll portrait layers, per contributing artist.

   The compositor stacks assets in fixed z-order (`layer-order` below,
   bottom → top). Every asset is `{:asset/id … :asset/url …}` — the
   compositor renders each selected layer as a CSS mask tinted by the
   character's colours, absolute-positioned in a common frame.

   `asset-inventory` is the illustrator's real set: ten layers, twenty-eight
   pieces, their own filenames. The files under
   resources/public/image/portraits/ are currently silhouettes at the paths
   the finished art will occupy; a production run overwrites them in place.

   Attribution reads directly off this registry: for a composed set of
   layers, look up each layer's asset by id, find the artist whose
   library contains that asset, list them once with :artist/name and
   :artist/link. See `artist-for-asset` and `all-artists-for-layers`."
  (:require [clojure.string :as s]))

(def layer-order
  "Layer keys in z-order, bottom (0) → top (9). Mirrors the illustrator's
   real folder convention (`layer 0 - hair bits`, `layer 9 - bangs`, …)."
  [:hair-bits :hair-back :head :shirt :hair-front :ears :eyes :nose :mouth :bangs])

(def layer-labels
  {:hair-bits  "Hair bits"
   :hair-back  "Hair back"
   :head       "Head"
   :shirt      "Shirt"
   :hair-front "Hair front"
   :ears       "Ears"
   :eyes       "Eyes"
   :nose       "Nose"
   :mouth      "Mouth"
   :bangs      "Bangs"})

(def layer-colors
  "Category tint per layer — hair family in amber, features warm flesh
   with cool eyes and clay-red mouth, shirt in slate blue. Used in the
   picker chrome; the actual composited art of course uses whatever hues
   the illustrator drew."
  {:hair-bits  "#e0a24d"
   :hair-back  "#c88a4a"
   :head       "#f2e6d0"
   :shirt      "#7a94b8"
   :hair-front "#e6a040"
   :ears       "#eab098"
   :eyes       "#78d0d4"
   :nose       "#d67c5c"
   :mouth      "#c85c5c"
   :bangs      "#f5c46b"})

;; ---------- the asset inventory ----------

(def asset-root
  "Where the layer art is served from. Files are named exactly as the
   illustrator named them, lower-cased."
  "/image/portraits/")

(def asset-inventory
  "The illustrator's real inventory: ten layers, twenty-eight pieces, their
   own filenames and counts.

   The files currently on disk are SILHOUETTES -- the Loom's 48px alpha
   shapes, un-squashed back to the source proportions and written to the paths
   the finished art will occupy. Real shapes and real arrangement, no line
   detail. Running scripts/build-portrait-assets.py over the originals
   overwrites these same paths and nothing here changes."
  {
   :hair-bits
   [{:asset/id :l0-hair-bits-pony-long
     :asset/label "Pony long"
     :asset/file  "l0_hair_bits_pony_long.png"}
    {:asset/id :l0-hair-bits-pony-short
     :asset/label "Pony short"
     :asset/file  "l0_hair_bits_pony_short.png"}]
   :hair-back
   [{:asset/id :l1-hair-back-02
     :asset/label "Hair back 02"
     :asset/file  "l1_hair_back_02.png"}
    {:asset/id :l1-hair-back-03
     :asset/label "Hair back 03"
     :asset/file  "l1_hair_back_03.png"}]
   :head
   [{:asset/id :l2-head-01
     :asset/label "Head 01"
     :asset/file  "l2_head_01.png"}
    {:asset/id :l2-head-02
     :asset/label "Head 02"
     :asset/file  "l2_head_02.png"}
    {:asset/id :l2-head-03
     :asset/label "Head 03"
     :asset/file  "l2_head_03.png"}]
   :shirt
   [{:asset/id :l3-shirt-01
     :asset/label "Shirt 01"
     :asset/file  "l3_shirt_01.png"}
    {:asset/id :l3-shirt-02
     :asset/label "Shirt 02"
     :asset/file  "l3_shirt_02.png"}
    {:asset/id :l3-shirt-03
     :asset/label "Shirt 03"
     :asset/file  "l3_shirt_03.png"}]
   :hair-front
   [{:asset/id :l4-hair-front-01
     :asset/label "Hair front 01"
     :asset/file  "l4_hair_front_01.png"}
    {:asset/id :l4-hair-front-02
     :asset/label "Hair front 02"
     :asset/file  "l4_hair_front_02.png"}
    {:asset/id :l4-hair-front-03
     :asset/label "Hair front 03"
     :asset/file  "l4_hair_front_03.png"}]
   :ears
   [{:asset/id :l5-ear-01
     :asset/label "Ears 01"
     :asset/file  "l5_ear_01.png"}
    {:asset/id :l5-ear-02
     :asset/label "Ears 02"
     :asset/file  "l5_ear_02.png"}
    {:asset/id :l5-ear-03
     :asset/label "Ears 03"
     :asset/file  "l5_ear_03.png"}]
   :eyes
   [{:asset/id :l6-eyes-01
     :asset/label "Eyes 01"
     :asset/file  "l6_eyes_01.png"}
    {:asset/id :l6-eyes-02
     :asset/label "Eyes 02"
     :asset/file  "l6_eyes_02.png"}
    {:asset/id :l6-eyes-03
     :asset/label "Eyes 03"
     :asset/file  "l6_eyes_03.png"}]
   :nose
   [{:asset/id :l7-nose-01
     :asset/label "Nose 01"
     :asset/file  "l7_nose_01.png"}
    {:asset/id :l7-nose-02
     :asset/label "Nose 02"
     :asset/file  "l7_nose_02.png"}
    {:asset/id :l7-nose-03
     :asset/label "Nose 03"
     :asset/file  "l7_nose_03.png"}]
   :mouth
   [{:asset/id :l8-mouth-01
     :asset/label "Mouth 01"
     :asset/file  "l8_mouth_01.png"}
    {:asset/id :l8-mouth-02
     :asset/label "Mouth 02"
     :asset/file  "l8_mouth_02.png"}
    {:asset/id :l8-mouth-03
     :asset/label "Mouth 03"
     :asset/file  "l8_mouth_03.png"}]
   :bangs
   [{:asset/id :l9-bangs-01
     :asset/label "Bangs 01"
     :asset/file  "l9_bangs_01.png"}
    {:asset/id :l9-bangs-02
     :asset/label "Bangs 02"
     :asset/file  "l9_bangs_02.png"}
    {:asset/id :l9-bangs-03
     :asset/label "Bangs 03"
     :asset/file  "l9_bangs_03.png"}]})

(defn- assets-for
  [layer-key entries]
  (mapv (fn [{:asset/keys [id label file]}]
          {:asset/id    id
           :asset/label label
           :asset/url   (str asset-root (name layer-key) "/" file)
           :asset/tags  #{layer-key}})
        entries))

(def house-pack
  "The single contributing artist.

   :artist/name is deliberately nil until the illustrator says how they want
   to be credited. credit-line skips unnamed artists, so nothing invents a
   byline for them in the meantime -- the sheet, the share card and the
   summary simply show no credit until this is filled in."
  {:artist/id      :house-pack
   :artist/name    nil
   :artist/link    nil
   :artist/license nil
   :artist/layers  (reduce-kv (fn [m k v] (assoc m k (assets-for k v)))
                              {} asset-inventory)})

(def registry
  "Contributing artists. Later entries layer on top of earlier ones —
   pickers show every artist's assets for a category, in registry order."
  [house-pack])

;; ---------- lookup helpers ----------

(defn assets-for-layer
  "All assets contributed for `layer-key` across every registered artist."
  [layer-key]
  (into [] (mapcat #(get-in % [:artist/layers layer-key])) registry))

(defn asset-count-for-layer [layer-key]
  (count (assets-for-layer layer-key)))

(defn asset-by-id
  "The `{:asset/id … :asset/url … …}` map for a given asset id in `layer-key`,
   or nil if not found."
  [layer-key asset-id]
  (some #(when (= asset-id (:asset/id %)) %) (assets-for-layer layer-key)))

(defn artist-for-asset
  "The `:artist/id` whose library contains `asset-id` in `layer-key`,
   or nil."
  [layer-key asset-id]
  (some (fn [{:keys [:artist/id :artist/layers]}]
          (when (some #(= asset-id (:asset/id %))
                      (get layers layer-key))
            id))
        registry))

(defn artist-info
  "Full `{:artist/id … :artist/name … :artist/link …}` map for an id."
  [artist-id]
  (some #(when (= artist-id (:artist/id %)) %) registry))

(defn all-artists-for-layers
  "Given the current portrait-layers selection (`{layer-key {:artist/id …
   :asset/id …}}`), return the DISTINCT artist ids currently on canvas,
   in registry order — the shape the attribution surface renders."
  [layers-selection]
  (let [in-use? (into #{}
                      (keep (fn [[_ {:keys [:artist/id]}]] id))
                      layers-selection)]
    (into []
          (comp (map :artist/id) (filter in-use?))
          registry)))

(defn artists-for-layers
  "Full artist maps for a portrait's layer selection, in registry order."
  [layers-selection]
  (into [] (keep artist-info) (all-artists-for-layers layers-selection)))

(defn format-credit
  "The credit string for a list of artist names, or nil for none."
  [names]
  (when (seq names)
    (str "Art: " (s/join ", " names))))

(defn credit-line
  "One-line attribution for a composed portrait.

   nil when nothing is composed, or when no artist on canvas has said how they
   want to be credited -- an unnamed artist is skipped rather than given an
   invented byline. Every caller can `when-let` and drop the surface entirely.
   Shared by the PDF export, the share card and the character summary so all
   three credit the same people the same way."
  [portrait]
  (format-credit (into [] (keep :artist/name) (artists-for-layers (:layers portrait)))))

;; ---------- pure helpers for seeded randomization ----------
;;
;; Lives here (cljc) rather than in portrait.cljs so JVM tests can reach it.
;; All bit-ops used are cljc-safe.

(defn seed->int
  "FNV-1a-flavored string hash. Same seed always resolves to the same
   integer, in both Clojure and ClojureScript."
  [seed]
  (reduce (fn [h c]
            (bit-and 0xffffffff
                     (unchecked-multiply (bit-xor h (int c)) 16777619)))
          2166136261
          (str seed)))

(defn mulberry32
  "Small stateless PRNG. `(mulberry32 seed-int)` returns a fn that yields
   a fresh number in [0,1) on each call."
  [seed-int]
  (let [a (atom (bit-and 0xffffffff seed-int))]
    (fn []
      (swap! a #(bit-and 0xffffffff (unchecked-add % 0x6D2B79F5)))
      (let [s @a
            t1 (bit-and 0xffffffff
                        (unchecked-multiply (bit-xor s (unsigned-bit-shift-right s 15))
                                            (bit-or s 1)))
            t2 (bit-and 0xffffffff
                        (unchecked-add t1
                                       (unchecked-multiply (bit-xor t1 (unsigned-bit-shift-right t1 7))
                                                           (bit-or t1 61))))]
        (/ (unsigned-bit-shift-right (bit-xor t2 (unsigned-bit-shift-right t2 14)) 0)
           4294967296.0)))))

(defn random-seed
  "Fresh 8-char alphanumeric seed. Uses (rand-int) which is available on
   both platforms."
  []
  (let [alpha "abcdefghjkmnpqrstuvwxyz23456789"
        n (count alpha)]
    (apply str (repeatedly 8 #(nth alpha (rand-int n))))))

(defn compose-for-seed
  "Deterministic — feed a seed, get a `{layer-key {:artist/id … :asset/id …}}`
   map covering every layer whose registry has at least one asset. Layers
   with an empty registry are skipped."
  [seed]
  (let [rand-fn (mulberry32 (seed->int seed))]
    (reduce
      (fn [acc layer-key]
        (let [assets (assets-for-layer layer-key)]
          (if (empty? assets)
            acc
            (let [asset (nth assets (int (Math/floor (* (rand-fn) (count assets)))))
                  artist-id (artist-for-asset layer-key (:asset/id asset))]
              (assoc acc layer-key {:artist/id artist-id :asset/id (:asset/id asset)})))))
      {}
      layer-order)))

;; ---------- character colors ----------
;;
;; A portrait is {:layers {…} :colors {slot hex} :tweaks {layer {:shade n
;; :override hex}}}. `:colors` holds one base color per slot that paints
;; every layer mapped to that slot; `:tweaks` lets a single piece shade
;; lighter/darker than the base or override it outright (bangs highlight,
;; hair-back shadow, dyed streak). Rendering applies the tint via CSS mask,
;; so one asset renders in any color — see portrait.cljs/composite.

(def empty-portrait {:layers {} :colors {} :tweaks {}})

(def color-slots
  "Which color slot each layer draws its base tint from. nil = the layer
   keeps its category tint (mouth stays clay red; lips in skin tone look
   wrong)."
  {:hair-bits  :hair
   :hair-back  :hair
   :hair-front :hair
   :bangs      :hair
   :head       :skin
   :ears       :skin
   :nose       :skin
   :eyes       :eyes
   :shirt      :shirt
   :mouth      nil})

(def color-slot-order [:hair :skin :eyes :shirt])

(def color-slot-labels {:hair "Hair" :skin "Skin" :eyes "Eyes" :shirt "Shirt"})

(def color-presets
  "One-tap starting points per slot; a native picker covers the rest."
  {:hair  ["#2b1a10" "#5c3a1e" "#a06430" "#d4a256" "#e6d58f" "#c8c8c8" "#f2f2f2" "#7a3f6e"]
   :skin  ["#f2ddc4" "#e8c69c" "#c99871" "#a06e46" "#6c4726" "#3d2617" "#c0a693" "#a4b5a0"]
   :eyes  ["#6b4a2a" "#3d5c8f" "#4a7a4c" "#8a7c3f" "#8a4a4a" "#4a8a8a" "#7a4a8a" "#c4b48a"]
   :shirt ["#3a4a5c" "#7a94b8" "#5c3a3a" "#8f5c3a" "#3d5a3a" "#5c3a5c" "#2c2c2c" "#c8c0a8"]})

(defn layers-in-slot
  "Layer keys mapped to `slot`, in z-order."
  [slot]
  (filterv #(= slot (color-slots %)) layer-order))

(defn- hex-pair->int [s]
  #?(:clj  (Integer/parseInt s 16)
     :cljs (js/parseInt s 16)))

(defn- int->hex-pair [n]
  (let [s #?(:clj  (Integer/toHexString (int n))
             :cljs (.toString n 16))]
    (if (< (count s) 2) (str "0" s) s)))

(def ^:private hex-re #"^#[0-9a-fA-F]{6}$")

(defn shade-hex
  "Mix a #rrggbb color toward white (pct > 0) or black (pct < 0); pct is
   clamped to [-100, 100]. A zero/nil pct or a non-#rrggbb input returns
   `hex` unchanged."
  [hex pct]
  (if (or (nil? pct) (zero? pct) (not (re-matches hex-re (str hex))))
    hex
    (let [p      (/ (min 100 (Math/abs pct)) 100.0)
          target (if (neg? pct) 0 255)
          mix    (fn [c] (int (Math/round (+ c (* (- target c) p)))))
          [r g b] (map #(hex-pair->int (subs hex % (+ % 2))) [1 3 5])]
      (str "#" (int->hex-pair (mix r)) (int->hex-pair (mix g)) (int->hex-pair (mix b))))))

(defn base-tint
  "What a layer renders in before any per-piece tweak: its slot's chosen
   color if set, else the layer's category tint."
  [portrait layer-key]
  (let [slot (color-slots layer-key)]
    (or (and slot (get-in portrait [:colors slot]))
        (layer-colors layer-key))))

(defn tint-for
  "Effective render color for a layer: per-piece override → shaded base →
   base."
  [portrait layer-key]
  (let [{:keys [override shade]} (get-in portrait [:tweaks layer-key])]
    (cond
      override override
      shade    (shade-hex (base-tint portrait layer-key) shade)
      :else    (base-tint portrait layer-key))))

(defn tweaked-layers-in-slot
  "Layers in `slot` carrying a real per-piece tweak (an override, or a
   non-zero shade), in z-order. Drives the tweak badge and sub-dots on the
   slot chip."
  [portrait slot]
  (filterv (fn [k]
             (let [{:keys [override shade]} (get-in portrait [:tweaks k])]
               (boolean (or override (and shade (not (zero? shade)))))))
           (layers-in-slot slot)))
