(ns orcpub.dnd.e5.views.pdf-options
  "PDF export and its options panel: the download form and the export handler (portrait baked,
   unnamed characters named), the full-window PDF Options sheet and every group in it."
  (:require [clojure.string :as s]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [orcpub.common :as common]
            [orcpub.components :refer [make-event-handler make-arg-event-handler]]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.emblems :as emblems]
            [orcpub.dnd.e5.events :as events]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.portrait-assets :as portrait-assets5e]
            [orcpub.dnd.e5.spell-packing :as packing]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.fork.integrations :as integrations]
            [orcpub.dnd.e5.views.builder-fields :refer [option-checkbox option-group labeled-dropdown
                                                        labeled-checkbox with-help]]))

(defn file-name-format
  "The player's file name choices with pdf-spec/file-name-defaults filling the rest. Must be
   called while rendering. Returns a map."
  []
  (merge pdf-spec/file-name-defaults @(subscribe [::char/file-name-format])))

(defn- set-option
  "A handler saving file name choice `k` as `v`."
  [k v]
  #(dispatch [::char/set-file-name-option k v]))

(defn- layout-keyword
  "Spell sheet layout dropdown value `v` as a keyword, nil for Automatic (blank)."
  [v]
  (when-not (s/blank? v) (keyword v)))

(defn file-name-group
  "The File name group for `built-char`: which parts the PDF's name shows, where the level goes
   and how it is written, and the name those choices give now. Returns hiccup."
  [built-char]
  (let [fmt (file-name-format)
        ;; called, not rendered as [tick ...]: a fn made each render would remount as a new component type
        tick (fn [label k help] [option-checkbox label (get fmt k) (set-option k (not (get fmt k))) help])
        nm (char/character-name built-char)
        preview (pdf-spec/file-name-for built-char (if (s/blank? nm) "(random name)" nm) fmt)]
    [option-group "File name"
     (tick "Player name" :player? "The player name from the Description step.")
     (tick "Character name" :character? "A character with no name is given one when exported.")
     (tick "Class" :class? "Each class, in the order they were taken.")
     (when (:class? fmt)
       [:div.option-nested (tick "Subclass" :subclass? "Written after its class, in brackets.")])
     (tick "Level" :level? "Each class's level, or the total when classes are left out.")
     (when (:level? fmt)
       [:div.option-nested.flex.flex-wrap
        [:div.m-r-10.m-b-10
         [labeled-dropdown "Level goes"
          {:items [{:title "After the class" :value false} {:title "Before the class" :value true}]
           :value (boolean (:level-first? fmt))
           :typed? true
           :on-change #(dispatch [::char/set-file-name-option :level-first? %])}]]
        [:div.m-b-10
         [labeled-dropdown "Written as"
          {:items [{:title "5" :value :plain} {:title "05" :value :padded} {:title "L5" :value :prefixed}]
           :value (:level-style fmt)
           :typed? true
           :on-change #(dispatch [::char/set-file-name-option :level-style %])}]]])
     [:div.option-note (let [safe (pdf-spec/file-safe preview)]
                         (str (if (s/blank? safe) "character" safe) ".pdf"))]]))

(defn- keep-tab-inside!
  "Keeps Tab and Shift+Tab, keydown event `e`, inside element `root`: past its last visible enabled
   control Tab goes to the first, before its first Shift+Tab goes to the last, and from outside it
   either goes in. Does nothing when `root` has no such control. Returns nil."
  [root e]
  (let [els (->> (.querySelectorAll root "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])")
                 array-seq
                 (remove #(.-disabled %))
                 (filter #(pos? (.-length (.getClientRects %)))))
        first-el (first els)
        last-el (last els)
        active (.-activeElement js/document)
        back? (.-shiftKey e)
        to (cond
             (nil? first-el) nil
             (not (.contains root active)) (if back? last-el first-el)
             (and back? (= active first-el)) last-el
             (and (not back?) (= active last-el)) first-el)]
    (when to
      (.preventDefault e)
      (.focus to))
    nil))

(defn sheet
  "PDF Options filling the window: a bar with the title and a close button, then `content`, which
   brings its own scrolling body and footer (views/print-options). Escape closes it, the close
   button takes focus on opening, and Tab stays inside it (keep-tab-inside!). Returns a component."
  [_content]
  (let [root (atom nil)
        close (atom nil)
        on-key (fn [e]
                 (case (.-key e)
                   "Escape" (dispatch [::char/hide-options])
                   "Tab" (some-> @root (keep-tab-inside! e))
                   nil))]
    (r/create-class
     {:component-did-mount (fn [_]
                             (.addEventListener js/document "keydown" on-key)
                             (some-> @close .focus))
      :component-will-unmount (fn [_] (.removeEventListener js/document "keydown" on-key))
      :reagent-render
      (fn [content]
        [:div.pdf-sheet.main-text-color {:role "dialog" :aria-modal "true" :aria-labelledby "pdf-sheet-title"
                                          :ref #(reset! root %)}
         [:div.pdf-sheet-bar
          [:h2#pdf-sheet-title.pdf-sheet-title "PDF Options"]
          [:button.pdf-sheet-close {:type "button" :aria-label "Close PDF Options"
                                    :ref #(reset! close %)
                                    :on-click #(dispatch [::char/hide-options])}
           [:i.fa.fa-times]]]
         content])})))

(defn- portrait-fields
  "The spec's portrait fields for baked `png-b64`: the pixels and the credit, which travels with
   them because the server has no layer selection. Fitted to the PDF metadata's 78 characters,
   so whole names drop rather than one being cut."
  [portrait png-b64]
  (when png-b64
    {:portrait-png png-b64
     :portrait-credit (portrait-assets5e/fit-credit
                       (portrait-assets5e/credit-names portrait)
                       #(<= (count %) 78))}))

(def portrait-wait-ms
  "How long Create PDF waits for a portrait still baking before sending the PDF without it."
  8000)

(defn- within-wait
  "`bake`, a promise of a portrait's base64 PNG, resolving to nil instead if it fails or is still
   running after portrait-wait-ms. Never rejects."
  [bake]
  (-> (js/Promise.race #js [bake (js/Promise. (fn [ok _] (js/setTimeout #(ok nil) portrait-wait-ms)))])
      (.catch (fn [_] nil))))

(defn- named-spec
  "`spec`, the export map for `built-char` (character `id`, exported with `options`), with a
   generated name in both name fields and the file name when the character has none, the way Save
   names it; the builder's character gets the same name when it is the one exported
   (::char/fill-blank-name). Returns the spec."
  [spec built-char id options]
  (if-not (s/blank? (:character-name spec))
    spec
    (let [n (events/generate-random-name built-char)]
      (dispatch [::char/fill-blank-name id n])
      (assoc spec :character-name n :character-name-2 n
             :file-name (pdf-spec/file-name-for built-char n (:file-name-format options))))))

(defn- making-pdf-tab!
  "Opens an empty tab named `tab` and writes a \"Making your PDF\" notice in it, which the PDF
   replaces when it arrives. Built with DOM calls: the tab inherits this page's CSP, which refuses
   inline style. Returns `tab`, or \"_blank\" when the browser would not open it."
  [tab]
  (if-let [w (.open js/window "" tab)]
    (do (try
          (let [d (.-document w)
                b (.-body d)
                p (.createElement d "p")
                small (.createElement d "p")]
            (set! (.-title d) "Making your PDF...")
            (doto (.-style b)
              (aset "margin" "0") (aset "minHeight" "100vh") (aset "display" "flex")
              (aset "flexDirection" "column") (aset "alignItems" "center")
              (aset "justifyContent" "center") (aset "background" "#313A4D")
              (aset "color" "#fff") (aset "fontFamily" "sans-serif"))
            (set! (.-textContent p) "Making your PDF...")
            (aset (.-style p) "fontSize" "20px")
            (set! (.-textContent small)
                  "It opens in this tab when it's ready. Spell cards and a spellbook take a few seconds.")
            (aset (.-style small) "opacity" "0.7")
            (.appendChild b p)
            (.appendChild b small))
          (catch :default _ nil))
        tab)
    "_blank"))

(defn- export-failed!
  "Logs export error `e` and tells the player the PDF couldn't be made. Returns nil."
  [e]
  (js/console.error "PDF export could not start:" e)
  (dispatch [:show-error-message
             "This character's PDF couldn't be made. Try again, or tell us with Feedback/Bug Reports at the bottom of the page."])
  nil)

(defn export-pdf
  "An onClick handler that submits the PDF form for `built-char`, with its portrait when one can be
   drawn, into a tab that says the PDF is being made until it arrives (making-pdf-tab!). Nothing
   optional stops the export: a missing, undrawable, failing or slow portrait sends the PDF without
   it. If the PDF's data cannot be made, the player is told.
   GOTCHA: the tab must open inside the click or a popup blocker stops it; the form is submitted
   into it a beat later, and after an unfinished bake when the bake resolves or the wait runs out."
  [built-char id plugin-data & [options]]
  (fn [_]
    (try
      (let [field (.getElementById js/document "fields-input")
            form (.getElementById js/document "download-form")
            spec (named-spec (pdf-spec/make-spec built-char id options plugin-data) built-char id options)
            portrait (char/portrait built-char)
            ;; Into an opened tab a beat later, so it paints its notice before the navigation
            ;; starts; with no tab (a blocked popup), at once, while still inside the click.
            submit! (fn [png-b64 target]
                      (aset field "value" (str (merge spec (try (portrait-fields portrait png-b64)
                                                                (catch :default _ nil)))))
                      (aset form "target" target)
                      (if (= "_blank" target)
                        (.submit form)
                        (js/setTimeout #(.submit form) 150)))
            ready (when (portrait-assets5e/drawable? portrait) (portrait/baked-png portrait))
            target (making-pdf-tab! (str "orcpub-pdf-" (rand-int 1000000)))]
        (cond
          (not (portrait-assets5e/drawable? portrait)) (submit! nil target)
          ready (submit! (first ready) target)
          :else (if-let [bake (try (portrait/bake-ahead! portrait) (catch :default _ nil))]
                  (-> (within-wait bake) (.then #(submit! % target)) (.catch export-failed!))
                  (submit! nil target))))
      (catch :default e
        (export-failed! e)))))

(defn bake-portrait
  "Bakes the character's composed portrait on mount and whenever it or the artist credits change,
   so export can submit without waiting. Renders nothing."
  [_portrait]
  (let [bake #(portrait/bake-ahead! (second (r/argv %)))]
    (r/create-class
     {:component-did-mount bake
      :component-did-update bake
      :reagent-render (fn [_] @(subscribe [:portrait/artist-overrides]) nil)})))

(defn download-form
  "The hidden form export-pdf fills and submits to /character.pdf; `built-char` is unused. Returns
   hiccup."
  [built-char]
  [:form.download-form
   {:id "download-form"
    :action (event-utils/backend-url "/character.pdf")
    :method "POST"
    :target "_blank"}
   [:input {:type "hidden" :name "body" :id "fields-input"}]])

(defn export-pdf-fn
  "A Create PDF click handler: exports `built-char` (character `id`) with `plugin-data` and the
   panel's choices, each passed as its own argument, then closes the panel. Returns a function."
  [built-char
   id
   plugin-data
   print-character-sheet?
   print-spell-cards?
   print-prepared-spells?
   print-large-abilities?
   print-character-sheet-style?
   print-spell-card-dc-mod?
   print-card-back-logo?
   card-back-logo-faded?
   print-bw?
   bw-faded?
   print-magic-item-cards?
   spell-layout
   spellbook-options]
  #(let [export-fn (export-pdf built-char
                               id
                               plugin-data
                               (merge
                                spellbook-options
                                {:print-character-sheet? print-character-sheet?
                                :print-spell-cards? print-spell-cards?
                                :print-prepared-spells? print-prepared-spells?
                                :print-large-abilities? print-large-abilities?
                                :print-character-sheet-style? print-character-sheet-style?
                                :print-spell-card-dc-mod? print-spell-card-dc-mod?
                                :print-card-back-logo? print-card-back-logo?
                                :card-back-logo-faded? card-back-logo-faded?
                                :print-bw? print-bw?
                                :bw-faded? bw-faded?
                                :print-magic-item-cards? print-magic-item-cards?
                                :spell-layout spell-layout}))]
     (export-fn)
     (dispatch [::char/hide-options])))

(def export-pdf-handler (memoize export-pdf-fn))

(defn print-button-style
  "Create PDF's inline style: dimmed and unclickable unless `print-button-enabled`. Returns a map."
  [print-button-enabled]
  (if print-button-enabled
    {}
    {:opacity 0.5
     :cursor :not-allowed
     :pointer-events "none"}))


(defn capture-images
  "Reads the character's pictures (`urls`) in the browser on mount, so their bytes are in
   hand before export is clicked. Renders nothing; reads on mount only (no update hook).
   GOTCHA: kept off the click handler: the export is a synchronous submit into a new tab, and
   an await before .submit() spends the user activation that keeps it from being blocked."
  [_urls]
  (let [ask (fn [this]
              (doseq [url (second (r/argv this))
                      :when (seq url)]
                (dispatch [::char/capture-image url])))]
    (r/create-class
     {:component-did-mount ask
      :reagent-render (fn [_] nil)})))

(defn emblem-picker
  "One casting class's emblem, with the icons it can be swapped for behind a toggle. The
   choice lasts the session, like the rest of the PDF options."
  [_class-kw _class-name _chosen]
  (let [open? (r/atom false)]
    (fn [class-kw class-name chosen]
      (let [icon (emblems/icon-for class-kw chosen)]
        [:div.m-b-5
         [:div.flex.align-items-c
          [:img.emblem-icon {:src (str "/image/emblems/" icon ".svg") :alt ""}]
          [:span.m-l-5 class-name]
          [:span.orange.underline.pointer.f-s-12.m-l-10
           {:on-click #(swap! open? not)}
           (if @open? "Done" "Change")]]
         (when @open?
           (into [:div.emblem-choices]
                 (for [[nm author] (emblems/options class-kw)]
                   [:button.emblem-choice
                    {:key nm
                     :type "button"
                     :class (when (= nm icon) "selected")
                     :title (str (s/replace nm "-" " ") " by " (s/replace author "-" " "))
                     :on-click #(dispatch [::char/set-spellbook-emblem class-kw nm])}
                    [:img.emblem-icon {:src (str "/image/emblems/" nm ".svg") :alt (s/replace nm "-" " ")}]])))]))))

(def spellbook-layouts
  "The spellbook layouts PDF Options offers, as dropdown items."
  [{:title "Spellbook: full text, two columns" :value "book"}
   {:title "Ledger: one row a spell" :value "ledger"}
   {:title "Prep sheet: a box to tick per spell" :value "prep"}])

(def spellbook-option-handler
  "The on-change handler setting spellbook option `k` from a dropdown value; blank clears it."
  (memoize
   (fn [k]
     #(dispatch [::char/set-spellbook-option k (when-not (s/blank? %) (keyword %))]))))

(defn spellbook-dropdown
  "A labelled dropdown for spellbook option `k`, offering `items` with `value` selected."
  [label k items value]
  [:div.m-b-10
   [labeled-dropdown label
    {:items items
     :value (name value)
     :on-change (spellbook-option-handler k)}]])

(defn print-options
  "The PDF Options body and footer for `built-char` (character `id`): the option groups, scrolling
   inside pdf-options/sheet, then Cancel and Create PDF. Returns hiccup."
  [id built-char]
  (let [print-character-sheet? @(subscribe [::char/print-character-sheet?])
        print-spell-cards? @(subscribe [::char/print-spell-cards?])
        print-magic-item-cards? @(subscribe [::char/print-magic-item-cards?])
        print-prepared-spells? @(subscribe [::char/print-prepared-spells?])
        print-large-abilities? @(subscribe [::char/print-large-abilities?])
        print-character-sheet-style? @(subscribe [::char/print-character-sheet-style?])
        print-spell-card-dc-mod? @(subscribe [::char/print-spell-card-dc-mod?])
        print-card-back-logo? @(subscribe [::char/print-card-back-logo?])
        card-back-logo-faded? @(subscribe [::char/card-back-logo-faded?])
        print-bw? @(subscribe [::char/print-bw?])
        bw-faded? @(subscribe [::char/bw-faded?])
        spell-layout @(subscribe [::char/spell-layout])
        spellbook-options @(subscribe [::char/spellbook-options])
        print-spellbook? (:print-spellbook? spellbook-options)
        ;; Read off built-char, not [::char/image-url id]: a character still being
        ;; built has no id yet, and the id-keyed subscription answers nil for it.
        ;; This is the same accessor pdf-spec uses to put the URL in the export.
        image-url (char/image-url built-char)
        faction-image-url (char/faction-image-url built-char)
        image-bytes @(subscribe [::char/image-bytes])
        plugin-data {:spells-map @(subscribe [::spells/spells-map])
                     :plugin-spells-map @(subscribe [::spells/plugin-spells-map])
                     :language-map @(subscribe [::langs/language-map])
                     :all-weapons-map @(subscribe [::mi/all-weapons-map])
                     :all-magic-items-map @(subscribe [::mi/all-magic-items-map])
                     :current-armor-class @(subscribe [::char/current-armor-class id])
                     :image-bytes image-bytes}
        has-spells? (seq (char/spells-known built-char))
        ;; Only a multiclass caster on a style that can be renumbered has a
        ;; choice to make, so the control is not shown to anyone else.
        casting-classes (when has-spells?
                          (pdf-spec/casting-classes built-char
                                                    (:spells-map plugin-data)))
        packable? (and (> (count casting-classes) 1)
                       (packing/packing-supported? print-character-sheet-style?)
                       ;; Not offered when the columns cannot hold the character.
                       ;; A Wizard 20 beside a Cleric leaves no column a Cleric
                       ;; fits, and packing anyway would print without it.
                       (packing/fits? print-character-sheet-style?
                                      (packing/packing-shape casting-classes)))
        ;; Exporting mid-read would send the address and let the server fetch what
        ;; the browser was already holding. A read ends either way -- capture has
        ;; its own deadline -- so this waits at most that long.
        reading-pictures? (boolean (some #(= :pending (get image-bytes %))
                                         (remove nil? [image-url faction-image-url])))
        print-button-enabled (and (not reading-pictures?)
                                  (not (or (nil? print-character-sheet-style?)
                                           (= (str print-character-sheet-style?) "NaN"))))
        ]
    ;; The body scrolls inside sheet; the footer under it keeps Create PDF in view.
    [:<>
     [:div.pdf-sheet-body
      [capture-images [image-url faction-image-url]]
      [bake-portrait (char/portrait built-char)]

      [:div.pdf-options-grid
       ;; Grouped by what a setting changes: the sheet, the cards behind it, then
       ;; how either is inked. The known / prepared choice is a sheet setting.
       [option-group "Character Sheet"
        [:div.m-b-10
         [labeled-dropdown
          "Sheet style"
          {:items (into [{:title "Select" :value " "}]
                        (integrations/sheet-styles @(subscribe [:user-tier])))
           :value print-character-sheet-style?
           :on-change (make-arg-event-handler ::char/set-print-character-sheet-style? js/parseInt)}]]
        [integrations/pdf-options-slot @(subscribe [:user-tier])]
        [option-checkbox
         "Print Abilities Large (and Bonuses Small)"
         print-large-abilities?
         (make-event-handler ::char/toggle-large-abilities-print)
         (str "The sheet prints the ability score big and its modifier small. "
              "Tick this to swap them, so the number you roll with is the one "
              "you can read across the table.")]
        (when packable?
          [:div.m-b-10
           [:div
            [labeled-dropdown
             "Spell Sheet Layout"
             {:items [{:title "Automatic" :value ""}
                      {:title "One column per class" :value "packed"}
                      {:title "One page per class" :value "per-class"}]
              :value (if spell-layout (name spell-layout) "")
              :on-change (make-arg-event-handler ::char/set-spell-layout layout-keyword)}]]
           ;; What the current setting will actually print, always on: this one is
           ;; the state of the build rather than a fixed explanation, so it is not
           ;; behind a ?. Set like the ? lines so the two read as one kind of note.
           [:div.option-note
            (if (= :per-class spell-layout)
              (str (count casting-classes) " spell pages, one per class")
              (str "One page, a column each for "
                   (s/join ", " (map :class casting-classes))))]])]

       ;; Which spells print, and in what order: the first for the sheet and the cards, the second
       ;; for the cards and the spellbook, so neither belongs to one of those groups.
       (when has-spells?
         [option-group "Spells"
          [:div.m-b-10
           [with-help
            [:div.flex.align-items-c.m-b-5
             [:span.f-w-b "Spells Printed"]]
            (str "Which spells go on the sheet and the cards. Known is every "
                 "spell the character has; Prepared is only the ones marked "
                 "prepared today, which is what a Cleric or a Wizard casts from.")]
           [:div.flex
            [:div.pointer
             {:on-click (make-event-handler ::char/toggle-known-spells-print)}
             [labeled-checkbox "Known" (not print-prepared-spells?)]]
            [:div.m-l-20.pointer
             {:on-click (make-event-handler ::char/toggle-known-spells-print)}
             [labeled-checkbox "Prepared" print-prepared-spells?]]]]
          (when (or print-spellbook? print-spell-cards?)
            [spellbook-dropdown "Spell order" :spell-order
             [{:title "By level, then name" :value "level"}
              {:title "By name" :value "alpha"}]
             (or (:spell-order spellbook-options) :level)])
          (when (or print-spellbook? print-spell-cards?)
            [:div.option-note
             (str "Orders the "
                  (cond (and print-spellbook? print-spell-cards?) "spellbook and the cards"
                        print-spellbook? "spellbook"
                        :else "cards")
                  " within each class.")])])

       [option-group "Cards"
        (when has-spells?
          [option-checkbox
           "Print Spell Cards"
           print-spell-cards?
           (make-event-handler ::char/toggle-spell-cards-print)
           (str "Adds a page of cut-out cards, one per spell, with its range, "
                "duration and description. Printed after the sheet.")])
        (when (and has-spells? print-spell-cards?)
          [:div.option-nested
           [option-checkbox
            "Print Spell DC and MOD"
            print-spell-card-dc-mod?
            (make-event-handler ::char/toggle-spell-cards-by-dc-mod)
            (str "Prints this character's save DC and attack bonus on each card, "
                 "so the numbers are on the card being held rather than back on "
                 "the sheet.")]])
        [option-checkbox
         "Print Magic Item Cards"
         print-magic-item-cards?
         (make-event-handler ::char/toggle-magic-item-cards-print)
         (str "The same cards for the magic items the character carries, "
              "attunement and charges included.")]]

       (when has-spells?
         (let [opt (fn [k] (or (get spellbook-options k)
                               (get pdf-spec/spellbook-defaults
                                    (keyword (s/replace (name k) #"^spellbook-" "")))))]
           [option-group "Spellbook"
            [option-checkbox
             "Print Spellbook"
             print-spellbook?
             (make-event-handler ::char/toggle-spellbook-print)
             (str "Adds pages listing every printed spell by class, with its save DC and "
                  "attack on every page. Fewer pages than cards and nothing to cut out.")]
            (when print-spellbook?
              [:div.option-nested
               [spellbook-dropdown "Layout" :spellbook-layout spellbook-layouts (opt :spellbook-layout)]
               (when (> (count casting-classes) 1)
                 [spellbook-dropdown "New class starts" :spellbook-class-break
                  [{:title "Straight after the last" :value "runon"}
                   {:title "On a new page" :value "page"}]
                  (opt :spellbook-class-break)])
               [spellbook-dropdown "Level tabs" :spellbook-tabs
                [{:title "In the page header" :value "head"}
                 {:title "Down the side (narrows the page)" :value "inset"}
                 {:title "Off" :value "off"}]
                (opt :spellbook-tabs)]
               [:div.f-w-b.m-b-5 "Class emblems"]
               (for [{:keys [class]} casting-classes
                     :let [class-kw (common/name-to-kw (str class))]]
                 ^{:key class}
                 [emblem-picker class-kw (str class)
                  (get-in spellbook-options [:spellbook-emblems class-kw])])])]))

       ;; Both card kinds are inked the same way, so this group follows either --
       ;; gating it on spell cards alone hid it from anyone printing only items.
       (when (or print-spell-cards? print-magic-item-cards?)
         [option-group "Appearance"
          [option-checkbox
           "Printer-friendly (black & white)"
           print-bw?
           (make-event-handler ::char/toggle-print-bw)
           (str "Drops the colour from the cards so they cost less to print and "
                "stay legible on a mono printer.")]
          ;; Under B&W: default is solid-black icons with white-halo labels;
          ;; opt into faded grayscale icons for a softer look.
          (when print-bw?
            [:div.option-nested
             [option-checkbox
              "Faded grayscale icons (else solid black)"
              bw-faded?
              (make-event-handler ::char/toggle-bw-faded)
              (str "Sets the card icons as pale grey behind the text instead of "
                   "solid black beside it.")]])
          [option-checkbox
           "Print logo on card backs"
           print-card-back-logo?
           (make-event-handler ::char/toggle-print-card-back-logo)
           (str "Puts a logo on the reverse of every card, so a printed sheet cut "
                "up and shuffled still has a back.")]
          ;; Treatment only matters when the logo is on AND B&W isn't forcing black.
          (when (and print-card-back-logo? (not print-bw?))
            [:div.option-nested
             [option-checkbox
              "Faded color (else solid black)"
              card-back-logo-faded?
              (make-event-handler ::char/toggle-card-back-logo-faded)
              (str "Prints the back logo as a pale colour wash rather than in "
                   "solid black.")]])])
       [file-name-group built-char]]

      [:div.t-a-r.m-t-10
       [:div.f-w-b.m-b-5 "Other PDFs"]
       [:a.orange {:href "/dnld/5eActionsReferencePage.pdf" :target "_blank"} "5e Actions Reference"]]]
     [:div.pdf-sheet-foot
      (when reading-pictures?
        [:div.f-s-12 "Reading the character's picture..."])
      [:button.link-button.underline {:type "button" :on-click (make-event-handler ::char/hide-options)}
       "Cancel"]
      [:button.form-button.p-10
       {:type "button"
        :style (print-button-style print-button-enabled)
        :on-click (export-pdf-handler built-char
                                      id
                                      plugin-data
                                      print-character-sheet?
                                      print-spell-cards?
                                      print-prepared-spells?
                                      print-large-abilities?
                                      print-character-sheet-style?
                                      print-spell-card-dc-mod?
                                      print-card-back-logo?
                                      card-back-logo-faded?
                                      print-bw?
                                      bw-faded?
                                      print-magic-item-cards?
                                      spell-layout
                                      (assoc spellbook-options :file-name-format
                                             (file-name-format)))}
       "Create PDF"]]]))

(defn live-print-options
  "print-options for the character as it is each time it draws: the builder's character when
   `builder?`, else saved character `id`, so a change while the panel is open is what gets exported."
  [id builder?]
  [print-options id (if builder?
                      @(subscribe [:built-character])
                      @(subscribe [::char/built-character id]))])

(defn make-print-handler
  "An on-click handler opening PDF Options for character `id`; `builder?` when it is the character
   being built. The panel is stored as which character to read, never as the character itself."
  [id builder?]
  #(dispatch
    [::char/show-options
     [live-print-options id builder?]]))
