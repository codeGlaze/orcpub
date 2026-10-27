(ns orcpub.dnd.e5.portrait
  "The portrait compositor UI: `drawer`, the builder's `tab-panel`, `composite` (also used
   by the character summary) and `rasterize` (the PNG for PDF export). Edits a draft at
   app-db :portrait/draft, in portrait-assets' shape; :portrait/save writes it back.
   Each layer is a div tinted by background-blend multiply, clipped to the asset by a mask.
   See PORTRAIT-COMPOSITOR.md and PORTRAIT-TINTING.md."
  (:require [re-frame.core :refer [dispatch subscribe]]
            [orcpub.art-mark :as art-mark]
            [re-frame.db]
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.artist-profile :as artist-profile]
            [orcpub.dnd.e5.portrait-colorize :as colorize]
            [orcpub.dnd.e5.portrait-effects :as fx]
            [orcpub.dnd.e5.portrait-face :as face]
            [orcpub.dnd.e5.portrait-light :as light]
            [orcpub.dnd.e5.portrait-layout :as layout]
            [orcpub.fork.branding :as branding]))

(defn- classes [& cs] (s/join " " (remove nil? cs)))

(defn- target-value [e] (.. e -target -value))

(defn- z-label [layer-key]
  (let [z (.indexOf pa/layer-order layer-key)]
    (str "z" (when (< z 10) "0") z)))

;; ---------------- composite (used by drawer AND summary) ----------------

(defn- as-drawn-layer
  "An asset shown exactly as the illustrator made it: a plain background image,
   no mask and no tint. A mask would throw the drawing away and keep only its
   alpha, which is how the teeth came out the colour of the mouth."
  [url z]
  {:class "portrait-layer-art" :style {"--pl-art" (str "url(" url ")") "--pl-z" z}})

(defn- tinted-layer
  "A tinted piece (.portrait-layer-tinted): the art as a background image multiplied over the
   tint, so its line work survives, then masked to its own shape so the tint stays inside it.
   A mask alone would keep only the alpha and flatten the drawing into a silhouette."
  [url tint z]
  {:class "portrait-layer-tinted"
   :style {"--pl-art" (str "url(" url ")") "--pl-tint" tint "--pl-z" z}})

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
  "A canvas the size of `img` holding it coloured through `hex`. An eye piece
   also takes the second eye colour, light from below and the pupil in `eye`
   ({:second-eye :pupil}, portrait-face)."
  [img asset hex gamma eye]
  (let [w (.-naturalWidth img) h (.-naturalHeight img)
        c (new-canvas w h)
        ctx (.getContext c "2d")
        _ (.drawImage ctx img 0 0)
        image-data (.getImageData ctx 0 0 w h)
        px (.-data image-data)
        cov (when (seq (:asset/iris asset)) (iris-coverage asset w h))
        [er eg eb :as colour] (colorize/hex->rgb hex)
        eye-fn (when cov
                 (face/eye-pixel-fn (colorize/iris-shapes asset [0 0 w h]) colour
                                    (some-> (:second-eye eye) colorize/hex->rgb)
                                    gamma (or (:pupil eye) :round)))
        all? (and eye-fn (not= :round (:pupil eye)))]
    (dotimes [p (* w h)]
      (let [i (* 4 p)
            ;; irises by their placed region; lips by how solid the stroke is
            k (if cov (/ (aget cov p) 255) (colorize/lip-weight (aget px (+ i 3))))]
        ;; a new pupil paints where the drawn one is, outside the iris region
        (when (and (pos? (aget px (+ i 3))) (or (pos? k) all?))
          (let [[r g b] (if eye-fn
                          (eye-fn (mod p w) (quot p w) [(aget px i) (aget px (+ i 1)) (aget px (+ i 2))] k)
                          (colorize/colorize-rgb (aget px i) (aget px (+ i 1)) (aget px (+ i 2))
                                                 er eg eb gamma k))]
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
  [img asset hex gamma eye]
  (let [k [(:asset/url asset) hex gamma (:asset/iris asset) eye]]
    (or (get @colorized-cache k)
        (let [c (colorize-image img asset hex gamma eye)]
          (swap! colorized-cache #(assoc (if (> (count %) 48) {} %) k c))
          c))))

;; ---------------- solid insides (developer tool) ----------------
;;
;; Off unless Developer mode is on: production draws the art as delivered.
;; The fix belongs in the art -- the Loom flags see-through insides and can
;; fill them when it exports a pack.
;;
;; Every piece is drawn from a copy with its inside made fully opaque
;; (portrait-colorize/solid-mask): a few thousand nearly-opaque pixels inside
;; the head art let the background show through as pale specks on dark skin.
;; The copy is made once per piece and kept as a data URL, so the CSS layers
;; can use it too. Strand fields are data, not art, and are left alone.

(defonce ^:private solid-urls (r/atom {}))
(defonce ^:private solid-pending (atom {}))

(defn- solid-promise
  "Resolves to the solid copy's URL for `url` (or `url` itself if it cannot
   be made, e.g. the image will not decode)."
  [url]
  (if-not (:dev-mode? @re-frame.db/app-db)
    (js/Promise.resolve url)
  (or (get @solid-pending url)
      (let [p (js/Promise.
               (fn [resolve _]
                 (if (or (nil? url) (re-find #"\.strands\.png$" url))
                   (resolve url)
                   (let [img (js/Image.)]
                     (set! (.-onerror img) #(resolve url))
                     (set! (.-onload img)
                           (fn []
                             (try
                               (let [w (.-naturalWidth img) h (.-naturalHeight img)
                                     c (new-canvas w h) g (.getContext c "2d")
                                     _ (.drawImage g img 0 0)
                                     data (.getImageData g 0 0 w h) px (.-data data)
                                     m (colorize/solid-mask #(aget px (+ 3 (* 4 %))) w h)]
                                 (dotimes [i (* w h)] (when (pos? (aget m i)) (aset px (+ 3 (* 4 i)) 255)))
                                 (.putImageData g data 0 0)
                                 ;; a data: URL, which the site's CSP allows for images
                                 ;; (blob: it does not)
                                 (let [u (.toDataURL c "image/png")]
                                   (swap! solid-urls assoc url u)
                                   (resolve u)))
                               (catch :default _ (resolve url)))))
                     (set! (.-src img) url)))))]
        (swap! solid-pending assoc url p)
        p))))

(defn- solid-src
  "The solid copy's URL for a CSS layer, or the original until it is ready
   (deref'd, so the layer redraws when it is)."
  [url]
  (if-not @(subscribe [:orcpub.dnd.e5/dev-mode?])
    url
    (or (get @solid-urls url) (do (solid-promise url) url))))

(defonce ^:private loaded-images (atom {}))

(defn- with-image
  "Call `f` with the loaded (solid) image for `url`, now if it is already
   loaded, or with nil if it will not load (remembered, so it is asked for
   once)."
  [url f]
  (if (contains? @loaded-images url)
    (f (get @loaded-images url))
    (.then (solid-promise url)
           (fn [src]
             (let [img (js/Image.)]
               (set! (.-onload img) #(do (swap! loaded-images assoc url img) (f img)))
               (set! (.-onerror img) #(do (swap! loaded-images assoc url nil) (f nil)))
               (set! (.-src img) src))))))

(defn- colorized-layer
  "A layer the drawer draws on a canvas rather than in CSS, sized by
   object-fit: contain so it sits exactly where the CSS layers do."
  [asset tint gamma eye z]
  (let [node (atom nil)
        paint! (fn [asset tint gamma eye]
                 (with-image (:asset/url asset)
                   (fn [img]
                     (when-let [^js el (and img @node)]
                       (let [src (colorized img asset tint gamma eye)]
                         (set! (.-width el) (.-width src))
                         (set! (.-height el) (.-height src))
                         (.drawImage (.getContext el "2d") src 0 0))))))]
    (r/create-class
     {:component-did-mount (fn [_] (paint! asset tint gamma eye))
      :component-did-update (fn [this _]
                              (let [[_ asset tint gamma eye] (r/argv this)]
                                (paint! asset tint gamma eye)))
      :reagent-render
      (fn [_ _ _ _ z]
        [:canvas.portrait-layer.portrait-layer-colorized
         {:ref #(reset! node %)
          :style {"--pl-z" z}}])})))

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
          :style {"--pl-z" z}}])})))

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

(defonce ^:private strand-cache (atom {}))

(defn- strand-field-of
  "The piece's strand field (portrait-effects/strand-field), in the art's own
   pixels: read from the .strands.png beside the art when there is one
   (`strands`, an image or nil), otherwise worked out from the art once."
  [img px w h strands layer-key]
  (let [k (.-src img)]
    (or (get @strand-cache k)
        (let [f (if strands
                  (let [gw (.-naturalWidth strands) gh (.-naturalHeight strands)
                        sp (pixels-of strands gw gh)]
                    (fx/bytes->field (fn [g] (aget sp (* 4 g))) gw gh w h))
                  (fx/strand-field (fn [p] (let [i (* 4 p)]
                                             (bit-or (bit-shift-left (aget px i) 16)
                                                     (bit-shift-left (aget px (+ i 1)) 8)
                                                     (aget px (+ i 2)))))
                                   (alpha-fn px) w h (fx/piece-seed layer-key)))]
          (swap! strand-cache #(assoc (if (> (count %) 48) {} %) k f))
          f))))

(defn- ombre-image
  "A canvas the size of `img` holding the hair piece coloured root to tip."
  [img head strands layer-key root-hex settings]
  (let [w (.-naturalWidth img) h (.-naturalHeight img)
        c (new-canvas w h) ctx (.getContext c "2d")
        _ (.drawImage ctx img 0 0)
        image-data (.getImageData ctx 0 0 w h)
        px (.-data image-data)
        root0 (colorize/hex->rgb root-hex)
        [root tip] (fx/layer-colours layer-key root0 (or (some-> (:tip settings) colorize/hex->rgb) root0) settings)
        crown (when head (crown-of head w h))
        shine? (boolean (and (pos? (:shine settings)) (fx/shine-layers layer-key)))
        ;; streaks only show where there is a second colour to streak; the
        ;; shine breaks along the strands too
        field (when (or shine? (and (:tip settings) (pos? (:clumps settings)) (not (:split settings))))
                (strand-field-of img px w h strands layer-key))
        pix (fx/pixel-fn root tip settings
                         (fx/gradient-frame (alpha-fn px) w h crown (fx/run-mode settings) shine?)
                         layer-key field)]
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
  [img head strands layer-key root-hex settings]
  (let [k [(.-src img) (some-> head .-src) layer-key root-hex settings]]
    (or (get @ombre-cache k)
        (let [c (ombre-image img head strands layer-key root-hex settings)]
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
                 (with-images [(:asset/url asset) head-url (fx/strands-url (:asset/url asset))]
                   (fn [[img head strands]]
                     (when-let [^js el (and img @node)]
                       (let [src (ombre img head strands layer-key root-hex settings)]
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
          :style {"--pl-z" z}}])})))

(defn- combined-alpha [imgs w h]
  (let [out (js/Float64Array. (* w h))]
    (doseq [img imgs :when img]
      (let [px (pixels-of img w h)]
        (dotimes [i (* w h)]
          (aset out i (max (aget out i) (aget px (+ 3 (* 4 i))))))))
    out))

(defn- eye-settings
  "The part of the face settings that changes how the eyes are drawn."
  [portrait]
  (select-keys (face/face-settings portrait) [:second-eye :pupil]))

(defn- skin-image
  "A canvas of what lands on the skin, to be laid over the portrait with
   multiply -- white where nothing does: the shadow the overhanging hair
   casts, then blush and freckles placed from the eyes (portrait-face)."
  [casters skins hairs eyes-img eyes-asset nose-img light fs]
  (let [ref (first (remove nil? (concat casters skins)))
        w (.-naturalWidth ref) h (.-naturalHeight ref)
        skin (combined-alpha skins w h)
        hair (combined-alpha hairs w h)
        k (when (seq (remove nil? casters))
            (fx/shadow-map (combined-alpha casters w h) skin hair w h light))
        m (when (and eyes-img (face/marks? fs) (seq (:asset/iris eyes-asset)))
            (let [eye-alpha (combined-alpha [eyes-img] w h)
                  showing (js/Float64Array. (* w h))]
              (dotimes [i (* w h)]
                (aset showing i (* (/ (aget skin i) 255)
                                   (- 1 (/ (max (aget hair i) (aget eye-alpha i)) 255)))))
              (face/marks fs (colorize/iris-shapes eyes-asset
                                                   (layout/contain-rect (.-naturalWidth eyes-img) (.-naturalHeight eyes-img) w h))
                          showing w h
                          (when nose-img (face/alpha-box (combined-alpha [nose-img] w h) w h)))))
        ks (when (pos? (:shade fs))
             (let [showing (js/Float64Array. (* w h))]
               (dotimes [i (* w h)]
                 (aset showing i (* (/ (aget skin i) 255) (- 1 (/ (aget hair i) 255)))))
               (face/shade-map fs showing hair w h)))
        mb (:blush m) mf (:freckles m)
        blush-rgb (colorize/hex->rgb (:blush-colour fs))
        c (new-canvas w h) ctx (.getContext c "2d")
        image-data (.createImageData ctx w h)
        px (.-data image-data)]
    (dotimes [p (* w h)]
      (let [kk (+ (if k (aget k p) 0) (if ks (aget ks p) 0))
            kb (if mb (aget mb p) 0)
            kf (if mf (aget mf p) 0)]
        (when (or (pos? kk) (pos? kb) (pos? kf))
          (let [[sr sg sb] (fx/shadow-factors kk)
                [mr mg mbb] (face/mark-factors kb kf blush-rgb)
                i (* 4 p)]
            (aset px i (* 255 sr mr)) (aset px (+ i 1) (* 255 sg mg)) (aset px (+ i 2) (* 255 sb mbb))
            (aset px (+ i 3) 255)))))
    (.putImageData ctx image-data 0 0)
    c))

(defonce ^:private skin-cache (atom {}))

(defn- skin-overlay [casters skins hairs eyes-img eyes-asset nose-img light fs]
  (let [k (conj (mapv #(mapv (fn [i] (some-> i .-src)) %) [casters skins hairs])
                (some-> eyes-img .-src) (:asset/iris eyes-asset) (some-> nose-img .-src) light fs)]
    (or (get @skin-cache k)
        (let [c (skin-image casters skins hairs eyes-img eyes-asset nose-img light fs)]
          (swap! skin-cache #(assoc (if (> (count %) 16) {} %) k c))
          c))))

(defn- skin-spec
  "What the skin overlay needs: {:casters :skin :hair (url lists) :eyes (url)
   :eyes-asset :light :face}, or nil when nothing lands on the skin -- no
   overhanging hair, no blush, no freckles."
  [portrait dev?]
  (let [sel (fn [ks pred] (vec (keep #(let [a (pa/selected-asset portrait %)] (when (and a (pred a)) (:asset/url a))) ks)))
        casters (sel pa/layer-order fx/casts-shadow?)
        fs (select-keys (face/face-settings portrait) [:blush :blush-colour :freckles :freckle-place :freckle-strength :shade])
        eyes (pa/selected-asset portrait :eyes)
        marks? (and (face/marks? fs) (seq (:asset/iris eyes)))
        skin (sel [:head :ears] any?)]
    (when (and (seq skin) (or (seq casters) marks? (pos? (:shade fs))))
      {:casters casters :skin skin :hair (sel [:hair-front :bangs] any?)
       :eyes (when marks? (:asset/url eyes))
       :nose (when marks? (some-> (pa/selected-asset portrait :nose) :asset/url))
       :eyes-asset (when marks? (as-placed eyes dev?))
       :light (:light (fx/ombre-settings portrait))
       :face fs})))

(defn- skin-layer
  "Shadow, blush and freckles, over everything, multiplied: confined to skin
   the hair does not cover, so they darken nothing else."
  [spec]
  (let [node (atom nil)
        paint! (fn [{:keys [casters skin hair eyes nose eyes-asset light face]}]
                 (let [n1 (count casters) n2 (count skin) n3 (count hair)]
                   (with-images (concat casters skin hair [eyes nose])
                     (fn [imgs]
                       (when-let [^js el @node]
                         (let [src (skin-overlay (subvec imgs 0 n1) (subvec imgs n1 (+ n1 n2))
                                                 (subvec imgs (+ n1 n2) (+ n1 n2 n3))
                                                 (nth imgs (+ n1 n2 n3)) eyes-asset (peek imgs) light face)]
                           (set! (.-width el) (.-width src))
                           (set! (.-height el) (.-height src))
                           (.drawImage (.getContext el "2d") src 0 0)))))))]
    (r/create-class
     {:component-did-mount (fn [_] (paint! spec))
      :component-did-update (fn [this _] (paint! (second (r/argv this))))
      :reagent-render
      (fn [_]
        [:canvas.portrait-layer.portrait-layer-shadow {:ref #(reset! node %)}])})))

(defn- light-images
  "The two overlays of portrait-light at the art's size: a canvas to lay
   over the portrait with multiply (white where nothing darkens) and one
   with screen (black where nothing lights)."
  [layer-imgs eyes-asset hair-imgs ls]
  (let [ref (first (remove nil? layer-imgs))
        w (.-naturalWidth ref) h (.-naturalHeight ref) n (* w h)
        raw (let [c (new-canvas w h) g (.getContext c "2d")]
              (doseq [img layer-imgs :when img] (.drawImage g img 0 0 w h))
              (.-data (.getImageData g 0 0 w h)))
        alpha (js/Float64Array. n) ink (js/Float64Array. n)
        _ (dotimes [p n]
            (let [i (* 4 p)]
              (aset alpha p (aget raw (+ i 3)))
              (aset ink p (/ (+ (aget raw i) (aget raw (+ i 1)) (aget raw (+ i 2))) 765))))
        maps (light/light-maps ls {:alpha alpha :ink ink
                                   :iris (when (and (:glow ls) (seq (:asset/iris eyes-asset))) (iris-coverage eyes-asset w h))
                                   :hair-cover (combined-alpha hair-imgs w h)}
                               w h)
        paint (fn [arrays fill]
                (let [c (new-canvas w h) ctx (.getContext c "2d")
                      data (.createImageData ctx w h) px (.-data data)]
                  (dotimes [p n]
                    (let [i (* 4 p)]
                      (if arrays
                        (let [[r g b] arrays]
                          (aset px i (* fill (aget r p))) (aset px (+ i 1) (* fill (aget g p))) (aset px (+ i 2) (* fill (aget b p))))
                        (do (aset px i fill) (aset px (+ i 1) fill) (aset px (+ i 2) fill)))
                      (aset px (+ i 3) 255)))
                  (.putImageData ctx data 0 0)
                  c))]
    [(when (:mul maps) (paint (:mul maps) 255)) (paint (:add maps) 1)]))

(defonce ^:private light-cache (atom {}))

(defn- light-overlays [layer-imgs eyes-asset hair-imgs ls]
  (let [k [(mapv #(some-> % .-src) layer-imgs) (:asset/iris eyes-asset) (mapv #(some-> % .-src) hair-imgs) ls]]
    (or (get @light-cache k)
        (let [c (light-images layer-imgs eyes-asset hair-imgs ls)]
          (swap! light-cache #(assoc (if (> (count %) 12) {} %) k c))
          c))))

(defn- light-spec
  "What the light overlays need, or nil when there is no light."
  [portrait dev?]
  (let [ls (light/light-settings portrait)]
    (when (light/lit? ls)
      (let [url-of #(some-> (pa/selected-asset portrait %) :asset/url)]
        {:layers (vec (keep url-of pa/layer-order))
         :hair (vec (keep url-of [:hair-front :bangs]))
         :eyes-asset (some-> (pa/selected-asset portrait :eyes) (as-placed dev?))
         :settings ls}))))

(defn- light-layer
  "Glow and mood light over everything: a multiply canvas, then a screen one."
  [spec]
  (let [mul-node (atom nil) add-node (atom nil)
        paint! (fn [{:keys [layers hair eyes-asset settings]}]
                 (with-images (concat layers hair)
                   (fn [imgs]
                     (let [[m a] (light-overlays (subvec imgs 0 (count layers)) eyes-asset
                                                 (subvec imgs (count layers)) settings)]
                       (doseq [[node src] [[@mul-node m] [@add-node a]]]
                         (when-let [^js el node]
                           (if src
                             (do (set! (.-width el) (.-width src))
                                 (set! (.-height el) (.-height src))
                                 (.drawImage (.getContext el "2d") src 0 0)
                                 (set! (.-hidden el) false))
                             (set! (.-hidden el) true))))))))]
    (r/create-class
     {:component-did-mount (fn [_] (paint! spec))
      :component-did-update (fn [this _] (paint! (second (r/argv this))))
      :reagent-render
      (fn [_]
        [:<>
         [:canvas.portrait-layer.portrait-layer-light.portrait-layer-multiply {:ref #(reset! mul-node %)}]
         [:canvas.portrait-layer.portrait-layer-light.portrait-layer-screen {:ref #(reset! add-node %)}]])})))

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
         skin (skin-spec portrait dev?)
         lit (light-spec portrait dev?)]
     [:div.portrait-composite
      (or attrs {})
      (map-indexed
        (fn [z layer-key]
          (when-let [asset (some->> (get layers layer-key)
                                    :asset/id
                                    (pa/asset-by-id layer-key))]
            (if (colorizes? layer-key asset)
              ^{:key layer-key}
              [:<>
               ;; filled-in whites go under the eye they belong to
               (when (and whites (:asset/whites asset))
                 [whites-layer (as-placed asset dev?) whites z])
               [colorized-layer (as-placed asset dev?)
                (pa/tint-for portrait layer-key)
                (pa/effective-gamma portrait layer-key asset)
                (eye-settings portrait) z]]
              (if (fx/hair-layer? layer-key)
                ^{:key layer-key}
                [ombre-layer layer-key asset (pa/tint-for portrait layer-key)
                 (fx/piece-settings ombre-settings asset layer-key) head-url z]
                ^{:key layer-key}
                [:div.portrait-layer
                 (if (= :as-drawn (pa/render-mode layer-key asset))
                   (as-drawn-layer (solid-src (:asset/url asset)) z)
                   (tinted-layer (solid-src (:asset/url asset)) (pa/tint-for portrait layer-key) z))]))))
        pa/layer-order)
      (when skin ^{:key "skin"} [skin-layer skin])
      (when lit ^{:key "light"} [light-layer lit])])))

;; ---------------- rasterization (for PDF export) ----------------
;;
;; The export bakes the layers on a canvas with the same placement (layout/contain-rect)
;; and tint (multiply, then the asset's alpha restored) as the drawer.

(def ^:private raster-width 600)
(def ^:private raster-height 750)   ;; 4:5, matching the on-screen frame

(defn- load-image [url]
  (.then (solid-promise url)
         (fn [src]
           (js/Promise.
             (fn [resolve _reject]
               (let [img (js/Image.)]
                 (set! (.-onload img) #(resolve img))
                 ;; A layer that will not decode is skipped, not fatal -- the rest of
                 ;; the portrait is still worth printing.
                 (set! (.-onerror img) #(resolve nil))
                 (set! (.-src img) src)))))))

(defn- draw-credit!
  "Draws the credit for artist `names` on canvas context `ctx` along the bottom of a `w`x`h`
   frame, stroked then filled: the longest wording from pa/credit-variants that fits
   (layout/fit-credit). The face must already be loaded. Why it is in the pixels:
   PORTRAIT-COMPOSITOR.md, \"The baked credit and the site mark\"."
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

(defn- b64->bytes
  "Base64 text `s` as a Uint8Array."
  [s]
  (let [bin (js/atob s) n (.-length bin) a (js/Uint8Array. n)]
    (dotimes [i n] (aset a i (.charCodeAt bin i)))
    a))

(defn- bytes->b64
  "Uint8Array `a` as base64 text."
  [^js a]
  (js/btoa (apply str (for [i (range 0 (.-length a) 0x8000)]
                        (.apply js/String.fromCharCode nil (.subarray a i (min (.-length a) (+ i 0x8000))))))))

(defn- piece-marks
  "Resolves to the licensed-art marks of `portrait`'s pieces (nil for unmarked ones), read from the
   files the browser already holds."
  [portrait]
  (js/Promise.all
   (clj->js (for [k pa/layer-order
                  :let [url (some->> (get-in portrait [:layers k]) :asset/id (pa/asset-by-id k) :asset/url)]
                  :when url]
              (-> (js/fetch url)
                  (.then #(.arrayBuffer %))
                  (.then #(art-mark/read-mark (js/Uint8Array. %)))
                  (.catch (constantly nil)))))))

(defn- rasterize*
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
            (clj->js (-> (mapv (fn [[_ a]] (load-image (:asset/url a))) selected)
                         ;; a canvas substitutes silently for a face that is
                         ;; not resident yet, so wait for it like an image
                         (conj (load-credit-font))
                         ;; each hair piece's precomputed strands, if any
                         (into (map (fn [[k a]] (if (fx/hair-layer? k)
                                                  (load-image (fx/strands-url (:asset/url a)))
                                                  (js/Promise.resolve nil)))
                                    selected)))))
          (.then
            (fn [results]
              (let [imgs (.slice results 0 (count selected))
                    strands-of (zipmap (map first selected)
                                       (array-seq (.slice results (inc (count selected)))))]
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
                    (when (and (:asset/whites asset) (pa/whites-colour portrait))
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
                    ;; Tint by multiply so the lines survive (PORTRAIT-TINTING.md);
                    ;; :as-drawn assets, such as the teeth, are not tinted at all.
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
                                                    (pa/effective-gamma portrait layer-key asset)
                                                    (eye-settings portrait))
                                    x y dw dh))

                      ;; hair: root to tip, the same as the drawer and the card
                      (fx/hair-layer? layer-key)
                      (let [[x y dw dh] (layout/contain-rect (.-naturalWidth img)
                                                             (.-naturalHeight img)
                                                             raster-width raster-height)]
                        (.clearRect tctx 0 0 raster-width raster-height)
                        (.drawImage tctx (ombre img (img-of :head) (strands-of layer-key) layer-key (pa/tint-for portrait layer-key)
                                                (fx/piece-settings settings asset layer-key))
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
                  ;; the cast shadow, blush and freckles after every layer,
                  ;; before the credit
                  (when-let [{:keys [casters skin hair eyes nose eyes-asset light face]}
                             (skin-spec portrait (:dev-mode? @re-frame.db/app-db))]
                    (let [by-url (into {} (map (fn [[_ a] img] [(:asset/url a) img]) selected (array-seq imgs)))
                          skins (vec (keep by-url skin))]
                      (when (seq skins)
                        (let [s (skin-overlay (vec (keep by-url casters)) skins (vec (keep by-url hair))
                                              (by-url eyes) eyes-asset (by-url nose) light face)
                              [x y dw dh] (layout/contain-rect (.-width s) (.-height s) raster-width raster-height)]
                          (set! (.-globalCompositeOperation ctx) "multiply")
                          (.drawImage ctx s x y dw dh)
                          (set! (.-globalCompositeOperation ctx) "source-over")))))
                  ;; glow and mood light over everything, before the credit
                  (when-let [{:keys [layers hair eyes-asset settings]}
                             (light-spec portrait (:dev-mode? @re-frame.db/app-db))]
                    (let [by-url (into {} (map (fn [[_ a] img] [(:asset/url a) img]) selected (array-seq imgs)))
                          limgs (vec (keep by-url layers))]
                      (when (seq limgs)
                        (let [[m a] (light-overlays limgs eyes-asset (vec (keep by-url hair)) settings)]
                          (doseq [[src op] [[m "multiply"] [a "screen"]] :when src]
                            (let [[x y dw dh] (layout/contain-rect (.-width src) (.-height src) raster-width raster-height)]
                              (set! (.-globalCompositeOperation ctx) op)
                              (.drawImage ctx src x y dw dh)))
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

(defn rasterize
  "Bake `portrait` into a PNG. Resolves to base64 (no data: prefix), or nil when there is nothing
   to draw or the browser cannot do it. Carries the licensed-art mark when any piece does."
  [portrait]
  (-> (js/Promise.all #js [(rasterize* portrait) (piece-marks portrait)])
      (.then (fn [[b64 marks]]
               (when b64 (bytes->b64 (art-mark/carry-marks (b64->bytes b64) (array-seq marks))))))))

;; Portraits baked ahead of export, by value: {:promise p} while baking, then {:png b64-or-nil}.
(defonce ^:private baked (atom {}))

(defn bake-ahead!
  "Start baking `portrait` unless it is baked or baking; the promise resolves to `rasterize`'s value.
   Keyed by the credits too, which are drawn into the picture and may arrive after a first bake.
   Keeps the last few, since a builder session walks through many portraits."
  [portrait]
  (when (pa/drawable? portrait)
    (let [k [portrait (pa/current-overrides)]]
      (or (:promise (get @baked k))
          (when-let [png (find (get @baked k) :png)] (js/Promise.resolve (val png)))
          (let [p (-> (rasterize portrait)
                      (.catch (fn [_] nil))
                      (.then (fn [png]
                               (swap! baked #(if (contains? % k) (assoc % k {:png png}) %))
                               png)))]
            (swap! baked (fn [m] (assoc (if (>= (count m) 8) {} m) k {:promise p})))
            p)))))

(defn baked-png
  "[png-b64-or-nil] once `portrait` is baked, else nil."
  [portrait]
  (when-let [e (find (get @baked [portrait (pa/current-overrides)]) :png)] [(val e)]))

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
      {:class (classes "pl-fill" (cond override "overridden" (not (zero? shade)) "shaded"))
       :style {"--pl-fill" eff}
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
  (let [stored  (get-in portrait [:colors slot])
        none?   (= stored pa/no-whites)
        cur     (when-not none? stored)
        tweaked (pa/tweaked-layers-in-slot portrait slot)
        open?   (= open-slot slot)
        label   (pa/color-slot-labels slot)]
    [:div.pl-slot
     {:class (classes (when stored "on") (when (seq tweaked) "has-tweaks") (when open? "open"))}
     [:span.pl-slot-name label]
     (when (seq tweaked)
       [:span.pl-slot-tweaks
        {:title (str (count tweaked) " piece" (when (> (count tweaked) 1) "s") " tweaked")}
        (count tweaked)])
     [:button.pl-slot-swatch
      {:type "button"
       :class (if cur "pl-fill" "unset")
       :style (when cur {"--pl-fill" cur})
       :aria-expanded open?
       :title (cond none? "No whites: the eye as drawn"
                    cur (str "Base color " (s/upper-case cur))
                    :else "Not set — tap to pick")
       :on-click #(dispatch [:portrait/toggle-slot-panel slot])}
      [:span.pl-sub-dots
       (map-indexed
         (fn [i k]
           ^{:key k}
           [:span.pl-sub-dot.pl-fill {:class (str "pl-sub-dot-" i)
                                      :style {"--pl-fill" (pa/tint-for portrait k)}
                              :title (str (pa/layer-labels k) ": " (s/upper-case (pa/tint-for portrait k)))}])
         (take 4 tweaked))]]
     [:button.pl-slot-clear
      {:type "button" :disabled (nil? cur)
       :title (str "Clear " label)
       :on-click #(dispatch [:portrait/clear-slot-color slot])}
      "×"]]))

(defn- ombre-slider
  "One setting, 0..100 on screen, 0..1 stored, through `event` (the ombre's
   by default)."
  ([label k value] (ombre-slider label k value :portrait/set-ombre))
  ([label k value event]
   [:label.pl-ombre-row
    [:span.pl-ombre-label label]
    [:input {:type "range" :min 0 :max 100 :step 5
             :value (js/Math.round (* 100 value))
             :aria-label label
             :on-change #(dispatch [event k (/ (js/parseInt (target-value %) 10) 100)])}]
    [:span.pl-sub-shade-val (str (js/Math.round (* 100 value)))]]))

(defn- segmented
  "A row of mutually exclusive buttons: `options` [[value label] ...]."
  [label aria current options on-pick]
  [:div.pl-ombre-row
   [:span.pl-ombre-label label]
   [:div.pl-segmented {:role "group" :aria-label aria}
    (for [[v l] options]
      ^{:key (str v)}
      [:button {:type "button"
                :class (when (= v current) "on")
                :aria-pressed (= v current)
                :on-click #(on-pick v)}
       l])]])

(defn- eye-controls
  "Pupils and a second eye colour. Only for an eye style with its irises
   placed: the effects are drawn inside that placement."
  [portrait]
  (when (seq (:asset/iris (pa/selected-asset portrait :eyes)))
    (let [{:keys [pupil second-eye]} (face/face-settings portrait)]
      [:div.pl-ombre
       [segmented "Pupils" "Pupil shape" pupil
        [[:round "Round"] [:snake "Snake"] [:goat "Goat"]]
        #(dispatch [:portrait/set-face :pupil (when-not (= % :round) %)])]
       [:span.pl-panel-heading "Second eye"]
       [:div.pl-presets
        [:button.pl-preset.pl-preset-none
         {:type "button" :title "Both eyes alike"
          :class (when-not second-eye "on")
          :on-click #(dispatch [:portrait/set-face :second-eye nil])} "\u2205"]
        (for [c (pa/color-presets :eyes)]
          ^{:key c}
          [:button.pl-preset.pl-fill
           {:type "button" :style {"--pl-fill" c} :title c
            :class (when (= c second-eye) "on")
            :on-click #(dispatch [:portrait/set-face :second-eye c])}])
        [:span.pl-preset.custom {:title "Custom colour for the right eye"}
         [:input {:type "color" :value (or second-eye "#3d5c8f")
                  :aria-label "Custom second eye colour"
                  :on-change #(dispatch [:portrait/set-face :second-eye (target-value %)])}]]]
       (let [{:keys [glow glow-strength]} (light/light-settings portrait)]
         [:<>
          [:span.pl-panel-heading "Glow"]
          [:div.pl-presets
           [:button.pl-preset.pl-preset-none
            {:type "button" :title "No glow" :class (when-not glow "on")
             :on-click #(dispatch [:portrait/set-light :glow nil])} "\u2205"]
           (for [c light/glow-colours]
             ^{:key c}
             [:button.pl-preset.pl-fill
              {:type "button" :style {"--pl-fill" c} :title c :class (when (= c glow) "on")
               :on-click #(dispatch [:portrait/set-light :glow c])}])
           [:span.pl-preset.custom {:title "Custom glow colour"}
            [:input {:type "color" :value (or glow "#ff8a3a") :aria-label "Custom glow colour"
                     :on-change #(dispatch [:portrait/set-light :glow (target-value %)])}]]]
          (when glow [ombre-slider "Strength" :glow-strength glow-strength :portrait/set-light])])])))

(defn- light-controls
  "Mood light over the whole portrait: which light, how it falls, from where."
  [portrait]
  (let [{:keys [mood style from]} (light/light-settings portrait)]
    [:div.pl-light.pl-ombre
     [:span.pl-strip-label "light"]
     [segmented "Mood" "Mood light" mood
      [[nil "None"] [:torch "Torch"] [:moon "Moon"] [:arcane "Arcane"]]
      #(dispatch [:portrait/set-light :mood %])]
     (when mood
       [:<>
        [segmented "Style" "How the light falls" style
         [[:edge "Edge"] [:cast "Cast"] [:rim "Rim"]]
         #(dispatch [:portrait/set-light :style %])]
        [segmented "From" "Where the light comes from" from
         [[:left "Left"] [:right "Right"] [:above "Above"] [:below "Below"]]
         #(dispatch [:portrait/set-light :from %])]])]))

(defn- skin-controls
  "Blush and freckles, placed from the eyes -- so only once an eye style with
   placed irises is chosen."
  [portrait]
  (when (seq (:asset/iris (pa/selected-asset portrait :eyes)))
    (let [{:keys [blush blush-colour freckles freckle-place freckle-strength shade]} (face/face-settings portrait)]
      [:div.pl-ombre
       [ombre-slider "Blush" :blush blush :portrait/set-face]
       (when (pos? blush)
         [:div.pl-presets
          (for [c face/blush-colours]
            ^{:key c}
            [:button.pl-preset.pl-fill
             {:type "button" :style {"--pl-fill" c} :title c
              :class (when (= c blush-colour) "on")
              :on-click #(dispatch [:portrait/set-face :blush-colour c])}])])
       [ombre-slider "Freckles" :freckles freckles :portrait/set-face]
       (when (pos? freckles)
         [:<>
          ;; 0 over the near cheek, 100 across both cheeks and the nose
          [ombre-slider "Placement" :freckle-place freckle-place :portrait/set-face]
          [ombre-slider "Prominence" :freckle-strength freckle-strength :portrait/set-face]])
       ;; the skin a little dimmer where the hair sits over it
       [ombre-slider "Shade" :shade shade :portrait/set-face]])))

(defn- ombre-controls
  "The hair's second colour and how it runs. With no tips colour the hair is
   one colour with a little depth, which is where everyone starts."
  [portrait]
  (let [{:keys [tip start falloff depth angle clumps bangs shine shine-at split]} (fx/ombre-settings portrait)]
    [:div.pl-ombre
     [:span.pl-panel-heading "Tips"]
     [:div.pl-presets
      [:button.pl-preset.pl-preset-none
       {:type "button" :title "No second colour"
        :class (when-not tip "on")
        :on-click #(dispatch [:portrait/set-ombre :tip nil])} "\u2205"]
      (for [c (pa/color-presets :hair)]
        ^{:key c}
        [:button.pl-preset.pl-fill
         {:type "button" :style {"--pl-fill" c} :title c
          :class (when (= c tip) "on")
          :on-click #(dispatch [:portrait/set-ombre :tip c])}])
      [:span.pl-preset.custom {:title "Custom tips colour"}
       [:input {:type "color" :value (or tip "#c0a080")
                :aria-label "Custom tips colour"
                :on-change #(dispatch [:portrait/set-ombre :tip (target-value %)])}]]]
     (when tip
       [:<>
        ;; on a split, where the line falls
        [ombre-slider (if split "Line" "Starts") :start start]
        [ombre-slider "Blend" :falloff falloff]
        ;; how far the tip colour runs up some strands and holds back on others
        [ombre-slider "Streaks" :clumps clumps]
        ;; a fringe can keep the root shade, take just the tips, or dye
        ;; with the rest; until chosen, each piece does what suits it
        ;; root to tip only: a split or an angle runs across the bangs too
        (when-let [asset (when-not (or split angle) (pa/selected-asset portrait :bangs))]
          (let [current (or bangs (fx/bangs-choice asset))]
            [:div.pl-ombre-row
             [:span.pl-ombre-label "Bangs"]
             [:div.pl-segmented {:role "group" :aria-label "How the bangs take the tips colour"}
              (for [[k label] [[:roots "Roots"] [:tipped "Tipped"] [:dyed "Dyed"]]]
                ^{:key k}
                [:button {:type "button"
                          :class (when (= k current) "on")
                          :aria-pressed (= k current)
                          :on-click #(dispatch [:portrait/set-ombre :bangs
                                                ;; the piece's own default is stored as no choice
                                                (when-not (= k (fx/bangs-choice asset)) k)])}
                 label])]]))])
     [ombre-slider "Depth" :depth depth]
     ;; light catching across the top of the head
     [ombre-slider "Shine" :shine shine]
     ;; how far down each piece the ring sits
     (when (pos? shine) [ombre-slider "Shine at" :shine-at shine-at])
     [:label.pl-ombre-row
      [:span.pl-ombre-label "Runs"]
      [:select {:value (cond split "split" angle "angle" :else "roots")
                :aria-label "Which way the tips run"
                :on-change #(dispatch [:portrait/set-run (target-value %)])}
       [:option {:value "roots"} "From the roots"]
       [:option {:value "angle"} "At an angle"]
       ;; not ready: one line across every piece, which is not how a split
       ;; dye falls on layered hair. Developer mode only, for the rework.
       (when (or split @(subscribe [:orcpub.dnd.e5/dev-mode?]))
         [:option {:value "split"} "Split"])]]
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
       ;; whites can be left unfilled: the art as drawn
       (when (= slot :whites)
         [:button.pl-preset.pl-preset-none
          {:type "button" :title "No whites: the eye as drawn"
           :class (when (= pa/no-whites (get-in portrait [:colors :whites])) "on")
           :on-click #(dispatch [:portrait/set-slot-color :whites pa/no-whites])} "\u2205"])
       (for [c (pa/color-presets slot)]
         ^{:key c}
         [:button.pl-preset.pl-fill
          {:type "button" :style {"--pl-fill" c} :title c
           :on-click #(dispatch [:portrait/set-slot-color slot c])}])
       [:span.pl-preset.custom {:title "Custom color"}
        [:input {:type "color"
                 :value (let [c (get-in portrait [:colors slot])]
                          (if (and (string? c) (re-matches #"#[0-9a-fA-F]{6}" c)) c "#c0a080"))
                 :aria-label (str "Custom " (pa/color-slot-labels slot) " color")
                 :on-change #(dispatch [:portrait/set-slot-color slot (target-value %)])}]]]]
     (when (= slot :hair) [ombre-controls portrait])
     (when (= slot :eyes) [eye-controls portrait])
     (when (= slot :skin) [skin-controls portrait])
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
      :class (classes "pl-fill" (when override "overridden")
                      (when (and (not override) shade (not (zero? shade))) "shaded")
                      (when open? "open"))
      :style {"--pl-fill" (pa/tint-for portrait layer-key)}
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
       [:span.pl-cat-chip.pl-fill {:style {"--pl-fill" (pa/layer-colors layer-key)}}]
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
  "An artist's name, as a link where they have one this site draws."
  [{:keys [:artist/name :artist/link]}]
  (if-let [attrs (artist-profile/external-link link)]
    [:a.pl-thumb-credit-link (assoc attrs :on-click #(.stopPropagation %)) name]
    [:span name]))

(defn- linked-credit
  "The credit under the character page's thumbnail, each name a link where the artist has
   one. When the whole line overflows the 100px strip, the lead artist is named and the rest
   collapse into a '+N' that opens the full list, every name still a link."
  [portrait]
  (r/with-let [open? (r/atom false)]
    (let [_ @(subscribe [:portrait/artist-overrides]) ; re-render when the credits arrive
          [lead & more :as named] (filter :artist/name (pa/artists-for-layers (:layers portrait)))
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
;; A small label, then each name on a broken rule, decorated only by the rule and the
;; link marks' colours. Design captures: docs/design/portrait-credit on agents/develop.

(defn- credit-mark
  "One of an artist's links, as its service's mark in the link's own colour.
   The label is the accessible name: an icon alone is unreadable to a screen
   reader, and a link with no icon falls back to the label as text."
  [artist-name {:link/keys [label url icon color]}]
  (when-let [attrs (artist-profile/external-link url)]
    [:a.lk-mark (merge attrs
                       {:title (str artist-name " on " label " (" (artist-profile/link-host url) ")")
                        :aria-label (str artist-name " on " label)
                        :style (when color {"--lk-mark-color" color})})
     (if icon
       [:span.lk-ico {:style {"--lk-icon" (str "url(/image/social/" icon ".svg)")}}]
       [:span.lk-mark-text (s/lower-case label)])]))

(defn- credit-name
  "The artist's name in the credit, a link to where they chose when it is one this site draws."
  [{:keys [:artist/name :artist/link]}]
  (if-let [attrs (artist-profile/external-link link)]
    [:a.lk-name attrs name]
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
  "The credit for `named` artists in credit order: an \"Art by\" label; the lead on the
   rule with their marks placed by pa/credit-mark-layout; \"with A, B and C\" for the rest;
   then the profile link. Three or four lines however many artists there are."
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
       [:div.lk-with "with " (name-list others)])
     ;; The way to the site's own page about them, kept apart from the name,
     ;; which goes wherever the artist chose. A new tab, like every link in
     ;; the credit, so an unsaved draft is not left behind.
     (when-let [{:keys [href label]} (artist-profile/profile-link named)]
       [:a.lk-about {:href href :target "_blank" :rel "noopener"}
        label [:span.lk-about-arrow {:aria-hidden true} "\u203a"]])]))

(defn credit-preview
  "One artist's credit exactly as the builder draws it, for the account page:
   an artist editing their credit should see the thing itself, not a
   description of it."
  [info]
  [:div.pl-root.pl-credit-preview
   [:div.pl-attribution [credit-lockup [info]]]])

(defn- attribution
  "Who drew what is on canvas.

   An artist with no :artist/name is on canvas but has not said how they want
   to be credited. Showing the label with an empty slot after it reads like a
   rendering fault, and inventing something to put there is worse, so the
   whole strip says so plainly instead."
  [layers]
  (let [_ @(subscribe [:portrait/artist-overrides]) ; re-render when the credits arrive
        infos (pa/artists-for-layers layers)
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
         [:div.pl-empty-hint {:style {"--pl-logo" (str "url(" branding/logo-path ")")}}
          "Pick a layer below, or hit " [:em "Randomize"] "."])]
      [iris-alternative-switch portrait]
      ;; Directly under the art, as on the character summary; not among the actions.
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
      [color-strip portrait open-slot]
      (when any? [light-controls portrait])]
     [:div.pl-pickers-side
      (for [layer-key pa/pickable-layers]
        ^{:key layer-key}
        [category-picker portrait layer-key open-layer])]]))

(defn drawer
  "The compositor drawer, an overlay shown while :portrait/drawer-open?. Mount once at the
   character-builder root. Its stylesheet mounts even when closed (it also styles the
   launcher and the Portrait tab), and its root carries the theme class itself because it
   sits outside .app. See PORTRAIT-COMPOSITOR.md, \"The drawer's chrome\"."
  []
  (let [open? @(subscribe [:portrait/drawer-open?])
        theme @(subscribe [:theme])]
    [:div.pl-root {:class theme}
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
  "The compositor in place, as the builder's Portrait tab: the drawer's body, with Full
   screen (opens the drawer) and a Save that reads Saved when clean, instead of Cancel.
   Ensures a draft on mount. Has its own .pl-root, the stylesheet's one theme hook."
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
  (let [_ @(subscribe [:portrait/artist-overrides])] ; re-render when the credits arrive
    (cond
      (pa/drawable? portrait-data)
      [:div.m-r-20.m-t-10.m-b-10
       [:div.image-character-thumbnail.pl-thumb-box
        [composite portrait-data]
        (when editable? [edit-overlay])]
       (when-let [credit (pa/credit-line portrait-data)]
         [:div.pl-thumb-credit {:title credit} [linked-credit portrait-data]])]

      image-url
      (if editable?
        [:div.m-r-20.m-t-10.m-b-10.pl-thumb-box
         [:img.image-character-thumbnail {:src image-url}]
         [edit-overlay]]
        [:img.m-r-20.m-t-10.m-b-10.image-character-thumbnail {:src image-url}])

      editable?
      [:div.m-r-20.m-t-10.m-b-10.pl-thumb-box.pl-thumb-empty
       {:style {"--pl-logo" (str "url(" branding/logo-path ")")}}
       [:span.pl-thumb-empty-label "PORTRAIT"]
       [edit-overlay]])))
