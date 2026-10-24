(ns orcpub.dnd.e5.rests
  "Resting and the time of day: what a short or long rest, dawn and dusk reset on a character,
  including each carried item's charges, and what changed so it can be shown and undone. Pure:
  dice come from a function passed in. Design: handoff-rest-controls.md, plan-charges-and-casting.md."
  (:require [orcpub.entity :as entity]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.units :as units]))

(def carried-selections
  "The selections whose carried items can hold charges."
  [:magic-weapons :magic-armor :other-magic-items])

(defn as-entry
  "A carried item's entry as a map: an old character may store only its quantity."
  [v]
  (if (int? v) {::char-equip/quantity v ::char-equip/equipped? true} v))

(defn carried
  "Every carried item that can hold charges. Takes the character and a fn from item key to item.
  Returns [{:selection-key :item-key :item :entry}], skipping keys the fn does not know."
  [character item-for]
  (for [sk carried-selections
        {k ::entity/key v ::entity/value} (get-in character [::entity/options sk])
        :let [item (item-for k)]
        :when item]
    {:selection-key sk :item-key k :item item :entry (as-entry v)}))

(defn update-carried
  "`character` with `f` applied to the carried entry of `item-key` under `selection-key`."
  [character selection-key item-key f]
  (update-in character [::entity/options selection-key]
             (fn [opts] (mapv #(if (= item-key (::entity/key %)) (update % ::entity/value (comp f as-entry)) %)
                              opts))))

(def recharges
  "For each action, the item recharge times it brings back."
  {:short #{::units/short-rest ::units/rest}
   :long #{::units/long-rest ::units/short-rest ::units/rest}
   :dawn #{::units/dawn}
   :dusk #{::units/dusk}})

(def clears
  "For each action, the use periods it clears in ::char5e/features-used."
  {:short units/short-rest-clears :long units/long-rest-clears :dawn units/dawn-clears :dusk units/dusk-clears})

(defn- actions
  "The actions a request takes, in order: the rest, then each time of day. Takes {:rest :times}."
  [{kind :rest times :times}]
  (cond-> [] kind (conj kind) (contains? times :dawn) (conj :dawn) (contains? times :dusk) (conj :dusk)))

(defn- time-of?
  "Whether an item recharges at dawn or dusk, by its charges or by a power's own recharge."
  [item]
  (let [times #{::units/dawn ::units/dusk}]
    (boolean (or (times (::mi/charges-recharge item))
                 (some (comp times ::mi/recharge) (concat (::mi/extra-damage item) (::mi/effects item)))))))

(defn has-time-of-day?
  "Whether the character holds anything that comes back at dawn or dusk: such an item, or a use
  already marked under dawn, dusk or day. Takes the character and its carried items."
  [character items]
  (boolean (or (some (comp time-of? :item) items)
               (some #(seq (get-in character [::entity/values ::char5e/features-used %]))
                     [::units/dawn ::units/dusk ::units/day]))))

(defn- spent
  "The positions in a carried stack with charges spent. Takes a carried item map."
  [{:keys [item entry]}]
  (filter #(pos? (- (or (mi/charge-max item entry %) 0) (mi/charges-left item entry %)))
          (range (max 1 (::char-equip/quantity entry 1)))))

(defn would-recharge
  "The carried items an action would give charges back to, and how many uses it would clear.
  Takes the character, its carried items and the action (:short :long :dawn :dusk). Returns
  {:items [names] :uses n}."
  [character items action]
  {:items (vec (for [{:keys [item] :as c} items
                     :when (and ((recharges action) (::mi/charges-recharge item)) (seq (spent c)))]
                 (::mi/name item)))
   :uses (count (mapcat #(get-in character [::entity/values ::char5e/features-used %]) (clears action)))})

(defn- rest-values
  "A character's values after a rest: a long rest also restores hit points and spell slots."
  [character kind]
  (cond-> character
    (= :long kind) (update ::entity/values dissoc ::spells/slots-used
                           ::char5e/current-hit-points ::char5e/max-hit-points)))

(defn apply-actions
  "A character after a request: {:rest (:short :long or nil) :times (#{:dawn :dusk})}. Clears the
  uses each action clears and gives charges back: an amount in `amounts` ({[item-key i] n}) where
  the player typed one, else rolled with `roll-die`. Returns {:character :recharged [{:item-key
  :selection-key :name :i :amount :dice :rolls}]}."
  [character items request amounts roll-die]
  (let [acts (actions request)
        periods (mapcat clears acts)
        times (set (mapcat recharges acts))
        regained (for [{:keys [item item-key selection-key] :as c} items
                       :when (times (::mi/charges-recharge item))
                       i (spent c)
                       :let [typed (get amounts [item-key i])
                             {:keys [amount rolls dice]} (if typed {:amount typed :rolls [] :dice nil}
                                                             (mi/roll-regain item roll-die))]]
                   {:item-key item-key :selection-key selection-key :name (::mi/name item) :i i
                    :amount amount :dice dice :rolls rolls :item item})
        character (-> (if (get-in character [::entity/values ::char5e/features-used])
                        (update-in character [::entity/values ::char5e/features-used] #(apply dissoc % periods))
                        character)
                      (rest-values (:rest request)))]
    {:character (reduce (fn [ch {:keys [selection-key item-key item i amount]}]
                          (update-carried ch selection-key item-key #(mi/regain-charges item % i amount)))
                        character regained)
     :recharged (mapv #(dissoc % :item) regained)}))

(defn ask-time-of-day?
  "Whether to ask \"did dawn or dusk pass?\" after a rest taken with no time chosen: the
  character holds dawn or dusk things and dawn or dusk would bring something back. Takes the
  character (before the rest), its carried items and the request."
  [character items {kind :rest times :times}]
  (boolean (and kind (empty? times) (has-time-of-day? character items)
                (some (fn [t] (let [{:keys [items uses]} (would-recharge character items t)]
                                (or (seq items) (pos? uses))))
                      [:dawn :dusk]))))
