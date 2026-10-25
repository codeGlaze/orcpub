(ns orcpub.dnd.e5.views.charges-builder
  "The item builder's Charges group: how many charges a custom item has, when and how many come
   back, and the spells it casts with their price in charges. Its events edit ::mi/builder-item.
   Plan: plan-charges-and-casting.md, step 7."
  (:require [clojure.string :as s]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch reg-event-db]]
            [orcpub.common :as common]
            [orcpub.dice :as dice]
            [orcpub.dnd.e5.events :as events]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.views.builder-fields :refer [base-builder-field input-builder-field
                                                        dropdown number-field]]))

(defn set-charges
  "`item` with its charges set from the typed `text`: a whole number is the maximum, anything
  else is kept as a rolled start (\"1d8+1\"), blank clears both."
  [item text]
  (let [t (s/trim (or text ""))
        item (dissoc item ::mi/charges ::mi/charges-start)]
    (cond
      (s/blank? t) item
      (re-matches #"\d+" t) (let [n (js/parseInt t)] (cond-> item (pos? n) (assoc ::mi/charges n)))
      :else (assoc item ::mi/charges-start t))))

(defn set-cast
  "`item` with key `k` of its `i`th cast set to `v`, or removed when `v` is nil. Picking a spell
  also sets the cast's level to the spell's own (`spell-level`) and its cost to 1 when unset."
  [item i k v spell-level]
  (update-in item [::mi/casts i]
             (fn [c]
               (cond-> (if (nil? v) (dissoc c k) (assoc c k v))
                 (= ::mi/cast-spell k) (assoc ::mi/cast-level (or spell-level 1))
                 (and (= ::mi/cast-spell k) (nil? (::mi/cast-cost c))) (assoc ::mi/cast-cost 1)))))

(reg-event-db
 ::mi/set-item-charges
 events/item-interceptors
 (fn [item [_ text]] (set-charges item text)))

(reg-event-db
 ::mi/set-item-charges-recharge
 events/item-interceptors
 (fn [item [_ v]]
   (if (s/blank? v)
     (dissoc item ::mi/charges-recharge ::mi/charges-regain)
     (assoc item ::mi/charges-recharge (keyword "orcpub.dnd.e5.units" v)))))

(reg-event-db
 ::mi/set-item-charges-regain
 events/item-interceptors
 (fn [item [_ text]]
   (if (s/blank? text) (dissoc item ::mi/charges-regain) (assoc item ::mi/charges-regain (s/trim text)))))

(reg-event-db
 ::mi/add-item-cast
 events/item-interceptors
 (fn [item _] (update item ::mi/casts (fnil conj []) {::mi/cast-level 1 ::mi/cast-cost 1})))

(reg-event-db
 ::mi/remove-item-cast
 events/item-interceptors
 (fn [item [_ i]]
   (let [casts (vec (concat (subvec (::mi/casts item) 0 i) (subvec (::mi/casts item) (inc i))))]
     (if (seq casts) (assoc item ::mi/casts casts) (dissoc item ::mi/casts)))))

(reg-event-db
 ::mi/set-item-cast
 events/item-interceptors
 (fn [item [_ i k v spell-level]] (set-cast item i k v spell-level)))

(def recharge-options
  "The times a builder item's charges can come back, as dropdown items."
  [{:value "" :title "Never"}
   {:value (name ::units5e/dawn) :title "At dawn"}
   {:value (name ::units5e/dusk) :title "At dusk"}
   {:value (name ::units5e/short-rest) :title "After a short rest"}
   {:value (name ::units5e/long-rest) :title "After a long rest"}])

(defn- part-text
  "One damage or healing part as text: \"8d6 fire\", \"1d8 + modifier\", \"2d8 fire or cold\"."
  [{:keys [dice type types mod?]}]
  (str dice
       (when type (str " " (name type)))
       (when (seq types) (str " " (s/join " or " (map name types))))
       (when mod? " + modifier")))

(defn roll-hint
  "What a spell rolls, in one line, from its rolling data `r` (spell-rolls.edn) and its `level`:
  \"8d6 fire · Dex save, half on success · +1d6 per level above 3rd\". Nil when it rolls nothing."
  [r level]
  (let [parts (concat (:damage r) (keep #(get r %) [:heal :temp-hp]))
        scaling (some :per-slot parts)
        step (some :slot-step parts)]
    (when (seq parts)
      (s/join " · "
              (cond-> [(str (when (:heal r) "Heals ") (when (:temp-hp r) "Temporary hit points ")
                            (s/join " + " (map part-text parts))
                            (when-let [{n :n} (:count r)] (str ", " n " times")))]
                (:attack r) (conj (str (s/capitalize (name (:attack r))) " spell attack"))
                (:save r) (conj (str (s/capitalize (name (:save r))) " save"
                                     (when (:save-half? r) ", half on success")))
                (some :cantrip? parts) (conj "more dice at levels 5, 11 and 17")
                scaling (conj (str "+" scaling " per " (if step (str step " levels") "level")
                                   " above " (common/ordinal level))))))))

(defn- cast-row
  "One spell the builder item casts, at position `i`: spell, level, price and DC, with what the
  spell rolls under it. `spells` is [[key spell]] sorted by name; `rolls` is spell-rolls.edn."
  [i {:keys [::mi/cast-spell ::mi/cast-level ::mi/cast-cost ::mi/cast-per-level
             ::mi/cast-max-level ::mi/save-dc]} spells-map spells rolls]
  (let [spell (spells-map cast-spell)
        setter (fn [k] #(dispatch [::mi/set-item-cast i k % nil]))
        hint (roll-hint (get rolls cast-spell) (:level spell))]
    [:div.item-cast-row
     [:div.flex.flex-wrap.align-items-end
      [:div.item-cast-spell
       (base-builder-field
        "Spell"
        [dropdown {:items (cons {:value "" :title "Choose a spell"}
                                (map (fn [[k {:keys [name]}]] {:value (clojure.core/name k) :title name}) spells))
                   :value (some-> cast-spell name)
                   :on-change #(let [k (when-not (s/blank? %) (keyword %))]
                                 (dispatch [::mi/set-item-cast i ::mi/cast-spell k (:level (spells-map k))]))}])]
      (for [[label k v] [["Level" ::mi/cast-level cast-level] ["Charges" ::mi/cast-cost cast-cost]
                         ["+ per level" ::mi/cast-per-level cast-per-level] ["Up to level" ::mi/cast-max-level cast-max-level]
                         ["Save DC" ::mi/save-dc save-dc]]]
        ^{:key label}
        [:div.item-cast-number (base-builder-field label [number-field {:value v :on-change (setter k)}])])
      [:button.form-button.item-cast-remove
       {:aria-label "Remove this spell" :on-click #(dispatch [::mi/remove-item-cast i])} "Remove"]]
     (when hint [:div.item-cast-hint hint])]))

(defn charges-group
  "The Charges group of the item builder for `item` (a ::mi/builder-item)."
  [{:keys [::mi/charges ::mi/charges-start ::mi/charges-recharge ::mi/charges-regain ::mi/casts]}]
  (r/with-let [_ (dispatch [:load-srd :spell-rolls])]
   (let [spells-map @(subscribe [::spells/spells-map])
        spells (sort-by (comp :name val) spells-map)
        rolls @(subscribe [:srd :spell-rolls])
        bad-start? (and charges-start (nil? (dice/parse-dice charges-start)))]
    [:div.m-b-40.item-charges-builder
     [:div.m-b-10 [:span.f-s-24.f-w-b "Charges"]]
     [:div.flex.flex-wrap
      [:div.item-charges-field
       [input-builder-field "Charges" (or charges charges-start)
        #(dispatch [::mi/set-item-charges %]) {:class "input" :placeholder "7, or 1d8+1"}]
       (when bad-start? [:div.item-cast-hint.red "Type a number, or dice like 1d8+1"])]
      (when (or charges charges-start)
        [:div.item-charges-field
         (base-builder-field "Recharge" [dropdown {:items recharge-options
                                                   :value (some-> charges-recharge name)
                                                   :on-change #(dispatch [::mi/set-item-charges-recharge %])}])])
      (when charges-recharge
        [:div.item-charges-field
         [input-builder-field "Regains" charges-regain #(dispatch [::mi/set-item-charges-regain %])
          {:class "input" :placeholder "All, or 1d6+1"}]])]
     (when (or charges charges-start)
       [:div.m-t-10
        [:div.f-w-b.m-b-5 "Spells it casts"]
        (doall (map-indexed (fn [i c] ^{:key i} [cast-row i c spells-map spells rolls]) casts))
        [:button.form-button.m-t-5 {:on-click #(dispatch [::mi/add-item-cast])} "Add a spell"]])])))
