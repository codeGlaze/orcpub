(ns orcpub.dnd.e5.views.inventory
  "The builder's inventory screens: the Weapons, Armor, Equipment, magic item and Treasure lists
   (pick, quantity, carried, custom items) and the Kits block over Equipment. The home for inventory
   UI; rules are in `orcpub.dnd.e5.inventory`. plan-inventory-kits.md on agents/develop."
  (:require [clojure.string :as s]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [orcpub.common :as common]
            [orcpub.components :as comps]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.inventory :as inventory]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.views.notifications :as notifications]))

(defn set-custom-item-name-fn
  "Handler renaming custom item `i` of `selection-key` to the value it is given."
  [selection-key i]
  #(dispatch [::char5e/set-custom-item-name selection-key i %]))

(def set-custom-item-name (memoize set-custom-item-name-fn))

(defn inventory-item
  "One inventory row: carried checkbox, name (an input for a player's own item), info toggle,
   quantity box and remove mark; `:note` adds a line under the row. Takes one map of those parts."
  []
  (let [expanded? (r/atom false)]
    (fn [{:keys [selection-key
                 item-key
                 item-name
                 item-qty
                 item-description
                 equipped?
                 user-created?
                 i
                 qty-input-width
                 check-fn
                 qty-change-fn
                 remove-fn
                 note]}]
      [:div.p-5
       [:div.f-w-b.flex.align-items-c
        [:div.pointer.m-l-5.m-r-5
         {:on-click check-fn}
         (comps/checkbox equipped? false)]
        (if user-created?
          [comps/input-field
           :input
           item-name
           (set-custom-item-name selection-key i)
           {:class "input m-t-0"}]
          [:div.flex-grow-1 item-name])
        (when item-description [:div.w-60 [comps/show-info-button expanded?]])
        [comps/int-field
         item-qty
         qty-change-fn
         {:class (str "input m-l-5 m-t-0 w-" (or qty-input-width 60))}]
        [:i.fa.fa-minus-circle.orange.f-s-16.m-l-5.pointer
         {:on-click remove-fn}]]
       (when note [:div.f-s-11.f-w-n.m-l-30 note])
       (when @expanded? [:div.m-t-5 item-description])])))

(defn add-inventory-item-fn
  "Change handler adding the item whose key is the event's value to selection `key`."
  [key]
  (fn [e]
     (let [value (.. e -target -value)
           item-key (keyword value)]
       (dispatch [:add-inventory-item key item-key]))))

(def add-inventory-item (memoize add-inventory-item-fn))

(defn inventory-option-selected-fn
  "Predicate: whether an option's key or db id is in `selected-keys`."
  [selected-keys]
  #(or (selected-keys (::t/key %))
       (selected-keys (:db/id %))))

(def inventory-option-selected? (memoize inventory-option-selected-fn))

(defn name-and-key
  "An option as {:name :key}, the key falling back to its db id."
  [{:keys [:db/id ::t/name ::t/key]}]
  {:name name
   :key (or key id)})

;;; selection creator for character builder
#_ ;; DEPRECATED 2026-09-06 -- superseded by inventory-combobox, which gets the top layer,
   ;; light dismiss, Escape and focus from popover="auto" instead of the backdrop and
   ;; keydown listener below. Unreferenced; remove once the combobox ships cleanly.
(defn inventory-picker
  "Compact 'Add item' control: a button that opens a small search overlay, lists up to 12
   matches, and closes on pick. Nothing renders until it is opened.
   Open state is a local r/atom, not app-db: transient UI state that nothing else reads."
  []
  (let [open? (r/atom false)
        query (r/atom "")]
    (fn [key options selected-keys]
      (let [items   (common/aloof-sort-by
                     :name
                     (sequence (comp (remove (inventory-option-selected? selected-keys))
                                     (map name-and-key))
                               options))
            q       (s/lower-case (s/trim @query))
            matches (if (s/blank? q)
                      items
                      (filterv #(s/includes? (s/lower-case (str (:name %))) q) items))
            ;; Only ever build a screenful. Search is the way to reach the rest.
            shown   (take 12 matches)
            close!  (fn [] (reset! open? false) (reset! query ""))]
        [:div.inv-picker
         [:button.inv-picker-btn
          {:on-click #(swap! open? not)}
          [:i.fa.fa-plus.m-r-5]
          (str "Add" (when (seq items) (str " (" (count items) ")")))]
         (when @open?
           [:div
            ;; backdrop closes on any outside click without a document-level listener
            [:div.inv-picker-backdrop {:on-click close!}]
            [:div.inv-picker-pop
             [:input.inv-picker-search
              {:auto-focus true
               :value @query
               :placeholder "Search…"
               :on-change #(reset! query (.. % -target -value))
               :on-key-down #(when (= 27 (.-keyCode %)) (close!))}]
             (if (empty? matches)
               [:div.inv-picker-empty (str "Nothing matches “" @query "”")]
               [:div.inv-picker-list
                (doall
                 (for [{item-key :key item-name :name} shown]
                   ^{:key item-key}
                   [:div.inv-picker-row
                    {:on-click (fn []
                                 (dispatch [:add-inventory-item key item-key])
                                 (close!))}
                    item-name]))])
             (when (> (count matches) (count shown))
               [:div.inv-picker-more
                (str (count matches) " matches — keep typing to narrow")])]])]))))

(defn inventory-datalist
  "Native filtering dropdown: a text input whose suggestions come from a <datalist>.
   The browser filters and paints the list, so the <option>s are a data source, never laid
   out. GOTCHA: the input is themed; the dropdown is drawn by the browser and not styleable."
  []
  (let [value (r/atom "")]
    (fn [key options selected-keys]
      (let [items    (common/aloof-sort-by
                      :name
                      (sequence (comp (remove (inventory-option-selected? selected-keys))
                                      (map name-and-key))
                                options))
            by-name  (into {} (map (juxt :name :key)) items)
            list-id  (str "inv-list-" (clojure.core/name key))]
        [:div.inv-datalist
         [:input.inv-datalist-input
          {:list list-id
           :value @value
           :placeholder (str "Add an item… (" (count items) ")")
           :on-change (fn [e]
                        (let [v (.. e -target -value)]
                          ;; A pick from the list arrives as a complete, exact name; typing
                          ;; arrives partial. Only dispatch on an exact hit, then clear.
                          (if-let [item-key (by-name v)]
                            (do (dispatch [:add-inventory-item key item-key])
                                (reset! value ""))
                            (reset! value v))))}]
         [:datalist {:id list-id}
          (doall
           (for [{item-name :name item-key :key} items]
             ^{:key item-key}
             [:option {:value item-name}]))]]))))

(defn- show-popover!
  "Opens the popover element `id` when it is closed; does nothing where popovers are unsupported."
  [id]
  ;; showPopover throws if it is already open, and the attribute is unsupported on old
  ;; engines -- both are non-fatal, so the control degrades to a plain filtered list.
  (when-let [el (.getElementById js/document id)]
    (try (when-not (.matches el ":popover-open") (.showPopover el))
         (catch :default _ nil))))

(defn- hide-popover!
  "Closes the popover element `id` when it is open."
  [id]
  (when-let [el (.getElementById js/document id)]
    (try (when (.matches el ":popover-open") (.hidePopover el))
         (catch :default _ nil))))

(defn highlight-match
  "Bold the part of the name the query matched. Standard combobox affordance: it shows WHY a
   row is in the list, which matters when a substring match lands mid-word.

   `q` must already be lower-cased -- the caller lower-cases once per render rather than once
   per row. Plain substring search, not a regex, so `+1` and `(` are literals. Returns the
   name unchanged when there is no match, so callers can render the result directly."
  [item-name q]
  (let [nm (str item-name)
        i  (when-not (s/blank? q) (s/index-of (s/lower-case nm) q))]
    (if-not i
      item-name
      (let [end (+ i (count q))]
        [:span
         (subs nm 0 i)
         [:span.inv-combo-hit (subs nm i end)]
         (subs nm end)]))))

(defn- scroll-active-into-view!
  "After the next render, scrolls popover `pop-id`'s highlighted row into view."
  [pop-id]
  ;; Runs after the re-render that moved the highlight, hence the rAF.
  (js/requestAnimationFrame
   (fn []
     (some-> (.getElementById js/document pop-id)
             (.querySelector ".inv-combo-row.active")
             (.scrollIntoView #js {:block "nearest"})))))

(defn inventory-combobox
  "Filter-and-pick dropdown for selection `key`: a `popover=\"auto\"` list anchored under its input
   (browser top layer, light dismiss, Escape), every match rendered, rows mounted only while open.
   Options: `:preset`, an atom a caller fills with {:label :keys :notes :open? true} to list just
   those keys under a removable label (× or a pick clears it); `:on-pick`, taking a picked key
   instead of `:add-inventory-item`; `:placeholder`, the box's text before its count."
  []
  (let [query     (r/atom "")
        active    (r/atom -1)
        open?     (r/atom false)
        on-toggle (fn [e] (reset! open? (= "open" (.-newState e))))
        el*       (atom nil)
        ;; Defined once per instance, not per render: a fresh closure each render would make
        ;; React tear down and re-attach, adding a listener every time.
        ref-fn    (fn [el]
                    (when-let [old @el*] (.removeEventListener old "beforetoggle" on-toggle))
                    (reset! el* el)
                    (when el (.addEventListener el "beforetoggle" on-toggle)))]
    (fn [key options selected-keys & [{:keys [preset on-pick placeholder]}]]
      (let [{only :keys label :label notes :notes} (some-> preset deref)
            items    (vec (common/aloof-sort-by
                           :name
                           (sequence (if only
                                       (comp (map name-and-key) (filter #(contains? only (:key %))))
                                       (comp (remove (inventory-option-selected? selected-keys))
                                             (map name-and-key)))
                                     options)))
            kname    (clojure.core/name key)
            pop-id   (str "inv-pop-" kname)
            anchor   (str "--inv-anchor-" kname)
            q        (s/lower-case (s/trim @query))
            matches  (if (s/blank? q)
                       items
                       (filterv #(s/includes? (s/lower-case (str (:name %))) q) items))
            n        (count matches)
            pick!    (fn [item-key]
                       (if on-pick (on-pick item-key) (dispatch [:add-inventory-item key item-key]))
                       (some-> preset (reset! nil))
                       (reset! query "")
                       (reset! active -1)
                       (reset! open? false)
                       (hide-popover! pop-id))
            open!    (fn [] (reset! open? true) (show-popover! pop-id))
            move!    (fn [e delta]
                       (.preventDefault e)
                       (open!)
                       (swap! active #(-> (+ % delta) (max 0) (min (dec n))))
                       (scroll-active-into-view! pop-id))]
        (when (some-> preset deref :open?)
          (swap! preset dissoc :open?)
          (r/after-render open!))
        [:div.inv-combo
         (when label
           [:button.inv-combo-preset {:on-click #(reset! preset nil)}
            label [:i.fa.fa-times.m-l-5 {:aria-hidden true}]])
         [:input.inv-combo-input
          {:style {:anchor-name anchor}
           :value @query
           :placeholder (str (or placeholder "Filter or browse…") " (" (count items) ")")
           ;; on-click, not on-focus: focus fires on mousedown, and light dismiss then treats
           ;; that same pointer sequence as an outside click and shuts the popover again.
           ;; Measured -- opening on focus left popoverOpen=0 after a click.
           :on-click #(open!)
           :on-key-down (fn [e]
                          (case (.-key e)
                            "ArrowDown" (move! e 1)
                            "ArrowUp"   (move! e -1)
                            "Enter"     (when-let [it (nth matches @active nil)]
                                          (.preventDefault e)
                                          (pick! (:key it)))
                            nil))
           :on-change (fn [e]
                        (reset! query (.. e -target -value))
                        ;; The old highlight points into the old result list.
                        (reset! active -1)
                        (open!))}]
         [:div.inv-combo-pop
          {:id pop-id
           :popover "auto"
           :ref ref-fn
           :style {:position-anchor anchor}}
          (cond
            (not @open?) nil
            (zero? n)    [:div.inv-combo-empty (str "Nothing matches \u201c" @query "\u201d")]
            :else
            [:div.inv-combo-list
             (doall
              (map-indexed
               (fn [i {item-name :name item-key :key}]
                 ^{:key item-key}
                 [:div.inv-combo-row
                  {:class (when (= i @active) "active")
                   :on-click #(pick! item-key)}
                  (highlight-match item-name q)
                  (when-let [note (get notes item-key)] [:span.opacity-7.m-l-5 note])])
               matches))])
          (when (pos? n)
            [:div.inv-combo-hint
             [:span (str n (if (= 1 n) " item" " items"))]
             [:span.inv-combo-keys "\u2191\u2193 browse \u00b7 \u21b5 add \u00b7 esc close"]])]]))))

(defn inventory-adder
  "The control that adds items from `options` to selection `key`; `selected-keys` are shown as
   picked. `opts` as `inventory-combobox`."
  [key options selected-keys & [opts]]
  ;; Switch by changing this line. inventory-datalist is kept live: it hands the dropdown to
  ;; the OS, which is the better control on mobile if the Popover API ever proves a problem.
  ;; inventory-picker above is deprecated, not an alternative.
  [inventory-combobox key options selected-keys opts])

(defn inventory-check-fn
  "Handler toggling whether item `i` of selection `key` is carried."
  [key i]
  #(dispatch [:toggle-inventory-item-equipped key i]))

(def inventory-check (memoize inventory-check-fn))

(defn inventory-qty-change-fn
  "Handler setting the quantity of item `i` of selection `key` to the value it is given."
  [key i]
  #(dispatch [:change-inventory-item-quantity key i %]))

(def inventory-qty-change (memoize inventory-qty-change-fn))

(defn remove-inventory-item-fn
  "Handler removing item `item-key` from selection `key`."
  [key item-key]
  (fn [_]
    (dispatch [:remove-inventory-item key item-key])))

(def remove-inventory-item (memoize remove-inventory-item-fn))

(defn toggle-custom-inventory-item-equipped-fn
  "Handler toggling whether custom item `i` under `custom-equipment-key` is carried."
  [custom-equipment-key i]
  (fn [_]
    (dispatch [:toggle-custom-inventory-item-equipped custom-equipment-key i])))

(def toggle-custom-inventory-item-equipped (memoize toggle-custom-inventory-item-equipped-fn))

(defn change-custom-inventory-item-quantity-fn
  "Handler setting custom item `i`'s quantity to the value it is given."
  [custom-equipment-key i]
  (fn [qty]
    (dispatch [:change-custom-inventory-item-quantity custom-equipment-key i qty])))

(def change-custom-inventory-item-quantity (memoize change-custom-inventory-item-quantity-fn))

(defn remove-custom-inventory-item-fn
  "Handler removing the custom item called `name` under `custom-equipment-key`."
  [custom-equipment-key name]
  (fn [_]
    (dispatch [:remove-custom-inventory-item custom-equipment-key name])))

(def remove-custom-inventory-item (memoize remove-custom-inventory-item-fn))

(defn new-custom-item-fn
  "Handler adding a blank custom item under `custom-equipment-key`."
  [custom-equipment-key]
  #(dispatch [::char5e/new-custom-item custom-equipment-key]))

(def new-custom-item (memoize new-custom-item-fn))

(defn make-inventory-item-fn
  "A function of (index, stored pick) returning the `inventory-item` row for selection `key`, its
   name from `item-map`, its quantity box `qty-input-width` wide."
  [key item-map qty-input-width]
  (fn [i {item-key ::entity/key
          {item-qty ::char-equip5e/quantity
           equipped? ::char-equip5e/equipped?
           item-name ::char-equip5e/name} ::entity/value}]
    (let [item (item-map item-key)
          final-name (or item-name (:name item) (::mi5e/name item))
          item-description (:description item)]
      ^{:key item-key}
      [inventory-item {:selection-key key
                       :item-key item-key
                       :item-name final-name
                       :item-qty item-qty
                       :item-description item-description
                       :equipped? equipped?
                       :i i
                       :qty-input-width qty-input-width
                       :check-fn (inventory-check key i)
                       :qty-change-fn (inventory-qty-change key i)
                       :remove-fn (remove-inventory-item key item-key)}])))

(def make-inventory-item (memoize make-inventory-item-fn))

(defn- share-line
  "The line under a gear row that a kit shows: each kit's share, then the unbundled stock, from
   `inventory/shares`. A low share is orange and bold."
  [{ks :kits spare :spare}]
  (into [:span.f-s-12]
        (interpose
         [:span.opacity-7 " · "]
         (concat
          (for [{:keys [pack held target low?]} ks]
            (let [text (str (inventory/short-name pack) " " held "/" target)]
              (if low? [:span.orange.f-w-b text] [:span.opacity-7 text])))
          (when (pos? spare) [[:span.opacity-7 (str spare " unbundled")]])))))

(defn- kit-icon
  "The icon of a kit's tile: the chest for a pack that comes in one, else the backpack."
  [chest? size]
  (comps/themed-icon (if chest? "open-treasure-chest" "backpack") size @(subscribe [:theme])))

(defn- act-event
  "The event applying kit action `action` with `args` to character `id` (nil: the builder's)."
  [id action & args]
  (if id (into [::inventory/sheet-act id action] args) (into [::inventory/act action] args)))

(defn- kit-act
  "A click handler applying kit action `action` with `args` to character `id` (`act-event`)."
  [id action & args]
  #(dispatch (apply act-event id action args)))

(defn- quiet-link
  "A kit action link (`.link-button.quiet`): `label`, `on-click`, and :strong? or :muted?."
  [label on-click & [{:keys [strong? muted?]}]]
  [:button.link-button.quiet {:class (cond strong? "strong" muted? "muted") :on-click on-click} label])

(defn- strip
  "The one kit strip: the shared quiet callout with `text`, an optional bold `title`, and link
   `actions` ([label on-click opts]), on their own row when `below?`."
  [{:keys [title text actions below?]}]
  [notifications/callout
   {:tone :note :quiet? true :title title :text text :actions-below? below?
    :actions (for [[label on-click opts] actions] (merge {:label label :link? true :on-click on-click} opts))}])

(defn- contents
  "A kit's summary line folded to about `budget` characters (`inventory/contents-line`): low items
   orange, folded counts underlined (the kit opens to show them), a full kit's items muted."
  [tracked budget]
  (let [{:keys [low more-low more-full full]} (inventory/contents-line tracked budget)
        nm #(inventory/item-short-name (:key %))]
    (into [:div.kit-contents]
          (if full
            (for [r full] [:span.opacity-7 (str (nm r) " " (:held r))])
            (concat
             (for [r low] [:span.orange.f-w-b (str (nm r) " " (:held r) "/" (:target r))])
             (when (pos? more-low) [[:span.orange.f-w-b.underline (str "+" more-low " low")]])
             (when (pos? more-full) [[:span.opacity-7.underline (str "+" more-full " full")]]))))))

(defn- kit-row
  "One kit: its tile and summary (tap to open the full list, Move to top and the two removes), and
   Restock when it is low, for character `id` (nil: the builder's). `asking` holds the pack whose
   Restock question the strip is showing; `budget` is the summary line's width in characters."
  []
  (let [open? (r/atom false)]
    (fn [id top? asking budget {:keys [pack name chest? tracked could-use-spare holds pack-count bought]}]
      (let [low? (some :low? tracked)
            toggle #(swap! open? not)
            close-then #(do (reset! open? false) (dispatch %))]
        [:div
         [:div.kit-row
          [:div.kit-tile.pointer {:on-click toggle} [kit-icon chest? 32]]
          [:button.kit-summary {:on-click toggle :aria-expanded @open?}
           [:div.kit-name-line
            [:span.kit-name.f-w-b.f-s-16 (str name (when (> pack-count 1) (str " \u00d7" pack-count)))]
            [:i.fa.m-l-5.f-s-12 {:class (if @open? "fa-caret-up" "fa-caret-down") :aria-hidden true}]]
           [contents tracked budget]]
          (when low?
            [:button.form-button.kit-restock
             {:class (when (= pack @asking) "opacity-5")
              :on-click (if (seq could-use-spare)
                          #(reset! asking pack)
                          #(do (reset! asking nil) (dispatch (act-event id :restock pack :all-new))))}
             "Restock"])]
         (when @open?
           [:div.kit-indent
            (into [:div.kit-list]
                  (mapcat (fn [{:keys [key held target low?]}]
                            (let [cls (if low? "orange f-w-b" "opacity-7")]
                              [[:span.kit-name {:class cls} (inventory/item-short-name key)]
                               [:span {:class cls} (if low? (str held "/" target) (str held))]]))
                          (concat (sort-by #(/ (:held %) (:target %)) (filter :low? tracked))
                                  (remove :low? tracked))))
            (when-not top?
              [:div [quiet-link "Move to top" #(close-then (act-event id :move-to-top pack))]])
            (when (and (> pack-count 1) (pos? bought))
              [:div.kit-remove
               [quiet-link "Remove one pack" #(close-then (act-event id :remove-one pack))]
               [:div.f-s-12.opacity-7 "Items stay in inventory"]])
            [:div.kit-remove
             [quiet-link "Remove kit" #(close-then (act-event id :remove pack))]
             [:div.f-s-12.opacity-7 "Items stay in inventory"]]
            [:div.kit-remove
             [quiet-link "Remove kit and items" #(close-then (act-event id :remove-with-items pack))]
             (when (seq holds)
               [:div.f-s-12.opacity-7 (str "Also removes " (inventory/held-label holds))])]])]))))

(defn- kit-question
  "A question character `id`'s kit needs answered: a used kit whose pack is gone (remove what it
   added, or keep it), or a pack's own choice (which holy symbol)."
  [id {:keys [kind pack group options]}]
  (let [pack-name (:name (inventory/pack pack))]
    (case kind
      :orphan
      [strip {:text (str "Remove what the " pack-name " added?")
              :actions [["Remove" (kit-act id :resolve-orphan pack :remove)]
                        ["Keep" (kit-act id :resolve-orphan pack :keep)]]}]
      :choice
      [strip {:text (str "Which " (s/lower-case (:name (equip5e/equipment-map group) (name group)))
                         " for the " pack-name "?")
              :actions (for [k options] [(:name (equip5e/equipment-map k)) (kit-act id :answer-choice pack group k)])}])))

(defn- packs-preset
  "The search box's preset for the kit shortcuts: every equipment pack, those already kits in `ks`
   (the panel's kits) noted \"You have N\"; picking one of those adds another pack to its kit."
  [ks]
  {:label "Packs" :open? true
   :keys (set (map :key equip5e/packs))
   :notes (into {} (for [{:keys [pack pack-count]} ks] [pack (str "You have " pack-count)]))})

(defn kits-block
  "The Kits block (plan-inventory-kits.md) for character `id` (nil: the builder's): each kit; the
   questions kits need answered; the one strip (Unbundled and Re-bundle, or Restock's question); the
   last action's Undo. `search-preset`: the search box's preset atom, which Add a kit fills with the
   packs. `adding?`: show the dashed Add a kit slot (the builder: header link, slot when empty).
   The summary lines fold to the block's live width (a ResizeObserver), not the device type."
  []
  (let [asking (r/atom nil)
        width (r/atom 390)
        observer (atom nil)
        ref (fn [el]
              (some-> @observer .disconnect)
              (when (and el (exists? js/ResizeObserver))
                (let [o (js/ResizeObserver.
                         (fn [entries] (reset! width (.. (aget entries 0) -contentRect -width))))]
                  (.observe o el)
                  (reset! observer o))))]
    (fn [{:keys [id search-preset adding?]}]
      (let [{ks :kits :keys [questions fillable]} @(subscribe [::inventory/panel id])
            undo @(subscribe [::inventory/undo id])
            add-kit #(reset! search-preset (packs-preset ks))
            builder? (nil? id)
            budget (max 20 (js/Math.round (* 40 (/ @width 390))))
            asking-kit (some #(when (= @asking (:pack %)) %) ks)]
        (when (or builder? adding? (seq ks))
          [:div.m-b-10 {:ref ref}
           [:div.flex.justify-cont-s-b.align-items-c
            [:div
             [:div.f-w-b.f-s-14 "Kits"]
             (when (> (count ks) 1) [:div.f-s-12.opacity-5 "Spent items come out of the top kit first"])]
            (when builder?
              [:button.link-button.quiet {:on-click add-kit}
               "Add a kit" [:i.fa.fa-plus-circle.m-l-5.f-s-14]])]
           (doall
            (map-indexed
             (fn [i kit] ^{:key (:pack kit)} [kit-row id (zero? i) asking budget kit])
             ks))
           (when (or adding? (and builder? (empty? ks)))
             [:div.kit-row.pointer {:on-click add-kit}
              [:div.kit-tile.kit-tile-empty [kit-icon false 32]]
              [:span.f-s-14.opacity-5 "Add a kit"]])
           [:div.m-t-5
            (for [q questions] ^{:key (str (:kind q) (:pack q))} [kit-question id q])
            (cond
              (seq (:could-use-spare asking-kit))
              (let [pk (:pack asking-kit)
                    restock #(do (reset! asking nil) (dispatch (act-event id :restock pk %)))]
                [strip {:title "Re-bundle items first?"
                        :text (inventory/unbundled-label (:could-use-spare asking-kit))
                        :below? true
                        :actions [["Cancel" #(reset! asking nil) {:muted? true}]
                                  ["No" #(restock :all-new)]
                                  ["Yes" #(restock :mine-first) {:strong? true}]]}])
              (seq fillable)
              [strip {:text (inventory/unbundled-label fillable)
                      :actions [["Re-bundle" (kit-act id :rebundle)]]}])
            (when undo
              [:div.flex.align-items-c.justify-cont-s-b.f-s-12
               [:span (:message undo)]
               [quiet-link "Undo" #(dispatch (if id [::inventory/sheet-undo id] [::inventory/undo]))]])]])))))

(defn make-custom-inventory-item
  "A function of (index, custom item) returning its `inventory-item` row; a starting item's name
   is fixed, a player's own is editable."
  [custom-equipment-key qty-input-width]
  (fn [i {:keys [::char-equip5e/name
                 ::char-equip5e/quantity
                 ::char-equip5e/equipped?
                 ::char-equip5e/background-starting-equipment?
                 ::char-equip5e/class-starting-equipment?] :as item}]
    (let [item-key (common/name-to-kw name)]
      ^{:key i}
      [inventory-item {:selection-key custom-equipment-key
                       :item-key item-key
                       :item-name name
                       :item-qty quantity
                       :equipped? equipped?
                       :user-created? (not (or background-starting-equipment?
                                               class-starting-equipment?))
                       :i i
                       :qty-input-width qty-input-width
                       :check-fn (toggle-custom-inventory-item-equipped custom-equipment-key i)
                       :qty-change-fn (change-custom-inventory-item-quantity custom-equipment-key i)
                       :remove-fn (remove-custom-inventory-item custom-equipment-key name)}])))

(defn inventory-selector
  "One inventory list for `selection`: the adder, its rows (names from `item-map-sub`), and with
   `custom-equipment-key` the custom items and Add Custom Item. Equipment also gets the Kits block,
   hides pack picks (their kits stand for them) and shows each kit's share under a row. Holds the
   search box's preset, which the Kits block sets."
  []
  (let [preset (r/atom nil)]
   (fn [item-map-sub qty-input-width {:keys [selection]} & [custom-equipment-key]]
  (let [{:keys [::t/key]} selection
        selected-items @(subscribe [:entity-option key])
        item-map @(subscribe item-map-sub)
        selected-keys (set (map ::entity/key selected-items))
        options (entity/selection-options selection)
        gear? (= key :equipment)
        shares (when gear? (:shares @(subscribe [::inventory/panel])))
        ;; A pack's pick stands for its kit, shown in the Kits block, not as a row.
        rows (keep-indexed (fn [i item] (when-not (and gear? (inventory/pack (::entity/key item))) [i item]))
                           selected-items)]
    [:div
     (when gear? [kits-block {:search-preset preset}])
     [inventory-adder key options selected-keys {:preset preset}]
     (when (seq rows)
       [:div.flex.f-s-12.opacity-5.m-t-10.justify-cont-s-b
        [:div.m-r-10 "Carried?"]
        [:div.m-r-30 "Quantity"]])
     [:div
      (doall
       (for [[i item] rows
             :let [row ((make-inventory-item key item-map qty-input-width) i item)
                   share (get shares (::entity/key item))]]
         (if share
           (update row 1 assoc :note [share-line share])
           row)))]
     (when custom-equipment-key
       [:div
        [:div
         (doall
          (map-indexed
           (make-custom-inventory-item custom-equipment-key qty-input-width)
           @(subscribe [:entity-value custom-equipment-key])))]
        [:div.flex.justify-cont-end
         [:div.orange.pointer.m-t-5.m-r-5
          {:on-click (new-custom-item custom-equipment-key)}
          [:span.underline "Add Custom Item"]
          [:i.fa.fa-plus-circle.m-l-5.f-s-16]]]])]))))

(def ^:private gear-options
  "Every Equipment item as a search box option."
  (mapv (fn [{:keys [key name]}] {::t/key key ::t/name name}) equip5e/equipment))

(defn sheet-equipment
  "The sheet's Other Equipment for character `id` (nil: the builder's): its kits, always with
   Restock; the item list with each kit's share; and a Manage inventory switch that turns the
   search from filtering this list to adding items, shows Add a kit and the − / + quantities, and
   ends at Done. The switch lives here, so leaving the tab or the sheet turns it off."
  []
  (let [managing? (r/atom false)
        query (r/atom "")
        preset (r/atom nil)]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            {:keys [shares]} @(subscribe [::inventory/panel id])
            items (merge @(subscribe [::char5e/equipment id])
                         (zipmap (range) @(subscribe [::char5e/custom-equipment id])))
            nm (fn [[k cfg]] (or (:name (equip5e/equipment-map k)) (::char-equip5e/name cfg) ""))
            q (s/lower-case (s/trim @query))
            rows (if (or @managing? (s/blank? q))
                   items
                   (filter #(s/includes? (s/lower-case (nm %)) q) items))
            change (fn [k delta]
                     (dispatch (if id
                                 [::inventory/sheet-change-quantity id k delta]
                                 [::inventory/change-quantity k delta])))]
        [:div
         [:div.flex.align-items-c
          (comps/themed-icon "backpack" 32 @(subscribe [:theme]))
          [:span.m-l-5.f-w-b.f-s-18.flex-grow-1 "Other Equipment"]
          [:span.dev-mode-row.m-t-0
           [:span.dev-mode-label.f-s-14.pointer {:on-click #(swap! managing? not)} "Manage inventory"]
           [:span.toggle-switch
            {:class (when @managing? "on") :role "switch" :aria-checked (str @managing?) :tabIndex 0
             :on-click #(swap! managing? not)}]]]
         [:div.m-t-10
          [kits-block {:id id :search-preset preset :adding? @managing?}]
          (if @managing?
            [:div.inv-combo-adding
             [:span.inv-combo-preset.adding [:i.fa.fa-plus.m-r-5 {:aria-hidden true}] "Adding items"]
             [inventory-combobox :equipment gear-options #{}
              {:preset preset :placeholder "Add to inventory…"
               :on-pick (when id #(dispatch [::inventory/sheet-add-item id %]))}]]
            [:div.inv-combo
             [:input.inv-combo-input
              {:value @query
               :placeholder (str "Filter my inventory… (" (count items) ")")
               :on-change #(reset! query (.. % -target -value))}]])]
         [:table.w-100-p.t-a-l.striped
          [:tbody.equipment
           [:tr.f-w-b
            {:class (when mobile? "f-s-12")}
            [:th.p-10 "Name"]
            [:th.p-10 "Qty."]
            [:th.p-10 "Details"]
            [:th]]
           (doall
            (for [[item-kw item-cfg :as row] rows
                  :let [{:keys [cost weight]} (equip5e/equipment-map item-kw)
                        qty (::char-equip5e/quantity item-cfg)
                        share (get shares item-kw)]]
              ^{:key item-kw}
              [:tr.item
               [:td.p-10.f-w-b (nm row)
                (when share [:div.f-w-n [share-line share]])]
               [:td.p-10
                (if (and @managing? (keyword? item-kw))
                  [:div.sheet-stepper
                   [:button {:aria-label "One fewer" :on-click #(change item-kw -1)}
                    [:i.fa.fa-minus-circle.orange.f-s-18]]
                   [:span qty]
                   [:button {:aria-label "One more" :on-click #(change item-kw 1)}
                    [:i.fa.fa-plus-circle.orange.f-s-18]]]
                  qty)]
               [:td.p-10
                (str (when cost (str (:num cost) " " (common/safe-name (:type cost)) ", ")) weight)]]))]]
         (when @managing?
           [:div.flex.justify-cont-end.m-t-10
            [:button.form-button {:on-click #(do (reset! managing? false) (reset! query ""))} "Done"]])]))))

