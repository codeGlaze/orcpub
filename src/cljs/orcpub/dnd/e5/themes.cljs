(ns orcpub.dnd.e5.themes
  "Theme registry — themes are DATA, mechanics are central.

   The scroll-scrub machinery, the header-shape CSS, the page-environment layers, and
   glyph rendering all live once (styles/core.clj + views). A theme is just a bundle
   that SELECTS among them:

     {:tokens {:header-shape :band|:recessed|:socket
               :glyph        <mark key or nil>
               :accent       <css color>
               :page {:ambient :watermark :grain :spine → bool}}
      :policy {:header-shape :glyph :page → :locked | :default | :choosable}}

   `policy` drives which controls a theme exposes in the switcher/authoring panel.
   Adding a theme is ONE entry here — no new CSS/mechanics until a theme needs a
   bespoke asset (custom sigil, keyframe) that doesn't fit the token model.")

(def themes
  {:classic {:label  "Classic"
             :swatch "#8a94a3"
             :note   "Flat band, no glyphs — minimal. For players who dislike change."
             :tokens {:header-shape :band
                      :glyph        nil
                      :accent       "#f0a100"
                      :page {:ambient false :watermark false :grain false :spine false}}
             :policy {:header-shape :locked :glyph :locked :page :locked}}

   :dwarven {:label  "Dwarven"
             :swatch "#f0a100"
             :note   "Recessed well + rune crest. Glyph is choosable."
             :tokens {:header-shape :recessed
                      :glyph        :runes
                      :accent       "#f0a100"
                      :page {:ambient false :watermark true :grain false :spine true}}
             :policy {:header-shape :default :glyph :choosable :page :default}}

   :arcane  {:label  "Arcane"
             :swatch "#7c8cff"
             :note   "Contained socket panel, arcane sigil — locked to theme."
             :tokens {:header-shape :socket
                      :glyph        :arcane
                      :accent       "#7c8cff"
                      :page {:ambient true :watermark true :grain false :spine false}}
             :policy {:header-shape :locked :glyph :locked :page :locked}}})

(def default-theme :classic)

;; render order for the switcher
(def theme-order [:classic :dwarven :arcane])

;; header-mark glyph tints (the badge color per mark; the crest watermark is cool slate)
(def glyph-colors
  {:runes "#f0d9a8" :draconic "#f0a100" :arcane "#6f97c4" :sylvan "#8fb98a"})

;; Header-mark glyphs — literal inline SVG (JS-built SVG didn't render through the template
;; holes). The header mark (upper-right, warm-tinted) is the per-family badge; it reads as a
;; system against the cool-slate background crest (lower-left). :glyph token selects one.
(def glyphs
  {:runes
   [:svg {:viewBox "0 0 100 100" :width "300" :height "300" :fill "none"
          :stroke "currentColor" :stroke-width "0.7"}
    [:circle {:cx 50 :cy 50 :r 46}] [:circle {:cx 50 :cy 50 :r 34}]
    [:polygon {:points "50,6 88,28 88,72 50,94 12,72 12,28"}]
    [:polygon {:points "50,16 79,33 79,67 50,84 21,67 21,33"}]
    [:line {:x1 50 :y1 6 :x2 50 :y2 94}]
    [:line {:x1 12 :y1 28 :x2 88 :y2 72}]
    [:line {:x1 88 :y1 28 :x2 12 :y2 72}]]
   :draconic
   [:svg {:viewBox "0 0 100 100" :width "300" :height "300" :fill "none"
          :stroke "currentColor" :stroke-width "0.7" :stroke-linejoin "round"}
    [:polygon {:points "50,8 86,29 86,71 50,92 14,71 14,29"}]
    [:polygon {:points "50,8 86,71 14,71"}] [:polygon {:points "50,92 86,29 14,29"}]
    [:circle {:cx 50 :cy 50 :r 12}]]
   :arcane
   [:svg {:viewBox "0 0 100 100" :width "300" :height "300" :fill "none"
          :stroke "currentColor" :stroke-width "0.7" :stroke-linejoin "round"}
    [:circle {:cx 50 :cy 50 :r 45}] [:polygon {:points "50,7 93,50 50,93 7,50"}]
    [:polygon {:points "20,20 80,20 80,80 20,80"}] [:circle {:cx 50 :cy 50 :r 9}]]
   :sylvan
   [:svg {:viewBox "0 0 100 100" :width "300" :height "300" :fill "none"
          :stroke "currentColor" :stroke-width "0.7" :stroke-linecap "round"}
    [:path {:d "M50 6 C 80 32, 80 68, 50 94 C 20 68, 20 32, 50 6 Z"}]
    [:line {:x1 50 :y1 12 :x2 50 :y2 88}]
    [:path {:d "M50 34 C 62 34, 70 42, 72 50"}] [:path {:d "M50 34 C 38 34, 30 42, 28 50"}]
    [:path {:d "M50 54 C 62 54, 70 62, 72 70"}] [:path {:d "M50 54 C 38 54, 30 62, 28 70"}]]})

(defn theme [k] (get themes k (get themes default-theme)))
(defn tokens [k] (:tokens (theme k)))
(defn policy [k] (:policy (theme k)))
(defn header-shape [k] (get-in (theme k) [:tokens :header-shape]))
(defn page-fx [k] (get-in (theme k) [:tokens :page]))
