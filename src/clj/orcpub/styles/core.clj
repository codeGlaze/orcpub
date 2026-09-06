(ns orcpub.styles.core
     (:require [garden.stylesheet :refer [at-media at-keyframes]]
               [garden.units :refer [px]]
               [orcpub.constants :as const]
               [garden.selectors :as s]))

;; Color palette — used across UI for consistent theming
(def orange "#f0a100")
(def button-color orange)
(def red "#9a031e")
;; The reds above and below are the same colour at two ends of a contrast problem.
;; #9a031e reads at about 9:1 on white and about 2:1 on the app's own near-black
;; background, which is under half the readable minimum -- so the dark theme, which
;; is the default, gets the lighter one and the light theme keeps the deep one.
(def red-on-dark "#ff6b6b")
(def amber-on-dark "#f5b942")
(def muted-on-dark "#9fb0c3")
(def green "#70a800")
(def cyan "#47eaf8")      ; import log, conflict rename option
(def purple "#8b7ec8")    ; conflict skip option
(def warning-yellow "#ffd21a") ; attention severity: unresolved conflicts, missing fields
(def broken-red "#e5637a")     ; broken severity: invalid / unexportable data

(def container-style
  {:display :flex
   :justify-content :center})

(def content-style
  {:max-width (px 1440)
   :width "100%"})

(def text-color
  {:color :white})

(defn px-prop [kw abbr values]
  (map
   (fn [v]
     [(keyword (str "." (name abbr) "-" v))
      {kw (str v "px !important")}])
   values))

(def margin-lefts
  (px-prop
   :margin-left
   :m-l
   (concat (range -1 10) (range 10 55 5))))

(def margin-tops
  (px-prop
   :margin-top
   :m-t
   (concat (range 0 10) [21] (range 10 30 5))))

(def widths
  (px-prop
   :width
   :w
   [12 14 15 18 20 24 32 36 40 48 50 60 70 80 85 90 100 110 120 200 220 250 300 500 1440]))

(defn handle-browsers [property value]
  {(keyword (str "-webkit-" (name property))) value
   (keyword (str "-moz-" (name property))) value
   property value})

(def font-family "Open Sans, sans-serif")

(def font-sizes
  [[:.f-s-10
    {:font-size "10px"}]
   [:.f-s-11
    {:font-size "11px"}]
   [:.f-s-12
    {:font-size "12px !important"}]
   [:.f-s-14
    {:font-size "14px !important"}]
   [:.f-s-16
    {:font-size "16px !important"}]
   [:.f-s-18
    {:font-size "18px !important"}]
   [:.f-s-20
    {:font-size "20px !important"}]
   [:.f-s-24
    {:font-size "24px !important"}]
   [:.f-s-28
    {:font-size "28px"}]
   [:.f-s-32
    {:font-size "32px !important"}]
   [:.f-s-36
    {:font-size "36px !important"}]
   [:.f-s-48
    {:font-size "48px !important"}]])

(def props
  [[:.sans
    {:font-family font-family}]
   [:.flex
    {:display :flex}]
   [:.inline-block
    {:display :inline-block}]

   [:.flex-column
    {:flex-direction :column}]

   [:.list-style-disc
    {:list-style-type :disc
     :list-style-position :inside}]

   [:.f-w-bold
    {:font-weight :bold}]

   [:.flex-grow-1
    {:flex-grow 1}]

   [:.flex-basis-50-p
    {:flex-basis "50%"}]

   [:.i
    {:font-style :italic}]

   [:.wsp-prw
    {:white-space "pre-wrap"
     :display "block"}]

   [:.f-w-n
    {:font-weight :normal}]
   [:.f-w-b
    {:font-weight :bold}]
   [:.f-w-600
    {:font-weight 600}]

   [:.l-h-19
    {:line-height "19px"}]
   [:.l-h-20
    {:line-height "20px"}]

   [:.m-r--10
    {:margin-right "-10px"}]
   [:.m-r--5
    {:margin-right "-5px"}]
   [:.m-r-2
    {:margin-right "2px"}]
   [:.m-r-5
    {:margin-right "5px"}]
   [:.m-r-10
    {:margin-right "10px"}]
   [:.m-r-18
    {:margin-right "18px"}]
   [:.m-r-20
    {:margin-right "20px"}]
   [:.m-r-30
    {:margin-right "30px"}]

   [:.m-r-80
    {:margin-right "80px"}]

   [:.m-t--10
    {:margin-top "-10px"}]
   [:.m-t--20
    {:margin-top "-20px"}]
   [:.m-t--5
    {:margin-top "-5px"}]
   [:.m-t-2
    {:margin-top "2px"}]
   [:.m-t-20
    {:margin-top "20px"}]
   [:.m-t-30
    {:margin-top "30px"}]
   [:.m-t-40
    {:margin-top "40px"}]
   [:.m-t-21
    {:margin-top "21px"}]

   [:.opacity-0
    {:opacity 0}]
   [:.opacity-1
    {:opacity "0.1"}]
   [:.opacity-2
    {:opacity "0.2"}]
   [:.opacity-5
    {:opacity "0.5"}]
   [:.opacity-6
    {:opacity "0.6"}]
   [:.opacity-7
    {:opacity "0.7"}]
   [:.opacity-9
    {:opacity "0.9"}]

   [:.m-b--2
    {:margin-bottom "-2px"}]
   [:.m-b--1
    {:margin-bottom "-1px"}]
   [:.m-b-0-last:last-child
    {:margin-bottom "0px"}]
   [:.m-b-2
    {:margin-bottom "2px"}]
   [:.m-b-5
    {:margin-bottom "5px"}]
   [:.m-b-10
    {:margin-bottom "10px"}]
   [:.m-b-16
    {:margin-bottom "16px"}]
   [:.m-b-19
    {:margin-bottom "19px"}]
   [:.m-b-20
    {:margin-bottom "20px"}]
   [:.m-b-30
    {:margin-bottom "30px"}]
   [:.m-b-40
    {:margin-bottom "40px"}]

   [:.m-l-2
    {:margin-left "2px"}]
   [:.m-l--10
    {:margin-left "-10px"}]
   [:.m-l--5
    {:margin-left "-5px"}]
   [:.m-l-30
    {:margin-left "30px"}]

   [:.m-5
    {:margin "5px"}]

   [:.text-shadow
    {:text-shadow "1px 2px 1px black"}]

   [:.white-text-shadow
    {:text-shadow "1px 2px 1px white"}]

   [:.slight-text-shadow
    {:text-shadow "1px 1px 1px rgba(0,0,0,0.8)"}]

   [:.hover-shadow:hover :.shadow
    {:box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"}]

   [:.hover-no-shadow:hover
    {:box-shadow :none}]

   [:.hover-underline:hover
    {:text-decoration :underline}]

   [:.orange-shadow
    {:box-shadow "0 1px 0 0 #f0a100"}]

   [:.t-a-c
    {:text-align :center}]
   [:.t-a-l
    {:text-align :left}]
   [:.t-a-r
    {:text-align :right}]
   [:.justify-cont-s-b
    {:justify-content :space-between}]
   [:.justify-cont-s-a
    {:justify-content :space-around}]
   [:.justify-cont-c
    {:justify-content :center}]
   [:.justify-cont-end
    {:justify-content :flex-end}]
   [:.align-items-c
    {:align-items :center}]
   [:.align-items-t
    {:align-items :flex-start}]
   [:.align-items-end
    {:align-items :flex-end}]
   [:.flex-wrap
    {:flex-wrap :wrap}]

   [:.w-auto
    {:width :auto}]
   [:.w-10-p
    {:width "10%"}]
   [:.w-20-p
    {:width "20%"}]
   [:.w-30-p
    {:width "30%"}]
   [:.w-40-p
    {:width "40%"}]
   [:.w-50-p
    {:width "50%"}]
   [:.w-60-p
    {:width "60%"}]
   [:.w-100-p
    {:width "100%"}]

   [:.h-0
    {:height "0px"}]
   [:.h-12
    {:height "12px"}]
   [:.h-14
    {:height "14px"}]
   [:.h-15
    {:height "15px"}]
   [:.h-18
    {:height "18px"}]
   [:.h-20
    {:height "20px"}]
   [:.h-24
    {:height "24px"}]
   [:.h-25
    {:height "25px"}]
   [:.h-32
    {:height "32px"}]
   [:.h-36
    {:height "36px"}]
   [:.h-40
    {:height "40px"}]
   [:.h-48
    {:height "48px"}]
   [:.h-60
    {:height "60px"}]
   [:.h-72
    {:height "72px"}]
   [:.h-120
    {:height "120px"}]
   [:.h-170
    {:height "170px"}]
   [:.h-200
    {:height "200px"}]
   [:.h-800
    {:height "800px"}]

   [:.h-10-p
    {:height "10%"}]
   [:.h-100-p
    {:height "100%"}]
   [:.h-auto
    {:height "auto"}]

   [:.overflow-auto
    {:overflow :auto}]

   [:.posn-rel
    {:position :relative}]
   [:.posn-abs
    {:position :absolute}]
   [:.posn-fixed
    {:position :fixed}]
   [:.main-text-color
    {:color :white
     :fill :white}]
   [:.stroke-color
    {:stroke :white}]
   [:.white
    {:color :white}]
   [:.black
    {:color "#191919"}]
   [:.orange
    {:color button-color}
    [:a :a:visited
     {:color button-color}]]
   [:.a-white
    [:a :a:visited
     {:color "white !important"}]]
   [:.green
    {:color green}

    [:a :a:visited
     {:color green}]]
   ;; The app is dark by default, so this is the dark-readable red. The light
   ;; theme takes the deep one back below -- the same colour cannot serve both.
   [:.red
    {:color red-on-dark}

    [:a :a:visited
     {:color red-on-dark}]]
   [:.uppercase
    {:text-transform :uppercase}]
   [:.bg-trans
    {:background-color :transparent}]
   [:.bg-white
    {:background-color :white}]
   [:.bg-slight-white
    {:background-color "rgba(255,255,255,0.05)"}]
   [:.no-border
    {:border :none}]

   [:.underline
    {:text-decoration :underline}]
   [:.no-text-decoration
    {:text-decoration :none}]

   [:.p-t-0
    {:padding-top "0px"}]
   [:.p-t-2
    {:padding-top "2px"}]
   [:.p-t-3
    {:padding-top "3px"}]
   [:.p-t-4
    {:padding-top "4px"}]
   [:.p-t-5
    {:padding-top "5px"}]
   [:.p-t-10
    {:padding-top "10px"}]
   [:.p-t-20
    {:padding-top "20px"}]

   [:.p-b-5
    {:padding-bottom "5px"}]
   [:.p-b-10
    {:padding-bottom "10px"}]
   [:.p-b-20
    {:padding-bottom "20px"}]
   [:.p-b-40
    {:padding-bottom "40px"}]
   [:.p-0
    {:padding "0px"}]
   [:.p-1
    {:padding "1px"}]
   [:.p-2
    {:padding "2px"}]
   [:.p-5
    {:padding "5px"}]
   [:.p-10
    {:padding "10px"}]
   [:.p-20
    {:padding "20px"}]
   [:.p-30
    {:padding "30px"}]
   [:.p-5-10
    {:padding "5px 10px"}]

   [:.p-l-0
    {:padding-left "0px"}]
   [:.p-l-5
    {:padding-left "5px"}]
   [:.p-l-10
    {:padding-left "10px"}]
   [:.p-l-15
    {:padding-left "15px"}]
   [:.p-l-20
    {:padding-left "20px"}]

   [:.p-r-5
    {:padding-right "5px"}]
   [:.p-r-10
    {:padding-right "10px"}]
   [:.p-r-20
    {:padding-right "20px"}]
   [:.p-r-40
    {:padding-right "40px"}]

   [:.b-rad-50-p
    {:border-radius "50%"}]
   [:.b-rad-5
    {:border-radius "5px"}]
   [:.b-rad-10
    {:border-radius "10px"}]

   [:.b-1
    {:border "1px solid"}]
   [:.b-3
    {:border "3px solid"}]

   [:.b-b-2
    {:border-bottom "2px solid"}]

   [:.b-w-3
    {:border-width "3px"}]
   [:.b-w-5
    {:border-width "5px"}]

   [:.b-color-gray
    {:border-color "rgba(255,255,255,0.2)"}]

   [:ul.list-style-disc
    {:list-style-type :disc}]

   [:.hidden
    {:display :none}]
   [:.invisible
    {:visibility :hidden}]

   [:.tooltip
    {:position "relative"
     :display "inline-block"
     :border-bottom "1px dotted black"}]

   [:.tooltip [:.tooltiptext
               {:visibility "hidden"
                :width "130px"
                :bottom "calc(100% - -5px)"
                :left "50%"
                :margin-left "-60px"
                :background-color "black"
                :font-family "Open Sans, sans-serif"
                :font-size "14px"
                :font-weight "normal"
                :color "#fff"
                :text-align "center"
                :padding "10px 10px"
                :border-radius "6px"
                :position "absolute"
                :z-index "1"}]]

   ;; A group heading in the PDF options. Set apart from the field labels under
   ;; it -- a heading in the same weight as its own first label reads as one more
   ;; option rather than as the name of the set.
   [:.option-group-title
    {:font-size "11px"
     :font-weight "bold"
     :letter-spacing "0.08em"
     :text-transform "uppercase"
     :opacity "0.6"
     :padding-top "8px"
     :margin-bottom "6px"
     :border-top "1px solid currentColor"}]

   ;; The ? beside a PDF option, and the line it opens. A ring rather than a
   ;; word, so a column of them reads as one affordance repeated and not as
   ;; another label to parse.
   [:.option-help
    {:display "inline-flex"
     :align-items "center"
     :justify-content "center"
     :width "15px"
     :height "15px"
     :border-radius "50%"
     :border "1px solid currentColor"
     :font-size "10px"
     :font-weight "bold"
     :line-height "1"
     :opacity "0.55"}]
   [:.option-help:hover
    {:opacity "1"}]
   [:.option-help-text
    {:font-size "12px"
     :line-height "16px"
     :max-width "320px"
     :margin "4px 0 6px 21px"
     :opacity "0.75"}]

   ;; An always-on note under a control, saying what the current setting will do.
   ;; Set like the ? lines so the two read as one kind of note, but its own class
   ;; -- it is the state of the build, not a fixed explanation.
   [:.option-note
    {:font-size "12px"
     :line-height "16px"
     :max-width "320px"
     :margin "4px 0 6px 0"
     :opacity "0.75"}]

   ;; ONE line under a field. One problem, one sentence, at most one action.
   ;;
   ;; This replaced four stacked blocks -- two notices, a suggestion and a panel
   ;; of controls -- that could all appear at once for a single unreachable
   ;; picture. Six lines of prose and three controls to say "we cannot get this".
   ;; Whatever is most actionable is the only thing shown; the rest waits behind
   ;; the disclosure, and most people never need it.
   [:.field-notice
    {:display "flex"
     :flex-wrap "wrap"
     :align-items "baseline"
     :gap "8px"
     :margin "6px 0 0 0"
     :padding "6px 10px"
     :border-radius "4px"
     :border-left "3px solid currentColor"
     :background-color "rgba(255, 255, 255, 0.06)"
     :font-size "12px"
     :line-height "18px"
     :max-width "560px"}]
   [:.field-notice-what
    {:flex "1 1 260px"}]
   ;; The one action a notice may carry, and only ever one. Set as a link, not a
   ;; button: a notice that grows a control panel stops reading as a message.
   [:.field-notice-action
    {:background "none"
     :border "none"
     :padding "0"
     :font-size "12px"
     :font-family "inherit"
     :color orange
     :cursor "pointer"
     :text-decoration "underline"
     :white-space "nowrap"
     :word-break "break-all"}]
   [:.field-notice.is-error {:color red-on-dark}]
   [:.field-notice.is-warning {:color amber-on-dark}]
   [:.field-notice.is-note {:color muted-on-dark}]

   ;; The other ways in, behind the disclosure. Plain: these are controls, not a
   ;; second warning, and they are not urgent.
   [:.field-remedy
    {:display "flex"
     :flex-wrap "wrap"
     :align-items "center"
     :gap "10px"
     :margin "6px 0 0 0"
     :padding "8px 10px"
     :border-radius "4px"
     :border "1px solid rgba(255, 255, 255, 0.12)"
     :max-width "560px"
     :font-size "12px"}
    [:button
     {:white-space "nowrap"}]]

   [:.image-thumbnail
    {:max-height "100px"
     :max-width "200px"
     :border-radius "5px"}]

   [:.tooltip:hover [:.tooltiptext
                     {:visibility "visible"}]]

   [:.image-character-thumbnail
    {:max-height "100px"
     :max-width "200px"
     :border-radius "5px"}]

   [:.image-faction-thumbnail
    {:max-height "100px"
     :max-width "200px"
     :border-radius "5px"}]

   (at-keyframes
    :fade-out
    [:from {:opacity 1
            :height "100%"}]
    [:50% {:opacity 0
           :height "100%"}]
    [:to {:height "0%"}])

   [:.pointer
    {:cursor :pointer}]
   [:.cursor-disabled
    {:cursor :not-allowed}]

   [:.c-f4692a
    {:color "#f4692a"}]
   [:.c-f32e50
    {:color "#f32e50"}]
   [:.c-b35c95
    {:color "#b35c95"}]
   [:.c-47eaf8
    {:color "#47eaf8"}]
   [:.c-bbe289
    {:color "#bbe289"}]
   [:.c-f9b747
    {:color "#f9b747"}]

   [:.b-orange
    {:border-color button-color}]
   [:.b-red
    {:border-color red}]
   [:.b-gray
    {:border-color "rgba(72,72,72,0.37)"}]

   [:.hover-slight-white:hover
    {:background-color "#2c3445"
     :opacity 0.2}]

   [:.hover-opacity-full:hover
    {:opacity 1.0}]

   [:.bg-light
    {:background-color "rgba(72,72,72,0.2)"}]
   [:.bg-lighter
    {:background-color "rgba(0,0,0,0.15)"}]
   [:.bg-orange
    {:background-color orange}]
   [:.bg-red
    {:background-color red}]
   [:.bg-green
    {:background-color "#70a800"}]

   [:.message
    {:padding "10px"
     :border-radius "5px"
     :display :flex
     :justify-content :space-between
     :color :white}]

   ;; Warning/alert styles
   [:.bg-warning
    {:background-color "rgba(240, 161, 0, 0.1)"
     :border "1px solid rgba(240, 161, 0, 0.3)"
     :border-radius "4px"}]
   [:.bg-warning-item
    {:background-color "rgba(0, 0, 0, 0.2)"
     :border-radius "4px"}]

   [:.fade-out
    {:animation-name :fade-out
     :animation-duration :5s}]

   [:.no-appearance
    (handle-browsers :appearance :none)]])

#_(def xs-min "0")
(def sm-min "768px")
(def sm-max "991px")
(def md-max "1199px")

(def xs-query
  {:max-width "767px"})

(def sm-query
  {:min-width sm-min :max-width sm-max})

(def md-min "992px")

(def md-query
  {:min-width md-min :max-width md-max})

#_(def ^:private sm-or-md-query
    {:min-width sm-min :max-width md-max})


(def lg-min "1200px")

(def lg-query
  {:min-width lg-min})

(def not-lg-query
  {:max-width md-max})

(def not-xs-query
  {:min-width sm-min})

(def media-queries
  [[:.visible-xs,
    :.visible-sm,
    :.visible-md,
    :.visible-lg
    {:display "none !important"}]

   [:.visible-xs-block,
    :.visible-xs-inline,
    :.visible-xs-inline-block,
    :.visible-sm-block,
    :.visible-sm-inline,
    :.visible-sm-inline-block,
    :.visible-md-block,
    :.visible-md-inline,
    :.visible-md-inline-block,
    :.visible-lg-block,
    :.visible-lg-inline,
    :.visible-lg-inline-block 
    {:display "none !important"}]

   (at-media xs-query
    [:.visible-xs {:display "block !important"}]
    [:table.visible-xs {:display "table !important"}]
    [:tr.visible-xs {:display "table-row !important"}]
    [:th.visible-xs,
     :td.visible-xs {:display "table-cell !important"}])

   (at-media xs-query
    [:.visible-xs-block
     {:display "block !important"}])
   (at-media xs-query [
                                   :.visible-xs-inline {
                                                        :display "inline !important"
                                                        }
                                   ])
   (at-media xs-query [
                                   :.visible-xs-inline-block {
                                                              :display "inline-block !important"
                                                              }
                                   ])
   (at-media sm-query [
                                                          :.visible-sm {
                                                                        :display "block !important"
                                                                        }
                                                          :table.visible-sm {
                                                                             :display "table !important"
                                                                             }
                                                          :tr.visible-sm {
                                                                          :display "table-row !important"
                                                                          }
                                                          :th.visible-sm,
                                                          :td.visible-sm {
                                                                          :display "table-cell !important"
                                                                          }
                                                          ])
   (at-media sm-query [
                                                          :.visible-sm-block {
                                                                              :display "block !important"
                                                                              }
                                                          ])
   (at-media sm-query [
                                                          :.visible-sm-inline {
                                                                               :display "inline !important"
                                                                               }
                                                          ])
   (at-media sm-query [
                                                          :.visible-sm-inline-block {
                                                                                     :display "inline-block !important"
                                                                                     }
                                                          ])
   (at-media md-query [
                                                           :.visible-md {
                                                                         :display "block !important"
                                                                         }
                                                           :table.visible-md {
                                                                              :display "table !important"
                                                                              }
                                                           :tr.visible-md {
                                                                           :display "table-row !important"
                                                                           }
                                                           :th.visible-md,
                                                           :td.visible-md {
                                                                           :display "table-cell !important"
                                                                           }
                                                           ])
   (at-media md-query [
                                                           :.visible-md-block {
                                                                               :display "block !important"
                                                                               }
                                                           ])
   (at-media md-query [
                                                           :.visible-md-inline {
                                                                                :display "inline !important"
                                                                                }
                                                           ])
   (at-media md-query [
                                                           :.visible-md-inline-block {
                                                                                      :display "inline-block !important"
                                                                                      }
                                                           ])
   (at-media lg-query [
                                    :.visible-lg {
                                                  :display "block !important"
                                                  }
                                    :table.visible-lg {
                                                       :display "table !important"
                                                       }
                                    :tr.visible-lg {
                                                    :display "table-row !important"
                                                    }
                                    :th.visible-lg,
                                    :td.visible-lg {
                                                    :display "table-cell !important"
                                                    }
                                    ])
   (at-media  [
                                    :.visible-lg-block {
                                                        :display "block !important"
                                                        }
                                    ])
   (at-media lg-query [
                                    :.visible-lg-inline {
                                                         :display "inline !important"
                                                         }
                                    ])
   (at-media lg-query [
                                    :.visible-lg-inline-block {
                                                               :display "inline-block !important"
                                                               }
                                    ])
   (at-media xs-query [
                                   :.hidden-xs {
                                                :display "none !important"
                                                }
                                   ])
   (at-media sm-query [
                                                          :.hidden-sm {
                                                                       :display "none !important"
                                                                       }
                                                          ])
   (at-media md-query [
                                                           :.hidden-md {
                                                                        :display "none !important"
                                                                        }
                                                           ])
   (at-media lg-query [
                                    :.hidden-lg {
                                                 :display "none !important"
                                                 }
                                    ])
   [:.visible-print
    :display "none !important"
    ]
   (at-media
    {:print true}
    [:.visible-print
     {:display "block !important"}]
    [:th.visible-print,
     :td.visible-print
     {:display "table-cell !important"}]
    [:table.visible-print
     {:display "table !important"}]
    [:tr.visible-print
     {:display "table-row !important"}])
   [:.visible-print-block
    {:display "none !important"}]
   
   (at-media
    {:print true}
    [:.visible-print-block
     {:display "block !important"}])
   [:.visible-print-inline
    {:display "none !important"}]
   (at-media
    {:print true}
    [:.visible-print-inline
     {:display "inline !important"}
     ])
   [:.visible-print-inline-block
    {:display "none !important"}]
   (at-media
    {:print true}
    [:.visible-print-inline-block
     {:display "inline-block !important"}])
   (at-media
    {:print true}
    [:.hidden-print
     {:display "none !important"}])
   
   (at-media
    xs-query
    [:.user-icon
     {:display :none}]
    [:.character-builder-header
     #_{:margin-bottom 0}]
    [:.list-character-summary
     {:font-size "18px"}]
    [:.character-summary
     {:flex-wrap :wrap}]
    [:.app-header
     {:height :auto
      :background-image :none
      :background-color "rgba(0, 0, 0, 0.3)"
      :min-height 0}]
    [:.app-header-bar
     {:min-height (px 50)
      :backdrop-filter :none
      :-webkit-backdrop-filter :none}]
    [:.app-header-menu
     {:flex-grow 1}]
    [:.content
     {:width "100%"}]
    #_[:.options-column
       {:width "100%"}]
    [:.header-button-text :.header-links
     {:display :none}])

    #_(at-media
     xs-query
     [:.build-tab
      {:display :none}]
     [:.options-tab-active
      [:.options-column
       {:display :none}]
      [:.options-column
       {:display :block}]
      [:.personality-column
       {:display :none}]
      [:.details-column
       {:display :none}]]
     [:.personality-tab-active
      [:.options-column
       {:display :none}]
      [:.personality-column
       {:display :block}]
      [:.details-column
       {:display :none}]]
     [:.details-tab-active
      [:.options-column
       {:display :none}]
      [:.personality-column
       {:display :none}]
      [:.details-column
       {:display :block}]])

    #_(at-media
     sm-or-md-query
     [:.build-tab
      {:display :block}]
     [:.options-tab
      {:display :none}]
     [:.personality-tab
      {:display :none}]
     [:.build-tab-active
      [:.options-column
       {:display :block}]
      [:.stepper-column
       {:display :block}]
      [:.personality-column
       {:display :block}]
      [:.details-column
       {:display :none}]]
     [:.details-tab-active
      [:.options-column
       {:display :none}]
      [:.personality-column
       {:display :none}]
      [:.details-column
       {:display :block}]])
    

    (at-media
     not-xs-query
     #_[:.details-columns
      {:display :flex}]
     #_[:.details-column-2
      {:margin-left "40px"}])

    (at-media
     not-lg-query
     [:.registration-image
      {:display :none}]
     [:.registration-content
      {:width "100%"
       :height "100%"}]
     [:.registration-input
      {:width "100%"}])

    #_(at-media
     lg-query
     [:.builder-column
      {:display :block}]
     [:.details-column
      {:max-width "500px"}])])

(def app
  (concat
   [[:.character-builder-header
     {:margin-bottom "19px"}]

    [:.senses
     {:width "450px"}]

    [:.notes
     {:width "350px"}]

    [:.registration-content
     {:width "785px"
      :min-height "600px"}]

    [:.login-form-inputs
     {:max-width "350px"
      :margin-left :auto
      :margin-right :auto
      :margin-top "50px"}
     [:input
      {:width "100%"
       :box-sizing :border-box}]]

    [:.registration-input
     {:min-width "438px"}]

    [:p
     {:margin "10px 0"}]

    #_["input::-webkit-outer-spin-button"
       "input::-webkit-inner-spin-button"
       {:-webkit-appearance :none
        :margin 0}]

    #_["input[type=number]"
       {:-moz-appearance :textfield}]

    [:a :a:visited
     {:color orange}]

    [:select
     {:font-family font-family
      :color "white"
      :background-color :transparent}]

    [:*:focus
     {:outline 0}]

    ;; The header sticks itself; the chrome goes on only once it has, which is
    ;; what the .stuck class the page's IntersectionObserver sets says. Sticky
    ;; rather than a fixed duplicate of the header: see content-page.
    [:.sticky-header
     {:position :sticky
      :top 0
      :z-index 100}]

    [:.sticky-header.stuck
     {:box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"
      :background-color "#313A4D"}]

    [:.container
     container-style]

    [:.content
     (merge
      content-style)]

    [:.app-header
     {:background-color :black
      :background-image "url(/../../image/header-background.jpg)"
      :background-position "center"
      :background-size "cover"
      :height (px const/header-height)}]

    [:.header-tab
     {:background-color "rgba(0, 0, 0, 0.5)"
      :-webkit-backdrop-filter "blur(5px)"
      :backdrop-filter "blur(5px)"
      :border-radius "5px"}]

    [:.header-tab.mobile
     [:.title
      {:display :none}]
     [:img
      {:height "24px"
       :width "24px"}]
     {:width "30px"}]

    [:.item-list
     {:border-top "1px solid rgba(255,255,255,0.5)"}]

    [:.item-list-item
     {:border-bottom "1px solid rgba(255,255,255,0.5)"}]

    ;; Flyout menus: hidden by default, shown on hover (desktop) or focus-within (mobile tap)
    ;; z-index on hover/focus-within prevents adjacent tabs from intercepting the dropdown
    [:.header-tab
     [:&:focus {:outline :none}]
     [:.header-flyout {:display :none}]
     [:&:hover {:z-index 100}
      [:.header-flyout {:display :block}]]
     [:&:focus-within {:z-index 100}
      [:.header-flyout {:display :block}]]]

    #_[:.header-tab:hover
       [(garden.selectors/& (garden.selectors/not :.disabled))
        {:background-color orange}]]

    [:.app-header-bar
     {:min-height (px 81)
      ;;:-webkit-backdrop-filter "blur(5px)"
      ;;:backdrop-filter "blur(5px)"
      :background-color "rgba(0, 0, 0, 0.25)"}]

    #_[:.options-column
       {:width "300px"}]

    [:.builder-column
     {:display :none
      :margin "0 5px"}]

    [:.stepper-column
     {:margin-right "-10px"}]

    [:table.striped
     [:tr
      [(s/& (s/nth-child :even))
       {:background-color "rgba(255, 255, 255, 0.1)"}]]]

    [:.builder-option
     {:border-width (px 1)
      :border-style :solid
      :border-color "rgba(255, 255, 255, 0.5)"
      :border-radius (px 5)
      :padding (px 10)
      :margin-top (px 5)
      :font-weight :normal}]

    [:.builder-tabs
     {:display :flex
      :padding "10px"
      :text-transform :uppercase
      :font-weight 600}]

    [:.builder-tab
     {:flex-grow 1
      :padding-bottom "13px"
      :text-align :center
      :cursor :pointer
      :border-bottom "5px solid rgba(72,72,72,0.37)"}
     [:.builder-tab-text
      {:opacity 0.2}]]

    [:.selected-builder-tab
     {:border-bottom-color "#f1a20f"}
     [:.builder-tab-text
      {:opacity 1}]]

    [:.collapsed-list-builder-option
     {:padding "1px"}]

    [:.disabled-builder-option
     {:color "rgba(255, 255, 255, 0.5)"
      :border-color "rgba(255, 255, 255, 0.25)"
      :cursor :auto}]

    [:.selectable-builder-option:hover
     {:border-color "#f1a20f"
      :box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"
      :cursor :pointer}]

    [:.builder-selector
     {:padding (px 5)
      :font-size (px 14)
      :margin-top (px 10)}]

    [:.builder-selector-header
     {:font-size (px 18)
      :font-weight :normal}]

    [:.builder-option-dropdown
     (merge
      {:background-color :transparent
       :width "100%"
       :cursor :pointer
       :border "1px solid white"}
      text-color
      (handle-browsers :appearance :menulist))

     [:&:active :&:focus
      {:outline :none}]]

    [:.builder-dropdown-item
     {:-webkit-appearance :none
      :-moz-appearance :none
      :appearance :none
      :background-color :black}]

    [:.selected-builder-option
     {:border-width (px 3)
      :border-color :white
      :font-weight :bold}]

    [:.remove-item-button
     {:color button-color
      :font-size "16px"
      :margin-left "5px"
      :cursor :pointer}]

    [:.add-item-button
     {:margin-top "19px"
      :color button-color
      :font-weight 600
      :text-decoration :underline
      :cursor :pointer}]

    [:.list-selector-option
     {:display :flex
      :align-items :center}]

    [:.expand-collapse-button
     {:font-size "12px"
      :max-width "100px"
      :margin-left "10px"
      :color "#f0a100"
      :text-decoration :underline
      :cursor :pointer
      :text-align :right}]

    [:.fa-caret-square-o-down
     {:color button-color}]

    [:.expand-collapse-button:hover
     {:color button-color}]

    [:.abilities-polygon
     {:transition "points 2s"
      :-webkit-transition "points 2s"}]

    [:.display-section-qualifier-text
     {:font-size "12px"
      :margin-left "5px"}]

    [:.form-button
     {:color :white
      :font-weight 600
      :font-size "12px"
      :border :none
      :border-radius "5px"
      :text-transform :uppercase
      :padding "10px 15px"
      :cursor :pointer
      :background-image "linear-gradient(to bottom, #f1a20f, #dbab50)"}]

    ;; ── Library-header "disabled" badges ─────────────────────────────────────
    ;; A small pill on a collapsed library row showing how many items are OFF,
    ;; colored by REASON (not a blanket warning): blue = you turned it off
    ;; (benign), amber = the app turned it off for compatibility (kept one of a
    ;; duplicate). The pill's tinted fill carries the contrast, so calm hues stay
    ;; legible. Light-mode re-tone lives under .app.light-theme below; and
    ;; prefers-contrast: more swaps to solid high-contrast fills automatically.
    [:.lib-badge
     {:display "inline-flex"
      :align-items :center
      :gap "5px"
      :font-size "12px"
      :font-weight 600
      :padding "3px 10px"
      :border-radius "999px"
      :line-height 1
      :margin-left "10px"
      :vertical-align :middle}
     [:.lib-dot {:width "6px" :height "6px" :border-radius "50%"}]]
    [:.lib-badge-benign
     {:background-color "rgba(110,168,220,0.18)" :color "#9ec7ea"}
     [:.lib-dot {:background-color "#6ea8dc"}]]
    [:.lib-badge-compat
     {:background-color "rgba(217,165,32,0.20)" :color "#e5c169"}
     [:.lib-dot {:background-color "#d9a520"}]]
    (at-media {:prefers-contrast "more"}
              [:.lib-badge-benign {:background-color "#6ea8dc" :color "#0b1a29"}]
              [:.lib-badge-compat {:background-color "#d9a520" :color "#241a00"}]
              [:.app.light-theme
               [:.lib-badge-benign {:background-color "#33658A" :color "#ffffff"}]
               [:.lib-badge-compat {:background-color "#8a5a00" :color "#ffffff"}]])

    [:.roll-button
     {:color :white
      :min-width "68px"
      :font-weight 600
      :font-size "14px"
      :border :none
      :border-radius "2px"
      :padding "6px 6px"
      :margin-right "2px"
      :margin-left "2px"
      :margin-bottom "2px"
      :margin-top "2px"
      :cursor :pointer
      :background-image "linear-gradient(to bottom, #f1a20f, #dbab50)"}]

    [:.form-button:hover
     {:box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"}]

    [:.form-button.disabled
     {:opacity 0.5
      :cursor :not-allowed
      :pointer-events "none"}]

    [:.form-button.disabled:hover
     {:box-shadow :none}]

    ;; ── My Content toolbar + delete guard + move/copy select mode ──────────
    ;; One tidy right-aligned row; icon+label buttons that collapse to icon-only
    ;; in PRIORITY order (Delete first, Export last) as width tightens.
    [:.mc-toolbar
     {:position :relative
      :display :flex
      :flex-wrap :nowrap
      :justify-content :space-between   ; content action left · library actions right
      :align-items :center
      :gap "10px"
      :margin "0 10px"}]
    [:.mc-right {:display :flex :align-items :center :gap "10px"}]
    [:.mc-btn
     {:color :white
      :font-weight 600
      :font-size "12px"
      :border :none
      :border-radius "5px"
      :text-transform :uppercase
      :padding "10px 15px"
      :cursor :pointer
      :white-space :nowrap
      :display :inline-flex
      :align-items :center
      :gap "7px"
      :background-image "linear-gradient(to bottom, #f1a20f, #dbab50)"}
     [:.fa {:font-size "13px"}]]
    [:.mc-btn:hover {:box-shadow "0 2px 6px 0 rgba(0,0,0,0.5)"}]
    ;; Export = primary: a subtle ring so the most-relied-on action leads.
    [:.mc-primary {:box-shadow "0 0 0 2px rgba(241,162,15,0.35)"}]
    [:.mc-primary:hover {:box-shadow "0 0 0 2px rgba(241,162,15,0.5), 0 2px 6px 0 rgba(0,0,0,0.5)"}]
    ;; hairline divider between the safe cluster and the destructive guard
    [:.mc-divider
     {:width "1px" :align-self :stretch :min-height "26px"
      :background-color "rgba(255,255,255,0.15)" :margin "0 5px"}]

    ;; Delete: a quiet, slightly-transparent red guard that unfurls
    [:.mc-guard-wrap {:position :relative :display :inline-flex}]
    [:.mc-guard
     {:display :inline-flex :align-items :center :gap "7px"
      :background-color "rgba(154,3,30,0.26)"
      :border "1px solid rgba(154,3,30,0.55)"
      :color "#ef8592" :border-radius "5px" :padding "10px 14px"
      :cursor :pointer :font-weight 600 :font-size "12px"
      :text-transform :uppercase :white-space :nowrap}
     [:.fa {:font-size "13px"}]]
    [:.mc-guard:hover {:background-color "rgba(154,3,30,0.45)" :color :white}]
    ;; the full button lifts OUT of the bar (absolute) — the row never reflows
    [:.mc-liftpop
     {:position :absolute :right "0" :bottom "calc(100% + 9px)"
      :display :flex :align-items :center :gap "10px"
      :background-color "#1a1013" :border "1px solid rgba(154,3,30,0.6)"
      :border-radius "7px" :padding "8px 10px" :white-space :nowrap
      :box-shadow "0 14px 34px rgba(0,0,0,0.55)" :z-index 20}]
    ;; solid-red form-button variant (palette red #9a031e)
    [:.mc-del {:background-image "linear-gradient(to bottom, #b3122a, #9a031e)"}]
    ;; the are-you-sure bar, opens UNDERNEATH the toolbar
    [:.mc-confirmbar
     {:display :flex :align-items :center :gap "12px" :flex-wrap :wrap
      :margin-top "12px" :padding "12px 14px"
      :background-color "#1a1013" :border "1px solid rgba(154,3,30,0.55)"
      :border-radius "6px"}]

    ;; Move/copy select mode: round selector (distinct from the square enable)
    ;; + whole-row tap.
    [:.mc-selrow {:cursor :pointer :border-radius "4px"}]
    [:.mc-selrow:hover {:background-color "rgba(255,255,255,0.03)"}]
    [:.mc-selrow.selected
     {:background-color "rgba(240,161,0,0.09)" :box-shadow "inset 3px 0 0 #f0a100"}]
    [:.mc-selcircle
     {:width "22px" :height "22px" :border-radius "50%"
      :border "2px solid #6b7788" :display :inline-flex
      :align-items :center :justify-content :center :flex "0 0 auto"}
     [:.fa {:font-size "11px" :color "#101720" :opacity 0}]]
    [:.mc-selcircle.on {:background-color "#f0a100" :border-color "#f0a100"}
     [:.fa {:opacity 1}]]

    ;; priority collapse — hide labels one at a time as the viewport narrows,
    ;; Delete first (rare + red = unmistakable), Export last (most relied-on).
    (at-media {:max-width "1000px"} [:.mc-toolbar [:.b-delete [:.mc-lbl {:display :none}]]])
    (at-media {:max-width "740px"}  [:.mc-toolbar [:.b-move   [:.mc-lbl {:display :none}]]])
    (at-media {:max-width "600px"}  [:.mc-toolbar [:.b-export [:.mc-lbl {:display :none}]]])

    ;; ── Library health status ─────────────────────────────────────────────
    ;; Passive card that appears only when something needs attention. Warm
    ;; escalation: warning-yellow for resolvable (conflicts, missing fields),
    ;; red reserved for broken. One --accent drives the rail, icon and action
    ;; link so it reads as a single object; a one-time flash fires on appearance
    ;; and whenever the count changes (the card is re-keyed on count).
    (at-keyframes "health-pulse"
                  [:0% {:box-shadow "0 0 0 0 rgba(255,210,26,0.55)"}]
                  [:60% {:box-shadow "0 0 0 12px rgba(255,210,26,0)"}]
                  [:100% {:box-shadow "0 0 0 0 rgba(255,210,26,0)"}])
    [:.health-card
     {:background-color "#171d27" :border-radius "6px" :overflow :hidden :margin-bottom "10px"
      :position :relative}]
    ;; dismiss × (never on My Content) — a quiet control in the corner
    [:.health-x
     {:position :absolute :top "6px" :right "10px" :cursor :pointer
      :color "rgba(255,255,255,0.35)" :font-size "13px" :z-index 1}]
    [:.health-x:hover {:color "rgba(255,255,255,0.75)"}]
    [:.health-flash {:animation "health-pulse 1.15s ease-out 2"}]
    [:.health-row
     {:display :flex :align-items :center :gap "10px" :padding "12px 14px 12px 0"}]
    [:.health-rail {:width "4px" :align-self :stretch :flex "0 0 auto" :background-color warning-yellow}]
    [:.health-ico {:flex "0 0 auto" :width "20px" :text-align :center :color warning-yellow}]
    [:.health-msg {:flex 1 :font-size "14px"}]
    [:.health-act
     {:color warning-yellow :text-transform :uppercase :font-weight 600 :font-size "12px"
      :text-decoration :underline :cursor :pointer :white-space :nowrap :padding-left "8px"}]
    ;; broken tier = red (reserved)
    [:.health-broken
     [:.health-rail {:background-color broken-red}]
     [:.health-ico {:color broken-red}]
     [:.health-act {:color broken-red}]]
    (at-media {:prefers-reduced-motion "reduce"} [:.health-flash {:animation :none}])

    [:.link-button
     {:color button-color
      :border :none
      :background-color :transparent
      :text-transform :uppercase
      :cursor :pointer
      :font-size "12px"
      :border-radius "5px"
      :padding "10px 15px"
      :text-decoration :underline}]

    [:.link-button.disabled
     {:opacity 0.5
      :cursor :not-allowed}]

    [:.field
     {:margin-top "30px"}]

    [:.field-label
     {:font-size "14px"}]

    [:.personality-label
     {:font-size "18px"}]

    [:.input
     {:background-color :transparent
      :color :white
      :border "1px solid white"
      :border-radius "5px"
      :margin-top "5px"
      :display :block
      :padding "10px"
      :width "100%"
      :box-sizing :border-box
      :font-size "14px"}]

    [:.checkbox-parent
     {:display :flex
      :padding "11px 0"
      :align-items :center}]

    [:.checkbox
     {:width "16px"
      :height "16px"
      :box-shadow "0 1px 0 0 #f0a100"
      :background-color :white
      :cursor :pointer}

     [:.fa-check
      {:font-size "14px"
       :margin "1px"}]]

    [:.checkbox.checked.disabled
     {:background-color "rgba(255, 255, 255, 0.37)"
      :cursor :not-allowed}]

    [:.checkbox-text
     {:margin-left "5px"}]

    ;; Character filter bar — scoped styles for dropdowns and checkboxes
    [:.char-filter-bar
     [:.filter-dropdown
      {:position :absolute
       :background-color "#313A4D"
       :padding "6px 4px"
       :top "100%"
       :margin-top "4px"
       :border "1px solid rgba(255,255,255,0.15)"
       :border-radius "4px"
       :max-height "300px"
       :overflow-y :auto
       :font-weight :normal
       :font-size "14px"
       :z-index 200
       :box-shadow "0 4px 12px rgba(0,0,0,0.4)"}]
     [:.filter-dropdown-item
      {:padding "6px 10px"
       :border-radius "3px"
       :cursor :pointer}]
     [:.filter-dropdown-item:hover
      {:background-color "rgba(255,255,255,0.08)"}]
     [:.checkbox
      {:width "14px"
       :height "14px"
       :min-width "14px"
       :flex-shrink 0}
      [:.fa-check
       {:font-size "12px"}]]
     [:.flex.pointer
      {:align-items :center
       :gap "8px"}]]

    [:#selection-stepper
     {:transition "top 2s ease-in-out"
      :width "240px"
      :position :relative
      :top 0}]

    [:.selection-stepper-inner
     {:position :absolute}]

    [:.selection-stepper-main
     {:width "200px"
      :border "1px solid white"
      :border-radius "5px"
      :padding "10px"
      :background-color "#1a1e28"
      :box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"}]

    [:.selection-stepper-title
     {:font-size "18px"
      :color "#f0a100"}]

    [:.selection-stepper-help
     {:font-size "14px"
      :font-weight 100}]

    [:.selection-stepper-footer
     {:justify-content :flex-end}]

    [:.option-header
     {:display :flex
      :justify-content :space-between
      :align-items :center}]

    ;; Clip rather than hide. overflow-x: hidden makes .app a scroll container,
    ;; and a scroll container between the sticky header and the viewport stops it
    ;; sticking at all; clip trims the same overflow without becoming one.
    [:.app
     {:overflow-x :clip}]

    [:.app.light-theme
     {:background-image "linear-gradient(182deg, #FFFFFF, #DDDDDD)"}

     ;; Both of these invert: a red that carries on near-black is washed out on
     ;; white, and the notice's own ground has to darken rather than lighten.
     [:.red
      {:color red}
      [:a :a:visited {:color red}]]
     [:.field-notice
      {:background-color "rgba(0, 0, 0, 0.05)"}]
     [:.field-remedy
      {:border "1px solid rgba(0, 0, 0, 0.18)"}]
     [:.field-notice.is-error {:color red}]
     [:.field-notice.is-warning {:color "#8a5a00"}]
     [:.field-notice.is-note {:color "#55637a"}]

     [:select
      {:font-family font-family
       :color "black";
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

     ;; light-mode badge re-tone: deeper text on a pale tint (bright amber on
     ;; white is unreadable, so compat uses a deep brown-amber). Blue ties to the
     ;; light accent (#33658A). Same meaning, values inverted for the light bg.
     [:.lib-badge-benign
      {:background-color "rgba(51,101,138,0.14)" :color "#2b567a"}
      [:.lib-dot {:background-color "#33658A"}]]
     [:.lib-badge-compat
      {:background-color "rgba(180,120,0,0.16)" :color "#8a5a00"}
      [:.lib-dot {:background-color "#b47800"}]]

     [:.builder-option-dropdown
      (merge
       {:border "1px solid #282828"
        :color "#282828"})

      [:&:active :&:focus
       {:outline :none}]]

     [:.builder-dropdown-item
      {:background-color :white
       :color "#282828"}]

     [:.sticky-header.stuck
      {:background-color :white}]

     [:table.striped
      [:tr
       [(s/& (s/nth-child :even))
        {:background-color "rgba(0, 0, 0, 0.1)"}]]]]

    ;;;; "Modal" styles
    [:.modal-container
     {:background-image "linear-gradient(to right, #d35730, #eda41e)"
      :padding ".5em 2em"}]

    [:.modal-container :.m-b-10,
     :.modal-container :.link-button
     {:font-weight "bold"}]
    
    [:.modal-container :.link-button
     {:color "#f7c257"
      ;:font-weight "bold"
      }]

    ;;;; WARNING TOOLTIP "warntip"
    [:.warntiptext
     {:width "20%"
      :margin-top "10px"
      :background-color "#d94b20"
      :color "#fff"
      :text-align "center"
      :padding "5px 0"
      :border-radius "0 0 6px 6px"
      :position "absolute"
      :border "solid 1px #e96868"
      :z-index 1}]

    [:.warntip :.warntiptext
     [:&:after
      {:content "\" \""
       :position "absolute"
       :bottom "100%"           ;; At the bottom of the tooltip
       :left "50%"
       :margin-left "-5px"
       :border-width "10px"
       :border-style "solid"
       :border-color "transparent transparent #e96868 transparent"}]]

    ;;;; CONFLICT RESOLUTION MODAL

    ;; Modal structure
    [:.conflict-backdrop
     {:position :fixed
      :top 0 :left 0 :right 0 :bottom 0
      :background "rgba(0,0,0,0.6)"
      :z-index 10001
      :display :flex
      :align-items :center
      :justify-content :center}]

    [:.conflict-modal
     {:background "#1a1e28"
      :border-radius "5px"
      :max-width "600px"
      :max-height "80vh"
      :overflow :hidden
      :display :flex
      :flex-direction :column
      :box-shadow "0 2px 6px 0 rgba(0,0,0,0.5)"}]

    [:.conflict-modal-header
     {:padding "16px 20px"
      :border-bottom "1px solid rgba(255,255,255,0.15)"
      :background "#2c3445"}]

    [:.conflict-modal-footer
     {:padding "16px 20px"
      :border-top "1px solid rgba(255,255,255,0.15)"
      :display :flex
      :justify-content :flex-end
      :gap "12px"}]

    [:.conflict-modal-body
     ;; The modal chrome is always dark (#1a1e28) in both themes, so give the body
     ;; an explicit light default — otherwise plain text (e.g. the opinionated
     ;; import summary) inherits the app's dark text color and reads dark-on-dark.
     ;; The advanced conflict cards set their own colors, so this only lifts text
     ;; that would otherwise be illegible.
     {:padding "16px 20px"
      :overflow-y :auto
      :color "rgba(255,255,255,0.85)"
      :flex 1}]

    ;; Header elements
    [:.conflict-title-icon
     {:color orange
      :font-size "18px"}]

    [:.conflict-title
     {:color orange}]

    ;; Attention-severity header (unresolved conflicts / missing fields) — speaks
    ;; the same warning-yellow language as the My Content health card, so the modal
    ;; that resolves an issue matches the banner that surfaced it. The confident
    ;; "Ready to import" summary stays brand-orange (it's resolved, not flagged).
    [:.conflict-title-icon.warn {:color warning-yellow}]
    [:.conflict-title.warn {:color warning-yellow}]

    ;; Inline "these can't coexist" note — FA triangle + yellow, matching the card.
    [:.conflict-warn-note
     {:color warning-yellow
      :font-size "11px"
      :margin "3px 0 2px"}
     [:i.fa {:margin-right "5px"}]]

    [:.conflict-subtitle
     {:color "rgba(255,255,255,0.5)"
      :margin-top "4px"}]

    [:.conflict-count
     {:color "rgba(255,255,255,0.5)"
      :margin-top "8px"}]

    ;; Conflict card — an unresolved conflict is an attention item, so its rail and
    ;; key carry the same warning-yellow used by the health card and library tint.
    [:.conflict-item
     {:background "rgba(255,255,255,0.07)"
      :border-radius "0 5px 5px 0"
      :padding "12px"
      :margin-bottom "8px"
      :border "1px solid rgba(255,255,255,0.12)"
      :border-left (str "3px solid " warning-yellow)}]

    [:.conflict-item-header
     {:margin-bottom "10px"}]

    [:.conflict-item-key
     {:color warning-yellow}]

    [:.conflict-item-type
     {:color "rgba(255,255,255,0.7)"
      :margin-left "8px"}]

    [:.conflict-item-desc
     {:color "rgba(255,255,255,0.7)"
      :margin-bottom "8px"}]

    [:.conflict-item-detail
     {:margin-left "12px"}]

    [:.conflict-source-import
     {:color cyan
      :font-weight :bold}]

    [:.conflict-source-existing
     {:color green
      :font-weight :bold}]

    [:.conflict-source-label
     {:color "rgba(255,255,255,0.5)"}]

    [:.conflict-source-origin
     {:color "rgba(255,255,255,0.35)"}]

    [:.conflict-source-row
     {:margin-bottom "6px"
      :color :white}]

    ;; Resolution options section
    [:.conflict-options
     {:margin-top "12px"
      :border-top "1px solid rgba(255,255,255,0.2)"
      :padding-top "12px"}]

    [:.conflict-options-label
     {:color "rgba(255,255,255,0.7)"
      :margin-bottom "10px"
      :text-transform :uppercase
      :letter-spacing "0.5px"
      :font-weight :bold
      :font-size "12px"}]

    ;; Radio option — base (unselected)
    [:.conflict-radio
     {:margin-bottom "8px"
      :padding "8px 8px 8px 12px"
      :background "rgba(255,255,255,0.04)"
      :border-left "3px solid rgba(255,255,255,0.1)"
      :border-radius "0 5px 5px 0"
      :cursor :pointer
      :transition "background 0.15s ease, border-color 0.15s ease"
      :color "rgba(255,255,255,0.7)"}
     [:.radio-icon
      {:color "rgba(255,255,255,0.35)"
       :font-size "16px"
       :margin-right "10px"
       :width "16px"}]]

    ;; Radio option — selected (shared)
    [:.conflict-radio.selected
     {:color "rgba(255,255,255,0.95)"}]

    ;; Radio option — rename variant (cyan)
    [:.conflict-radio-rename.selected
     {:border-left (str "3px solid " cyan)
      :background (str cyan "18")}
     [:.radio-icon
      {:color cyan}]]

    ;; Radio option — keep variant (orange)
    [:.conflict-radio-keep.selected
     {:border-left (str "3px solid " orange)
      :background (str orange "18")}
     [:.radio-icon
      {:color orange}]]

    ;; Radio option — skip variant (purple)
    [:.conflict-radio-skip.selected
     {:border-left (str "3px solid " purple)
      :background (str purple "18")}
     [:.radio-icon
      {:color purple}]]

    ;; Code block in rename option
    [:.conflict-code
     {:background "rgba(0,0,0,0.3)"
      :padding "3px 8px"
      :border-radius "3px"
      :margin-left "6px"
      :color cyan
      :font-weight :bold}]

    ;; Export warning modal reuses conflict-backdrop, conflict-modal,
    ;; conflict-modal-header, conflict-modal-footer, conflict-modal-body

    [:.export-issue-type
     {:color "rgba(255,255,255,0.7)"
      :margin-bottom "6px"
      :font-weight :bold}]

    [:.export-issue-item
     {:color "rgba(255,255,255,0.5)"
      :font-size "12px"
      :margin-bottom "4px"}]

    [:.export-issue-name
     {:color "rgba(255,255,255,0.8)"}]

    [:.export-issue-missing
     {:color orange
      :margin-left "8px"}]

    [:.export-edit-row
     {:display :flex
      :align-items :center
      :margin-bottom "4px"
      :gap "8px"}]

    [:.export-edit-label
     {:color "rgba(255,255,255,0.5)"
      :font-size "11px"
      :min-width "55px"
      :text-transform :uppercase}]

    [:.export-edit-input
     {:background "rgba(255,255,255,0.1)"
      :border "1px solid rgba(255,255,255,0.2)"
      :border-radius "3px"
      :color :white
      :padding "4px 8px"
      :font-size "12px"
      :flex 1}]

    [:.export-edit-select
     {:background "#2c3445"
      :border "1px solid rgba(255,255,255,0.2)"
      :border-radius "3px"
      :color :white
      :padding "4px 8px"
      :font-size "12px"}]

    [:.export-edit-select.unfilled
     {:border-color "#f0a100"}]

    ;; A required builder field left empty when a save was attempted. Same amber
    ;; cue as the export modal's unfilled dropdowns, for consistency.
    [:.builder-field-unfilled
     {:border "2px solid #f0a100 !important"
      :background-color "rgba(240,161,0,0.10) !important"
      :border-radius "3px"}]

    ;; A required builder field that was filled but rejected (e.g. a name that
    ;; doesn't start with a letter). Red, to read as "wrong" rather than "empty".
    [:.builder-field-invalid
     {:border "2px solid #d9534f !important"
      :background-color "rgba(217,83,79,0.10) !important"
      :border-radius "3px"}]

    [:.export-bug-toggle
     {:cursor :pointer
      :color "rgba(255,255,255,0.2)"
      :font-size "14px"
      :padding "4px"
      :transition "color 0.2s ease"}]

    [:.export-bug-toggle:hover
     {:color "rgba(255,255,255,0.5)"}]

    [:.export-bug-toggle.active
     {:color "rgba(255,255,255,0.5)"}]

    [:.export-as-is-link
     {:color "rgba(255,255,255,0.35)"
      :font-size "11px"
      :text-decoration :underline
      :cursor :pointer
      :margin-left "4px"}]

    ;; ── Export busy page ────────────────────────────────────────────────────
    ;; The page a character sheet export lands on when every export slot is busy.
    ;; It is served into the download tab, where the builder's markup and scripts
    ;; are absent, so it restates the app's ground and panel rather than reusing
    ;; app layout classes. Colours are the app's own: the #app gradient, the
    ;; #1a1e28 panel, and .form-button for the button.
    [:.busy-body
     {:margin 0
      :min-height "100vh"
      :background-color "#080A0D"
      :background-image "linear-gradient(182deg, #313A4D, #080A0D)"
      :background-attachment :fixed}]

    [:.busy-wrap
     {:display :flex
      :justify-content :center
      :padding "56px 20px"}]

    [:.busy-card
     {:max-width "560px"
      :width "100%"
      :background-color "#1a1e28"
      :border-radius "5px"
      :padding "32px 36px"
      :box-shadow "0 2px 16px rgba(0,0,0,0.45)"}
     [:h1
      (merge text-color
             {:margin "0 0 14px"
              :font-size "24px"
              :font-weight 600})]
     [:p
      {:margin "0 0 16px"
       :font-size "16px"
       :line-height "1.6"
       :color "rgba(255,255,255,0.7)"}]
     ;; The countdown is the one line that changes while the page waits, so it
     ;; sits at full strength against the muted text around it.
     [:.busy-countdown
      (merge text-color
             {:font-weight 600
              :font-variant-numeric :tabular-nums})]]
];concat-bracket
   margin-lefts
   margin-tops
   widths
   font-sizes
   props
   media-queries) ;concat
);def app


   ;;);concat;app
