(ns orcpub.dnd.e5.portrait
  "Paper-doll character-portrait compositor.

   The drawer opens via [:portrait/open], which seeds a draft in app-db
   (:portrait/draft) from the character's saved ::char5e/portrait. A draft
   is {:layers {layer-key {:artist/id … :asset/id …}}
       :colors {slot hex}
       :tweaks {layer-key {:shade n :override hex}}}
   (see portrait-assets for the color model). Every pick / randomize /
   color change mutates the draft; Save writes it back as an EDN string
   (events.cljs) and closes; Cancel discards it.

   Rendering: each selected layer is a <div> whose CSS mask is the asset
   image and whose background is the layer's effective tint
   (portrait-assets/tint-for), so one asset renders in any hair / skin /
   eye color. `composite` is shared with the character summary."
  (:require [re-frame.core :refer [dispatch subscribe]]
            [re-frame.db]
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-colorize :as colorize]
            [orcpub.dnd.e5.portrait-effects :as fx]
            [orcpub.dnd.e5.portrait-layout :as layout]
            [orcpub.fork.branding :as branding]))

(defn- classes [& cs] (s/join " " (remove nil? cs)))

(defn- target-value [e] (.. e -target -value))

(defn- z-label [layer-key]
  (let [z (.indexOf pa/layer-order layer-key)]
    (str "z" (when (< z 10) "0") z)))

;; ---------------- composite (used by drawer AND summary) ----------------

(defn- as-drawn-style
  "An asset shown exactly as the illustrator made it: a plain background image,
   no mask and no tint. A mask would throw the drawing away and keep only its
   alpha, which is how the teeth came out the colour of the mouth."
  [url z]
  {:position "absolute" :inset 0 :width "100%" :height "100%"
   :background-image (str "url(" url ")")
   :background-size "contain"
   :background-repeat "no-repeat"
   :background-position "center"
   :z-index z
   :pointer-events "none"})

(defn- mask-style
  "A tinted layer, in CSS.

   The art is laid down as a background IMAGE with the colour behind it and
   `background-blend-mode: multiply`, rather than as a mask over a flat colour.
   A mask keeps only the asset's alpha and discards its drawing, so every piece
   of line art rendered as a flat silhouette of itself. Multiply keeps the
   lines and matches what the export and the share card now do.

   The mask is still there underneath, clipping the blend to the asset's own
   shape -- without it the colour would fill the whole box, because a blend
   mode applies everywhere the background does."
  [url tint z]
  {:position "absolute" :inset 0 :width "100%" :height "100%"
   :background-color tint
   :background-image (str "url(" url ")")
   :background-size "contain"
   :background-repeat "no-repeat"
   :background-position "center"
   :background-blend-mode "multiply"
   :-webkit-mask-image    (str "url(" url ")")
   :mask-image            (str "url(" url ")")
   :-webkit-mask-size     "contain"   :mask-size     "contain"
   :-webkit-mask-repeat   "no-repeat" :mask-repeat   "no-repeat"
   :-webkit-mask-position "center"    :mask-position "center"
   :z-index z
   :pointer-events "none"})

;; ---------------- developer: the Eyes 01 far-iris alternative ----------------
;;
;; A switch in the footer's Developer mode tools, remembered per browser, that
;; draws Eyes 01's far iris at the alternative placement in
;; portrait-assets/eyes-01-far-iris-alternative -- so the two can be lived
;; with side by side for a while before one is chosen. It applies only while
;; Developer mode is on, and only here in the browser: the share card and
;; everyone else always get the registry's placement.

(def ^:private iris-alt-key "orcpub.portrait.eyes-01-far-iris")

(defonce iris-alternative?
  (r/atom (try (= "alternative" (.getItem js/localStorage iris-alt-key))
               (catch :default _ false))))

(defn set-iris-alternative! [on]
  (reset! iris-alternative? (boolean on))
  (try (.setItem js/localStorage iris-alt-key (if on "alternative" "artist"))
       (catch :default _ nil)))

(defn- iris-alternative-switch
  "Right under the face it changes, and only while Developer mode is on and
   Eyes 01 -- the style it applies to -- is the one on canvas."
  [portrait]
  (when (and @(subscribe [:orcpub.dnd.e5/dev-mode?])
             (= (:asset-id pa/eyes-01-far-iris-alternative)
                (get-in portrait [:layers :eyes :asset/id])))
    (let [alt? @iris-alternative?]
      [:div.pl-dev-switch {:role "group" :aria-label "Far iris placement (developer)"}
       [:span.pl-dev-switch-label "Far iris"]
       [:button {:type "button" :aria-pressed (str (not alt?))
                 :on-click #(set-iris-alternative! false)} "Artist's"]
       [:button {:type "button" :aria-pressed (str alt?)
                 :on-click #(set-iris-alternative! true)} "Alternative"]])))

(defn- as-placed
  "The asset as it should be drawn: the registry's, unless Developer mode is
   on (`dev?`) and the switch asks for the alternative far iris."
  [asset dev?]
  (if (and dev? @iris-alternative?)
    (pa/with-iris-alternative asset)
    asset))

;; ---------------- colorized layers: irises and lips ----------------
;;
;; CSS has no way to map a drawing's luminance through a colour, so a layer
;; that wants it is drawn on a canvas instead -- the same maths the share card
;; runs (orcpub.dnd.e5.portrait-colorize), at the asset's own size.

(defn colorizes?
  "Whether this asset is drawn by colorizing. An eye style with no placed
   region is multiplied instead -- colouring the whole asset would paint the
   whites and the lashes too. Mirrors portrait-render/colorizes?."
  [layer-key asset]
  (and (= :colorize (pa/render-mode layer-key asset))
       (or (seq (:asset/iris asset))
           (not= :eyes (pa/slot-for-asset layer-key asset)))))

(defn- new-canvas [w h]
  (let [c (.createElement js/document "canvas")]
    (set! (.-width c) w)
    (set! (.-height c) h)
    c))

(defn- trace-ellipse! [ctx {:keys [cx cy rx ry rot]}]
  (.beginPath ctx)
  (.ellipse ctx cx cy rx ry rot 0 (* 2 js/Math.PI))
  (.fill ctx))

(defn- iris-coverage
  "The placed iris region's coverage per pixel, 0..255: filled into a canvas
   (antialiased by the canvas itself), then feathered inward with the same
   blur the share card uses (portrait-colorize/feather)."
  [asset w h]
  (let [m (new-canvas w h)
        ctx (.getContext m "2d")
        shapes (colorize/iris-shapes asset [0 0 w h])]
    (set! (.-fillStyle ctx) "#fff")
    (doseq [{:keys [iris]} shapes] (trace-ellipse! ctx iris))
    (set! (.-globalCompositeOperation ctx) "destination-out")
    (doseq [{:keys [pupil lid]} shapes]
      (trace-ellipse! ctx pupil)
      (when-let [{:keys [x0 y0 mx my x1 y1 top]} lid]
        (.beginPath ctx)
        (.moveTo ctx x0 y0)
        (.quadraticCurveTo ctx mx my x1 y1)
        (.lineTo ctx x1 top)
        (.lineTo ctx x0 top)
        (.closePath ctx)
        (.fill ctx)))
    (let [rgba (.-data (.getImageData ctx 0 0 w h))
          cov (js/Float64Array. (* w h))]
      (dotimes [p (* w h)] (aset cov p (aget rgba (+ 3 (* 4 p)))))
      (colorize/feather cov w h (colorize/feather-radius h)))))

(defn- colorize-image
  "A canvas the size of `img` holding it coloured through `hex`."
  [img asset hex gamma]
  (let [w (.-naturalWidth img) h (.-naturalHeight img)
        c (new-canvas w h)
        ctx (.getContext c "2d")
        _ (.drawImage ctx img 0 0)
        image-data (.getImageData ctx 0 0 w h)
        px (.-data image-data)
        cov (when (seq (:asset/iris asset)) (iris-coverage asset w h))
        [er eg eb] (colorize/hex->rgb hex)]
    (dotimes [p (* w h)]
      (let [i (* 4 p)
            k (if cov (/ (aget cov p) 255) 1)]
        (when (and (pos? (aget px (+ i 3))) (pos? k))
          (let [[r g b] (colorize/colorize-rgb (aget px i) (aget px (+ i 1)) (aget px (+ i 2))
                                               er eg eb gamma k)]
            (aset px i r)
            (aset px (+ i 1) g)
            (aset px (+ i 2) b)))))
    (.putImageData ctx image-data 0 0)
    c))

(defonce ^:private colorized-cache (atom {}))

(defn- colorized
  "`colorize-image`, remembered: dragging a colour picker re-renders the
   drawer many times a second, and each asset/colour pair only needs doing
   once. Kept small -- a picker drag walks through many colours."
  [img asset hex gamma]
  (let [k [(:asset/url asset) hex gamma (:asset/iris asset)]]
    (or (get @colorized-cache k)
        (let [c (colorize-image img asset hex gamma)]
          (swap! colorized-cache #(assoc (if (> (count %) 48) {} %) k c))
          c))))

(defonce ^:private loaded-images (atom {}))

(defn- with-image
  "Call `f` with the loaded image for `url`, now if it is already loaded."
  [url f]
  (if-let [img (get @loaded-images url)]
    (f img)
    (let [img (js/Image.)]
      (set! (.-onload img) #(do (swap! loaded-images assoc url img) (f img)))
      (set! (.-src img) url))))

(defn- colorized-layer
  "A layer the drawer draws on a canvas rather than in CSS, sized by
   object-fit: contain so it sits exactly where the CSS layers do."
  [asset tint gamma z]
  (let [node (atom nil)
        paint! (fn [asset tint gamma]
                 (with-image (:asset/url asset)
                   (fn [img]
                     (when-let [^js el @node]
                       (let [src (colorized img asset tint gamma)]
                         (set! (.-width el) (.-width src))
                         (set! (.-height el) (.-height src))
                         (.drawImage (.getContext el "2d") src 0 0))))))]
    (r/create-class
     {:component-did-mount (fn [_] (paint! asset tint gamma))
      :component-did-update (fn [this _]
                              (let [[_ asset tint gamma] (r/argv this)]
                                (paint! asset tint gamma)))
      :reagent-render
      (fn [_ _ _ z]
        [:canvas.portrait-layer.portrait-layer-colorized
         {:ref #(reset! node %)
          :style {:position "absolute" :inset 0 :width "100%" :height "100%"
                  :object-fit "contain" :z-index z :pointer-events "none"}}])})))

(defn- fill-whites!
  "Fill the whites of an eye style drawn without them, into `ctx`, for the
   asset drawn into `rect`."
  [ctx asset rect colour]
  (set! (.-fillStyle ctx) colour)
  (doseq [[[x0 y0] [mx my x1 y1] [lx1 ly1] [lmx lmy lx0 ly0]] (colorize/whites-outlines asset rect)]
    (.beginPath ctx)
    (.moveTo ctx x0 y0)
    (.quadraticCurveTo ctx mx my x1 y1)
    (.lineTo ctx lx1 ly1)
    (.quadraticCurveTo ctx lmx lmy lx0 ly0)
    (.closePath ctx)
    (.fill ctx)))

(defn- whites-layer
  "The whites under an eye style drawn without them: a canvas at the art's own
   size, sized by object-fit like every other layer so it lines up with it."
  [asset colour z]
  (let [node (atom nil)
        paint! (fn [asset colour]
                 (with-image (:asset/url asset)
                   (fn [img]
                     (when-let [^js el @node]
                       (let [w (.-naturalWidth img) h (.-naturalHeight img)]
                         (set! (.-width el) w)
                         (set! (.-height el) h)
                         (fill-whites! (.getContext el "2d") asset [0 0 w h] colour))))))]
    (r/create-class
     {:component-did-mount (fn [_] (paint! asset colour))
      :component-did-update (fn [this _] (let [[_ asset colour] (r/argv this)] (paint! asset colour)))
      :reagent-render
      (fn [_ _ z]
        [:canvas.portrait-layer.portrait-layer-whites
         {:ref #(reset! node %)
          :style {:position "absolute" :inset 0 :width "100%" :height "100%"
                  :object-fit "contain" :z-index z :pointer-events "none"}}])})))

;; ---------------- hair ombre and cast shadows (portrait-effects) ----------------
;;
;; The same maths the share card runs, on a canvas at the art's own size.

(defn- pixels-of
  "An image's RGBA pixels at `w` x `h`."
  [img w h]
  (let [c (new-canvas w h) g (.getContext c "2d")]
    (.drawImage g img 0 0 w h)
    (.-data (.getImageData g 0 0 w h))))

(defn- alpha-fn [rgba] (fn [i] (aget rgba (+ 3 (* 4 i)))))

(defonce ^:private crown-cache (atom {}))

(defn- crown-of
  "Where the hair grows from, on this head image."
  [head w h]
  (let [k [(.-src head) w h]]
    (if (contains? @crown-cache k)
      (get @crown-cache k)
      (let [c (fx/crown (alpha-fn (pixels-of head w h)) w h)]
        (swap! crown-cache assoc k c)
        c))))

(defn- ombre-image
  "A canvas the size of `img` holding the hair piece coloured root to tip."
  [img head layer-key root-hex settings]
  (let [w (.-naturalWidth img) h (.-naturalHeight img)
        c (new-canvas w h) ctx (.getContext c "2d")
        _ (.drawImage ctx img 0 0)
        image-data (.getImageData ctx 0 0 w h)
        px (.-data image-data)
        root0 (colorize/hex->rgb root-hex)
        [root tip] (fx/layer-colours layer-key root0 (or (some-> (:tip settings) colorize/hex->rgb) root0) settings)
        pix (fx/pixel-fn root tip settings
                         (fx/gradient-frame (alpha-fn px) w h (when head (crown-of head w h)) (:angle settings))
                         layer-key)]
    (dotimes [p (* w h)]
      (let [i (* 4 p)]
        (when (pos? (aget px (+ i 3)))
          (let [rgb (pix (mod p w) (quot p w)
                         (bit-or (bit-shift-left (aget px i) 16) (bit-shift-left (aget px (+ i 1)) 8) (aget px (+ i 2))))]
            (aset px i (bit-and (bit-shift-right rgb 16) 0xff))
            (aset px (+ i 1) (bit-and (bit-shift-right rgb 8) 0xff))
            (aset px (+ i 2) (bit-and rgb 0xff))))))
    (.putImageData ctx image-data 0 0)
    c))

(defonce ^:private ombre-cache (atom {}))

(defn- ombre
  "`ombre-image`, remembered; dragging an ombre slider redraws constantly."
  [img head layer-key root-hex settings]
  (let [k [(.-src img) (some-> head .-src) layer-key root-hex settings]]
    (or (get @ombre-cache k)
        (let [c (ombre-image img head layer-key root-hex settings)]
          (swap! ombre-cache #(assoc (if (> (count %) 48) {} %) k c))
          c))))

(defn- with-images
  "Call `f` with the loaded images for `urls`, in order (nil for a nil url)."
  [urls f]
  (let [urls (vec urls) out (atom (vec (repeat (count urls) nil))) left (atom (count (remove nil? urls)))]
    (if (zero? @left)
      (f @out)
      (doseq [[i u] (map-indexed vector urls) :when u]
        (with-image u (fn [img]
                        (swap! out assoc i img)
                        (when (zero? (swap! left dec)) (f @out))))))))

(defn- ombre-layer
  "A hair piece, drawn on a canvas so it can take the ombre."
  [layer-key asset root-hex settings head-url z]
  (let [node (atom nil)
        paint! (fn [layer-key asset root-hex settings head-url]
                 (with-images [(:asset/url asset) head-url]
                   (fn [[img head]]
                     (when-let [^js el @node]
                       (let [src (ombre img head layer-key root-hex settings)]
                         (set! (.-width el) (.-width src))
                         (set! (.-height el) (.-height src))
                         (.drawImage (.getContext el "2d") src 0 0))))))]
    (r/create-class
     {:component-did-mount (fn [_] (paint! layer-key asset root-hex settings head-url))
      :component-did-update (fn [this _] (let [[_ lk a r s hu] (r/argv this)] (paint! lk a r s hu)))
      :reagent-render
      (fn [_ _ _ _ _ z]
        [:canvas.portrait-layer.portrait-layer-hair
         {:ref #(reset! node %)
          :style {:position "absolute" :inset 0 :width "100%" :height "100%"
                  :object-fit "contain" :z-index z :pointer-events "none"}}])})))

(defn- combined-alpha [imgs w h]
  (let [out (js/Float64Array. (* w h))]
    (doseq [img imgs :when img]
      (let [px (pixels-of img w h)]
        (dotimes [i (* w h)]
          (aset out i (max (aget out i) (aget px (+ 3 (* 4 i))))))))
    out))

(defn- shadow-image
  "A canvas of the shade the overhanging hair throws onto the skin, to be
   laid over the portrait with multiply: white where there is none."
  [casters skins hairs light]
  (let [ref (first (remove nil? casters))
        w (.-naturalWidth ref) h (.-naturalHeight ref)
        k (fx/shadow-map (combined-alpha casters w h) (combined-alpha skins w h) (combined-alpha hairs w h) w h light)
        c (new-canvas w h) ctx (.getContext c "2d")
        image-data (.createImageData ctx w h)
        px (.-data image-data)]
    (dotimes [p (* w h)]
      (let [kk (aget k p)]
        (when (pos? kk)
          (let [[fr fg fb] (fx/shadow-factors kk) i (* 4 p)]
            (aset px i (* 255 fr)) (aset px (+ i 1) (* 255 fg)) (aset px (+ i 2) (* 255 fb))
            (aset px (+ i 3) 255)))))
    (.putImageData ctx image-data 0 0)
    c))

(defonce ^:private shadow-cache (atom {}))

(defn- shadow [casters skins hairs light]
  (let [k (conj (mapv #(mapv (fn [i] (some-> i .-src)) %) [casters skins hairs]) light)]
    (or (get @shadow-cache k)
        (let [c (shadow-image casters skins hairs light)]
          (swap! shadow-cache #(assoc (if (> (count %) 16) {} %) k c))
          c))))

(defn- shadow-urls
  "The images the cast shadow needs, as [casters skin hair] url lists, or nil
   when nothing selected hangs over the face."
  [portrait]
  (let [sel (fn [ks pred] (vec (keep #(let [a (pa/selected-asset portrait %)] (when (and a (pred a)) (:asset/url a))) ks)))
        casters (sel pa/layer-order fx/casts-shadow?)]
    (when (seq casters)
      [casters (sel [:head :ears] any?) (sel [:scalp :hair-front :bangs] any?)])))

(defn- shadow-layer
  "The cast shadow, over everything, multiplied: it is already confined to
   skin the hair does not cover, so it darkens nothing else."
  [[caster-urls skin-urls hair-urls :as urls] light]
  (let [node (atom nil)
        paint! (fn [[cu su hu] light]
                 (let [n1 (count cu) n2 (count su)]
                   (with-images (concat cu su hu)
                     (fn [imgs]
                       (when-let [^js el @node]
                         (let [src (shadow (subvec imgs 0 n1) (subvec imgs n1 (+ n1 n2)) (subvec imgs (+ n1 n2)) light)]
                           (set! (.-width el) (.-width src))
                           (set! (.-height el) (.-height src))
                           (.drawImage (.getContext el "2d") src 0 0)))))))]
    (r/create-class
     {:component-did-mount (fn [_] (paint! urls light))
      :component-did-update (fn [this _] (let [[_ u l] (r/argv this)] (paint! u l)))
      :reagent-render
      (fn [_ _]
        [:canvas.portrait-layer.portrait-layer-shadow
         {:ref #(reset! node %)
          :style {:position "absolute" :inset 0 :width "100%" :height "100%"
                  :object-fit "contain" :z-index 100 :pointer-events "none"
                  :mix-blend-mode "multiply"}}])})))

(defn composite
  "Stacked, tinted portrait for a `portrait` map (see ns doc). `attrs`
   (optional) merges into the outer div so callers can size/position it."
  ([portrait] (composite portrait nil))
  ([portrait attrs]
   (let [layers (:layers portrait)
         ;; read here, not inside the lazy seq below: Reagent only tracks what
         ;; is deref'd while the render runs, and the seq is realised after,
         ;; so a switch read in there would not redraw the face
         dev? @(subscribe [:orcpub.dnd.e5/dev-mode?])
         _ @iris-alternative?
         whites (pa/whites-colour portrait)
         ombre-settings (fx/ombre-settings portrait)
         head-url (:asset/url (pa/selected-asset portrait :head))
         shadows (shadow-urls portrait)]
     [:div.portrait-composite
      (merge {:style {:position "relative" :width "100%" :height "100%"}} attrs)
      (map-indexed
        (fn [z layer-key]
          (when-let [asset (some->> (get layers layer-key)
                                    :asset/id
                                    (pa/asset-by-id layer-key))]
            (if (colorizes? layer-key asset)
              ^{:key layer-key}
              [:<>
               ;; filled-in whites go under the eye they belong to
               (when (:asset/whites asset)
                 [whites-layer (as-placed asset dev?) whites z])
               [colorized-layer (as-placed asset dev?)
                (pa/tint-for portrait layer-key)
                (pa/effective-gamma portrait layer-key asset) z]]
              (if (fx/hair-layer? layer-key)
                ^{:key layer-key}
                [ombre-layer layer-key asset (pa/tint-for portrait layer-key) ombre-settings head-url z]
                ^{:key layer-key}
                [:div.portrait-layer
                 {:style (if (= :as-drawn (pa/render-mode layer-key asset))
                           (as-drawn-style (:asset/url asset) z)
                           (mask-style (:asset/url asset)
                                       (pa/tint-for portrait layer-key) z))}]))))
        pa/layer-order)
      (when shadows ^{:key "shadow"} [shadow-layer shadows (:light ombre-settings)])])))

;; ---------------- rasterization (for PDF export) ----------------
;;
;; On screen a layer is a CSS mask -- shape from the asset's alpha, color from
;; background-color. Neither PDFBox nor a canvas understands that, so an export
;; has to bake it: for each layer draw the asset, switch to "source-in", and
;; flood-fill the tint, which paints the color only where the asset is opaque.
;; Compositing those in z-order reproduces exactly what the drawer shows.

(def ^:private raster-width 600)
(def ^:private raster-height 750)   ;; 4:5, matching the on-screen frame

(defn- load-image [url]
  (js/Promise.
    (fn [resolve _reject]
      (let [img (js/Image.)]
        (set! (.-onload img) #(resolve img))
        ;; A layer that will not decode is skipped, not fatal -- the rest of
        ;; the portrait is still worth printing.
        (set! (.-onerror img) #(resolve nil))
        (set! (.-src img) url)))))

(defn- draw-credit!
  "Burn the artist credit into the baked picture.

   The sheet and the page can each carry a credit beside the portrait, but the
   composed image is what gets passed around -- saved off a share card, lifted
   out of a PDF -- and it arrives detached from both. A caption in the pixels
   travels with it. Dark fill under a white outline, because a transparent PNG
   may land on a white page or a dark chat client and one of the two always
   reads."
  [ctx names w h]
  (let [{:keys [halo center-x baseline outline fill]} (layout/credit-layout w h)
        font-at #(str "italic " % "px '" layout/credit-font-family "', Georgia, serif")
        {:keys [text size]} (layout/fit-credit
                             (pa/credit-variants names) w h
                             (fn [text size]
                               (set! (.-font ctx) (font-at size))
                               (.-width (.measureText ctx text))))
        rgba (fn [[r g b :as c]]
               (str "rgba(" r "," g "," b "," (layout/alpha->unit c) ")"))]
    (set! (.-font ctx) (font-at size))
    (set! (.-textAlign ctx) "center")
    (set! (.-textBaseline ctx) "alphabetic")
    (set! (.-lineWidth ctx) (* 2 halo))
    (set! (.-lineJoin ctx) "round")
    (set! (.-strokeStyle ctx) (rgba outline))
    (.strokeText ctx text center-x baseline)
    (set! (.-fillStyle ctx) (rgba fill))
    (.fillText ctx text center-x baseline)))

(defn- load-credit-font
  "Resolves once the credit face is usable by a canvas.

   Resolves either way: a webfont that will not fetch should cost the
   typeface, not the export."
  []
  (if-let [fonts (.-fonts js/document)]
    (-> (.load fonts (str "italic 20px '" layout/credit-font-family "'"))
        (.then (fn [_] true))
        (.catch (fn [_] false)))
    (js/Promise.resolve false)))

(defn rasterize
  "Bake `portrait` into a PNG. Resolves to base64 (no data: prefix), or nil
   when there is nothing to draw or the browser cannot do it."
  [portrait]
  (let [selected (keep (fn [k]
                         (when-let [asset (some->> (get-in portrait [:layers k])
                                                   :asset/id
                                                   (pa/asset-by-id k))]
                           [k asset]))
                       pa/layer-order)]
    (if (empty? selected)
      (js/Promise.resolve nil)
      (-> (js/Promise.all
            (clj->js (conj (mapv (fn [[_ a]] (load-image (:asset/url a))) selected)
                           ;; a canvas substitutes silently for a face that is
                           ;; not resident yet, so wait for it like an image
                           (load-credit-font))))
          (.then
            (fn [results]
              (let [imgs (.slice results 0 (count selected))]
              (try
                (let [canvas (.createElement js/document "canvas")
                      _ (set! (.-width canvas) raster-width)
                      _ (set! (.-height canvas) raster-height)
                      ctx (.getContext canvas "2d")
                      tmp (.createElement js/document "canvas")
                      _ (set! (.-width tmp) raster-width)
                      _ (set! (.-height tmp) raster-height)
                      tctx (.getContext tmp "2d")
                      img-of (into {} (map (fn [[[k _] img]] [k img]) (map vector selected (array-seq imgs))))
                      settings (fx/ombre-settings portrait)]
                  (doseq [[[layer-key asset] img] (map vector selected (array-seq imgs))
                          :when img]
                    ;; filled-in whites go down first, under the eye art
                    (when (:asset/whites asset)
                      (fill-whites! ctx (as-placed asset (:dev-mode? @re-frame.db/app-db))
                                    (layout/contain-rect (.-naturalWidth img) (.-naturalHeight img)
                                                         raster-width raster-height)
                                    (pa/whites-colour portrait)))
                    (.clearRect tctx 0 0 raster-width raster-height)
                    (set! (.-globalCompositeOperation tctx) "source-over")
                    ;; Fit, do not stretch: the drawer composites with CSS
                    ;; `mask-size: contain`, so filling the frame here would
                    ;; hand the PDF a differently-shaped face from the one on
                    ;; screen -- 10% wider, for 8:11 art in a 4:5 frame.
                    (let [[x y dw dh] (layout/contain-rect (.-naturalWidth img)
                                                    (.-naturalHeight img)
                                                    raster-width raster-height)]
                      (.drawImage tctx img x y dw dh))
                    ;; Tint by MULTIPLY, not by masking. source-in reads the
                    ;; asset's alpha and throws its RGB away, so line art came
                    ;; out as one flat colour -- the same picture as its own
                    ;; silhouette. Multiply keeps the lines: white fill takes
                    ;; the colour, black lines stay black.
                    ;;
                    ;; :as-drawn skips it entirely. Teeth are white because
                    ;; they were drawn white; tinting them through the mouth's
                    ;; category colour is what made them red.
                    (cond
                      (= :as-drawn (pa/render-mode layer-key asset)) nil

                      ;; irises and lips: coloured through their luminance,
                      ;; the same picture the drawer and the share card show
                      (colorizes? layer-key asset)
                      (let [[x y dw dh] (layout/contain-rect (.-naturalWidth img)
                                                             (.-naturalHeight img)
                                                             raster-width raster-height)]
                        (.clearRect tctx 0 0 raster-width raster-height)
                        (.drawImage tctx (colorized img
                                                    ;; not a component, so no subscription:
                                                    ;; read Developer mode once, at bake time
                                                    (as-placed asset (:dev-mode? @re-frame.db/app-db))
                                                    (pa/tint-for portrait layer-key)
                                                    (pa/effective-gamma portrait layer-key asset))
                                    x y dw dh))

                      ;; hair: root to tip, the same as the drawer and the card
                      (fx/hair-layer? layer-key)
                      (let [[x y dw dh] (layout/contain-rect (.-naturalWidth img)
                                                             (.-naturalHeight img)
                                                             raster-width raster-height)]
                        (.clearRect tctx 0 0 raster-width raster-height)
                        (.drawImage tctx (ombre img (img-of :head) layer-key (pa/tint-for portrait layer-key) settings)
                                    x y dw dh))

                      :else
                      (do
                      (set! (.-globalCompositeOperation tctx) "multiply")
                      (set! (.-fillStyle tctx) (pa/tint-for portrait layer-key))
                      (.fillRect tctx 0 0 raster-width raster-height)
                      ;; multiply paints the whole rect, including where the
                      ;; asset is transparent, so put the asset's own alpha back
                      (set! (.-globalCompositeOperation tctx) "destination-in")
                      (let [[x y dw dh] (layout/contain-rect (.-naturalWidth img)
                                                             (.-naturalHeight img)
                                                             raster-width raster-height)]
                        (.drawImage tctx img x y dw dh))))
                    (.drawImage ctx tmp 0 0))
                  ;; the cast shadow after every layer, before the credit
                  (when-let [[cu su hu] (shadow-urls portrait)]
                    (let [by-url (into {} (map (fn [[_ a] img] [(:asset/url a) img]) selected (array-seq imgs)))
                          casters (keep by-url cu)]
                      (when (seq casters)
                        (let [s (shadow (vec casters) (vec (keep by-url su)) (vec (keep by-url hu)) (:light settings))
                              [x y dw dh] (layout/contain-rect (.-width s) (.-height s) raster-width raster-height)]
                          (set! (.-globalCompositeOperation ctx) "multiply")
                          (.drawImage ctx s x y dw dh)
                          (set! (.-globalCompositeOperation ctx) "source-over")))))
                  (when-let [names (not-empty (pa/credit-names portrait))]
                    ;; The face has to be resident before fillText or the
                    ;; canvas silently substitutes; document.fonts.load is
                    ;; awaited up in `rasterize` before we get here.
                    (draw-credit! ctx names raster-width raster-height))
                  (some-> (.toDataURL canvas "image/png")
                          (s/split #",")
                          second))
                ;; Never let a failed bake block the export -- the sheet is
                ;; worth more than the picture.
                (catch :default e
                  (js/console.warn "portrait rasterize failed" e)
                  nil)))))
          (.catch (fn [_] nil))))))

;; ---------------- drawer chrome ----------------

(def empty-slot-styles
  "The empty portrait slot.

   It used to be lit by a radial gradient -- a spotlight centred at 50% 35% --
   which read as a different design language from everything around it: the app
   panels are flat or top-down, and the accents are amber. A centred glow in a
   cool slate belongs to some other screen.

   So the ground is a shallow top-down gradient like the app's other panels, and
   what fills the space is the site's own logo, debossed: masked to 5% white with
   a one-pixel dark shadow under it, so it reads as a detail lifted out of the
   wall rather than a picture in its own right. Loud enough that the slot is not
   empty-looking, quiet enough that it never competes with the art that replaces
   it.

   The mark comes from branding/logo-path, not a literal, so a fork gets its own
   rather than ours -- the same reason the site mark on a shared portrait is read
   from branding.

   Only the EMPTY states carry it: .pl-empty-hint and .pl-thumb-empty both render
   solely when there is nothing composed, so a portrait never has a logo showing
   through its transparent parts."
  (str "
.pl-portrait-frame, .pl-thumb-empty {
  background: linear-gradient(180deg, #1b2230 0%, #141a25 100%);
}
.pl-thumb-empty { position: relative; }
.pl-empty-hint::before, .pl-thumb-empty::before {
  content: ''; position: absolute; pointer-events: none;
  left: 50%; top: 44%; transform: translate(-50%, -50%);
  width: 56%; height: 34%;
  background-color: rgba(255,255,255,0.055);
  -webkit-mask-image: url(" branding/logo-path "); mask-image: url(" branding/logo-path ");
  -webkit-mask-size: contain; mask-size: contain;
  -webkit-mask-repeat: no-repeat; mask-repeat: no-repeat;
  -webkit-mask-position: center; mask-position: center;
  /* on the masked shape, not a box: this is what makes it read as debossed */
  filter: drop-shadow(0 1px 0 rgba(0,0,0,0.5));
}
/* The big frame also carries the 'pick a layer' hint, and a wordmark directly
   behind type muddies both. The mark sits high, the hint sits low. */
.pl-empty-hint { align-content: end; padding-bottom: 34%; }
.pl-empty-hint::before { top: 34%; width: 50%; height: 26%; }
.pl-thumb-empty::before { top: 40%; width: 62%; height: 38%; }
"))

(def drawer-styles "
/* The baked portrait credit is drawn on a canvas, and a canvas can only use
   faces the document has loaded. Vollkorn is the face the PDF itself is set
   in, so the caption inside the exported picture matches the sheet around it
   rather than falling back to whatever the viewer's OS offers. Fetched only
   when something actually asks for it -- i.e. on an export.

   A subset, because the whole face is 357 KB to draw one short line. Cut to
   Latin-1 plus the separators and quotes a name might carry: 197 glyphs,
   26 KB, kerning kept. The full TTF stays as the fallback -- it is on disk
   anyway for PDFBox, and it covers a name the subset does not.

   Regenerate with fonttools + brotli -- one line, because a backslash
   continuation is not a legal escape inside this Clojure string:
     pyftsubset resources/public/fonts/Vollkorn-Italic.ttf --flavor=woff2 --unicodes='U+0020-007E,U+00A0-00FF,U+2013-2014,U+2018-2019,U+201C-201D' --output-file=resources/public/fonts/Vollkorn-Italic-credit.woff2 */
@font-face {
  font-family: 'Vollkorn';
  src: url('/fonts/Vollkorn-Italic-credit.woff2') format('woff2'),
       url('/fonts/Vollkorn-Italic.ttf') format('truetype');
  font-style: italic;
  font-weight: 400;
  font-display: swap;
}
.pl-backdrop {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.55);
  z-index: 9998;
  animation: pl-fade 160ms ease-out;
}
.pl-drawer {
  position: fixed; top: 0; right: 0; bottom: 0;
  width: 600px; max-width: 100vw;
  background: #131924;
  border-left: 1px solid rgba(240, 161, 0, 0.16);
  box-shadow: -30px 0 60px -20px rgba(0, 0, 0, 0.6);
  display: flex; flex-direction: column;
  z-index: 9999;
  animation: pl-slide 220ms cubic-bezier(.22, .8, .36, 1);
  font-family: 'Open Sans', system-ui, sans-serif;
  color: #ebeef4;
}
@keyframes pl-fade { from { opacity: 0; } to { opacity: 1; } }
@keyframes pl-slide { from { transform: translateX(32px); opacity: 0.6; } to { transform: none; opacity: 1; } }

.pl-drawer-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: #0e131a;
  flex-shrink: 0;
}
.pl-drawer-title { font: 600 18px/1 'Open Sans', system-ui, sans-serif; }
.pl-drawer-title-rune { color: #f0a100; font-style: italic; padding-right: 5px; }
.pl-drawer-close {
  display: grid; place-items: center;
  width: 36px; height: 36px; border-radius: 8px;
  background: transparent; border: 1px solid rgba(255,255,255,0.06);
  color: #8b95a5; cursor: pointer;
  font-size: 16px; line-height: 1;
}
.pl-drawer-close:hover { color: #ffcc5e; border-color: #f0a100; }

.pl-drawer-body {
  flex: 1; overflow: hidden;
  display: grid; grid-template-columns: 280px 1fr;
}
.pl-canvas-side {
  background: #0e131a;
  border-right: 1px solid rgba(255,255,255,0.06);
  padding: 16px;
  display: flex; flex-direction: column; gap: 10px;
  overflow-y: auto;
}
.pl-pickers-side {
  overflow-y: auto; padding: 12px 14px;
  display: flex; flex-direction: column; gap: 8px;
  background: #131924;
}
@media (max-width: 700px) {
  .pl-drawer { width: 100vw; }
  .pl-drawer-body { display: block; overflow-y: auto; }
  .pl-canvas-side { border-right: none; border-bottom: 1px solid rgba(255,255,255,0.06); overflow: visible; }
  .pl-pickers-side { overflow: visible; }
  .pl-portrait-frame { width: 200px !important; height: 260px !important; }
}

.pl-portrait-frame {
  /* Fills the column rather than sitting at a hard 208px inside a 247px one.
     Centring a narrower box left the art the only thing in the column whose
     edges lined up with nothing -- every button, the seed row, the colour
     strip and the credit all run the full width. The ratio is the frame's,
     4:5, so height follows width instead of being pinned beside it. */
  width: 100%; aspect-ratio: 4 / 5;
  /* the 1px border is otherwise added OUTSIDE the 100%, so the frame came out
     2px wider than everything it is meant to line up with */
  box-sizing: border-box;
  background: radial-gradient(circle at 50% 35%, #202939, #131924 60%, #0f141c);
  border: 1px solid rgba(240, 161, 0, 0.16); border-radius: 10px;
  align-self: stretch; position: relative; overflow: hidden;
  flex-shrink: 0;
}
.pl-empty-hint {
  position: absolute; inset: 0;
  display: grid; place-content: center;
  color: #616a7a; font: italic 12px/1.5 'Open Sans', system-ui, sans-serif;
  text-align: center; padding: 10px;
}
.pl-toolbar { display: flex; flex-direction: column; gap: 8px; }
/* Matched to the app's .form-button (styles/core.clj): 5px radius, uppercase,
   600/12px. The panel had 8px, title case and 500/13px -- close enough to read
   as a mistake rather than a distinction. */
.pl-btn {
  display: inline-flex; align-items: center; justify-content: center;
  gap: 7px; padding: 10px 15px; border-radius: 5px;
  border: 1px solid transparent; background: transparent;
  color: #ebeef4; font: 600 12px/1 inherit;
  text-transform: uppercase; letter-spacing: 0.04em;
  cursor: pointer; touch-action: manipulation;
  min-height: 44px;
}
.pl-btn:focus-visible { outline: 2px solid #ffcc5e; outline-offset: 2px; }
.pl-btn-primary {
  background: linear-gradient(to bottom, #f1a20f, #dbab50);
  color: #15202e; font-weight: 700;
  border-color: #b57500;
  box-shadow: 0 6px 14px -8px rgba(240,161,0,0.32);
}
.pl-btn-primary:hover { filter: brightness(1.06); }
.pl-btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.pl-btn-ghost { border-color: rgba(255,255,255,0.10); color: #8b95a5; }
.pl-btn-ghost:hover:not(:disabled) { border-color: #f0a100; color: #ffcc5e; }
.pl-btn-ghost:disabled { opacity: 0.4; cursor: not-allowed; }
.pl-ombre { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; }
.pl-ombre-row { display: grid; grid-template-columns: 54px 1fr 40px; gap: 6px; align-items: center; }
.pl-ombre-row input[type=range] { width: 100%; accent-color: #f0a100; margin: 0; }
.pl-ombre-row select { font: 500 11px/1 inherit; background: #131924; color: #c9d0da; border: 1px solid rgba(255,255,255,0.14); border-radius: 4px; padding: 3px 4px; grid-column: 2 / 4; }
.pl-ombre-label { font: 500 11px/1 inherit; color: #8b95a5; }
.pl-preset.on { box-shadow: 0 0 0 2px #f0a100; }
.pl-preset-none { background: transparent; color: #8b95a5; font: 600 12px/1 inherit; border: 1px dashed rgba(255,255,255,0.3); }
.pl-root.light-theme .pl-ombre-row select { background: #fff; color: #363636; border-color: rgba(0,0,0,0.16); }
.pl-root.light-theme .pl-ombre-row input[type=range] { accent-color: #33658A; }
.pl-dev-switch {
  display: flex; align-items: center; gap: 6px; justify-content: center;
  margin: 8px auto 0; padding: 5px 8px; width: fit-content;
  border: 1px dashed rgba(240,161,0,0.45); border-radius: 6px;
  font: 500 11px/1 inherit; color: #8b95a5;
}
.pl-dev-switch-label { text-transform: uppercase; letter-spacing: 0.08em; font-size: 10px; margin-right: 2px; }
.pl-dev-switch button {
  font: 600 11px/1 inherit; color: #c9d0da; background: transparent; cursor: pointer;
  border: 1px solid rgba(255,255,255,0.14); border-radius: 4px; padding: 4px 8px;
}
.pl-dev-switch button[aria-pressed=true] { background: rgba(240,161,0,0.18); border-color: #f0a100; color: #ffcc5e; }
.pl-dev-switch button:focus-visible { outline: 2px solid #ffcc5e; outline-offset: 1px; }
.pl-root.light-theme .pl-dev-switch button { color: #363636; border-color: rgba(0,0,0,0.16); }
.pl-root.light-theme .pl-dev-switch button[aria-pressed=true] { background: rgba(51,101,138,0.14); border-color: #33658A; color: #2b5677; }
.pl-seed-row {
  display: flex; align-items: center; justify-content: space-between;
  color: #616a7a; font: 500 11px/1 inherit; letter-spacing: 0.06em; text-transform: uppercase;
  margin-top: 4px;
}
.pl-seed-row code {
  font: 500 12px/1 ui-monospace, Menlo, monospace; color: #ffcc5e;
  background: rgba(240,161,0,0.08); border: 1px solid rgba(240,161,0,0.16);
  padding: 3px 7px; border-radius: 4px; text-transform: none; letter-spacing: 0.02em;
}

/* ---- character colors ---- */
.pl-color-strip {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  padding: 8px 10px;
  background: #171e29;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
}
.pl-strip-label, .pl-panel-heading {
  font: 700 9.5px/1 inherit; color: #616a7a;
  letter-spacing: 0.14em; text-transform: uppercase;
}
.pl-strip-label { margin-right: 4px; }
.pl-slot {
  display: inline-flex; align-items: center;
  background: #131924;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 999px;
  padding: 3px 3px 3px 10px; gap: 6px;
}
.pl-slot.on { border-color: #f0a100; box-shadow: 0 0 0 1px rgba(240,161,0,0.24); }
.pl-slot.has-tweaks .pl-slot-swatch { box-shadow: 0 0 0 2px rgba(240,161,0,0.42); }
.pl-slot::after {
  content: '';
  display: inline-block; width: 6px; height: 6px;
  border-right: 1.5px solid #616a7a; border-bottom: 1.5px solid #616a7a;
  transform: rotate(45deg) translateY(-1px);
  margin: 0 6px 0 -2px;
  transition: transform 120ms, border-color 120ms;
}
.pl-slot.open::after { transform: rotate(-135deg); border-color: #ffcc5e; }
.pl-slot-name { font: 500 11px/1 inherit; color: #ebeef4; }
.pl-slot-tweaks {
  display: inline-flex; align-items: center;
  background: rgba(240,161,0,0.16); color: #ffcc5e;
  font: 700 9.5px/1 inherit; letter-spacing: 0.06em;
  padding: 2px 6px 2px 5px; border-radius: 999px;
  margin: 0 2px 0 -2px;
}
.pl-slot-swatch {
  position: relative; display: inline-block;
  width: 28px; height: 28px; border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.15);
  cursor: pointer; padding: 0; background-clip: padding-box;
}
.pl-slot-swatch.unset {
  background: repeating-linear-gradient(45deg, #0e131a 0 4px, rgba(255,255,255,0.05) 4px 8px);
}
.pl-slot-swatch:focus-visible { outline: 2px solid #ffcc5e; outline-offset: 2px; }
.pl-sub-dots { position: absolute; inset: 0; pointer-events: none; }
.pl-sub-dot {
  position: absolute; width: 10px; height: 10px; border-radius: 50%;
  border: 1.5px solid #171e29; box-shadow: 0 0 0 0.5px rgba(0,0,0,0.35);
}
.pl-sub-dot-0 { top: -4px; left: 50%; transform: translateX(-50%); }
.pl-sub-dot-1 { top: 50%; right: -4px; transform: translateY(-50%); }
.pl-sub-dot-2 { bottom: -4px; left: 50%; transform: translateX(-50%); }
.pl-sub-dot-3 { top: 50%; left: -4px; transform: translateY(-50%); }
.pl-slot-clear, .pl-sub-clear {
  display: inline-grid; place-items: center;
  width: 22px; height: 22px; border-radius: 50%;
  background: transparent; border: 1px solid transparent;
  color: #616a7a; cursor: pointer; padding: 0; font-size: 12px; line-height: 1;
}
.pl-slot-clear:hover:not(:disabled), .pl-sub-clear:hover:not(:disabled) { color: #ffcc5e; border-color: rgba(240,161,0,0.16); }
.pl-slot-clear:disabled, .pl-sub-clear:disabled { opacity: 0.25; cursor: default; }

.pl-slot-panel {
  flex-basis: 100%; min-width: 100%;
  display: flex; flex-direction: column; gap: 10px;
  padding: 10px; margin-top: -2px;
  background: #1e2635;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
}
.pl-panel-heading { margin-bottom: 6px; display: block; }
.pl-presets { display: flex; flex-wrap: wrap; gap: 5px; }
.pl-preset {
  width: 26px; height: 26px; border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.12);
  cursor: pointer; padding: 0; position: relative; overflow: hidden;
}
.pl-preset:focus-visible { outline: 2px solid #ffcc5e; outline-offset: 2px; }
.pl-preset.custom { background: conic-gradient(#f0a100, #78d0d4, #c85c5c, #6fbb5a, #7a94b8, #f0a100); }
.pl-preset.custom input, .pl-sub-chip input {
  position: absolute; inset: 0; width: 100%; height: 100%;
  opacity: 0; padding: 0; border: none; cursor: pointer;
}
.pl-sublayers {
  display: flex; flex-direction: column; gap: 8px;
  padding-top: 8px; border-top: 1px dashed rgba(255,255,255,0.06);
}
.pl-sub-row {
  display: grid; grid-template-columns: 22px 1fr 40px 22px;
  gap: 6px; align-items: center;
}
.pl-sub-name {
  grid-column: 1 / -1;
  font: 500 11px/1 inherit; color: #ebeef4;
}
.pl-sub-chip {
  position: relative; display: inline-block;
  width: 22px; height: 22px; border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.15);
  cursor: pointer; overflow: hidden;
}
.pl-sub-chip.shaded { border-color: #f0a100; border-style: dashed; }
.pl-sub-chip.overridden { border-color: #f0a100; box-shadow: 0 0 0 1px rgba(240,161,0,0.4); }
.pl-sub-row input[type=range] { width: 100%; accent-color: #f0a100; height: 22px; margin: 0; }
.pl-sub-depth-icon { font-size: 13px; line-height: 1; color: #8b95a5; text-align: center; }
.pl-sub-shade-val {
  font: 500 10px/1 ui-monospace, Menlo, monospace; color: #8b95a5;
  text-align: right; font-variant-numeric: tabular-nums;
}
.pl-layer-panel {
  margin-top: 10px; padding: 10px 12px;
  background: #1e2635; border: 1px solid rgba(240,161,0,0.16); border-radius: 6px;
}

/* ---- pickers ---- */
.pl-picker {
  background: #171e29; border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px; padding: 10px 12px;
}
.pl-picker-head {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  margin-bottom: 8px;
  position: sticky; top: -1px;
  background: #171e29;
  z-index: 2;
}
.pl-cat {
  display: inline-flex; align-items: center; gap: 6px;
  font: 700 12px/1 inherit; color: #ebeef4;
}
.pl-cat-chip {
  width: 10px; height: 10px; border-radius: 2.5px;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.08);
}
.pl-cat-z {
  font: 500 9.5px/1 ui-monospace, Menlo, monospace;
  color: #616a7a;
  padding: 2px 5px; background: #131924; border-radius: 3px;
  letter-spacing: 0.04em;
}
.pl-tint-chip {
  width: 18px; height: 18px; border-radius: 50%;
  border: 1.5px solid transparent;
  cursor: pointer; padding: 0; margin-left: 4px; flex-shrink: 0;
}
.pl-tint-chip:focus-visible { outline: 2px solid #ffcc5e; outline-offset: 2px; }
.pl-tint-chip.shaded { border-color: #f0a100; border-style: dashed; }
.pl-tint-chip.overridden { border-color: #f0a100; }
.pl-tint-chip.open { box-shadow: 0 0 0 2px rgba(240,161,0,0.32); }
.pl-picker-sel {
  font: italic 12px/1 'Open Sans', system-ui, sans-serif; color: #8b95a5;
  overflow: hidden; white-space: nowrap; text-overflow: ellipsis; max-width: 180px;
}
.pl-swatches {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(56px, 1fr)); gap: 5px;
}
.pl-sw {
  aspect-ratio: 1; border-radius: 6px;
  background: #131924;
  border: 1px solid rgba(255,255,255,0.05);
  display: grid; place-items: center; overflow: hidden;
  cursor: pointer; padding: 0;
  touch-action: manipulation;
}
.pl-sw:focus-visible { outline: 2px solid #ffcc5e; outline-offset: 2px; }
.pl-sw:hover { border-color: #f0a100; }
.pl-sw.selected {
  border-color: #f0a100;
  box-shadow: 0 0 0 2px rgba(240,161,0,0.32);
  background: #1e2635;
}
.pl-sw img { width: 100%; height: 100%; object-fit: contain; pointer-events: none; }
.pl-sw-none {
  background: repeating-linear-gradient(45deg, #131924 0 5px, transparent 5px 10px);
  color: #616a7a; font: italic 10px/1 'Open Sans', system-ui, sans-serif;
}
/* A piece the illustrator has planned but not drawn. Deliberately unlike a
   swatch you can press: dashed, no hover, no pointer. */
.pl-sw-gap {
  cursor: default;
  border: 1px dashed rgba(139, 149, 165, 0.32);
  background: repeating-linear-gradient(-45deg,
    rgba(139, 149, 165, 0.10) 0 3px,
    rgba(139, 149, 165, 0.02) 3px 6px);
}
.pl-sw-gap:hover { border-color: rgba(139, 149, 165, 0.32); }
.pl-sw-gap-badge {
  padding: 1px 5px; border-radius: 3px;
  background: rgba(139, 149, 165, 0.16);
  border: 1px solid rgba(139, 149, 165, 0.4);
  color: #8b95a5;
  font: 700 8px/1 'Open Sans', system-ui, sans-serif;
  letter-spacing: 0.12em; text-transform: uppercase;
}
.pl-root.light-theme .pl-sw-gap {
  border-color: rgba(54,54,54,0.28);
  background: repeating-linear-gradient(-45deg,
    rgba(54,54,54,0.08) 0 3px, rgba(54,54,54,0.02) 3px 6px);
}
.pl-root.light-theme .pl-sw-gap:hover { border-color: rgba(54,54,54,0.28); }
.pl-root.light-theme .pl-sw-gap-badge { color: #6b6b6b; }
.pl-empty-registry {
  padding: 8px; color: #616a7a;
  font: italic 11px/1.4 'Open Sans', system-ui, sans-serif; text-align: center;
}

/* ---- foot ---- */
.pl-drawer-foot {
  padding: 12px 18px 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
  background: #0e131a;
  display: flex; flex-direction: column; gap: 10px;
  flex-shrink: 0;
}
.pl-attribution {
  display: flex; flex-direction: column; align-items: stretch;
  gap: 7px; margin: 9px 0 3px; font-size: 12px; text-align: center;
}
.pl-credit-preview { background: #0e131a; border-radius: 8px; padding: 10px 22px 14px; max-width: 440px; }
.pl-attribution-empty { color: #616a7a; font-style: italic; font-family: 'Vollkorn', Georgia, serif; }

/* The credit: a small label, then each artist's name on a broken rule with
   their link marks as its end caps, or centred below when they cannot balance
   (see credit-mark-layout). One typeface for the words; no ornament but the
   rule and the marks' own colours.
   No double quotes anywhere in this stylesheet: it is a Clojure string. */
.pl-root { --lk-rule: rgba(240,161,0,0.40); --lk-name: #ebeef4; --lk-dim: #7b8494; }
.pl-root.light-theme { --lk-rule: rgba(51,101,138,0.45); --lk-name: #33465c; --lk-dim: #858c96; }
.lk-row { display: flex; align-items: center; justify-content: center; gap: 10px; }
.lk-rule { flex: 1; height: 1px; min-width: 10px; }
.lk-rule.l { background: linear-gradient(90deg, transparent, var(--lk-rule)); }
.lk-rule.r { background: linear-gradient(90deg, var(--lk-rule), transparent); }
.pl-root {
  --lk-under: rgba(240,161,0,0.45);
  /* pastel spectrum for the dark ground; both ends match so the loop is seamless */
  --lk-prism: linear-gradient(100deg, #ffd3ef 0%, #d4c2ff 17%, #a9e4ff 33%, #b9ffe0 50%,
                               #fff0b3 67%, #ffc9d9 83%, #ffd3ef 100%);
  --lk-halo: drop-shadow(0 0 3px rgba(214,186,255,0.7)) drop-shadow(0 0 9px rgba(140,210,255,0.4));
  --lk-halo-hot: drop-shadow(0 0 4px rgba(255,214,245,0.9)) drop-shadow(0 0 12px rgba(160,220,255,0.6));
  --lk-under-lit: #d8c6ff;
}
.pl-root.light-theme {
  --lk-under: rgba(51,101,138,0.5);
  /* jewel tones for the light ground, where pastels would wash out */
  --lk-prism: linear-gradient(100deg, #b0258a 0%, #6d3fe0 17%, #1f6fd1 33%, #0c9c86 50%,
                               #c77a0a 67%, #c43565 83%, #b0258a 100%);
  --lk-halo: drop-shadow(0 0 3px rgba(150,120,255,0.45)) drop-shadow(0 0 8px rgba(80,170,255,0.3));
  --lk-halo-hot: drop-shadow(0 0 4px rgba(190,110,255,0.6)) drop-shadow(0 0 10px rgba(80,170,255,0.45));
  --lk-under-lit: #6d3fe0;
}
@keyframes lk-prism {
  from { background-position: 0% 50%; }
  to   { background-position: 300% 50%; }
}
.lk-name {
  font: italic 14px/1 'Vollkorn', Georgia, serif; color: var(--lk-name);
  text-decoration: none; white-space: nowrap;
}
/* A name that links reads as one: a thin dotted underline at rest, drawn as
   a text decoration rather than a border so it does not grow the box and
   pull the name off the rule's centre line. */
a.lk-name {
  text-decoration: underline dotted var(--lk-under);
  text-decoration-thickness: 1px; text-underline-offset: 3px;
  transition: filter 240ms ease, text-decoration-color 240ms ease;
}
/* ...and when someone reaches for it -- mouse, keyboard or tap -- the letters
   turn prismatic: a spectrum clipped to the glyphs, drifting slowly, under a
   two-colour halo. Only while it is being reached for; nothing moves at rest.
   The halo is a drop-shadow filter, not a text-shadow, because with the text
   itself transparent a text-shadow would show through the letters. */
a.lk-name:hover, a.lk-name:focus-visible {
  color: transparent;
  background-image: var(--lk-prism); background-size: 300% 100%;
  -webkit-background-clip: text; background-clip: text;
  animation: lk-prism 3.2s linear infinite;
  filter: var(--lk-halo);
  text-decoration-color: var(--lk-under-lit); outline: none;
}
a.lk-name:active { filter: var(--lk-halo-hot); }
/* Anyone who has asked for less motion gets the spectrum standing still. */
@media (prefers-reduced-motion: reduce) {
  a.lk-name { transition: none; }
  a.lk-name:hover, a.lk-name:focus-visible { animation: none; }
}
.lk-cap {
  font: 600 8.5px/1 'Open Sans', system-ui, sans-serif; letter-spacing: 0.16em;
  text-transform: uppercase; color: var(--lk-dim); text-align: center;
}
.lk-marks { display: flex; justify-content: center; gap: 14px; }
.lk-artist-block { display: flex; flex-direction: column; gap: 7px; }
.lk-with {
  font: italic 12px/1.55 'Vollkorn', Georgia, serif; color: var(--lk-dim);
  text-align: center; padding: 0 8px; text-wrap: balance;
}
.lk-with .lk-name { font-size: 13px; }
.lk-mark {
  display: inline-flex; color: var(--lk-dim); text-decoration: none;
  transition: filter 110ms ease, transform 110ms ease;
}
.lk-mark:hover { transform: translateY(-1px); filter: brightness(1.28); }
.pl-root.light-theme .lk-mark:hover { filter: brightness(0.86); }
.lk-ico {
  display: block; width: 12px; height: 12px; background-color: currentColor;
  -webkit-mask-size: contain; mask-size: contain;
  -webkit-mask-repeat: no-repeat; mask-repeat: no-repeat;
  -webkit-mask-position: center; mask-position: center;
}
.lk-mark-text { font: 400 11px/1 'Open Sans', system-ui, sans-serif; }
.pl-drawer-actions {
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
}

/* ---- light theme ----
   Scoped to .pl-root.light-theme because the drawer is a sibling of
   content-page and cannot inherit .app's theme class. Follows the app's own
   light vocabulary rather than inventing one: #363636 text and the #33658A
   the light theme already uses for .form-button, since it recolours .orange
   to near-black and would leave an amber drawer looking pasted on.

   The portrait frame stays dark in BOTH themes on purpose. What it holds is
   character colours -- pale skin, blonde hair -- and a light-on-light frame
   would swallow them. */
.pl-root.light-theme .pl-drawer {
  background: #f4f4f6;
  border-left: 1px solid rgba(0,0,0,0.12);
  color: #363636;
  box-shadow: -30px 0 60px -24px rgba(0,0,0,0.28);
}
.pl-root.light-theme .pl-backdrop { background: rgba(0,0,0,0.32); }
.pl-root.light-theme .pl-drawer-head,
.pl-root.light-theme .pl-drawer-foot { background: #fff; border-color: rgba(0,0,0,0.10); }
.pl-root.light-theme .pl-drawer-title-rune { color: #33658A; }
.pl-root.light-theme .pl-drawer-close {
  border-color: rgba(0,0,0,0.14); color: #5a5a5a;
}
.pl-root.light-theme .pl-drawer-close:hover { color: #33658A; border-color: #33658A; }
.pl-root.light-theme .pl-canvas-side { background: #fff; border-right-color: rgba(0,0,0,0.10); }
.pl-root.light-theme .pl-pickers-side { background: #f4f4f6; }
.pl-root.light-theme .pl-picker,
.pl-root.light-theme .pl-picker-head {
  background: #fff; border-color: rgba(0,0,0,0.10);
}
.pl-root.light-theme .pl-cat,
.pl-root.light-theme .pl-slot-name { color: #363636; }
.pl-root.light-theme .pl-cat-z,
.pl-root.light-theme .pl-sub-shade-val { background: #eceef1; color: #6b6b6b; }
.pl-root.light-theme .pl-picker-sel,
.pl-root.light-theme .pl-empty-hint,
.pl-root.light-theme .pl-empty-registry,
.pl-root.light-theme .pl-attribution-empty,
.pl-root.light-theme .pl-strip-label,
.pl-root.light-theme .pl-panel-heading { color: #6b6b6b; }
.pl-root.light-theme .pl-btn { color: #363636; }
.pl-root.light-theme .pl-btn-primary {
  background: linear-gradient(to bottom, #33658A, #2b5677);
  color: #fff; border-color: #24485f;
  box-shadow: 0 6px 14px -8px rgba(51,101,138,0.5);
}
.pl-root.light-theme .pl-btn-ghost { border-color: rgba(0,0,0,0.16); color: #5a5a5a; }
.pl-root.light-theme .pl-btn-ghost:hover:not(:disabled) { border-color: #33658A; color: #33658A; }
.pl-root.light-theme .pl-seed-row { color: #6b6b6b; }
.pl-root.light-theme .pl-seed-row code {
  color: #2b5677; background: rgba(51,101,138,0.10); border-color: rgba(51,101,138,0.28);
}
.pl-root.light-theme .pl-color-strip,
.pl-root.light-theme .pl-slot-panel { background: #fff; border-color: rgba(0,0,0,0.10); }
.pl-root.light-theme .pl-slot { background: #f4f4f6; border-color: rgba(0,0,0,0.10); }
.pl-root.light-theme .pl-slot.on { border-color: #33658A; box-shadow: 0 0 0 1px rgba(51,101,138,0.28); }
.pl-root.light-theme .pl-slot.has-tweaks .pl-slot-swatch { box-shadow: 0 0 0 2px rgba(51,101,138,0.45); }
.pl-root.light-theme .pl-slot::after { border-color: #8a8a8a; }
.pl-root.light-theme .pl-slot.open::after { border-color: #33658A; }
.pl-root.light-theme .pl-slot-tweaks { background: rgba(51,101,138,0.16); color: #2b5677; }
.pl-root.light-theme .pl-slot-swatch.unset {
  background: repeating-linear-gradient(45deg, #e7e9ec 0 4px, #f7f8fa 4px 8px);
}
.pl-root.light-theme .pl-sub-dot { border-color: #fff; }
.pl-root.light-theme .pl-slot-clear,
.pl-root.light-theme .pl-sub-clear { color: #8a8a8a; }
.pl-root.light-theme .pl-slot-clear:hover:not(:disabled),
.pl-root.light-theme .pl-sub-clear:hover:not(:disabled) {
  color: #33658A; border-color: rgba(51,101,138,0.28);
}
.pl-root.light-theme .pl-sub-name { color: #363636; }
.pl-root.light-theme .pl-sub-row input[type=range],
.pl-root.light-theme .pl-slot-panel input[type=range] { accent-color: #33658A; }
.pl-root.light-theme .pl-sub-chip.shaded,
.pl-root.light-theme .pl-sub-chip.overridden,
.pl-root.light-theme .pl-tint-chip.shaded,
.pl-root.light-theme .pl-tint-chip.overridden { border-color: #33658A; }
.pl-root.light-theme .pl-tint-chip.open { box-shadow: 0 0 0 2px rgba(51,101,138,0.32); }
.pl-root.light-theme .pl-sw {
  background: #f7f8fa; border-color: rgba(0,0,0,0.10);
}
.pl-root.light-theme .pl-sw:hover { border-color: #33658A; }
.pl-root.light-theme .pl-sw.selected {
  border-color: #33658A; background: #e9eef3;
  box-shadow: 0 0 0 2px rgba(51,101,138,0.32);
}
.pl-root.light-theme .pl-sw-none {
  background: repeating-linear-gradient(45deg, #eceef1 0 5px, transparent 5px 10px);
  color: #8a8a8a;
}
/* The launcher lives in the Description tab, not in the drawer, so it hangs
   off .app rather than .pl-root -- scoping it to .pl-root left an amber
   button sitting in an otherwise blue light theme. */
.app.light-theme .pl-launcher {
  background: linear-gradient(to bottom, #33658A, #2b5677);
  color: #fff; border-color: #24485f;
  box-shadow: 0 6px 14px -8px rgba(51,101,138,0.5);
}

/* the launcher button that sits next to the Image URL input in the builder */
.pl-launcher {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 9px 14px; border-radius: 8px;
  background: linear-gradient(to bottom, #f1a20f, #dbab50);
  color: #15202e;
  font: 700 13px/1 'Open Sans', system-ui, sans-serif;
  border: 1px solid #b57500;
  box-shadow: 0 6px 14px -8px rgba(240,161,0,0.32);
  cursor: pointer;
  margin-top: 5px;
}
.pl-launcher:hover { filter: brightness(1.06); }

/* Second way in: a pencil over the summary thumbnail, for people who are
   looking at the portrait rather than at the Description tab's URL field.
   Only rendered in the builder, where there is a drawer to open. */
.pl-thumb-edit {
  position: absolute; right: -7px; bottom: -7px;
  width: 30px; height: 30px; padding: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  background: linear-gradient(to bottom, #f1a20f, #dbab50);
  color: #15202e; font-size: 15px; line-height: 1;
  border: 2px solid #15202e;
  box-shadow: 0 3px 8px -2px rgba(0,0,0,0.5);
  cursor: pointer;
}
.pl-thumb-edit:hover { filter: brightness(1.08); }
.app.light-theme .pl-thumb-edit {
  background: linear-gradient(to bottom, #33658A, #2b5677);
  color: #fff; border-color: #fff;
}
/* Nothing chosen yet: the slot still needs to be visible, or there is no
   thumbnail to hang the pencil on. */
.pl-thumb-empty {
  display: flex; align-items: flex-end; justify-content: center;
  border: 1px dashed rgba(240, 161, 0, 0.3);
  border-radius: 10px;
  /* Opaque, and the same ground the drawer's frame uses: the empty slot
     should read as a portrait-shaped hole, and a translucent fill let the
     page behind it show through. Dark in both themes, like the frame. */
  background: radial-gradient(circle at 50% 35%, #202939, #131924 60%, #0f141c);
}
.pl-thumb-credit {
  width: 100px;
  margin-top: 5px;
  font: 500 9px/1.25 'Open Sans', system-ui, sans-serif;
  color: #8b95a5;
  letter-spacing: 0.02em;
  position: relative;
}
.pl-thumb-more {
  font: 700 9px/1 'Open Sans', system-ui, sans-serif; color: #f0a100;
  background: rgba(240,161,0,0.12); border: 1px solid rgba(240,161,0,0.45);
  border-radius: 8px; padding: 1px 5px; cursor: pointer; vertical-align: 1px;
}
.pl-thumb-more:hover, .pl-thumb-more[aria-expanded=true] { background: rgba(240,161,0,0.24); }
.pl-thumb-more:focus-visible { outline: 2px solid #f0a100; outline-offset: 1px; }
.pl-thumb-more-list {
  position: absolute; left: 0; top: calc(100% + 4px); z-index: 20;
  min-width: 150px; max-width: 220px; padding: 8px 10px;
  background: #131924; border: 1px solid rgba(240,161,0,0.35); border-radius: 6px;
  box-shadow: 0 6px 18px rgba(0,0,0,0.45);
  font: 500 12px/1.6 'Open Sans', system-ui, sans-serif; color: #c9d0da;
}
.pl-thumb-more-lead { font-weight: 700; color: #ebeef4; }
.pl-thumb-more-cap { font-size: 10px; text-transform: uppercase; letter-spacing: 0.12em; color: #7b8494; margin-top: 2px; }
.app.light-theme .pl-thumb-more-list { background: #fff; color: #363636; border-color: rgba(51,101,138,0.35); }
.app.light-theme .pl-thumb-more-lead { color: #1f2a37; }
.app.light-theme .pl-thumb-credit { color: #6a6a6a; }
.pl-thumb-credit-link { color: inherit; text-decoration: underline; text-underline-offset: 2px; }
.pl-thumb-credit-link:hover { color: #f0a640; }
.pl-thumb-empty-label {
  font: 600 10px/1 'Open Sans', system-ui, sans-serif;
  color: #8b95a5; letter-spacing: 0.08em;
  /* clear of the pencil in the bottom-right corner */
  padding: 0 22px 11px 0;
}

/* ---- inline: the compositor as a builder tab ---- */
/* Everything that makes the drawer a drawer hangs off .pl-drawer -- fixed
   position, backdrop, slide-in -- so the shared body needs only a frame of
   its own and a scroll bound to sit in the page flow instead of over it. */
.pl-inline {
  display: flex; flex-direction: column;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  background: #131924;
  overflow: hidden;
  font-family: 'Open Sans', system-ui, sans-serif;
  color: #ebeef4;
  margin-bottom: 20px;
}
.pl-inline .pl-drawer-body { max-height: 70vh; }
.pl-inline .pl-btn-primary:disabled { opacity: 0.45; cursor: default; filter: none; }
.pl-root.light-theme.pl-inline {
  background: #f7f7f5; color: #363636; border-color: rgba(0,0,0,0.1);
}
/* on a phone the two columns already stack; let the page scroll, not the panel */
@media (max-width: 700px) {
  .pl-inline .pl-drawer-body { max-height: none; }
}
")

;; ---------------- color controls ----------------

(defn- sub-row
  "One piece's color controls: effective-color chip (native override
   picker inside), shade slider, readout, clear. Used both inside the slot
   panel (all pieces of a slot) and inline under a picker card."
  [portrait layer-key]
  (let [{:keys [override shade depth]} (get-in portrait [:tweaks layer-key])
        shade (or shade 0)
        depth (or depth 0)
        asset (pa/selected-asset portrait layer-key)
        colorized? (and asset (colorizes? layer-key asset))
        eff   (pa/tint-for portrait layer-key)
        any?  (boolean (or override (not (zero? shade)) (not (zero? depth))))
        label (pa/layer-labels layer-key)]
    [:div.pl-sub-row
     [:span.pl-sub-name label]
     [:span.pl-sub-chip
      {:class (cond override "overridden" (not (zero? shade)) "shaded")
       :style {:background eff}
       :title (str "Override — currently " (s/upper-case eff))}
      [:input {:type "color"
               :value (or override eff)
               :aria-label (str "Override " label " color")
               :on-change #(dispatch [:portrait/set-layer-tweak layer-key
                                      {:override (target-value %)}])}]]
     [:input {:type "range" :min -30 :max 30 :step 5
              :value shade
              :disabled (some? override)
              :aria-label (str "Shade " label)
              :on-change #(dispatch [:portrait/set-layer-tweak layer-key
                                     {:shade (js/parseInt (target-value %) 10)}])}]
     [:span.pl-sub-shade-val (str (when (pos? shade) "+") shade "%")]
     [:button.pl-sub-clear
      {:type "button" :disabled (not any?)
       :title (str "Use base color for " label)
       :on-click #(dispatch [:portrait/clear-layer-tweak layer-key])}
      "×"]
     ;; Eyes and lips are coloured through their own shading, so they get a
     ;; second knob on a line of its own: how far into the colour the
     ;; shading reaches. 0 is the settled look.
     (when colorized?
       [:<>
        [:span.pl-sub-depth-icon {:aria-hidden true :title "Brightness"} "\u2600"]
        [:input.pl-sub-depth
         {:type "range" :min (- pa/depth-steps) :max pa/depth-steps :step 1
          :value depth
          :aria-label (str "Brightness of " label)
          :title "Brightness: lift or deepen the colour, keeping the shading"
          :on-change #(dispatch [:portrait/set-layer-tweak layer-key
                                 {:depth (js/parseInt (target-value %) 10)}])}]
        [:span.pl-sub-shade-val (str (when (pos? depth) "+") depth)]])]))

(defn- slot-chip [portrait slot open-slot]
  (let [cur     (get-in portrait [:colors slot])
        tweaked (pa/tweaked-layers-in-slot portrait slot)
        open?   (= open-slot slot)
        label   (pa/color-slot-labels slot)]
    [:div.pl-slot
     {:class (classes (when cur "on") (when (seq tweaked) "has-tweaks") (when open? "open"))}
     [:span.pl-slot-name label]
     (when (seq tweaked)
       [:span.pl-slot-tweaks
        {:title (str (count tweaked) " piece" (when (> (count tweaked) 1) "s") " tweaked")}
        (count tweaked)])
     [:button.pl-slot-swatch
      {:type "button"
       :class (when-not cur "unset")
       :style (when cur {:background cur})
       :aria-expanded open?
       :title (if cur (str "Base color " (s/upper-case cur)) "Not set — tap to pick")
       :on-click #(dispatch [:portrait/toggle-slot-panel slot])}
      [:span.pl-sub-dots
       (map-indexed
         (fn [i k]
           ^{:key k}
           [:span.pl-sub-dot {:class (str "pl-sub-dot-" i)
                              :style {:background (pa/tint-for portrait k)}
                              :title (str (pa/layer-labels k) ": " (s/upper-case (pa/tint-for portrait k)))}])
         (take 4 tweaked))]]
     [:button.pl-slot-clear
      {:type "button" :disabled (nil? cur)
       :title (str "Clear " label)
       :on-click #(dispatch [:portrait/clear-slot-color slot])}
      "×"]]))

(defn- ombre-slider
  "One ombre setting, 0..100 on screen, 0..1 stored."
  [label k value]
  [:label.pl-ombre-row
   [:span.pl-ombre-label label]
   [:input {:type "range" :min 0 :max 100 :step 5
            :value (js/Math.round (* 100 value))
            :aria-label label
            :on-change #(dispatch [:portrait/set-ombre k (/ (js/parseInt (target-value %) 10) 100)])}]
   [:span.pl-sub-shade-val (str (js/Math.round (* 100 value)))]])

(defn- ombre-controls
  "The hair's second colour and how it runs. With no tips colour the hair is
   one colour with a little depth, which is where everyone starts."
  [portrait]
  (let [{:keys [tip start falloff depth angle]} (fx/ombre-settings portrait)]
    [:div.pl-ombre
     [:span.pl-panel-heading "Tips"]
     [:div.pl-presets
      [:button.pl-preset.pl-preset-none
       {:type "button" :title "No second colour"
        :class (when-not tip "on")
        :on-click #(dispatch [:portrait/set-ombre :tip nil])} "\u2205"]
      (for [c (pa/color-presets :hair)]
        ^{:key c}
        [:button.pl-preset
         {:type "button" :style {:background c} :title c
          :class (when (= c tip) "on")
          :on-click #(dispatch [:portrait/set-ombre :tip c])}])
      [:span.pl-preset.custom {:title "Custom tips colour"}
       [:input {:type "color" :value (or tip "#c0a080")
                :aria-label "Custom tips colour"
                :on-change #(dispatch [:portrait/set-ombre :tip (target-value %)])}]]]
     (when tip
       [:<>
        [ombre-slider "Starts" :start start]
        [ombre-slider "Blend" :falloff falloff]])
     [ombre-slider "Depth" :depth depth]
     [:label.pl-ombre-row
      [:span.pl-ombre-label "Runs"]
      [:select {:value (if angle "angle" "roots")
                :aria-label "Which way the tips run"
                :on-change #(dispatch [:portrait/set-ombre :angle (when (= "angle" (target-value %)) 0)])}
       [:option {:value "roots"} "From the roots"]
       [:option {:value "angle"} "At an angle"]]]
     (when angle
       [:label.pl-ombre-row
        [:span.pl-ombre-label "Angle"]
        [:input {:type "range" :min 0 :max 345 :step 15 :value angle
                 :aria-label "Tips angle"
                 :on-change #(dispatch [:portrait/set-ombre :angle (js/parseInt (target-value %) 10)])}]
        [:span.pl-sub-shade-val (str angle "\u00b0")]])]))

(defn- slot-panel [portrait slot]
  (let [pieces (pa/layers-in-slot portrait slot)]
    [:div.pl-slot-panel
     [:div
      [:span.pl-panel-heading "Presets"]
      [:div.pl-presets
       (for [c (pa/color-presets slot)]
         ^{:key c}
         [:button.pl-preset
          {:type "button" :style {:background c} :title c
           :on-click #(dispatch [:portrait/set-slot-color slot c])}])
       [:span.pl-preset.custom {:title "Custom color"}
        [:input {:type "color"
                 :value (or (get-in portrait [:colors slot]) "#c0a080")
                 :aria-label (str "Custom " (pa/color-slot-labels slot) " color")
                 :on-change #(dispatch [:portrait/set-slot-color slot (target-value %)])}]]]]
     (when (= slot :hair) [ombre-controls portrait])
     (when (seq pieces)
       [:div.pl-sublayers
        [:span.pl-panel-heading (str (pa/color-slot-labels slot) " pieces")]
        (for [k pieces]
          ^{:key k} [sub-row portrait k])])]))

(defn- color-strip [portrait open-slot]
  [:div.pl-color-strip
   [:span.pl-strip-label "colors"]
   ;; not color-slot-order: Lips only applies while a mouth asset that HAS
   ;; lips is selected, and a swatch that tints nothing is worse than no swatch
   (for [slot (pa/active-color-slots portrait)]
     ^{:key slot} [slot-chip portrait slot open-slot])
   (when open-slot
     [slot-panel portrait open-slot])])

(defn- layer-tint-chip [portrait layer-key open-layer]
  (let [{:keys [override shade]} (get-in portrait [:tweaks layer-key])
        open? (= open-layer layer-key)]
    [:button.pl-tint-chip
     {:type "button"
      :class (classes (when override "overridden")
                      (when (and (not override) shade (not (zero? shade))) "shaded")
                      (when open? "open"))
      :style {:background (pa/tint-for portrait layer-key)}
      :aria-expanded open?
      :title "Adjust this piece's color"
      :on-click #(dispatch [:portrait/toggle-layer-panel layer-key])}]))

;; ---------------- pickers ----------------

(defn- category-picker [portrait layer-key open-layer]
  (let [selected       (get-in portrait [:layers layer-key])
        selected-asset (when selected (pa/asset-by-id layer-key (:asset/id selected)))
        assets         (pa/assets-for-layer layer-key)
        gaps           (pa/gaps-for-layer layer-key)]
    [:div.pl-picker
     [:div.pl-picker-head
      [:span.pl-cat
       [:span.pl-cat-chip {:style {:background (pa/layer-colors layer-key)}}]
       (pa/layer-labels layer-key)
       [:span.pl-cat-z (z-label layer-key)]
       [layer-tint-chip portrait layer-key open-layer]]
      (when selected
        [:span.pl-picker-sel (or (:asset/label selected-asset) "picked")])]
     (if (and (empty? assets) (empty? gaps))
       [:div.pl-empty-registry "no art here yet"]
       [:div.pl-swatches
        [:button.pl-sw.pl-sw-none
         {:type "button"
          :class (when-not selected "selected")
          :on-click #(dispatch [:portrait/pick-layer layer-key nil])
          :title "None"}
         "none"]
        (for [asset assets
              :let [asset-id (:asset/id asset)]]
          ^{:key asset-id}
          [:button.pl-sw
           {:type "button"
            :class (when (= asset-id (:asset/id selected)) "selected")
            :on-click #(dispatch [:portrait/pick-layer layer-key asset-id])
            :title (or (:asset/label asset) (name asset-id))}
           [:img {:src (:asset/url asset)
                  :alt (or (:asset/label asset) "")}]])
        ;; Pieces the illustrator has marked as not drawn yet. Inert on
        ;; purpose -- the point is to say the set is still growing, so an
        ;; empty-looking category is not mistaken for a finished one.
        (for [{:keys [:gap/id :gap/label]} gaps]
          ^{:key id}
          [:div.pl-sw.pl-sw-gap
           {:title (str (or label (name id)) " \u2014 not drawn yet")
            :aria-label (str (or label (name id)) ", not drawn yet")}
           [:span.pl-sw-gap-badge "soon"]])])
     (when (= open-layer layer-key)
       [:div.pl-layer-panel [sub-row portrait layer-key]])]))

(def ^:private thumb-credit-chars
  "About what the 100px strip holds in two lines of its 9px type."
  34)

(defn- artist-ref
  "An artist's name, as a link where they have one."
  [{:keys [:artist/name :artist/link]}]
  (if link
    [:a.pl-thumb-credit-link
     {:href link :target "_blank" :rel "noopener"
      :on-click #(.stopPropagation %)}
     name]
    [:span name]))

(defn- linked-credit
  "The credit under the character page's thumbnail, each name a link where
   the artist has one.

   The strip is 100px, so it can't hold many names. When the whole line fits
   it's shown whole; when it doesn't, the artist who drew most of the picture
   is named and everyone else collapses into a '+N' that opens the full list,
   every name still a link. Before, the strip clipped after two lines and the
   smallest contributors just vanished.

   This is the one surface downstream of the builder that can send someone to
   the artist -- a PNG, a PDF and a social unfurl can only carry the name as
   text -- so nobody's link is allowed to drop off it."
  [portrait]
  (r/with-let [open? (r/atom false)]
    (let [[lead & more :as named] (filter :artist/name (pa/artists-for-layers (:layers portrait)))
          full (pa/format-credit (map :artist/name named))]
      (cond
        (nil? lead) nil

        (<= (count full) thumb-credit-chars)
        [:<> "Art: " [artist-ref lead]
         (when (seq more)
           [:<> " with "
            (for [[i a] (map-indexed vector more)]
              ^{:key (:artist/id a)}
              [:<> (cond (zero? i) nil (= i (dec (count more))) " and " :else ", ")
               [artist-ref a]])])]

        :else
        [:<> "Art: " [artist-ref lead] " "
         [:button.pl-thumb-more
          {:type "button"
           :aria-expanded (str @open?)
           :aria-label (str "Show all " (count named) " artists")
           :title (str "Show all " (count named) " artists")
           :on-click #(do (.stopPropagation %) (swap! open? not))
           :on-key-down #(when (= "Escape" (.-key %)) (reset! open? false))}
          (str "+" (count more))]
         (when @open?
           [:div.pl-thumb-more-list {:role "dialog" :aria-label "Everyone who drew this portrait"
                                     :on-click #(.stopPropagation %)}
            [:div.pl-thumb-more-lead [artist-ref lead]]
            [:div.pl-thumb-more-cap "with"]
            (for [a more]
              ^{:key (:artist/id a)} [:div [artist-ref a]])])]))))

;; ---------------- the artist credit ----------------
;;
;; Settled after an exploration whose screenshots are stashed at
;; docs/design/portrait-credit on agents/develop: a small label, then each
;; artist's name on a broken rule, the words in one typeface and nothing else
;; decorated but the rule and the link marks' own colours.

(defn- credit-mark
  "One of an artist's links, as its service's mark in the link's own colour.
   The label is the accessible name: an icon alone is unreadable to a screen
   reader, and a link with no icon falls back to the label as text."
  [artist-name {:link/keys [label url icon color]}]
  [:a.lk-mark {:href url :target "_blank" :rel "noopener"
               :title (str artist-name " on " label)
               :aria-label (str artist-name " on " label)
               :style (when color {:color color})}
   (if icon
     [:span.lk-ico {:style {:WebkitMaskImage (str "url(/image/social/" icon ".svg)")
                            :maskImage (str "url(/image/social/" icon ".svg)")}}]
     [:span.lk-mark-text (s/lower-case label)])])

(defn- credit-name [{:keys [:artist/name :artist/link]}]
  (if link
    [:a.lk-name {:href link :target "_blank" :rel "noopener"} name]
    [:span.lk-name name]))

(defn- rule [side] [:span.lk-rule {:class side :aria-hidden true}])

(defn- name-list
  "Names as prose: A / A and B / A, B and C. Each is its artist's link."
  [artists]
  (let [n (count artists)]
    (for [[i a] (map-indexed vector artists)]
      ^{:key (:artist/id a)}
      [:<>
       (cond (zero? i) nil
             (= i (dec n)) " and "
             :else ", ")
       [credit-name a]])))

(defn- credit-lockup
  "Small label, then the artists in credit order (pa/credit-order): whoever
   drew most of the picture on the rule, with their link marks as its end caps
   when those balance and centred below when not (pa/credit-mark-layout); then
   everyone else on one line -- 'with A, B and C' -- each name its artist's
   link. Height stays at three or four lines however many artists there are,
   and the lead keeps the marks because the lead is who most people looking
   at the picture want to find."
  [named]
  (let [[{:keys [:artist/name :artist/links] :as lead} & others] named
        {:keys [left right below]} (pa/credit-mark-layout links)]
    [:<>
     [:div.lk-cap "Art by"]
     [:div.lk-artist-block
      [:div.lk-row
       [rule "l"]
       (for [l left] ^{:key (:link/url l)} [credit-mark name l])
       [credit-name lead]
       (for [l right] ^{:key (:link/url l)} [credit-mark name l])
       [rule "r"]]
      (when (seq below)
        [:div.lk-marks
         (for [l below] ^{:key (:link/url l)} [credit-mark name l])])]
     (when (seq others)
       [:div.lk-with "with " (name-list others)])]))

(defn credit-preview
  "One artist's credit exactly as the builder draws it, for the account page:
   an artist editing their credit should see the thing itself, not a
   description of it."
  [info]
  [:div.pl-root.pl-credit-preview
   [:style drawer-styles]
   [:div.pl-attribution [credit-lockup [info]]]])

(defn- attribution
  "Who drew what is on canvas.

   An artist with no :artist/name is on canvas but has not said how they want
   to be credited. Showing the label with an empty slot after it reads like a
   rendering fault, and inventing something to put there is worse, so the
   whole strip says so plainly instead."
  [layers]
  (let [infos (pa/artists-for-layers layers)
        named (filter :artist/name infos)]
    [:div.pl-attribution
     (cond
       (seq named)
       [credit-lockup named]

       (seq infos)
       [:span.pl-attribution-empty "artist credit pending"]

       :else
       [:span.pl-attribution-empty "no layers selected yet"])]))

;; ---------------- drawer ----------------

(defn- compositor-body
  "Canvas on one side, layer pickers on the other. Shared verbatim by the
   drawer and the inline Portrait tab so the two cannot drift apart."
  [portrait seed open-slot open-layer]
  (let [any? (boolean (seq (:layers portrait)))]
    [:div.pl-drawer-body
     [:div.pl-canvas-side
      [:div.pl-portrait-frame
       (if any?
         [composite portrait]
         [:div.pl-empty-hint
          "Pick a layer below, or hit " [:em "Randomize"] "."])]
      [iris-alternative-switch portrait]
      ;; Directly under the art it describes. It used to sit in the footer
      ;; beside Save Portrait, which put a credit in a row of actions and made
      ;; the drawer disagree with the character summary, where it has always
      ;; sat under the thumbnail.
      [attribution (:layers portrait)]
      [:div.pl-toolbar
       [:button.pl-btn.pl-btn-primary
        {:type "button" :on-click #(dispatch [:portrait/randomize])}
        "Randomize"]
       [:button.pl-btn.pl-btn-ghost
        {:type "button" :on-click #(dispatch [:portrait/reset]) :disabled (not any?)}
        "Reset"]
       (when seed
         [:div.pl-seed-row [:span "seed"] [:code seed]])]
      [color-strip portrait open-slot]]
     [:div.pl-pickers-side
      (for [layer-key pa/pickable-layers]
        ^{:key layer-key}
        [category-picker portrait layer-key open-layer])]]))

(defn drawer
  "Renders the compositor drawer when :portrait/drawer-open? is truthy.
   Mount once at the character-builder root; it overlays.

   The stylesheet is mounted unconditionally, not inside the open? branch --
   it also styles the launcher button and the inline Portrait tab, both of
   which are on screen precisely when the drawer is not.

   The theme class goes on this component's own root rather than being
   inherited: the drawer is mounted as a SIBLING of content-page, and .app --
   which carries the theme -- is inside content-page, so .app.light-theme
   cannot reach it."
  []
  (let [open? @(subscribe [:portrait/drawer-open?])
        theme @(subscribe [:theme])]
    [:div.pl-root {:class theme}
     [:style (str drawer-styles empty-slot-styles)]
     (when open?
       (let [portrait   @(subscribe [:portrait/draft])
             seed       @(subscribe [:portrait/draft-seed])
             open-slot  @(subscribe [:portrait/open-slot])
             open-layer @(subscribe [:portrait/open-layer])]
         [:div
          [:div.pl-backdrop {:on-click #(dispatch [:portrait/close])}]
          [:div.pl-drawer
           [:div.pl-drawer-head
            [:div.pl-drawer-title
             [:span.pl-drawer-title-rune "\u00a7"] "Compose portrait"]
            [:button.pl-drawer-close
             {:type "button"
              :on-click #(dispatch [:portrait/close])
              :aria-label "Close portrait compositor"}
             "\u2715"]]
           [compositor-body portrait seed open-slot open-layer]
           [:div.pl-drawer-foot
            [:div.pl-drawer-actions
             [:button.pl-btn.pl-btn-ghost
              {:type "button" :on-click #(dispatch [:portrait/close])}
              "Cancel"]
             [:button.pl-btn.pl-btn-primary
              {:type "button" :on-click #(dispatch [:portrait/save])}
              "Save portrait"]]]]]))]))

(defn tab-panel
  "The compositor rendered in place, as a builder tab.

   Same body as the drawer, different chrome: there is nothing to cancel back
   to, so instead of Cancel/Save it carries Save with a dirty marker and a way
   to pop the focused overlay -- which is the nicer place to work on a phone,
   where the two columns stack into a long scroll.

   Needs its own .pl-root because the drawer stylesheet scopes the light theme
   to that class; here it also happens to sit inside .app, but relying on that
   would mean two different theme hooks for one stylesheet."
  []
  (r/create-class
   {:display-name "portrait-tab-panel"
    :component-did-mount #(dispatch [:portrait/ensure-draft])
    :reagent-render
    (fn []
      (let [portrait   @(subscribe [:portrait/draft])
            seed       @(subscribe [:portrait/draft-seed])
            open-slot  @(subscribe [:portrait/open-slot])
            open-layer @(subscribe [:portrait/open-layer])
            dirty?     @(subscribe [:portrait/dirty?])
            theme      @(subscribe [:theme])]
        [:div.pl-root.pl-inline {:class theme}
         [compositor-body portrait seed open-slot open-layer]
         [:div.pl-drawer-foot
          [:div.pl-drawer-actions
           [:button.pl-btn.pl-btn-ghost
            {:type "button" :on-click #(dispatch [:portrait/open])}
            "Full screen"]
           [:button.pl-btn.pl-btn-primary
            {:type "button"
             :disabled (not dirty?)
             :on-click #(dispatch [:portrait/save true])}
            (if dirty? "Save portrait" "Saved")]]]]))}))

(defn launcher-button
  "The 'Compose portrait' button that slots next to the Image URL input in
   the character-builder Description tab."
  []
  [:button.pl-launcher
   {:type "button"
    :on-click #(dispatch [:portrait/open])
    :title "Compose portrait from layered art"}
   "Compose portrait"])

(defn edit-overlay
  "Pencil layered onto the character-summary thumbnail. stopPropagation
   because summaries are sometimes wrapped in a clickable row."
  []
  [:button.pl-thumb-edit
   {:type "button"
    :title "Edit portrait"
    :on-click (fn [e]
                (.stopPropagation e)
                (dispatch [:portrait/open]))}
   "\u270e"])

(defn thumbnail
  "The summary thumbnail. A composed portrait wins over a pasted image-url;
   with neither, an empty frame stands in so the pencil has somewhere to sit.
   `editable?` is builder-only -- elsewhere there is no drawer to open.

   A composed portrait carries its artists' names under it. This is the one
   surface a reader lands on, so it is where the credit does the most good."
  [portrait-data image-url editable?]
  (let [box {:position "relative" :width "100px" :height "125px" :flex-shrink 0}]
    (cond
      (seq (:layers portrait-data))
      [:div.m-r-20.m-t-10.m-b-10
       [:div.image-character-thumbnail {:style box}
        [composite portrait-data
         {:style {:position "absolute" :inset 0 :width "100%" :height "100%"}}]
        (when editable? [edit-overlay])]
       (when-let [credit (pa/credit-line portrait-data)]
         [:div.pl-thumb-credit {:title credit} [linked-credit portrait-data]])]

      image-url
      (if editable?
        [:div.m-r-20.m-t-10.m-b-10 {:style box}
         [:img.image-character-thumbnail
          {:src image-url :style {:width "100%" :height "100%"}}]
         [edit-overlay]]
        [:img.m-r-20.m-t-10.m-b-10.image-character-thumbnail {:src image-url}])

      editable?
      [:div.m-r-20.m-t-10.m-b-10.pl-thumb-empty {:style box}
       [:span.pl-thumb-empty-label "PORTRAIT"]
       [edit-overlay]])))
