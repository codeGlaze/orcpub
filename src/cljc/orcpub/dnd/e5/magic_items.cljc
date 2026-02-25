(ns orcpub.dnd.e5.magic-items
  "Magic item specs, internal-format converters, and expansion logic.
   SRD item data lives in magic-items-data."
  (:require [clojure.spec.alpha :as spec]
            [orcpub.common :as common]
            [orcpub.modifiers :as mod]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.damage-types :as damage-types5e]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.magic-items-data :as magic-items-data]
            [clojure.string :as s]
            [clojure.set :refer [intersection difference]])
  #?(:cljs (:require-macros [orcpub.dnd.e5.modifiers :as mod5e])))

;(spec/def ::name string?)
(spec/def ::name (spec/and string? common/starts-with-letter?))
;(spec/def ::type keyword?)
(spec/def ::type (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::rarity keyword?)
(spec/def ::description string?)
(spec/def ::magical-attack-bonus int?)
(spec/def ::magical-damage-bonus int?)
(spec/def ::modifiers (spec/coll-of ::mod/mod-cfg))
(spec/def ::subtypes (spec/coll-of keyword?))
(spec/def ::attunement (spec/coll-of keyword?))

(def name-key ::name)
(def item-type-key ::type)
(def item-subtype-key ::item-subtype)
(def description-key ::description)
(def summary-key ::summary)
(def magical-attack-bonus-key ::magical-attack-bonus)
(def magical-damage-bonus-key ::magical-damage-bonus)
(def subtypes-key ::subtypes)
(def attunement-key ::attunement)
(def modifiers-key ::modifiers)

(spec/def ::magic-item
  (spec/keys :req [::name]
             :opt [::type
                   ::rarity
                   ::description
                   ::modifiers
                   ::magical-attack-bonus
                   ::magical-damage-bonus
                   ::owner
                   ::subtypes
                   ::attunement]))

(spec/def ::internal-magic-item
  (spec/keys :opt [::name
                   ::type
                   ::rarity
                   ::description
                   ::magical-attack-bonus
                   ::magical-damage-bonus
                   ::attunement]))

(def toggle-mod-keys
  #{:damage-resistance
    :damage-vulnerability
    :damage-immunity
    :condition-immunity})

(def ability-mod-keys
  #{:ability
    :ability-override})

(def speed-mod-keys
  #{:speed
    :speed-override
    :flying-speed-bonus
    :flying-speed-override
    :flying-speed-equal-to-walking
    :swimming-speed
    :swimming-speed-override
    :swimming-speed-equal-to-walking
    :climbing-speed
    :climbing-speed-override
    :climbing-speed-equal-to-walking})

(defn add-internal-speed [mod-map speed-type mod-type value]
  (let [cfg {:type mod-type}]
    (assoc mod-map speed-type (if value (assoc cfg :value value) cfg))))

(defn to-internal-modifiers [modifiers]
  (reduce
   (fn [mod-map {:keys [::mod/key ::mod/args]}]
     (let [[arg-1 arg-2] (mod/raw-args args)]
       (cond
         (toggle-mod-keys key) (assoc-in mod-map [key arg-1] true)
         (ability-mod-keys key) (assoc-in mod-map
                                          [:ability arg-1]
                                          {:value arg-2
                                           :type (if (= :ability key)
                                                   :increases-by
                                                   :becomes-at-least)})
         (= key :saving-throw-bonus) (assoc-in mod-map
                                               [:save arg-1]
                                               {:value arg-2})
         :else (case key
                 :speed (add-internal-speed mod-map :speed :increases-by arg-1)
                 :speed-override (add-internal-speed mod-map :speed :becomes-at-least arg-1)
                 :flying-speed-bonus (add-internal-speed mod-map :flying-speed :increases-by arg-1)
                 :flying-speed-override (add-internal-speed mod-map :flying-speed :becomes-at-least arg-1)
                 :flying-speed-equal-to-walking (add-internal-speed mod-map :flying-speed :equals-walking-speed nil)
                 :swimming-speed (add-internal-speed mod-map :swimming-speed :increases-by arg-1)
                 :swimming-speed-override (add-internal-speed mod-map :swimming-speed :becomes-at-least arg-1)
                 :swimming-speed-equal-to-walking (add-internal-speed mod-map :swimming-speed :equals-walking-speed nil)
                 :climbing-speed (add-internal-speed mod-map :climbing-speed :increases-by arg-1)
                 :climbing-speed-override (add-internal-speed mod-map :climbing-speed :becomes-at-least arg-1)
                 :climbing-speed-equal-to-walking (add-internal-speed mod-map :climbing-speed :equals-walking-speed nil)))))
   {}
   modifiers))

(defn to-internal-item [{:keys [::modifiers ::subtypes] :as item}]
  (cond-> item
    (seq modifiers) (assoc ::internal-modifiers (to-internal-modifiers (::modifiers item)))
    true (dissoc ::modifiers)
    true (update ::attunement set)
    (seq subtypes) (update ::subtypes #(into #{} %))
    true entity/remove-empty-fields))

(defn mod-args [args]
  (map
   (fn [arg]
     (cond
       (string? arg) {::mod/string-arg arg}
       (keyword? arg) {::mod/keyword-arg arg}
       (int? arg) {::mod/int-arg arg}))
   args))

(defn mod-cfg [key & args]
  (cond-> {::mod/key key}
    (seq args) (assoc ::mod/args (mod-args args))))

(defn toggle-mods [kw value-map]
  (sequence
   (comp
    (filter val)
    (map #(mod-cfg kw (key %))))
   value-map))

(defn default-int [value]
  (if (int? value)
    value
    0))

(defn ability-mods [items]
  (map
   (fn [[ability-kw {:keys [value type]}]]
     (if (= type :increases-by)
       (mod-cfg :ability ability-kw (default-int value))
       (mod-cfg :ability-override ability-kw (default-int value))))
   items))

(defn speed-mod-fn [{:keys [increases-by becomes-at-least equals-walking-speed]}]
  (fn [{:keys [type value]}]
    (case type
      :increases-by (mod-cfg increases-by (default-int value))
      :equals-walking-speed (mod-cfg equals-walking-speed)
      (mod-cfg becomes-at-least (default-int value)))))

(def speed-mod
  (speed-mod-fn
   {:increases-by :speed
    :becomes-at-least :speed-override}))

(def flying-speed-mod
  (speed-mod-fn
   {:increases-by :flying-speed-bonus
    :becomes-at-least :flying-speed-override
    :equals-walking-speed :flying-speed-equal-to-walking}))

(def swimming-speed-mod
  (speed-mod-fn
   {:increases-by :swimming-speed
    :becomes-at-least :swimming-speed-override
    :equals-walking-speed :swimming-speed-equal-to-walking}))

(def climbing-speed-mod
  (speed-mod-fn
   {:increases-by :climbing-speed
    :becomes-at-least :climbing-speed-override
    :equals-walking-speed :climbing-speed-equal-to-walking}))

(defn save-mods [items]
  (map
   (fn [[ability-kw {:keys [value]}]]
     (mod-cfg :saving-throw-bonus ability-kw (default-int value)))
   items))

(defn from-internal-modifiers [modifiers]
  (reduce
   (fn [mod-vec [k v]]
     (concat mod-vec
             (cond
               (toggle-mod-keys k) (toggle-mods k v)
               (= :ability k) (ability-mods v)
               (= :save k) (save-mods v)
               (= :speed k) [(speed-mod v)]
               (= :flying-speed k) [(flying-speed-mod v)]
               (= :swimming-speed k) [(swimming-speed-mod v)]
               (= :climbing-speed k) [(climbing-speed-mod v)])))
   []
   modifiers))

(defn from-internal-item [item]
  (-> item
      (assoc ::modifiers (from-internal-modifiers (::internal-modifiers item)))
      (select-keys [:db/id
                    ::name
                    ::type
                    ::subtypes
                    ::rarity
                    ::description
                    ;::attunementb ;typo?
                    ::attunement
                    ::magical-damage-bonus
                    ::magical-attack-bonus
                    ::magical-ac-bonus
                    ::modifiers
                    ::weapons5e/type
                    ::weapons5e/damage-type
                    ::weapons5e/damage-die-count
                    ::weapons5e/damage-die
                    ::weapons5e/range
                    ::weapons5e/versatile
                    ::weapons5e/special?
                    ::weapons5e/loading?
                    ::weapons5e/melee?
                    ::weapons5e/ranged?
                    ::weapons5e/heavy?
                    ::weapons5e/light?
                    ::weapons5e/thrown?
                    ::weapons5e/two-handed?
                    ::weapons5e/finesse?
                    ::weapons5e/reach?
                    ::weapons5e/ammunition?])
      entity/remove-empty-fields))


(def weapons-and-ammunition
  (concat
   weapons5e/weapons
   weapons5e/ammunition))

(defn add-key [item]
  (assoc item
         :key (common/name-to-kw (name-key item))
         :name (name-key item)))

(def weapon-subtypes
  #{:axe :sword :staff})

(defn any-fn [item]
  true)

(defn types-fn [types]
  (fn [{:keys [type]}]
    (types type)))

(defn subtypes-fn [subtypes]
  (fn [{:keys [::weapons5e/subtype]}]
    (subtypes subtype)))

(defn keys-fn [keys]
  (fn [item]
    (keys (:key item))))

(defn make-base-weapon-fn [item-subtype subtypes]
  (let [subtypes-set (into #{}
                           (if (and item-subtype (not (fn? item-subtype)))
                               (conj subtypes item-subtype)
                               subtypes))
        type-intersection (intersection subtypes-set weapon-subtypes)
        diff (difference subtypes-set weapon-subtypes)]
    (if (subtypes-set :all)
      any-fn
      (apply
       some-fn
       (cond-> []
         (fn? item-subtype) (conj item-subtype)
         (seq type-intersection) (conj (subtypes-fn type-intersection))
         (seq diff) (conj (keys-fn diff)))))))

(defn expand-weapon [{:keys [::item-subtype name-fn ::subtypes] :as item}]
  (if (or name-fn
          item-subtype
          (and (seq subtypes)
               (not ((set subtypes) :other))))
    (let [base-weapon-fn (make-base-weapon-fn item-subtype subtypes)
          of-type (filter base-weapon-fn (concat weapons5e/weapons
                                                 weapons5e/ammunition))]
      #?(:clj (when (empty? of-type)
                 (throw (IllegalArgumentException. (str "No base types matched for weapon item!: " (::name item))))))
      (map
       (fn [weapon]
         (let [name (if name-fn
                      (name-fn weapon)
                      (if (> (count of-type) 1)
                        (str (name-key item) ", " (:name weapon))
                        (name-key item)))
               item-key (common/name-to-kw name)]
           (merge
            weapon
            item
            {name-key (name-key item)
             :name name
             :base-key (:key weapon)
             :key item-key})))
       of-type))
    (add-key item)))

(def armor-types
  #{:light :medium :heavy :shield})

(defn make-base-armor-fn [item-subtype subtypes]
  (let [subtypes-set (into #{}
                           (if (and item-subtype (not (fn? item-subtype)))
                               (conj subtypes item-subtype)
                               subtypes))
        type-intersection (intersection subtypes-set armor-types)
        diff (difference subtypes-set armor-types)]
    (if (subtypes-set :all)
      any-fn
      (apply
       some-fn
       (cond-> []
         (fn? item-subtype) (conj item-subtype)
         (seq type-intersection) (conj (types-fn type-intersection))
         (seq diff) (conj (keys-fn diff)))))))

(defn expand-armor [{:keys [::item-subtype name-fn ::subtypes] :as item}]
  (if (or name-fn
          item-subtype
          (seq subtypes))
    (let [base-armor-fn (make-base-armor-fn item-subtype subtypes)
          of-type (filter
                   base-armor-fn
                   armor5e/armor)]
      #?(:clj (when (empty? of-type)
                 (throw (IllegalArgumentException. "No base types matched for armor item!"))))
      (map
       (fn [armor]
         (let [name (if (> (count of-type) 1)
                      (if name-fn
                        (name-fn armor)
                        (str (name-key item) ", " (:name armor)))
                      (name-key item))
               item-key (common/name-to-kw name)]
           (merge
            armor
            item
            {name-key (name-key item)
             :name name
             :base-armor (:key armor)
             :key item-key})))
       of-type))
    (add-key item)))

(defn expand-magic-items [magic-items]
  (flatten
   (map
    (fn [{:keys [::type] :as item}]
      (case type
        :weapon (expand-weapon item)
        :armor (expand-armor item)
        (add-key item)))
    magic-items)))

(def magic-items
  (expand-magic-items magic-items-data/raw-magic-items))

(def magic-item-map
  (into {} (map (fn [i] [(:key i) i])) magic-items))

(def magic-weapon-xform
  (filter
   #(= :weapon (::type %))))

(def magic-weapons
  (sequence
   magic-weapon-xform
   magic-items))

(def magic-weapon-map
  (common/map-by-key magic-weapons))

(defn compute-all-weapons-map
  "Compute merged weapons map: static PHB + static magic + custom magic.
   custom-items are raw user-imported items (pre-expansion).
   SSOT for weapon lookup — called by both subscriptions and modifier
   condition code."
  [custom-items]
  (let [expanded (expand-magic-items custom-items)
        all-items (concat expanded magic-items)
        all-magic-weapons (sequence magic-weapon-xform all-items)
        magic-weapon-lookup (common/map-by-key all-magic-weapons)]
    (merge magic-weapon-lookup weapons5e/weapons-map)))

(def all-weapons-map
  "Static weapons map (no custom items). Use compute-all-weapons-map
   when custom items may be present."
  (compute-all-weapons-map nil))

(def magic-armor-xform
  (filter
   #(= :armor (::type %))))

(def magic-armor
  (sequence
   magic-armor-xform
   magic-items))

(def magic-armor-map
  (common/map-by-key magic-armor))

(def all-armor-map
  (merge
   armor5e/armor-map
   magic-armor-map))

(def other-magic-items-xform
  (remove
   #(#{:armor :weapon} (::type %))))

(def other-magic-items
  (sequence
   other-magic-items-xform
   magic-items))

(def other-magic-item-map
  (common/map-by-key other-magic-items))

(def all-magic-items-map
  (merge
   magic-armor-map
   magic-weapon-map
   other-magic-item-map))

(def all-equipment-map
  (merge
   equip5e/equipment-map
   equip5e/treasure-map
   other-magic-item-map
   all-armor-map
   all-weapons-map))

(defn equipped-items-details [items item-map]
  (filter
   ::char-equip5e/equipped?
   (map
    (fn [[item-kw cfg]]
      (merge
       cfg
       (item-map item-kw)))
    items)))

(defn equipped-armor-details [armor]
  (equipped-items-details armor all-armor-map))

;; Strip base-weapon detail keys that only apply to custom (:other) weapons.
(defn remove-custom-weapon-fields [item]
  (dissoc item
          ::weapons5e/finesse?
          ::weapons5e/versatile?
          ::weapons5e/reach?
          ::weapons5e/two-handed?
          ::weapons5e/thrown?
          ::weapons5e/heavy?
          ::weapons5e/light?
          ::weapons5e/ammunition?
          ::weapons5e/special?
          ::weapons5e/loading?
          ::weapons5e/damage-die-count
          ::weapons5e/damage-die
          ::weapons5e/versatile
          ::weapons5e/melee?
          ::weapons5e/ranged?
          ::weapons5e/type
          ::weapons5e/range
          ::weapons5e/damage-type))

;; Apply a subtype toggle to an item. Initialises sane defaults when
;; switching to :other (Custom) so the view doesn't need to dispatch
;; defaults during render.
(defn apply-subtype-toggle [item type]
  (case type
    :other (-> item
               remove-custom-weapon-fields
               (assoc ::subtypes #{:other})
               (assoc ::weapons5e/damage-die-count 1)
               (assoc ::weapons5e/damage-die 4)
               (assoc ::weapons5e/type :simple)
               (assoc ::weapons5e/damage-type :bludgeoning)
               (assoc ::weapons5e/melee? true)
               (assoc ::weapons5e/ranged? false))
    :all (-> item
             remove-custom-weapon-fields
             (assoc ::subtypes #{:all}))
    (-> item
        remove-custom-weapon-fields
        (update ::subtypes
                (fn [s]
                  (let [clean (disj (or s #{}) :other :all)]
                    (if (get clean type)
                      (disj clean type)
                      (conj clean type))))))))
