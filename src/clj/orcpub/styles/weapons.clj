(ns orcpub.styles.weapons
  "The character sheet's Weapons table: rows, roll tiles, strips and chips. Every colour is a
  CSS variable set per theme on .weapons-section, so a display setting only swaps variables.
  Design and values: weapons-table-spec.md and handoff-weapons-table.md.")

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
   :--wpn-tile-shadow "0 1px 1px rgba(0,0,0,0.35), 0 0 1px rgba(0,0,0,0.25)"})

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
   :--wpn-tile-shadow "none"})

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

    ;; the opened row: an inset panel under the row
    [:.weapon-opened
     {:margin "0 10px 10px" :padding "10px 12px" :border-radius "4px"
      :background "rgba(0,0,0,0.18)" :font-size "13px"}]
    [:.app.light-theme [:.weapon-opened {:background "rgba(255,255,255,0.55)"}]]
    [:.weapon-opened-name {:font-weight 700 :font-size "14px" :margin-bottom "4px"}]]
   (for [[t [dot ribbon]] damage-type-colours]
     [(keyword (str ".dt-" (name t))) {:--dt-dot dot :--dt-ribbon ribbon}])))
