(ns orcpub.styles.weapons
  "The character sheet's Weapons table: rows, roll tiles, strips and chips. Every colour is a
  CSS variable set per theme on .weapons-section, so a display setting only swaps variables.
  Design and values: weapons-table-spec.md and handoff-weapons-table.md."
  (:require [garden.stylesheet :refer [at-media at-keyframes]]))

(def damage-type-colours
  "Each damage type's dot (on a dark strip) and ribbon (a filled strip or chip), v5 palette."
  {:acid ["#c6e83a" "#465403"] :bludgeoning ["#c9a47e" "#664521"] :cold ["#5ee6e0" "#025956"]
   :fire ["#ff8a4a" "#9a3a10"] :force ["#c792f0" "#5a2f7a"] :lightning ["#8fb0ff" "#2f4fa0"]
   :necrotic ["#a3b07a" "#3b3f2a"] :piercing ["#7fc48a" "#23633a"] :poison ["#9fd36a" "#3f6b1f"]
   :psychic ["#f08ad8" "#7a2f6b"] :radiant ["#ffd95a" "#7a6212"] :slashing ["#e8705a" "#8a2f1f"]
   :thunder ["#a8a0b8" "#50495e"]})

(def dark-vars
  "The table's colours on the dark theme."
  {:--wpn-muted "rgba(255,255,255,0.75)"
   :--wpn-row-alt "rgba(255,255,255,0.1)"
   :--wpn-strip "#1a1e28"
   :--wpn-strip-ink "#f0a100"
   :--wpn-strip-text "#e6e9ee"
   :--wpn-no-prof "#a4262c"
   :--wpn-track "#1a1e28"
   :--wpn-track-ink "#a3acbd"
   :--wpn-toggle-on "#d6dbe3"
   :--wpn-toggle-on-ink "#1a1e28"
   :--wpn-no-roll-edge "#6b7690"
   :--wpn-tint "22%"
   :--wpn-tile-shadow "0 1px 1px rgba(0,0,0,0.35), 0 0 1px rgba(0,0,0,0.25)"
   :--wpn-outline-ink "#f0a100"
   :--wpn-glow "rgba(214,219,227,0.55)"
   :--wpn-menu-bg "#1a1e28"
   :--wpn-menu-ink "#ffffff"
   :--wpn-menu-line "rgba(255,255,255,0.14)"
   :--wpn-menu-value "#f0a100"
   :--wpn-card "#232a38"
   :--wpn-card-line "rgba(255,255,255,0.12)"})

(def light-vars
  "The table's colours on the light theme."
  {:--wpn-muted "rgba(0,0,0,0.62)"
   :--wpn-row-alt "rgba(0,0,0,0.1)"
   :--wpn-strip "#363636"
   :--wpn-strip-ink "#ffc35c"
   :--wpn-strip-text "#ffffff"
   :--wpn-track "#bdbdbd"
   :--wpn-track-ink "#4a4a4a"
   :--wpn-toggle-on "#4a4a4a"
   :--wpn-toggle-on-ink "#ffffff"
   :--wpn-no-roll-edge "#8f8f8f"
   :--wpn-tint "18%"
   :--wpn-tile-shadow "none"
   :--wpn-outline-ink "#8a5a00"
   :--wpn-glow "rgba(74,74,74,0.4)"
   :--wpn-menu-bg "#ffffff"
   :--wpn-menu-ink "#363636"
   :--wpn-menu-line "rgba(0,0,0,0.2)"
   :--wpn-menu-value "#9a5b00"
   :--wpn-card "#f6f6f6"
   :--wpn-card-line "rgba(0,0,0,0.18)"})

(def grid
  "The column template shared by the headings and every row."
  "minmax(0,1fr) 62px 106px")

(def weapons
  "The Weapons table's rules, concatenated into orcpub.styles.core/app."
  (concat
   [[:.weapons-section dark-vars]
    [:.app.light-theme [:.weapons-section light-vars]]

    [:.weapon-head
     {:display :grid :grid-template-columns grid :align-items :center :padding "4px 0"
      :font-size "11px" :font-weight 700 :letter-spacing ".06em" :text-transform :uppercase
      :color "var(--wpn-muted)"}
     [:span {:text-align :center}]
     [:span:first-child {:text-align :left :padding-left "10px"}]]

    [".weapon-row:nth-child(even)" {:background "var(--wpn-row-alt)"}]
    [:.weapon-row-main
     {:display :grid :grid-template-columns grid :align-items :center :padding "8px 0"}]
    [:.weapon-cell-name {:padding "0 6px 0 10px" :min-width 0 :min-height "44px" :cursor :pointer
                         :display :flex :flex-direction :column :justify-content :center}]
    [:.weapon-cell-tile {:padding "0 4px" :position :relative}]

    [:.weapon-name-line
     {:display :flex :align-items :baseline :min-width 0 :cursor :pointer
      :background :none :border :none :padding 0 :color :inherit :font :inherit :text-align :left
      :max-width "100%"}]
    [:.weapon-name
     {:font-weight 700 :font-size "15px" :line-height 1.25 :white-space :nowrap
      :overflow :hidden :text-overflow :ellipsis :min-width 0 :flex "0 1 auto"}]
    [:.weapon-caret {:flex-shrink 0 :margin-left "2px" :font-size "12px" :color "#f0a100"}]
    [:.app.light-theme [:.weapon-caret {:color "#363636"}]]

    [:.weapon-grey-line
     {:color "var(--wpn-muted)" :font-size "13px" :margin-top "4px" :display :flex
      :flex-wrap :wrap :align-items :center :gap "5px"}]
    [:.weapon-grey-line.text {:display :block}]

    ;; segmented switches: 1H | 2H, and item toggles
    [:.weapon-switch
     {:display :inline-flex :padding "2px" :border-radius "999px" :background "var(--wpn-track)"}]
    [:.weapon-switch-option
     {:font-size "12px" :font-weight 700 :padding "3px 9px" :border-radius "999px" :line-height 1.4
      :cursor :pointer :border :none :background :transparent :color "var(--wpn-track-ink)"
      :font-family :inherit}]
    [:.weapon-switch-option.on
     {:background "var(--wpn-toggle-on)" :color "var(--wpn-toggle-on-ink)"}]
    [:.weapon-switch-option.on.typed
     {:background "var(--dt-ribbon)" :color "#fff" :box-shadow "inset 0 0 0 1px var(--dt-dot)"}]

    ;; tiles: the app's amber .roll-button, reshaped into a body over a 17px strip
    [:.roll-button.weapon-tile
     {:width "54px" :height "54px" :min-width 0 :min-height 0 :padding 0 :margin "0 auto"
      :border-radius "5px" :overflow :hidden :display :flex :flex-direction :column
      :box-sizing :border-box :position :relative :line-height 1}]
    [:.roll-button.weapon-damage-tile {:width "98px"}]
    [:.weapon-tile-body
     {:flex 1 :display :flex :flex-direction :column :align-items :center :justify-content :center
      :gap "3px" :font-weight 700 :font-size "17px" :font-variant-numeric "tabular-nums"
      :white-space :nowrap :text-shadow "var(--wpn-tile-shadow)"}]
    [:.weapon-tile-body.with-more {:font-size "15px"}]
    [:.weapon-strip
     {:height "17px" :flex "0 0 17px" :display :flex :align-items :center :justify-content :center
      :gap "2px" :font-size "10px" :font-weight 600 :white-space :nowrap :text-shadow :none
      :background "var(--wpn-strip)" :color "var(--wpn-strip-ink)"}]
    [:.weapon-strip-num {:color "var(--wpn-strip-text)" :font-weight 700 :margin-left "1px"}]
    [:.weapon-strip.no-prof {:background "var(--wpn-no-prof, #a4262c)" :color "#fff"}]
    [:.weapon-strip.typed
     {:gap "4px" :font-size "10.5px" :letter-spacing ".06em" :color "var(--wpn-strip-text)"}]
    [:.app.light-theme [:.weapon-strip.typed {:background "var(--dt-ribbon)" :color "#fff"}]]
    [:.weapon-dot
     {:width "6px" :height "6px" :border-radius "50%" :flex-shrink 0 :background "var(--dt-dot)"}]
    [:.app.light-theme [:.weapon-strip.typed [:.weapon-dot {:display :none}]]]
    [:.weapon-more
     {:display :inline-flex :align-items :center :gap "3px" :padding "1px 5px" :border-radius "3px"
      :font-size "10px" :font-weight 700 :line-height 1.3 :text-shadow :none
      :background "var(--wpn-strip)" :color "var(--wpn-strip-text)"}]

    [:.weapon-no-roll
     {:width "98px" :height "54px" :box-sizing :border-box :border-radius "5px"
      :border "1.5px dashed var(--wpn-no-roll-edge)" :display :flex :flex-direction :column
      :align-items :center :justify-content :center :margin "0 auto"}]
    [:.weapon-no-roll-effect {:font-weight 600 :font-size "14px"}]
    [:.weapon-no-roll-label {:font-size "10.5px" :font-weight 600 :letter-spacing ".06em" :opacity 0.7}]

    ;; the lines under the row: a label, then chips that wrap
    [:.weapon-lines {:padding "0 10px 10px" :display :flex :flex-direction :column :gap "6px"}]
    [:.weapon-line {:display :flex :flex-wrap :wrap :align-items :center :gap "4px"}]
    [:.weapon-line-label
     {:font-size "11px" :font-weight 700 :letter-spacing ".06em" :text-transform :uppercase
      :white-space :nowrap :color "var(--wpn-muted)" :margin-right "2px"}]
    [:.weapon-chip
     {:display :inline-flex :align-items :center :padding "1px 5px" :border-radius "3px"
      :font-size "11px" :font-weight 700 :line-height 1.3 :white-space :nowrap}]
    [:.weapon-chip.fill {:background "var(--dt-ribbon)" :color "#fff"}]
    [:.weapon-chip.fill.ringed {:box-shadow "inset 0 0 0 1.5px var(--dt-dot)"}]
    [:.weapon-chip.tint
     {:background "color-mix(in srgb, var(--dt-dot) var(--wpn-tint), transparent)"}]
    [:.app.light-theme
     [:.weapon-chip.tint
      {:background "color-mix(in srgb, var(--dt-ribbon) var(--wpn-tint), transparent)"}]]
    [:.weapon-chip.outline
     {:font-size "12px" :font-weight 600 :padding "1px 7px" :border-radius "999px"
      :box-shadow "inset 0 0 0 1px var(--wpn-muted)"}]

    ;; the shield rules: weapons-table-spec.md rules 14-17
    [:.weapons-heading {:display :flex :align-items :center :flex-wrap :wrap :gap "8px"}]
    [:.weapon-switch.disabled {:opacity 0.5}]
    [:.weapon-switch.disabled [:.weapon-switch-option {:cursor :not-allowed}]]
    [:.roll-button.weapon-tile.needs-hands
     {:background "transparent" :background-image "none" :color "var(--wpn-outline-ink)"
      :box-shadow "inset 0 0 0 2px #f0a100"}]
    [:.roll-button.weapon-tile.needs-hands [:.weapon-tile-body {:text-shadow :none}]]
    [:.weapon-switch.pulse {:animation "weapon-pulse 10s ease-out 0s 6"}]
    [:.weapon-hands-note {:font-weight 600}]
    [:.share-pill.weapon-hand-pill
     {:flex-shrink 0 :align-self :center :gap "4px" :font-size "11px" :padding "1px 7px"
      :margin-left "6px"}]

    ;; the + tab and its menu: 18px tab, 44px tap area
    [:.weapon-plus
     {:position :absolute :top 0 :right "4px" :width "18px" :height "18px" :padding 0 :border :none
      :border-radius "0 5px 0 5px" :background "var(--wpn-strip)" :color "var(--wpn-strip-ink)"
      :font-size "15px" :font-weight 800 :line-height 1 :cursor :pointer :z-index 1}]
    [:.weapon-plus:before {:content "''" :position :absolute :top "-13px" :right "-4px" :bottom "-13px"
                           :left "-22px"}]
    [:.weapon-menu-backdrop {:position :fixed :top 0 :right 0 :bottom 0 :left 0 :z-index 2}]
    [:.weapon-menu
     {:position :absolute :top "60px" :right "4px" :z-index 3 :width "252px" :max-width "calc(100vw - 32px)"
      :background "var(--wpn-menu-bg)" :color "var(--wpn-menu-ink)" :border "1px solid var(--wpn-menu-line)"
      :border-radius "6px" :box-shadow "0 4px 16px rgba(0,0,0,.4)" :overflow :hidden :font-size "14px"}]
    [:.weapon-menu.up {:top :auto :bottom "60px"}]
    [:.weapon-menu-item
     {:display :flex :width "100%" :min-height "44px" :align-items :center :justify-content :space-between
      :gap "10px" :padding "6px 12px" :border :none :border-top "1px solid var(--wpn-menu-line)"
      :background :transparent :color :inherit :font-family :inherit :text-align :left :cursor :pointer}]
    [:.weapon-menu-item.primary
     {:min-height "56px" :border-top :none :color "#fff" :text-shadow "var(--wpn-tile-shadow)"
      :background-image "linear-gradient(to bottom, #f1a20f, #dbab50)"}]
    [:.weapon-menu-item.used {:opacity 0.45 :cursor :default}]
    [:.weapon-menu-item:focus-visible {:outline "2px solid var(--wpn-menu-ink)" :outline-offset "-2px"}]
    [:.weapon-menu-text {:display :flex :flex-direction :column :line-height 1.25}]
    [:.weapon-menu-label {:font-weight 600 :font-size "14px"}]
    [:.weapon-menu-item.primary [:.weapon-menu-label {:font-weight 800 :font-size "16px"}]]
    [:.weapon-menu-sub {:font-size "11px" :opacity 0.75}]
    [:.weapon-menu-value
     {:color "var(--wpn-menu-value)" :font-weight 700 :font-variant-numeric "tabular-nums" :white-space :nowrap
      :font-size "13px"}]
    [:.weapon-menu-item.primary [:.weapon-menu-value {:color "#fff" :font-size "15px"}]]

    ;; the Weapon Mastery band (design B3): dark band, amber angled cap
    [:.weapon-mastery-band
     {:display :flex :align-items :center :gap "8px" :min-height "24px" :padding "3px 10px"
      :background "var(--wpn-strip)" :overflow :hidden :box-shadow "0 2px 4px rgba(0,0,0,.3)"}]
    [:.weapon-mastery-cap
     {:align-self :stretch :display :flex :align-items :center :margin "-3px 0 -3px -10px"
      :padding "0 14px 0 10px" :background "#f0a100" :color "#1a1e28"
      :clip-path "polygon(0 0,100% 0,calc(100% - 8px) 100%,0 100%)" :font-size "10.5px" :font-weight 700
      :letter-spacing ".06em" :text-transform :uppercase :white-space :nowrap}]
    [:.weapon-mastery-trigger
     {:font-size "10.5px" :font-weight 700 :letter-spacing ".06em" :text-transform :uppercase
      :color "rgba(230,233,238,.6)" :white-space :nowrap}]
    [:.weapon-mastery-summary
     {:font-size "12px" :font-weight 600 :color "#e6e9ee" :min-width 0 :overflow :hidden
      :text-overflow :ellipsis :white-space :nowrap}]

    ;; desktop (weapons-table-spec.md section 5): a Details column takes the grey line; the name
    ;; cell's box goes away so its two lines become grid items in their own columns
    [:.weapons-section.desktop
     [:.weapon-head :.weapon-row-main {:grid-template-columns "minmax(0,1.1fr) minmax(0,1fr) 66px 110px"}]
     [:.weapon-head {:font-size "13px" :letter-spacing 0 :text-transform :none}]
     [".weapon-head span:nth-child(2)" {:text-align :left}]
     [:.weapon-cell-name {:display :contents}]
     [:.weapon-name-line {:padding "0 8px 0 12px" :min-height "44px" :align-items :center}]
     [:.weapon-grey-line {:margin-top 0 :padding-right "8px" :line-height 1.4}]
     [:.roll-button.weapon-tile {:width "58px"}]
     [:.roll-button.weapon-damage-tile :.weapon-no-roll {:width "102px"}]]

    ;; the result: a bottom sheet on a phone (no backdrop), a card on desktop
    [:.weapon-sheet
     {:position :fixed :z-index 60 :background "var(--wpn-menu-bg)" :color "var(--wpn-menu-ink)"
      :box-shadow "0 -4px 18px rgba(0,0,0,.35)" :padding "10px 16px 14px" :overflow-y :auto
      :max-height "70vh" :box-sizing :border-box}]
    [:.weapon-sheet.sheet {:left 0 :right 0 :bottom 0 :border-radius "14px 14px 0 0"}]
    [:.weapon-sheet.card {:right "16px" :bottom "16px" :width "380px" :border-radius "8px"
                          :border "1px solid var(--wpn-menu-line)"}]
    [:.weapon-sheet-bar {:position :relative :min-height "28px" :display :flex :justify-content :center
                         :align-items :flex-start}]
    [:.weapon-sheet-handle {:width "40px" :height "4px" :border-radius "2px" :margin-top "2px"
                            :background "var(--wpn-muted)" :opacity 0.6}]
    [:.weapon-sheet-close {:position :absolute :top "-6px" :right "-8px" :border :none :background :transparent
                           :color :inherit :font-size "22px" :line-height 1 :cursor :pointer
                           :min-width "44px" :min-height "44px"}]
    [:.weapon-sheet-history {:background "var(--wpn-row-alt)" :border-radius "4px" :margin-bottom "8px"}]
    [:.weapon-sheet-history-row {:display :grid :grid-template-columns "24px 1fr auto" :gap "8px"
                                 :padding "6px 10px" :font-size "13px"}]
    [:.weapon-sheet-top {:display :flex :justify-content :space-between :align-items :flex-start
                         :gap "12px"}]
    [:.weapon-sheet-title {:font-weight 700 :font-size "15px"}]
    [:.weapon-sheet-count {:display :flex :align-items :center :gap "4px" :font-size "12px"
                           :color "var(--wpn-muted)" :margin-top "2px"}]
    [:.weapon-sheet-dot {:width "7px" :height "7px" :border-radius "50%"
                         :box-shadow "inset 0 0 0 1.5px var(--wpn-muted)"}]
    [:.weapon-sheet-dot.done {:background "#f0a100" :box-shadow :none}]
    [:.weapon-sheet-big {:display :flex :align-items :baseline :gap "8px" :font-size "32px" :font-weight 800
                         :margin "4px 0 6px" :font-variant-numeric "tabular-nums"}]
    [:.weapon-sheet-small :.weapon-sheet-d20 {:font-size "13px" :font-weight 400 :color "var(--wpn-muted)"}]
    [:.weapon-sheet-crit {:font-size "12px" :font-weight 800 :letter-spacing ".06em" :padding "2px 8px"
                          :border-radius "999px" :background "#f0a100" :color "#1a1e28" :align-self :center}]
    [:.weapon-sheet-label {:font-size "11px" :font-weight 700 :letter-spacing ".06em" :text-transform :uppercase
                           :color "var(--wpn-muted)" :margin "4px 0 6px"}]
    [:.weapon-sheet-option
     {:display :flex :width "100%" :min-height "44px" :align-items :center :justify-content :space-between
      :gap "10px" :padding "6px 14px" :margin-bottom "8px" :border "1px solid var(--wpn-menu-line)"
      :border-radius "6px" :background :transparent :color :inherit :font-family :inherit :cursor :pointer}]
    [:.weapon-sheet-option.primary
     {:min-height "52px" :border :none :color "#fff" :text-shadow "var(--wpn-tile-shadow)"
      :background-image "linear-gradient(to bottom, #f1a20f, #dbab50)"}]
    [:.weapon-sheet-option.primary [:.weapon-menu-label {:font-weight 800 :font-size "16px"}]
     [:.weapon-menu-value {:color "#fff"}]]
    [:.weapon-sheet-part {:display :grid :grid-template-columns "10px 1fr auto 36px" :align-items :center
                          :gap "8px" :padding "7px 0" :border-top "1px solid var(--wpn-menu-line)" :font-size "14px"}]
    [:.weapon-sheet-part [:b {:text-align :right}]]
    [:.weapon-sheet-part-detail {:font-size "12px" :color "var(--wpn-muted)"}]
    [:.weapon-sheet-total {:display :flex :justify-content :space-between :align-items :baseline
                           :border-top "2px solid var(--wpn-menu-ink)" :padding-top "8px" :margin-top "2px"
                           :font-weight 700}]
    [:.weapon-sheet-total [:b {:font-size "26px"}]]
    [:.weapon-sheet-after {:display :flex :justify-content :space-between :font-size "13px" :padding-top "4px"}]
    [:.weapon-sheet-foot {:display :flex :justify-content :space-between :align-items :center :margin-top "10px"
                          :font-weight 700 :font-size "13px"}]
    [:.weapon-sheet-done {:border :none :background :transparent :color "var(--wpn-menu-value)" :font-weight 700
                          :font-family :inherit :cursor :pointer :padding "8px 4px" :min-height "44px"}]
    [:.roll-button.weapon-sheet-next {:flex-shrink 0 :margin 0}]

    ;; the opened row: an inset panel under the row
    [:.weapon-opened
     {:margin "0 10px 10px" :padding "10px 12px 12px" :display :flex :flex-direction :column :gap "6px"
      :font-size "13px" :line-height 1.45 :background "var(--wpn-card)" :border "1px solid var(--wpn-card-line)"
      :border-top 0 :border-bottom "3px solid var(--wpn-strip)"}]
    [:.weapon-opened-name {:font-size "14px" :font-weight 700}]
    [:.weapon-opened-props {:color "var(--wpn-muted)"}]
    [:.weapon-opened-rules {:border-top "1px solid var(--wpn-menu-line)" :padding-top "6px"}]]
   (for [[t [dot ribbon]] damage-type-colours]
     [(keyword (str ".dt-" (name t))) {:--dt-dot dot :--dt-ribbon ribbon}])
   [(at-keyframes "weapon-pulse"
                  ["from" {:box-shadow "0 0 0 0 var(--wpn-glow)"}]
                  ["6%" {:box-shadow "0 0 0 5px var(--wpn-glow)"}]
                  ["14%" {:box-shadow "0 0 0 0 transparent"}]
                  ["to" {:box-shadow "0 0 0 0 transparent"}])
    (at-media {:prefers-reduced-motion "reduce"}
              [:.weapon-switch.pulse {:animation :none :box-shadow "0 0 0 2px var(--wpn-glow)"}])]))
