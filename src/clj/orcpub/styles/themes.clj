(ns orcpub.styles.themes
  "Theme definitions for OrcPub.

   Each theme is a vector of Garden CSS rules targeting .app.<theme-name>.
   Themes override the default dark theme colors and styles.

   Available themes:
   - light-theme: Basic light mode
   - nord-theme: Nord dark palette
   - nord-light-theme: Nord light palette
   - nord-theme-elevated: Nord dark with shadows/depth
   - nord-light-theme-elevated: Nord light with modern card design"
  (:require [garden.selectors :as s]
            [orcpub.styles.colors :as colors]))

;; =============================================================================
;; Shared Constants
;; =============================================================================

(def font-family "Open Sans, sans-serif")

;; =============================================================================
;; Light Theme
;; =============================================================================

(def light-theme
  [[:.app.light-theme
    {:background-image "linear-gradient(182deg, #FFFFFF, #DDDDDD)"}

    [:select
     {:font-family font-family
      :color "black"
      :background-color :transparent}]

    [:.item-list
     {:border-top "1px solid rgba(0,0,0,0.5)"}]

    [:.link-button
     {:color "#363636"}]

    [:.item-list-item
     {:border-bottom "1px solid rgba(0,0,0,0.5)"}]

    [:.main-text-color
     {:color "#363636"
      :fill "#363636"}]
    [:.stroke-color
     {:stroke "#363636"}]

    ;; Header icons need white text shadow for visibility on background image
    [:.app-header
     [:.svg-icon-wrapper
      {:filter "drop-shadow(1px 1px 3px rgba(255,255,255,0.9))"}]]

    ;; Menu backgrounds should be light themed
    [:.shadow
     {:background-color "rgba(255,255,255,0.95) !important"}]

    [:.input
     {:background-color :transparent
      :color :black
      :border "1px solid #282828"
      :border-radius "5px"
      :margin-top "5px"
      :display :block
      :padding "10px"
      :width "100%"
      :box-sizing :border-box
      :font-size "14px"}]

    [:.form-button
     {:background-image "linear-gradient(to bottom, #33658A, #33658A)"}]

    [:.orange
     {:color "rgba(0,0,0,0.8)"}]

    [:.b-orange
     {:border-color "rgba(0,0,0,0.6)"}]

    [:.text-shadow
     {:text-shadow :none}]

    [:.bg-light
     {:background-color "rgba(0,0,0,0.4)"}]
    [:.bg-lighter
     {:background-color "rgba(0,0,0,0.15)"}]

    [:.b-color-gray
     {:border-color "rgba(0,0,0,0.3)"}]

    [:.builder-option-dropdown
     (merge
      {:border "1px solid #282828"
       :color "#282828"})

     [:&:active :&:focus
      {:outline :none}]]

    [:.builder-dropdown-item
     {:background-color :white
      :color "#282828"}]

    [:.sticky-header
     {:background-color :white}]

    [:table.striped
     [:tr
      [(s/& (s/nth-child :even))
       {:background-color "rgba(0, 0, 0, 0.1)"}]]]]])

;; =============================================================================
;; Nord Dark Theme
;; =============================================================================

(def nord-theme
  [[:.app.nord-theme
    {:background-image (str "linear-gradient(182deg, " colors/nord0 ", " colors/nord1 ")")}

    [:select
     {:font-family font-family
      :color colors/nord4
      :background-color :transparent}]

    [:.item-list
     {:border-top (str "1px solid " colors/nord3)}]

    [:.link-button
     {:color colors/nord8}

     [:a :a:visited
      {:color colors/nord8}]]

    ;; Header-specific overrides for icon/logo contrast (Nord dark)
    [:.app-header
     {:--header-icon-color colors/nord6}
     [:img.h-60
      {:filter "invert(1) brightness(2.5)"}]]

    [:.item-list-item
     {:border-bottom (str "1px solid " colors/nord3)}]

    [:.main-text-color
     {:color colors/nord4
      :fill colors/nord4}]

    [:.stroke-color
     {:stroke colors/nord4}]

    ;; Menu backgrounds should use Nord colors
    [:.shadow
     {:background-color (str colors/nord1 " !important")}]

    [:.input
     {:background-color colors/nord1
      :color colors/nord4
      :border (str "1px solid " colors/nord3)
      :border-radius "5px"
      :margin-top "5px"
      :display :block
      :padding "10px"
      :width "100%"
      :box-sizing :border-box
      :font-size "14px"}]

    [:.form-button
     {:background-color colors/nord13
      :background-image :none
      :color colors/nord0}]

    [:.roll-button
     {:background-color colors/nord13
      :background-image :none
      :color colors/nord0}]

    [:.orange
     {:color colors/nord13}

     [:a :a:visited
      {:color colors/nord13}]]

    [:.green
     {:color colors/nord14}

     [:a :a:visited
      {:color colors/nord14}]]

    [:.red
     {:color colors/nord11}

     [:a :a:visited
      {:color colors/nord11}]]

    [:.b-orange
     {:border-color colors/nord13}]

    [:.text-shadow
     {:text-shadow :none}]

    [:.bg-light
     {:background-color colors/nord2}]

    [:.bg-lighter
     {:background-color colors/nord1}]

    [:.b-color-gray
     {:border-color colors/nord3}]

    [:.builder-option-dropdown
     (merge
      {:border (str "1px solid " colors/nord8)
       :color colors/nord4})

     [:&:active :&:focus
      {:outline :none}]]

    [:.builder-dropdown-item
     {:background-color colors/nord1
      :color colors/nord4}]

    [:.sticky-header
     {:background-color colors/nord0}]

    [:table.striped
     [:tr
      [(s/& (s/nth-child :even))
       {:background-color colors/nord1}]]]]])

;; =============================================================================
;; Nord Light Theme
;; =============================================================================

(def nord-light-theme
  [[:.app.nord-light-theme
    {:background-image (str "linear-gradient(182deg, " colors/nord6 ", " colors/nord5 ")")}

    [:select
     {:font-family font-family
      :color colors/nord0
      :background-color :transparent}]

    [:.item-list
     {:border-top (str "1px solid " colors/nord3)}]

    [:.link-button
     {:color colors/nord10}

     [:a :a:visited
      {:color colors/nord10}]]

    ;; Header-specific overrides for icon/logo contrast (Nord light)
    ;; Note: Header background stays dark, so icons use light color (nord6)
    [:.app-header
     {:--header-icon-color colors/nord6}
     [:.svg-icon-wrapper
      {:filter "drop-shadow(1px 1px 3px rgba(236,239,244,0.9))"}]
     [:img.h-60
      {:filter "none"}]]

    [:.item-list-item
     {:border-bottom (str "1px solid " colors/nord3)}]

    [:.main-text-color
     {:color colors/nord0
      :fill colors/nord0}]

    [:.stroke-color
     {:stroke colors/nord0}]

    ;; Menu backgrounds should be light themed
    [:.shadow
     {:background-color (str "rgba(236, 239, 244, 0.98) !important")}]

    [:.input
     {:background-color :white
      :color colors/nord0
      :border (str "1px solid " colors/nord9)
      :border-radius "5px"
      :margin-top "5px"
      :display :block
      :padding "10px"
      :width "100%"
      :box-sizing :border-box
      :font-size "14px"}]

    [:.form-button
     {:background-color colors/nord10
      :background-image :none
      :color :white}]

    [:.roll-button
     {:background-color colors/nord10
      :background-image :none
      :color :white}]

    [:.orange
     {:color colors/nord10}

     [:a :a:visited
      {:color colors/nord10}]]

    [:.green
     {:color colors/nord14}

     [:a :a:visited
      {:color colors/nord14}]]

    [:.red
     {:color colors/nord11}

     [:a :a:visited
      {:color colors/nord11}]]

    [:.b-orange
     {:border-color colors/nord10}]

    [:.text-shadow
     {:text-shadow :none}]

    [:.bg-light
     {:background-color colors/nord4}]

    [:.bg-lighter
     {:background-color colors/nord5}]

    [:.b-color-gray
     {:border-color colors/nord9}]

    [:.builder-option-dropdown
     (merge
      {:border (str "1px solid " colors/nord9)
       :color colors/nord0})

     [:&:active :&:focus
      {:outline :none}]]

    [:.builder-dropdown-item
     {:background-color :white
      :color colors/nord0}]

    [:.sticky-header
     {:background-color colors/nord6}]

    [:table.striped
     [:tr
      [(s/& (s/nth-child :even))
       {:background-color "rgba(94, 129, 172, 0.12)"}]]]]])

;; =============================================================================
;; Nord Dark Theme - Elevated (with shadows and depth)
;; =============================================================================

(def nord-theme-elevated
  [[:.app.nord-theme-elevated
    {:background-image (str "linear-gradient(182deg, " colors/nord0 ", " colors/nord1 ")")}

    [:select
     {:font-family font-family
      :color colors/nord4
      :background-color :transparent}]

    ;; Header-specific overrides for icon/logo contrast (Nord elevated)
    [:.app-header
     {:--header-icon-color colors/nord6}
     [:img.h-60
      {:filter "invert(1) brightness(2.5)"}]]

    [:.item-list
     {:border-top (str "1px solid " colors/nord3)}]

    [:.link-button
     {:color colors/nord8}

     [:a :a:visited
      {:color colors/nord8}]

     [:&:hover
      {:color colors/nord7}]]

    [:.item-list-item
     {:border-bottom (str "1px solid " colors/nord3)}]

    [:.main-text-color
     {:color colors/nord4
      :fill colors/nord4}]

    [:.stroke-color
     {:stroke colors/nord4}]

    ;; Menu backgrounds with elevated styling
    [:.shadow
     {:background-color (str colors/nord1 " !important")
      :border (str "1px solid " colors/nord3)}]

    [:.input
     {:background-color colors/nord1
      :color colors/nord4
      :border (str "1px solid " colors/nord8)
      :border-radius "5px"
      :margin-top "5px"
      :display :block
      :padding "10px"
      :width "100%"
      :box-sizing :border-box
      :font-size "14px"
      :box-shadow "0 2px 4px rgba(0, 0, 0, 0.3)"}

     [:&:focus
      {:border-color colors/nord8
       :box-shadow "0 2px 8px rgba(136, 192, 208, 0.4)"}]]

    [:.form-button
     {:background-color colors/nord13
      :background-image :none
      :color colors/nord0
      :box-shadow "0 2px 4px rgba(0, 0, 0, 0.3)"}

     [:&:hover
      {:box-shadow "0 4px 8px rgba(0, 0, 0, 0.4)"
       :transform "translateY(-1px)"}]]

    [:.roll-button
     {:background-color colors/nord13
      :background-image :none
      :color colors/nord0
      :box-shadow "0 2px 4px rgba(0, 0, 0, 0.3)"}

     [:&:hover
      {:box-shadow "0 4px 8px rgba(0, 0, 0, 0.4)"
       :transform "translateY(-1px)"}]]

    [:.orange
     {:color colors/nord13}

     [:a :a:visited
      {:color colors/nord13}]]

    [:.green
     {:color colors/nord14}

     [:a :a:visited
      {:color colors/nord14}]]

    [:.red
     {:color colors/nord11}

     [:a :a:visited
      {:color colors/nord11}]]

    [:.b-orange
     {:border-color colors/nord13}]

    [:.text-shadow
     {:text-shadow :none}]

    [:.bg-light
     {:background-color colors/nord2
      :border-left (str "3px solid " colors/nord8)
      :box-shadow "0 1px 3px rgba(0, 0, 0, 0.3)"}]

    [:.bg-lighter
     {:background-color colors/nord1
      :box-shadow "0 1px 2px rgba(0, 0, 0, 0.2)"}]

    [:.b-color-gray
     {:border-color colors/nord8}]

    [:.builder-option-dropdown
     (merge
      {:border (str "1px solid " colors/nord8)
       :color colors/nord4
       :border-radius "4px"
       :box-shadow "0 1px 3px rgba(0, 0, 0, 0.2)"})

     [:&:active :&:focus
      {:outline :none
       :border-color colors/nord7
       :box-shadow "0 2px 6px rgba(143, 188, 187, 0.4)"}]]

    [:.builder-dropdown-item
     {:background-color colors/nord1
      :color colors/nord4}

     [:&:hover
      {:background-color colors/nord2}]]

    [:.sticky-header
     {:background-color colors/nord0
      :box-shadow "0 2px 4px rgba(0, 0, 0, 0.3)"}]

    [:table.striped
     [:tr
      [(s/& (s/nth-child :even))
       {:background-color colors/nord1}]]]]])

;; =============================================================================
;; Nord Light Theme - Elevated (with modern card design)
;; =============================================================================

(def nord-light-theme-elevated
  [[:.app.nord-light-theme-elevated
    {:background-image (str "linear-gradient(182deg, " colors/nord6 ", " colors/nord5 ")")}

    ;; Header-specific overrides for icon/logo contrast (Nord light elevated)
    ;; Note: Header background stays dark, so icons use light color (nord6)
    [:.app-header
     {:--header-icon-color colors/nord6}
     [:.svg-icon-wrapper
      {:filter "drop-shadow(1px 1px 3px rgba(236,239,244,0.9))"}]
     [:img.h-60
      {:filter "none"}]]

    [:select
     {:font-family font-family
      :color colors/nord0
      :background-color :transparent}]

    [:.item-list
     {:border-top (str "1px solid " colors/nord9)
      :background "linear-gradient(90deg, rgba(136,192,208,0.03), rgba(163,190,140,0.03))"}]

    [:.link-button
     {:color colors/nord9}

     [:a :a:visited
      {:color colors/nord9}]

     [:&:hover
      {:color colors/nord8
       :text-shadow "0 0 8px rgba(136, 192, 208, 0.3)"}]]

    [:.item-list-item
     {:border-bottom (str "1px solid " colors/nord9)}

     [:&:hover
      {:background-color "rgba(136, 192, 208, 0.05)"}]]

    [:.main-text-color
     {:color colors/nord0
      :fill colors/nord0}]

    [:.stroke-color
     {:stroke colors/nord0}]

    ;; Menu backgrounds should be light themed with subtle elevation
    [:.shadow
     {:background-color :white
      :background "linear-gradient(180deg, rgba(255,255,255,0.98), rgba(236,239,244,0.98)) !important"
      :backdrop-filter "blur(10px)"
      :-webkit-backdrop-filter "blur(10px)"}]

    [:.input
     {:background-color :white
      :color colors/nord0
      :border (str "1px solid " colors/nord9)
      :border-radius "5px"
      :margin-top "5px"
      :display :block
      :padding "10px"
      :width "100%"
      :box-sizing :border-box
      :font-size "14px"
      :box-shadow "0 1px 3px rgba(0, 0, 0, 0.1)"}

     [:&:focus
      {:border-color colors/nord8
       :box-shadow "0 2px 6px rgba(136, 192, 208, 0.3)"}]]

    [:.form-button
     {:background-color colors/nord8
      :background-image :none
      :color :white
      :box-shadow "0 2px 4px rgba(0, 0, 0, 0.15)"}

     [:&:hover
      {:background-color colors/nord7
       :box-shadow "0 4px 8px rgba(0, 0, 0, 0.2)"
       :transform "translateY(-1px)"}]]

    [:.roll-button
     {:background-color colors/nord8
      :background-image :none
      :color :white
      :box-shadow "0 2px 4px rgba(0, 0, 0, 0.15)"}

     [:&:hover
      {:background-color colors/nord7
       :box-shadow "0 4px 8px rgba(0, 0, 0, 0.2)"
       :transform "translateY(-1px)"}]]

    ;; Button color variants for gentle aesthetic
    [:.btn-success
     {:background-color colors/nord14
      :background-image :none}

     [:&:hover
      {:background-color "#93ae7c"}]]

    [:.btn-info
     {:background-color colors/nord9
      :background-image :none}

     [:&:hover
      {:background-color colors/nord10}]]

    [:.btn-warning
     {:background-color colors/nord13
      :background-image :none
      :color colors/nord0}

     [:&:hover
      {:background-color "#d9bb7b"}]]

    [:.btn-purple
     {:background-color colors/nord15
      :background-image :none}

     [:&:hover
      {:background-color "#a47e9d"}]]

    [:.btn-frost
     {:background-color colors/nord7
      :background-image :none}

     [:&:hover
      {:background-color colors/nord8}]]

    [:.orange
     {:color colors/nord13}

     [:a :a:visited
      {:color colors/nord13}]

     [:&:hover
      {:color colors/nord12}]]

    [:.green
     {:color colors/nord14}

     [:a :a:visited
      {:color colors/nord14}]]

    [:.red
     {:color colors/nord11}

     [:a :a:visited
      {:color colors/nord11}]]

    [:.b-orange
     {:border-color colors/nord13}]

    ;; ====================================================================
    ;; Icon Color Customization Examples
    ;; ====================================================================
    ;; With CSS mask approach, icons can use any color via .main-text-color
    ;; These examples show how to colorize icons with Aurora palette
    ;; Add classes to svg-icon calls to enable: (svg-icon "bookshelf" 32 "icon-accent")
    ;; ====================================================================

    ;; Cyan accent icons (default for this theme already)
    [:.svg-icon-wrapper.icon-accent
     {:color colors/nord8}]

    ;; Purple specialty icons
    [:.svg-icon-wrapper.icon-special
     {:color colors/nord15}]

    ;; Yellow attention/important icons
    [:.svg-icon-wrapper.icon-important
     {:color colors/nord13}]

    ;; Green success/positive icons
    [:.svg-icon-wrapper.icon-success
     {:color colors/nord14}]

    [:.text-shadow
     {:text-shadow :none}]

    [:.bg-light
     {:background-color :white
      :border-left (str "4px solid transparent")
      :border-image "linear-gradient(180deg, #88C0D0, #B48EAD) 1"
      :border-image-slice "1"
      :border-radius "4px"
      :box-shadow "0 2px 6px rgba(0, 0, 0, 0.1)"}]

    [:.bg-lighter
     {:background-color :white
      :border-left (str "3px solid transparent")
      :border-image "linear-gradient(180deg, #81A1C1, #88C0D0) 1"
      :border-image-slice "1"
      :border-radius "4px"
      :box-shadow "0 1px 3px rgba(0, 0, 0, 0.08)"}]

    [:.b-color-gray
     {:border-color colors/nord9}]

    [:.builder-option-dropdown
     (merge
      {:border (str "1px solid " colors/nord9)
       :color colors/nord0
       :border-radius "4px"
       :background-color :white
       :box-shadow "0 1px 3px rgba(0, 0, 0, 0.1)"})

     [:&:active :&:focus
      {:outline :none
       :border-color colors/nord8
       :box-shadow "0 2px 6px rgba(136, 192, 208, 0.3)"}]]

    [:.builder-dropdown-item
     {:background-color :white
      :color colors/nord0}

     [:&:hover
      {:background-color colors/nord6}]]

    [:.sticky-header
     {:background "linear-gradient(180deg, rgba(255,255,255,1), rgba(136,192,208,0.05))"
      :box-shadow "0 2px 4px rgba(0, 0, 0, 0.1)"
      :border-bottom (str "2px solid transparent")
      :border-image "linear-gradient(90deg, #88C0D0, #A3BE8C, #EBCB8B) 1"
      :border-image-slice "1"}]

    [:table.striped
     [:tr
      [(s/& (s/nth-child :even))
       {:background "linear-gradient(90deg, rgba(136,192,208,0.06), rgba(163,190,140,0.06))"}]]]]])

;; =============================================================================
;; All Themes
;; =============================================================================

(def all-themes
  "All theme definitions as a vector for concatenation into main styles."
  (concat light-theme
          nord-theme
          nord-light-theme
          nord-theme-elevated
          nord-light-theme-elevated))
