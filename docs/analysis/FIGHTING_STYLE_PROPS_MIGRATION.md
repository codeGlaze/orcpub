# Fighting Style Props Migration - Complete Picture

**Correcting the oversimplified examples** with actual data structure

---

## What's Actually In Fighting Styles

### Current Structure (Complete)

```clojure
(def fighting-style-options
  [(t/option-cfg
    {:name "Archery"  // Short name for selection UI
     :modifiers [
       ;; Game mechanic modifier
       (modifiers/ranged-attack-bonus 2)

       ;; Display trait with metadata
       (modifiers/trait-cfg
         {:name "Archery Fighting Style"  // Full display name
          :page 72                         // Page reference (PHB)
          :description "You gain a +2 bonus to attack rolls you make with ranged weapons."})]})

   (t/option-cfg
    {:name "Protection"
     :modifiers [
       ;; Different type - REACTION, not trait!
       (modifiers/reaction
         {:name "Protection Fighting Style"
          :page 72
          :description "When a creature you can see attacks a target other than you that is within 5 feet of you, you can use your reaction to impose disadvantage on the attack roll. You must be wielding a shield."})]})

   (t/option-cfg
    {:name "Dueling"
     :modifiers [
       ;; Trait with description
       (modifiers/trait-cfg
         {:name "Dueling Fighting Style"
          :page 72
          :description "When you are wielding a melee weapon in one hand and no other weapons, you gain a +2 bonus to damage rolls with that weapon."})

       ;; Complex conditional damage modifier
       (mods/vec-mod ?damage-bonus-fns
         (fn [weapon _]
           (if (or (weapon ::weapons/two-handed?)
                   (weapon ::weapons/ranged?))
             0
             2))
         nil nil
         [(complex-weapon-checking-logic)])]})])
```

**Key observations**:
1. ✅ Short name (`:name`) for selection dropdown
2. ✅ Full trait name (in `trait-cfg`)
3. ✅ Page numbers (`:page`)
4. ✅ Full descriptions (`:description`)
5. ✅ Different modifier types (`trait-cfg` vs `reaction`)
6. ✅ Complex conditional modifiers (Dueling's weapon check)

---

## The Props Format Would Need All This Too

### What Props Would Look Like (Complete)

```clojure
(def fighting-style-data
  [;; Simple style - just a bonus
   {:name "Archery"
    :key :archery
    :page 72
    :source :phb
    :description "You gain a +2 bonus to attack rolls you make with ranged weapons."
    :props {:ranged-attack-bonus 2}}

   ;; Reaction-based style
   {:name "Protection"
    :key :protection
    :page 72
    :source :phb
    :description "When a creature you can see attacks a target other than you that is within 5 feet of you, you can use your reaction to impose disadvantage on the attack roll. You must be wielding a shield."
    :ability-type :reaction  // ← Marks this as reaction, not trait
    :props {}}  // No numeric props, just the reaction

   ;; Complex conditional style
   {:name "Dueling"
    :key :dueling
    :page 72
    :source :phb
    :description "When you are wielding a melee weapon in one hand and no other weapons, you gain a +2 bonus to damage rolls with that weapon."
    :props {:conditional-damage-bonus
            {:value 2
             :conditions {:weapon-one-handed true
                         :no-off-hand-weapon true
                         :not-two-handed true
                         :not-ranged true}}}}])
```

**Conversion function would need to handle**:
```clojure
(defn fighting-style-option-from-cfg
  [{:keys [name key page source description ability-type props]}]
  (let [;; Convert props to game mechanic modifiers
        mechanic-mods (plugin-modifiers props key)

        ;; Create trait/reaction/bonus-action wrapper
        display-mod (case ability-type
                      :reaction
                      (modifiers/reaction
                        {:name (str name " Fighting Style")
                         :page page
                         :source source
                         :description description})

                      :bonus-action
                      (modifiers/bonus-action
                        {:name (str name " Fighting Style")
                         :page page
                         :source source
                         :description description})

                      ;; Default: trait
                      (modifiers/trait-cfg
                        {:name (str name " Fighting Style")
                         :page page
                         :source source
                         :description description}))

        ;; Combine both
        all-mods (concat mechanic-mods [display-mod])]

    (t/option-cfg
      {:name name
       :key key
       :modifiers all-mods})))
```

---

## The Missing Pieces in Props System

### Problem 1: Complex Conditional Modifiers

**Dueling's conditional damage** (current):
```clojure
(mods/vec-mod ?damage-bonus-fns
  (fn [weapon _]
    (if (or (weapon ::weapons/two-handed?)
            (weapon ::weapons/ranged?))
      0
      2))
  nil nil
  [(let [main-hand-weapon ?orcpub.dnd.e5.character/main-hand-weapon
         off-hand-weapon ?orcpub.dnd.e5.character/off-hand-weapon
         all-weapons-map @(subscribe [::mi/all-weapons-map])]
     (and (and main-hand-weapon
               (-> all-weapons-map
                   main-hand-weapon
                   ::weapons/melee?)
               (not (-> all-weapons-map
                        main-hand-weapon
                        ::weapons/two-handed?)))
          (and off-hand-weapon
               (not (-> all-weapons-map
                        off-hand-weapon
                        ::weapons/type)))))])
```

**This is VERY complex!**
- Checks main-hand weapon properties
- Checks off-hand weapon status
- Uses re-frame subscriptions
- Dynamic runtime logic

**Props format would need**:
```clojure
:props {:conditional-damage-bonus
        {:value 2
         :weapon-conditions {:melee true
                            :one-handed true
                            :no-two-handed true
                            :no-ranged true}
         :off-hand-conditions {:no-weapon true}}}
```

**Then `make-feat-modifiers` would need to handle**:
```clojure
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; ... existing props ...

    :conditional-damage-bonus
    [(create-conditional-damage-modifier v)]  // ← Complex!

    nil))

(defn create-conditional-damage-modifier [config]
  (mods/vec-mod ?damage-bonus-fns
    (fn [weapon character]
      (if (meets-weapon-conditions? weapon character (:weapon-conditions config))
        (:value config)
        0))
    nil nil
    [(complex-weapon-and-character-checks config)]))
```

**This is doable, but NOT trivial!**

### Problem 2: Ability Type Metadata

**Current**: Implicit in modifier type used
```clojure
(modifiers/trait-cfg {...})      // Passive ability
(modifiers/reaction {...})       // Reaction
(modifiers/bonus-action {...})   // Bonus action
```

**Props format would need**:
```clojure
{:ability-type :reaction  // ← Explicit metadata
 :props {...}}
```

**Or**: Make it a prop?
```clojure
{:props {:grants-reaction {:name "Protection"
                          :description "..."}}}
```

### Problem 3: Description vs Mechanics Split

**Current**: Description is in `trait-cfg` modifier
```clojure
:modifiers [(modifiers/ranged-attack-bonus 2)        // Mechanic
            (modifiers/trait-cfg {:description "..."})]  // Display
```

**Props format**: Description is top-level, mechanics in `:props`
```clojure
{:description "You gain a +2 bonus..."  // Top-level
 :props {:ranged-attack-bonus 2}}       // Mechanics
```

**Conversion must recreate trait-cfg**:
```clojure
(defn fighting-style-option-from-cfg [cfg]
  (let [mechanic-mods (plugin-modifiers (:props cfg) (:key cfg))
        trait-mod (modifiers/trait-cfg
                    {:name (str (:name cfg) " Fighting Style")
                     :page (:page cfg)
                     :description (:description cfg)})]
    (t/option-cfg
      {:modifiers (concat mechanic-mods [trait-mod])})))
```

---

## What Would ACTUALLY Need to Be Done

### Step 1: Extend `make-feat-modifiers` for Fighting Style Props

**Add cases for fighting-style-specific mechanics**:
```clojure
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; Existing feat props
    :initiative [(modifiers/initiative v)]
    :skill-prof (map #(modifiers/skill-proficiency %) v)

    ;; NEW: Simple fighting style props
    :ranged-attack-bonus [(modifiers/ranged-attack-bonus v)]
    :melee-attack-bonus [(modifiers/melee-attack-bonus v)]
    :armored-ac-bonus [(modifiers/armored-ac-bonus v)]

    ;; NEW: Complex conditional props
    :conditional-damage-bonus [(create-conditional-damage-modifier v)]
    :weapon-ability-damage-modifier [(create-weapon-ability-modifier v)]

    nil))
```

### Step 2: Create Conversion Function

```clojure
(defn fighting-style-option-from-cfg
  [{:keys [name key page source description ability-type props] :as cfg}]
  (let [;; Convert props to mechanic modifiers
        mechanic-mods (plugin-modifiers props key)

        ;; Create display modifier (trait/reaction/bonus-action)
        display-mod-fn (case ability-type
                         :reaction modifiers/reaction
                         :bonus-action modifiers/bonus-action
                         modifiers/trait-cfg)  // Default

        display-mod (display-mod-fn
                      {:name (str name " Fighting Style")
                       :page page
                       :source source
                       :description description})

        ;; Combine
        all-mods (if (seq mechanic-mods)
                   (concat mechanic-mods [display-mod])
                   [display-mod])]

    (t/option-cfg
      {:name name
       :key key
       :modifiers all-mods})))
```

### Step 3: Convert Data Format

```clojure
(def fighting-style-data
  [;; PHB Styles
   {:name "Archery"
    :key :archery
    :page 72
    :source :phb
    :description "You gain a +2 bonus to attack rolls you make with ranged weapons."
    :props {:ranged-attack-bonus 2}}

   {:name "Defense"
    :key :defense
    :page 72
    :source :phb
    :description "While you are wearing armor, you gain a +1 bonus to AC."
    :props {:armored-ac-bonus 1}}

   {:name "Dueling"
    :key :dueling
    :page 72
    :source :phb
    :description "When you are wielding a melee weapon in one hand and no other weapons, you gain a +2 bonus to damage rolls with that weapon."
    :props {:conditional-damage-bonus
            {:value 2
             :weapon-conditions {:one-handed true :melee true}
             :off-hand-conditions {:no-weapon true}}}}

   {:name "Great Weapon Fighting"
    :key :great-weapon-fighting
    :page 72
    :source :phb
    :description "When you roll a 1 or 2 on a damage die for an attack you make with a melee weapon that you are wielding with two hands, you can reroll the die and must use the new roll, even if the new roll is a 1 or a 2. The weapon must have the two-handed or versatile property for you to gain this benefit."
    :props {}}  // No props - this is purely descriptive!

   {:name "Protection"
    :key :protection
    :page 72
    :source :phb
    :description "When a creature you can see attacks a target other than you that is within 5 feet of you, you can use your reaction to impose disadvantage on the attack roll. You must be wielding a shield."
    :ability-type :reaction
    :props {}}  // No props - reaction is defined by ability-type

   {:name "Two Weapon Fighting"
    :key :two-weapon-fighting
    :page 72
    :source :phb
    :description "When you engage in two-weapon fighting, you can add your ability modifier to the damage of the second attack."
    :props {:weapon-ability-damage-modifier true}}])

(def fighting-style-options
  (map fighting-style-option-from-cfg fighting-style-data))
```

### Step 4: Handle Edge Cases

**Great Weapon Fighting** - purely descriptive!
```clojure
{:name "Great Weapon Fighting"
 :description "When you roll a 1 or 2..."
 :props {}}  // ← No mechanical props, just the description!
```

**This is fine** - `trait-cfg` with description is still created, no game mechanics added.

**Two Weapon Fighting** - complex ability modifier
```clojure
// Current (direct modifier)
(mods/modifier ?weapon-ability-damage-modifier
  (fn [weapon finesse? _]
    (?weapon-ability-modifier weapon finesse?)))

// Would need prop like:
:props {:weapon-ability-damage-modifier true}

// And make-feat-modifiers case:
:weapon-ability-damage-modifier
  [(mods/modifier ?weapon-ability-damage-modifier
     (fn [weapon finesse? _]
       (?weapon-ability-modifier weapon finesse?)))]
```

---

## Conclusion: Is It Worth It?

### What You'd Preserve

✅ All page numbers
✅ All source references
✅ All descriptions
✅ All ability types (trait, reaction, bonus action)
✅ All game mechanics
✅ Character saves unchanged (`:archery` key)

### What You'd Need to Build

🔨 Complex conditional prop handlers:
- `conditional-damage-bonus` (Dueling)
- `weapon-ability-damage-modifier` (Two Weapon Fighting)

🔨 Ability type handling:
- Map `:ability-type` → `trait-cfg` / `reaction` / `bonus-action`

🔨 Description wrapper:
- Always create trait-cfg with description, even if no mechanic props

### Is It Worth the Effort?

**For just converting existing styles**: Maybe not
- Current hardcoded styles work fine
- Complex modifiers (Dueling) need custom handlers anyway
- Effort vs benefit is unclear

**For enabling plugin fighting styles**: YES!
- Plugin styles would use props (they have to, it's data)
- Need conversion function anyway
- Can keep SOURCE styles as direct modifiers (grandfathered)
- Or convert them for consistency (but not required)

### Recommended Approach

**Don't convert existing SOURCE styles immediately:**
```clojure
// Keep existing as-is (direct modifiers)
(def source-fighting-style-options
  [(t/option-cfg {:modifiers [...]})])

// Support plugin styles with props
(defn plugin-fighting-style-options [plugins]
  (map fighting-style-option-from-cfg
       (mapcat ::e5/fighting-styles (vals plugins))))

// Merge both
(defn all-fighting-style-options [plugins]
  (concat source-fighting-style-options
          (plugin-fighting-style-options plugins)))
```

**Advantage**: No migration needed, plugin support works, both patterns coexist.

**Later**: Can optionally migrate SOURCE styles to props for consistency.

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Status**: Complete Analysis with All Metadata
