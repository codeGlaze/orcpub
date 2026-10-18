(ns orcpub.styles.portrait
  "The portrait compositor: the drawer, the builder tab, the summary thumbnail and the artist
   credit, all under .pl-, .lk- and .portrait- classes. Values that come from data (stacking order,
   art, tints, swatch and link colours, icons, the site logo) are CSS variables set on the element.
   docs/PORTRAIT-STYLES.md gives the reasons behind the less obvious rules.
   GOTCHA: a rule over eight properties is split into several maps, because a larger map literal
   is a hash map and would not keep the declarations in order."
  (:require [garden.stylesheet :refer [at-media at-keyframes at-font-face]]))

(def drawer
  "The drawer, the builder tab and the credit."
  [
   ;; The baked credit is drawn on a canvas, which can use only faces the page has loaded: Vollkorn,
   ;; the PDF's own face, as a 26 KB Latin-1 subset with the full TTF behind it. Fetched only when
   ;; an export draws a credit. docs/PORTRAIT-STYLES.md has the command that cuts the subset.
   (at-font-face {:font-family "'Vollkorn'"
                  :src "url('/fonts/Vollkorn-Italic-credit.woff2') format('woff2'), url('/fonts/Vollkorn-Italic.ttf') format('truetype')"
                  :font-style "italic"
                  :font-weight "400"
                  :font-display "swap"})
   [".pl-backdrop" {:position "fixed"
                    :inset "0"
                    :background "rgba(0, 0, 0, 0.55)"
                    :z-index "9998"
                    :animation "pl-fade 160ms ease-out"}]
   [".pl-drawer" {:position "fixed"
                  :top "0"
                  :right "0"
                  :bottom "0"
                  :width "600px"
                  :max-width "100vw"
                  :background "#131924"
                  :border-left "1px solid rgba(240, 161, 0, 0.16)"}
                 {:box-shadow "-30px 0 60px -20px rgba(0, 0, 0, 0.6)"
                  :display "flex"
                  :flex-direction "column"
                  :z-index "9999"
                  :animation "pl-slide 220ms cubic-bezier(.22, .8, .36, 1)"
                  :font-family "'Open Sans', system-ui, sans-serif"
                  :color "#ebeef4"}]
   (at-keyframes "pl-fade"
     ["from" {:opacity "0"}]
     ["to" {:opacity "1"}])
   (at-keyframes "pl-slide"
     ["from" {:transform "translateX(32px)"
 :opacity "0.6"}]
     ["to" {:transform "none"
 :opacity "1"}])
   [".pl-drawer-head" {:display "flex"
                       :align-items "center"
                       :justify-content "space-between"
                       :padding "14px 18px"
                       :border-bottom "1px solid rgba(255,255,255,0.06)"
                       :background "#0e131a"
                       :flex-shrink "0"}]
   [".pl-drawer-title" {:font "600 18px/1 'Open Sans', system-ui, sans-serif"}]
   [".pl-drawer-title-rune" {:color "#f0a100"
                             :font-style "italic"
                             :padding-right "5px"}]
   [".pl-drawer-close" {:display "grid"
                        :place-items "center"
                        :width "36px"
                        :height "36px"
                        :border-radius "8px"
                        :background "transparent"
                        :border "1px solid rgba(255,255,255,0.06)"
                        :color "#8b95a5"}
                       {:cursor "pointer"
                        :font-size "16px"
                        :line-height "1"}]
   [".pl-drawer-close:hover" {:color "#ffcc5e"
                              :border-color "#f0a100"}]
   [".pl-drawer-body" {:flex "1"
                       :overflow "hidden"
                       :display "grid"
                       :grid-template-columns "280px 1fr"}]
   [".pl-canvas-side" {:background "#0e131a"
                       :border-right "1px solid rgba(255,255,255,0.06)"
                       :padding "16px"
                       :display "flex"
                       :flex-direction "column"
                       :gap "10px"
                       :overflow-y "auto"}]
   [".pl-pickers-side" {:overflow-y "auto"
                        :padding "12px 14px"
                        :display "flex"
                        :flex-direction "column"
                        :gap "8px"
                        :background "#131924"}]
   (at-media {:max-width "700px"}
     [".pl-drawer" {:width "100vw"}]
     [".pl-drawer-body" {:display "block"
                         :overflow-y "auto"}]
     [".pl-canvas-side" {:border-right "none"
                         :border-bottom "1px solid rgba(255,255,255,0.06)"
                         :overflow "visible"}]
     [".pl-pickers-side" {:overflow "visible"}]
     [".pl-portrait-frame" {:width "200px !important"
                            :height "260px !important"}])
   ;; Full column width, like every button and row under it; the 4:5 ratio sets the height.
   [".pl-portrait-frame" {:width "100%"
                          :aspect-ratio "4 / 5"
                          :box-sizing "border-box"
                          :background "radial-gradient(circle at 50% 35%, #202939, #131924 60%, #0f141c)"
                          :border "1px solid rgba(240, 161, 0, 0.16)"
                          :border-radius "10px"
                          :align-self "stretch"
                          :position "relative"}
                         {:overflow "hidden"
                          :flex-shrink "0"}]
   [".pl-empty-hint" {:position "absolute"
                      :inset "0"
                      :display "grid"
                      :place-content "center"
                      :color "#616a7a"
                      :font "italic 12px/1.5 'Open Sans', system-ui, sans-serif"
                      :text-align "center"
                      :padding "10px"}]
   [".pl-toolbar" {:display "flex"
                   :flex-direction "column"
                   :gap "8px"}]
   ;; Matched to the app's .form-button (styles/core.clj): 5px radius, uppercase, 600/12px.
   [".pl-btn" {:display "inline-flex"
               :align-items "center"
               :justify-content "center"
               :gap "7px"
               :padding "10px 15px"
               :border-radius "5px"
               :border "1px solid transparent"
               :background "transparent"}
              {:color "#ebeef4"
               :font "600 12px/1 inherit"
               :text-transform "uppercase"
               :letter-spacing "0.04em"
               :cursor "pointer"
               :touch-action "manipulation"
               :min-height "44px"}]
   [".pl-btn:focus-visible" {:outline "2px solid #ffcc5e"
                             :outline-offset "2px"}]
   [".pl-btn-primary" {:background "linear-gradient(to bottom, #f1a20f, #dbab50)"
                       :color "#15202e"
                       :font-weight "700"
                       :border-color "#b57500"
                       :box-shadow "0 6px 14px -8px rgba(240,161,0,0.32)"}]
   [".pl-btn-primary:hover" {:filter "brightness(1.06)"}]
   [".pl-btn-primary:disabled" {:opacity "0.5"
                                :cursor "not-allowed"}]
   [".pl-btn-ghost" {:border-color "rgba(255,255,255,0.10)"
                     :color "#8b95a5"}]
   [".pl-btn-ghost:hover:not(:disabled)" {:border-color "#f0a100"
                                          :color "#ffcc5e"}]
   [".pl-btn-ghost:disabled" {:opacity "0.4"
                              :cursor "not-allowed"}]
   [".pl-ombre" {:display "flex"
                 :flex-direction "column"
                 :gap "6px"
                 :margin-top "10px"}]
   [".pl-ombre-row" {:display "grid"
                     :grid-template-columns "76px 1fr 40px"
                     :gap "6px"
                     :align-items "center"}]
   [".pl-ombre-row input[type=range]" {:width "100%"
                                       :accent-color "#f0a100"
                                       :margin "0"}]
   [".pl-ombre-row select" {:font "500 11px/1 inherit"
                            :background "#131924"
                            :color "#c9d0da"
                            :border "1px solid rgba(255,255,255,0.14)"
                            :border-radius "4px"
                            :padding "3px 4px"
                            :grid-column "2 / 4"}]
   [".pl-ombre-label" {:font "500 11px/1 inherit"
                       :color "#8b95a5"}]
   [".pl-light" {:margin-top "12px"}]
   [".pl-segmented" {:grid-column "2 / 4"
                     :display "flex"
                     :border "1px solid rgba(255,255,255,0.14)"
                     :border-radius "4px"
                     :overflow "hidden"}]
   [".pl-segmented button" {:flex "1"
                            :white-space "nowrap"
                            :font "500 11px/1 inherit"
                            :padding "5px 4px"
                            :background "#131924"
                            :color "#c9d0da"
                            :border "0"
                            :cursor "pointer"}]
   [".pl-segmented button + button" {:border-left "1px solid rgba(255,255,255,0.14)"}]
   [".pl-segmented button.on" {:background "#f0a100"
                               :color "#131924"}]
   [".pl-root.light-theme .pl-segmented" {:border-color "rgba(0,0,0,0.16)"}]
   [".pl-root.light-theme .pl-segmented button" {:background "#fff"
                                                 :color "#363636"}]
   [".pl-root.light-theme .pl-segmented button + button" {:border-left-color "rgba(0,0,0,0.16)"}]
   [".pl-root.light-theme .pl-segmented button.on" {:background "#33658A"
                                                    :color "#fff"}]
   [".pl-preset.on" {:box-shadow "0 0 0 2px #f0a100"}]
   [".pl-preset-none" {:background "transparent"
                       :color "#8b95a5"
                       :font "600 12px/1 inherit"
                       :border "1px dashed rgba(255,255,255,0.3)"}]
   [".pl-root.light-theme .pl-ombre-row select" {:background "#fff"
                                                 :color "#363636"
                                                 :border-color "rgba(0,0,0,0.16)"}]
   [".pl-root.light-theme .pl-ombre-row input[type=range]" {:accent-color "#33658A"}]
   [".pl-dev-switch" {:display "flex"
                      :align-items "center"
                      :gap "6px"
                      :justify-content "center"
                      :margin "8px auto 0"
                      :padding "5px 8px"
                      :width "fit-content"
                      :border "1px dashed rgba(240,161,0,0.45)"}
                     {:border-radius "6px"
                      :font "500 11px/1 inherit"
                      :color "#8b95a5"}]
   [".pl-dev-switch-label" {:text-transform "uppercase"
                            :letter-spacing "0.08em"
                            :font-size "10px"
                            :margin-right "2px"}]
   [".pl-dev-switch button" {:font "600 11px/1 inherit"
                             :color "#c9d0da"
                             :background "transparent"
                             :cursor "pointer"
                             :border "1px solid rgba(255,255,255,0.14)"
                             :border-radius "4px"
                             :padding "4px 8px"}]
   [".pl-dev-switch button[aria-pressed=true]" {:background "rgba(240,161,0,0.18)"
                                                :border-color "#f0a100"
                                                :color "#ffcc5e"}]
   [".pl-dev-switch button:focus-visible" {:outline "2px solid #ffcc5e"
                                           :outline-offset "1px"}]
   [".pl-root.light-theme .pl-dev-switch button" {:color "#363636"
                                                  :border-color "rgba(0,0,0,0.16)"}]
   [".pl-root.light-theme .pl-dev-switch button[aria-pressed=true]" {:background "rgba(51,101,138,0.14)"
                                                                     :border-color "#33658A"
                                                                     :color "#2b5677"}]
   [".pl-seed-row" {:display "flex"
                    :align-items "center"
                    :justify-content "space-between"
                    :color "#616a7a"
                    :font "500 11px/1 inherit"
                    :letter-spacing "0.06em"
                    :text-transform "uppercase"
                    :margin-top "4px"}]
   [".pl-seed-row code" {:font "500 12px/1 ui-monospace, Menlo, monospace"
                         :color "#ffcc5e"
                         :background "rgba(240,161,0,0.08)"
                         :border "1px solid rgba(240,161,0,0.16)"
                         :padding "3px 7px"
                         :border-radius "4px"
                         :text-transform "none"
                         :letter-spacing "0.02em"}]
   ;; ---- character colors ----
   [".pl-color-strip" {:display "flex"
                       :align-items "center"
                       :gap "6px"
                       :flex-wrap "wrap"
                       :padding "8px 10px"
                       :background "#171e29"
                       :border "1px solid rgba(255,255,255,0.06)"
                       :border-radius "8px"}]
   [".pl-strip-label, .pl-panel-heading" {:font "700 9.5px/1 inherit"
                                          :color "#616a7a"
                                          :letter-spacing "0.14em"
                                          :text-transform "uppercase"}]
   [".pl-strip-label" {:margin-right "4px"}]
   [".pl-slot" {:display "inline-flex"
                :align-items "center"
                :background "#131924"
                :border "1px solid rgba(255,255,255,0.06)"
                :border-radius "999px"
                :padding "3px 3px 3px 10px"
                :gap "6px"}]
   [".pl-slot.on" {:border-color "#f0a100"
                   :box-shadow "0 0 0 1px rgba(240,161,0,0.24)"}]
   [".pl-slot.has-tweaks .pl-slot-swatch" {:box-shadow "0 0 0 2px rgba(240,161,0,0.42)"}]
   [".pl-slot::after" {:content "''"
                       :display "inline-block"
                       :width "6px"
                       :height "6px"
                       :border-right "1.5px solid #616a7a"
                       :border-bottom "1.5px solid #616a7a"
                       :transform "rotate(45deg) translateY(-1px)"
                       :margin "0 6px 0 -2px"}
                      {:transition "transform 120ms, border-color 120ms"}]
   [".pl-slot.open::after" {:transform "rotate(-135deg)"
                            :border-color "#ffcc5e"}]
   [".pl-slot-name" {:font "500 11px/1 inherit"
                     :color "#ebeef4"}]
   [".pl-slot-tweaks" {:display "inline-flex"
                       :align-items "center"
                       :background "rgba(240,161,0,0.16)"
                       :color "#ffcc5e"
                       :font "700 9.5px/1 inherit"
                       :letter-spacing "0.06em"
                       :padding "2px 6px 2px 5px"
                       :border-radius "999px"}
                      {:margin "0 2px 0 -2px"}]
   [".pl-slot-swatch" {:position "relative"
                       :display "inline-block"
                       :width "28px"
                       :height "28px"
                       :border-radius "50%"
                       :border "1px solid rgba(255,255,255,0.15)"
                       :cursor "pointer"
                       :padding "0"}
                      {:background-clip "padding-box"}]
   [".pl-slot-swatch.unset" {:background "repeating-linear-gradient(45deg, #0e131a 0 4px, rgba(255,255,255,0.05) 4px 8px)"}]
   [".pl-slot-swatch:focus-visible" {:outline "2px solid #ffcc5e"
                                     :outline-offset "2px"}]
   [".pl-sub-dots" {:position "absolute"
                    :inset "0"
                    :pointer-events "none"}]
   [".pl-sub-dot" {:position "absolute"
                   :width "10px"
                   :height "10px"
                   :border-radius "50%"
                   :border "1.5px solid #171e29"
                   :box-shadow "0 0 0 0.5px rgba(0,0,0,0.35)"}]
   [".pl-sub-dot-0" {:top "-4px"
                     :left "50%"
                     :transform "translateX(-50%)"}]
   [".pl-sub-dot-1" {:top "50%"
                     :right "-4px"
                     :transform "translateY(-50%)"}]
   [".pl-sub-dot-2" {:bottom "-4px"
                     :left "50%"
                     :transform "translateX(-50%)"}]
   [".pl-sub-dot-3" {:top "50%"
                     :left "-4px"
                     :transform "translateY(-50%)"}]
   [".pl-slot-clear, .pl-sub-clear" {:display "inline-grid"
                                     :place-items "center"
                                     :width "22px"
                                     :height "22px"
                                     :border-radius "50%"
                                     :background "transparent"
                                     :border "1px solid transparent"
                                     :color "#616a7a"}
                                    {:cursor "pointer"
                                     :padding "0"
                                     :font-size "12px"
                                     :line-height "1"}]
   [".pl-slot-clear:hover:not(:disabled), .pl-sub-clear:hover:not(:disabled)" {:color "#ffcc5e"
                                                                               :border-color "rgba(240,161,0,0.16)"}]
   [".pl-slot-clear:disabled, .pl-sub-clear:disabled" {:opacity "0.25"
                                                       :cursor "default"}]
   [".pl-slot-panel" {:flex-basis "100%"
                      :min-width "100%"
                      :display "flex"
                      :flex-direction "column"
                      :gap "10px"
                      :padding "10px"
                      :margin-top "-2px"
                      :background "#1e2635"}
                     {:border "1px solid rgba(255,255,255,0.06)"
                      :border-radius "8px"}]
   [".pl-panel-heading" {:margin-bottom "6px"
                         :display "block"}]
   [".pl-presets" {:display "flex"
                   :flex-wrap "wrap"
                   :gap "5px"}]
   [".pl-preset" {:width "26px"
                  :height "26px"
                  :border-radius "50%"
                  :border "1px solid rgba(255,255,255,0.12)"
                  :cursor "pointer"
                  :padding "0"
                  :position "relative"
                  :overflow "hidden"}]
   [".pl-preset:focus-visible" {:outline "2px solid #ffcc5e"
                                :outline-offset "2px"}]
   [".pl-preset.custom" {:background "conic-gradient(#f0a100, #78d0d4, #c85c5c, #6fbb5a, #7a94b8, #f0a100)"}]
   [".pl-preset.custom input, .pl-sub-chip input" {:position "absolute"
                                                   :inset "0"
                                                   :width "100%"
                                                   :height "100%"
                                                   :opacity "0"
                                                   :padding "0"
                                                   :border "none"
                                                   :cursor "pointer"}]
   [".pl-sublayers" {:display "flex"
                     :flex-direction "column"
                     :gap "8px"
                     :padding-top "8px"
                     :border-top "1px dashed rgba(255,255,255,0.06)"}]
   [".pl-sub-row" {:display "grid"
                   :grid-template-columns "22px 1fr 40px 22px"
                   :gap "6px"
                   :align-items "center"}]
   [".pl-sub-name" {:grid-column "1 / -1"
                    :font "500 11px/1 inherit"
                    :color "#ebeef4"}]
   [".pl-sub-chip" {:position "relative"
                    :display "inline-block"
                    :width "22px"
                    :height "22px"
                    :border-radius "50%"
                    :border "1px solid rgba(255,255,255,0.15)"
                    :cursor "pointer"
                    :overflow "hidden"}]
   [".pl-sub-chip.shaded" {:border-color "#f0a100"
                           :border-style "dashed"}]
   [".pl-sub-chip.overridden" {:border-color "#f0a100"
                               :box-shadow "0 0 0 1px rgba(240,161,0,0.4)"}]
   [".pl-sub-row input[type=range]" {:width "100%"
                                     :accent-color "#f0a100"
                                     :height "22px"
                                     :margin "0"}]
   [".pl-sub-depth-icon" {:font-size "13px"
                          :line-height "1"
                          :color "#8b95a5"
                          :text-align "center"}]
   [".pl-sub-shade-val" {:font "500 10px/1 ui-monospace, Menlo, monospace"
                         :color "#8b95a5"
                         :text-align "right"
                         :font-variant-numeric "tabular-nums"}]
   [".pl-layer-panel" {:margin-top "10px"
                       :padding "10px 12px"
                       :background "#1e2635"
                       :border "1px solid rgba(240,161,0,0.16)"
                       :border-radius "6px"}]
   ;; ---- pickers ----
   [".pl-picker" {:background "#171e29"
                  :border "1px solid rgba(255,255,255,0.06)"
                  :border-radius "8px"
                  :padding "10px 12px"}]
   [".pl-picker-head" {:display "flex"
                       :align-items "center"
                       :justify-content "space-between"
                       :gap "8px"
                       :margin-bottom "8px"
                       :position "sticky"
                       :top "-1px"
                       :background "#171e29"}
                      {:z-index "2"}]
   [".pl-cat" {:display "inline-flex"
               :align-items "center"
               :gap "6px"
               :font "700 12px/1 inherit"
               :color "#ebeef4"}]
   [".pl-cat-chip" {:width "10px"
                    :height "10px"
                    :border-radius "2.5px"
                    :box-shadow "0 0 0 1px rgba(255,255,255,0.08)"}]
   [".pl-cat-z" {:font "500 9.5px/1 ui-monospace, Menlo, monospace"
                 :color "#616a7a"
                 :padding "2px 5px"
                 :background "#131924"
                 :border-radius "3px"
                 :letter-spacing "0.04em"}]
   [".pl-tint-chip" {:width "18px"
                     :height "18px"
                     :border-radius "50%"
                     :border "1.5px solid transparent"
                     :cursor "pointer"
                     :padding "0"
                     :margin-left "4px"
                     :flex-shrink "0"}]
   [".pl-tint-chip:focus-visible" {:outline "2px solid #ffcc5e"
                                   :outline-offset "2px"}]
   [".pl-tint-chip.shaded" {:border-color "#f0a100"
                            :border-style "dashed"}]
   [".pl-tint-chip.overridden" {:border-color "#f0a100"}]
   [".pl-tint-chip.open" {:box-shadow "0 0 0 2px rgba(240,161,0,0.32)"}]
   [".pl-picker-sel" {:font "italic 12px/1 'Open Sans', system-ui, sans-serif"
                      :color "#8b95a5"
                      :overflow "hidden"
                      :white-space "nowrap"
                      :text-overflow "ellipsis"
                      :max-width "180px"}]
   [".pl-swatches" {:display "grid"
                    :grid-template-columns "repeat(auto-fill, minmax(56px, 1fr))"
                    :gap "5px"}]
   [".pl-sw" {:aspect-ratio "1"
              :border-radius "6px"
              :background "#131924"
              :border "1px solid rgba(255,255,255,0.05)"
              :display "grid"
              :place-items "center"
              :overflow "hidden"
              :cursor "pointer"}
             {:padding "0"
              :touch-action "manipulation"}]
   [".pl-sw:focus-visible" {:outline "2px solid #ffcc5e"
                            :outline-offset "2px"}]
   [".pl-sw:hover" {:border-color "#f0a100"}]
   [".pl-sw.selected" {:border-color "#f0a100"
                       :box-shadow "0 0 0 2px rgba(240,161,0,0.32)"
                       :background "#1e2635"}]
   [".pl-sw img" {:width "100%"
                  :height "100%"
                  :object-fit "contain"
                  :pointer-events "none"}]
   [".pl-sw-none" {:background "repeating-linear-gradient(45deg, #131924 0 5px, transparent 5px 10px)"
                   :color "#616a7a"
                   :font "italic 10px/1 'Open Sans', system-ui, sans-serif"}]
   ;; A piece the illustrator has planned but not drawn. Deliberately unlike a
   ;; swatch you can press: dashed, no hover, no pointer.
   [".pl-sw-gap" {:cursor "default"
                  :border "1px dashed rgba(139, 149, 165, 0.32)"
                  :background "repeating-linear-gradient(-45deg, rgba(139, 149, 165, 0.10) 0 3px, rgba(139, 149, 165, 0.02) 3px 6px)"}]
   [".pl-sw-gap:hover" {:border-color "rgba(139, 149, 165, 0.32)"}]
   [".pl-sw-gap-badge" {:padding "1px 5px"
                        :border-radius "3px"
                        :background "rgba(139, 149, 165, 0.16)"
                        :border "1px solid rgba(139, 149, 165, 0.4)"
                        :color "#8b95a5"
                        :font "700 8px/1 'Open Sans', system-ui, sans-serif"
                        :letter-spacing "0.12em"
                        :text-transform "uppercase"}]
   [".pl-root.light-theme .pl-sw-gap" {:border-color "rgba(54,54,54,0.28)"
                                       :background "repeating-linear-gradient(-45deg, rgba(54,54,54,0.08) 0 3px, rgba(54,54,54,0.02) 3px 6px)"}]
   [".pl-root.light-theme .pl-sw-gap:hover" {:border-color "rgba(54,54,54,0.28)"}]
   [".pl-root.light-theme .pl-sw-gap-badge" {:color "#6b6b6b"}]
   [".pl-empty-registry" {:padding "8px"
                          :color "#616a7a"
                          :font "italic 11px/1.4 'Open Sans', system-ui, sans-serif"
                          :text-align "center"}]
   ;; ---- foot ----
   [".pl-drawer-foot" {:padding "12px 18px 16px"
                       :border-top "1px solid rgba(255,255,255,0.06)"
                       :background "#0e131a"
                       :display "flex"
                       :flex-direction "column"
                       :gap "10px"
                       :flex-shrink "0"}]
   [".pl-attribution" {:display "flex"
                       :flex-direction "column"
                       :align-items "stretch"
                       :gap "7px"
                       :margin "9px 0 3px"
                       :font-size "12px"
                       :text-align "center"}]
   [".pl-credit-preview" {:background "#0e131a"
                          :border-radius "8px"
                          :padding "10px 22px 14px"
                          :max-width "440px"}]
   [".pl-attribution-empty" {:color "#616a7a"
                             :font-style "italic"
                             :font-family "'Vollkorn', Georgia, serif"}]
   ;; The credit: a small label, then each name on a broken rule with its link marks as end caps,
   ;; or centred below when they cannot balance (see credit-mark-layout in portrait.cljs).
   [".pl-root" {:--lk-rule "rgba(240,161,0,0.40)"
                :--lk-name "#ebeef4"
                :--lk-dim "#7b8494"}]
   [".pl-root.light-theme" {:--lk-rule "rgba(51,101,138,0.45)"
                            :--lk-name "#33465c"
                            :--lk-dim "#858c96"}]
   [".lk-row" {:display "flex"
               :align-items "center"
               :justify-content "center"
               :gap "10px"}]
   [".lk-rule" {:flex "1"
                :height "1px"
                :min-width "10px"}]
   [".lk-rule.l" {:background "linear-gradient(90deg, transparent, var(--lk-rule))"}]
   [".lk-rule.r" {:background "linear-gradient(90deg, var(--lk-rule), transparent)"}]
   ;; pastel spectrum for the dark ground; both ends match so the loop is seamless
   [".pl-root" {:--lk-under "rgba(240,161,0,0.45)"
                :--lk-prism "linear-gradient(100deg, #ffd3ef 0%, #d4c2ff 17%, #a9e4ff 33%, #b9ffe0 50%, #fff0b3 67%, #ffc9d9 83%, #ffd3ef 100%)"
                :--lk-halo "drop-shadow(0 0 3px rgba(214,186,255,0.7)) drop-shadow(0 0 9px rgba(140,210,255,0.4))"
                :--lk-halo-hot "drop-shadow(0 0 4px rgba(255,214,245,0.9)) drop-shadow(0 0 12px rgba(160,220,255,0.6))"
                :--lk-under-lit "#d8c6ff"}]
   ;; jewel tones for the light ground, where pastels would wash out
   [".pl-root.light-theme" {:--lk-under "rgba(51,101,138,0.5)"
                            :--lk-prism "linear-gradient(100deg, #b0258a 0%, #6d3fe0 17%, #1f6fd1 33%, #0c9c86 50%, #c77a0a 67%, #c43565 83%, #b0258a 100%)"
                            :--lk-halo "drop-shadow(0 0 3px rgba(150,120,255,0.45)) drop-shadow(0 0 8px rgba(80,170,255,0.3))"
                            :--lk-halo-hot "drop-shadow(0 0 4px rgba(190,110,255,0.6)) drop-shadow(0 0 10px rgba(80,170,255,0.45))"
                            :--lk-under-lit "#6d3fe0"}]
   (at-keyframes "lk-prism"
     ["from" {:background-position "0% 50%"}]
     ["to" {:background-position "300% 50%"}])
   [".lk-name" {:font "italic 14px/1 'Vollkorn', Georgia, serif"
                :color "var(--lk-name)"
                :text-decoration "none"
                :white-space "nowrap"}]
   ;; A name that links reads as one: a thin dotted underline at rest, drawn as
   ;; a text decoration rather than a border so it does not grow the box and
   ;; pull the name off the rule's centre line.
   ["a.lk-name" {:text-decoration "underline dotted var(--lk-under)"
                 :text-decoration-thickness "1px"
                 :text-underline-offset "3px"
                 :transition "filter 240ms ease, text-decoration-color 240ms ease"}]
   ;; ...and when someone reaches for it, the letters turn prismatic under a two-colour halo; nothing
   ;; moves at rest. The halo is a drop-shadow, because a text-shadow shows through clear letters.
   ["a.lk-name:hover, a.lk-name:focus-visible" {:color "transparent"
                                                :background-image "var(--lk-prism)"
                                                :background-size "300% 100%"
                                                :-webkit-background-clip "text"
                                                :background-clip "text"
                                                :animation "lk-prism 3.2s linear infinite"
                                                :filter "var(--lk-halo)"
                                                :text-decoration-color "var(--lk-under-lit)"}
                                               {:outline "none"}]
   ["a.lk-name:active" {:filter "var(--lk-halo-hot)"}]
   ;; Anyone who has asked for less motion gets the spectrum standing still.
   (at-media {:prefers-reduced-motion "reduce"}
     ["a.lk-name" {:transition "none"}]
     ["a.lk-name:hover, a.lk-name:focus-visible" {:animation "none"}])
   [".lk-cap" {:font "600 8.5px/1 'Open Sans', system-ui, sans-serif"
               :letter-spacing "0.16em"
               :text-transform "uppercase"
               :color "var(--lk-dim)"
               :text-align "center"}]
   [".lk-marks" {:display "flex"
                 :justify-content "center"
                 :gap "14px"}]
   [".lk-artist-block" {:display "flex"
                        :flex-direction "column"
                        :gap "7px"}]
   [".lk-with" {:font "italic 12px/1.55 'Vollkorn', Georgia, serif"
                :color "var(--lk-dim)"
                :text-align "center"
                :padding "0 8px"
                :text-wrap "balance"}]
   [".lk-with .lk-name" {:font-size "13px"}]
   [".lk-mark" {:display "inline-flex"
                :color "var(--lk-mark-color, var(--lk-dim))"
                :text-decoration "none"
                :transition "filter 110ms ease, transform 110ms ease"}]
   [".lk-mark:hover" {:transform "translateY(-1px)"
                      :filter "brightness(1.28)"}]
   [".pl-root.light-theme .lk-mark:hover" {:filter "brightness(0.86)"}]
   [".lk-ico" {:display "block"
               :width "12px"
               :height "12px"
               :background-color "currentColor"
               :-webkit-mask-size "contain"
               :mask-size "contain"
               :-webkit-mask-repeat "no-repeat"
               :mask-repeat "no-repeat"}
              {:-webkit-mask-image "var(--lk-icon)"
               :mask-image "var(--lk-icon)"
               :-webkit-mask-position "center"
               :mask-position "center"}]
   [".lk-mark-text" {:font "400 11px/1 'Open Sans', system-ui, sans-serif"}]
   [".pl-drawer-actions" {:display "flex"
                          :justify-content "space-between"
                          :align-items "center"
                          :gap "10px"}]
   ;; ---- light theme ----
   ;; Scoped to .pl-root.light-theme: the drawer is a sibling of content-page, outside .app's theme.
   ;; The app's light colours (#363636 text, #33658A accents). The portrait frame stays dark in both
   ;; themes so pale skin and blonde hair still show.
   [".pl-root.light-theme .pl-drawer" {:background "#f4f4f6"
                                       :border-left "1px solid rgba(0,0,0,0.12)"
                                       :color "#363636"
                                       :box-shadow "-30px 0 60px -24px rgba(0,0,0,0.28)"}]
   [".pl-root.light-theme .pl-backdrop" {:background "rgba(0,0,0,0.32)"}]
   [".pl-root.light-theme .pl-drawer-head, .pl-root.light-theme .pl-drawer-foot" {:background "#fff"
                                                                                  :border-color "rgba(0,0,0,0.10)"}]
   [".pl-root.light-theme .pl-drawer-title-rune" {:color "#33658A"}]
   [".pl-root.light-theme .pl-drawer-close" {:border-color "rgba(0,0,0,0.14)"
                                             :color "#5a5a5a"}]
   [".pl-root.light-theme .pl-drawer-close:hover" {:color "#33658A"
                                                   :border-color "#33658A"}]
   [".pl-root.light-theme .pl-canvas-side" {:background "#fff"
                                            :border-right-color "rgba(0,0,0,0.10)"}]
   [".pl-root.light-theme .pl-pickers-side" {:background "#f4f4f6"}]
   [".pl-root.light-theme .pl-picker, .pl-root.light-theme .pl-picker-head" {:background "#fff"
                                                                             :border-color "rgba(0,0,0,0.10)"}]
   [".pl-root.light-theme .pl-cat, .pl-root.light-theme .pl-slot-name" {:color "#363636"}]
   [".pl-root.light-theme .pl-cat-z, .pl-root.light-theme .pl-sub-shade-val" {:background "#eceef1"
                                                                              :color "#6b6b6b"}]
   [".pl-root.light-theme .pl-picker-sel, .pl-root.light-theme .pl-empty-hint, .pl-root.light-theme .pl-empty-registry, .pl-root.light-theme .pl-attribution-empty, .pl-root.light-theme .pl-strip-label, .pl-root.light-theme .pl-panel-heading" {:color "#6b6b6b"}]
   [".pl-root.light-theme .pl-btn" {:color "#363636"}]
   [".pl-root.light-theme .pl-btn-primary" {:background "linear-gradient(to bottom, #33658A, #2b5677)"
                                            :color "#fff"
                                            :border-color "#24485f"
                                            :box-shadow "0 6px 14px -8px rgba(51,101,138,0.5)"}]
   [".pl-root.light-theme .pl-btn-ghost" {:border-color "rgba(0,0,0,0.16)"
                                          :color "#5a5a5a"}]
   [".pl-root.light-theme .pl-btn-ghost:hover:not(:disabled)" {:border-color "#33658A"
                                                               :color "#33658A"}]
   [".pl-root.light-theme .pl-seed-row" {:color "#6b6b6b"}]
   [".pl-root.light-theme .pl-seed-row code" {:color "#2b5677"
                                              :background "rgba(51,101,138,0.10)"
                                              :border-color "rgba(51,101,138,0.28)"}]
   [".pl-root.light-theme .pl-color-strip, .pl-root.light-theme .pl-slot-panel" {:background "#fff"
                                                                                 :border-color "rgba(0,0,0,0.10)"}]
   [".pl-root.light-theme .pl-slot" {:background "#f4f4f6"
                                     :border-color "rgba(0,0,0,0.10)"}]
   [".pl-root.light-theme .pl-slot.on" {:border-color "#33658A"
                                        :box-shadow "0 0 0 1px rgba(51,101,138,0.28)"}]
   [".pl-root.light-theme .pl-slot.has-tweaks .pl-slot-swatch" {:box-shadow "0 0 0 2px rgba(51,101,138,0.45)"}]
   [".pl-root.light-theme .pl-slot::after" {:border-color "#8a8a8a"}]
   [".pl-root.light-theme .pl-slot.open::after" {:border-color "#33658A"}]
   [".pl-root.light-theme .pl-slot-tweaks" {:background "rgba(51,101,138,0.16)"
                                            :color "#2b5677"}]
   [".pl-root.light-theme .pl-slot-swatch.unset" {:background "repeating-linear-gradient(45deg, #e7e9ec 0 4px, #f7f8fa 4px 8px)"}]
   [".pl-root.light-theme .pl-sub-dot" {:border-color "#fff"}]
   [".pl-root.light-theme .pl-slot-clear, .pl-root.light-theme .pl-sub-clear" {:color "#8a8a8a"}]
   [".pl-root.light-theme .pl-slot-clear:hover:not(:disabled), .pl-root.light-theme .pl-sub-clear:hover:not(:disabled)" {:color "#33658A"
                                                                                                                         :border-color "rgba(51,101,138,0.28)"}]
   [".pl-root.light-theme .pl-sub-name" {:color "#363636"}]
   [".pl-root.light-theme .pl-sub-row input[type=range], .pl-root.light-theme .pl-slot-panel input[type=range]" {:accent-color "#33658A"}]
   [".pl-root.light-theme .pl-sub-chip.shaded, .pl-root.light-theme .pl-sub-chip.overridden, .pl-root.light-theme .pl-tint-chip.shaded, .pl-root.light-theme .pl-tint-chip.overridden" {:border-color "#33658A"}]
   [".pl-root.light-theme .pl-tint-chip.open" {:box-shadow "0 0 0 2px rgba(51,101,138,0.32)"}]
   [".pl-root.light-theme .pl-sw" {:background "#f7f8fa"
                                   :border-color "rgba(0,0,0,0.10)"}]
   [".pl-root.light-theme .pl-sw:hover" {:border-color "#33658A"}]
   [".pl-root.light-theme .pl-sw.selected" {:border-color "#33658A"
                                            :background "#e9eef3"
                                            :box-shadow "0 0 0 2px rgba(51,101,138,0.32)"}]
   [".pl-root.light-theme .pl-sw-none" {:background "repeating-linear-gradient(45deg, #eceef1 0 5px, transparent 5px 10px)"
                                        :color "#8a8a8a"}]
   ;; The launcher lives in the Description tab, not in the drawer, so it hangs
   ;; off .app rather than .pl-root -- scoping it to .pl-root left an amber
   ;; button sitting in an otherwise blue light theme.
   [".app.light-theme .pl-launcher" {:background "linear-gradient(to bottom, #33658A, #2b5677)"
                                     :color "#fff"
                                     :border-color "#24485f"
                                     :box-shadow "0 6px 14px -8px rgba(51,101,138,0.5)"}]
   ;; the launcher button that sits next to the Image URL input in the builder
   [".pl-launcher" {:display "inline-flex"
                    :align-items "center"
                    :gap "7px"
                    :padding "9px 14px"
                    :border-radius "8px"
                    :background "linear-gradient(to bottom, #f1a20f, #dbab50)"
                    :color "#15202e"
                    :font "700 13px/1 'Open Sans', system-ui, sans-serif"}
                   {:border "1px solid #b57500"
                    :box-shadow "0 6px 14px -8px rgba(240,161,0,0.32)"
                    :cursor "pointer"
                    :margin-top "5px"}]
   [".pl-launcher:hover" {:filter "brightness(1.06)"}]
   ;; Second way in: a pencil over the summary thumbnail, for people who are
   ;; looking at the portrait rather than at the Description tab's URL field.
   ;; Only rendered in the builder, where there is a drawer to open.
   [".pl-thumb-edit" {:position "absolute"
                      :right "-7px"
                      :bottom "-7px"
                      :width "30px"
                      :height "30px"
                      :padding "0"
                      :display "flex"
                      :align-items "center"}
                     {:justify-content "center"
                      :border-radius "50%"
                      :background "linear-gradient(to bottom, #f1a20f, #dbab50)"
                      :color "#15202e"
                      :font-size "15px"
                      :line-height "1"
                      :border "2px solid #15202e"
                      :box-shadow "0 3px 8px -2px rgba(0,0,0,0.5)"}
                     {:cursor "pointer"}]
   [".pl-thumb-edit:hover" {:filter "brightness(1.08)"}]
   [".app.light-theme .pl-thumb-edit" {:background "linear-gradient(to bottom, #33658A, #2b5677)"
                                       :color "#fff"
                                       :border-color "#fff"}]
   ;; Nothing chosen yet: the slot still shows, so the pencil has somewhere to sit. Opaque and
   ;; dark in both themes, like the frame: a portrait-shaped hole, not a window onto the page.
   [".pl-thumb-empty" {:display "flex"
                       :align-items "flex-end"
                       :justify-content "center"
                       :border "1px dashed rgba(240, 161, 0, 0.3)"
                       :border-radius "10px"
                       :background "radial-gradient(circle at 50% 35%, #202939, #131924 60%, #0f141c)"}]
   [".pl-thumb-credit" {:width "100px"
                        :margin-top "5px"
                        :font "500 9px/1.25 'Open Sans', system-ui, sans-serif"
                        :color "#8b95a5"
                        :letter-spacing "0.02em"
                        :position "relative"}]
   [".pl-thumb-more" {:font "700 9px/1 'Open Sans', system-ui, sans-serif"
                      :color "#f0a100"
                      :background "rgba(240,161,0,0.12)"
                      :border "1px solid rgba(240,161,0,0.45)"
                      :border-radius "8px"
                      :padding "1px 5px"
                      :cursor "pointer"
                      :vertical-align "1px"}]
   [".pl-thumb-more:hover, .pl-thumb-more[aria-expanded=true]" {:background "rgba(240,161,0,0.24)"}]
   [".pl-thumb-more:focus-visible" {:outline "2px solid #f0a100"
                                    :outline-offset "1px"}]
   [".pl-thumb-more-list" {:position "absolute"
                           :left "0"
                           :top "calc(100% + 4px)"
                           :z-index "20"
                           :min-width "150px"
                           :max-width "220px"
                           :padding "8px 10px"
                           :background "#131924"}
                          {:border "1px solid rgba(240,161,0,0.35)"
                           :border-radius "6px"
                           :box-shadow "0 6px 18px rgba(0,0,0,0.45)"
                           :font "500 12px/1.6 'Open Sans', system-ui, sans-serif"
                           :color "#c9d0da"}]
   [".pl-thumb-more-lead" {:font-weight "700"
                           :color "#ebeef4"}]
   [".pl-thumb-more-cap" {:font-size "10px"
                          :text-transform "uppercase"
                          :letter-spacing "0.12em"
                          :color "#7b8494"
                          :margin-top "2px"}]
   [".app.light-theme .pl-thumb-more-list" {:background "#fff"
                                            :color "#363636"
                                            :border-color "rgba(51,101,138,0.35)"}]
   [".app.light-theme .pl-thumb-more-lead" {:color "#1f2a37"}]
   [".app.light-theme .pl-thumb-credit" {:color "#6a6a6a"}]
   [".pl-thumb-credit-link" {:color "inherit"
                             :text-decoration "underline"
                             :text-underline-offset "2px"}]
   [".pl-thumb-credit-link:hover" {:color "#f0a640"}]
   ;; clear of the pencil in the bottom-right corner
   [".pl-thumb-empty-label" {:font "600 10px/1 'Open Sans', system-ui, sans-serif"
                             :color "#8b95a5"
                             :letter-spacing "0.08em"
                             :padding "0 22px 11px 0"}]
   ;; ---- inline: the compositor as a builder tab ----
   ;; Everything that makes the drawer a drawer hangs off .pl-drawer -- fixed
   ;; position, backdrop, slide-in -- so the shared body needs only a frame of
   ;; its own and a scroll bound to sit in the page flow instead of over it.
   [".pl-inline" {:display "flex"
                  :flex-direction "column"
                  :border "1px solid rgba(255,255,255,0.06)"
                  :border-radius "12px"
                  :background "#131924"
                  :overflow "hidden"
                  :font-family "'Open Sans', system-ui, sans-serif"
                  :color "#ebeef4"}
                 {:margin-bottom "20px"}]
   [".pl-inline .pl-drawer-body" {:max-height "70vh"}]
   [".pl-inline .pl-btn-primary:disabled" {:opacity "0.45"
                                           :cursor "default"
                                           :filter "none"}]
   [".pl-root.light-theme.pl-inline" {:background "#f7f7f5"
                                      :color "#363636"
                                      :border-color "rgba(0,0,0,0.1)"}]
   ;; on a phone the two columns already stack; let the page scroll, not the panel
   (at-media {:max-width "700px"}
     [".pl-inline .pl-drawer-body" {:max-height "none"}])])

(def empty-slot
  "The empty frame and thumbnail: a shallow gradient with the site logo debossed in it. The logo
   is --pl-logo, set from branding on the element, so a fork shows its own."
  [[".pl-portrait-frame, .pl-thumb-empty" {:background "linear-gradient(180deg, #1b2230 0%, #141a25 100%)"}]
   [".pl-thumb-empty" {:position "relative"}]
   [".pl-empty-hint::before, .pl-thumb-empty::before" {:content "''"
                                                       :position "absolute"
                                                       :pointer-events "none"
                                                       :left "50%"
                                                       :top "44%"
                                                       :transform "translate(-50%, -50%)"
                                                       :width "56%"
                                                       :height "34%"}
                                                      {:background-color "rgba(255,255,255,0.055)"
                                                       :-webkit-mask-image "var(--pl-logo)"
                                                       :mask-image "var(--pl-logo)"
                                                       :-webkit-mask-size "contain"
                                                       :mask-size "contain"
                                                       :-webkit-mask-repeat "no-repeat"
                                                       :mask-repeat "no-repeat"
                                                       :-webkit-mask-position "center"}
                                                      {:mask-position "center"
                                                       :filter "drop-shadow(0 1px 0 rgba(0,0,0,0.5))"}]
   ;; The big frame also carries the 'pick a layer' hint, and a wordmark directly
   ;; behind type muddies both. The mark sits high, the hint sits low.
   [".pl-empty-hint" {:align-content "end"
                      :padding-bottom "34%"}]
   [".pl-empty-hint::before" {:top "34%"
                              :width "50%"
                              :height "26%"}]
   [".pl-thumb-empty::before" {:top "40%"
                               :width "62%"
                               :height "38%"}]])

(def composite
  "The composed portrait. Each layer gets its place in the stack, art and tint as --pl-z,
   --pl-art and --pl-tint."
  [[".portrait-composite" {:position "relative" :width "100%" :height "100%"}]
   [".portrait-layer" {:position "absolute" :inset "0" :width "100%" :height "100%"
                       :object-fit "contain" :z-index "var(--pl-z)" :pointer-events "none"}]
   [".portrait-layer.portrait-layer-shadow" {:z-index "100" :mix-blend-mode "multiply"}]
   [".portrait-layer.portrait-layer-multiply" {:z-index "101" :mix-blend-mode "multiply"}]
   [".portrait-layer.portrait-layer-screen" {:z-index "102" :mix-blend-mode "screen"}]
   [".portrait-layer-art, .portrait-layer-tinted" {:background-image "var(--pl-art)"
                                                   :background-size "contain"
                                                   :background-repeat "no-repeat"
                                                   :background-position "center"}]
   ;; the mask clips the multiply to the piece's own shape; without it the tint fills the box
   [".portrait-layer-tinted" {:background-color "var(--pl-tint)"
                              :background-blend-mode "multiply"
                              :-webkit-mask-image "var(--pl-art)"
                              :mask-image "var(--pl-art)"
                              :-webkit-mask-size "contain"
                              :mask-size "contain"
                              :-webkit-mask-repeat "no-repeat"
                              :mask-repeat "no-repeat"}
                             {:-webkit-mask-position "center"
                              :mask-position "center"}]
   [".pl-thumb-box" {:position "relative" :width "100px" :height "125px" :flex-shrink "0"}]
   [".pl-thumb-box > .portrait-composite" {:position "absolute" :inset "0"}]
   [".pl-thumb-box > img" {:width "100%" :height "100%"}]
   ;; last, so a swatch's own colour wins over its class's rules
   [".pl-fill" {:background "var(--pl-fill)"}]])

(def styles
  "Everything above, for orcpub.styles.core/app."
  (concat drawer empty-slot composite))
