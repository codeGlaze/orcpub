# Props Implementation Design - Option C (Hybrid Pattern)

## Core Principle
Use Pattern 3 (direct value) where the value is a **map of parameters**.
- **Disabled**: `:uses 0` or key missing entirely
- **Enabled**: `:uses > 0`

## Resource Pool Features

### Barbarian Rage
```clojure
{:props {:rage {:uses 2              ; Number of rages per long rest
                :damage 2            ; Bonus damage while raging
                :duration 10}}}      ; Duration in rounds (turns)
```

**Conversion in `make-feat-modifiers`:**
```clojure
:rage (when (pos? (:uses v))
        [(mod5e/bonus-action
          {:name "Rage"
           :duration (:duration v)
           :summary (str "..." (:damage v) " bonus damage...")})
         (mod5e/limited-use
          {:name "Rage"
           :uses (:uses v)
           :restore-trigger :long-rest})
         (mod5e/damage-bonus (:damage v) {:while-raging true})])
```

### Monk Ki
```clojure
{:props {:ki {:uses 3}}}             ; Ki points = monk level
```

**Conversion:**
```clojure
:ki (when (pos? (:uses v))
      [(mod5e/limited-use
        {:name "Ki Points"
         :uses (:uses v)
         :restore-trigger :short-rest})])
```

### Rogue Sneak Attack
```clojure
{:props {:sneak-attack {:dice 2}}}   ; 2d6 sneak attack damage
```

**Conversion:**
```clojure
:sneak-attack (when (pos? (:dice v))
                [(mod5e/conditional-damage
                  {:name "Sneak Attack"
                   :dice-count (:dice v)
                   :dice-type 6
                   :trigger "once per turn when you have advantage"})])
```

### Warlock Spell Slots
```clojure
{:props {:warlock-spell-slots {:slots 2         ; Number of slots
                               :level 3}}}      ; Slot level
```

**Conversion:**
```clojure
:warlock-spell-slots
  (when (pos? (:slots v))
    [(mod5e/spell-slots
      {:level (:level v)
       :slots (:slots v)
       :restore-trigger :short-rest})])
```

### Sorcerer Sorcery Points
```clojure
{:props {:sorcery-points {:uses 3}}} ; Sorcery points = sorcerer level
```

### Fighter Action Surge
```clojure
{:props {:action-surge {:uses 1}}}   ; Number of uses between rests
```

### Paladin Lay On Hands
```clojure
{:props {:lay-on-hands {:pool 15}}}  ; HP pool = paladin level × 5
```

### Cleric Channel Divinity
```clojure
{:props {:channel-divinity {:uses 1}}} ; Uses between rests
```

### Bard Bardic Inspiration
```clojure
{:props {:bardic-inspiration {:uses 3      ; Charisma modifier
                              :die 6}}}    ; d6, d8, d10, d12
```

### Druid Wild Shape
```clojure
{:props {:wild-shape {:uses 2              ; Uses between rests
                      :max-cr 0.25}}}      ; Maximum CR
```

## Attack/Damage Modifiers

### Extra Attack
```clojure
{:props {:extra-attack {:num-attacks 2}}}  ; Total attacks (2, 3, or 4)
```

**Conversion:**
```clojure
:extra-attack (when (> (:num-attacks v) 1)
                [(mod5e/num-attacks (:num-attacks v))])
```

### Martial Arts (Monk)
```clojure
{:props {:martial-arts {:die 4}}}          ; d4, d6, d8, d10
```

### Rage Damage (separate from rage resource)
```clojure
{:props {:rage-damage {:bonus 2}}}         ; +2, +3, +4
```

## Spellcasting Features

### Spell Slot Levels
```clojure
{:props {:spell-slots {1 4    ; 1st-level: 4 slots
                       2 3    ; 2nd-level: 3 slots
                       3 2}}} ; 3rd-level: 2 slots
```

### Cantrips Known
```clojure
{:props {:cantrips-known {:count 3}}}
```

### Spells Known
```clojure
{:props {:spells-known {:count 6}}}
```

### Ritual Casting
```clojure
{:props {:ritual-casting {:enabled true}}} ; Simple boolean still works
```

## Movement & Defense

### Unarmored Defense
```clojure
{:props {:unarmored-defense {:ability-1 :dex    ; DEX + ...
                             :ability-2 :wis}}} ; WIS
```

### Unarmored Movement
```clojure
{:props {:unarmored-movement {:bonus 10}}}      ; +10 ft speed
```

### Fast Movement (Barbarian)
```clojure
{:props {:fast-movement {:bonus 10}}}           ; +10 ft speed
```

## Proficiencies & Expertise

### Armor Proficiency (keeps existing Pattern 2)
```clojure
{:props {:armor-prof {:light true :medium true :heavy false}}}
```

### Skill Expertise
```clojure
{:props {:expertise {:acrobatics true :stealth true}}}
```

## Level-Scaling Features

For features that scale with level, store the **current value** at each level:

```clojure
;; Barbarian Level 5
{:rage {:uses 3 :damage 2}
 :extra-attack {:num-attacks 2}}

;; Barbarian Level 9
{:rage {:uses 4 :damage 3}
 :extra-attack {:num-attacks 2}}
```

## Implementation Steps

### 1. Extend `make-feat-modifiers` (options.cljc:3230-3286)

Add cases for each feature type:

```clojure
(defn make-feat-modifiers [k v option-key]
  (if v
    (case k
      ;; Existing cases...
      :initiative [(modifiers/initiative v)]
      :speed [(modifiers/speed v)]
      :armor-prof (collect-map-modifiers v #(modifiers/armor-proficiency %))

      ;; NEW: Resource Pools
      :rage (rage-modifiers v option-key)
      :ki (ki-modifiers v option-key)
      :sneak-attack (sneak-attack-modifiers v option-key)
      :action-surge (action-surge-modifiers v option-key)
      :bardic-inspiration (bardic-inspiration-modifiers v option-key)
      :wild-shape (wild-shape-modifiers v option-key)
      :channel-divinity (channel-divinity-modifiers v option-key)
      :lay-on-hands (lay-on-hands-modifiers v option-key)
      :sorcery-points (sorcery-points-modifiers v option-key)

      ;; NEW: Attack Modifiers
      :extra-attack (when (> (:num-attacks v) 1)
                      [(modifiers/num-attacks (:num-attacks v))])
      :martial-arts (martial-arts-modifiers v option-key)

      ;; NEW: Defense
      :unarmored-defense (unarmored-defense-modifiers v option-key)
      :unarmored-movement (when (pos? (:bonus v))
                            [(modifiers/speed (:bonus v) {:condition "while unarmored"})])

      ;; NEW: Spellcasting
      :spell-slots (spell-slots-modifiers v option-key)
      :cantrips-known (when (pos? (:count v))
                        [(modifiers/cantrips-known (:count v))])
      :spells-known (when (pos? (:count v))
                      [(modifiers/spells-known (:count v))])

      nil)))
```

### 2. Create Helper Functions

Each complex feature gets a helper function:

```clojure
(defn rage-modifiers [rage-cfg option-key]
  (when (pos? (:uses rage-cfg))
    [(mod5e/bonus-action
      {:name "Rage"
       :page 48
       :source :phb
       :summary (str "You have advantage on STR checks and saves, +"
                     (:damage rage-cfg) " melee damage, resistance to physical damage.")
       :duration (:duration rage-cfg)})
     (mod5e/limited-use
      {:name "Rage"
       :uses (:uses rage-cfg)
       :restore-trigger :long-rest})
     (mod5e/damage-resistance :bludgeoning {:while-raging true})
     (mod5e/damage-resistance :piercing {:while-raging true})
     (mod5e/damage-resistance :slashing {:while-raging true})
     (mod5e/ability-check-advantage :str {:while-raging true})
     (mod5e/saving-throw-advantage :str {:while-raging true})]))

(defn ki-modifiers [ki-cfg option-key]
  (when (pos? (:uses ki-cfg))
    [(mod5e/limited-use
      {:name "Ki Points"
       :uses (:uses ki-cfg)
       :restore-trigger :short-rest})]))

(defn sneak-attack-modifiers [sa-cfg option-key]
  (when (pos? (:dice sa-cfg))
    [(mod5e/conditional-damage
      {:name "Sneak Attack"
       :dice-count (:dice sa-cfg)
       :dice-type 6
       :trigger "once per turn with advantage or ally within 5ft"})]))
```

### 3. Update UI (views.cljs)

Add input fields for map values:

```clojure
(defn class-rage-config [class]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Rage"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     [:label "Uses per Long Rest"]
     [comps/number-input
      (get-in class [:props :rage :uses])
      #(dispatch [::classes/set-class-prop-value :rage :uses %])]]
    [:div.m-r-20.m-b-10
     [:label "Damage Bonus"]
     [comps/number-input
      (get-in class [:props :rage :damage])
      #(dispatch [::classes/set-class-prop-value :rage :damage %])]]
    [:div.m-r-20.m-b-10
     [:label "Duration (rounds)"]
     [comps/number-input
      (get-in class [:props :rage :duration])
      #(dispatch [::classes/set-class-prop-value :rage :duration %])]]]])
```

### 4. Add Event Handlers (events.cljs)

```clojure
(reg-event-db
 ::classes/set-class-prop-value
 class-interceptors
 (fn [class [_ prop-key sub-key value]]
   (assoc-in class [:props prop-key sub-key] value)))
```

## Testing Strategy

### Unit Tests for Conversion
```clojure
(deftest rage-modifiers-test
  (testing "Rage with uses > 0 creates modifiers"
    (is (seq (make-feat-modifiers :rage {:uses 2 :damage 2 :duration 10} :test))))

  (testing "Rage with uses = 0 creates no modifiers"
    (is (nil? (make-feat-modifiers :rage {:uses 0 :damage 2} :test)))))
```

### Integration Tests
1. Create homebrew Barbarian with `:rage {:uses 2 :damage 2}`
2. Add to character
3. Verify modifiers appear in character sheet
4. Verify limited-use tracking works

## Backward Compatibility

**CRITICAL**: All existing `:props` continue to work unchanged:
- `:initiative 2` → Still works (Pattern 3)
- `:armor-prof {:light true}` → Still works (Pattern 2)
- `:passive-perception-5 true` → Still works (Pattern 1)

**New features** use Pattern 3 with map values:
- `:rage {:uses 2 :damage 2}` → New (Pattern 3 + map)

No breaking changes to existing data!

## Implementation Status

### ✅ Completed: Rage Prototype

**File: `src/cljc/orcpub/dnd/e5/options.cljc`**

#### 1. Helper Function (lines 3230-3248)
```clojure
(defn rage-modifiers [rage-cfg option-key]
  "Creates modifiers for Barbarian Rage feature.
   rage-cfg map should contain :uses and :damage.
   If :uses is 0 or missing, returns nil (feature disabled)."
  (when (and rage-cfg (pos? (or (:uses rage-cfg) 0)))
    (let [{:keys [uses damage]} rage-cfg
          damage-val (or damage 2)]
      [(modifiers/bonus-action
        {:name "Rage"
         :page 48
         :duration units5e/minutes-1
         :frequency (units5e/long-rests uses)
         :summary (str "Advantage on STR checks and saves; "
                       (common/bonus-str damage-val) " melee damage; "
                       "resistance to bludgeoning, piercing, and slashing damage")})
       (modifiers/damage-resistance :bludgeoning "while raging")
       (modifiers/damage-resistance :piercing "while raging")
       (modifiers/damage-resistance :slashing "while raging")
       (modifiers/saving-throw-advantage ["Rage"] [:str])])))
```

#### 2. Case Addition (line 3307)
```clojure
;; Resource pool features
:rage (rage-modifiers v option-key)
```

#### 3. Unit Tests (test/cljc/orcpub/dnd/e5/options_test.clj)
- ✅ Test with uses > 0 creates 5 modifiers
- ✅ Test with uses = 0 returns nil
- ✅ Test with nil config returns nil
- ✅ Test with missing uses returns nil
- ✅ Test default damage value
- ✅ Test integration with make-feat-modifiers

### Key Design Decisions

1. **Duration**: Fixed at 1 minute (standard D&D 5e Rage duration) using `units5e/minutes-1`
2. **Resistance Qualifier**: Used "while raging" text to indicate conditional nature
3. **Default Damage**: Defaults to +2 if not specified
4. **Disabled State**: `uses: 0` or missing `:uses` = feature disabled

### Usage Example

Homebrew class with Rage:
```clojure
{:name "My Custom Barbarian"
 :key :custom-barb
 :hit-die 12
 :props {:rage {:uses 2 :damage 2}}}  ; Level 1 Barbarian
```

This generates:
- Bonus action "Rage" usable 2 times per long rest
- Duration: 1 minute
- Summary includes +2 damage, advantage on STR, resistances
- 3 damage resistances (bludgeoning, piercing, slashing) with "while raging" qualifier
- Advantage on STR saves with "Rage" type indicator

### ✅ Completed: UI and Event Handlers

**File: `src/cljs/orcpub/dnd/e5/views.cljs`**

#### UI Component (lines 5429-5452)
```clojure
(defn class-resource-pools [class]
  "UI for configuring class resource pool features like Rage, Ki, etc."
  [:div.m-b-30
   [:div.f-s-24.f-w-b.m-b-10 "Resource Pools"]
   [:div.f-s-14.m-b-10.i "Configure resource-based class features (Rage, Ki, Sorcery Points, etc.)"]

   ;; Rage Configuration
   [:div.m-b-20
    [:div.f-s-18.f-w-b.m-b-10 "Rage (Barbarian)"]
    [:div.flex.flex-wrap
     [:div.m-r-20.m-b-10
      [:label.f-w-b.m-b-5 "Uses per Long Rest"]
      [comps/number-input
       (get-in class [:props :rage :uses] 0)
       #(dispatch [::classes/set-class-prop-value :rage :uses %])
       {:min 0 :max 20}]]
     [:div.m-r-20.m-b-10
      [:label.f-w-b.m-b-5 "Damage Bonus"]
      [comps/number-input
       (get-in class [:props :rage :damage] 2)
       #(dispatch [::classes/set-class-prop-value :rage :damage %])
       {:min 1 :max 10}]]
     [:div.m-b-10.f-s-12.i
      "Set Uses to 0 to disable this feature."]]]])
```

#### Integration (line 5686-5687)
Added `[class-resource-pools class]` to class-builder after skill expertise section.

**File: `src/cljs/orcpub/dnd/e5/events.cljs`**

#### Event Handler (lines 2626-2630)
```clojure
(reg-event-db
 ::class5e/set-class-prop-value
 class-interceptors
 (fn [class [_ prop-key sub-key value]]
   (assoc-in class [:props prop-key sub-key] value)))
```

### How It Works

1. **User opens class builder** → sees "Resource Pools" section
2. **User sets "Uses per Long Rest" to 3** → dispatches `[::classes/set-class-prop-value :rage :uses 3]`
3. **Event handler updates** → `(assoc-in class [:props :rage :uses] 3)`
4. **User sets "Damage Bonus" to 2** → dispatches `[::classes/set-class-prop-value :rage :damage 2]`
5. **Resulting class data:**
   ```clojure
   {:name "My Barbarian"
    :hit-die 12
    :props {:rage {:uses 3 :damage 2}}}
   ```
6. **When class is saved** → `plugin-modifiers` calls `make-feat-modifiers`
7. **Conversion happens** → `rage-modifiers` creates 5 modifiers
8. **Character builder sees** → Rage bonus action with frequency, resistances, save advantages

## Next Steps

1. ✅ Design `:props` format
2. ✅ Prototype Rage end-to-end (COMPLETED)
3. ✅ Add UI components for rage configuration (COMPLETED)
4. ✅ Add event handlers for rage prop updates (COMPLETED)
5. ⏭️ Manual integration test with character builder UI
6. ⏭️ Add Ki, Sneak Attack, Action Surge features
7. ⏭️ Document for users
8. ⏭️ Implement level-based scaling (different rage values per level)
