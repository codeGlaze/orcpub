(ns orcpub.dnd.e5.portrait-assets
  "Data-only registry of paper-doll portrait layers, per contributing artist.

   The compositor stacks assets in fixed z-order (`layer-order` below,
   bottom → top). Every asset is `{:asset/id … :asset/url …}` — the
   compositor renders <img src=url> per selected layer, absolute-positioned
   in a common frame. For the MVP the placeholder pack ships inline SVG
   data URIs so the feature is functional today; when the real illustrator
   commits PNGs under resources/public/image/portraits/, add or replace
   entries in `registry` with `{:asset/url \"/image/portraits/…\"}` — no
   other change needed.

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

;; ---------- placeholder SVG art (MVP) ----------

(defn- svg-uri
  "Wrap raw SVG markup as a utf-8 data URI. Only characters that require
   escaping in this context are `#` (encoded as `%23`) and the URL-reserved
   set — the paths below deliberately avoid the latter."
  [markup]
  (str "data:image/svg+xml;utf8,"
       (s/replace markup "#" "%23")))

(defn- svg-shape
  "Build an SVG placeholder shape at the compositor's common 400x500
   viewBox, filled with the layer's category tint."
  [layer-key path-d]
  (svg-uri
    (str "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 500'>"
         "<path d='" path-d
         "' fill='" (layer-colors layer-key)
         "' stroke='" (layer-colors layer-key)
         "' stroke-width='2' stroke-linejoin='round' opacity='0.9'/>"
         "</svg>")))

;; Per-layer placeholder paths. Real art will drop into the same layer
;; slots and stack in the same order — the shapes here just occupy the
;; regions the real assets will occupy.
(def ^:private placeholder-shapes
  {:hair-bits  [{:id :hair-bits-fringe
                 :label "Fringe"
                 :d "M120,180 Q200,140 280,180 L280,192 Q200,150 120,192 Z"}]
   :hair-back  [{:id :hair-back-full
                 :label "Full"
                 :d "M110,190 Q110,110 200,100 Q290,110 290,190 L306,340 L94,340 Z"}
                {:id :hair-back-short
                 :label "Short"
                 :d "M120,190 Q120,120 200,110 Q280,120 280,190 L280,260 L120,260 Z"}]
   :head       [{:id :head-oval
                 :label "Oval"
                 :d "M200,110 C260,110 285,155 285,205 C285,265 250,300 200,300 C150,300 115,265 115,205 C115,155 140,110 200,110 Z M175,290 L175,330 L225,330 L225,290 Z"}
                {:id :head-round
                 :label "Round"
                 :d "M200,110 Q285,110 285,215 Q285,300 200,300 Q115,300 115,215 Q115,110 200,110 Z M175,290 L175,335 L225,335 L225,290 Z"}]
   :shirt      [{:id :shirt-tunic
                 :label "Tunic"
                 :d "M130,362 Q100,430 76,500 L324,500 Q300,430 268,362 L235,352 L200,378 L165,352 Z"}
                {:id :shirt-cloak
                 :label "Cloak"
                 :d "M120,360 Q80,410 60,500 L340,500 Q320,410 280,360 L240,340 L200,360 L160,340 Z"}]
   :hair-front [{:id :hair-front-swept
                 :label "Swept"
                 :d "M118,190 Q108,150 128,124 L200,148 L272,124 Q292,150 282,192 L268,182 Q220,192 200,184 Q180,192 132,182 Z"}]
   :ears       [{:id :ears-small
                 :label "Small"
                 :d "M105,220 Q95,235 102,258 Q112,266 122,258 L122,220 Z M295,220 Q305,235 298,258 Q288,266 278,258 L278,220 Z"}]
   :eyes       [{:id :eyes-focused
                 :label "Focused"
                 :d "M154,196 Q172,182 190,196 M210,196 Q228,182 246,196 M170,200 L174,200 M226,200 L230,200"}
                {:id :eyes-wide
                 :label "Wide"
                 :d "M154,200 Q172,188 190,200 Q172,212 154,200 Z M210,200 Q228,188 246,200 Q228,212 210,200 Z"}]
   :nose       [{:id :nose-slim
                 :label "Slim"
                 :d "M198,214 L196,246 Q200,254 204,246 L202,214 Z"}]
   :mouth      [{:id :mouth-small
                 :label "Small"
                 :d "M184,268 Q200,282 216,268"}]
   :bangs      [{:id :bangs-side-swept
                 :label "Side-swept"
                 :d "M116,178 Q136,132 200,132 Q188,150 168,158 Q142,170 116,190 Z"}]})

(def placeholder-pack
  {:artist/id      :placeholder-pack
   :artist/name    "Placeholder Pack"
   :artist/link    nil
   :artist/license "CC0 — geometric MVP stand-ins"
   :artist/layers
   (reduce-kv
     (fn [m layer-key shapes]
       (assoc m layer-key
              (mapv (fn [{:keys [id label d]}]
                      {:asset/id    id
                       :asset/label label
                       :asset/url   (svg-shape layer-key d)
                       :asset/tags  #{:placeholder layer-key}})
                    shapes)))
     {}
     placeholder-shapes)})

(def registry
  "Contributing artists. Later entries layer on top of earlier ones —
   pickers show every artist's assets for a category, in registry order."
  [placeholder-pack])

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
