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
            [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.dnd.e5.portrait-assets :as pa]
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

(defn composite
  "Stacked, tinted portrait for a `portrait` map (see ns doc). `attrs`
   (optional) merges into the outer div so callers can size/position it."
  ([portrait] (composite portrait nil))
  ([portrait attrs]
   (let [layers (:layers portrait)]
     [:div.portrait-composite
      (merge {:style {:position "relative" :width "100%" :height "100%"}} attrs)
      (map-indexed
        (fn [z layer-key]
          (when-let [asset (some->> (get layers layer-key)
                                    :asset/id
                                    (pa/asset-by-id layer-key))]
            ^{:key layer-key}
            [:div.portrait-layer
             {:style (if (= :as-drawn (pa/render-mode layer-key asset))
                       (as-drawn-style (:asset/url asset) z)
                       (mask-style (:asset/url asset)
                                   (pa/tint-for portrait layer-key) z))}]))
        pa/layer-order)])))

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
  [ctx text w h]
  (let [{:keys [size halo center-x baseline outline fill]} (layout/credit-layout w h)
        rgba (fn [[r g b :as c]]
               (str "rgba(" r "," g "," b "," (layout/alpha->unit c) ")"))]
    (set! (.-font ctx) (str "italic " size "px '" layout/credit-font-family "', Georgia, serif"))
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
                      tctx (.getContext tmp "2d")]
                  (doseq [[[layer-key asset] img] (map vector selected (array-seq imgs))
                          :when img]
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
                    (when-not (= :as-drawn (pa/render-mode layer-key asset))
                      (set! (.-globalCompositeOperation tctx) "multiply")
                      (set! (.-fillStyle tctx) (pa/tint-for portrait layer-key))
                      (.fillRect tctx 0 0 raster-width raster-height)
                      ;; multiply paints the whole rect, including where the
                      ;; asset is transparent, so put the asset's own alpha back
                      (set! (.-globalCompositeOperation tctx) "destination-in")
                      (let [[x y dw dh] (layout/contain-rect (.-naturalWidth img)
                                                             (.-naturalHeight img)
                                                             raster-width raster-height)]
                        (.drawImage tctx img x y dw dh)))
                    (.drawImage ctx tmp 0 0))
                  (when-let [credit (pa/credit-line portrait)]
                    ;; The face has to be resident before fillText or the
                    ;; canvas silently substitutes; document.fonts.load is
                    ;; awaited up in `rasterize` before we get here.
                    (draw-credit! ctx credit raster-width raster-height))
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
/* One rhythm across the row, in multiples of 5px: things inside a group sit
   one unit apart, groups two units apart. The gaps used to run 7/5/13/11 --
   four values, no relationship between them -- so nothing read as grouped. */
.pl-attribution {
  display: flex; flex-wrap: wrap; align-items: center;
  justify-content: center; gap: 3px 10px;
  margin: 7px 0 2px; font-size: 12px; text-align: center;
}
.pl-attribution-label {
  font: 700 10px/1 inherit; letter-spacing: 0.18em; text-transform: uppercase;
  color: #616a7a;
}
.pl-attribution-empty { color: #616a7a; font-style: italic; font-family: 'Vollkorn', Georgia, serif; }
/* A credit is a line of prose, not a row of buttons. Boxed uppercase labels
   read as navigation and competed with the artist's name, which is the part
   that matters. These sit after it as quiet lowercase text. */
/* The icons sit a touch lower than a flexbox centre would put them: the text
   beside them is small caps with no descenders, so its optical centre is
   below its box centre and centring the marks left them floating. */
.pl-artist-links {
  /* the links sit INSIDE .pl-artist, so they inherit its 5px intra-group gap;
     this takes them to 10, the same distance that separates the label from
     the name -- marks are their own group, not part of the byline */
  margin-left: 5px; display: inline-flex; align-items: center; gap: 10px;
}
.pl-artist-link {
  font: 400 11px/1 'Open Sans', system-ui, sans-serif;
  /* the fallback for a link with no colour of its own; anything with a
     :link/color overrides this inline and wears it at rest */
  color: #98a2b3; text-decoration: none;
  display: inline-flex; align-items: center;
  transition: filter 110ms ease, transform 110ms ease;
}
.pl-artist-link:hover { transform: translateY(-1px); filter: brightness(1.28); }
/* currentColor through a mask, so the mark is the link's colour and inherits
   the hover with it -- an <img> would need a second asset per theme. */
/* 12px, not 15. A mark next to text is sized to the CAP HEIGHT of that text,
   not to its line box. The name measures about 9px from cap to baseline and
   the marks were 15px, so they broke the cap line top and bottom and read as
   floating above it however carefully their boxes were centred.
   (No double quotes in here: this stylesheet is a Clojure string.) */
.pl-artist-icon {
  display: block; width: 12px; height: 12px;
  background-color: currentColor;
  -webkit-mask-size: contain;   mask-size: contain;
  -webkit-mask-repeat: no-repeat; mask-repeat: no-repeat;
  -webkit-mask-position: center;  mask-position: center;
}
.app.light-theme .pl-artist-link,
.pl-root.light-theme .pl-artist-link { color: #7c8493; }
.app.light-theme .pl-artist-link:hover,
.pl-root.light-theme .pl-artist-link:hover { filter: brightness(0.86); }
/* centre, not baseline: the name's box carries an underline and a pixel of
   padding, so a shared baseline put its centre 1.5px below the swirl's and
   the icons' -- five pixels of spread across the row, which is what made it
   look unsettled */
.pl-artist { display: inline-flex; align-items: center; gap: 5px; }
.pl-artist-swirl {
  color: #f0a100; font-family: 'Vollkorn', Georgia, serif; font-style: italic;
  /* A highlight parked off the left edge. Both ends of the gradient are the
     swirl's own colour, so at rest it is indistinguishable from the flat
     glyph -- the sweep only exists while it is moving. */
  background-image: linear-gradient(105deg,
    #f0a100 0%, #f0a100 40%, #fff0c4 50%, #f0a100 60%, #f0a100 100%);
  background-size: 320% 100%;
  background-position: 100% 0;
  -webkit-background-clip: text; background-clip: text;
}
/* Hovering the ARTIST, not the glyph: a 9px target is a poor thing to ask
   anyone to find, and the flourish belongs to her name rather than to itself. */
.pl-artist:hover .pl-artist-swirl {
  color: transparent;
  animation: pl-swirl-shimmer 900ms cubic-bezier(.33,0,.2,1) 1;
}
@keyframes pl-swirl-shimmer {
  from { background-position: 100% 0; }
  to   { background-position: 0% 0; }
}
/* Anyone who has asked for less motion gets the colour and none of the sweep. */
@media (prefers-reduced-motion: reduce) {
  .pl-artist:hover .pl-artist-swirl { color: #f0a100; animation: none; }
}
.pl-artist a:not(.pl-artist-link), .pl-artist span.pl-artist-name {
  color: #ebeef4; text-decoration: none;
  border-bottom: 1px dotted rgba(240,161,0,0.32);
  /* the underline hangs below the text rather than growing the box, so the
     name's centre matches the swirl's and the marks' */
  padding-bottom: 1px; margin-top: 1px;
  font: italic 13px/1 'Vollkorn', Georgia, serif;
}
.pl-artist a:not(.pl-artist-link):hover { color: #ffcc5e; border-bottom-color: #f0a100; }
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
.pl-root.light-theme .pl-drawer-title-rune,
.pl-root.light-theme .pl-artist-swirl {
  color: #33658A;
  /* the dark theme's cream highlight vanishes on a pale ground, so light gets
     its own: a lift toward cyan rather than toward white */
  background-image: linear-gradient(105deg,
    #33658A 0%, #33658A 40%, #6fb3d8 50%, #33658A 60%, #33658A 100%);
}
@media (prefers-reduced-motion: reduce) {
  .pl-root.light-theme .pl-artist:hover .pl-artist-swirl { color: #33658A; }
}
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
.pl-root.light-theme .pl-panel-heading,
.pl-root.light-theme .pl-attribution-label { color: #6b6b6b; }
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
.pl-root.light-theme .pl-artist a:not(.pl-artist-link),
.pl-root.light-theme .pl-artist span.pl-artist-name {
  color: #363636; border-bottom-color: rgba(51,101,138,0.4);
}
.pl-root.light-theme .pl-artist a:hover { color: #33658A; border-bottom-color: #33658A; }
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
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
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
  (let [{:keys [override shade]} (get-in portrait [:tweaks layer-key])
        shade (or shade 0)
        eff   (pa/tint-for portrait layer-key)
        any?  (boolean (or override (not (zero? shade))))
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
      "×"]]))

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

(defn- linked-credit
  "The credit line with each named artist turned into a link where one is
   known.

   The drawer has done this since it was built; this is the character page,
   which is where the art actually travels. A share link, a PDF and a baked
   PNG can only carry the name as text -- an image cannot hold a link and a
   social unfurl renders its description as plain text -- so this is the one
   surface downstream of the builder that can send someone to the artist, and
   it was printing a dead string."
  [portrait credit]
  (let [named (filter :artist/name (pa/artists-for-layers (:layers portrait)))
        linked (filter :artist/link named)]
    (if (empty? linked)
      credit
      [:<>
       "Art: "
       (interpose
        ", "
        (for [{:keys [:artist/id :artist/name :artist/link]} named]
          ^{:key id}
          (if link
            [:a.pl-thumb-credit-link
             {:href link :target "_blank" :rel "noopener"
              :on-click #(.stopPropagation %)}
             name]
            [:span name])))])))

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
       [:<>
        [:span.pl-attribution-label "Art by"]
        (for [{:keys [:artist/id :artist/name :artist/link :artist/links]} named]
          ^{:key id}
          [:span.pl-artist
           [:span.pl-artist-swirl "\u00a7"]
           (if link
             [:a {:href link :target "_blank" :rel "noopener"} name]
             [:span.pl-artist-name name])
           ;; The full set lives only here. The baked card and the PDF carry
           ;; the name as text -- a PNG cannot hold a link -- and the character
           ;; page strip fits one. This is the surface with room, and the one
           ;; where someone is looking at the art as it is being made.
           (when (seq links)
             [:span.pl-artist-links
              (for [{:link/keys [label url icon color]} links]
                ^{:key url}
                [:a.pl-artist-link
                 {:href url :target "_blank" :rel "noopener"
                  :title (str name " on " label)
                  ;; the mark's own colour, worn at rest -- the icon is a mask
                  ;; over currentColor, so setting the link's colour paints it
                  :style (when color {:color color})
                  ;; the label is the accessible name either way -- an icon
                  ;; with no text is unreadable to a screen reader otherwise
                  :aria-label (str name " on " label)
                  :class (when icon "has-icon")}
                 (if icon
                   ;; mask rather than <img>, so the mark takes the link's own
                   ;; colour and warms on hover with everything else
                   [:span.pl-artist-icon
                    {:style {:WebkitMaskImage (str "url(/image/social/" icon ".svg)")
                             :maskImage (str "url(/image/social/" icon ".svg)")}}]
                   (s/lower-case label))])])])]

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
         [:div.pl-thumb-credit {:title credit} [linked-credit portrait-data credit]])]

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
