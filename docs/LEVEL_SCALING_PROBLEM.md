# CRITICAL: Level Scaling Problem with Current Implementation

## The Problem

The current Rage prototype implementation has a **fundamental architectural flaw** regarding level-based scaling.

### How Built-In Barbarian Handles Level Scaling

**File: `src/cljc/orcpub/dnd/e5/classes.cljc:77-88`**

```clojure
(mod5e/bonus-action
 {:name "Rage"
  :page 48
  :duration units5e/minutes-1
  :frequency (units5e/long-rests (condp <= (?class-level :barbarian)
                              17 6    ; Level 17+: 6 uses
                              12 5    ; Level 12-16: 5 uses
                              6 4     ; Level 6-11: 4 uses
                              3 3     ; Level 3-5: 3 uses
                              2))     ; Level 1-2: 2 uses
  :summary (str "Advantage on Strength checks and saves; melee damage bonus "
                (common/bonus-str (condp <= (?class-level :barbarian)
                                    16 4    ; Level 16+: +4 damage
                                    9 3     ; Level 9-15: +3 damage
                                    2))     ; Level 1-8: +2 damage
                "; resistance to bludgeoning, piercing, and slashing damage")})
```

**Key Points:**
- Uses `(?class-level :barbarian)` for **dynamic runtime evaluation**
- Modifier is defined ONCE but values change based on character's current level
- Rage uses scale: 2 → 3 → 4 → 5 → 6 (at levels 1, 3, 6, 12, 17)
- Rage damage scales: +2 → +3 → +4 (at levels 1, 9, 16)

### How My Current Implementation Works

**File: `src/cljc/orcpub/dnd/e5/options.cljc:3230-3248`**

```clojure
(defn rage-modifiers [rage-cfg option-key]
  (when (and rage-cfg (pos? (or (:uses rage-cfg) 0)))
    (let [{:keys [uses damage]} rage-cfg
          damage-val (or damage 2)]
      [(modifiers/bonus-action
        {:name "Rage"
         :page 48
         :duration units5e/minutes-1
         :frequency (units5e/long-rests uses)  ; STATIC VALUE!
         :summary (str "Advantage on STR checks and saves; "
                       (common/bonus-str damage-val)  ; STATIC VALUE!
                       " melee damage; ...")})])))
```

**Problems:**
- ❌ `uses` is a **static value** (e.g., always 2)
- ❌ `damage` is a **static value** (e.g., always +2)
- ❌ No way to make these values scale with character level
- ❌ User would need to manually set different props per level somehow

### How Homebrew Classes Handle Levels

**File: `src/cljs/orcpub/dnd/e5/db.cljs:112-115`**

```clojure
(def default-class {:hit-die 6
                    :ability-increase-levels [4 8 12 16 19]
                    :traits []
                    :level-modifiers []})  ; Array of {level, type, value}
```

**File: `src/cljs/orcpub/dnd/e5/spell_subs.cljs:344-362`**

```clojure
(defn make-levels [spell-lists spells-map selection-map {:keys [key class spellcasting] :as option}]
  (let [modifiers (:level-modifiers option)        ; [{:level 5 :type :num-attacks :value 2}]
        selections (:level-selections option)
        by-level (group-by :level modifiers)]      ; {5 [{:type :num-attacks :value 2}]}
    (reduce-kv
     (fn [levels level level-modifiers]
       (update-in levels
                  [level :modifiers]
                  (fn [existing-mods]
                    (vec (concat existing-mods
                                (map modifier->fn level-modifiers))))))
     {}
     by-level)))
```

**How it works:**
1. User defines modifiers with explicit levels: `{:level 5 :type :num-attacks :value 2}`
2. System groups by level and creates modifier functions
3. Each level gets independent modifiers added

**Available modifier types** (from `views.cljs:5160-5225`):
- `:weapon-prof` - Weapon proficiency
- `:num-attacks` - Number of attacks (simple integer)
- `:damage-resistance` - Damage resistance
- `:damage-immunity` - Damage immunity
- `:saving-throw-advantage` - Save advantage
- `:skill-prof` - Skill proficiency
- `:armor-prof` - Armor proficiency
- `:tool-prof` - Tool proficiency
- `:flying-speed` - Flying speed
- `:swimming-speed` - Swimming speed
- `:spell` - Individual spell

**Limitations:**
- ❌ Only simple, single-value modifiers
- ❌ No support for complex resource pools (Rage, Ki, etc.)
- ❌ Can't define "Rage with 3 uses and +2 damage"
- ❌ Would need multiple separate modifiers per level

## Why This Is A Problem

### Example: Level 1 Barbarian

**Current Implementation:**
```clojure
{:name "My Barbarian"
 :hit-die 12
 :props {:rage {:uses 2 :damage 2}}}  ; ← Works at level 1
```

Generates: Rage with 2 uses, +2 damage

### Example: Level 3 Barbarian

**What SHOULD happen:**
- Rage uses: 2 → **3** (increases at level 3)
- Rage damage: +2 (stays same until level 9)

**What ACTUALLY happens with current implementation:**
- Rage uses: **2** (doesn't change! Still static!)
- Rage damage: **+2** (doesn't change!)

**Why:**
The `:props` generate modifiers ONCE at class definition, not per character level.

### Example: Level 9 Barbarian

**What SHOULD happen:**
- Rage uses: **4** (increased at level 6)
- Rage damage: **+3** (increases at level 9)

**What ACTUALLY happens:**
- Rage uses: **2** (still stuck at level 1 value!)
- Rage damage: **+2** (still stuck at level 1 value!)

## Possible Solutions

### Solution A: Multiple `:props` Entries Per Level (UGLY)

```clojure
{:level-modifiers [{:level 1 :type :rage-pool-l1}    ; Custom modifier type
                   {:level 3 :type :rage-pool-l3}
                   {:level 6 :type :rage-pool-l6}
                   {:level 9 :type :rage-pool-l9}
                   ...]}
```

**Problems:**
- ❌ Extremely verbose and error-prone
- ❌ Need to add custom modifier types for every level breakpoint
- ❌ UI nightmare (how to configure all these?)
- ❌ Doesn't leverage existing `:props` system

### Solution B: Store Level Breakpoints in `:props` (BETTER)

```clojure
{:props {:rage {:levels {1 {:uses 2 :damage 2}
                         3 {:uses 3 :damage 2}
                         6 {:uses 4 :damage 2}
                         9 {:uses 4 :damage 3}
                         12 {:uses 5 :damage 3}
                         16 {:uses 5 :damage 4}
                         17 {:uses 6 :damage 4}}}}}
```

Then modify `rage-modifiers` to:
1. Accept character level as parameter (HOW??)
2. Find appropriate breakpoint
3. Return modifiers with correct values

**Problems:**
- ❌ `rage-modifiers` is called during **class definition**, not character building
- ❌ No access to character level at this point
- ❌ Would require fundamental changes to `plugin-modifiers` pipeline

### Solution C: Dynamic Evaluation Like Built-In (IDEAL BUT COMPLEX)

Modify the modifier system to support **lazy evaluation** of values:

```clojure
(defn rage-modifiers [rage-cfg option-key]
  (when rage-cfg
    [(modifiers/bonus-action
      {:name "Rage"
       :frequency (fn [?class-level]  ; ← LAZY FUNCTION
                    (units5e/long-rests
                      (get-in rage-cfg [:breakpoints :uses ?class-level])))
       :summary (fn [?class-level]    ; ← LAZY FUNCTION
                  (str "... +"
                       (get-in rage-cfg [:breakpoints :damage ?class-level])
                       " melee damage ..."))})])))
```

**Problems:**
- ❌ Requires changing modifier architecture
- ❌ Built-in modifiers don't support lazy values
- ❌ Would break existing modifier system
- ❌ MAJOR refactoring needed

### Solution D: Use `:levels` Map Like Built-In Classes (PRAGMATIC)

Instead of putting Rage in `:props`, put it in the class's **base modifiers** but make it configurable via UI.

**Data structure:**
```clojure
{:name "My Barbarian"
 :hit-die 12
 :modifiers [(mod5e/bonus-action
              {:name "Rage"
               :frequency (units5e/long-rests (condp <= ?class-level
                                                17 (:rage-uses-17 config)
                                                12 (:rage-uses-12 config)
                                                ...))
               :summary (str "... " (condp <= ?class-level
                                      16 (:rage-damage-16 config)
                                      9 (:rage-damage-9 config)
                                      ...))})]
 :config {:rage-uses-1 2    ; UI stores these
          :rage-uses-3 3
          :rage-damage-1 2
          :rage-damage-9 3}}
```

**Problems:**
- ❌ Can't modify `:modifiers` via UI (it's code, not data)
- ❌ Homebrew classes don't support custom code in modifiers
- ❌ Would need to generate ClojureScript code (eval() equivalent)

### Solution E: Per-Level Props (CURRENT WORKAROUND)

Accept that each level needs separate configuration:

**UI Approach:**
```
Rage Configuration:
  Level 1-2:  [2] uses, [+2] damage
  Level 3-5:  [3] uses, [+2] damage
  Level 6-11: [4] uses, [+2] damage
  Level 9-15: [4] uses, [+3] damage (damage override)
  Level 12-16: [5] uses, [+3] damage
  Level 16-19: [5] uses, [+4] damage
  Level 17-20: [6] uses, [+4] damage (uses override)
```

**Data structure:**
```clojure
{:level-modifiers [{:level 1 :type :rage :uses 2 :damage 2}
                   {:level 3 :type :rage :uses 3 :damage 2}
                   {:level 6 :type :rage :uses 4 :damage 2}
                   {:level 9 :type :rage :uses 4 :damage 3}
                   {:level 12 :type :rage :uses 5 :damage 3}
                   {:level 16 :type :rage :uses 5 :damage 4}
                   {:level 17 :type :rage :uses 6 :damage 4}]}
```

**Modified `make-feat-modifiers`:**
- When converting level-modifier with `:type :rage`
- Call `rage-modifiers` with the level-specific config
- Generate modifiers for that specific level

**Pros:**
- ✅ Works with existing architecture
- ✅ No need for dynamic evaluation
- ✅ Can configure via UI
- ✅ Clear and explicit

**Cons:**
- ⚠️ Verbose (7 entries for Rage across 20 levels)
- ⚠️ User must understand level breakpoints
- ⚠️ Repetitive UI (but can be templated)

## Recommendation

**Use Solution E (Per-Level Props)** as the pragmatic path forward:

1. Move Rage from `:props` to `:level-modifiers`
2. Extend `:level-modifiers` to support `:type :rage` with `:uses` and `:damage`
3. Create UI that shows level breakpoints
4. Each level's modifiers are independent and static

**Future enhancement:**
- Add "template" system that auto-fills standard progressions
- User clicks "Standard Barbarian Rage" → auto-fills all 7 level entries
- User can then customize individual levels

## Impact on Current Work

### What Works ✅
- Core `rage-modifiers` function (converts config → modifiers)
- Unit tests (test conversion logic)
- Event handlers (handle UI updates)

### What Needs Fixing ❌
1. **Remove Rage from `:props`** in UI
2. **Add Rage to `:level-modifiers`** system
3. **Create new UI** for configuring Rage per level
4. **Modify conversion logic** to work with level-modifiers
5. **Update documentation** with correct approach

### Estimated Effort
- UI changes: 2-3 hours
- Logic changes: 1-2 hours
- Testing: 1 hour
- **Total: ~4-6 hours of work**

## Next Steps

1. Document this finding (THIS DOCUMENT)
2. Discuss with user about preferred solution
3. If Solution E approved:
   - Redesign UI for level-based configuration
   - Modify conversion pipeline
   - Update tests
   - Integration test with character builder
4. Consider creating "class feature templates" for common progressions

## References

- Built-in Barbarian: `src/cljc/orcpub/dnd/e5/classes.cljc:73-88`
- Homebrew levels: `src/cljs/orcpub/dnd/e5/spell_subs.cljs:344-362`
- Level modifiers UI: `src/cljs/orcpub/dnd/e5/views.cljs:5160-5225`
- Modifier values: `src/cljs/orcpub/dnd/e5/views.cljs:5160-5225`
