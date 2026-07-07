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

(defn theme [k] (get themes k (get themes default-theme)))
(defn tokens [k] (:tokens (theme k)))
(defn policy [k] (:policy (theme k)))
(defn header-shape [k] (get-in (theme k) [:tokens :header-shape]))
(defn page-fx [k] (get-in (theme k) [:tokens :page]))
