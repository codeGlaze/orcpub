(ns orcpub.dnd.e5.options
  (:require [clojure.string :as s]
            [clojure.set :as sets]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.entity :as entity]
            [orcpub.dice :as dice]
            [orcpub.entity-spec :as es]
            [orcpub.modifiers :as mods]
            [orcpub.dnd.e5.character :as character]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.modifiers :as modifiers]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.equipment :as equipment]
            [orcpub.dnd.e5.spell-lists :as sl]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.event-handlers :as eh]
            [orcpub.components :as comps]
            [re-frame.core :refer [dispatch subscribe]]
            [re-frame.db])
  #?(:cljs (:require-macros [orcpub.dnd.e5.modifiers :as modifiers])))

#?(:cljs (enable-console-print!))

(def alignment-titles
  ["Lawful Good" "Lawful Neutral" "Lawful Evil" "Neutral Good" "Neutral" "Neutral Evil" "Chaotic Good" "Chaotic Neutral" "Chaotic Evil"])

(def xps [0 300 900 2700 6500 14000 23000 34000 48000 64000 85000 100000 120000 140000 165000 195000 225000 265000 305000 355000])

#_ ;; unreferenced — xps is used directly elsewhere
(def levels
  (map-indexed
   (fn [i xp] {:level (inc i) :min-xp xp})
   xps))

(def level-xps
  (zipmap
   (map inc (range))
   xps))

(def alignments
  (map
   (fn [alignment]
     {:name alignment
      :key (common/name-to-kw alignment)})
   alignment-titles))

(def abilities
  [{:key ::character/str
    :name "Strength"
    :abbr "STR"}
   {:key ::character/dex
    :name "Dexterity"
    :abbr "DEX"}
   {:key ::character/con
    :name "Constitution"
    :abbr "CON"}
   {:key ::character/int
    :name "Intelligence"
    :abbr "INT"}
   {:key ::character/wis
    :name "Wisdom"
    :abbr "WIS"}
   {:key ::character/cha
    :name "Charisma"
    :abbr "CHA"}])

(def abilities-map
  (common/map-by-key abilities))

(def conditions
  [{:name "Blinded"
    :key :blinded
    :icon "sight-disabled"}
   {:name "Charmed"
    :key :charmed
    :icon "smitten"}
   {:name "Deafened"
    :key :deafened
    :icon "hearing-disabled"}
   {:name "Exhausted"
    :key :exhausted
    :icon "knockout"}
   {:name "Frightened"
    :key :frightened
    :icon "terror"}
   {:name "Grappled"
    :key :grappled
    :icon "grab"}
   {:name "Incapacitated"
    :key :incapacitated
    :icon "cement-shoes"}
   {:name "Invisible"
    :key :invisible
    :icon "invisible"}
   {:name "Paralyzed"
    :key :paralyzed
    :icon "oppression"}
   {:name "Petrified"
    :key :petrified
    :icon "stone-block"}
   {:name "Poisoned"
    :key :poisoned
    :icon "vomiting"}
   {:name "Prone"
    :key :prone
    :icon "despair"}
   {:name "Restrained"
    :key :restrained
    :icon "imprisoned"}
   {:name "Stunned"
    :key :stunned
    :icon "knockout"}
   {:name "Unconscious"
    :key :unconscious
    :icon "coma"}])

(def damage-types
  [:acid
   :bludgeoning
   :cold
   :fire
   :force
   :lightning
   :necrotic
   :piercing
   :poison
   :psychic
   :radiant
   :slashing
   :thunder])

(def conditions-map
  (common/map-by-key (common/add-keys conditions)))

(defn skill-option [skill]
  (t/option-cfg
   {:name (:name skill)
    :icon (:icon skill)
    :key (:key skill)
    :help (:description skill)
    :prereqs [(t/option-prereq
               "You already have this skill"
               (fn [c]
                 (let [skill-profs (character/skill-proficiencies c)]
                   (not (get skill-profs (:key skill))))))]
    :modifiers [(modifiers/skill-proficiency (:key skill))]}))

(defn weapon-proficiency-option [{:keys [name key]}]
  (t/option-cfg
   {:name name
    :modifiers [(modifiers/weapon-proficiency key)]}))

(defn tool-option [tool]
  (t/option-cfg
   {:name (:name tool)
    :key (:key tool)
    :icon (:icon tool)
    :modifiers [(modifiers/tool-proficiency (:key tool))]}))

(defn weapon-option [weapon & [num]]
  (t/option-cfg
   {:name (:name weapon)
    :key (:key weapon)
    :help (:description weapon)
    :modifiers [(modifiers/weapon (:key weapon) {::char-equip/equipped? true
                                                 ::char-equip/quantity (or num 1)})]}))

(defn weapon-options [weapons & [num]]
  (map
   #(weapon-option % num)
   weapons))

(defn simple-melee-weapon-options [num weapons]
  (weapon-options
   (filter
    #(and (= :simple (::weapons/type %)) (::weapons/melee? %))
    weapons)
   num))

(defn martial-weapon-options [num weapons]
  (weapon-options
   (filter
    #(= :martial (::weapons/type %))
    weapons)
   num))

(defn simple-weapon-options [num weapons]
  (weapon-options
   (filter
    #(= :simple (::weapons/type %))
    weapons)
   num))

(defn skill-options [skills]
  (map
   skill-option
   skills))

(defn weapon-proficiency-options [weapons]
  (map
   weapon-proficiency-option
   weapons))

(defn tool-options [tools]
  (map
   tool-option
   tools))

(defn ability-bonus [ability-value]
  (- (int (/ ability-value 2)) 5))

(defn ability-bonus-str [ability-value]
  (common/bonus-str (ability-bonus ability-value)))

(defn get-raw-abilities [character]
  (get-in character [::entity/options :ability-scores ::entity/value]))

(defn ability-increase-selection-2 [{:keys [ability-keys num-increases min max different? modifier-fn modifier-fns]}]
  (t/selection-cfg
   {:name "Ability Score Improvement"
    :key :asi
    :min (or num-increases min)
    :max (or num-increases max)
    :tags #{:ability-scores}
    :different? different?
    :multiselect? true
    :options (map
              (fn [k]
                (t/option-cfg
                 {:name (:name (abilities-map k))
                  :key k
                  :modifiers (concat
                              [(if modifier-fn
                                 (modifier-fn k)
                                 (modifiers/level-ability-increase k 1))]
                              (map
                               #(% k)
                               modifier-fns))}))
              (or ability-keys
                  character/ability-keys))}))

(defn ability-increase-selection [ability-keys num-increases & [different? modifier-fns]]
  (ability-increase-selection-2 {:ability-keys ability-keys
                                 :num-increases num-increases
                                 :different? different?
                                 :modifier-fns modifier-fns}))

(defn ability-increase-option [num-increases different? ability-keys]
  (t/option-cfg
   {:name "Ability Score Improvement"
    :key :ability-score-improvement
    :selections [(ability-increase-selection ability-keys num-increases different?)]
    :modifiers [(modifiers/deferred-ability-increases)]}))

;; --- Ability-increase spreads (roadmap A4) ------------------------------------------------------
;; A race/feat/background's :ability-increases is ONE spread: a terse list of [amount pool] pairs,
;; e.g. [[2 :cha] [1 :martial]] = "+2 CHA, +1 to any martial stat". The whole list is the unit of
;; the Tasha's "different abilities" rule — every increment lands on a DISTINCT ability. A pool is
;;   :any | :martial | :mental (named groups) | #{:wis :con} (explicit set) | :con (single stat = FIXED).
;; Short ability keywords are namespaced here so authors keep the export compact (rationale +
;; full format spec: docs/kb/ability-increase-spreads.md).
(def ability-groups
  {:any     (set character/ability-keys)
   :martial #{::character/str ::character/dex ::character/con}
   :mental  #{::character/int ::character/wis ::character/cha}})

(defn ns-ability
  "Short ability keyword -> namespaced (:con -> :orcpub.dnd.e5.character/con); idempotent."
  [k]
  (keyword "orcpub.dnd.e5.character" (clojure.core/name k)))

(defn resolve-pool
  "A spread pool -> a set of namespaced ability keys: a named group, an explicit set, or a single stat."
  [pool]
  (cond
    (coll? pool)          (set (map ns-ability pool))
    (ability-groups pool) (ability-groups pool)
    :else                 #{(ns-ability pool)}))

(defn pool-entry?
  "Is `e` a well-formed [amount/count pool …] entry — a vector with a NUMERIC first element and a pool
   that resolve-pool can handle (keyword or collection)? Junk (non-vectors, a missing/non-numeric
   amount, a nil/number pool) is skipped by the compilers so ONE malformed entry in a homebrew pak
   can't crash the whole race/background list at the sub's fan-out (resolve-pool on nil would NPE).
   The entry is un-compilable, not meaningful data — the authoring form is where a creator sees bad
   input; here the job is fan-out crash-safety.
   FOLLOW-UP (harden → surface, guardrail 6): runtime skips silently (correct for a sub's fan-out), but
   the AUTHORING form should report 'N entries ignored as malformed' (save-coverage-notes is the home).
   Tracked in docs/kb/data-safety-layers.md."
  [e]
  (and (vector? e)
       (number? (first e))
       (let [p (second e)] (or (keyword? p) (coll? p)))))

(defn compile-ability-increases
  "Compile a :ability-increases spread (list of [amount pool] pairs) -> {:modifiers :selections}.
   Single-stat pools are FIXED (race-ability modifiers, applied always); multi-stat pools are
   FLOATING — the player assigns each amount to a distinct ability. All floating slots live in ONE
   :asi selection that carries the full spread on ::t/spread, so the assign-from-bag widget can show
   the fixed labels, offer each slot its own pool, and enforce one-ability-per-spread. The slot
   options are keyed asi-<idx>-<ability> and carry their own level-ability-increase. Additive: nil/
   empty -> {} (a race without :ability-increases is unchanged).

   SAVE RIDER (opt-in): an increment may carry a trailing :save — [amount pool :save] — meaning 'also
   grant proficiency in the save for this increment's ability'. Fixed: an unconditional save on that
   stat; floating: the save rides the CHOSEN option (each slot option also grants its own save). This
   reuses modifiers/saving-throws; default (no :save) is bump-only. For saves unrelated to a bump (a
   different stat, or no bump at all), use the standalone :save-proficiencies field instead.

   ATTRIBUTION: a FIXED increment is applied via `:fixed-modifier` (default `race-ability`, the RACE
   column of the ability breakdown). NON-racial silos MUST pass a neutral modifier (see
   compile-ability-grants :attribution) so a background/subclass/feat fixed +N doesn't masquerade as a
   RACIAL increase. See docs/kb/ability-increase-spreads.md."
  [spread & [{:keys [fixed-modifier] :or {fixed-modifier modifiers/race-ability}}]]
  (let [;; skip non-pair entries: the races sub maps this over EVERY homebrew race, so one malformed
        ;; entry must not break the whole list. nil/no field -> no increments (additive).
        increments (map-indexed
                    (fn [idx [amount pool :as incr]]
                      (let [keys (resolve-pool pool)]
                        {:idx idx :amount amount :pool (vec keys) :fixed? (= 1 (count keys))
                         ;; trailing :save = grant a save proficiency on this increment's ability too
                         :save? (= :save (nth incr 2 nil))}))
                    (filter pool-entry? spread))   ; skip junk so one bad entry can't crash the list
        modifiers  (mapcat (fn [{:keys [amount pool save?]}]
                             (concat (fixed-modifier (first pool) amount)
                                     (when save? [(modifiers/saving-throws nil (first pool))])))
                           (filter :fixed? increments))
        floating   (remove :fixed? increments)
        slot-opts  (fn [{:keys [idx amount pool save?]}]
                     (map (fn [k]
                            (t/option-cfg
                             {:name      (str (:name (abilities-map k)) " " (common/bonus-str amount)
                                              (when save? " + save"))
                              :key       (keyword (str "asi-" idx "-" (clojure.core/name k)))
                              ;; a floating pick, once chosen, IS a fixed increase on the chosen
                              ;; ability — so it attributes via the SAME fixed-modifier as a fixed
                              ;; increment (race→race col, subrace→subrace col, else→other), NOT the
                              ;; level-up bucket. Single-sourcing attribution across fixed & floating.
                              :modifiers (cond-> (vec (fixed-modifier k amount))
                                           save? (conj (modifiers/saving-throws nil k)))}))
                          pool))]
    {:modifiers  (vec modifiers)
     :selections (if (seq floating)
                   [(assoc (t/selection-cfg
                            {:name         "Ability Score Improvement"
                             :key          :asi
                             :min          (count floating)
                             :max          (count floating)
                             :tags         #{:ability-scores}
                             :multiselect? true
                             :options      (vec (mapcat slot-opts floating))})
                           ::t/spread (vec increments))]
                   [])}))

(defn toggle-increment-save
  "Toggle the opt-in :save rider on ONE ability-increase increment: [amount pool] <-> [amount pool
   :save]. Rebuilds canonically from amount+pool, so it is idempotent, SELF-HEALING (a malformed longer
   increment normalizes back to 2/3 elements), and can never yield nil/garbage. This is presence of a
   keyword in a VECTOR — NOT a boolean flag in a map — a different shape from common/toggle-in (the
   map-flag toggle helper), so it deliberately does not use it. Backs the ability-increase-choices
   '+ save prof' checkbox so that toggle is tested, not hand-rolled inline."
  [increment]
  (let [[amount pool] increment]
    (if (= :save (nth increment 2 nil))
      [amount pool]
      [amount pool :save])))

(defn compile-save-proficiencies
  "Compile a :save-proficiencies list ([[count pool] ...]) -> {:modifiers :selections}, INDEPENDENT of
   any ability bump (the separate save tool — for saves on a different stat than a bump, or with no
   bump at all). Single-stat pool -> a fixed save proficiency; multi-stat pool -> 'choose <count>
   distinct saves from the pool' (one selection per entry, options keyed save-<idx>-<ability>, each
   granting modifiers/saving-throws). Same terse [count pool] shape as the ASI spread — here the number
   is HOW MANY saves, not a bonus. Cross-entry duplicates collapse harmlessly (saving-throws is a set).
   Additive: nil/empty -> {}. See docs/kb/ability-increase-spreads.md."
  [save-spread]
  (let [entries   (map-indexed
                   (fn [idx [n pool]]
                     (let [keys (resolve-pool pool)]
                       {:idx idx :n n :pool (vec keys) :fixed? (= 1 (count keys))}))
                   (filter pool-entry? save-spread))   ; skip junk (same fan-out crash-safety)
        modifiers (map (fn [{:keys [pool]}] (modifiers/saving-throws nil (first pool)))
                       (filter :fixed? entries))
        choices   (remove :fixed? entries)]
    {:modifiers  (vec modifiers)
     :selections (vec (for [{:keys [idx n pool]} choices]
                        (t/selection-cfg
                         {:name         "Saving Throw Proficiency"
                          :key          (keyword (str "save-prof-" idx))
                          :min          n
                          :max          n
                          :tags         #{:profs}
                          :multiselect? true
                          :different?   true
                          :options      (vec (map (fn [k]
                                                     (t/option-cfg
                                                      {:name      (:name (abilities-map k))
                                                       :key       (keyword (str "save-" idx "-" (clojure.core/name k)))
                                                       :modifiers [(modifiers/saving-throws nil k)]}))
                                                   pool))})))}))

(defn general-ability
  "A FIXED ability increase attributed to NO source column — a plain +N in ?ability-increases, which the
   ability breakdown shows under 'other'. For non-racial silos (background/subclass/feat) so their fixed
   ASIs don't masquerade as RACIAL (race-ability also writes ?race-ability-increases = the race column).
   Returns a vector (same shape as race-ability/subrace-ability)."
  [ability-kw amount]
  [(modifiers/ability ability-kw amount)])

(defn compile-ability-grants
  "The single hook a silo calls to turn a content entry's ability/save DATA into mechanics: merges the
   ASI spread (:ability-increases, incl. the :save rider) and the standalone save tool
   (:save-proficiencies) -> {:modifiers :selections}. Additive (an entry with neither is unchanged).
   Used by plugin-races/subraces/backgrounds/subclasses and feat-option-from-cfg.

   :attribution controls where a FIXED increment lands in the ability breakdown — :race (default) |
   :subrace | :general. NON-racial silos MUST pass :general, or their fixed ASI is shown as racial."
  [{:keys [ability-increases save-proficiencies]} & [{:keys [attribution] :or {attribution :race}}]]
  (let [fixed-modifier (case attribution
                         :subrace modifiers/subrace-ability
                         :general general-ability
                         modifiers/race-ability)
        ai (compile-ability-increases ability-increases {:fixed-modifier fixed-modifier})
        sp (compile-save-proficiencies save-proficiencies)]
    {:modifiers  (concat (:modifiers ai) (:modifiers sp))
     :selections (concat (:selections ai) (:selections sp))}))

(defn ignored-entry-warnings
  "Authoring-time SURFACE for the harden→surface guardrail: the compilers silently skip malformed
   :ability-increases / :save-proficiencies entries (pool-entry? — a vector with a numeric amount and a
   keyword/collection pool) for fan-out crash-safety. That silent drop is correct at runtime but hides
   data loss from a creator editing imported/hand-edited content. Returns a vector of human-readable
   strings naming how many entries in each field will be ignored (empty = all entries compile).
   See docs/kb/data-safety-layers.md (the tracked follow-up)."
  [{:keys [ability-increases save-proficiencies]}]
  (let [ignored (fn [field label]
                  (let [n (count (remove pool-entry? field))]
                    (when (pos? n)
                      (str n " " label " entr" (if (= 1 n) "y is" "ies are")
                           " malformed and will be IGNORED — each must be [amount pool].") )))]
    (vec (keep identity [(ignored ability-increases "ability-increase")
                         (ignored save-proficiencies "saving-throw")]))))

(defn save-coverage-warnings
  "Authoring-time guidance ONLY (no mechanical effect): scan a content entry's save grants — the
   :ability-increases :save riders and the standalone :save-proficiencies — for redundant or
   overlapping coverage, so the builder form can warn-and-explain. Save proficiencies collapse at
   runtime (a set), so duplicates are harmless; these notes just flag wasted authoring/picks. Looks at
   the entry's OWN data only (no class/character context). Returns a vector of strings (empty = clean)."
  [{:keys [ability-increases save-proficiencies]}]
  (let [;; every save-GRANTING source, as a set of namespaced ability keys (single = fixed, many = choice)
        sources (concat (for [[_ pool sv] (filter vector? ability-increases) :when (= :save sv)]
                          (resolve-pool pool))
                        (for [[_ pool] (filter vector? save-proficiencies)]
                          (resolve-pool pool)))
        nm      (fn [k] (s/upper-case (clojure.core/name k)))
        fixed   (->> sources (filter #(= 1 (count %))) (map first))      ; fixed save stats
        choices (->> sources (filter #(> (count %) 1)))                  ; choice pools
        dup-fixed       (->> (frequencies fixed) (filter #(> (val %) 1)) (map key) distinct)
        fixed-in-choice (distinct (for [f (distinct fixed) c choices :when (contains? c f)] f))
        choice-overlap? (boolean (some (fn [[a b]] (seq (sets/intersection a b)))
                                       (for [i (range (count choices))
                                             j (range (inc i) (count choices))]
                                         [(nth choices i) (nth choices j)])))]
    (cond-> []
      (seq dup-fixed)
      (conj (str "The " (s/join ", " (map nm dup-fixed)) " save"
                 (when (> (count dup-fixed) 1) "s") " "
                 (if (> (count dup-fixed) 1) "are" "is")
                 " granted more than once here — the duplicate has no effect."))
      (seq fixed-in-choice)
      (conj (str "A player could pick " (s/join ", " (map nm fixed-in-choice))
                 " from a save choice here, duplicating a save this content already grants outright."))
      choice-overlap?
      (conj "Two save choices here draw from overlapping pools — a player could pick the same save in both and waste one."))))

(defn min-ability [ability-kw min-value]
  (fn [c] (>= (ability-kw (character/ability-values c)) min-value)))

(defn ability-prereq [ability-kw min-value]
  (t/option-prereq (str "Requires " (s/upper-case (name ability-kw)) " " min-value " or higher")
                   (min-ability ability-kw min-value)))

(defn armor-prereq [armor-kw]
  (t/option-prereq (str "Requires proficiency with " (name armor-kw) " armor")
                   (fn [c] (let [prof-keys (character/armor-proficiencies c)]
                             (boolean (and prof-keys (prof-keys armor-kw)))))))

(def elemental-disciplines
  [(t/option-cfg
    {:name "Breath of Winter"
     :modifiers [(modifiers/action
                  {:name "Breath of Winter"
                   :level 17
                   :page 81
                   :summary "spend 6 ki to cast cone of cold"})]})
   (t/option-cfg
    {:name "Clench of the North Wind"
     :modifiers [(modifiers/action
                  {:name "Clench of the North Wind"
                   :page 81
                   :level 6
                   :summary "spend 3 ki to cast hold person"})]})
   (t/option-cfg
    {:name "Eternal Mountain Defense"
     :modifiers [(modifiers/action
                  {:name "Eternal Mountain Defense"
                   :level 17
                   :page 81
                   :summary "spend 5 ki to cast stoneskin on yourself"})]})
   (t/option-cfg
    {:name "Fangs of the Fire Snake"
     :modifiers [(modifiers/trait-cfg
                  {:name "Fangs of the Fire Snake"
                   :page 81
                   :summary "spend 1 ki point when you use Attack action to increase your unarmed strike reach by 10 ft. You unarmed strike deals fire damage and if you spend 1 more ki it deals an extra 2d10 damage"})]})
   (t/option-cfg
    {:name "Fist of Four Thunders"
     :modifiers [(modifiers/action
                  {:name "Fist of Four Thunders"
                   :page 81
                   :summary "spend 2 ki to cast thunderwave"})]})
   (t/option-cfg
    {:name "Fist of Unbroken Air"
     :modifiers [(modifiers/action
                  {:name "Fist of Unbroken Air"
                   :page 81
                   :summary (str "spend 2 + X ki, a creature within 30 ft. takes 3d10 + Xd10 damage on failed DC " (?spell-save-dc ::character/wis) " STR save, is pushed up to 20 ft., and is knocked prone. On successful save it just takes half damage.")})]})
   (t/option-cfg
    {:name "Flames of the Phoenix"
     :modifiers [(modifiers/action
                  {:name "Flames of the Phoenix"
                   :level 11
                   :page 81
                   :summary "spend 4 ki to cast fireball"})]})
   (t/option-cfg
    {:name "Gong of the Summit"
     :modifiers [(modifiers/action
                  {:name "Gong of the Summit"
                   :page 81
                   :level 6
                   :summary "spend 3 ki to cast shatter"})]})
   (t/option-cfg
    {:name "Mist Stance"
     :modifiers [(modifiers/action
                  {:name "Mist Stance"
                   :page 81
                   :level 11
                   :summary "spend 4 ki to cast gaseous form on yourself"})]})
   (t/option-cfg
    {:name "Ride the Wind"
     :modifiers [(modifiers/action
                  {:name "Ride the Wind"
                   :page 81
                   :level 11
                   :summary "spend 4 ki to cast fly on yourself"})]})
   (t/option-cfg
    {:name "River of Hungry Flame"
     :modifiers [(modifiers/action
                  {:name "River of Hungry Flame"
                   :page 81
                   :level 17
                   :summary "spend 5 ki to cast wall of fire"})]})
   (t/option-cfg
    {:name "Rush of the Gale Spirits"
     :modifiers [(modifiers/action
                  {:name "Rush of the Gale Spirits"
                   :page 81
                   :summary "spend 2 ki to cast gust of wind"})]})
   (t/option-cfg
    {:name "Shape of the Flowing River"
     :modfiers [(modifiers/action
                 {:name "Shape of the Flowing River"
                  :page 81
                  :summary "spend 1 ki to transform ice to water, and vice versa, reshape ice"})]})
   (t/option-cfg
    {:name "Sweeping Cinder Strike"
     :modifiers [(modifiers/action
                  {:name "Sweeping Cinder Strike"
                   :page 81
                   :summary "spend 2 ki to cast burning hands"})]})
   (t/option-cfg
    {:name "Water Whip"
     :modifiers [(modifiers/bonus-action
                  {:name "Water Whip"
                   :page 81
                   :summary (str "spend 2 + X ki, a creature within 30 ft. takes 3d10 + Xd10 damage on failed DC " (?spell-save-dc ::character/wis) " DEX save, is pulled up to 25 ft. or knocked prone. On successful save it just takes half damage.")})]})
   (t/option-cfg
    {:name "Wave of Rolling Earth"
     :modifiers [(modifiers/action
                  {:name "Wave of Rolling Earth"
                   :level 17
                   :page 81
                   :summary "spend 6 ki to cast wall of stone"})]})])

(defn monk-elemental-disciplines []
  (t/selection-cfg
   {:name "Elemental Disciplines"
    :tags #{:class}
    :ref [:class :monk :elemental-disciplines]
    :multiselect? true
    :options elemental-disciplines}))

(defn language-option [{:keys [name key]}]
  (t/option-cfg
   {:name name
    :modifiers [(modifiers/language key)]
    :prereqs [(t/option-prereq
               "You already have this language"
               (fn [c] (not (get (character/languages c) key))))]}))

#_ ;; unreferenced — common/name-to-kw is used instead
(defn key-to-name [key]
  (s/join " " (map s/capitalize (s/split (name key) #"-"))))

(defn spell-field [name value]
  [:div.m-b-2
   [:span.f-w-b (str name ": ")]
   [:span.f-w-n value]])

(defn spell-help [{:keys [school casting-time range duration components description summary source page]}]
  [:div
   [:div.m-b-5
    (spell-field "School" school)
    (spell-field "Casting Time" casting-time)
    (spell-field "Range" range)
    (spell-field "Duration" duration)
    (let [{:keys [verbal somatic material material-component]} components]
      (spell-field "Components" (str
          (s/join ", " (remove nil?
              [(when verbal "V")
               (when somatic "S")
               (when material "M")]))
          (when material-component (str " (" material-component ")")))))]
   [:div.f-w-n (when (or description summary)
                 (doall
                  (map-indexed
                   (fn [i p]
                     ^{:key i} [:p.m-t-5 p])
                   (s/split (or description summary) #"\n"))))]
   #_(if source
     (let [{:keys [abbr url]} (disp/sources source)]
       [:div.f-w-n
        [:span "(see"]
        [:a.m-l-5 {:href url :target :_blank} abbr]
        [:span.m-l-5 (str "page " page)]
        [:span " for more details)"]]))])

(defn using-source? [option-sources source]
  (or (nil? source)
      (= :phb source)
      (get option-sources source)))

(defn spell-option [spells-map spellcasting-ability class-name key & [prepend-level? qualifier]]
  (let [{:keys [name level source edit-event] :as spell} (spells-map key)
        ;; When a spell list references a spell whose definition isn't loaded
        ;; (imported homebrew that lists a spell but never defines it, or whose
        ;; source was quarantined), fall back to a name derived from the key so the
        ;; card is identifiable — "Guiding Hand" for :guiding-hand — instead of blank.
        display-name (or name (common/kw-to-name key true))
        level (or level 0)
        ;; When the spell has no definition, seed an edit-event that opens the spell
        ;; builder pre-filled with the key + derived name, so a dangling spell can be
        ;; DEFINED here instead of sitting un-editable.
        edit-event (or edit-event
                       (when (nil? name)
                         [::spells/edit-spell {:key key :name display-name :level level}]))]
    (t/option-cfg
     {:name (if prepend-level? (str level " - " display-name) display-name)
      :key key
      :edit-event edit-event
      :help (spell-help spell)
      :prereqs [(t/option-prereq
                 "You already know this spell"
                 (fn [c] (let [spells-known (character/spells-known c)]
                           (or (not spells-known)
                               (not-any?
                                (fn [[[_ kw]]]
                                  (= key kw))
                                (get spells-known level))))))]
      :modifiers [(modifiers/spells-known level key spellcasting-ability class-name nil qualifier)]})))


(def memoized-spell-option (memoize spell-option))

(defn missing-spell-keys
  "Spell keys in a class's spell list (a `{level #{keys}}` map) that have no
   definition in `spells-map` — an imported list names a spell whose definition or
   source didn't load. Pure; returns a set (empty when everything resolves)."
  [spell-list-by-level spells-map]
  (into #{}
        (for [[_ keyset] spell-list-by-level
              k keyset
              :when (nil? (get spells-map k))]
          k)))

(def ^:private warn-missing-spells!
  ;; Memoized so a given (class, missing-set) is reported at most once, not on
  ;; every re-render of the spell selection. cljs-only side effect.
  (memoize
   (fn [class-name missing]
     #?(:cljs (js/console.warn
               (str "\"" class-name "\" spell list references spells with no loaded "
                    "definition: " (s/join ", " (map name (sort missing)))
                    " — shown by a key-derived name; define each via its edit link.")))
     nil)))

(defn spell-options [spells-map spells spellcasting-ability class-name & [prepend-level? qualifier]]
  (map
   #(memoized-spell-option spells-map spellcasting-ability class-name % prepend-level? qualifier)
   (sort spells)))

(defn spell-level-title
  "Display title for a class's spell selection at a given level."
  [class-name level]
  (str class-name (if (and level (zero? level)) " Cantrips Known" (str " Spells Known" (when level (str " " level))))))

(defn spell-selection-key
  "Identity-derived selection key for a class's spell selection at a given
   level. Mirrors the shape spell-level-title produces but rooted in
   :class-key, not :name."
  [class-key level]
  (keyword (str (name class-key)
                (if (and level (zero? level))
                  "-cantrips-known"
                  (str "-spells-known" (when level (str "-" level)))))))

(defn spell-selection [spell-lists spells-map {:keys [title class-key level spellcasting-ability class-name num prepend-level? spell-keys options min max exclude-ref? ref]}]
  ;; Identity (kw) derives from :class-key. :class-name still feeds title
  ;; for display.
  (let [title (or title (spell-level-title class-name level))
        kw (spell-selection-key class-key level)
        ref (or ref (when (not exclude-ref?) [:class class-key kw]))]
     (t/selection-cfg
      {:name title
       :key kw
       :ref ref
       :order (if (and level (zero? level)) 0 1)
       :multiselect? true
       :options (or options
                    (spell-options
                     spells-map
                     (or spell-keys (get-in spell-lists [class-key level]))
                     spellcasting-ability
                     class-name
                     prepend-level?))
       :min (or min num)
       :max (or max num)
       :tags #{:spells}})))

(defn spell-slot-schedule [level-factor]
  (case level-factor
    1 {1 {1 2}
       2 {1 1}
       3 {1 1
          2 2}
       4 {2 1}
       5 {3 2}
       6 {3 1}
       7 {4 1}
       8 {4 1}
       9 {4 1
          5 1}
       10 {5 1}
       11 {6 1}
       13 {7 1}
       15 {8 1}
       17 {9 1}
       18 {5 1}
       19 {6 1}
       20 {7 1}}
    2 {2 {1 2}
       3 {1 1}
       5 {1 1
          2 2}
       7 {2 1}
       9 {3 2}
       11 {3 1}
       13 {4 1}
       15 {4 1}
       17 {4 1
           5 1}
       19 {5 1}}
    3 {3 {1 2}
       4 {1 1}
       7 {1 1
          2 2}
       10 {2 1}
       13 {3 2}
       16 {3 1}
       19 {4 1}}
    4 {1 {1 2}
       3 {1 1}
       5 {1 1
          2 2}
       7 {2 1}
       9 {3 2}
       11 {3 1}
       13 {4 1}
       15 {4 1}
       17 {4 1
           5 1}
       19 {5 1}}
    5 {1 {1 1}
       2 {1 2}
       3 {2 2}
       4 {2 2}
       5 {3 2}
       6 {3 2}
       7 {4 2}
       8 {4 2}
       9 {5 2}
       10 {5 2}
       11 {5 3}
       12 {5 3}
       13 {5 3}
       14 {5 3}
       15 {5 3}
       16 {5 3}
       17 {5 4}
       18 {5 4}
       19 {5 4}
       20 {5 4}}
    6 {3 {1 1}
       4 {1 2}
       5 {2 2}
       6 {2 2}
       7 {3 2}
       8 {3 2}
       9 {4 2}
       10 {4 2}
       11 {5 2}
       12 {5 2}
       13 {5 3}
       14 {5 3}
       15 {5 3}
       16 {5 3}
       17 {5 3}
       18 {5 3}
       19 {5 4}
       20 {5 4}}
    {}))

(defn total-slots [level level-factor]
  (let [schedule (spell-slot-schedule level-factor)]
    (reduce
     (fn [m lvl]
       (merge-with + m (schedule lvl)))
     {}
     (range 1 (inc level)))))

(defn spell-tags [cls-key-nm level]
  #{:spells (keyword (str cls-key-nm "-spells")) (keyword (str "level-" level))})

(defn bard-magical-secrets [spells-map min-level]
  (let [max-level (key (last (total-slots min-level 1)))
        spells-by-level (group-by :level (vals spells-map))
        filtered-spells-by-level (select-keys spells-by-level (range 0 (inc max-level)))]
    (t/selection-cfg
     {:name "Bard Magical Secrets"
      :tags #{:spells}
      :min 2
      :max 2
      :ref [:class :bard :magical-secrets]
      :options (mapcat
                (fn [[lvl spells]]
                  (map
                   (fn [{:keys [name] :as spell}]
                     (let [key (or (:key spell) (common/name-to-kw name))]
                       (spell-option spells-map ::character/cha "Bard" key true)))
                   spells))
                filtered-spells-by-level)})))

(defn cantrip-selections [spell-lists spells-map class-key class-name ability cantrips-known]
  (reduce
   (fn [m [k v]]
     (assoc m k [(spell-selection spell-lists
                                  spells-map
                                  {:class-key class-key
                                   :level 0
                                   :spellcasting-ability ability
                                   :class-name class-name
                                   :num v})]))
   {}
   cantrips-known))

(defn apply-spell-restriction [spells-map spell-keys restriction]
  (if restriction
    (filter
     (fn [spell-key]
       (restriction (spells-map spell-key)))
     spell-keys)
    spell-keys))


(defn spells-known-selections [spell-lists
                               spells-map
                               {:keys [class-key
                                       level-factor
                                       spells-known
                                       spell-list-kw
                                       known-mode
                                       spells
                                       ability
                                       slot-schedule] :as cfg}
                               cls-cfg]
  (reduce
   (fn [m [cls-lvl v]]
     (let [[num restriction] (if (number? v) [v] ((juxt :num :restriction) v))
           slots (or (when slot-schedule (slot-schedule cls-lvl)) (total-slots cls-lvl level-factor))
           all-spells (select-keys
                       (or spells (spell-lists (or spell-list-kw class-key)))
                       (keys slots))
           ;; Reconcile pre-2024 wizard-possessive spell keys (e.g.
           ;; :leomunds-secret-chest) to their current de-named SRD keys
           ;; (:secret-chest) so imported paks that reference the old names resolve
           ;; to the real spell. resolve-spell-key is non-destructive: it only
           ;; remaps a known rename whose target is loaded, so loaded homebrew and
           ;; genuinely-missing spells are left alone (and still flagged below).
           all-spells (reduce-kv
                       (fn [m lvl ks]
                         (assoc m lvl (into #{} (map #(spells/resolve-spell-key spells-map %)) ks)))
                       {} all-spells)
           ;; Raise (once) any spells this list references but that aren't defined,
           ;; so a dangling import reference is visible, not silent.
           _ (let [missing (missing-spell-keys all-spells spells-map)]
               (when (seq missing) (warn-missing-spells! (:name cls-cfg) missing)))
           acquire? (= :acquire known-mode)
           options (flatten
                      (map
                       (fn [[lvl spell-keys]]
                         (let [spell-keys (vec spell-keys)
                               filtered-keys (apply-spell-restriction spells-map spell-keys restriction)]
                           ;; spell-option derives a display name from the key when a
                           ;; spell's definition isn't loaded (an imported list names an
                           ;; undefined/quarantined spell), so the card is identifiable
                           ;; rather than blank.
                           (map
                            (fn [spell-key]
                              (memoized-spell-option
                               spells-map
                               ability
                               (:name cls-cfg)
                               spell-key
                               true))
                            filtered-keys)))
                       all-spells))]
         (assoc m cls-lvl
                [(spell-selection
                  spell-lists
                  spells-map
                  {:class-key class-key
                   :class-name (:name cls-cfg)
                   :min num
                   :max (when (not acquire?) num)
                   :options options})])))
   {}
   spells-known))

;; The richest spell path, CLASS-level only. A homebrew class's :spellcasting becomes full/half/
;; third-caster spellcasting with real spell CHOICES (spells-known-selections) and an optional
;; custom `:spell-list` (line below assoc's it into spell-lists under class-key). Subclasses/feats/
;; races cannot reach this — see the "no general parameterized spell-choice" gap in
;; docs/kb/decision-vocabulary.md.
(defn spellcasting-template [spell-lists
                             spells-map
                             {:keys [class-key
                                     level-factor
                                     cantrips-known
                                     spells-known
                                     known-mode
                                     ability
                                     spell-list] :as cfg}
                             cls-cfg]
  (let [spell-lists (if spell-list
                      (assoc spell-lists class-key spell-list)
                      spell-lists)
        spell-selections (spells-known-selections spell-lists spells-map cfg cls-cfg)
        cantrip-selections (cantrip-selections spell-lists spells-map class-key (:name cls-cfg) ability cantrips-known)]
    {:selections (merge-with
                  concat
                  cantrip-selections
                  spell-selections)}))

(defn magic-initiate-option [spells-map class-key class-name spellcasting-ability spell-lists]
  (t/option-cfg
   {:name (name class-key)
    :selections [(t/selection-cfg
                  {:name "Cantrip"
                   :order 1
                   :tags #{:spells}
                   :options (spell-options spells-map (get-in spell-lists [class-key 0]) spellcasting-ability class-name)
                   :min 2
                   :max 2})
                 (t/selection-cfg
                  {:name "Level 1 Spell"
                   :order 2
                   :tags #{:spells}
                   :options (spell-options spells-map (get-in spell-lists [class-key 1]) spellcasting-ability class-name)
                   :min 1
                   :max 1})]}))

(defn ritual-spell? [spell]
  (:ritual spell))

(defn ritual-caster-option [spells-map class-key class-name spellcasting-ability spell-lists]
  (t/option-cfg
   {:name (name class-key)
    :key class-key
    :selections [(t/selection-cfg
                  {:name "Level 1 Ritual Spells"
                   :tags #{:spells}
                   :options (spell-options
                             spells-map
                             (filter (fn [spell-kw] (ritual-spell? (spells-map spell-kw))) (get-in spell-lists [class-key 1]))
                             spellcasting-ability
                             class-name
                             false
                             "Ritual Only")
                   :min 2
                   :max 2
                   :order 7})
                 (t/selection-cfg
                  {:name "Additional Ritual Spells"
                   :tags #{:spells}
                   :show-if-zero? true
                   :multiselect? true
                   :min 0
                   :max nil
                   :order 8
                   :options (spell-options
                             spells-map
                             (filter
                              (fn [spell-kw] (ritual-spell? (spells-map spell-kw)))
                              (apply concat
                                     (vals (get spell-lists class-key))))
                             spellcasting-ability
                             class-name
                             false
                             "Ritual Only")})]}))

(defn spell-sniper-option [spells-map class-key class-name spellcasting-ability spell-lists]
  (let [options (spell-options spells-map (filter (fn [spell-kw] (:attack-roll? (spells-map spell-kw))) (get-in spell-lists [class-key 0])) spellcasting-ability class-name)]
    (t/option-cfg
     {:name (name class-key)
      :key class-key
      :prereqs [(t/option-prereq
                 "There are no attack cantrips for this class"
                 (fn [_] (seq options)))]
      :selections [(t/selection-cfg
                    {:name "Attack Cantrip"
                     :tags #{:spells}
                     :options options})]})))

(defn weapon-proficiency-selection-2 [weapon-map weapon-proficiency-options]
  (let [{num :choose options :options} weapon-proficiency-options
        weapons (if (:any options)
                  (vals weapon-map)
                  (map weapon-map (keys options)))]
    (t/selection-cfg
     {:name "Weapon Proficiency"
      :options (map
                (fn [{:keys [name key :db/id] :as item}]
                  (t/option-cfg
                   {:name (or name (::mi/name item))
                    :key key}))
                weapons)
      :multiselect? true
      :tags #{:profs}
      :min (or num 1)
      :max (or num 1)})))

(defn language-selection-aux [languages num]
  (t/selection-cfg
   {:name "Languages"
    :options (map
              (fn [lang]
                (language-option lang))
              languages)
    :ref [:languages]
    :multiselect? true
    :tags #{:profs :language-profs}
    :min (or num 0)
    :max num}))

(def ^:private language-key-corrections
  "Maps legacy/misspelled language keys to their corrected keys.
   Existing characters may reference these; the correction ensures
   they resolve to the proper language-map entry instead of generating
   a fallback with the misspelled name."
  {:primoridial :primordial})

(defn language-selection [language-map language-options]
  (let [{lang-num :choose lang-options :options} language-options
        languages (if (:any lang-options)
                    (vals language-map)
                    (map (fn [k]
                           (or (language-map k)
                               (language-map (language-key-corrections k))
                               {:name (common/kw-to-name k true) :key k}))
                         (keys lang-options)))]
    (language-selection-aux languages lang-num)))

#_ ;; unreferenced — language-selection and homebrew-language-selection used instead
(defn any-language-selection [language-map & [num]]
  (language-selection-aux (vals language-map) num))

#_(defn maneuver-option [name & [desc]]
  (t/option-cfg
   {:name name
    :modifiers [(modifiers/trait (str name " Maneuver")
                      desc)]}))

#_(defn mod-maneuver-option [name mods]
  (t/option-cfg
   {:name name
    :modifiers mods}))

(defn proficiency-help [num singular plural]
  (str "Select additional " (if (> num 1) plural singular) " for which you are proficient."))

(defn skill-selection-2 [{:keys [options num min max order key prereq-fn]}]
  (t/selection-cfg
   {:name "Skill Proficiency"
    :key key
    :order (or order 0)
    :help (proficiency-help (or num min) "a skill" "skills")
    :options (let [key-set (set options)]
               (skill-options
                (filter
                 (comp key-set :key)
                 skills/skills)))
    :min (or min num)
    :max (or max num)
    :multiselect? true
    ;;:ref [:skill-profs]
    :tags #{:skill-profs :profs}
    :prereq-fn prereq-fn}))

(defn skill-prof-or-expertise [skill-kw source]
  [(modifiers/skill-proficiency skill-kw source)
   (modifiers/skill-expertise skill-kw [(some
                                         (fn [[k v]]
                                           (not= k source))
                                         (?skill-profs skill-kw))])])

(defn tool-prof-or-expertise [tool-kw source]
  [(modifiers/tool-proficiency tool-kw false nil source)
   (modifiers/tool-expertise tool-kw [(some
                                         (fn [[k v]]
                                           (not= k source))
                                         (?tool-profs tool-kw))])])

;; dead — only called from deprecated ua_race_feats.cljc
#_(defn skill-or-expertise-selection [num skill-kws option-source]
  (t/selection-cfg
   {:name "Skill Proficiency"
    :order 0
    :tags #{:skill-profs :profs}
    :options (map
              (fn [skill-kw]
                (let [{:keys [name icon]} (skills/skills-map skill-kw)]
                  (t/option-cfg
                   {:name name
                    :icon icon
                    :modifiers [(skill-prof-or-expertise skill-kw option-source)]})))
              skill-kws)}))

(defn skill-expertise-selection [skill-kws num]
  (t/selection-cfg
   {:name "Skill Expertise (Double Proficiency)"
    :tags #{:profs}
    :min num
    :max num
    :options (map
              (fn [skill-kw]
                (let [{:keys [name icon]} (skills/skills-map skill-kw)]
                  (t/option-cfg
                   {:name name
                    :icon icon
                    :modifiers [(modifiers/skill-proficiency skill-kw)
                                (modifiers/skill-expertise skill-kw)]})))
              skill-kws)}))

(defn skill-selection
  ([num]
   (skill-selection-2 {:num num
                       :options (map :key skills/skills)}))
  ([options num & [order key prereq-fn]]
   (skill-selection-2 {:options options
                       :num num
                       :order order
                       :key key
                       :prereq-fn prereq-fn})))

(defn tool-proficiency-selection-2 [{:keys [num min max] :as cfg}]
  (t/selection-cfg
   (merge
    {:name "Tool Proficiency"
     :help (proficiency-help (or num min) "a tool" "tools")
     :multiselect 2
     :tags #{:tool-profs :profs}}
    (when num {:min num :max num})
    cfg)))

(defn tool-proficiency-selection [cfg]
  (tool-proficiency-selection-2
   cfg))

(defn tool-selection
  ([num]
   (tool-proficiency-selection
    {:options (tool-options equipment/tools)
     :num num}))
  ([options num]
   (tool-proficiency-selection
    {:options (tool-options
               (filter
                (comp (set options) :key)
                equipment/tools))
     :num num})))


(defn weapon-proficiency-selection
  ([num custom-and-standard-weapons]
   (t/selection-cfg
    {:name "Weapon Proficiency"
     :help (proficiency-help num "a weapon" "weapons")
     :options (weapon-proficiency-options custom-and-standard-weapons)
     :min num
     :max num
     :tags #{:weapon-profs :profs}}))
  ([options num custom-and-standard-weapons]
   (t/selection-cfg
    {:name "Weapon Proficiency"
     :help (proficiency-help num "a weapon" "weapons")
     :options (weapon-proficiency-options
               (filter
                (comp (set options) :key)
                custom-and-standard-weapons))
     :min num
     :max num
     :tags #{:weapon-profs :profs}})))

(defn skilled-selection [title]
  (t/selection-cfg
   {:name title
    :tags #{:profs}
    :options [(t/option-cfg
              {:name "Skill"
               :selections [(skill-selection 1)]})
             (t/option-cfg
              {:name "Tool"
               :selections [(tool-selection 1)]})]}))

#_(def maneuver-options
  [(maneuver-option "Commander's Strike"
                    "When you take Attack action, forgo one attack, expend a superiority die, give a creature an immediate reaction attack, adding superiority die to damage")
   (maneuver-option "Disarming Attack"
                    "When you hit with a weapon attack, expend a superiority die and force the target to drop an item of your choice on failed STR save")
   (maneuver-option "Distracting Strike"
                    "When you hit with a weapon attack, expend a superiority die, add die to damage, give advantage to next attack roll by someone else against the creature")
   (maneuver-option "Evasive Footwork"
                    "Add superiority die to AC when moving")
   (mod-maneuver-option
    "Feinting Attack"
    [(modifiers/bonus-action
      {:name "Feinting Attack Maneuver"
       :page 74
       :summary "feint attack on a creature and gain advantage on next attack against it, adding superiority die to damage"})])
   (mod-maneuver-option
    "Goading Attack"
    [(modifiers/dependent-trait
      {:name "Goading Attack Maneuver"
       :page 74
       :summary (str "add superiority die to a successful attack's damage, if target fails DC " ?maneuver-save-dc " WIS save, the next attack it makes must be against you or have disadvantage")})])
   (maneuver-option "Lunging Attack"
                    "increase melee attack reach by 5 ft., add superiority die to damage")
   (maneuver-option "Manuevering Attack"
                    "add superiority die to a successful attack's damage, choose a friendly creature that can move half it's speed as a reaction without opportunity attack from attack target")
   (mod-maneuver-option
    "Menacing Attack"
    [(modifiers/dependent-trait
      {:name "Menacing Attack Maneuver"
       :page 74
       :summary (str "add superiority die to a successful attack's damage, if target fails DC " ?maneuver-save-dc " WIS save, it becomes frightened of you until your next turn")})])
   (mod-maneuver-option
    "Parry"
    [(modifiers/reaction
      {:name "Parry Maneuver"
       :page 74
       :summary (str "reduce melee attack damage dealt to you by superiority die roll " (common/mod-str (?ability-bonuses ::character/dex)))})])
   (maneuver-option "Precision Attack"
                    "add superiority die to weapon attack roll")
   (mod-maneuver-option
    "Pushing Attack"
    [(modifiers/dependent-trait
      {:name "Pushing Attack Maneuver"
       :page 74
       :summary (str "add superiority die to a successful attack's damage, if target is Large or smaller and fails a DC " ?maneuver-save-dc " STR save, it is pushed 15 ft. away")})])
   (mod-maneuver-option
    "Rally"
    [(modifiers/bonus-action
      {:name "Rally Maneuver"
       :page 74
       :summary (str "give superiority die "
                     (common/mod-str (?ability-bonuses ::character/cha))
                     " temp HPs to a friendly creature")})])
   (mod-maneuver-option
    "Riposte"
    [(modifiers/reaction
      {:name "Riposte Maneuver"
       :page 74
       :summary "if a creature misses you with a melee attack, attack as a reaction and add superiority die to damage"})])
   (maneuver-option "Sweeping Attack"
                    "if you hit a creature with an attack roll, choose another creature within 5 ft., if the roll would hit the creature, it takes superiority die worth of damage")
   (mod-maneuver-option
    "Trip Attack"
    [(modifiers/dependent-trait
      {:name "Trip Attack Maneuver"
       :page 74
       :summary (str "add superiority die to successful attack's damage, if target fails a DC " ?maneuver-save-dc " STR save, it is knocked prone")})])])

(def can-cast-spell-prereq
  (t/option-prereq "Requires the ability to cast at least one spell."
                   (fn [c] (some (fn [[k v]] (seq v)) (character/spells-known c)))))

(defn does-not-have-feat-prereq [kw]
  {::t/label "You already have this feat."
   ::t/prereq-fn (fn [c] (let [feats (character/feats c)]
                           (not (and feats (feats kw)))))})

(defn feat-option [cfg & [multiselect?]]
  (let [kw (common/name-to-kw (:name cfg))
        summary (:summary cfg)]
    (t/option-cfg
     (cond-> cfg
       true (assoc :key kw :help summary)
       (not (:exclude-trait? cfg)) (update :modifiers
                                           conj
                                           (modifiers/trait-cfg
                                            {:name (str (:name cfg) " Feat")
                                             :page (:page cfg)
                                             :source (:source cfg)
                                             :summary summary}))
       true (update :modifiers
                    conj
                    (mods/set-mod ?feats kw))
       (not multiselect?) (update :prereqs conj (does-not-have-feat-prereq kw))))))

;; dead — zero callers
#_(def charge-summary "when you Dash, you can make 1 melee attack or shove as a bonus action; if you move 10 ft. before taking this bonus action you gain +5 damage to attack or shove 10 ft.")

;; dead — zero callers
#_(def defensive-duelist-summary "when you are hit with a melee attack, you can add your prof bonus to AC for the attack if you are wielding a finesse weapon you are proficient with")

#_(defn homebrew-spell-selection [spell-lists spells-map]
  (spell-selection
   spell-lists
   spells-map
   {:class-key :homebrew
    :class-name "Homebrew"
    :ref [:optional-content :homebrew :spells-known]
    :min 0
    :max nil
    :spell-keys (keys spells-map)}))

(def homebrew-tool-prof-selection
  (tool-proficiency-selection-2
   {:min 0
    :max nil
    :multiselect? true
    :ref [:tool-profs]
    :options (tool-options equipment/tools)}))

(def homebrew-skill-prof-selection
  (skill-selection-2 {:min 0
                      :max nil
                      :options (map :key skills/skills)}))

(defn homebrew-language-selection [language-map & [min max]]
  (t/selection-cfg
   {:name "Languages"
    :options (map
              (fn [lang]
                (language-option lang))
              (vals language-map))
    :multiselect? true
    :tags #{:profs :language-profs}
    :min (or min 0)
    :max max}))

(def homebrew-armor-prof-selection
  (t/selection-cfg
   {:name "Armor Proficiency"
    :key :armor-prof
    :tags #{:profs}
    :min 0
    :max nil
    :multiselect? true
    :options (map
              (fn [armor-type]
                (t/option-cfg
                 {:name (s/capitalize (name armor-type))
                  :key armor-type
                  :modifiers [(modifiers/armor-proficiency armor-type)]}))
              [:light :medium :heavy :shields])}))

(defn homebrew-weapon-prof-selection [weapon-map]
  (t/selection-cfg
   {:name "Weapon Proficiency"
    :key :weapon-prof
    :tags #{:profs}
    :min 0
    :max nil
    :multiselect? true
    :options (map
              (fn [{:keys [name key]}]
                (t/option-cfg
                 {:name name
                  :key key
                  :modifiers [(modifiers/weapon-proficiency key)]}))
              (conj
               (vals weapon-map)
               {:name "Simple"
                :key :simple}
               {:name "Martial"
                :key :martial}))}))

(def dual-wield-ac-mod
  (mods/vec-mod ?ac-bonus-fns
                (fn [_ _] 1)
                nil
                nil
                [(let [main-hand-weapon ?orcpub.dnd.e5.character/main-hand-weapon
                       off-hand-weapon ?orcpub.dnd.e5.character/off-hand-weapon
                       all-weapons-map (mi/compute-all-weapons-map
                                        ;; include view-once shared custom items so their conditional modifiers apply too
                                        (concat (get @re-frame.db/app-db ::mi/custom-items)
                                                (get @re-frame.db/app-db :shared-custom-items)))]
                   (and main-hand-weapon
                        (-> all-weapons-map
                            main-hand-weapon
                            ::weapons/melee?)
                        off-hand-weapon
                        (-> all-weapons-map
                            off-hand-weapon
                            ::weapons/melee?)))]))

(def dual-wield-weapon-mod
  (mods/modifier ?dual-wield-weapon? weapons/one-handed-weapon?))

(def medium-armor-master-max-bonus
  (modifiers/armor-dex-cap :medium 3))

(def medium-armor-master-stealth
  (mods/fn-mod ?armor-stealth-disadvantage?
               (fn [armor]
                 (if (= :medium (:type armor))
                   false
                   (?armor-stealth-disadvantage? armor)))))

(defn custom-option-builder
  "Renders a name input that dispatches name-event with the typed value.
   When inject-template? is true, also passes the built template via dispatch
   for handlers that need entity/get-option-value-path (set-custom-subclass,
   set-custom-feat-name)."
  ([name-sub name-event] (custom-option-builder name-sub name-event false))
  ([name-sub name-event inject-template?]
   (let [built-template (when inject-template? @(subscribe [:built-template]))]
     [:div.m-t-10
      [:span "Name"]
      [comps/input-field
       :input
       @(subscribe name-sub)
       (fn [value]
         (dispatch (cond-> name-event
                     built-template (conj built-template)
                     true (conj value))))
       {:class "input"}]])))

(defn feat-options [spell-lists spells-map]
  [#_(feat-option
      {:name "Alert"
       :icon "look-at"
       :page 165
       :summary "+5 initiative; can't be surprised; creatures don't gain advantage on attacks against you for being hidden"
       :modifiers [(modifiers/initiative 5)]})
   #_(feat-option
      {:name "Athlete"
       :icon "weight-lifting-up"
       :page 165
       :summary "increase STR or DEX by 1; standing up only uses 5 ft movement; climbing doesn't cost extra movement; make running long or high jump after moving only 5 ft."
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)]})
   #_(feat-option
      {:name "Actor"
       :icon "drama-masks"
       :page 165
       :summary "increase CHA by 1; advantage on Deception and Performance when trying to pass as someone else; mimic the speech of a person you have heard"
       :modifiers [(modifiers/ability ::character/cha 1)]})
   #_(feat-option
      {:name "Charger"
       :icon "charging-bull"
       :page 165
       :summary charge-summary
       :modifiers [(modifiers/bonus-action
                    {:name "Charge"
                     :page 165
                     :summary charge-summary})]})
   #_(feat-option
      {:name "Crossbow Expert"
       :icon "crossbow"
       :page 165
       :summary "ignore loading property of crossbows you are proficient with; don't have disadvantage from being within 5 ft of hostile creature; when you Attack with 1 hand weapon, you can attack with a hand crossbow as bonus action"
       :modifiers [(modifiers/bonus-action
                    {:name "Crossbow Expert"
                     :page 165
                     :summary "when you Attack with 1 hand weapon, you can attack with a hand crossbow"})]})
   #_(feat-option
      {:name "Defensive Duelist"
       :icon "spinning-sword"
       :page 165
       :exclude-trait? true
       :summary defensive-duelist-summary
       :modifiers [(modifiers/reaction
                    {:name "Defensive Duelist"
                     :page 165
                     :summary defensive-duelist-summary})]
       :prereqs [(ability-prereq ::character/dex 13)]})
   #_(feat-option
      {:name "Dual Wielder"
       :icon "rogue"
       :page 165
       :summary "+1 AC bonus when wielding two melee weapons; two-weapon fighting with any one-handed melee weapon"
       :modifiers [dual-wield-weapon-mod
                   dual-wield-ac-mod]})
   #_(feat-option
      {:name "Dungeon Delver"
       :icon "dungeon-gate"
       :page 166
       :summary "advantage to detect secret doors; advantage on saves against and resistance to trap damage; search for traps at normal pace"
       :modifiers [(modifiers/damage-resistance :trap)
                   (modifiers/saving-throw-advantage [:traps])]})
   #_(feat-option
      {:name "Durable"
       :icon "hospital-cross"
       :page 166
       :exclude-trait? true
       :summary "increase CON by 1; when you roll Hit Die to regain HPs, the min points regained is 2X your CON modifier"
       :modifiers [(modifiers/ability ::character/con 1)
                   (modifiers/dependent-trait
                    {:name "Durable"
                     :page 166
                     :summary (str "when you roll Hit Die to regain HPs, the min points regained is " (* 2 (?ability-bonuses ::character/con)))})]})
   #_(feat-option
      {:name "Elemental Adept"
       :icon "wind-hole"
       :page 166
       :summary "select a damage type, your spells ignore resistance to that type and min damage die roll is 2"
       :prereqs [can-cast-spell-prereq]}
      true)
   (feat-option
    {:name "Grappler"
     :icon "muscle-up"
     :page 167
     :summary "advantage on attacks against creature you grapple; can use an action to pin the creature"
     :modifiers [(modifiers/action
                  {:name "Grappler"
                   :page 167
                   :summary "restrain a creature you are grappling"})]
     :prereqs [(ability-prereq ::character/str 13)]})
   #_(feat-option
      {:name "Great Weapon Master"
       :icon "broadsword"
       :page 167
       :summary "When you critical or reduce a creature to 0 HPs with melee weapon, make one melee weapon attack as bonus action. When you melee Attack with heavy weapon, you can take -5 on attack to deal +10 damage."
       :modifiers [(modifiers/bonus-action
                    {:name "Great Weapon Master"
                     :page 167
                     :summary "When you critical or reduce a creature to 0 HPs with melee weapon, make one melee weapon attack"})]})
   #_(feat-option
      {:name "Healer"
       :icon "medical-pack-alt"
       :page 167
       :summary "When you stabilize with healer's kit, the creature regains 1 HP; use a healer's kit to restore 1d6 + 4 + creature's max hit dice HPs"
       :modifiers [(modifiers/action
                    {:name "Healer Feat"
                     :page 167
                     :summary "use a healer's kit to restore 1d6 + 4 + creature's max hit dice HPs"})]})
   #_(feat-option
      {:name "Heavily Armored"
       :icon "lamellar"
       :summary "increase STR by 1; proficiency in heavy armor"
       :page 167
       :modifiers [(modifiers/heavy-armor-proficiency)
                   (modifiers/ability ::character/str 1)]
       :prereqs [(armor-prereq :medium)]})
   #_(feat-option
      {:name "Heavy Armor Master"
       :icon "gauntlet"
       :page 167
       :summary "increase STR by 1; when wearing heavy armor, slashing, piercing, and bludgeoning damage from non-magical weapons is 3 less"
       :modifiers [(modifiers/ability ::character/str 1)]
       :prereqs [(armor-prereq :heavy)]})
   #_(feat-option
      {:name "Inspiring Leader"
       :icon "public-speaker"
       :page 167
       :summary "give 6 friendly creatures within 30 ft. temp HPs equal to you CHA mod + your level"
       :prereqs [(ability-prereq ::character/cha 13)]})
   #_(feat-option
      {:name "Keen Mind"
       :icon "brain"
       :page 167
       :summary "increase INT by 1; always know which direction is north; know hours before sunset or sunrise; recall anything heard or seen within a month"
       :modifiers [(modifiers/ability ::character/int 1)]})
   #_(feat-option
      {:name "Lightly Armored"
       :icon "scale-mail"
       :page 167
       :summary "increase STR or DEX by 1; proficiency in light armor"
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)]
       :modifiers [(modifiers/light-armor-proficiency)]})
   #_(feat-option
      {:name "Linguist"
       :icon "lips"
       :page 167
       :summary "increase INT by 1; learn 3 languages; create written ciphers"
       :selections [(language-selection languages 3)]
       :modifiers [(modifiers/ability ::character/int 1)]})
   #_(feat-option
      {:name "Lucky"
       :icon "clover"
       :page 167
       :summary "3 luck points, which you can use to roll an additional d20 when rolling an attack, save, or ability check, and choose which one to use"})
   #_(feat-option
      {:name "Mage Slayer"
       :icon "zeus-sword"
       :page 168
       :summary "use reaction to attack a caster within 5 ft.; impose disadvantage to a caster's concentration check when you attack; advantage on saves against spells cast within 5ft."
       :modifiers [(modifiers/reaction
                    {:name "Mage Slayer"
                     :range units5e/ft-5
                     :page 168
                     :summary "attack a creature that casts a spell"})]})
   #_(feat-option
      {:name "Magic Initiate"
       :icon "magic-palm"
       :page 168
       :summary "gain 2 cantrips and 1 1st level spell from a chosen class"
       :selections [(t/selection-cfg
                     {:name "Spell Class"
                      :order 0
                      :tags #{:spells}
                      :options [(magic-initiate-option :bard "Bard" ::character/cha sl/spell-lists)
                                (magic-initiate-option :cleric "Cleric" ::character/wis sl/spell-lists)
                                (magic-initiate-option :druid "Druid" ::character/wis sl/spell-lists)
                                (magic-initiate-option :sorcerer "Sorcerer" ::character/cha sl/spell-lists)
                                (magic-initiate-option :warlock "Warlock" ::character/cha sl/spell-lists)
                                (magic-initiate-option :wizard "Wizard" ::character/int sl/spell-lists)]})]})
   #_(feat-option
      {:name "Martial Adept"
       :icon "visored-helm"
       :page 168
       :summary "learn two Battle Master martial maneuvers using 1 d6 superiority die"
       :selections [(t/selection-cfg
                     {:name "Martial Maneuvers"
                      :tags #{:class}
                      :options maneuver-options
                      :min 2
                      :max 2})]})
   #_(feat-option
      {:name "Medium Armor Master"
       :icon "bracers"
       :page 168
       :summary "medium armor doesn't give disadvantage to Stealth; max DEX bonus to AC is 3 for medium armor"
       :modifiers [medium-armor-master-max-bonus
                   medium-armor-master-stealth]
       :prereqs [(armor-prereq :medium)]})
   #_(feat-option
      {:name "Mobile"
       :icon "move"
       :page 168
       :summary "speed increases by 10 ft.; Dash through difficult terrain doesn't cost extra movement; don't provoke opportunity attacks from a creature you made a melee attack against"
       :modifiers [(modifiers/speed 10)]})
   #_(feat-option
      {:name "Moderately Armored"
       :icon "shoulder-armor"
       :page 168
       :summary "increase STR or DEX by 1; gain proficiency with shields and medium armor"
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)]
       :modifiers [(modifiers/medium-armor-proficiency)
                   (modifiers/shield-armor-proficiency)]
       :prereqs [(armor-prereq :light)]})
   #_(feat-option
      {:name "Mounted Combatant"
       :icon "cavalry"
       :page 168
       :summary "while mounted: advantage on attacks against unmounted creatures smaller than mount, force attack on mount to target you; mount takes no damage on sucessful DEX saves and half on failed"})
   #_(feat-option
      {:name "Observant"
       :icon "surrounded-eye"
       :page 168
       :summary "increase INT or WIS by 1; read lips; +5 bonus to passive Perception and passive Investigation"
       :selections [(ability-increase-selection [::character/int ::character/wis] 1 false)]
       :modifiers [(modifiers/passive-perception 5)
                   (modifiers/passive-investigation 5)]})
   #_(feat-option
      {:name "Polearm Master"
       :icon "halberd"
       :page 168
       :exclude-trait? true
       :summary "bonus attack with opposite end of quarterstaff, glaive, or halberd; opportunity attacks have the reach of glaive, pike, halberd, or quarterstaff"
       :modifiers [(modifiers/bonus-action
                    {:name "Polearm Master"
                     :page 168
                     :summary "when you make an Attack with a glaive, quarterstaff, or halberd, make an additionaal melee attack with the other end of the weapon, dealing d4 bludgeoning damage"})]})
   #_(feat-option
      {:name "Resilient"
       :icon "dodging"
       :page 168
       :summary "increase ability by 1 and gain proficiency in saves with that ability"
       :selections [(ability-increase-selection
                     character/ability-keys
                     1
                     false
                     [(fn [k] (modifiers/saving-throws nil k))])]})
   #_(feat-option
      {:name "Ritual Caster"
       :icon "gift-of-knowledge"
       :page 169
       :summary "choose a spellcaster class and learn 2 rituals from that class"
       :selections [(t/selection-cfg
                     {:name "Ritual Caster: Spell Class"
                      :tags #{:spells}
                      :options [(ritual-caster-option :bard "Bard" ::character/cha sl/spell-lists)
                                (ritual-caster-option :cleric "Cleric" ::character/wis sl/spell-lists)
                                (ritual-caster-option :druid "Druid" ::character/wis sl/spell-lists)
                                (ritual-caster-option :sorcerer "Sorcerer" ::character/cha sl/spell-lists)
                                (ritual-caster-option :warlock "Warlock" ::character/cha sl/spell-lists)
                                (ritual-caster-option :wizard "Wizard" ::character/int sl/spell-lists)]})]
       :prereqs [(t/option-prereq "Requires Intelligence or Wisdom 13 or higher"
                                  (fn [c]
                                    (let [{:keys [::character/wis ::character/int] :as abilities} (character/ability-values c)]
                                      (or (and wis (>= wis 13))
                                          (and int (>= int 13))))))]})
   #_(feat-option
      {:name "Savage Attacker"
       :icon "saber-slash"
       :page 169
       :summary "reroll melee weapon attack damage and use either total"})
   #_(feat-option
      {:name "Sentinal"
       :icon "guards"
       :page 169
       :summary "reduce target's speed to 0 when you hit with opportunity attack; opportunity attacks even when target Disengages; use reaction to make a weapon attack against a creature within 5 ft. that attacks another target"})
   #_(feat-option
      {:name "Sharpshooter"
       :icon "bullseye"
       :page 170
       :summary "no disadvantage for long range; ignore half and 3/4 cover; take -5 to ranged attack to gain +10 on damage"})
   #_(feat-option
      {:name "Shield Master"
       :icon "attached-shield"
       :page 170
       :summary "when Attacking use bonus action to shove; add shield's AC bonus to saves that target just you; take no damage on a sucessful save"
       :modifiers [(modifiers/bonus-action
                    {:name "Shield Master: Shove"
                     :page 170
                     :summary "make a shove with shield when taking the Attack action"})]})
   #_(feat-option
      {:name "Skilled"
       :icon "juggler"
       :page 170
       :summary "proficiency in three skills and/or tools"
       :selections [(skilled-selection "Skill/Tool 1")
                    (skilled-selection "Skill/Tool 2")
                    (skilled-selection "Skill/tool 3")]})
   #_(feat-option
      {:name "Skulker"
       :icon "ghost-ally"
       :page 170
       :summary "hide when lightly obscured; when hiding, missing an attack doesn't reveal you; no disadvantage on Perception checks in dim light"
       :prereqs [(ability-prereq ::character/dex 13)]})
   #_(feat-option
      {:name "Spell Sniper"
       :icon "laser-precision"
       :page 170
       :summary "attack spells have double range; ignore half and 3/4 cover; learn a cantrip that requires an attack roll"
       :prereqs [can-cast-spell-prereq]
       :selections [(t/selection-cfg
                     {:name "Spell Sniper: Spell Class"
                      :tags #{:spells}
                      :options [(spell-sniper-option :bard "Bard" ::character/cha sl/spell-lists)
                                (spell-sniper-option :cleric "Cleric" ::character/wis sl/spell-lists)
                                (spell-sniper-option :druid "Druid" ::character/wis sl/spell-lists)
                                (spell-sniper-option :sorcerer "Sorcerer" ::character/cha sl/spell-lists)
                                (spell-sniper-option :warlock "Warlock" ::character/cha sl/spell-lists)
                                (spell-sniper-option :wizard "Wizard" ::character/int sl/spell-lists)]})]})
   #_(feat-option
      {:name "Tavern Brawler"
       :icon "broken-bottle"
       :page 170
       :summary "increase STR or CON by 1; improvised weapon proficiency; d4 damage on unarmed strike; grapple as bonus action"
       :selections [(ability-increase-selection [::character/str ::character/con] 1 false)]
       :modifiers [(modifiers/weapon-proficiency :improvised)
                   (modifiers/bonus-action
                    {:name "Tavern Brawler: Grapple"
                     :page 170
                     :summary "attempt grapple when you hit with improvised weapon or unarmed strike"})]})
   #_(feat-option
      {:name "Tough"
       :icon "defensive-wall"
       :page 170
       :summary "2 extra HPs per level"
       :modifiers [(mods/modifier ?hit-point-level-bonus (+ 2 ?hit-point-level-bonus))]})
   #_(feat-option
      {:name "War Caster"
       :icon "deadly-strike"
       :page 170
       :summary "adv. on CON saves for spell concentration; somatic components with weapons or shield in hand; cast spell as opporunity attack"
       :prereqs [can-cast-spell-prereq]})
   #_(feat-option
      {:name "Weapon Master"
       :icon "sword-slice"
       :page 170
       :summary "increase STR or DEX by 1; proficiency with 4 weapons"
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)
                    (weapon-proficiency-selection 4)]})]
  #_(map
   (fn [i]
     (t/option-cfg
      (let [kw (keyword (str "custom-feat-" i))]
        {:name (str "Custom Feat " (inc i))
         :key kw
         :icon "beer-stein"
         :order (inc i)
         :ui-fn #(custom-option-builder
                  [:custom-feat-name [:feats kw]]
                  [:set-custom-feat-name [:feats kw]]
                  true)
         :selections [(t/selection-cfg
                       {:name "Feat Modifiers"
                        :min 0
                        :max nil
                        :multiselect? true
                        :order 2
                        :tags #{:feats}
                        :options [(t/option-cfg
                                   {:name "Tool Proficiency or Expertise"
                                    :help "Gain proficiency in a particular tool or expertise if you already have a proficiency in the tool (select on the 'Proficiencies' tab)."
                                    :selections [(t/selection-cfg
                                                  {:name "Tool Proficiency or Expertise"
                                                   :tags #{:profs}
                                                   :options (map
                                                             (fn [{:keys [name key]}]
                                                               (t/option-cfg
                                                                {:name name
                                                                 :key key
                                                                 :modifiers [(tool-prof-or-expertise key kw)]}))
                                                             equipment/tools)})]})
                                  (t/option-cfg
                                   {:name "Skill Proficiency or Expertise"
                                    :help "Gain proficiency in a particular skill or expertise if you already have proficiency in it"
                                    :selections [(t/selection-cfg
                                                  {:name "Skill Proficiency or Expertise"
                                                   :tags #{:profs}
                                                   :options (map
                                                             (fn [{:keys [name key]}]
                                                               (t/option-cfg
                                                                {:name name
                                                                 :key key
                                                                 :modifiers [(skill-prof-or-expertise key kw)]}))
                                                             skills/skills)})]})
                                  (t/option-cfg
                                   {:name "Ability Score Increase"
                                    :help "This will allow you to select and ability score to increase by 1 (see the 'Abilities Variant' section above)"
                                    :selections [(ability-increase-selection character/ability-keys 1 false)]})
                                  (t/option-cfg
                                   {:name "Extra 2 HPs Per Level"
                                    :help "This will give you an extra 2 HPs per level"
                                    :modifiers [(mods/modifier ?hit-point-level-bonus (+ 2 ?hit-point-level-bonus))]})
                                  (t/option-cfg
                                   {:name "Speed +10"
                                    :help "Increase your speed by 10 ft."
                                    :modifiers [(modifiers/speed 10)]})
                                  (t/option-cfg
                                   {:name "Passive Perception +5"
                                    :help "Increase your passive perception by 5"
                                    :modifiers [(modifiers/passive-perception 5)]})
                                  (t/option-cfg
                                   {:name "Passive Investigation +5"
                                    :help "Increase your passive investigation by 5"
                                    :modifiers [(modifiers/passive-perception 5)]})
                                  (t/option-cfg
                                   {:name "Save Proficiency"
                                    :help "Select proficiency in saving throws with a particular ability (select on the 'Proficiencies' tab)"
                                    :selections [(t/selection-cfg
                                                  {:name "Saving Throw Proficiency"
                                                   :tags #{:profs}
                                                   :options (map
                                                             (fn [k]
                                                               (t/option-cfg
                                                                {:name (:name (abilities-map k))
                                                                 :key k
                                                                 :modifiers [(modifiers/saving-throws nil k)]}))
                                                             character/ability-keys)})]})
                                  (t/option-cfg
                                   {:name "Initiative +5"
                                    :help "This will increase your initiative by 5."
                                    :modifiers [(modifiers/initiative 5)]})
                                  (t/option-cfg
                                   {:name "Weapon Proficiency"
                                    :help "This will allow you to select weapon proficiencies, from 'Simple', 'Martial', or specific weapons (select on the 'Proficiencies' tab)."
                                    :selections [homebrew-weapon-prof-selection]})
                                  (t/option-cfg
                                   {:name "Improvised Weapons Proficiency"
                                    :help "Gain proficiency in improvised weapons, such as broken bottles"})
                                  (t/option-cfg
                                   {:name "Armor Proficiency"
                                    :help "This will allow you to select armor proficiencies, from 'Shields', 'Light', 'Medium', or 'Heavy' (select on the 'Proficiencies' tab)."
                                    :selections [homebrew-armor-prof-selection]})
                                  (t/option-cfg
                                   {:name "Medium Armor: Max DEX Bonus of 3"
                                    :help "This will set your max dexterity bonus with medium armor to 3 instead of 2"
                                    :modifiers [medium-armor-master-max-bonus]})
                                  (t/option-cfg
                                   {:name "Ritual Spells"
                                    :help "Learn 2 ritual spells from a particular class"
                                    :selections [(t/selection-cfg
                                                  {:name "Spellaster Class"
                                                   :tags #{:spells}
                                                   :options [(ritual-caster-option spells-map :bard "Bard" ::character/cha spell-lists)
                                                             (ritual-caster-option spells-map :cleric "Cleric" ::character/wis spell-lists)
                                                             (ritual-caster-option spells-map :druid "Druid" ::character/wis spell-lists)
                                                             (ritual-caster-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
                                                             (ritual-caster-option spells-map :warlock "Warlock" ::character/cha spell-lists)
                                                             (ritual-caster-option spells-map :wizard "Wizard" ::character/int spell-lists)]})]})
                                  (t/option-cfg
                                   {:name "Three Skills or Tools"
                                    :help "Select proficiency in three skills or tools"
                                    :selections [(skilled-selection "Skill/Tool 1")
                                                 (skilled-selection "Skill/Tool 2")
                                                 (skilled-selection "Skill/tool 3")]})
                                  (t/option-cfg
                                   {:name "Attack Cantrip"
                                    :help "Select a cantrip that requires an attack roll"
                                    :selections [(t/selection-cfg
                                                  {:name "Attack Cantrip Class"
                                                   :tags #{:spells}
                                                   :options [(spell-sniper-option spells-map :bard "Bard" ::character/cha spell-lists)
                                                             (spell-sniper-option spells-map :cleric "Cleric" ::character/wis spell-lists)
                                                             (spell-sniper-option spells-map :druid "Druid" ::character/wis spell-lists)
                                                             (spell-sniper-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
                                                             (spell-sniper-option spells-map :warlock "Warlock" ::character/cha spell-lists)
                                                             (spell-sniper-option spells-map :wizard "Wizard" ::character/int spell-lists)]})]})
                                  (t/option-cfg
                                   {:name "Medium Armor: Stealthy"
                                    :help "This will allow you to use medium armor without stealth disadvantage"
                                    :modifiers [medium-armor-master-stealth]})
                                  (t/option-cfg
                                   {:name "Language Proficiency"
                                    :help "This will allow you to select language proficiencies"
                                    :selections [(homebrew-language-selection)]})
                                  (t/option-cfg
                                   {:name "Dual Wielding: AC +1"
                                    :help "When wielding two-weapons, this will give you a +1 bonus to AC."
                                    :key :dual-wield-ac-mod
                                    :modifiers [dual-wield-ac-mod]})
                                  (t/option-cfg
                                   {:name "Dual Wielding: Any One-Handed Melee Weapon"
                                    :help "This will allow you to engage in two-weapon fighting with any two single-handed melee weapons"
                                    :key :dual-wield-weapon-mod
                                    :modifiers [dual-wield-weapon-mod]})
                                  (t/option-cfg
                                   {:name "Spellcasting"
                                    :help "Select low-level spells from a particular class"
                                    :selections [(t/selection-cfg
                                                  {:name "Spell Class"
                                                   :order 0
                                                   :tags #{:spells}
                                                   :options [(magic-initiate-option spells-map :bard "Bard" ::character/cha spell-lists)
                                                             (magic-initiate-option spells-map :cleric "Cleric" ::character/wis spell-lists)
                                                             (magic-initiate-option spells-map :druid "Druid" ::character/wis spell-lists)
                                                             (magic-initiate-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
                                                             (magic-initiate-option spells-map :warlock "Warlock" ::character/cha spell-lists)
                                                             (magic-initiate-option spells-map :wizard "Wizard" ::character/int spell-lists)]})]})]})]})))
   (range 10)))

(def fighting-style-options
  [(t/option-cfg
    {:name "Archery"
     :modifiers [(modifiers/ranged-attack-bonus 2)
      (modifiers/trait-cfg
       {:name "Archery Fighting Style"
        :page 72
        :description "You gain a +2 bonus to attack rolls you make with ranged weapons."})]})
   (t/option-cfg
    {:name "Defense"
     :modifiers [(modifiers/armored-ac-bonus 1)
      (modifiers/trait-cfg
       {:name "Defense Fighting Style"
        :page 72
        :description "While you are wearing armor, you gain a +1 bonus to AC."})]})
   (t/option-cfg
    {:name "Dueling"
     :modifiers [(modifiers/trait-cfg
       {:name "Dueling Fighting Style"
        :page 72
        :description "When you are wielding a melee weapon in one hand and no other weapons, you gain a +2 bonus to damage rolls with that weapon."})
                (mods/vec-mod ?damage-bonus-fns ;vec-mod prop
                              (fn [weapon _] (if (or (weapon ::weapons/two-handed?)
                                                     (weapon ::weapons/ranged?)) 0 2)) ;vec-mod val ... maybe?
                              nil ;vec-mod nm
                              nil ;vec-mod value ... maybe?
                              [(let [main-hand-weapon ?orcpub.dnd.e5.character/main-hand-weapon
                                     off-hand-weapon ?orcpub.dnd.e5.character/off-hand-weapon
                                     all-weapons-map (mi/compute-all-weapons-map
                                                      ;; include view-once shared custom items so their conditional modifiers apply too
                                        (concat (get @re-frame.db/app-db ::mi/custom-items)
                                                (get @re-frame.db/app-db :shared-custom-items)))]
                                 (and main-hand-weapon
                                      (-> all-weapons-map
                                          main-hand-weapon
                                          ::weapons/melee?)
                                      (not (-> all-weapons-map
                                               main-hand-weapon
                                               ::weapons/two-handed?))
                                      off-hand-weapon
                                      (not (-> all-weapons-map ;ensure no weapons in off hand
                                               off-hand-weapon
                                               ::weapons/type))))])
                 ]})
   (t/option-cfg
    {:name "Great Weapon Fighting"
     :modifiers [(modifiers/trait-cfg
       {:name "Great Weapon Fighting Style"
        :page 72
        :description "When you roll a 1 or 2 on a damage die for an attack you make with a melee weapon that you are wielding with two hands, you can reroll the die and must use the new roll, even if the new roll is a 1 or a 2. The weapon must have the two-handed or versatile property for you to gain this benefit."})]})
   (t/option-cfg
    {:name "Protection"
     :modifiers [(modifiers/reaction
       {:name "Protection Fighting Style"
        :page 72
        :description "When a creature you can see attacks a target other than you that is within 5 feet of you, you can use your reaction to impose disadvantage on the attack roll. You must be wielding a shield."})]})
   (t/option-cfg
    {:name"Two Weapon Fighting"
     :modifiers [(modifiers/trait-cfg
                  {:name "Two Weapon Fighting"
                   :description "When you engage in two-weapon fighting, you can add your ability modifier to the damage of the second attack."})
                 (mods/modifier ?weapon-ability-damage-modifier
                                (fn [weapon finesse? _]
                                  (?weapon-ability-modifier weapon finesse?)))]})])

(defn fighting-style-selection-2 [class-kw num options]
  (t/selection-cfg
   {:name "Fighting Style"
    :tags #{:class}
    :ref [:class class-kw :fighting-style]
    :multiselect? true
    :min num
    :max num
    :options options}))

(defn fighting-style-selection [class-kw & [restrictions additional-options]]
  (fighting-style-selection-2
   class-kw
   1
   (if restrictions
     (filter
      (fn [o]
        (restrictions (::t/key o)))
      fighting-style-options)
     fighting-style-options)))

(defn feat-selection [spell-lists spells-map num]
  (t/selection-cfg
   {:name "Feats"
    :options (feat-options spell-lists spells-map)
    :multiselect? true
    :tags #{:feats}
    :ref [:feats]
    :show-if-zero? true
    :min num
    :max num}))

(defn ability-score-improvement-selection [spell-lists spells-map cls lvl]
  (t/selection-cfg
   {:name "Ability Score Improvement or Feat"
    :key :asi-or-feat
    :tags #{:ability-scores}
    :options [(ability-increase-option 2 false character/ability-keys)
              (t/option-cfg
               {:name "Feat"
                :selections [(feat-selection spell-lists spells-map 1)]})]}))

(defn expertise-selection [num & [key]]
  (t/selection-cfg
   {:name "Skill Expertise"
    :key (or key :skill-expertise)
    :order 2
    :options (map
              (fn [{:keys [name key icon]}]
                (t/option-cfg
                 {:name name
                  :key key
                  :icon icon
                  :modifiers [(modifiers/skill-expertise key)]
                  :prereqs [(t/option-prereq (str "Requires proficiency in " name)
                                             (fn [built-char]
                                               (let [skill-profs (character/skill-proficiencies built-char)]
                                                 (and skill-profs (skill-profs key)))))]}))
              skills/skills)
    :min num
    :max num
    :multiselect? true
    :ref [:skill-expertise]
    :tags #{:profs :expertise}}))

(def rogue-expertise-selection
  (t/selection-cfg
   {:name "Expertise"
    :tags #{:profs :skill-profs :expertise}
    :order 1
    :options [(t/option-cfg
               {:name "Two Skills"
                :selections [(expertise-selection 2 :two-skills)]})
              (t/option-cfg
               {:name "One Skill/Thieves' Tools"
                :selections [(expertise-selection 1 :one-skill-thieves-tools)]
                :modifiers [(modifiers/tool-proficiency :thieves-tools)
                            (modifiers/tool-expertise :thieves-tools)]})]}))

(defn cleric-spell [spell-level spell-key min-level]
  (modifiers/spells-known-cfg
   spell-level
   {:key spell-key
    :ability ::character/wis
    :class "Cleric"
    :qualifier "Domain"
    :class-key :cleric
    :always-prepared? true}
   min-level
   nil))


(defn potent-spellcasting [page & [source]]
  (modifiers/dependent-trait
   {:level 8
    :page page
    :source source
    :summary (str "Add "
                  (common/bonus-str (?ability-bonuses ::character/wis))
                  " to damage from cantrips you cast")
    :name "Potent Spellcasting"}))

(def monk-base-cfg
  {:name "Monk"
   :subclass-level 3
   :subclass-title "Monastic Tradition"})

(def paladin-base-cfg
  {:name "Paladin"
   :subclass-level 3
   :subclass-title "Sacred Oath"})

(def ua-al-illegal (modifiers/al-illegal "Unearthed Arcana options are not allowed"))

;; dead — all callers are in #_ discarded template blocks (dmg-classes, ua, scag)
#_(defn subclass-plugin [class-base-cfg source subclasses ua-al-illegal?]
  (merge
   class-base-cfg
   {:source source
    :plugin? true
    :subclasses (if ua-al-illegal?
                  (map
                   (fn [subclass]
                     (update subclass :modifiers conj ua-al-illegal))
                   subclasses)
                  subclasses)}))

(defn paladin-spell [spell-level key]
  (modifiers/spells-known-cfg spell-level
                              {:key key
                               :ability ::character/cha
                               :class "Paladin"
                               :always-prepared? true
                               :class-key :paladin}
                              (case spell-level
                                1 3
                                2 5
                                3 9
                                4 13
                                5 17)
                              nil))

(defn subclass-spell-selection [spell-lists spells-map class-key class-name ability spells num]
  (spell-selection
   spell-lists
   spells-map
   {:class-key class-key
    :spell-keys spells
    :spellcasting-ability ability
    :class-name class-name
    :num num
    :prepend-level? true}))

;; dead — only called from deprecated ua_sorcerer.cljc
#_(defn subclass-cantrip-selection [spell-lists spells-map class-key class-name ability spells num]
  (spell-selection
   spell-lists
   spells-map
   {:class-key class-key
    :level 0
    :spellcasting-ability ability
    :class-name class-name
    :spell-keys spells
    :num num}))

(defn warlock-subclass-spell-selection [spell-lists spells-map spells]
  (subclass-spell-selection spell-lists spells-map :warlock "Warlock" ::character/cha spells 0))

(defn traits-modifiers [traits & [class-key source]]
  (map
   (fn [trait]
     (modifiers/trait-cfg (merge {:source source
                                  :class-key class-key}
                                 trait)))
   traits))

(defn armor-prof-modifiers [armor-proficiencies & [cls-kw]]
  (map
   (fn [armor-prof]
     (let [[armor-kw first-class?] (if (keyword? armor-prof) [armor-prof false] armor-prof)]
       (modifiers/armor-proficiency armor-kw first-class? cls-kw)))
   armor-proficiencies))

(defn tool-prof-modifiers [tool-proficiencies & [cls-kw]]
  (map
   (fn [tool-prof]
     (let [[tool-kw first-class?] (if (keyword? tool-prof) [tool-prof false] tool-prof)]
       (modifiers/tool-proficiency tool-kw first-class? cls-kw)))
   tool-proficiencies))

(defn weapon-prof-modifiers [weapon-proficiencies & [cls-kw]]
  (map
   (fn [weapon-prof]
     (let [[weapon-kw first-class?] (if (keyword? weapon-prof) [weapon-prof false] weapon-prof)]
       (if (#{:simple :martial} weapon-kw)
         (modifiers/weapon-proficiency weapon-kw first-class? cls-kw)
         (modifiers/weapon-proficiency weapon-kw first-class? cls-kw))))
   weapon-proficiencies))


#_:clj-kondo/ignore ;; source param shadows outer source — intentional override
;; Assembly fn for the SUBRACE silo — mirrors race-option: fixed :abilities, :profs (incl. a
;; skill-prof CHOICE), :spells (fixed), :traits, and :props mechanics. No spell choice, no prereqs.
;; See docs/kb/decision-vocabulary.md (backward trace: Subrace).
(defn subrace-option [race
                      spell-lists
                      spells-map
                      languages
                      source
                      {:keys [name
                              abilities
                              profs
                              size
                              speed
                              darkvision
                              subrace-options
                              armor-proficiencies
                              weapon-proficiencies
                              modifiers
                              selections
                              traits
                              source
                              edit-event]}]
  (let [{:keys [skill-options tool]} profs
        {skill-num :choose options :options} skill-options
        skill-kws (if (:any options)
                    (map :key skills/skills)
                    (map
                     clojure.core/key
                     (filter val options)))]
    (t/option-cfg
     {:name name
      :edit-event edit-event
      :selections (concat
                   (when (seq skill-kws)
                     [(skill-selection skill-kws (or skill-num 1))])
                   selections)
      :modifiers (concat
                  [(modifiers/subrace name)]
                  (when (and speed
                           (not= speed (:speed race)))
                    [(modifiers/speed (- speed (:speed race)))])
                  (when (and darkvision
                           (not= darkvision (:darkvision race)))
                    [(modifiers/darkvision darkvision)])
                  modifiers
                  (armor-prof-modifiers armor-proficiencies)
                  (weapon-prof-modifiers weapon-proficiencies)
                  (tool-prof-modifiers (common/true-keys tool))
                  (map
                   (fn [[k v]]
                     (modifiers/subrace-ability k v))
                   abilities)
                  (traits-modifiers traits nil source)
                  (when source [(modifiers/used-resource source name)]))})))

#_ ;; unreferenced — inline (map modifiers/ability ...) used at call sites
(defn ability-modifiers [abilities]
  (map
   (fn [[k v]]
     (modifiers/ability k v))
   abilities))

(defn darkvision-modifiers [range]
  [(modifiers/darkvision range)])

(defn feat-selection-2 [cfg]
  (t/selection-cfg
   (merge
    {:name "Feats"
     :ref [:feats]
     :show-if-zero? true
     :tags #{:feats}
     :order 1
     :multiselect? true}
    cfg)))

(def homebrew-ability-increase-selection
  (ability-increase-selection-2
   {:min 0}))

(defn homebrew-feat-selection [spell-lists spells-map]
  (feat-selection-2
   {:min 0
    :max nil
    :options (feat-options spell-lists spells-map)}))

(def homebrew-al-illegal
  (modifiers/al-illegal "Homebrew options are not allowed"))

(defn none-option [path]
  (t/option-cfg
   {:name "<none>"
    :key :none
    :order 1001
    :prereqs [(t/option-prereq
               nil
               (fn [_]
                 ;; Read homebrew flag from raw character in app-db;
                 ;; built entity doesn't carry ::homebrew-paths.
                 (get-in (:character @re-frame.db/app-db)
                         [::entity/homebrew-paths path]))
               true)]}))


(defn custom-subrace-builder []
  (custom-option-builder
   [:custom-subrace-name]
   [:set-custom-subrace]))

(def homebrew-speed-selection
  (t/selection-cfg
   {:name "Speed"
    :tags #{:race}
    :min 0
    :max 1
    :options (map
              (fn [speed]
                (t/option-cfg
                 {:name (str speed " ft.")
                  :key (keyword (str "ft-" speed))
                  :modifiers [(modifiers/speed speed)]}))
              (range -10 55 5))}))

(def homebrew-darkvision-selection
  (t/selection-cfg
   {:name "Darkvision"
    :tags #{:race}
    :min 0
    :max 1
    :options (map
              (fn [distance]
                (t/option-cfg
                 {:name (str distance " ft.")
                  :key (keyword (str "ft-" distance))
                  :modifiers [(modifiers/darkvision distance)]}))
              (range 0 150 30))}))

(defn custom-subrace-option [spell-lists spells-map language-map weapon-map path]
  (t/option-cfg
   {:name "Custom"
    :icon "beer-stein"
    :ui-fn custom-subrace-builder
    :help "Homebrew subrace. This allows you to use a subrace that is not on the list. This will allow unrestricted access to skill and tool proficiencies, racial ability increases, and feats."
    :modifiers [(modifiers/deferred-subrace)
                homebrew-al-illegal]
    :order 1000
    :selections [homebrew-skill-prof-selection
                 homebrew-tool-prof-selection
                 homebrew-ability-increase-selection
                 (homebrew-feat-selection spell-lists spells-map)
                 homebrew-speed-selection
                 homebrew-darkvision-selection
                 homebrew-armor-prof-selection
                 (homebrew-weapon-prof-selection weapon-map)
                 (homebrew-language-selection language-map)]}))

(defn custom-race-builder []
  (custom-option-builder
   [:custom-race-name]
   [:set-custom-race]))

(defn subrace-selection [race spell-lists spells-map language-map weapon-map plugin? source subraces path]
  (let [subrace-path (conj path :subrace)]
    (t/selection-cfg
     {:name "Subrace"
      :tags #{:subrace}
      :min (if subraces 1 0)
      :options (cond->
                (if (seq subraces)
                  (map
                   (partial subrace-option race spell-lists spells-map language-map source)
                   (if source
                     (map (fn [sr] (assoc sr :source source)) subraces)
                     subraces))
                  [(none-option subrace-path)])

                 (not plugin?)
                 (conj (custom-subrace-option spell-lists spells-map language-map weapon-map subrace-path)))})))

(defn custom-race-option [spell-lists spells-map language-map weapon-map]
  (t/option-cfg
   {:name "Custom"
    :icon "beer-stein"
    :ui-fn custom-race-builder
    :help "Homebrew race. This allows you to use a race that is not on the list. This will allow unrestricted access to skill and tool proficiencies, racial ability increases, and feats."
    :modifiers [(modifiers/deferred-race)
                homebrew-al-illegal]
    #_:prereqs #_[(t/option-prereq
               nil
               (fn [_] @(subscribe [:homebrew? [:race]]))
               true)]
    :order 1000
    :selections [(subrace-selection {} spell-lists spells-map language-map weapon-map false nil nil [:race :custom])
                 homebrew-skill-prof-selection
                 homebrew-tool-prof-selection
                 homebrew-ability-increase-selection
                 (homebrew-feat-selection spell-lists spells-map)
                 homebrew-speed-selection
                 homebrew-darkvision-selection
                 homebrew-armor-prof-selection
                 (homebrew-weapon-prof-selection weapon-map)
                 (homebrew-language-selection language-map)]}))

(defn custom-background-builder []
  (custom-option-builder
   [:custom-background-name]
   [:set-custom-background]))

(defn custom-background-option [language-map]
  (t/option-cfg
   {:name "Custom"
    :ui-fn custom-background-builder
    :order 1000
    :modifiers [(modifiers/deferred-background)]
    :selections [(skill-selection 2)
                 (t/selection-cfg
                  {:name "Tool / Language Proficiencies"
                   :tags #{:profs}
                   :options [(t/option-cfg
                              {:name "Two Tools"
                               :selections [(tool-selection 2)]})
                             (t/option-cfg
                              {:name "One Tool / One Language"
                               :selections [(tool-selection 1)
                                            (homebrew-language-selection language-map 1 1)]})
                             (t/option-cfg
                              {:name "Two Languages"
                               :selections [(homebrew-language-selection language-map 2 2)]})]})]}))

;; Assembly fn for the RACE silo. NOT fixed-only: compiles :abilities (fixed ASI), :profs →
;; :skill-options/:language-options/:weapon-proficiency-options (proficiency CHOICES via
;; skill-selection/language-selection), :subraces, :traits, :spells (fixed known), :selections, and
;; :props (make-feat-modifiers). Comparable richness to feats minus ASI options/prereqs/spell
;; choice. See docs/kb/decision-vocabulary.md (backward trace: Race).
(defn race-option [spell-lists
                   spells-map
                   language-map
                   weapon-map
                   {:keys [name
                           icon
                           key
                           help
                           abilities
                           size
                           speed
                           darkvision
                           subraces
                           modifiers
                           selections
                           traits
                           source
                           languages
                           language-options
                           armor-proficiencies
                           weapon-proficiencies
                           profs
                           plugin?
                           edit-event]
                    :as race}]
  (let [key (or key (common/name-to-kw name))
        {:keys [armor weapon save skill-options weapon-proficiency-options tool-options tool language-options]} profs
        {skill-num :choose options :options} skill-options
        skill-kws (if (:any options)
                    (map :key skills/skills)
                    (map
                     clojure.core/key
                     (filter val options)))]
    (t/option-cfg
     {:name name
      :icon icon
      :key key
      :help help
      :edit-event edit-event
      :selections (concat
                   (when (seq skill-kws)
                     [(skill-selection skill-kws (or skill-num 1))])
                   (when (seq subraces)
                     [(subrace-selection race spell-lists spells-map language-map weapon-map plugin? source subraces [:race key])])
                   (when (seq language-options) [(language-selection language-map language-options)])
                   (when (seq weapon-proficiency-options)
                     [(weapon-proficiency-selection-2 weapon-map weapon-proficiency-options)])
                   selections)
      :modifiers (concat
                  (when (not plugin?)
                    (remove
                     nil?
                     [(modifiers/race name)
                      (when size (modifiers/size size))
                      (when speed (modifiers/speed speed))]))
                  (when darkvision
                    (darkvision-modifiers darkvision))
                  (map
                   (fn [language]
                     (modifiers/language (common/name-to-kw language)))
                   languages)
                  (map
                   (fn [[k v]]
                     (modifiers/race-ability k v))
                   abilities)
                  modifiers
                  (tool-prof-modifiers (common/true-keys tool))
                  (traits-modifiers traits nil source)
                  (armor-prof-modifiers armor-proficiencies)
                  (weapon-prof-modifiers weapon-proficiencies)
                  (when source [(modifiers/used-resource source name)]))})))

;; dead — only called from #_ discarded block in template.cljc
#_(defn add-sources [source background]
  (-> background
      (assoc :source source)
      (update :traits (fn [traits] (map (fn [t] (assoc t :source source)) traits)))))

;; dead — only called from #_ discarded backgrounds in template.cljc and deprecated scag.cljc
#_(def artisans-tools-choice-cfg
  {:name "Artisan's Tool"
   :options (zipmap (map :key equipment/artisans-tools) (repeat 1))})

(defn starting-equipment-option [equipment num]
  (t/option-cfg
   {:name (:name equipment)
    :key (:key equipment)
    :modifiers [(modifiers/equipment (:key equipment) num)]}))

(defn class-starting-equipment-entity-options [key items]
  (eh/starting-equipment-entity-options ::char-equip/class-starting-equipment? key items))

(defn tool-prof-selection-aux [tool num & [key prereq-fn]]
  (t/selection-cfg
   {:name (str "Tool Proficiency: " (:name tool))
    :key (when key (keyword (str (name key) "--" (common/name-to-kw (:name tool)))))
    :help (str "Select " (s/lower-case (:name tool)) " for which you are proficient.")
    :options (map
              (fn [{:keys [name key icon]}]
                (t/option-cfg
                 {:name name
                  :key key
                  :icon icon
                  :modifiers [(modifiers/tool-proficiency key)]}))
              (:values tool))
    :min num
    :max num
    :prereq-fn prereq-fn
    :tags #{:tool-profs :profs}}))

(defn tool-prof-selection [tool-options & [key prereq-fn]]
  (let [[first-key first-num] (-> tool-options first)
        first-option (equipment/tools-map first-key)]
    (if (and (= 1 (count tool-options))
             (seq (:values first-option)))
      (tool-prof-selection-aux first-option first-num key prereq-fn)
      (t/selection-cfg
       {:name "Tool Proficiencies"
        :key key
        :options (map
                  (fn [[k num]]
                    (let [tool (equipment/tools-map k)]
                      (if (:values tool)
                        (t/option-cfg
                         {:name (:name tool)
                          :selections [(tool-prof-selection-aux tool num key prereq-fn)]})
                        (t/option-cfg
                         {:name (:name tool)
                          :key (:key tool)
                          :icon (:icon tool)
                          :modifiers [(modifiers/tool-proficiency (:key tool))]}))))
                  tool-options)
        :prereq-fn prereq-fn
        :tags #{:profs :tool-profs}}))))

(defn first-class? [class-kw & [classes]]
  (fn [c] (= class-kw (first (or classes (character/classes c))))))

(defn new-starting-equipment-selection [class-kw {:keys [name options] :as cfg}]
  (t/selection-cfg
   (merge
    cfg
    {:name (str "Starting Equipment: " name)
     :tags #{:equipment :starting-equipment}
     :order 1
     :options (conj options
                    (t/option-cfg
                     {:name "<none>"
                      :key :none}))
     :prereq-fn (when class-kw (first-class? class-kw))})))

(defn simple-weapon-selection [num class-kw weapon-map]
  (new-starting-equipment-selection
   class-kw
   {:name "Simple Weapon"
    :tags #{:starting-equipment}
    :options (weapon-options (weapons/simple-weapons (vals weapon-map)))
    :min num
    :max num
    :prereq-fn (first-class? class-kw)}))

(defn weapon-option-2 [class-kw weapon-map [k num]]
  (case k
    :simple (t/option-cfg
             {:name "Any Simple Weapon"
              :selections [(simple-weapon-selection num class-kw weapon-map)]})
    :martial (t/option-cfg
              {:name "Any Martial Weapon"
               :selections [(new-starting-equipment-selection
                             class-kw
                             {:name "Martial Weapon"
                              :options (weapon-options (weapons/martial-weapons (vals weapon-map)))
                              :min num
                              :max num})]})
    (t/option-cfg
     {:name (-> k weapon-map :name (str (if (> num 1) (str " (" num ")") "")))
      :modifiers [(modifiers/weapon k num)]})))

(defn class-options [class-kw option-fn choices help]
  (map
   (fn [{:keys [name options]}]
     (new-starting-equipment-selection
      class-kw
      {:name name
       :help help
       :options (map
                 option-fn
                 options)}))
   choices))

(defn class-weapon-options [weapon-choices class-kw weapon-map]
  (class-options class-kw (partial weapon-option-2 class-kw weapon-map) weapon-choices "Select a weapon to begin your adventuring career with."))

(defn armor-option [[k num]]
  (t/option-cfg
     {:name (-> k armor/armor-map :name)
      :modifiers [(modifiers/armor k num)]}))

(defn class-armor-options [armor-choices class-kw]
  (class-options class-kw armor-option armor-choices "Select armor to begin your adventuring career with."))

(defn equipment-option [class-kw [k num]]
  (let [equipment (equipment/equipment-map k)]
    (if (:values equipment)
      (t/option-cfg
       {:name (:name equipment)
        :selections [(t/selection-cfg
                      {:name (:name equipment)
                       :tags #{:equipment :starting-equipment}
                       :options (map
                                 #(equipment-option class-kw %)
                                 (zipmap (map :key (:values equipment)) (repeat num)))
                       :prereq-fn (first-class? class-kw)})]})
      (t/option-cfg
       {:name (-> equipment :name (str (if (> num 1) (str " (" num ")") "")))
        :modifiers (if (:items equipment)
                     (map
                      (fn [[kw num]]
                        (modifiers/equipment kw num))
                      (:items equipment))
                     [(modifiers/equipment k num)])}))))

(defn class-equipment-options [equipment-choices class-kw]
  (class-options class-kw (partial equipment-option class-kw) equipment-choices "Select equipment to start your adventuring career with."))

;; Rich starting-equipment choice groups — the full SRD form as serializable data.
;; Unlike the shorthand :*-choices (one item per option), an option here can grant a
;; BUNDLE of items (:grants) and/or offer a nested sub-choice (:choose), e.g. Fighter's
;; "(a) chain mail, or (b) leather + longbow + 20 arrows" and "a martial weapon + shield".
;; Shape on the class map:
;;   :equipment-selections
;;   [{:name "Armor"
;;     :options [{:name "Chain Mail" :grants [{:kind :armor :key :chain-mail}]}
;;               {:name "Leather, Longbow, 20 Arrows"
;;                :grants [{:kind :armor :key :leather} {:kind :weapon :key :longbow}
;;                         {:kind :equipment :key :arrow :qty 20}]}]}
;;    {:name "Weapon"
;;     :options [{:name "A martial weapon and a shield"
;;                :grants [{:kind :armor :key :shield}]
;;                :choose [{:name "Martial Weapon" :from :martial}]}]}]
;; One fixed grant ({:kind :key :qty}) -> the matching modifier that drops the item
;; onto the character's ?weapons/?armor/?equipment.
(defn- equipment-grant->modifier [{:keys [kind key qty] :or {qty 1}}]
  (case kind
    :weapon    (modifiers/weapon key qty)
    :armor     (modifiers/armor key qty)
    :equipment (modifiers/equipment key qty)
    nil))

;; The grouped-equipment sub-choices, pointing at the un-shadowed member lists
;; (equipment-map shadows these grouped keys with plain items via its zipmap merge).
(def ^:private equipment-group-choosers
  {:holy-symbol        {:name "Holy Symbol"        :items equipment/holy-symbols}
   :arcane-focus       {:name "Arcane Focus"       :items equipment/arcane-focuses}
   :druidic-focus      {:name "Druidic Focus"      :items equipment/druidic-focuses}
   :musical-instrument {:name "Musical Instrument" :items equipment/musical-instruments}
   :pack               {:name "Equipment Pack"     :items equipment/packs}})

;; One sub-choice ({:from ...}) -> a nested "pick one" selection. :from is either a
;; weapon class (:simple/:martial/:any-weapon) or a grouped-equipment key
;; (:holy-symbol/:arcane-focus/:druidic-focus/:musical-instrument/:pack), which expands
;; to a pick among that group's members (equipment-option handles pack-contents, etc.).
(declare equipment-selection-option)

(defn- equipment-subchoice->selection [class-kw weapon-map {:keys [name from options]}]
  (cond
    ;; Explicit enumerated pick — a nested selection listed option-by-option. The general
    ;; fallback for a sub-choice that isn't one of the named pools (e.g. "pick one of these
    ;; three specific items"). Built like a grouped pick — plain selection, name verbatim,
    ;; no prefix / no "<none>" — so it round-trips exactly.
    (seq options)
    (t/selection-cfg
     {:name name
      :tags #{:equipment :starting-equipment}
      :options (mapv #(equipment-selection-option class-kw weapon-map %) options)
      :prereq-fn (first-class? class-kw)})

    ;; Grouped-equipment pick (focus / holy symbol / instrument / pack). Mirror the live
    ;; equipment-option EXACTLY: a plain starting-equipment selection named for the group,
    ;; WITHOUT the "Starting Equipment: " prefix and WITHOUT a "<none>" opt-out — so a class
    ;; filled from an SRD class reproduces the SRD's own nested selection verbatim (its name
    ;; also feeds the selection's minted key, which must stay stable).
    (equipment-group-choosers from)
    (let [chooser (equipment-group-choosers from)]
      (t/selection-cfg
       {:name (or name (:name chooser))
        :tags #{:equipment :starting-equipment}
        :options (mapv #(equipment-option class-kw [(:key %) 1]) (:items chooser))
        :prereq-fn (first-class? class-kw)}))

    ;; Weapon-class pick. Live builds these through new-starting-equipment-selection, so
    ;; keep the prefix (and the "<none>" it appends) to match.
    :else
    (new-starting-equipment-selection
     class-kw
     {:name (or name (case from :simple "Simple Weapon" :martial "Martial Weapon"
                       :simple-melee "Simple Melee Weapon" :any-weapon "Weapon" "Choose one"))
      :min 1 :max 1
      :options (cond
                 (= from :simple)       (simple-weapon-options 1 (vals weapon-map))
                 (= from :martial)      (martial-weapon-options 1 (vals weapon-map))
                 (= from :simple-melee) (simple-melee-weapon-options 1 (vals weapon-map))
                 (= from :any-weapon)   (weapon-options (vals weapon-map) 1)
                 :else                  [])})))

;; One option -> an option-cfg carrying its bundle (:grants -> :modifiers) and any
;; nested picks (:choose -> :selections).
(defn- equipment-selection-option [class-kw weapon-map {:keys [name grants choose]}]
  (t/option-cfg
   (cond-> {:name name}
     (seq grants) (assoc :modifiers (vec (keep equipment-grant->modifier grants)))
     (seq choose) (assoc :selections (mapv #(equipment-subchoice->selection class-kw weapon-map %) choose)))))

;; A whole :equipment-selections vector -> the starting-equipment selections class-option
;; splices into a class. This is the serializable twin of the SRD's hand-built form.
(defn class-equipment-selections [equipment-selections class-kw weapon-map]
  (mapv (fn [{:keys [name options]}]
          (new-starting-equipment-selection
           class-kw
           {:name name
            :options (mapv #(equipment-selection-option class-kw weapon-map %) options)}))
        equipment-selections))

(defn background-skills-cfg [background-nm skill-kws]
  {:modifiers (map
               (fn [skill-kw]
                 (modifiers/skill-proficiency skill-kw
                                              background-nm
                                              [(not (get ?skill-profs skill-kw))]))
               skill-kws)
   :selections (map
                (fn [skill-kw]
                  (skill-selection (map :key skills/skills)
                                   1
                                   0
                                   nil
                                   (fn [c]
                                     (let [skill-profs (character/skill-proficiencies c)
                                           skill-sources (get skill-profs skill-kw)
                                           passes? (and skill-sources
                                                        (not (skill-sources background-nm)))]
                                       passes?))))
                skill-kws)})

(defn background-option [language-map
                         weapon-map
                         {:keys [name
                                 help
                                 page
                                 profs
                                 selections
                                 modifiers
                                 weapon-choices
                                 weapons
                                 equipment
                                 custom-equipment
                                 equipment-choices
                                 armor
                                 armor-choices
                                 treasure
                                 custom-treasure
                                 traits
                                 source
                                 edit-event]
                          :as background}]
  (let [kw (common/name-to-kw name)
        {:keys [skill skill-options tool-options tool language-options]
         armor-profs :armor weapon-profs :weapon} profs
        {skill-num :choose options :options} skill-options
        skill-kws (if (:any options) (map :key skills/skills) (keys options))]
    (t/option-cfg
     (merge-with
      concat
      (background-skills-cfg name (keys skill))
      {:name name
       :key kw
       :help help
       :edit-event edit-event
       :page page
       :select-fn (fn [_ _]
                    (dispatch [:add-background-starting-equipment background]))
       :selections (concat
                    selections
                    (when (seq tool-options) [(tool-prof-selection tool-options)])
                    (class-weapon-options weapon-choices nil weapon-map)
                    (class-armor-options armor-choices nil)
                    (class-equipment-options equipment-choices nil)
                    (when (seq skill-kws) [(skill-selection skill-kws skill-num)])
                    (when (seq language-options) [(language-selection
                                                 language-map
                                                 language-options)]))
       :modifiers (concat
                   [(modifiers/background name)]
                   (traits-modifiers traits)
                   modifiers
                   (armor-prof-modifiers (keys armor-profs))
                   (weapon-prof-modifiers (keys weapon-profs))
                   (tool-prof-modifiers (keys tool)))}))))

(defn total-levels-prereq [level & [class-key]]
  (fn [c] (>= (if class-key
                ((character/class-level-fn c) class-key)
                (character/total-levels c))
              level)))

(defn total-levels-prereq-2 [level & [class-key]]
  (fn [c]
    (and c
         (character/class-level-fn c)
         level
         (>= (or (if class-key
                   ((character/class-level-fn c) class-key)
                   (character/total-levels c))
                 0)
             (or level 0)))))


(defn total-levels-option-prereq [level & [class-key]]
  (t/option-prereq
   (str "You must have at least " level " " (name class-key) " levels")
   (total-levels-prereq level class-key)))

(defn add-mod-total-levels-prereq [lvl cls modifier]
  (if (sequential? modifier)
    (map
     add-mod-total-levels-prereq lvl cls
     modifier)
    (update
     modifier
     ::mods/conditions
     conj
     (total-levels-prereq-2 lvl (:key cls)))))

(defn subclass-option [spell-lists
                       spells-map
                       language-map
                       cls
                       {:keys [name
                               key
                               source
                               edit-event
                               profs
                               selections
                               spellcasting
                               modifiers
                               level-modifiers
                               traits
                               prereqs
                               levels]
                        :as subcls}]
  ;; Use explicit :key if present (for renamed plugins), otherwise generate from name
  (let [kw (or key (common/name-to-kw name))
        {:keys [armor weapon save skill-options skill-expertise-options tool-options tool language-options]} profs
        {skill-num :choose options :options} skill-options
        {level-factor :level-factor} spellcasting
        skill-kws (if (:any options) (map :key skills/skills) (keys options))
        skill-expertise-kws (if (get-in skill-expertise-options [:options :any])
                              (map :key skills/skills)
                              (keys (:options skill-expertise-options)))
        armor-profs (keys armor)
        weapon-profs (keys weapon)
        tool-profs (keys tool)
        spellcasting-template (spellcasting-template
                               spell-lists
                               spells-map
                               (assoc
                                spellcasting
                                :class-key
                                (or (:spell-list spellcasting) kw))
                               subcls)
        spell-selections (mapcat
                          (fn [[lvl selections]]
                            (map
                             (fn [selection]
                               (assoc selection
                                      ::t/prereq-fn
                                      (fn [c] (let [total-levels (character/total-levels c)]
                                                (>= lvl total-levels)))))
                             selections))
                          (:selections spellcasting-template))
        level-selections (mapcat
                          (fn [[lvl {selections :selections}]]
                            (map
                             (fn [selection]
                               (assoc
                                selection
                                ::t/prereq-fn
                                (total-levels-prereq lvl (:key cls))))
                             selections))
                          levels)
        level-modifiers (mapcat
                         (fn [[lvl {modifiers :modifiers}]]
                           (map
                            (partial add-mod-total-levels-prereq lvl cls)
                            modifiers))
                         levels)]
    (t/option-cfg
     {:name name
      :edit-event edit-event
      :prereqs prereqs
      :selections (map
                   (fn [selection]
                     (update selection ::t/tags sets/union #{(:key cls) kw}))
                   (concat
                    selections
                    level-selections
                    spell-selections
                    (when (seq tool-options) [(tool-prof-selection tool-options)])
                    (when (seq skill-kws) [(skill-selection skill-kws skill-num)])
                    (when (seq skill-expertise-kws)
                      [(skill-expertise-selection skill-expertise-kws (:choose skill-expertise-options))])
                    (when (seq language-options) [(language-selection language-map language-options)])))
      :modifiers (concat
                  modifiers
                  level-modifiers
                  [(modifiers/subclass (:key cls) kw)
                   (modifiers/subclass-name (:key cls) name)]
                  (when (:known-mode spellcasting)
                    [(modifiers/spells-known-mode name (:known-mode spellcasting))])
                  (armor-prof-modifiers armor-profs)
                  (weapon-prof-modifiers weapon-profs)
                  (tool-prof-modifiers tool-profs)
                  (traits-modifiers traits (:key cls))
                  (when level-factor [(modifiers/spell-slot-factor (:key cls) level-factor)])
                  (when source [(modifiers/used-resource source name)]))})))

(defn level-key [index]
  (keyword (str "level-" index)))

(defn level-name [index]
  (str "Level " index))

#_ ;; unreferenced — subclass-option builds level options inline
(defn subclass-level-option [{:keys [name
                                     levels] :as subcls}
                             kw
                             spellcasting-template
                             i]
  (let [selections (some-> levels (get i) :selections)]
    (t/option-cfg
     {:name (level-name i)
      :key (level-key i)
      :order i
      :selections (concat
                   selections
                   (some-> spellcasting-template :selections (get i)))
      :modifiers (some-> levels (get i) :modifiers)})))

(defn al-illegal-hit-points-mod [reason]
  (modifiers/al-illegal (str reason " The only legal option is 'Average'.")))

(defn hit-points-selection [die class-nm level]
  (t/selection-cfg
   {:name (str "Hit Points: " class-nm " " level)
    :key :hit-points
    :require-value? true
    :help "Select the method with which to determine this level's hit points."
    :tags #{:class}
    :options [{::t/name "Manual Entry"
               ::t/key :manual-entry
               ::t/help "This option allows you to manually type in the value for this level's hit points. Use this if you want to roll dice yourself or if you already have a character with known hit points for this level."
               ::t/modifiers [(modifiers/deferred-max-hit-points)
                              (al-illegal-hit-points-mod "Manual entry for hit points is not legal.")]}
              {::t/name (str "Roll (1D" die ")")
               ::t/key :roll
               ::t/help "This option rolls virtual dice for you and sets that value for this level's hit points. It could pay off with a high roll, but you might also roll a 1."
               ::t/modifiers [(modifiers/deferred-max-hit-points)
                              (al-illegal-hit-points-mod "Rolling for hit points is not legal.")]}
              (let [average (dice/die-mean-round-up die)]
                (t/option-cfg
                 {:name "Average"
                  :key :average
                  :help (str "This option just gives you the average value (" average ") for the die roll (1D" die ").")
                  :modifiers [(modifiers/max-hit-points average)]}))]}))

(defn custom-subclass-builder
  "Renders custom subclass name input. Passes built-template via dispatch
   because the handler needs entity/get-option-value-path."
  [path]
  (custom-option-builder
   [:custom-subclass-name path]
   [:set-custom-subclass path]
   true))

#_(defn custom-subclass-spell-selection [ability-kw level]
  (t/selection-cfg
   {:name (if (zero? level)
            "Cantrips Known"
            (str (common/ordinal level) "-Level Spells Known"))
    :key (keyword (str "lvl-" level "-spells-known"))
    :min 0
    :max nil
    :multiselect? true
    :tags #{:spells}
    :order level
    :prereq-fn (fn [c] (or (zero? level)
                           (-> (character/total-levels c)
                               (total-slots 3)
                               (get level)
                               pos?)))

    :options (sequence
              (comp
               (filter
                (fn [s]
                  (= level (:level s))))
               (map :key)
               (map (partial memoized-spell-option ability-kw "Custom")))
              spells/spells)}))

#_(defn custom-subclass-spellcasting-selection [cls-key]
  (t/selection-cfg
   {:name "Spellcasting Ability"
    :key :spellcasting-ability
    :min 0
    :max 1
    :tags #{:class}
    :options (conj
              (map
               (fn [{ability-kw :key name :name}]
                 (t/option-cfg
                  {:name name
                   :key ability-kw
                   :modifiers [(modifiers/spell-slot-factor cls-key 3)]
                   :selections (map
                                (fn [level]
                                  (custom-subclass-spell-selection ability-kw level))
                                (range 0 5))}))
               abilities)
              (t/option-cfg
               {:name "<none>"
                :key :none}))}))

(defn custom-subclass-option [spell-lists spells-map weapon-map cls-key level-key subclass-selection-key spellcasting-class?]
  (let [path [:class cls-key :levels level-key subclass-selection-key]]
    (t/option-cfg
     {:name "Custom"
      :icon "beer-stein"
      :ui-fn #(custom-subclass-builder path)
      :help "Homebrew subclass. This allows you to use a subclass that is not on the list. This will allow unrestricted access to skill and tool proficiencies and feats."
      #_:prereqs #_[(t/option-prereq
                     nil
                     (fn [_] @(subscribe [:homebrew? path]))
                     true)]
      :order 1000
      :modifiers [(modifiers/deferred-subclass-name cls-key)
                  homebrew-al-illegal]
      :selections (let [selections
                        [homebrew-skill-prof-selection
                         homebrew-tool-prof-selection
                         (homebrew-feat-selection spell-lists spells-map)
                         homebrew-armor-prof-selection
                         (homebrew-weapon-prof-selection weapon-map)]]
                    selections
                    #_(if spellcasting-class?
                      selections
                      (conj selections
                            (custom-subclass-spellcasting-selection cls-key))))})))

;; Builds one class level's options (called per level 1..20 from class-option). NOTE the `plugin?`
;; guard below gates the standard ASI selection, hit-points selection, and per-level modifier — but
;; `:plugin? true` marks only the hardcoded UA *overlay* templates (templates/ua_*.cljc), NOT
;; homebrew builder classes (which never set it), so builder classes DO get ASI/hit-points normally.
;; See docs/kb/decision-vocabulary.md (backward trace: Class — "(not plugin?) gate is not a gap").
(defn level-option [spell-lists
                    spells-map
                    language-map
                    weapon-map
                    {:keys [name
                            plugin?
                            hit-die
                            profs
                            levels
                            traits
                            spellcasting
                            ability-increase-levels
                            subclass-title
                            subclass-help
                            subclass-level
                            subclasses
                            source] :or {subclass-level 1} :as cls}
                    kw
                    spellcasting-template
                    i]
  (let [ability-inc-set (set ability-increase-levels)
        level-kw (level-key i)]
    (t/option-cfg
     {:name (level-name i)
      :key level-kw
      :order i
      :selections (map
                   (fn [selection]
                     (update selection ::t/tags sets/union #{:level level-kw}))
                   (concat
                    (some-> levels (get i) :selections)
                    (some-> spellcasting-template :selections (get i))
                    (when (= i subclass-level)
                      (let [subclass-selection-key (common/name-to-kw subclass-title)]
                        [(t/selection-cfg
                          {:name (or subclass-title (str name " Archetype"))
                           :adder-key-fn (fn [_] [:subclass])
                           :key subclass-selection-key
                           :help subclass-help
                           :tags #{:subclass}
                           :order 2
                           :options (conj
                                     (map
                                      #(subclass-option spell-lists spells-map language-map (assoc cls :key kw) %)
                                      (if source (map (fn [sc] (assoc sc :source source)) subclasses) subclasses))
                                     (custom-subclass-option spell-lists spells-map weapon-map kw level-kw subclass-selection-key (some? spellcasting)))})]))
                    (when (and (not plugin?) (ability-inc-set i))
                      [(ability-score-improvement-selection spell-lists spells-map name i)])
                    (when (not plugin?)
                      [(assoc
                        (hit-points-selection hit-die name i)
                        ::t/prereq-fn
                        (fn [c] (or (not (= kw (first (character/classes c))))
                                    (> i 1))))])))
      :modifiers (concat
                  (some-> levels (get i) :modifiers)
                  (traits-modifiers
                   (filter
                    (fn [{level :level :or {level 1}}]
                      (= level i))
                    traits)
                   kw)
                  (when (and (not plugin?)
                           (= i 1))
                    [(mods/cum-sum-mod
                      ?hit-point-level-increases
                      hit-die
                      nil
                      nil
                      [(= kw (first ?classes))])])
                  (when (not plugin?)
                    [(modifiers/level kw name i hit-die)]))})))



(defn class-skill-selection [{skill-num :choose options :options skill-select-order :order} key prereq-fn]
  (let [skill-kws (if (:any options) (map :key skills/skills) (keys options))]
    (skill-selection skill-kws skill-num skill-select-order key prereq-fn)))

(defn class-help-field [name value]
  [:div.m-t-5
    [:span.f-w-b (str name ":")]
   [:span.m-l-10 value]])


(defn class-help [hd saves weapon-profs armor-profs]
  [:div
   (class-help-field "Hit Die" (str "d" hd))
   (class-help-field "Saving Throw Proficiencies" (s/join ", " (map (comp s/upper-case name) saves)))
   (class-help-field "Weapon Proficiencies" (s/join ", " (map (comp name key) weapon-profs)))
   (class-help-field "Armor Proficiencies" (s/join ", " (map (comp name key) armor-profs)))])

(defn class-option [spell-lists
                    spells-map
                    plugin-subclasses-map
                    language-map
                    weapon-map
                    {:keys [name
                            key
                            help
                            hit-die
                            plugin?
                            plugin-source
                            profs
                            levels
                            ability-increase-levels
                            subclass-title
                            subclass-level
                            subclasses
                            selections
                            modifiers
                            source
                            weapon-choices
                            weapons
                            equipment
                            equipment-choices
                            equipment-selections
                            armor
                            armor-choices
                            spellcasting
                            multiclass-prereqs]
                     :as cls}]
  (let [merged-class (update cls :subclasses #(into (sorted-set-by (fn [x y] (compare (:name x) (:name y)))) (concat (reverse (get plugin-subclasses-map key)) %)))
        kw (or key (common/name-to-kw name))
        {:keys [save skill-options skill-expertise-options multiclass-skill-options tool-options multiclass-tool-options tool]
         armor-profs :armor weapon-profs :weapon} profs
        {level-factor :level-factor} spellcasting
        skill-expertise-kws (if (get-in skill-expertise-options [:options :any])
                              (map :key skills/skills)
                              (keys (:options skill-expertise-options)))
        save-profs (keys save)
        spellcasting-template (spellcasting-template
                               spell-lists
                               spells-map
                               (assoc spellcasting :class-key kw)
                               merged-class)
        first-class? (fn [c] (let [first-class (first (character/classes c))]
                               (= kw first-class)))]
    (t/option-cfg
     {:name name
      :key kw
      :plugin-source plugin-source
      :help [:div.p-t-5.p-l-10.p-r-10
             (class-help hit-die save-profs weapon-profs armor-profs)
             [:div.m-t-10 help]]
      :prereqs multiclass-prereqs
      :selections (map
                   (fn [selection]
                     (update selection ::t/tags sets/union #{kw}))
                   (concat
                    selections
                    (when (seq tool-options)
                      [(tool-prof-selection tool-options :tool-selection first-class?)])
                    (when (seq multiclass-tool-options)
                      [(tool-prof-selection multiclass-tool-options :multiclass-tool-selection (fn [c] (not= kw (first (:classes c)))))])
                    (when weapon-choices (class-weapon-options weapon-choices kw weapon-map))
                    (when armor-choices (class-armor-options armor-choices kw))
                    (when equipment-choices (class-equipment-options equipment-choices kw))
                    (when equipment-selections (class-equipment-selections equipment-selections kw weapon-map))
                    (when skill-options
                      [(class-skill-selection skill-options :skill-proficiency first-class?)])
                    (when (seq skill-expertise-kws)
                      [(skill-expertise-selection skill-expertise-kws (:choose skill-expertise-options))])
                    (when multiclass-skill-options
                      [(class-skill-selection multiclass-skill-options :multiclass-skill-proficiency (complement first-class?))])
                    [(t/selection-cfg
                      {:name (str name " Levels")
                       :key :levels
                       :help "These are your levels in the containing class. You can add levels by clicking the 'Add Levels' button below."
                       :new-item-fn (fn [selection options current-values]
                                      {::entity/key (-> current-values count inc level-key)})
                       :tags #{kw}
                       :options (map
                                 (partial level-option spell-lists spells-map language-map weapon-map merged-class kw spellcasting-template)
                                 (range 1 21))
                       :min 1
                       :sequential? true
                       :multiselect? true
                       :max nil})]))
      :associated-options (remove
                           nil?
                           [(class-starting-equipment-entity-options :weapons weapons)
                            (class-starting-equipment-entity-options :armor armor)
                            (class-starting-equipment-entity-options :equipment equipment)])
      :modifiers (concat
                  modifiers
                  (when (:prepares-spells? spellcasting)
                    [(mods/map-mod ?prepares-spells name true)])
                  (when (= :all (:known-mode spellcasting))
                    (let [spell-list (spell-lists kw)]
                      (mapcat
                       (fn [[lvl spell-keys]]
                         (map
                          (fn [spell-key]
                            (modifiers/spells-known-cfg lvl
                                                        {:class-key kw
                                                         :key spell-key
                                                         :class name
                                                         :ability (:ability spellcasting)}
                                                        1
                                                        [(let [slots (?class-spell-slots kw)]
                                                           (slots lvl))
                                                         (let [spell (spells-map spell-key)]
                                                           (using-source? ?option-sources (:source spell)))]))
                          spell-keys))
                       spell-list)))
                  (when armor-profs (armor-prof-modifiers armor-profs kw))
                  (when weapon-profs (weapon-prof-modifiers weapon-profs kw))
                  (when tool (tool-prof-modifiers tool kw))
                  (when level-factor [(modifiers/spell-slot-factor kw level-factor)])
                  (when (and source (not plugin?))
                    [(modifiers/used-resource source name)])
                  (when (:known-mode spellcasting)
                    [(modifiers/spells-known-mode name (:known-mode spellcasting))])
                  (remove
                   nil?
                   [(modifiers/cls kw)
                    (when save-profs (apply modifiers/saving-throws kw save-profs))]))})))

#_(defn source-url [source]
  (some-> source disp/sources :url))

(def ranger-base-cfg
  {:name "Ranger"
   :subclass-level 3
   :subclass-title "Ranger Archetype"})

(defn background-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Background"
     :tags #{:background}}
    cfg)))

(defn class-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Class"
     :order 0
     :tags #{:class}
     :multiselect? true
     :min 1
     :max nil}
    cfg)))

(defn race-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Race"
     :order 0
     :help "Race determines your appearance and helps shape your culture and background. It also affects your ability scores, size, speed, languages and many other crucial inherent traits."
     :tags #{:race}}
    cfg)))

(def ranger-skills {:animal-handling true :athletics true :insight true :investigation true :nature true :perception true :stealth true :survival true})

(defn evasion [level page]
  {:name "Evasion"
   :page page
   :level level
   :summary "when you succeed on a DEX save to take half damage, you take none, if you fail, you take half"})

(defn uncanny-dodge-modifier [page]
  (modifiers/reaction
   {:name "Uncanny Dodge"
    :page page
    :summary "halve the damage from an attacker you can see that hits you"}))

(defn divine-strike [damage-desc page & [source]]
  (modifiers/dependent-trait
   {:level 8
    :name "Divine Strike"
    :page page
    :source source
    :frequency units5e/turns-1
    :summary (str "Add "
                  (if (>= (?class-level :cleric) 14) 2 1)
                  "d8 "
                  damage-desc
                  " damage to a successful weapon attack's damage")}))

(defn favored-enemy-types [language-map]
  {:aberration [:deep-speech :undercommon :grell :slaad]
   :beast [:giant-elk :giant-eagle :giant-owl]
   :celestial (keys language-map)
   :construct [:modron]
   :dragon [:aquan :draconic :sylvan]
   :elemental [:auran :terran :ignan :aquan]
   :fey [:draconic :elvish :sylvan :abyssal :infernal :primordial :aquan :giant]
   :fiend (keys language-map)
   :giant [:giant :orc :undercommon]
   :monstrosity [:draconic :sylvan :elvish :hook-horror :abyssal :celestial :infernal :primordial :aquan :sphynx :umber-hulk :yeti :winter-wolf :goblin :worg]
   :ooze []
   :plant [:druidic :elvish :sylvan]
   :undead (keys language-map)})

(def humanoid-enemies
  {:bugbear [:goblin]
   :bullywug [:bullywug]
   :githyanki [:gith]
   :gitzerai [:gith]
   :gnoll [:gnoll :abyssal]
   :goblin [:goblin]
   :grimlock [:undercommon]
   :hobgoblin [:goblin]
   :kobold [:draconic]
   :koa-toa [:undercommon]
   :lizardfolk [:draconic :abyssal]
   :merfolk [:aquan]
   :orc [:orc]
   :thri-kreen [:thri-kreen]
   :troglodyte [:troglodyte]
   :yuan-ti-pureblood {:name "Yuan-Ti Pureblood"
                       :languages [:abyssal :draconic]}})

;; dead — only called from deprecated ua_race_feats.cljc
#_(defn druid-cantrip-selection [spell-lists spells-map class-nm]
  (t/selection-cfg
   {:name "Druid Cantrip"
    :tags #{:spells}
    :options (spell-options spells-map (get-in spell-lists [:druid 0]) ::character/wis class-nm)}))

(defn eldritch-invocation-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Eldritch Invocations"
     :multiselect? true
     :ref [:class :warlock :eldritch-invocations]
     :tags #{:spells}}
    cfg)))

(def pact-of-the-tome-name "Pact Boon: Pact of the Tome")
(def pact-of-the-chain-name "Pact Boon: Pact of the Chain")
(def pact-of-the-blade-name "Pact Boon: Pact of the Blade")

(defn has-trait-with-name-prereq [name]
  (t/option-prereq
   (str "You must have " name)
   (fn [c] (some #(= name (:name %)) (character/traits c)))))

(def pact-of-the-tome-prereq
  (has-trait-with-name-prereq pact-of-the-tome-name))

(def pact-of-the-blade-prereq
  (has-trait-with-name-prereq pact-of-the-blade-name))

(def pact-of-the-chain-prereq
  (has-trait-with-name-prereq pact-of-the-chain-name))

(def has-eldritch-blast-prereq
  (t/option-prereq
   "You must know the edritch blast cantrip"
   (fn [c]
     (get-in (character/spells-known c)
             [0 ["Warlock" :eldritch-blast]]))))

;; dead — only called from #_ discarded blocks in template.cljc
#_(defn deep-gnome-option-cfg [key source page]
  {:name "Gnome"
   :plugin? true
   :subraces
   [{:name (str "Deep Gnome (" (s/upper-case (name source)) ")")
     :key key
     :abilities {::character/dex 1}
     :modifiers [(modifiers/darkvision 120)
                 (modifiers/language :undercommon)]
     :source source
     :traits [{:name "Stone Camouflage"
               :source source
               :page page
               :summary "Advantage on hide checks in rocky terrain"}]}]})

;; dead — only called from deprecated ua_warlock_and_wizard.cljc / ua_revised_class_options.cljc
#_(defmacro eldritch-invocation-option [{:keys [name summary source page prereqs modifiers trait-type frequency range]}]
  `(t/option-cfg
    {:name ~name
     :prereqs ~prereqs
     :modifiers (conj
                 ~modifiers
                 (~(case trait-type
                    :action `modifiers/action
                    :bonus-action `modifiers/bonus-action
                    :reaction `modifiers/reaction
                    `modifiers/dependent-trait)
                  {:name (str "Eldritch Invocation: " ~name)
                   :page ~page
                   :source ~source
                   :summary ~summary
                   :frequency ~frequency
                   :range ~range}))}))

(defn race-prereq [race-nms]
  (let [name-set (if (string? race-nms)
                   #{race-nms}
                   (into #{} race-nms))]
    (t/option-prereq
     (str (common/list-print name-set "or") " Only")
     (fn [c] (name-set (character/race c))))))

;; dead — only called from deprecated ua_race_feats.cljc
#_(defn subrace-prereq [race-nm subrace-nm]
  (t/option-prereq
   (str subrace-nm " Only")
   (fn [c] (and (= race-nm (character/race c))
                (= subrace-nm (character/subrace c))))))

#_(def deep-gnome-prereq
  (t/option-prereq
   "Deep Gnome only"
   (fn [c] (let [subrace (character/subrace c)]
             (or (= "Deep Gnome (EE)" subrace)
                 (= "Deep Gnome (SCAG)" subrace))))))

#_(defn svirfneblin-magic-feat [source page]
  (feat-option
   {:name (str "Svirfneblin Magic (" (s/upper-case (name source)) ")")
    :page page
    :source source
    :summary "Can cast 'nondetection', 'blindness/deafness', 'blur', and 'disguise self'"
    :prereqs [deep-gnome-prereq]
    :modifiers [(modifiers/spells-known 3 :nondetection ::character/cha "Deep Gnome" 0 "at will")
                (modifiers/spells-known 2 :blindness-deafness ::character/cha "Deep Gnome" 0 "once per long rest")
                (modifiers/spells-known 2 :blur ::character/cha "Deep Gnome" 0 "once per long rest")
                (modifiers/spells-known 1 :disguise-self ::character/cha "Deep Gnome" 0 "once per long rest")]}))


(defn feat-prereqs
  "Build prereq list for a feat. race-map is a {key->race} lookup
   threaded from template-selections so we avoid subscribing."
  [prereqs path-prereqs race-map]
  (concat
   (map
    (fn [prereq]
      (cond
        ((into #{} character/ability-keys) prereq)
        (ability-prereq prereq 13)

        (= :spellcasting prereq)
        can-cast-spell-prereq

        :else
        (armor-prereq prereq)))
    prereqs)
   (let [race-prereqs (:race path-prereqs)
         race-keys (sequence
                    (comp
                     (filter
                      val)
                     (map
                      key))
                    race-prereqs)]
     (when (seq race-keys)
       (let [race-names (map (comp :name race-map) race-keys)]
         [(race-prereq race-names)])))))

(def filter-true (filter val))

(defn magic-initiate-selection [spells-map spell-lists]
  (t/selection-cfg
   {:name "Spell Class"
    :order 0
    :tags #{:spells}
    :options [(magic-initiate-option spells-map :bard "Bard" ::character/cha spell-lists)
              (magic-initiate-option spells-map :cleric "Cleric" ::character/wis spell-lists)
              (magic-initiate-option spells-map :druid "Druid" ::character/wis spell-lists)
              (magic-initiate-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
              (magic-initiate-option spells-map :warlock "Warlock" ::character/cha spell-lists)
              (magic-initiate-option spells-map :wizard "Wizard" ::character/int spell-lists)]}))

(defn ritual-caster-selection [spells-map spell-lists]
  (t/selection-cfg
   {:name "Ritual Caster: Spell Class"
    :tags #{:spells}
    :order 6
    :options [(ritual-caster-option spells-map :bard "Bard" ::character/cha spell-lists)
              (ritual-caster-option spells-map :cleric "Cleric" ::character/wis spell-lists)
              (ritual-caster-option spells-map :druid "Druid" ::character/wis spell-lists)
              (ritual-caster-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
              (ritual-caster-option spells-map :warlock "Warlock" ::character/cha spell-lists)
              (ritual-caster-option spells-map :wizard "Wizard" ::character/int spell-lists)]}))

(defn spell-sniper-selection [spells-map spell-lists]
  (t/selection-cfg
   {:name "Spell Sniper: Spell Class"
    :tags #{:spells}
    :options [(spell-sniper-option spells-map :bard "Bard" ::character/cha spell-lists)
              (spell-sniper-option spells-map :cleric "Cleric" ::character/wis spell-lists)
              (spell-sniper-option spells-map :druid "Druid" ::character/wis spell-lists)
              (spell-sniper-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
              (spell-sniper-option spells-map :warlock "Warlock" ::character/cha spell-lists)
              (spell-sniper-option spells-map :wizard "Wizard" ::character/int spell-lists)]}))

;; `:props` → CHOICES (proficiency/spell-choice selections). This is the choice side of grant
;; vocabulary A — but it is FEAT-ONLY (only `feat-option-from-cfg` calls it; the race/subrace/
;; subclass compile paths do not). The spell-choice arms (:ritual-casting/:magic-novice/
;; :attack-spell) are the only homebrew spell *choices* outside classes, and only as 3 fixed
;; templates — there is no general "pick N from list L" decision. Making this reachable from every
;; silo is the prime cross-silo target. See docs/kb/decision-vocabulary.md.
(defn make-feat-selections [language-map spells-map spell-lists proficiency-weapons k v]
  (when v
    (case k
      :weapon-prof-choice [(weapon-proficiency-selection v proficiency-weapons)]
      :language-choice [(language-selection-aux (vals language-map) v)]
      :skill-tool-choice (map
                          (fn [i]
                            (skilled-selection (str "Skill/Tool " (inc i))))
                          (range v))
      :ritual-casting [(ritual-caster-selection spells-map
                                                spell-lists)]
      :magic-novice [(magic-initiate-selection spells-map
                                               spell-lists)]
      :attack-spell [(spell-sniper-selection spells-map
                                             spell-lists)]
      nil)))

(defn collect-map-modifiers [m modifier-fn]
  (sequence
   (comp
    filter-true
    (map
     (fn [[k]]
       (modifier-fn k))))
   m))

(def ^:private short-ability
  "Authors may write the short :dex or the fully-qualified ability keyword; both mean the same."
  {:str ::character/str :dex ::character/dex :con ::character/con
   :int ::character/int :wis ::character/wis :cha ::character/cha})

(defn- ac-applies?
  "Do this calculation's conditions hold for the equipped armor and shield? Both tags work the same
  way, so there is one rule to remember:

    false  = only when that item is NOT equipped
    true   = only when it IS equipped
    absent = either way

  So :shield? false DISQUALIFIES the calculation while a shield is held rather than merely skipping
  the shield's bonus — a Monk holding a shield loses Unarmored Defense entirely (14, not 15). And
  :shield? true expresses the opposite, 'only while wielding a shield', which a construct-style
  homebrew feature wants. No built-in content uses that today; the vocabulary supports it because
  homebrew flexibility is the point, not because SRD needs it."
  [{:keys [armor? shield?]} armor shield]
  (and (or (nil? armor?)  (= armor?  (some? armor)))
       (or (nil? shield?) (= shield? (some? shield)))))

(defn ac-calculation-modifiers
  "Compile an authored AC calculation — {:ac N :abilities [...] :armor? b :shield? b} — into a
  formula competing in ?ac-fns. :abilities SUM (Barbarian adds both Dex and Con); 'whichever is
  better' is written as two separate calculations, which the max reconciles.

  Named abilities are taken literally. Substituting a different ability for Dex app-wide is a
  separate parameter and is not built yet."
  [{:keys [ac abilities] :as spec}]
  [(modifiers/ac-formula
    (fn [armor shield]
      (if (ac-applies? spec armor shield)
        (reduce (fn [total a] (+ total (?ability-bonuses (short-ability a a))))
                (or ac 0)
                abilities)
        0)))])

(defn ac-bonus-modifiers
  "Compile an authored flat bonus — {:ac-bonus N :armor? b :shield? b} — into ?ac-bonus-fns.
  Bonuses are summed onto whichever calculation wins, so a bonus is never lost to a calculation
  that beats the base."
  [{:keys [ac-bonus] :as spec}]
  [(modifiers/ac-bonus-fn
    (fn [armor shield]
      (if (ac-applies? spec armor shield) (or ac-bonus 0) 0)))])

;; Grant vocabulary A — `:props` → FIXED mechanics. This `case` is the shared, cross-silo
;; vocabulary: it runs for feats AND races/subraces/classes/subclasses (despite the "feat" name),
;; so adding a `case` arm here + a form field reaches every silo. The CHOICE counterpart is
;; `make-feat-selections` (feat-only). Vocabulary B is `level-modifier` (spell_subs.cljs) — they
;; overlap but diverge. See docs/kb/decision-vocabulary.md ("grant types live in up to FOUR places").
(defn make-feat-modifiers [k v option-key]
  (when v
    (case k
      :initiative [(modifiers/initiative v)]
      :ac (ac-calculation-modifiers v)
      :ac-bonus (ac-bonus-modifiers v)
      :two-weapon-ac-1 [dual-wield-ac-mod]
      :two-weapon-any-one-handed [dual-wield-weapon-mod]
      :max-hp-bonus [(mods/modifier ?hit-point-level-bonus (+ v ?hit-point-level-bonus))]
      :passive-investigation-5 [(modifiers/passive-investigation 5)]
      :passive-perception-5 [(modifiers/passive-perception 5)]
      ;; Kept for saved content (D9); it is the general :armor-dex-cap prop underneath.
      :medium-armor-max-dex-3 [medium-armor-master-max-bonus]
      ;; General form: {:armor-dex-cap {:medium 3 :heavy 2}} raises whichever types it names.
      :armor-dex-cap (mapv (fn [[armor-type cap]] (modifiers/armor-dex-cap armor-type cap)) v)
      :medium-armor-stealth [medium-armor-master-stealth]
      :speed [(modifiers/speed v)]
      :flying-speed [(modifiers/flying-speed-override v)]
      :flying-speed-equals-walking-speed [(modifiers/flying-speed-equal-to-walking)]
      :swimming-speed [(modifiers/swimming-speed-override v)]
      :saving-throw-advantage-traps [(modifiers/saving-throw-advantage [:traps])]
      ;; Kept for saved content (D9) but no longer bespoke: it compiles to the universal :ac
      ;; shape, {:ac 13 :abilities [:dex]} with no :armor? tag. That reproduces both sentences of
      ;; the rule — 13 + Dex while unarmored, and still available while armored so it wins when
      ;; the worn armor would be worse. It used to REPLACE ?armor-class-with-armor with its own
      ;; max; ?ac-fns already is that max, and shield/character-magic are summed onto the winner
      ;; rather than baked into the replacement's hardcoded sum.
      :lizardfolk-ac (when v (vec (ac-calculation-modifiers {:ac 13 :abilities [:dex]})))
      ;; Kept for saved content (D9), now split into the two things it was welding together:
      ;; a flat natural-AC calculation, and "worn armor gives no AC". The old form replaced
      ;; ?armor-class-with-armor with (+ 17 shield) so worn armor could never beat 17 — a ceiling
      ;; standing in for "a tortle can't wear armor". The split reproduces that AC behaviour
      ;; exactly while making both halves separately authorable: a high flat natural AC without
      ;; the suppression, or an armor-wearing tortle, are each one prop.
      ;; It is NOT the rules restriction. Nothing prevents equipping armor, and everything else
      ;; armor causes still applies. Building the actual restriction is roadmapped.
      ;; The ?natural-ac-bonus 7 the old form wrote alongside was inert (the replacement never
      ;; consulted ?base-armor-class), so it is gone rather than carried forward.
      :tortle-ac (when v
                   (conj (vec (ac-calculation-modifiers {:ac 17 :abilities []}))
                         (modifiers/armor-gives-no-ac)))
      ;; The AC half on its own, for authors who want it without the flat 17. Named for what it
      ;; does: worn armor stops counting toward AC. It does not prevent equipping armor, and does
      ;; not suppress anything else armor causes (stealth disadvantage still applies). A real
      ;; "you can't wear armor" restriction is roadmapped.
      :armor-gives-no-ac (when v [(modifiers/armor-gives-no-ac)])
      :language (collect-map-modifiers
                 v
                 #(modifiers/language %))
      :saving-throw-advantage (collect-map-modifiers
                               v
                               #(modifiers/saving-throw-advantage [%]))
      :skill-prof (collect-map-modifiers
                   v
                   #(modifiers/skill-proficiency %))
      :tool-prof-or-expertise (collect-map-modifiers
                                v
                                #(tool-prof-or-expertise % option-key))
      :skill-prof-or-expertise (collect-map-modifiers
                                v
                                #(skill-prof-or-expertise % option-key))
      :armor-prof (collect-map-modifiers
                   v
                   #(modifiers/armor-proficiency %))
      :weapon-prof (collect-map-modifiers
                   v
                   #(modifiers/weapon-proficiency %))
      :damage-resistance (collect-map-modifiers
                          v
                          #(modifiers/damage-resistance %))
      :damage-immunity (collect-map-modifiers
                        v
                        #(modifiers/damage-immunity %))
      nil)))

(defn plugin-modifiers [props option-key]
  (reduce
   (fn [mods [k v]]
     (let [feat-mods (make-feat-modifiers k v option-key)]
       (if feat-mods
         (concat mods feat-mods)
         mods)))
   []
   props))

(defn feat-modifiers [key name description props ability-increases]
  (let [without-saves (sets/intersection ability-increases
                                         (into #{} character/ability-keys))]
    (concat
     (plugin-modifiers props key)
     (if (= 1 (count without-saves))
       (let [ability-kw (first without-saves)
             ability-mod (modifiers/ability ability-kw 1)]
         (if (:saves? ability-increases)
           [ability-mod
            (modifiers/saving-throws nil ability-kw)]
           [ability-mod]))
       [])
     [(modifiers/trait-cfg
       {:name name
        :description description})])))

(defn feat-selections [language-map spells-map spell-lists proficiency-weapons props ability-increases]
  (let [without-saves (sets/intersection ability-increases
                                         (into #{} character/ability-keys))]
    (reduce
     (fn [selections [k v]]
       (let [feat-selections (make-feat-selections language-map spells-map spell-lists proficiency-weapons k v)]
         (if feat-selections
           (concat selections feat-selections)
           selections)))
     (if (< 1 (count without-saves))
       [(if (:saves? ability-increases)
          (ability-increase-selection
           without-saves
           1
           false
           [(fn [k] (modifiers/saving-throws nil k))])
          (ability-increase-selection
           without-saves
           1
           false))]
       [])
     props)))


;; ─── BRIDGE PROTOTYPE: feat-granted fighting style (pool+grant as DATA) ──────────
;; Additive/reversible. Mirrors the draconic-ancestry pool+grant pattern for a different
;; bucket (feats), to test whether that pattern generalizes. To revert: delete this block,
;; the `:grant` hook in feat-option-from-cfg, the grantable-pools arg at its call site
;; (template.cljc), and any ::e5/fighting-styles pool sub (spell_subs.cljs).
;; Placed here (after plugin-modifiers) so it can compile homebrew styles' :props.
(defn fighting-style-option
  "Compile a HOMEBREW fighting style (orcbrew data: name + optional :props + :description)
   into a fighting-style option — the same shape draconic-ancestry-option uses. Mechanical
   effects ride the shared :props vocabulary (plugin-modifiers); the description becomes a
   trait. (Conditional/complex effects like Mariner's need the richer mechanical-feature
   builder the maintainer flagged — out of scope for this slice.)"
  [{:keys [name key props description]}]
  (t/option-cfg
   (cond-> {:name name
            :modifiers (concat
                        (when description
                          [(modifiers/trait-cfg {:name (str name " Fighting Style")
                                                 :description description})])
                        (when props (plugin-modifiers props key)))}
     key (assoc :key key))))

(defn grant-selection
  "GENERIC cross-bucket grant. Given `:grant {:from <pool-key> …}` data and a `grantable-pools`
   registry ({pool-key {:name … :options [...]}}), produce a choice from that pool. Four modes:
     {:from p}                 -> ALL entries (choose N, default 1)
     {:from p :filter #{…}}    -> a FILTERED subset (entries whose ::t/key is in the set)
     {:from p :key :k}         -> a SPECIFIC entry (a forced single-option choice)
     (custom entry)            -> the pool already includes homebrew entries, so {:from p} grants them too
   Pool-agnostic AND owner-agnostic — one hook serves feat/background/race/subrace/class/subclass."
  [{:keys [from choose key] flt :filter :or {choose 1}} grantable-pools]
  (when-let [{:keys [name options]} (get grantable-pools from)]
    (let [opts (cond->> options
                 flt (filter (fn [o] (contains? flt (::t/key o))))
                 key (filter (fn [o] (= key (::t/key o)))))
          n    (if key 1 choose)]
      ;; NO :ref — a nested grant (inside an owner's :selections) resolves by NESTING; a top-level
      ;; :ref breaks that addressing (verified: adding one zeroed a feat-granted style's mechanic).
      ;; Top-level grants (e.g. a class's own fighting-style) carry a :ref via their own constructor.
      (t/selection-cfg
       {:name name
        :tags #{:grant from}
        :multiselect? true
        :min n
        :max n
        :options opts}))))
;; ─── end bridge prototype (part 1) ──────────────────────────────────────────────

(defn feat-option-from-cfg
  "Build a feat option. race-map is threaded from template-selections.
   Assembly fn for the FEAT silo (richest): compiles :props → fixed mechanics (make-feat-modifiers)
   AND :props → choices (make-feat-selections), :ability-increases (fixed or choose-which-to-bump),
   and :prereqs/:path-prereqs (feat-prereqs, a limited vocab). The only silo with ASI *options*,
   prereqs, and spell-choice templates. See docs/kb/decision-vocabulary.md (backward trace: Feat)."
  [language-map
   spells-map
   spell-lists
   custom-and-standard-weapons
   race-map
   grantable-pools                        ; ← BRIDGE PROTOTYPE: registry {pool-key {:name … :options}}
   {:keys [name
           key
           icon
           description
           prereqs
           path-prereqs
           props
           ability-increases
           save-proficiencies
           edit-event
           grant]}]                       ; ← BRIDGE PROTOTYPE: generic grant {:from <pool> :choose N}
  ;; ASI dual-format reader (D34 feat-path reconciliation): a feat's :ability-increases is read by
  ;; SHAPE, so the cross-silo spread reaches feats without breaking the released format.
  ;;   - vector  → the new terse [amount pool] SPREAD → compile-ability-increases (same path as
  ;;               races/backgrounds/subclasses). Gives feats amounts/groups/multi-increment.
  ;;   - set     → the LEGACY feat format (#{:str :con}, +1 to one, optional :saves? marker granting a
  ;;               save proficiency). Left untouched — saves has no spread model, so this is the only
  ;;               place it lives. Released feat data keeps working verbatim.
  ;; When the spread path is used, the set-based ASI is suppressed (pass #{}) but feat-modifiers'/
  ;; feat-selections' OTHER work (props mechanics, the trait, prop choices) still runs.
  ;; The standalone :save-proficiencies tool (independent of the bump) is wired here too, via the same
  ;; compile-save-proficiencies the other silos use — so a feat can grant saves with no ASI at all.
  (let [spread? (vector? ability-increases)
        legacy-ai (if spread? #{} ability-increases)
        ;; :general attribution — a feat's fixed ASI is NOT racial (shows under 'other', like the legacy
        ;; feat set path's modifiers/ability), not the race column.
        {ai-mods :modifiers ai-sels :selections} (when spread?
                                                    (compile-ability-increases ability-increases
                                                                               {:fixed-modifier general-ability}))
        {sp-mods :modifiers sp-sels :selections} (compile-save-proficiencies save-proficiencies)
        feat-mods (concat (feat-modifiers key
                                          name
                                          description
                                          props
                                          legacy-ai)
                          ai-mods sp-mods)
        feat-selections (concat (feat-selections language-map
                                                 spells-map
                                                 spell-lists
                                                 custom-and-standard-weapons
                                                 props
                                                 legacy-ai)
                                ai-sels sp-sels)
        ;; BRIDGE PROTOTYPE: a feat's DATA can grant a choice from any pool via the generic
        ;; :grant key. Same hook every other bucket would use — see grant-selection. The same
        ;; one line, added to background/race/subclass assembly fns, gives them grants too.
        feat-selections (cond-> feat-selections
                          grant
                          (concat [(grant-selection grant grantable-pools)]))]
    (t/option-cfg
     {:name name
      :key key
      :icon icon
      :edit-event edit-event
      :modifiers feat-mods
      :selections feat-selections
      :summary description
      :prereqs (feat-prereqs prereqs path-prereqs race-map)})))

(def draconic-ancestries
  [{:name "Black"
    :breath-weapon {:damage-type :acid
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Blue"
    :breath-weapon {:damage-type :lightning
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Brass"
    :breath-weapon {:damage-type :fire
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Bronze"
    :breath-weapon {:damage-type :lightning
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Copper"
    :breath-weapon {:damage-type :acid
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Gold"
    :breath-weapon {:damage-type :fire
                    :area-type :cone
                    :length 15
                    :save ::character/dex}}
   {:name "Green"
    :breath-weapon {:damage-type :poison
                    :area-type :cone
                    :length 15
                    :save ::character/con}}
   {:name "Red"
    :breath-weapon {:damage-type :fire
                    :area-type :cone
                    :length 15
                    :save ::character/dex}}
   {:name "Silver"
    :breath-weapon {:damage-type :cold
                    :area-type :cone
                    :length 15
                    :save ::character/con}}
   {:name "White"
    :breath-weapon {:damage-type :cold
                    :area-type :cone
                    :length 15
                    :save ::character/con}}])
