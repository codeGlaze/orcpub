(ns orcpub.dnd.e5.srd-starting-equipment
  "'Start from an SRD class': derive a class's starting equipment as serializable data
   (fixed grants + :equipment-selections) DIRECTLY from the live class definition — no
   hand-transcribed copy. class-option's built output holds the equipment as fixed
   associated-options plus choice selections whose grants live in modifier fns; we read
   the fixed ones as data and recover choice grants by APPLYING each modifier fn (exactly
   how the app applies them to a character). Verified by a decompile->recompile round-trip
   against the live class in orcpub.starting-equipment-test."
  (:require [clojure.string :as str]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.equipment :as equipment]))

;; class-kw -> a thunk building the live class option with inert spell/subclass/language
;; args (the equipment portion doesn't depend on them). Warlock takes two extra args.
(def ^:private class-option-thunks
  (let [wm weapons/weapons-map]
    {:barbarian #(classes/barbarian-option {} {} {} {} wm)
     :bard      #(classes/bard-option      {} {} {} {} wm)
     :cleric    #(classes/cleric-option    {} {} {} {} wm)
     :druid     #(classes/druid-option     {} {} {} {} wm)
     :fighter   #(classes/fighter-option   {} {} {} {} wm)
     :monk      #(classes/monk-option      {} {} {} {} wm)
     :paladin   #(classes/paladin-option   {} {} {} {} wm)
     :ranger    #(classes/ranger-option    {} {} {} {} wm)
     :rogue     #(classes/rogue-option     {} {} {} {} wm)
     :sorcerer  #(classes/sorcerer-option  {} {} {} {} wm)
     :warlock   #(classes/warlock-option   {} {} {} {} wm {} {})
     :wizard    #(classes/wizard-option    {} {} {} {} wm)}))

(def ^:private bucket->kind {:weapons :weapon :armor :armor :equipment :equipment})

;; item-key sets for the "pick one of a pool" sub-choices, so a nested selection whose
;; options are exactly a pool is recovered as {:from <pool>} instead of listing members.
(def ^:private pool-key-sets
  (delay
   {:simple             (set (map :key (weapons/simple-weapons (vals weapons/weapons-map))))
    :martial            (set (map :key (weapons/martial-weapons (vals weapons/weapons-map))))
    :simple-melee       (set (map :key (filter #(and (= :simple (:orcpub.dnd.e5.weapons/type %))
                                                     (:orcpub.dnd.e5.weapons/melee? %))
                                                (vals weapons/weapons-map))))
    :any-weapon         (set (keys weapons/weapons-map))
    :holy-symbol        (set (map :key equipment/holy-symbols))
    :arcane-focus       (set (map :key equipment/arcane-focuses))
    :druidic-focus      (set (map :key equipment/druidic-focuses))
    :musical-instrument (set (map :key equipment/musical-instruments))
    :pack               (set (map :key equipment/packs))}))

(defn- modifier->grants
  "Apply the modifier's fn to {} and read the {bucket {item-key {quantity}}} it produces."
  [m]
  (let [bucket  (:orcpub.modifiers/key m)
        kind    (bucket->kind bucket)
        applied (when (and kind (fn? (:orcpub.modifiers/fn m)))
                  (try ((:orcpub.modifiers/fn m) {}) (catch #?(:clj Throwable :cljs :default) _ nil)))]
    (for [[k v] (get applied bucket)]
      {:kind kind :key k
       :qty (get v :orcpub.dnd.e5.character.equipment/quantity 1)})))

(defn- real-options [sel]
  (remove #(= "<none>" (:orcpub.template/name %)) (:orcpub.template/options sel)))

(defn- strip-prefix [nm]
  (if (and (string? nm) (str/starts-with? nm "Starting Equipment: "))
    (subs nm (count "Starting Equipment: ")) nm))

(defn- nested->from
  "Recognise a nested sub-selection as a named pool (:martial, :arcane-focus, …)."
  [nested]
  (let [keys (set (mapcat #(map :key (mapcat modifier->grants (:orcpub.template/modifiers %)))
                          (real-options nested)))]
    (some (fn [[from ks]] (when (= ks keys) from)) @pool-key-sets)))

(declare option->data)

(defn- nested->choose
  "One nested sub-selection -> a :choose entry. A selection whose options are exactly a
   known pool collapses to {:from <pool>}; anything else is ENUMERATED option-by-option
   ({:name … :options […]}) rather than dropped — so a sub-choice we don't recognise as a
   pool (a future/homebrew 'pick one of these three specific items') still round-trips
   instead of silently vanishing. Only a selection with no representable options at all is
   genuinely undecompilable, and that throws with context rather than losing equipment."
  [nested]
  (if-let [from (nested->from nested)]
    {:name (strip-prefix (:orcpub.template/name nested)) :from from}
    (let [opts (mapv option->data (real-options nested))]
      (if (seq opts)
        {:name (:orcpub.template/name nested) :options opts}
        (throw (ex-info "Undecompilable starting-equipment sub-choice: not a known pool and no enumerable options"
                        {:selection (:orcpub.template/name nested)}))))))

(defn- starting-equipment-selection? [sel]
  (and (map? sel) (contains? (:orcpub.template/tags sel) :starting-equipment)))

(defn- option->data [opt]
  (let [grants (vec (mapcat modifier->grants (:orcpub.template/modifiers opt)))
        ;; only equipment sub-choices; an unrelated nested selection is not equipment, so
        ;; skipping it here is correct, not data loss (and must not reach nested->choose).
        sub-choices (mapv nested->choose
                          (filter starting-equipment-selection?
                                  (:orcpub.template/selections opt)))]
    (cond-> {:name (:orcpub.template/name opt)}
      (seq grants) (assoc :grants grants)
      (seq sub-choices) (assoc :choose sub-choices))))

(defn- decompile-fixed
  "associated-options carrying the class-starting-equipment flag -> {:weapons {k q} …}."
  [built]
  (reduce
   (fn [acc entry]
     (reduce-kv
      (fn [acc bucket items]
        (reduce (fn [acc {k :orcpub.entity/key v :orcpub.entity/value}]
                  (if (:orcpub.dnd.e5.character.equipment/class-starting-equipment? v)
                    (assoc-in acc [bucket k]
                              (:orcpub.dnd.e5.character.equipment/quantity v 1))
                    acc))
                acc items))
      acc entry))
   {} (:orcpub.template/associated-options built)))

(defn- decompile-selections
  "top-level starting-equipment selections -> [{:name … :options [{…}]}]."
  [built]
  (->> (:orcpub.template/selections built)
       (filter starting-equipment-selection?)
       (mapv (fn [sel]
               {:name    (strip-prefix (:orcpub.template/name sel))
                :options (mapv option->data (real-options sel))}))))

(defn decompile
  "A built class-option's starting equipment in the builder's editable form (fixed grants +
   :equipment-selections). Public so the round-trip can be exercised on any built option,
   not only the SRD thunks."
  [built]
  (let [fixed  (decompile-fixed built)
        groups (decompile-selections built)]
    (cond-> {}
      (:weapons fixed)   (assoc :weapons (:weapons fixed))
      (:armor fixed)     (assoc :armor (:armor fixed))
      (:equipment fixed) (assoc :equipment (:equipment fixed))
      (seq groups)       (assoc :equipment-selections groups))))

(defn builder-equipment
  "The SRD class's starting equipment in the builder's editable form, derived from the live
   class. nil for an unknown class."
  [class-kw]
  (when-let [thunk (get class-option-thunks class-kw)]
    (decompile (thunk))))

(def srd-class-keys
  "The SRD classes offered by 'start from a class'."
  [:barbarian :bard :cleric :druid :fighter :monk :paladin :ranger :rogue :sorcerer :warlock :wizard])
