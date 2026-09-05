(ns orcpub.pdf-spec
  (:require [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.entity-spec :as es]
            [orcpub.dice :as dice]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.display :as disp5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.skills :as skill5e]
            [orcpub.dnd.e5.spell-packing :as packing]
))

(defn entity-vals [built-char kws]
  (reduce
   (fn [vs kw]
     (let [[to from] (if (keyword? kw) [kw kw] kw)]
       (assoc vs to (es/entity-val built-char from))))
   {}
   kws))

(defn class-string [classes levels]
  (s/join
   " / "
   (map
    (fn [cls-key]
      (let [{:keys [class-name class-level subclass-name]} (levels cls-key)]
        (if (empty? subclass-name)
          (str class-name " (" class-level ")")
          (str class-name " [" subclass-name "] (" class-level ")"))))
    classes)))

(defn ability-related-bonuses [suffix vals]
  (into {}
        (map
         (fn [[k v]]
           [(keyword (str (name k) "-" suffix)) (common/bonus-str v)
            v])
         vals)))

(defn save-bonuses [built-char]
  (ability-related-bonuses "save"
                           (es/entity-val built-char :save-bonuses)))

(defn skill-fields [built-char]
  (let [skill-bonuses (es/entity-val built-char :skill-bonuses)
        skill-profs (char5e/skill-proficiencies built-char)]
    (reduce
     (fn [m {k :key}]
       (-> m
           (assoc k (common/bonus-str (skill-bonuses k)))
           (assoc (keyword (str (name k) "-check")) (boolean (k skill-profs)))))
     {}
     skill5e/skills)))

(defn total-length [traits]
  (reduce + (map
             (fn [{:keys [name description]}]
               ;; Handle nil name/description gracefully
               (+ (count (or name "")) (count (or description ""))))
             traits)))

(defn trait-string [nm desc]
  ;; Handle nil name gracefully - use description start or placeholder
  (let [desc-str (when (and (string? desc) (not (s/blank? desc))) desc)
        display-name (or nm
                         (when desc-str (str (subs desc-str 0 (min 30 (count desc-str))) "..."))
                         "(Unnamed Trait)")]
    (str display-name ". " (common/sentensize desc-str))))

(defn traits-string [traits]
  (s/join
   "\n\n"
   (map
    (fn [{:keys [name description summary page source] :as trait}]
      (trait-string name (disp5e/action-description trait)))
    traits)))

(defn actions-string [title actions]
  (when (seq actions)
    (str
     title
     "\n"
     (s/join
      "\n\n"
      (map
       (fn [action]
         (trait-string (:name action) (common/sentensize (disp5e/action-description action))))
       actions)))))

(defn vec-trait [nm items]
  (when (seq items) (str nm ": " (s/join ", " items))))

(defn keyword-vec-trait [nm keywords]
  (vec-trait nm (map #(if % (name %) "(unknown)") (remove nil? keywords))))

(defn resistance-strings [resistances]
  (map
   (fn [{:keys [value qualifier]}]
     (str (if value (name value) "(unknown)")
          (when qualifier
            (str "(" qualifier ")"))))
   resistances))

(defn features-and-traits-header [built-char]
  (let [darkvision (char5e/darkvision built-char)
        damage-resistances (char5e/damage-resistances built-char)
        damage-immunities (char5e/damage-immunities built-char)
        condition-immunities (char5e/condition-immunities built-char)
        immunities (char5e/immunities built-char)
        crit-values (char5e/critical-hit-values built-char)]
    (s/join
     "\n"
     (remove nil?
             [(when (and darkvision (pos? darkvision)) (str "Darkvision: " darkvision " ft."))
              (when (> (count crit-values) 1) (str "Critical Hits: " (char5e/crit-values-str built-char)))
              (vec-trait "Damage Resistances" (resistance-strings damage-resistances))
              (vec-trait "Damage Immunities" (resistance-strings damage-immunities))
              (vec-trait "Condition Immunities" (resistance-strings condition-immunities))
              (vec-trait "Immunities" (resistance-strings immunities))]))))

(defn traits-fields [built-char]
  (let [traits-by-type (group-by :type (es/entity-val built-char :traits))
        bonus-actions (common/aloof-sort-by :name (concat (es/entity-val built-char :bonus-actions)
                                             (traits-by-type :b-action)))
        actions (common/aloof-sort-by :name (concat (es/entity-val built-char :actions)
                                       (traits-by-type :action)))
        reactions (common/aloof-sort-by :name (concat (es/entity-val built-char :reactions)
                                         (traits-by-type :reaction)))
        traits (common/aloof-sort-by :name (traits-by-type nil))
        traits-str (traits-string traits)
        actions? (or (seq bonus-actions)
                     (seq actions)
                     (seq reactions)
                     (seq traits))
        header (features-and-traits-header built-char)
        ]
    {:features-and-traits-2 (str header "\n\n"
                             (if actions?
                               (s/join
                                "\n\n"
                                (remove nil?
                                        [(actions-string "----------Bonus Actions----------" bonus-actions)
                                         (actions-string "---------------Actions--------------" actions)
                                         (actions-string "-------------Reactions-------------" reactions)
                                         (actions-string "-----------Other Traits------------" traits)]))
                               traits-str))}))

(defn attack-string [attack]
  (str "- " (trait-string (:name attack) (disp5e/attack-description attack))))

(defn attacks-string [attacks]
  (s/join
   "\n\n"
   (map
    attack-string
    attacks)))

(def coin-keys [:cp :sp :ep :gp :pp])

(defn equipment-fields
  "Build PDF equipment field map. all-magic-items-map from plugin-data."
  [built-char all-magic-items-map]
  (let [equipment-map (merge mi5e/all-equipment-map all-magic-items-map)
        equipment (es/entity-val built-char :equipment)
        armor (es/entity-val built-char :armor)
        magic-armor (es/entity-val built-char :magic-armor)
        magic-items (es/entity-val built-char :magic-items)
        weapons (sort (es/entity-val built-char :weapons))
        magic-weapons (sort (es/entity-val built-char :magic-weapons))
        custom-equipment (into {}
                               (map
                                (juxt ::char-equip5e/name identity)
                                (char5e/custom-equipment built-char)))
        custom-treasure (into {}
                               (map
                                (juxt ::char-equip5e/name identity)
                                (char5e/custom-treasure built-char)))
        all-equipment (merge equipment custom-equipment custom-treasure magic-items armor magic-armor)
        treasure (es/entity-val built-char :treasure)
        treasure-map (into {} (map (fn [[kw {qty ::char-equip5e/quantity}]] [kw qty]) treasure))
        unequipped-items  (filter
                          (fn [[kw {:keys [::char-equip5e/equipped? ::char-equip5e/quantity]}]]
                            (and (not equipped?)
                                 (pos? quantity)))
                          (merge all-equipment weapons magic-weapons magic-items))]
    (merge
     (select-keys treasure-map coin-keys)
     {:features-and-traits (s/join
                  "\n"
                  (sort
                   (map
                   (fn [[kw {count ::char-equip5e/quantity}]]
                     (if (> count 1)
                       (str (disp5e/equipment-name equipment-map kw) " x" count )
                       (str (disp5e/equipment-name equipment-map kw))
                       )
                     )
                   (filter
                    (fn [[kw {:keys [::char-equip5e/equipped? ::char-equip5e/quantity]}]] (and equipped? (pos? quantity)))
                    (concat all-equipment)))))
      :treasure (s/join
                  "\n"
                  (map
                   (fn [[kw {count ::char-equip5e/quantity}]]
                     (str (disp5e/equipment-name equipment-map kw) " (" count ")"))
                   (merge
                    (apply dissoc treasure coin-keys)
                    unequipped-items)))})))

(defn treasure-fields [built-char]
  (let [equipment (es/entity-val built-char :equipment)
        armor (es/entity-val built-char :armor)
        magic-armor (es/entity-val built-char :magic-armor)
        magic-items (es/entity-val built-char :magic-items)
        weapons (sort (es/entity-val built-char :weapons))
        magic-weapons (sort (es/entity-val built-char :magic-weapons))
        custom-equipment (into {}
                               (map
                                (juxt ::char-equip5e/name identity)
                                (char5e/custom-equipment built-char)))
        custom-treasure (into {}
                               (map
                                (juxt ::char-equip5e/name identity)
                                (char5e/custom-treasure built-char)))
        all-equipment (merge equipment custom-equipment custom-treasure magic-items armor magic-armor)
        treasure (es/entity-val built-char :treasure)
        treasure-map (into {} (map (fn [[kw {qty ::char-equip5e/quantity}]] [kw qty]) treasure))
        unequipped-items  (filter
                          (fn [[kw {:keys [::char-equip5e/equipped? ::char-equip5e/quantity]}]]
                            (and (not equipped?)
                                 (pos? quantity)))
                          (merge all-equipment weapons magic-weapons magic-items))]
    (merge
     (select-keys treasure-map coin-keys)
     {:treasure (s/join
                  "\n"
                  (map
                   (fn [[kw {count ::char-equip5e/quantity}]]
                     (str (disp5e/equipment-name mi5e/all-equipment-map kw) " (" count ")"))
                   (merge
                    (apply dissoc treasure coin-keys)
                    unequipped-items)))})))



(defn level-max-spells
  "Rows the chosen sheet style prints at each spell level.

   Reads spell-packing/sheet-geometry rather than carrying its own copy. It DID
   carry one, style 1's, and used it whatever style was being exported -- so a
   style 4 sheet was handed 8 cantrips for a box with 7 fields and lost one, and
   was handed 12 first-level spells for a box that holds 13.

   Falls back to style 1 for an unrecognised id, matching the server, which
   clamps an unknown style to the default rather than failing the export."
  [style]
  (let [rows (get packing/sheet-geometry style (get packing/sheet-geometry 1))]
    (into {} (map-indexed vector rows))))

(defn filter-prepared [spell-cfgs
                       lvl
                       prepares-spells
                       prepared-spells-by-class]
  (filter
   (fn [{:keys [key class always-prepared?]}]
     (char5e/spell-prepared? {:hide-unprepared? true
                              :always-prepared? always-prepared?
                              :lvl lvl
                              :key key
                              :class class
                              :prepares-spells prepares-spells
                              :prepared-spells-by-class prepared-spells-by-class}))
   spell-cfgs))

(defn make-page-map [spells-known
                     print-prepared-spells?
                     prepares-spells
                     prepared-spells-by-class
                     spells-map]
  (reduce-kv
   (fn [m k s]
     (let [spell-cfgs (vals s)
           filtered (if print-prepared-spells?
                      (filter-prepared spell-cfgs
                                       k
                                       prepares-spells
                                       prepared-spells-by-class)
                      spell-cfgs)
           by-ability (group-by :ability filtered)]
       (reduce-kv
        (fn [am a a-s]
          (assoc-in am [a k] (sort-by (comp :name spells-map :key) a-s)))
        m
        by-ability)))
   {}
   spells-known))

(defn make-pages [spells
                  print-prepared-spells
                  prepares-spells
                  prepared-spells-by-class
                  spells-map
                  style]
  (let [page-map (make-page-map spells
                                print-prepared-spells
                                prepares-spells
                                prepared-spells-by-class
                                spells-map)]
    (mapcat
     (fn [[ability levels]]
       (let [ability-classes (into
                              (sorted-set)
                              (mapcat
                               (fn [[_ spells]]
                                 (map :class spells))
                               levels))
             split-levels (common/map-vals
                           (fn [level spells]
                             (vec (partition-all (get (level-max-spells style) (or level 0))
                                                 spells)))
                           levels)
             num-pages (apply max
                              (map (fn [[level parts]]
                                     (count parts))
                                   split-levels))]
         (for [page-index (range num-pages)]
           {:ability ability
            :classes ability-classes
            :spells (map (fn [level]
                           {:level level
                            :spells (get-in split-levels [level page-index])})
                         (range 10))})))
     page-map)))

(defn magic-item-card-info
  "The magic items a character carries, in the shape the card printer needs.

   Every magic item, equipped or not: a card is a reference you fan through, and
   an item left in the bag is exactly the one whose wording you have forgotten.
   Only the fields a card draws are sent -- the full entries carry modifier
   functions and weapon statistics that would bloat the request for nothing."
  [built-char all-magic-items-map]
  (let [item-map (merge mi5e/magic-item-map all-magic-items-map)
        carried (merge (es/entity-val built-char :magic-items)
                       (es/entity-val built-char :magic-armor)
                       (es/entity-val built-char :magic-weapons))]
    (->> (keys carried)
         (keep item-map)
         (map #(select-keys % [:name
                               ::mi5e/name
                               ::mi5e/type
                               ::mi5e/subtype
                               ::mi5e/rarity
                               ::mi5e/attunement
                               ::mi5e/attunement-details
                               ::mi5e/description
                               ::mi5e/summary
                               ::mi5e/page
                               ::mi5e/source]))
         (sort-by #(or (:name %) (::mi5e/name %)))
         vec)))

(defn make-spell-card-info [spells-known
                            save-dc-fn
                            attack-mod-fn
                            print-prepared-spells?
                            prepares-spells
                            prepared-spells-by-class
                            spells-map
                            plugin-spells-map]

  (let [flat-spells (char5e/flat-spells spells-known)
        filtered-spells-known (reduce
                               (fn [m [k v]]
                                 (assoc m k (if print-prepared-spells?
                                              (filter-prepared (vals v)
                                                               k
                                                               prepares-spells
                                                               prepared-spells-by-class)
                                              (vals v))))
                               {}
                               spells-known)
        filtered-spell-keys (->> filtered-spells-known
                                 vals
                                 (apply concat)
                                 (map :key))
        custom-spells (sequence
                       (comp
                        (keep plugin-spells-map)
                        (map #(select-keys % [:name
                                              :key
                                              :school
                                              :description
                                              :level
                                              :components
                                              :range
                                              :casting-time
                                              :duration])))
                       filtered-spell-keys)]
    (reduce
     (fn [m {:keys [key ability qualifier class]}]
       (-> m
           (assoc-in [:spell-save-dcs class] (save-dc-fn ability))
           (assoc-in [:spell-attack-mods class] (attack-mod-fn ability))))
     {:custom-spells custom-spells
      :spells-known filtered-spells-known}
     flat-spells)))

(defn packing-classes
  "The per-class lists spell-packing/packed-fields takes.

   `spells-known` is keyed by LEVEL, each value spell-key -> config carrying the
   :class that granted it, so this regroups it the other way up. That regrouping
   is the point: the shipped layout groups by :ability instead, which puts a
   Warlock and a Sorcerer in one section under one slot row.

   Slots follow 5e rather than the grouping. Every non-pact caster draws on the
   SHARED table -- a multiclass has one pool from combined caster levels, not one
   each -- and a pact caster gets its own, which is what :pact? marks.

   A pact caster's whole list is reported at the single level it casts at, its
   highest pact slot, because that is how a Warlock casts: not a list per level."
  [spells-known spells-map shared-slots pact-slots save-dc-fn attack-mod-fn]
  (let [by-class (reduce-kv
                  (fn [acc level cfgs]
                    (reduce (fn [a cfg]
                              (update-in a [(:class cfg) level] (fnil conj []) cfg))
                            acc
                            (vals cfgs)))
                  {}
                  spells-known)
        pact-level (when (seq pact-slots) (apply max (keys pact-slots)))]
    (vec
     (for [[class levels] (sort-by key by-class)
           :let [pact? (and pact-level
                            (some #(= :warlock (common/name-to-kw (str (:class %))))
                                  (mapcat val levels)))
                 ability (:ability (first (mapcat val levels)))
                 named (fn [cfgs]
                         (mapv (fn [cfg]
                                 (let [d (get spells-map (:key cfg))]
                                   (or (:name d)
                                       (when (:key cfg) (name (:key cfg)))
                                       "(Unknown Spell)")))
                               cfgs))
                 ;; A Warlock's spells all sit at its pact level; everyone else's
                 ;; stay at the level they were learned.
                 levels (if pact?
                          (let [cantrips (get levels 0)
                                at-pact (mapcat val (dissoc levels 0))]
                            (cond-> {}
                              (seq cantrips) (assoc 0 (named cantrips))
                              (seq at-pact) (assoc pact-level (named at-pact))))
                          (into {} (map (fn [[lvl cfgs]] [lvl (named cfgs)])) levels))]]
       (cond-> {:class class
                :levels levels
                :slots (if pact? pact-slots shared-slots)
                ;; The ABBREVIATION -- "CHA" -- because this heads a column in a
                ;; bar, not the section box that reads SPELLCASTING ABILITY and
                ;; wants the full word.
                :ability (when ability (:abbr (opt5e/abilities-map ability)))}
         pact? (assoc :pact? true)
         ability (assoc :dc (save-dc-fn ability)
                        :attack (common/bonus-str (attack-mod-fn ability))))))))

(defn spell-page-fields [spells
                         spell-slots
                         save-dc-fn
                         attack-mod-fn
                         print-prepared-spells?
                         prepares-spells
                         prepared-spells-by-class
                         spells-map
                         plugin-spells-map
                         style]
  (let [spell-pages (make-pages spells
                                print-prepared-spells?
                                prepares-spells
                                prepared-spells-by-class
                                spells-map
                                style)]
    (apply
     merge
     (make-spell-card-info spells
                           save-dc-fn
                           attack-mod-fn
                           print-prepared-spells?
                           prepares-spells
                           prepared-spells-by-class
                           spells-map
                           plugin-spells-map)
     (flatten
      (map-indexed
       (fn [i {:keys [ability classes spells]}]
         ;; A class whose list outgrows one page takes another, and both would
         ;; otherwise be headed with the bare class name and carry the same slot
         ;; totals -- two identical "SLOTS EXPENDED" rows for one set of slots,
         ;; with nothing saying which page follows which.
         (let [suffix (str "-" (inc i))
               continued? (some #(= classes (:classes %)) (take i spell-pages))
               class-header {(keyword (str "spellcasting-class" suffix))
                             (cond-> (s/join ", " classes)
                               continued? (str " (continued)"))}]
           [(if ability
              (merge
               class-header
               {(keyword (str "spellcasting-ability" suffix)) (:name (opt5e/abilities-map ability))
                (keyword (str "spell-save-dc" suffix)) (save-dc-fn ability)
                (keyword (str "spell-attack-bonus" suffix)) (common/bonus-str (attack-mod-fn ability))})
              class-header)
            (map
             (fn [{:keys [level spells]}]
               (conj
                (map-indexed
                 (fn [spell-index spell]
                   {(keyword (str "spells-" level "-" (inc spell-index) suffix))
                    (let [spell-key (:key spell)
                          spell-data (spells-map spell-key)
                          spell-name (or (:name spell-data)
                                         (when spell-key (name spell-key))
                                         "(Unknown Spell)")
                          qualifier (:qualifier spell)]
                      (str spell-name (when qualifier (str " (" qualifier ")"))))})
                 spells)
                ;; Slots belong to the class, not the page, so only the first
                ;; page of a class carries them.
                (when-not continued?
                  {(keyword (str "spell-slots-" level suffix))
                   (spell-slots level)})))
             spells)]))
       spell-pages)))))

(defn casting-classes
  "The casting classes of `built-char`, grouped the way a packed layout needs.

   Separate from spellcasting-fields so the builder can ask how many casting
   classes a character has without building a field map: with one class there is
   nothing to pack, and the layout choice is not worth offering."
  [built-char spells-map]
  (packing-classes (into {}
                         (map (fn [[id datum]]
                                [id (into (sorted-map) datum)]))
                         (char5e/spells-known built-char))
                   spells-map
                   (or (char5e/shared-spell-slots built-char) {})
                   (or (char5e/pact-spell-slots built-char) {})
                   (char5e/spell-save-dc-fn built-char)
                   (char5e/spell-attack-modifier-fn built-char)))

(defn default-spell-layout
  "Which layout suits this character, before any override.

   Packed when there is more than one casting class AND the style's numerals can
   be relabelled. A single caster already reads down its own page and gains
   nothing from packing, so the tighter layout is not forced on the common case.

   A style whose numerals have not been measured is not offered it at all: a
   packed page there prints the old level number beside the new."
  [classes style]
  (if (and (> (count classes) 1)
           (packing/packing-supported? style)
           ;; A packing that cannot hold the character is not offered as the
           ;; default, and choosing it explicitly falls back below.
           (packing/fits? style (packing/packing-shape classes)))
    :packed
    :per-class))

(defn packed-spellcasting-fields
  "The field map, relabel list and column headings for a packed layout."
  [classes style]
  (let [{:keys [fields relabels headings]} (packing/packed-fields style classes)]
    (assoc fields
           :spell-relabels relabels
           :spell-headings headings)))

(defn spellcasting-fields
  "The spell page fields, in whichever layout `spell-layout` asks for.

   :per-class is what ships -- a section per casting ability, which is why a
   Warlock and a Sorcerer share one today. :packed gives each class its own run of
   boxes in one column, which is what lets a pact caster carry its own slots.

   nil asks for the default, computed from the build rather than from a
   preference: packed only when there is more than one casting class and the style
   can be relabelled."
  ([built-char print-prepared-spells? spells-map plugin-spells-map style]
   (spellcasting-fields built-char print-prepared-spells? spells-map plugin-spells-map
                        style nil))
  ([built-char print-prepared-spells? spells-map plugin-spells-map style spell-layout]
  (let [spell-attack-modifier-fn (char5e/spell-attack-modifier-fn built-char)
        spell-save-dc-fn (char5e/spell-save-dc-fn built-char)
        spell-slots (char5e/spell-slots built-char)
        prepares-spells (char5e/prepares-spells built-char)
        prepared-spells-by-class (char5e/prepared-spells-by-class built-char)
        sorted-spells-known (into {}
                                  (map (fn [[id datum]]
                                         [id (into (sorted-map) datum)]))
                                  (char5e/spells-known built-char))
        classes (casting-classes built-char spells-map)
        layout (or spell-layout (default-spell-layout classes style))]
    (if (and (= :packed layout) (packing/packing-supported? style) (seq classes)
             ;; Even when asked for outright. A packed page that lost a class is
             ;; worse than the layout the caller did not want.
             (packing/fits? style (packing/packing-shape classes)))
      (packed-spellcasting-fields classes style)
      (spell-page-fields sorted-spells-known
                         spell-slots
                         spell-save-dc-fn
                         spell-attack-modifier-fn
                         print-prepared-spells?
                         prepares-spells
                         prepared-spells-by-class
                         spells-map
                         plugin-spells-map
                         style)))))

(defn profs-paragraph [profs prof-map title]
  (when (seq profs)
    (str
     title
     " Proficiencies: "
     (s/join "; " (map (fn [p]
                         (let [prof (if p (prof-map p) nil)]
                           (cond
                             (:name prof) (:name prof)
                             (keyword? p) (s/capitalize (name p))
                             (string? p) (s/capitalize p)
                             :else "(unknown)")))
                       (sort (remove nil? profs)))))))

(defn other-profs-field [built-char language-map]
  (let [tool-profs (char5e/tool-proficiencies built-char)
        weapon-profs (char5e/weapon-proficiencies built-char)
        armor-profs (char5e/armor-proficiencies built-char)
        languages (char5e/languages built-char)]
    (s/join
     "\n\n"
     (remove
      nil?
      [(profs-paragraph (map first tool-profs) equip5e/tools-map "Tool")
       (profs-paragraph weapon-profs weapon5e/weapons-map "Weapon")
       (profs-paragraph armor-profs armor5e/armor-map "Armor")
       (profs-paragraph languages language-map "Language")]))))

(defn damage-str [die die-count mod damage-type]
  (str (dice/dice-string die-count die mod)
       (when damage-type
         (str " " (if (keyword? damage-type) (name damage-type) (str damage-type))))))

(defn attacks-and-spellcasting-fields 
  "For each weapon, we are creating a new map with the name, the attack bonus, and the damage.
  The attack bonus is calculated using the function 'char5e/best-weapon-attack-modifier' which
  calculates the attack bonus for a specific weapon.
  The damage is calculated using the function 'damage-str' which takes the damage die, damage die count,
  the damage modifier, and the damage type, and combines them into a string of the form 'dice-string damage-type'.
  If the weapon is versatile, we also create a second map, where the name is suffixed with ' (two-handed)'
  and the attack bonus and damage is calculated for the versatile form of the weapon.
  We then remove any nil maps, and mapcat concatenates all of the maps into a single list.
  The remove function filters out weapons of type 'ammunition'."
  [built-char all-weapons-map]
  (let [all-weapons ; map of all weapons in inventory
        (mi5e/equipped-items-details ; function to filter for equipped weapons
         (char5e/all-weapons-inventory built-char)
         all-weapons-map)
        weapon-fields (mapcat
                       (fn [{:keys [name ::weapon5e/damage-die ::weapon5e/damage-die-count ::weapon5e/damage-type] :as weapon}]
                         (let [versatile (:versatile weapon)
                               normal-damage-modifier (char5e/best-weapon-damage-modifier built-char weapon false)
                               finesse-damage-modifier (char5e/weapon-damage-modifier built-char weapon true)
                               normal {:name (:name weapon)
                                       :attack-bonus (char5e/best-weapon-attack-modifier built-char weapon)
                                       :damage (damage-str damage-die damage-die-count normal-damage-modifier damage-type)}]
                           (remove
                            nil?
                            [normal
                             (when (:versatile weapon)
                               {:name (str (:name weapon) " (two-handed)")
                                :attack-bonus (char5e/weapon-attack-modifier built-char weapon false)
                                :damage (damage-str (:damage-die versatile) (:damage-die-count versatile) normal-damage-modifier damage-type)})])))
                       (remove
                        (fn [{:keys [::weapon5e/type]}]
                          (= type :ammunition))
                        all-weapons))
        first-3-weapons (take 3 weapon-fields)
        rest-weapons (drop 3 weapon-fields)
        number-of-attacks (char5e/number-of-attacks built-char)]

    (apply merge
     {:attacks-and-spellcasting (str "Number of Attacks: " number-of-attacks "\n"
                                     (s/join "\n"
                                        (concat (map
                                                 attack-string
                                                 (es/entity-val built-char :attacks))
                                                (map
                                                 (fn [{:keys [name attack-bonus damage]}]
                                                   (str name ". " (common/bonus-str attack-bonus) ", " damage))
                                                 rest-weapons))))}
     (map-indexed
      (fn [i {:keys [name attack-bonus damage]}]
        {(keyword (str "weapon-name-" (inc i))) name
         (keyword (str "weapon-attack-bonus-" (inc i))) (common/bonus-str attack-bonus)
         (keyword (str "weapon-damage-" (inc i))) damage})
      first-3-weapons))))

(defn speed [built-char]
  (let [speed (char5e/base-land-speed built-char)
        speed-with-armor (char5e/land-speed-with-armor built-char)
        unarmored-speed-bonus (char5e/unarmored-speed-bonus built-char)
        equipped-armor (char5e/normal-armor-inventory built-char)
        unarmored-speed (+ (or unarmored-speed-bonus 0)
                           (if speed-with-armor
                             (speed-with-armor nil)
                             speed))]
    (if (not= unarmored-speed speed)
      (str unarmored-speed "/" speed)
      speed)))

(defn abilities-spec [vals suffix bonus?]
  (reduce-kv
   (fn [m k v]
     (let [new-k (if suffix
                   (keyword (str (name k) "-mod"))
                   k)
           new-v (if bonus? (common/bonus-str v) v)]
       (assoc m new-k new-v)))
   {}
   vals))

(defn make-spec
  "Generate the PDF field spec. plugin-data is a map with keys:
   :spells-map, :plugin-spells-map, :language-map,
   :all-weapons-map, :all-magic-items-map, :current-armor-class
   — all pre-subscribed by the calling component."
  [built-char
   id
   {:keys [print-character-sheet?
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
           spell-layout] :as options}
   {:keys [spells-map plugin-spells-map language-map
           all-weapons-map all-magic-items-map current-armor-class]}]
  (let [race (char5e/race built-char)
        subrace (char5e/subrace built-char)
        abilities (abilities-spec
                   (char5e/ability-values built-char)
                   (when (not print-large-abilities?)
                     "-mod")
                   false)
        ability-bonuses (abilities-spec
                         (char5e/ability-bonuses built-char)
                         (when print-large-abilities?
                           "-mod")
                         true)
        saving-throws (set (char5e/saving-throws built-char))
        unarmored-armor-class (char5e/base-armor-class built-char)
        ac-with-armor-fn (char5e/armor-class-with-armor built-char)
        all-armor-inventory (mi5e/equipped-armor-details (char5e/all-armor-inventory built-char))
        equipped-armor (armor5e/non-shields all-armor-inventory)
        equipped-shields (armor5e/shields all-armor-inventory)
        all-armor-classes (for [armor (conj equipped-armor nil)
                                shield (conj equipped-shields nil)]
                            (ac-with-armor-fn armor shield))
        max-armor-class (apply max all-armor-classes)
        levels (char5e/levels built-char)
        classes (char5e/classes built-char)
        character-name (char5e/character-name built-char)
        con-mod (es/entity-val built-char :con-mod)
        total-hit-dice (->> levels
                            vals
                            (reduce
                              (fn [levels-per-die level]
                                (update levels-per-die (:hit-die level) (fnil #(+ % (:class-level level)) 0)))
                              {})
                            (sort-by key)
                            (map #(str (val %) "x(1d" (key %) "+" con-mod ")"))
                            (s/join "\n"))
        speed (speed built-char)]
    (merge
     {:race (str race (when subrace (str "/" subrace)))
      :alignment (char5e/alignment built-char)
      :class-level (class-string classes levels)
      :background (char5e/background built-char)
      :prof-bonus (common/bonus-str (es/entity-val built-char :prof-bonus))
      :ac current-armor-class
      :hd total-hit-dice
      :initiative (common/bonus-str (es/entity-val built-char :initiative))
      :speed speed
      :hp-max (es/entity-val built-char :max-hit-points)
      :hp-current (char5e/current-hit-points built-char)
      :passive (es/entity-val built-char :passive-perception)
      :other-profs (other-profs-field built-char language-map)
      :personality-traits (s/join "\n\n" [(char5e/personality-trait-1 built-char) (char5e/personality-trait-2 built-char)])
      :ideals (char5e/ideals built-char)
      :bonds (char5e/bonds built-char)
      :flaws (char5e/flaws built-char)
      :backstory (char5e/description built-char)
      :character-name character-name
      :character-name-2 character-name
      :xp (char5e/xps built-char)
      :player-name (char5e/player-name built-char)
      :age (char5e/age built-char)
      :height (char5e/height built-char)
      :weight (char5e/weight built-char)
      :eyes (char5e/eyes built-char)
      :skin (char5e/skin built-char)
      :hair (char5e/hair built-char)
      :image-url (char5e/image-url built-char)
      :image-url-failed (char5e/image-url-failed built-char)
      :faction-image-url (char5e/faction-image-url built-char)
      :faction-image-url-failed (char5e/faction-image-url-failed built-char)
      :faction-name (char5e/faction-name built-char)
      :print-character-sheet? print-character-sheet?
      :print-spell-cards? print-spell-cards?
      :print-character-sheet-style? print-character-sheet-style?
      :print-spell-card-dc-mod? print-spell-card-dc-mod?
      :print-card-back-logo? print-card-back-logo?
      :card-back-logo-faded? card-back-logo-faded?
      :print-bw? print-bw?
      :bw-faded? bw-faded?
      :print-magic-item-cards? print-magic-item-cards?
      :magic-items-known (when print-magic-item-cards?
                           (magic-item-card-info built-char all-magic-items-map))
      }
     (attacks-and-spellcasting-fields built-char all-weapons-map)
     (skill-fields built-char)
     abilities
     ability-bonuses
     (save-bonuses built-char)
     (reduce
      (fn [saves key]
        (assoc saves (keyword (str (name key) "-save-check")) (boolean (key saving-throws))))
      {}
      char5e/ability-keys)
     (traits-fields built-char)
     (equipment-fields built-char all-magic-items-map)
     (spellcasting-fields built-char print-prepared-spells? spells-map plugin-spells-map
                          print-character-sheet-style? spell-layout))))
