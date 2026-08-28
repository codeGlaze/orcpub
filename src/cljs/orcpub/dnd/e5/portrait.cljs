(ns orcpub.dnd.e5.portrait
  "Paper-doll character-portrait compositor.

   The drawer is opened by dispatching `[:portrait/open]`. It reads
   `::char5e/portrait-layers` off the current character to seed a draft
   in app-db under `:portrait/draft`; every pick / randomize / reset
   mutates the draft. On Save the draft is committed back to
   `::char5e/portrait-layers` and the drawer closes. On Cancel the
   draft is discarded.

   Rendering: layers stack in `portrait-assets/layer-order` as absolute-
   positioned <img>s. Same composite is reused wherever a character's
   portrait is shown — see `composite` below, called from
   `character-summary-2`."
  (:require [re-frame.core :refer [dispatch subscribe]]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.character :as char5e]))

;; Pure helpers live in portrait-assets (cljc) so JVM tests can reach them.
(def random-seed pa/random-seed)
(def compose-for-seed pa/compose-for-seed)

;; ---------------- portrait composite (used by drawer AND summary) ----------------

(defn composite
  "Reagent component: stacked-layer portrait. `layers` is the same shape as
   `::char5e/portrait-layers` — `{layer-key {:artist/id … :asset/id …}}`.
   Renders inside whatever container the caller provides — sizing/framing
   is up to the caller (inline width/height or CSS class).

   `attrs` (optional) merges into the outer <div>'s props so callers can
   set className, style, etc."
  ([layers] (composite layers nil))
  ([layers attrs]
   [:div.portrait-composite
    (merge {:style {:position "relative" :width "100%" :height "100%"}}
           attrs)
    (map-indexed
      (fn [z layer-key]
        (when-let [asset (some->> (get layers layer-key)
                                  :asset/id
                                  (pa/asset-by-id layer-key))]
          ^{:key layer-key}
          [:img.portrait-layer
           {:src (:asset/url asset)
            :style {:position "absolute" :inset 0
                    :width "100%" :height "100%"
                    :object-fit "contain"
                    :z-index z
                    :pointer-events "none"}}]))
      pa/layer-order)]))

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
  width: 560px; max-width: 100vw;
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
  display: grid; grid-template-columns: 240px 1fr;
}
@media (max-width: 700px) {
  .pl-drawer { width: 100vw; }
  .pl-drawer-body { grid-template-columns: 1fr; grid-template-rows: auto 1fr; }
  .pl-canvas-side { border-right: none !important; border-bottom: 1px solid rgba(255,255,255,0.06); }
  .pl-portrait-frame { width: 200px !important; height: 260px !important; }
}

.pl-canvas-side {
  background: #0e131a;
  border-right: 1px solid rgba(255,255,255,0.06);
  padding: 16px;
  display: flex; flex-direction: column; gap: 10px;
}
.pl-portrait-frame {
  width: 208px; height: 260px;
  background: radial-gradient(circle at 50% 35%, #202939, #131924 60%, #0f141c);
  border: 1px solid rgba(240, 161, 0, 0.16); border-radius: 10px;
  align-self: center; position: relative; overflow: hidden;
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
.pl-btn-ghost:hover { border-color: #f0a100; color: #ffcc5e; }
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

.pl-pickers-side {
  overflow-y: auto; padding: 12px 14px;
  display: flex; flex-direction: column; gap: 8px;
  background: #131924;
}
.pl-picker {
  background: #171e29; border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px; padding: 10px 12px;
}
.pl-picker-head {
  display: flex; align-items: baseline; justify-content: space-between; gap: 8px;
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
.pl-sw img {
  width: 100%; height: 100%;
  object-fit: contain;
  pointer-events: none;
}
.pl-sw-none {
  background: repeating-linear-gradient(45deg, #131924 0 5px, transparent 5px 10px);
  color: #616a7a; font: italic 10px/1 Georgia, serif;
}
.pl-empty-registry {
  padding: 8px; color: #616a7a;
  font: italic 11px/1.4 Georgia, serif; text-align: center;
}

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
.pl-attribution-empty {
  color: #616a7a; font-style: italic; font-family: Georgia, serif;
}
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

(defn- category-picker [layer-key]
  (let [draft @(subscribe [:portrait/draft])
        selected (get draft layer-key)
        selected-asset (when selected (pa/asset-by-id layer-key (:asset/id selected)))
        assets (pa/assets-for-layer layer-key)
        z (.indexOf pa/layer-order layer-key)]
    [:div.pl-picker
     [:div.pl-picker-head
      [:span.pl-cat
       [:span.pl-cat-chip {:style {:background (pa/layer-colors layer-key)}}]
       (pa/layer-labels layer-key)
       [:span.pl-cat-z (str "z" (when (< z 10) "0") z)]]
      (when selected
        [:span.pl-picker-sel (or (:asset/label selected-asset) "picked")])]
     (if (empty? assets)
       [:div.pl-empty-registry "no art here yet"]
       [:div.pl-swatches
        [:button.pl-sw.pl-sw-none
         {:class (when-not selected "selected")
          :on-click #(dispatch [:portrait/pick-layer layer-key nil])
          :title "None"}
         "none"]
        (for [asset assets
              :let [asset-id (:asset/id asset)
                    is-selected? (= asset-id (:asset/id selected))]]
          ^{:key asset-id}
          [:button.pl-sw
           {:class (when is-selected? "selected")
            :on-click #(dispatch [:portrait/pick-layer layer-key asset-id])
            :title (or (:asset/label asset) (name asset-id))}
           [:img {:src (:asset/url asset)
                  :alt (or (:asset/label asset) "")}]])])]))

(defn- attribution [draft]
  (let [artist-ids (pa/all-artists-for-layers draft)]
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

(defn drawer
  "Renders the compositor drawer when `:portrait/drawer-open?` is truthy.
   Mount once at the character-builder root; the drawer overlays."
  []
  (when @(subscribe [:portrait/drawer-open?])
    (let [draft @(subscribe [:portrait/draft])
          seed  @(subscribe [:portrait/draft-seed])
          any?  (seq draft)]
      [:div
       [:style drawer-styles]
       [:div.pl-backdrop
        {:on-click #(dispatch [:portrait/close])}]
       [:div.pl-drawer
        [:div.pl-drawer-head
         [:div.pl-drawer-title
          [:span.pl-drawer-title-rune "§"] "Compose portrait"]
         [:button.pl-drawer-close
          {:on-click #(dispatch [:portrait/close])
           :aria-label "Close portrait compositor"}
          "✕"]]
        [:div.pl-drawer-body
         [:div.pl-canvas-side
          [:div.pl-portrait-frame
           (if any?
             [composite draft]
             [:div.pl-empty-hint
              "Pick a layer below, or hit " [:em "Randomize"] "."])]
          [:div.pl-toolbar
           [:button.pl-btn.pl-btn-primary
            {:on-click #(dispatch [:portrait/randomize])}
            "🎲 Randomize"]
           [:button.pl-btn.pl-btn-ghost
            {:on-click #(dispatch [:portrait/reset])
             :disabled (not any?)}
            "Reset"]
           (when seed
             [:div.pl-seed-row
              [:span "seed"]
              [:code seed]])]]
         [:div.pl-pickers-side
          (for [layer-key pa/layer-order]
            ^{:key layer-key}
            [category-picker layer-key])]]
        [:div.pl-drawer-foot
         [attribution draft]
         [:div.pl-drawer-actions
          [:button.pl-btn.pl-btn-ghost
           {:on-click #(dispatch [:portrait/close])}
           "Cancel"]
          [:button.pl-btn.pl-btn-primary
           {:on-click #(dispatch [:portrait/save])}
           "Save portrait"]]]]])))

(defn launcher-button
  "The 'Compose portrait' button that slots next to the Image URL input
   in the character-builder Description tab."
  []
  [:button.pl-launcher
   {:type "button"
    :on-click #(dispatch [:portrait/open])
    :title "Compose portrait from layered art"}
   "Compose portrait"])
