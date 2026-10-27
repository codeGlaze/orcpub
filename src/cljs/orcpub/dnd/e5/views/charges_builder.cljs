(ns orcpub.dnd.e5.views.charges-builder
  "The item builder's Charges group and Weapon powers group: how many charges a custom item has, when and how many come
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
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.damage-types :as damage-types5e]
            [orcpub.dnd.e5.srd-conditions :as srd-conditions]
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
   (if (or (s/blank? text) (= "all" (s/lower-case (s/trim text))))
     (dissoc item ::mi/charges-regain)
     (assoc item ::mi/charges-regain (s/trim text)))))

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
  "The times a builder item's charges can come back, as dropdown items (handoff section 7)."
  [{:value "" :title "Never"}
   {:value (name ::units5e/short-rest) :title "Short rest"}
   {:value (name ::units5e/long-rest) :title "Long rest"}
   {:value (name ::units5e/dawn) :title "Dawn"}
   {:value (name ::units5e/dusk) :title "Dusk"}])

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
      [:div.item-charges-field.charges-count
       [input-builder-field "Charges" (or charges charges-start)
        #(dispatch [::mi/set-item-charges %]) {:class "input"}]
       (when bad-start? [:div.item-cast-hint.red "Type a number, or dice like 1d8+1"])]
      (when (or charges charges-start)
        [:div.item-charges-field
         (base-builder-field "Recharges at" [dropdown {:items recharge-options
                                                   :value (some-> charges-recharge name)
                                                   :on-change #(dispatch [::mi/set-item-charges-recharge %])}])])
      (when charges-recharge
        [:div.item-charges-field.charges-regains
         [input-builder-field "Regains" (or charges-regain "all") #(dispatch [::mi/set-item-charges-regain %])
          {:class "input"}]])]
     [:div.item-cast-hint "An item that recharges at dawn or dusk adds the time-of-day toggle to the character's Rest box."]
     (when (or charges charges-start)
       [:div.m-t-10
        [:div.f-w-b.m-b-5 "Spells it casts"]
        (doall (map-indexed (fn [i c] ^{:key i} [cast-row i c spells-map spells rolls]) casts))
        [:button.form-button.m-t-5 {:on-click #(dispatch [::mi/add-item-cast])} "Add a spell"]])])))

;; Weapon powers: extra damage and effects on a custom weapon, in the fields the SRD weapons use
;; (weapon-damage-model.md).

(defn set-power-dice
  "An extra damage row with its dice typed as `text`: \"2d6\" or \"2d6+1\" sets the dice and any
  flat bonus, a plain number only the bonus, anything else clears both."
  [row text]
  (let [{:keys [num sides mod]} (dice/parse-dice text)
        row (dissoc row ::weapons/damage-die ::weapons/damage-die-count ::mi/damage-bonus)]
    (cond-> row
      sides (assoc ::weapons/damage-die sides ::weapons/damage-die-count num)
      (and mod (not (zero? mod))) (assoc ::mi/damage-bonus mod)
      (and (nil? sides) mod) (assoc ::mi/damage-bonus mod))))

(defn set-power
  "`item` with key `k` of row `i` under `kind` (::mi/extra-damage or ::mi/effects) set to `v`, or
  removed when `v` is nil or blank. A save ability makes an effect a save, none a plain effect."
  [item kind i k v]
  (update-in item [kind i]
             (fn [row]
               (let [row (if (or (nil? v) (= "" v)) (dissoc row k) (assoc row k v))]
                 (cond-> row
                   (= ::mi/save-ability k) (assoc ::mi/effect-type (if (::mi/save-ability row) :save :condition)))))))

(reg-event-db
 ::mi/add-item-power
 events/item-interceptors
 (fn [item [_ kind]]
   (update item kind (fnil conj []) (if (= kind ::mi/effects) {::mi/effect-type :condition ::mi/text ""} {}))))

(reg-event-db
 ::mi/remove-item-power
 events/item-interceptors
 (fn [item [_ kind i]]
   (let [rows (vec (concat (subvec (get item kind) 0 i) (subvec (get item kind) (inc i))))]
     (if (seq rows) (assoc item kind rows) (dissoc item kind)))))

(reg-event-db
 ::mi/set-item-power
 events/item-interceptors
 (fn [item [_ kind i k v]] (set-power item kind i k v)))

(reg-event-db
 ::mi/set-item-power-dice
 events/item-interceptors
 (fn [item [_ i text]] (update-in item [::mi/extra-damage i] set-power-dice text)))

(def ^:private when-options
  [{:value "" :title "On a hit"} {:value "miss" :title "On a miss"} {:value "crit" :title "On a critical hit"}])

(def ^:private use-options
  [{:value "" :title "Every time"}
   {:value (name ::units5e/turn) :title "Once per turn"}
   {:value (name ::units5e/short-rest) :title "Once per short rest"}
   {:value (name ::units5e/long-rest) :title "Once per long rest"}
   {:value (name ::units5e/dawn) :title "Once per dawn"}])

(defn- power-row
  "The controls shared by a power row: when it applies, how often, and its charge cost. Takes the
  kind, the position and the row."
  [kind i row]
  (let [setter (fn [k f] #(dispatch [::mi/set-item-power kind i k (f %)]))]
    [:<>
     [:div.item-cast-number.wide (base-builder-field "When" [dropdown {:items when-options :value (some-> (::mi/on row) name)
                                                                       :on-change (setter ::mi/on #(when-not (s/blank? %) (keyword %)))}])]
     [:div.item-cast-number.wide (base-builder-field "Uses" [dropdown {:items use-options :value (some-> (::mi/recharge row) name)
                                                                       :on-change (setter ::mi/recharge #(when-not (s/blank? %) (keyword "orcpub.dnd.e5.units" %)))}])]
     [:div.item-cast-number (base-builder-field "Charges" [number-field {:value (::mi/charge-cost row)
                                                                         :on-change #(dispatch [::mi/set-item-power kind i ::mi/charge-cost (when (and % (pos? %)) %)])}])]
     [:button.form-button.item-cast-remove {:aria-label "Remove" :on-click #(dispatch [::mi/remove-item-power kind i])} "Remove"]]))

(defn powers-group
  "The item builder's Weapon powers group for `item`: extra damage rows and effect rows."
  [{:keys [::mi/extra-damage ::mi/effects]}]
  (let [types (cons {:value "" :title "Weapon's type"}
                    (map (fn [t] {:value (name t) :title (common/kw-to-name t)}) damage-types5e/damage-types))
        abilities (cons {:value "" :title "No save"}
                        (map (fn [[k t]] {:value (name k) :title t})
                             [[:str "Strength"] [:dex "Dexterity"] [:con "Constitution"] [:int "Intelligence"] [:wis "Wisdom"] [:cha "Charisma"]]))
        conditions (cons {:value "" :title "No condition"} (map (fn [c] {:value (name c) :title (common/kw-to-name c)}) (sort srd-conditions/condition-keys)))]
    [:div.m-b-40.item-powers-builder
     [:div.m-b-10 [:span.f-s-24.f-w-b "Weapon powers"]]
     [:div.f-w-b.m-b-5 "Extra damage"]
     (doall
      (map-indexed
       (fn [i row]
         ^{:key i}
         [:div.item-cast-row
          [:div.flex.flex-wrap.align-items-end
           [:div.item-cast-number.wide
            [input-builder-field "Dice" (str (when (::weapons/damage-die row) (str (::weapons/damage-die-count row) "d" (::weapons/damage-die row)))
                                              (when-let [b (::mi/damage-bonus row)] (if (::weapons/damage-die row) (common/mod-str b) (str b))))
             #(dispatch [::mi/set-item-power-dice i %]) {:class "input" :placeholder "2d6"}]]
           [:div.item-cast-number.wide (base-builder-field "Type" [dropdown {:items types :value (some-> (::weapons/damage-type row) name)
                                                                             :on-change #(dispatch [::mi/set-item-power ::mi/extra-damage i ::weapons/damage-type (when-not (s/blank? %) (keyword %))])}])]
           [power-row ::mi/extra-damage i row]]])
       extra-damage))
     [:button.form-button.m-t-5.m-b-10 {:on-click #(dispatch [::mi/add-item-power ::mi/extra-damage])} "Add extra damage"]
     [:div.f-w-b.m-b-5.m-t-10 "Effects"]
     (doall
      (map-indexed
       (fn [i row]
         ^{:key i}
         [:div.item-cast-row
          [input-builder-field "What happens" (::mi/text row) #(dispatch [::mi/set-item-power ::mi/effects i ::mi/text %]) {:class "input"}]
          [:div.flex.flex-wrap.align-items-end
           [:div.item-cast-number.wide (base-builder-field "Save" [dropdown {:items abilities :value (some-> (::mi/save-ability row) name)
                                                                             :on-change #(dispatch [::mi/set-item-power ::mi/effects i ::mi/save-ability (when-not (s/blank? %) (keyword %))])}])]
           (when (::mi/save-ability row)
             [:<>
              [:div.item-cast-number (base-builder-field "Save DC" [number-field {:value (::mi/save-dc row)
                                                                                  :on-change #(dispatch [::mi/set-item-power ::mi/effects i ::mi/save-dc (when (and % (pos? %)) %)])}])]
              [:div.item-cast-number.wide (base-builder-field "On a failed save" [dropdown {:items conditions :value (some-> (::mi/save-fail row) name)
                                                                                            :on-change #(dispatch [::mi/set-item-power ::mi/effects i ::mi/save-fail (when-not (s/blank? %) (keyword %))])}])]])
           [power-row ::mi/effects i row]]])
       effects))
     [:button.form-button.m-t-5 {:on-click #(dispatch [::mi/add-item-power ::mi/effects])} "Add an effect"]]))
