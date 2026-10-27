(ns orcpub.dnd.e5.portrait-assets
  "Registry of portrait layer art per contributing artist, the colour model, and the credit.
   Assets are {:asset/id … :asset/url …}, stacked in `layer-order` and tinted per
   `render-mode`; per-piece data (iris regions, lids, whites, shadows) is merged in from
   resources/portrait-pack at build time (portrait-calibration). Attribution goes through
   `registry`: `credit-order`, `credit-line`. The repo's art files are silhouettes.
   Why the code is shaped this way: PORTRAIT-COMPOSITOR.md, PORTRAIT-TINTING.md."
  (:require [clojure.string :as s]
            [orcpub.dnd.e5.portrait-random :as rnd]
            #?(:clj [orcpub.dnd.e5.portrait-calibration :refer [calibration]]))
  #?(:cljs (:require-macros [orcpub.dnd.e5.portrait-calibration :refer [calibration]])))

(defn- ascii-lower
  "Lower case for ASCII names and hosts. On the JVM the default locale decides the folding, and
   Turkish turns I into a dotless i; these strings are compared with fixed ASCII."
  [x]
  #?(:clj (.toLowerCase (str x) java.util.Locale/ROOT) :cljs (s/lower-case x)))

(def layer-order
  "Layer keys in z-order, bottom (0) → top (9). Mirrors the illustrator's
   real folder convention (`layer 0 - hair bits`, `layer 9 - bangs`, …)."
  [:hair-bits :hair-back :head :shirt :hair-front :ears :eyes :nose :mouth :bangs])

(def hidden-layers
  "Layers that render but are not offered in the picker. None today; a saved portrait that
   still names the retired :scalp is read without it (`current-layers`)."
  #{})

(def pickable-layers
  "The layers a person actually picks from, in z-order."
  (vec (remove hidden-layers layer-order)))

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
  "{layer-key [{:asset/id :asset/label :asset/file}]}: the illustrator's ten layers and
   twenty-eight pieces under their own filenames. The files in the repo are silhouettes at
   the real art's paths; the art pack is unpacked over them at deploy."
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
   ;; three mouths wanting three treatments (a line, lips, teeth); which is
   ;; which is set in pieces.edn
   [{:asset/id :l8-mouth-01
     :asset/label "Mouth 01"
     :asset/file  "l8_mouth_01.png"}
    {:asset/id :l8-mouth-02
     :asset/label "Lips"
     :asset/file  "l8_mouth_02.png"}
    {:asset/id :l8-mouth-03
     :asset/label "Smile"
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

(def piece-calibration
  "{layer {\"file.png\" {field value}}} from resources/portrait-pack, read at
   build time (loom.edn under pieces.edn)."
  (calibration))

(def calibration-fields
  "What a piece can carry from its calibration, and the asset key it becomes.

   :slot          the colour slot, when it differs from its layer's (lips)
   :gamma         iris or lip shading
   :iris :pupil   the placed iris regions and pupil size (from the Loom)
   :whites        drawn without whites; each iris carries a :lower lid and
                  the whites are filled between the lids
   :casts-shadow  hangs over the face, so throws a soft shadow onto the skin
   :tips-from     hair only: stays in the root colour until this far along
                  the piece (0..1; 1 = never takes the tip colour)"
  {:slot :asset/slot :gamma :asset/gamma :iris :asset/iris :pupil :asset/pupil
   :whites :asset/whites :casts-shadow :asset/casts-shadow :tips-from :asset/tips-from})

(defn- assets-for
  [layer-key entries]
  (mapv (fn [{:asset/keys [id label file]}]
          (let [cal (get-in piece-calibration [layer-key (ascii-lower file)])]
            (into {:asset/id    id
                   :asset/label label
                   :asset/url   (str asset-root (name layer-key) "/" file)
                   :asset/tags  #{layer-key}}
                  (keep (fn [[k v]] (when-let [ak (calibration-fields k)] (when (some? v) [ak v]))))
                  cal)))
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
  "Replace the runtime artist overrides. `m` is {artist-id {field value}}; only the name, link,
   links, licence and the page switches (:artist/profile?, :artist/show-examples?, -links? and
   -count?, false to turn off; artist-profile/shows?) are kept.
   See PORTRAIT-COMPOSITOR.md, \"Deployment overrides\"."
  [m]
  (reset! artist-overrides
          (into {}
                (for [[id fields] m
                      :let [keep-fields (select-keys fields [:artist/name
                                                             :artist/link
                                                             :artist/links
                                                             :artist/license
                                                             :artist/profile?
                                                             :artist/show-examples?
                                                             :artist/show-links?
                                                             :artist/show-count?])]
                      :when (seq keep-fields)]
                  [id keep-fields]))))

(defn current-overrides
  "The overrides in force, as set by `set-artist-overrides!`."
  []
  @artist-overrides)

(defn artist-info
  "Full `{:artist/id … :artist/name … :artist/link …}` map for an id, with any
   deployment override applied."
  [artist-id]
  (when-let [base (some #(when (= artist-id (:artist/id %)) %) registry)]
    (merge base (get @artist-overrides artist-id))))

(defn- current-layers
  "A saved selection with only the layers the app still draws. A portrait
   saved before a layer was retired (the old :scalp) keeps the key, and it
   must not go on crediting whoever drew it."
  [layers-selection]
  (select-keys layers-selection layer-order))

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
                        (current-layers layers-selection))]
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
   piece defines the character. A layer missing from the map counts 1.
   Why fixed numbers: PORTRAIT-COMPOSITOR.md, \"Who the credit names\"."
  {:head 10
   :hair-front 6
   :hair-back 6
   :shirt 5
   :bangs 4
   :eyes 4
   :hair-bits 3
   :mouth 2
   :nose 1
   :ears 1})

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
                        {} (current-layers layers-selection))
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

(defn- and-list
  "Names as prose: A / A and B / A, B and C."
  [names]
  (let [names (vec names)
        n (count names)]
    (case n
      0 ""
      1 (first names)
      (str (s/join ", " (pop names)) " and " (peek names)))))

(defn- others [n] (str n (if (= 1 n) " other" " others")))

(defn credit-variants
  "Every way to write the credit for `names` (in credit order), longest first.
   The same shape as the builder's lockup: whoever drew most of the picture
   leads, everyone else follows 'with', and when there isn't room the tail
   collapses into a count rather than being cut off mid-name:

     Art: Fusspot with Bex, Cy and Dee
     Art: Fusspot with Bex, Cy and 1 other
     Art: Fusspot with Bex and 2 others
     Art: Fusspot and 3 others

   Every surface that has to fit the credit somewhere takes the first variant
   that fits (`fit-credit`), so a share card, a PDF and a page all drop the
   same people in the same order -- the smallest contributors, last."
  [names]
  (let [[lead & more] names
        n (count more)]
    (cond
      (nil? lead) []
      (zero? n) [(str "Art: " lead)]
      :else (concat [(str "Art: " lead " with " (and-list more))]
                    (for [k (range (dec n) 0 -1)]
                      (str "Art: " lead " with " (s/join ", " (take k more)) " and " (others (- n k))))
                    [(str "Art: " lead " and " (others n))]))))

(defn fit-credit
  "The longest variant of the credit that `fits?`, else the shortest."
  [names fits?]
  (let [vs (credit-variants names)]
    (or (first (filter fits? vs)) (last vs))))

(defn format-credit
  "The credit string for a list of artist names, or nil for none: the full
   variant, for surfaces with room for it."
  [names]
  (first (credit-variants names)))

(defn credit-names
  "The named artists on canvas, in credit order."
  [portrait]
  (into [] (keep :artist/name) (artists-for-layers (:layers portrait))))

(defn credit-line
  "\"Art: A, B\" for a composed portrait's named artists in credit order, or nil when none
   is named. Artists without :artist/name are skipped, never given a byline. Shared by the
   PDF export, the share card and the character summary; surfaces with a width to fit use
   `credit-names` and layout/fit-credit."
  [portrait]
  (format-credit (credit-names portrait)))

;; ---------- pure helpers for seeded randomization ----------
;;
;; Lives here (cljc) rather than in portrait.cljs so JVM tests can reach it.
;; All bit-ops used are cljc-safe.

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
  (let [rand-fn (rnd/mulberry32 (rnd/seed->int seed))]
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
   :ears       :skin
   :nose       :skin
   :eyes       :eyes
   :shirt      :shirt
   :mouth      nil})

(def color-slot-order
  "Slot order in the picker. `:whites` and `:lips` only apply to some assets;
   `slot-in-use?` decides whether to show them."
  [:hair :skin :eyes :whites :shirt :lips])

(def color-slot-labels
  {:hair "Hair" :skin "Skin" :eyes "Eyes" :whites "Whites" :shirt "Shirt" :lips "Lips"})

(def conditional-slots
  "Slots that only apply when an asset asks for them, rather than to a whole
   layer. Showing a Lips swatch against a closed-mouth asset would be a control
   that does nothing."
  #{:lips :whites})

(def color-presets
  "One-tap starting points per slot; a native picker covers the rest."
  {:hair  ["#2b1a10" "#5c3a1e" "#a06430" "#d4a256" "#e6d58f" "#c8c8c8" "#f2f2f2" "#7a3f6e"]
   :skin  ["#f2ddc4" "#e8c69c" "#c99871" "#a06e46" "#6c4726" "#3d2617" "#c0a693" "#a4b5a0"]
   :eyes  ["#6b4a2a" "#3d5c8f" "#4a7a4c" "#8a7c3f" "#8a4a4a" "#4a8a8a" "#7a4a8a" "#c4b48a"]
   :shirt ["#3a4a5c" "#7a94b8" "#5c3a3a" "#8f5c3a" "#3d5a3a" "#5c3a5c" "#2c2c2c" "#c8c0a8"]
   ;; bare through berry, then plum and coral. These read as lips rather than
   ;; as a stripe of paint because the art supplies the shading and only the
   ;; hue comes from here.
   :lips  ["#c98d82" "#c46a6a" "#b04a5a" "#9b3c55" "#6e334e" "#d87a5c" "#a85c4a" "#7a4442"]
   ;; white, then the everyday off-whites, then the fantastic ones: the
   ;; all-black and red of a fiend or an undead
   :whites ["#ffffff" "#f4efe4" "#e6e2d8" "#dfe6ee" "#efe3c2" "#f0d4cf" "#2a2a2e" "#a8322c"]})

(def default-slot-colors
  "What a slot renders as before anyone picks. Without one, each layer fell
   back to its own category tint, so a slot's pieces disagreed: a pale head
   with a pink ear and a clay-red nose, and five hair pieces in five ambers.
   Every slot whose pieces must match has a default. A lipped mouth with no
   lip colour would render in greys."
  {:skin "#f2ddc4"
   :hair "#a06430"
   :lips "#c98d82"
   ;; only for an eye style drawn without painted whites (:asset/whites), where
   ;; the whites are filled in underneath it; white unless someone picks
   :whites "#ffffff"})

(def no-whites
  "Stored as the Whites colour to leave an eye style's whites unfilled: the
   art as drawn, with the skin showing through."
  "none")

(defn whites-colour
  "What an eye style's filled-in whites are drawn in, or nil when the
   portrait leaves them unfilled."
  [portrait]
  (let [c (or (get-in portrait [:colors :whites]) (default-slot-colors :whites))]
    (when-not (= c no-whites) c)))

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
               (let [asset (asset-by-id layer-key asset-id)]
                 (or (= slot (:asset/slot asset))
                     ;; an eye style whose whites are filled in, not painted
                     (and (= slot :whites) (:asset/whites asset))))))
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
   clamped to [-100, 100]. A pct that is zero or not a number, or a non-#rrggbb input, returns
   `hex` unchanged."
  [hex pct]
  (if (or (not (number? pct)) (zero? pct) (not (re-matches hex-re (str hex))))
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

(def eyes-01-far-iris-alternative
  "A second placement for the far (right-hand) iris of Eyes 01, to live with
   before deciding. The registry above carries the illustrator's own; this is
   an edit that sits a little further left. Developer mode can switch the
   builder and the PDF to it (orcpub.dnd.e5.portrait); the share card and
   every other visitor always get the registry's."
  {:asset-id :l6-eyes-01 :iris-index 1 :cx 0.72 :cy 0.36102})

(defn with-iris-alternative
  "`asset` with the alternative far iris swapped in, if it is Eyes 01."
  [asset]
  (let [{:keys [asset-id iris-index cx cy]} eyes-01-far-iris-alternative]
    (if (and (= asset-id (:asset/id asset)) (< iris-index (count (:asset/iris asset))))
      (update-in asset [:asset/iris iris-index] assoc :cx cx :cy cy)
      asset)))

(def depth-steps
  "How far the Brightness slider on a colorized piece goes each way."
  3)

(defn effective-gamma
  "The gamma a :colorize piece is actually drawn with: the settled value from
   `tint-gamma`, moved by the piece's Brightness tweak. Each step up lifts the
   colour (a lower gamma raises the midtones), each step down deepens it; 0 is
   exactly the settled look. Junk in a stored tweak reads as 0."
  [portrait layer-key asset]
  (let [depth (get-in portrait [:tweaks layer-key :depth])
        depth (if (number? depth) (max (- depth-steps) (min depth-steps depth)) 0)]
    (* (tint-gamma layer-key asset) (Math/pow 1.25 (- depth)))))

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
             (let [{:keys [override shade depth]} (get-in portrait [:tweaks k])]
               (boolean (or override
                            (and (number? shade) (not (zero? shade)))
                            (and (number? depth) (not (zero? depth)))))))
           (layers-in-slot portrait slot)))
