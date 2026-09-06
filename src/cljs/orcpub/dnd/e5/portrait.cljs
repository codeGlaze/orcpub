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
            [clojure.string :as s]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(defn- classes [& cs] (s/join " " (remove nil? cs)))

(defn- target-value [e] (.. e -target -value))

(defn- z-label [layer-key]
  (let [z (.indexOf pa/layer-order layer-key)]
    (str "z" (when (< z 10) "0") z)))

;; ---------------- composite (used by drawer AND summary) ----------------

(defn- mask-style [url tint z]
  {:position "absolute" :inset 0 :width "100%" :height "100%"
   :background-color tint
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
             {:style (mask-style (:asset/url asset) (pa/tint-for portrait layer-key) z)}]))
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
      (-> (js/Promise.all (clj->js (map (fn [[_ a]] (load-image (:asset/url a))) selected)))
          (.then
            (fn [imgs]
              (try
                (let [canvas (.createElement js/document "canvas")
                      _ (set! (.-width canvas) raster-width)
                      _ (set! (.-height canvas) raster-height)
                      ctx (.getContext canvas "2d")
                      tmp (.createElement js/document "canvas")
                      _ (set! (.-width tmp) raster-width)
                      _ (set! (.-height tmp) raster-height)
                      tctx (.getContext tmp "2d")]
                  (doseq [[[layer-key _] img] (map vector selected (array-seq imgs))
                          :when img]
                    (.clearRect tctx 0 0 raster-width raster-height)
                    (set! (.-globalCompositeOperation tctx) "source-over")
                    (.drawImage tctx img 0 0 raster-width raster-height)
                    ;; paint the tint through the asset's alpha
                    (set! (.-globalCompositeOperation tctx) "source-in")
                    (set! (.-fillStyle tctx) (pa/tint-for portrait layer-key))
                    (.fillRect tctx 0 0 raster-width raster-height)
                    (.drawImage ctx tmp 0 0))
                  (some-> (.toDataURL canvas "image/png")
                          (s/split #",")
                          second))
                ;; Never let a failed bake block the export -- the sheet is
                ;; worth more than the picture.
                (catch :default e
                  (js/console.warn "portrait rasterize failed" e)
                  nil))))
          (.catch (fn [_] nil))))))

;; ---------------- drawer chrome ----------------

(def drawer-styles "
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
.pl-drawer-title { font: 400 18px/1 Georgia, serif; }
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
  width: 208px; height: 260px;
  background: radial-gradient(circle at 50% 35%, #202939, #131924 60%, #0f141c);
  border: 1px solid rgba(240, 161, 0, 0.16); border-radius: 10px;
  align-self: center; position: relative; overflow: hidden;
  flex-shrink: 0;
}
.pl-empty-hint {
  position: absolute; inset: 0;
  display: grid; place-content: center;
  color: #616a7a; font: italic 12px/1.5 Georgia, serif;
  text-align: center; padding: 10px;
}
.pl-toolbar { display: flex; flex-direction: column; gap: 8px; }
.pl-btn {
  display: inline-flex; align-items: center; justify-content: center;
  gap: 7px; padding: 10px 14px; border-radius: 8px;
  border: 1px solid transparent; background: transparent;
  color: #ebeef4; font: 500 13px/1 inherit;
  cursor: pointer; touch-action: manipulation;
  min-height: 44px;
}
.pl-btn:focus-visible { outline: 2px solid #ffcc5e; outline-offset: 2px; }
.pl-btn-primary {
  background: linear-gradient(to bottom, #f0a100, #d38a00);
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
  font: italic 12px/1 Georgia, serif; color: #8b95a5;
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
  color: #616a7a; font: italic 10px/1 Georgia, serif;
}
.pl-empty-registry {
  padding: 8px; color: #616a7a;
  font: italic 11px/1.4 Georgia, serif; text-align: center;
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
  display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 12px;
  font-size: 12px;
}
.pl-attribution-label {
  font: 700 10px/1 inherit; letter-spacing: 0.18em; text-transform: uppercase;
  color: #616a7a;
}
.pl-attribution-empty { color: #616a7a; font-style: italic; font-family: Georgia, serif; }
.pl-artist { display: inline-flex; align-items: baseline; gap: 5px; }
.pl-artist-swirl { color: #f0a100; font-family: Georgia, serif; font-style: italic; }
.pl-artist a, .pl-artist span.pl-artist-name {
  color: #ebeef4; text-decoration: none;
  border-bottom: 1px dotted rgba(240,161,0,0.32);
  padding-bottom: 1px;
  font: italic 13px/1 Georgia, serif;
}
.pl-artist a:hover { color: #ffcc5e; border-bottom-color: #f0a100; }
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
.pl-root.light-theme .pl-artist-swirl { color: #33658A; }
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
.pl-root.light-theme .pl-artist a,
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
  background: linear-gradient(to bottom, #f0a100, #d38a00);
  color: #15202e;
  font: 700 13px/1 'Open Sans', system-ui, sans-serif;
  border: 1px solid #b57500;
  box-shadow: 0 6px 14px -8px rgba(240,161,0,0.32);
  cursor: pointer;
  margin-top: 5px;
}
.pl-launcher:hover { filter: brightness(1.06); }
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
  (let [pieces (pa/layers-in-slot slot)]
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
   (for [slot pa/color-slot-order]
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
        assets         (pa/assets-for-layer layer-key)]
    [:div.pl-picker
     [:div.pl-picker-head
      [:span.pl-cat
       [:span.pl-cat-chip {:style {:background (pa/layer-colors layer-key)}}]
       (pa/layer-labels layer-key)
       [:span.pl-cat-z (z-label layer-key)]
       [layer-tint-chip portrait layer-key open-layer]]
      (when selected
        [:span.pl-picker-sel (or (:asset/label selected-asset) "picked")])]
     (if (empty? assets)
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
                  :alt (or (:asset/label asset) "")}]])])
     (when (= open-layer layer-key)
       [:div.pl-layer-panel [sub-row portrait layer-key]])]))

(defn- attribution [layers]
  (let [artist-ids (pa/all-artists-for-layers layers)]
    [:div.pl-attribution
     [:span.pl-attribution-label "Art by"]
     (if (seq artist-ids)
       (for [aid artist-ids
             :let [info (pa/artist-info aid)]]
         ^{:key aid}
         [:span.pl-artist
          [:span.pl-artist-swirl "§"]
          (if (:artist/link info)
            [:a {:href (:artist/link info) :target "_blank" :rel "noopener"}
             (:artist/name info)]
            [:span.pl-artist-name (:artist/name info)])])
       [:span.pl-attribution-empty "no layers selected yet"])]))

;; ---------------- drawer ----------------

(defn drawer
  "Renders the compositor drawer when :portrait/drawer-open? is truthy.
   Mount once at the character-builder root; it overlays.

   The stylesheet is mounted unconditionally, not inside the open? branch --
   it also styles the launcher button, which lives in the Description tab and
   is on screen precisely when the drawer is not.

   The theme class goes on this component's own root rather than being
   inherited: the drawer is mounted as a SIBLING of content-page, and .app --
   which carries the theme -- is inside content-page, so .app.light-theme
   cannot reach it."
  []
  (let [open? @(subscribe [:portrait/drawer-open?])
        theme @(subscribe [:theme])]
    [:div.pl-root {:class theme}
     [:style drawer-styles]
     (when open?
       (let [portrait   @(subscribe [:portrait/draft])
             seed       @(subscribe [:portrait/draft-seed])
             open-slot  @(subscribe [:portrait/open-slot])
             open-layer @(subscribe [:portrait/open-layer])
             layers     (:layers portrait)
             any?       (boolean (seq layers))]
         [:div
          [:div.pl-backdrop {:on-click #(dispatch [:portrait/close])}]
       [:div.pl-drawer
        [:div.pl-drawer-head
         [:div.pl-drawer-title
          [:span.pl-drawer-title-rune "§"] "Compose portrait"]
         [:button.pl-drawer-close
          {:type "button"
           :on-click #(dispatch [:portrait/close])
           :aria-label "Close portrait compositor"}
          "✕"]]
        [:div.pl-drawer-body
         [:div.pl-canvas-side
          [:div.pl-portrait-frame
           (if any?
             [composite portrait]
             [:div.pl-empty-hint
              "Pick a layer below, or hit " [:em "Randomize"] "."])]
          [:div.pl-toolbar
           [:button.pl-btn.pl-btn-primary
            {:type "button" :on-click #(dispatch [:portrait/randomize])}
            "🎲 Randomize"]
           [:button.pl-btn.pl-btn-ghost
            {:type "button" :on-click #(dispatch [:portrait/reset]) :disabled (not any?)}
            "Reset"]
           (when seed
             [:div.pl-seed-row [:span "seed"] [:code seed]])]
          [color-strip portrait open-slot]]
         [:div.pl-pickers-side
          (for [layer-key pa/layer-order]
            ^{:key layer-key}
            [category-picker portrait layer-key open-layer])]]
        [:div.pl-drawer-foot
         [attribution layers]
         [:div.pl-drawer-actions
          [:button.pl-btn.pl-btn-ghost
           {:type "button" :on-click #(dispatch [:portrait/close])}
           "Cancel"]
          [:button.pl-btn.pl-btn-primary
           {:type "button" :on-click #(dispatch [:portrait/save])}
           "Save portrait"]]]]]))]))

(defn launcher-button
  "The 'Compose portrait' button that slots next to the Image URL input in
   the character-builder Description tab."
  []
  [:button.pl-launcher
   {:type "button"
    :on-click #(dispatch [:portrait/open])
    :title "Compose portrait from layered art"}
   "Compose portrait"])
