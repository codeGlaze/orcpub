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
   :artist/link. See `artist-for-asset` and `all-artists-for-layers`.

   What each piece CARRIES beyond its name -- iris regions, lids, whites,
   whether it casts a shadow, which mouth is lips -- is not written here. It
   lives in resources/portrait-pack (loom.edn from the Loom, pieces.edn by
   hand) and is merged in at build time; see orcpub.dnd.e5.portrait-calibration."
  (:require [clojure.string :as s]
            #?(:clj [orcpub.dnd.e5.portrait-calibration :refer [calibration]]))
  #?(:cljs (:require-macros [orcpub.dnd.e5.portrait-calibration :refer [calibration]])))

(def layer-order
  "Layer keys in z-order, bottom (0) → top (9). Mirrors the illustrator's
   real folder convention (`layer 0 - hair bits`, `layer 9 - bangs`, …)."
  [:hair-bits :hair-back :head :shirt :hair-front :ears :eyes :nose :mouth :bangs])

(def hidden-layers
  "Layers that render but are not offered in the picker. None today.

   There was one, :scalp -- a generated blob under the hair for the hair-front
   and bangs pairs that left an island of skin on the crown. The art now meets
   (hair front 02 was patched to reach bangs 03), so it was retired; a saved
   portrait that still names it is read without it (see `current-layers`)."
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
  "Pieces the illustrator has planned but not drawn yet, per layer.

   Her convention is a `no <name>.txt` file sitting in the layer folder beside
   the art. Both asset scripts already read those and report them, so filling
   this in is a transcription job, not a guess -- run either one and it prints
   the gaps it found.

   The picker shows these as inert \"coming\" swatches. Empty is fine and means
   only that nothing is marked pending; it is not a claim that the set is
   finished."
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
          (let [cal (get-in piece-calibration [layer-key (s/lower-case file)])]
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
   :artist/name    "Fusspot"
   ;; The single link, for surfaces that can only hold one -- the character
   ;; page credit is a 100px strip. Her homepage, because it is the list she
   ;; maintains, so it cannot go stale the way a copied handle can.
   :artist/link    "https://fusspot.rip/"
   ;; The full set, for the builder, which has room. Chosen with her rather
   ;; than harvested: not every handle she has, the ones she wants on this.
   ;; Twitch is where she does most of the work, so it is not an afterthought
   ;; here -- someone who liked the art can go and watch it being made.
   ;; :link/icon names a mark in /image/social. A link with no icon falls back
   ;; to its label as text, so adding a service nobody has drawn yet degrades
   ;; to a word rather than to an empty box.
   ;; Her site and her Twitch, which is what she asked for. The bluesky and
   ;; kofi marks stay in /image/social -- they cost nothing sitting there and
   ;; the next artist may want them.
   ;; :link/color is the mark's own brand colour, and it is worn at REST, not
   ;; only on hover. A credit rendered in the same quiet grey as everything
   ;; around it is a credit designed to be skipped; the eye finds a purple
   ;; Twitch mark and slides straight past a grey globe. Site takes the app's
   ;; amber because her homepage has no mark of its own and it is the primary
   ;; link.
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
  "Replace the runtime artist overrides. `m` is {artist-id {field value}};
   only :artist/name, :artist/link and :artist/license are honoured."
  [m]
  (reset! artist-overrides
          (into {}
                (for [[id fields] m
                      :let [keep-fields (select-keys fields [:artist/name
                                                             :artist/link
                                                             :artist/links
                                                             :artist/license])]
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
                      ;; Resolve through the registry by asset id first, and
                      ;; only then fall back to whatever artist the saved
                      ;; portrait names. A portrait is client-supplied data
                      ;; stored as EDN: trusting its :artist/id meant deleting
                      ;; that one key detached the credit from art that is
                      ;; plainly in the registry -- on the page, on the sheet,
                      ;; and in the picture itself.
                      (keep (fn [[layer-key sel]]
                              (or (artist-for-asset layer-key (:asset/id sel))
                                  (:artist/id sel))))
                        (current-layers layers-selection))]
      (into []
            (comp (map :artist/id) (filter in-use?))
            registry))))

(defn drawable-layers
  "The entries of a selection whose asset ids actually resolve in the registry.

   A selection can be non-empty and still draw nothing: an id that no longer
   exists, or one from a pack this deployment does not carry. `seq` on the raw
   selection cannot tell the difference, which is how a share card came to
   advertise a portrait the renderer then 404ed on."
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
  "How much of a portrait each layer is, for ordering the credit.

   Roughly by area and by how much the piece defines the character: the head
   is the foundation everything else sits on, the hair is most of the
   silhouette, a nose is a few strokes. Fixed numbers rather than measured
   pixel area, so the browser and the server agree without loading a single
   image, the numbers can be read and tuned, and a large mostly transparent
   asset cannot climb the list.

   A layer missing from this map counts 1."
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
  "The artists on canvas, in the order a credit should name them: most of the
   picture first, like the ingredients on a label.

   Ranked by the total `layer-credit-weight` of the pieces each drew, then by
   how many pieces, then by registry order so the credit never reshuffles
   between two renders of the same portrait. Weight comes before count because
   count misleads: someone who drew the ears, nose and hair bits has three
   pieces and one who drew the head and shirt has two, and the second drew the
   face.

   Pieces are attributed the way `all-artists-for-layers` attributes them --
   through the registry by asset id first -- and an artist not in the registry
   is left out, as there. Each artist map gains :credit/weight and
   :credit/pieces."
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
  "Where an artist's link marks sit around their name in the credit.

   Marks flank the name only when they can do it symmetrically -- an even
   count, up to `max-flanking-marks`, split evenly. Anything else (one link,
   three, more than four) goes on a centred line under the name instead. Both
   outcomes balance on the name; the credit's broken rule is symmetric, and a
   lone mark on one side of it reads as a mistake.

   Returns {:left [...] :right [...] :below [...]}, links in their given order."
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
  "One-line attribution for a composed portrait.

   nil when nothing is composed, or when no artist on canvas has said how they
   want to be credited -- an unnamed artist is skipped rather than given an
   invented byline. Every caller can `when-let` and drop the surface entirely.
   Shared by the PDF export, the share card and the character summary so all
   three credit the same people the same way. Surfaces with a width to fit
   use `credit-names` + `fit-credit` instead."
  [portrait]
  (format-credit (credit-names portrait)))

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
  "Which color slot each LAYER draws its base tint from. nil = the layer has no
   slot of its own and falls back to its category tint.

   A layer-wide rule is not enough for every layer. The mouth carries three
   assets that want three different things -- a bare line, lips, and teeth --
   so the lipped one names its own slot with :asset/slot and slot-for-asset
   prefers that. See `lips` in color-slot-order."
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
  "How a renderer should apply `base-tint` to this asset.

   :multiply  -- the default. Lay the colour down through the art's alpha and
                 multiply the drawing back over it, so lines stay lines.
   :colorize  -- map the art's own luminance through the colour. For art drawn
                 as a hueless ramp: the irises, and the lips.
   :as-drawn  -- do not tint at all. Teeth are not lips.

   Assets that name a conditional slot are colorized, because that is what
   naming one is for; an asset with no slot at all is left alone. Returning a
   MODE rather than a boolean is what lets the mouth's three pieces differ."
  [layer-key asset]
  (let [slot (slot-for-asset layer-key asset)]
    (cond
      (nil? slot) :as-drawn
      (contains? conditional-slots slot) :colorize
      (= :eyes slot) :colorize
      :else :multiply)))

(defn tint-gamma
  "The luminance gamma for a :colorize asset.

   Not one number, because the art it applies to sits at opposite ends of the
   tonal range: the irises are drawn near-black and need a gamma below 1 to
   lift them off the floor, while the lips are drawn light -- 75% of that
   asset is above the ramp's midpoint -- and need one above 1 to pull them
   down into the colour. A single value washes out whichever it was not
   chosen for."
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
           (layers-in-slot slot)))
