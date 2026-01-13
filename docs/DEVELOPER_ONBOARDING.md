# OrcPub Developer Onboarding

**Welcome to OrcPub development!** This document explains key architectural concepts you'll need to understand.

---

## Core Architectural Concepts

### Props vs Modifiers: The Critical Distinction

**TL;DR**: Props are **serializable data**, Modifiers are **executable code**. Props convert to Modifiers at runtime.

#### Why Both Exist

**The Problem**: User-created content (orcbrew files) needs to be saved as data (.edn files)
```clojure
;; ❌ This CANNOT work in an orcbrew file (it's CODE)
{:modifiers [(modifiers/initiative 5)]}  ;; Function call - not data!

;; ✅ This CAN work (it's DATA)
{:props {:initiative 5}}  ;; Just a map - serializable!
```

**The Flow**:
```
Orcbrew file (.edn)
    ↓
Props (data: {:initiative 5})
    ↓ plugin-modifiers function converts
Modifiers (code: [(modifiers/initiative 5)])
    ↓
Applied to character
    ↓
Character stats calculated
```

#### Pattern 1: SOURCE Content (Hardcoded)

**Example**: Fighting styles in `options.cljc` (line 1688)
```clojure
(def fighting-style-options
  [(t/option-cfg
    {:name "Archery"
     :modifiers [(modifiers/ranged-attack-bonus 2)]})  ;; Direct modifier calls
```

**Why direct modifiers**:
- Defined in ClojureScript source files
- Can use function calls directly
- Compiled into application
- No serialization needed

#### Pattern 2: Plugin Content (User-Created)

**Example**: Feats in orcbrew files
```clojure
;; In orcbrew file (pure data)
{::e5/feats
 [{:name "Alert"
   :props {:initiative 5              ;; Props (data)
           :skill-prof {:perception true}}}]}
```

**Why props**:
- Must be serializable to .edn format
- Can't contain function calls
- Converted at runtime via `plugin-modifiers`
- Safe to import/export

#### The Converter: `plugin-modifiers`

**Location**: `src/cljc/orcpub/dnd/e5/options.cljc` line 3288

```clojure
(defn plugin-modifiers [props option-key]
  "Converts props (data) → modifiers (code)"
  (reduce
   (fn [mods [k v]]
     (let [feat-mods (make-feat-modifiers k v option-key)]
       (if feat-mods
         (concat mods feat-mods)
         mods)))
   []
   props))

(defn make-feat-modifiers [k v option-key]
  "Maps prop keys to modifier functions"
  (case k
    :initiative [(modifiers/initiative v)]
    :skill-prof (map #(modifiers/skill-proficiency %) v)
    :damage-resistance (map #(modifiers/damage-resistance %) v)
    ;; ... 30+ more prop types
    nil))
```

**How it works**:
1. Takes props map: `{:initiative 5}`
2. Iterates each prop: `[:initiative 5]`
3. Looks up conversion in `make-feat-modifiers`
4. Returns modifier function: `[(modifiers/initiative 5)]`
5. Modifier gets applied to character

#### Historical Context: The "Shim"

**Why fighting styles don't use props** (yet):
1. **Original system** (pre-plugins): Everything hardcoded → direct modifiers
2. **Plugin system added**: Need serialization → props invented
3. **Feats migrated** to props (for plugin support)
4. **Fighting styles never migrated** → still use direct modifiers (holdover!)

**This is the "odd shim"**: Fighting styles and feats do the same thing differently.

**The future**: Fighting styles should use props for consistency and plugin support.

---

## Character Save Format

**Critical understanding**: Characters save **keys/references**, not actual modifiers!

### Example: Fighting Style Selection

**Character save** (localStorage/export):
```clojure
{:classes {:fighter {:level 5
                     :fighting-style :archery}}}  ;; Just the keyword!
```

**NOT saved**:
```clojure
{:classes {:fighter {:modifiers [...]}}}  ;; ❌ Modifiers not saved
```

**At runtime**:
1. Load character: sees `:archery` keyword
2. Look up `:archery` in `fighting-style-options` list
3. Get modifiers from definition
4. Apply to character

**Why this matters**: The **internal representation** (props vs direct modifiers) doesn't affect the save format!

### Converting SOURCE Content to Props is Safe

**Current** (direct modifiers):
```clojure
(def fighting-style-options
  [{:name "Archery"
    :key :archery
    :modifiers [(modifiers/ranged-attack-bonus 2)]}])
```

**Converted** (props):
```clojure
(def fighting-style-data
  [{:name "Archery"
    :key :archery
    :props {:ranged-attack-bonus 2}}])

(def fighting-style-options
  (map fighting-style-option-from-cfg fighting-style-data))
```

**Character saves**: Still just `{:fighting-style :archery}` ✅
**Runtime lookup**: Still works (finds `:archery` key) ✅
**Modifiers applied**: Same result (props → modifiers) ✅

**Nothing breaks!** Character saves are decoupled from internal representation.

---

## Plugin System Architecture

### How Plugins Work

**Plugin file** (user creates):
```clojure
;; my-homebrew.edn
{::e5/plugin
 {::e5/name "My Homebrew"
  ::e5/feats
  [{:name "Custom Feat"
    :key :custom-feat
    :props {:initiative 3
            :skill-prof {:stealth true}}}]}}
```

**Import process**:
1. User imports .edn file
2. Validated against `::e5/plugin` spec
3. Stored in browser localStorage
4. Merged with SOURCE content at runtime

**At character creation**:
```clojure
(defn all-feats [plugins]
  (let [source-feats (feat-options spell-lists spells-map)  ;; Hardcoded
        plugin-feats (mapcat ::e5/feats (vals plugins))]    ;; From imports
    (concat source-feats
            (map #(feat-option-from-cfg ... %) plugin-feats))))  ;; Convert props
```

**User sees**: All feats (SOURCE + plugins) in one list!

### Why Props Enable Plugins

**Props are data** → can be in .edn files → can be imported/exported → plugins work!

**Direct modifiers are code** → can't be in .edn files → can't be imported → no plugins!

---

## Adding New Prop Types

**When you need a new capability** (e.g., blindsight for fighting styles):

### Step 1: Check if Modifier Exists

```clojure
;; In src/cljc/orcpub/dnd/e5/modifiers.cljc
(defn blindsight [value]
  (mods/modifier ?blindsight value "Blindsight" (str value " feet")))
```

If it doesn't exist, create it first!

### Step 2: Add to `make-feat-modifiers`

```clojure
;; In src/cljc/orcpub/dnd/e5/options.cljc
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; ... existing props ...

    :blindsight [(modifiers/blindsight v)]  ;; ← Add new case

    nil))
```

### Step 3: Add to Spec (Optional but Recommended)

```clojure
;; In src/cljc/orcpub/dnd/e5/[type].cljc
(spec/def ::blindsight pos-int?)

(spec/def ::props
  (spec/keys :opt-un [::initiative
                      ::skill-prof
                      ::blindsight  ;; ← Add to spec
                      ;; ...
                      ]))
```

### Step 4: Use It!

```clojure
;; In orcbrew file
{:name "Blind Fighting"
 :props {:blindsight 10}}  ;; Now supported!
```

**That's it!** The prop→modifier conversion happens automatically.

---

## Common Patterns

### Pattern: Conditional Modifiers

**Some modifiers need logic**:
```clojure
;; Dueling: +2 damage only with one-handed melee weapon, no off-hand
(mods/vec-mod ?damage-bonus-fns
              (fn [weapon _]
                (if (or (weapon ::weapons/two-handed?)
                        (weapon ::weapons/ranged?))
                  0
                  2))
              nil nil
              [(complex-condition-checking-weapons)])
```

**These are harder to represent as props** - may need custom handling.

### Pattern: Selection-Based Props

**Some props create selections**:
```clojure
;; Magic Initiate - choose spell class
:magic-novice [(magic-initiate-selection spells-map spell-lists)]
```

**Handled by `make-feat-selections`** (similar to `make-feat-modifiers`).

### Pattern: Map-Based Props

**Collection of related booleans**:
```clojure
;; Multiple skills
:skill-prof {:perception true
             :stealth true
             :athletics true}

;; Converted to multiple modifiers
(collect-map-modifiers v #(modifiers/skill-proficiency %))
```

---

## Backward Compatibility Rules

### Rule 1: Never Change Existing Props

```clojure
// ❌ BAD - breaks existing orcbrews
:initiative → :initiative-bonus  // Renamed!

// ✅ GOOD - both work
:initiative [(modifiers/initiative v)]           // Old (still works)
:initiative-bonus [(modifiers/initiative v)]     // New (alias)
```

### Rule 2: All Changes Must Be Additive

```clojure
// ✅ GOOD - adding new prop
(defn make-feat-modifiers [k v option-key]
  (case k
    :initiative [...]
    :blindsight [...]  // NEW - old props still work
    nil))
```

### Rule 3: Character Saves Are Sacred

**Never change**:
- Character save format
- How selections are stored
- Key/reference values (`:archery`, `:alert`, etc.)

**Can change** (internal only):
- How modifiers are generated
- Conversion logic
- Internal data structures

---

## Testing Your Changes

### Test 1: Backward Compatibility

```clojure
(deftest backward-compat-test
  (testing "Old orcbrew files still load"
    (let [old-plugin {::e5/feats [{:name "Alert"
                                   :props {:initiative 5}}]}]
      (is (spec/valid? ::e5/plugin old-plugin)))))
```

### Test 2: Conversion

```clojure
(deftest prop-conversion-test
  (testing "Props convert to modifiers correctly"
    (let [props {:initiative 5}
          mods (plugin-modifiers props :test)]
      (is (some #(= :initiative (:key %)) mods)))))
```

### Test 3: Character Application

```clojure
(deftest character-application-test
  (testing "Modifiers apply to character"
    (let [char (create-character-with-feat :alert)]
      (is (= 5 (get-initiative-bonus char))))))
```

---

## FAQ

**Q: Why do fighting styles use direct modifiers instead of props?**
A: Historical - they predate the plugin system. Should be migrated eventually.

**Q: Can I convert SOURCE content to props?**
A: Yes! Character saves use keys, not internal representation. Safe to convert.

**Q: What's the difference between `make-feat-modifiers` and `make-feat-selections`?**
A: Modifiers are applied to character stats. Selections let user choose sub-options.

**Q: Why is it called `make-feat-modifiers` if other things use it?**
A: Historical naming. Should probably be `make-plugin-modifiers` or `convert-props-to-modifiers`.

**Q: Can props handle complex conditional logic?**
A: Sometimes. Simple conditions (if value > 0) work. Complex logic (check weapon properties) may need custom modifiers.

**Q: How do I debug prop conversion?**
A: Add logging to `plugin-modifiers` or check the generated `option-cfg` structure.

---

## Key Files Reference

| File | Purpose | Key Functions |
|------|---------|---------------|
| `options.cljc` | Feat/fighting style definitions | `plugin-modifiers`, `make-feat-modifiers` |
| `modifiers.cljc` | Modifier functions | `initiative`, `skill-proficiency`, etc. |
| `template.cljc` | Core templates | `option-cfg`, `selection-cfg` |
| `db_5e.cljc` | Plugin specs | `::plugin`, `::feats`, etc. |
| `events.cljs` | Import/save handlers | Plugin import, character save |

---

## Next Steps

1. Read the codebase overview in `CODEBASE.md`
2. Explore existing feats in `options.cljc` (line 1181+)
3. Look at plugin examples in test files
4. Try creating a simple homebrew feat
5. Ask questions in team chat!

**Welcome aboard!** 🚀
