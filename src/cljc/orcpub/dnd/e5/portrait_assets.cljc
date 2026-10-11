(ns orcpub.dnd.e5.portrait-assets
  "Registry of portrait layer art per contributing artist, the colour model, and the credit.
   Assets are {:asset/id … :asset/url …}, stacked in `layer-order` and tinted per
   `render-mode`. Attribution resolves each selected asset to its artist through
   `registry`: see `credit-order` and `credit-line`, shared by every surface.
   The files in the repo are silhouettes; the real art is never committed.
   Why the code is shaped this way: PORTRAIT-COMPOSITOR.md, PORTRAIT-TINTING.md."
  (:require [clojure.string :as s]))

(def layer-order
  "Layer keys in z-order, bottom (0) → top (9). Mirrors the illustrator's
   real folder convention (`layer 0 - hair bits`, `layer 9 - bangs`, …)."
  [:hair-bits :hair-back :head :scalp :shirt :hair-front :ears :eyes :nose :mouth :bangs])

(def hidden-layers
  "Layers that render but are not offered in the picker. :scalp is one hair-coloured blob
   between head and hair that fills the skin gap where some hair-front and bangs pairs do
   not meet; with one option there is nothing to pick."
  #{:scalp})

(def pickable-layers
  "The layers a person actually picks from, in z-order."
  (vec (remove hidden-layers layer-order)))

(def layer-labels
  {:hair-bits  "Hair bits"
   :hair-back  "Hair back"
   :head       "Head"
   :scalp      "Scalp"
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
   :scalp      "#c88a4a"
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
  "{layer-key [{:asset/id :asset/label :asset/file}]}: the illustrator's ten layers and
   twenty-eight pieces under their own filenames, plus the generated :scalp.
   The files in the repo are silhouettes at the real art's paths;
   scripts/build-portrait-assets.py overwrites them from the originals."
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
   :scalp
   [{:asset/id :l2b-scalp-01
     :asset/label "Scalp"
     :asset/file  "l2b_scalp_01.png"}]

   :mouth
   ;; The three mouths want three different treatments, which is why
   ;; :asset/slot is on the ASSET and not on the layer. All three are drawn in
   ;; greys -- max saturation 1 out of 255 -- so "carries its own colour"
   ;; cannot tell them apart; what differs is what the shape IS.
   [{:asset/id :l8-mouth-01
     :asset/label "Mouth 01"
     :asset/file  "l8_mouth_01.png"}          ; a closed line, nothing to colour
    {:asset/id :l8-mouth-02
     :asset/label "Lips"
     :asset/file  "l8_mouth_02.png"
     :asset/slot  :lips}                      ; drawn shaded and hueless
    {:asset/id :l8-mouth-03
     :asset/label "Smile"
     :asset/file  "l8_mouth_03.png"}]         ; teeth: they stay white
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

(def gap-inventory
  "{layer-key [{:gap/id :gap/label}]}: pieces planned but not drawn, shown in the picker as
   inert \"soon\" swatches. Transcribe from the `no <name>.txt` files the asset scripts
   report. Empty means nothing is marked pending, not that the set is finished."
  {}
  ;; shape, when there is something to record:
  ;;   {:head  [{:gap/id :l2-head-04 :gap/label "Head 04"}]
  ;;    :shirt [{:gap/id :l3-shirt-04 :gap/label "Shirt 04"}]}
  )

(defn gaps-for-layer
  "Planned-but-undrawn pieces for `layer-key`, or an empty vector."
  [layer-key]
  (get gap-inventory layer-key []))

(defn any-gaps?
  "Whether anything at all is marked pending, so a surface can skip the
   whole affordance rather than render an empty one."
  []
  (boolean (some (comp seq val) gap-inventory)))

(defn- assets-for
  [layer-key entries]
  (mapv (fn [{:asset/keys [id label file slot gamma]}]
          (cond-> {:asset/id    id
                   :asset/label label
                   :asset/url   (str asset-root (name layer-key) "/" file)
                   :asset/tags  #{layer-key}}
            slot  (assoc :asset/slot slot)
            gamma (assoc :asset/gamma gamma)))
        entries))

(def house-pack
  "The illustrator who drew the portrait art.

   The name and link are the public default. A deployment can override them
   without touching this file -- see `artist-overrides` below -- which is what
   lets a fork credit its own contributors while the open-source repo still
   credits the artist whose work it ships."
  {:artist/id      :house-pack
   ;; The profile page's address, /artists/fusspot. Not the id: saved
   ;; portraits store :house-pack, so the id cannot change. Not overridable
   ;; either -- a URL that moved whenever a deployment restated a name would
   ;; break every link already shared. See artist-profile/slug.
   :artist/slug    "fusspot"
   :artist/name    "Fusspot"
   ;; The one link for surfaces that hold one, such as the character page's credit strip.
   :artist/link    "https://fusspot.rip/"
   ;; Exactly the links she asked for; add none she did not list. :link/icon names a mark
   ;; in /image/social (no icon shows the label as text); :link/color is the mark's colour,
   ;; worn at rest. Reasons: docs/design/artist-profiles/PLAN.md, "Fusspot's links".
   :artist/links   [{:link/label "Site"   :link/icon "site"
                     :link/color "#f0a100"
                     :link/url "https://fusspot.rip/"}
                    {:link/label "Twitch" :link/icon "twitch"
                     :link/color "#9146ff"
                     :link/url "https://www.twitch.tv/fusspot"}]
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

;; Per-artist fields supplied at runtime, keyed by :artist/id.
;;
;; defonce takes no docstring, hence the comment.
(comment
  "Per-artist fields supplied at runtime, keyed by :artist/id.

   The registry holds the STRUCTURE -- which artist drew which asset -- and
   that has to be public, because the app cannot resolve an asset without it.
   The presentation of a credit is a deployment concern: a fork may ship the
   same art under a different arrangement with the artist, or add artists its
   own users contributed. So the name, link and licence are overridable and
   nothing else is.

   Set on the server from env at startup and on the client from the config
   the page injects, so both runtimes credit the same person -- the drawer and
   the PDF render in the browser, the share card on the server, and a credit
   that disagreed between them would be worse than none.")

(defonce ^:private artist-overrides (atom {}))

(defn set-artist-overrides!
  "Replace the runtime artist overrides. `m` is {artist-id {field value}}; only
   :artist/name, :artist/link, :artist/links, :artist/license and :artist/profile? are
   kept. :artist/profile? false turns the artist's profile page off (artist-profile/profile).
   See PORTRAIT-COMPOSITOR.md, \"Deployment overrides\"."
  [m]
  (reset! artist-overrides
          (into {}
                (for [[id fields] m
                      :let [keep-fields (select-keys fields [:artist/name
                                                             :artist/link
                                                             :artist/links
                                                             :artist/license
                                                             :artist/profile?])]
                      :when (seq keep-fields)]
                  [id keep-fields]))))

(defn artist-info
  "Full `{:artist/id … :artist/name … :artist/link …}` map for an id, with any
   deployment override applied."
  [artist-id]
  (when-let [base (some #(when (= artist-id (:artist/id %)) %) registry)]
    (merge base (get @artist-overrides artist-id))))

(defn all-artists-for-layers
  "Given the current portrait-layers selection (`{layer-key {:artist/id …
   :asset/id …}}`), return the DISTINCT artist ids currently on canvas,
   in registry order — the shape the attribution surface renders."
  [layers-selection]
  ;; Belt and braces with character/sane-portrait: this is public and reached
  ;; from cljs too, and walking a non-map selection throws rather than
  ;; returning nothing.
  (if-not (map? layers-selection)
    []
    (let [in-use? (into #{}
                      ;; Registry by asset id first, the saved :artist/id only as
                      ;; a fallback: a saved portrait is client data. See
                      ;; PORTRAIT-COMPOSITOR.md, "Saved portraits are untrusted".
                      (keep (fn [[layer-key sel]]
                              (or (artist-for-asset layer-key (:asset/id sel))
                                  (:artist/id sel))))
                        layers-selection)]
      (into []
            (comp (map :artist/id) (filter in-use?))
            registry))))

(defn drawable-layers
  "The entries of a selection whose asset ids resolve in the registry; nil for a non-map.
   A non-empty selection can draw nothing (a removed id, a pack this deployment lacks);
   drawable? tells the two apart."
  [layers-selection]
  (when (map? layers-selection)
    (into {}
          (filter (fn [[layer-key sel]]
                    (some? (asset-by-id layer-key (:asset/id sel)))))
          layers-selection)))

(defn drawable?
  "Whether a portrait has anything the renderer can actually put on a canvas."
  [portrait]
  (boolean (seq (drawable-layers (:layers portrait)))))

(def layer-credit-weight
  "How much of a portrait each layer is, for credit-order: roughly area and how much the
   piece defines the character. :scalp is 0 (generated, not drawn); a layer missing from
   the map counts 1. Why fixed numbers: PORTRAIT-COMPOSITOR.md, \"Who the credit names\"."
  {:head 10
   :hair-front 6
   :hair-back 6
   :shirt 5
   :bangs 4
   :eyes 4
   :hair-bits 3
   :mouth 2
   :nose 1
   :ears 1
   :scalp 0})

(defn credit-order
  "Artist maps (artist-info) for the artists on canvas, most of the picture first: by total
   layer-credit-weight, then piece count, then registry order. Each gains :credit/weight
   and :credit/pieces. Attribution as all-artists-for-layers; artists not in the registry
   are left out. [] for a non-map selection."
  [layers-selection]
  (if-not (map? layers-selection)
    []
    (let [credited (set (all-artists-for-layers layers-selection))
          tally (reduce (fn [acc [layer-key sel]]
                          (let [id (or (artist-for-asset layer-key (:asset/id sel))
                                       (:artist/id sel))]
                            (if (contains? credited id)
                              (-> acc
                                  (update-in [id :weight] (fnil + 0)
                                             (get layer-credit-weight layer-key 1))
                                  (update-in [id :pieces] (fnil inc 0)))
                              acc)))
                        {} layers-selection)
          registry-rank (into {} (map-indexed (fn [i a] [(:artist/id a) i])) registry)]
      (->> tally
           (keep (fn [[id {:keys [weight pieces]}]]
                   (when-let [a (artist-info id)]
                     (assoc a :credit/weight weight :credit/pieces pieces))))
           (sort-by (juxt (comp - :credit/weight)
                          (comp - :credit/pieces)
                          #(registry-rank (:artist/id %))))
           vec))))

(defn artists-for-layers
  "Full artist maps for a portrait's layer selection, in credit order -- see
   `credit-order`. Every surface that names artists goes through this, so the
   drawer, the character page, the share card and the PDF agree on who comes
   first."
  [layers-selection]
  (credit-order layers-selection))

(def max-flanking-marks
  "The most link marks that can sit either side of an artist's name, in
   total. Two a side still fits the 247px drawer column beside a long name."
  4)

(defn credit-mark-layout
  "Where an artist's link marks sit around their name: {:left [...] :right [...] :below
   [...]}, links in their given order. An even count up to max-flanking-marks is split
   either side of the name; any other count goes below, so the credit always balances."
  [links]
  (let [links (vec links)
        n (count links)]
    (if (and (pos? n) (even? n) (<= n max-flanking-marks))
      {:left (subvec links 0 (quot n 2))
       :right (subvec links (quot n 2))
       :below []}
      {:left [] :right [] :below links})))

(defn format-credit
  "The credit string for a list of artist names, or nil for none."
  [names]
  (when (seq names)
    (str "Art: " (s/join ", " names))))

(defn credit-line
  "\"Art: A, B\" for a composed portrait's named artists in credit order, or nil when none
   is named. Artists without :artist/name are skipped, never given a byline. Shared by the
   PDF export, the share card and the character summary."
  [portrait]
  (format-credit (into [] (keep :artist/name) (artists-for-layers (:layers portrait)))))

;; ---------- pure helpers for seeded randomization ----------
;;
;; Lives here (cljc) rather than in portrait.cljs so JVM tests can reach it.
;; All bit-ops used are cljc-safe.

(defn- u32
  "`x` wrapped to an unsigned 32-bit integer."
  [x]
  #?(:clj (bit-and 0xffffffff x) :cljs (unsigned-bit-shift-right x 0)))

(defn- mul32
  "`a` times `b` modulo 2^32, unsigned.
   GOTCHA: in cljs a plain multiply loses the low bits past 2^53; Math/imul does not."
  [a b]
  #?(:clj (bit-and 0xffffffff (unchecked-multiply a b))
     :cljs (unsigned-bit-shift-right (js/Math.imul a b) 0)))

(defn- char-code
  "The UTF-16 code unit of character `c`, the same number on both platforms."
  [c]
  #?(:clj (int c) :cljs (.charCodeAt c 0)))

(defn seed->int
  "32-bit FNV-1a hash of `(str seed)`, the same integer in Clojure and ClojureScript."
  [seed]
  (reduce (fn [h c] (mul32 (u32 (bit-xor h (char-code c))) 16777619))
          2166136261
          (str seed)))

(defn mulberry32
  "Small stateless PRNG. `(mulberry32 seed-int)` returns a fn that yields
   a fresh number in [0,1) on each call, the same sequence on both platforms."
  [seed-int]
  (let [a (atom (u32 seed-int))]
    (fn []
      (swap! a #(u32 (+ % 0x6D2B79F5)))
      (let [s @a
            t1 (mul32 (u32 (bit-xor s (unsigned-bit-shift-right s 15))) (u32 (bit-or s 1)))
            t2 (u32 (bit-xor t1 (u32 (+ t1 (mul32 (u32 (bit-xor t1 (unsigned-bit-shift-right t1 7)))
                                                   (u32 (bit-or t1 61)))))))]
        (/ (u32 (bit-xor t2 (unsigned-bit-shift-right t2 14)))
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
;; A portrait is {:layers {…} :colors {slot hex} :tweaks {layer {:shade n :override hex}}}.
;; :colors paints every layer in a slot; :tweaks shades one piece or overrides its colour.
;; Each renderer applies `tint-for` as `render-mode` says: PORTRAIT-TINTING.md.

(def empty-portrait {:layers {} :colors {} :tweaks {}})

(def color-slots
  "The colour slot each LAYER draws its base tint from; nil means no slot (category tint).
   An asset's own :asset/slot wins over its layer's (slot-for-asset): the mouth's lipped
   piece names :lips while its line and teeth have none."
  {:hair-bits  :hair
   :hair-back  :hair
   :hair-front :hair
   :bangs      :hair
   :head       :skin
   ;; the scalp is hair, not skin -- that is the entire point of it
   :scalp      :hair
   :ears       :skin
   :nose       :skin
   :eyes       :eyes
   :shirt      :shirt
   :mouth      nil})

(def color-slot-order
  "Slot order in the picker. `:lips` is last because it only applies to some
   mouth assets; `slot-in-use?` decides whether to show it."
  [:hair :skin :eyes :shirt :lips])

(def color-slot-labels
  {:hair "Hair" :skin "Skin" :eyes "Eyes" :shirt "Shirt" :lips "Lips"})

(def conditional-slots
  "Slots that only apply when an asset asks for them, rather than to a whole
   layer. Showing a Lips swatch against a closed-mouth asset would be a control
   that does nothing."
  #{:lips})

(def color-presets
  "One-tap starting points per slot; a native picker covers the rest."
  {:hair  ["#2b1a10" "#5c3a1e" "#a06430" "#d4a256" "#e6d58f" "#c8c8c8" "#f2f2f2" "#7a3f6e"]
   :skin  ["#f2ddc4" "#e8c69c" "#c99871" "#a06e46" "#6c4726" "#3d2617" "#c0a693" "#a4b5a0"]
   :eyes  ["#6b4a2a" "#3d5c8f" "#4a7a4c" "#8a7c3f" "#8a4a4a" "#4a8a8a" "#7a4a8a" "#c4b48a"]
   :shirt ["#3a4a5c" "#7a94b8" "#5c3a3a" "#8f5c3a" "#3d5a3a" "#5c3a5c" "#2c2c2c" "#c8c0a8"]
   ;; bare through berry, then plum and coral. These read as lips rather than
   ;; as a stripe of paint because the art supplies the shading and only the
   ;; hue comes from here.
   :lips  ["#c98d82" "#c46a6a" "#b04a5a" "#9b3c55" "#6e334e" "#d87a5c" "#a85c4a" "#7a4442"]})

(def default-slot-colors
  "What a slot renders as before anyone picks. Only :lips has one: the other
   slots fall back to their layer's category tint, but a lipped mouth with no
   lip colour would render in greys, which is the bug this slot exists to fix."
  {:lips "#c98d82"})

(defn selected-asset
  "The asset a portrait has chosen for `layer-key`, or nil."
  [portrait layer-key]
  (when-let [asset-id (:asset/id (get-in portrait [:layers layer-key]))]
    (asset-by-id layer-key asset-id)))

(defn slot-for-asset
  "The colour slot an asset renders from: its own `:asset/slot` when it names
   one, otherwise its layer's.

   The asset wins because it knows more. A layer holds pieces that are not
   alike -- one mouth is lips and another is teeth -- and only the piece can
   say which it is."
  [layer-key asset]
  (or (:asset/slot asset) (color-slots layer-key)))

(defn slot-in-use?
  "Whether `slot` applies to anything this portrait has actually selected.

   Unconditional slots always apply. A conditional one applies only while a
   selected asset asks for it, so the Lips swatch appears with the lips and
   goes away again with a closed mouth."
  [portrait slot]
  (if-not (contains? conditional-slots slot)
    true
    (boolean
     (some (fn [layer-key]
             (when-let [asset-id (:asset/id (get-in portrait [:layers layer-key]))]
               (= slot (:asset/slot (asset-by-id layer-key asset-id)))))
           layer-order))))

(defn active-color-slots
  "The slots the picker should show for `portrait`, in order."
  [portrait]
  (filterv #(slot-in-use? portrait %) color-slot-order))

(defn layers-in-slot
  "Layer keys mapped to `slot`, in z-order.

   With a `portrait`, a layer counts when its SELECTED asset names the slot --
   which is the only way the mouth appears under Lips, since the layer itself
   is mapped to nothing."
  ([slot] (filterv #(and (not (hidden-layers %)) (= slot (color-slots %)))
                   layer-order))
  ([portrait slot]
   (filterv #(and (not (hidden-layers %))
                  (= slot (slot-for-asset % (selected-asset portrait %))))
            layer-order)))

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
   color if set, else the slot's default, else the layer's category tint.

   The slot comes from the SELECTED ASSET, not the layer, so a lipped mouth
   draws from :lips while a closed one in the same layer draws from neither."
  [portrait layer-key]
  (let [slot (slot-for-asset layer-key (selected-asset portrait layer-key))]
    (or (and slot (get-in portrait [:colors slot]))
        (and slot (default-slot-colors slot))
        (layer-colors layer-key))))

(defn render-mode
  "How a renderer tints `asset` in `layer-key`: :multiply (the default; lines stay lines),
   :colorize (luminance mapped through the colour, for the eyes slot and conditional slots
   such as :lips), or :as-drawn (no slot: left as the illustrator drew it, like the teeth).
   See PORTRAIT-TINTING.md."
  [layer-key asset]
  (let [slot (slot-for-asset layer-key asset)]
    (cond
      (nil? slot) :as-drawn
      (contains? conditional-slots slot) :colorize
      (= :eyes slot) :colorize
      :else :multiply)))

(defn tint-gamma
  "The luminance gamma for a :colorize asset: its :asset/gamma, else 2.2 for lips (drawn
   light) and 0.5 otherwise (irises, drawn near-black). See PORTRAIT-TINTING.md, \"Floor
   and gamma\"."
  [layer-key asset]
  (or (:asset/gamma asset)
      (if (= :lips (slot-for-asset layer-key asset)) 2.2 0.5)))

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
