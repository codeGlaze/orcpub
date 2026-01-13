# Clarification: Existing vs New, and Semantic Function Preservation

**Critical Corrections** to previous documentation
**Date**: 2026-01-12

---

## Part 1: What Already Exists vs What's New

### The Confusion: `ranged-attack-bonus`

**What EXISTS**:
```clojure
;; In modifiers.cljc line 542
(defn ranged-attack-bonus [bonus]
  (mods/cum-sum-mod ?ranged-attack-bonus bonus))

;; Used DIRECTLY in fighting styles (options.cljc line 1691)
(def fighting-style-options
  [(t/option-cfg
    {:name "Archery"
     :modifiers [(modifiers/ranged-attack-bonus 2)  ;; ← Direct modifier call
                 (modifiers/trait-cfg {...})]})])
```

**The modifier function EXISTS and is used!** ✅

**What DOESN'T exist**:
```clojure
;; This is what I was proposing as "NEW"
{:name "Archery"
 :props {:ranged-attack-bonus 2}}  ;; ← Props system, NOT direct modifier

;; Which would convert via make-feat-modifiers:
(defn make-feat-modifiers [k v option-key]
  (case k
    :ranged-attack-bonus [(modifiers/ranged-attack-bonus v)]  ;; ← NEW case
    ...))
```

**Using it as a PROP would be NEW!** The modifier exists, but the PROPS SYSTEM doesn't support it yet.

### Current State: Two Different Patterns

**Fighting Styles (current)**:
```clojure
;; HARDCODED - direct modifier calls
{:modifiers [(modifiers/ranged-attack-bonus 2)
             (modifiers/armored-ac-bonus 1)]}
```

**Feats (current)**:
```clojure
;; PROPS-BASED - converted via plugin-modifiers
{:props {:initiative 5
         :skill-prof {:perception true}}}

;; Converted by make-feat-modifiers
```

**Fighting styles DON'T use the props system currently!**

### What I Was Proposing

Enable fighting styles to use the props system:

```clojure
;; Instead of hardcoding modifiers:
{:modifiers [(modifiers/ranged-attack-bonus 2)]}

;; Use props (like feats do):
{:props {:ranged-attack-bonus 2}}

;; Converted the same way feats are
```

**This is what I meant by "NEW"** - new USAGE pattern, not new modifier.

---

## Part 2: Semantic Functions MUST Be Preserved

### Your Critical Question

> "I see the documentation for a unified 'feat' - is this intended to get rid of semantically labeled functions/methods/macros that expect specific props?"

**ANSWER: NO!** Semantic functions are critical and must be preserved!

> "Isn't there a case for having a difference between invoking (pseudocode): feat(one, two, three) vs fighting-style(one, seven, ten)?"

**ANSWER: YES!** Absolutely there is!

### The Problem with My Documentation

I showed this:
```clojure
;; Shared internal function
(defn ability->option-cfg [ability-data package-type context]
  ...)
```

And it looked like I was proposing to replace:
```clojure
(feat-option-from-cfg language-map spells-map spell-lists weapons cfg)
(fighting-style-option-from-cfg cfg)
```

With just:
```clojure
(ability-option-from-cfg cfg)  ;; ← WRONG! Loses semantics!
```

**That would be BAD!** Here's why:

---

## Part 3: What I'm ACTUALLY Proposing

### Keep Semantic Functions, Share Internal Logic

**PUBLIC API (semantic, different signatures)**:

```clojure
;; Feat - needs spell/language context
(defn feat-option-from-cfg
  [language-map spells-map spell-lists weapons {:keys [name key props] :as cfg}]
  ;; Semantic: This is a FEAT
  ;; Signature: Needs language-map, spells-map, etc.
  (let [modifiers (plugin-modifiers props key)  ;; ← Shared logic
        selections (feat-selections language-map spells-map spell-lists weapons props)]
    (t/option-cfg
     {:name name
      :key key
      :modifiers modifiers
      :selections selections})))

;; Fighting Style - needs plugin context, different params
(defn fighting-style-option-from-cfg
  [{:keys [name key props class-restrictions] :as cfg}]
  ;; Semantic: This is a FIGHTING STYLE
  ;; Signature: Different params than feat!
  (let [modifiers (plugin-modifiers props key)  ;; ← SAME shared logic
        prereqs (build-class-restrictions class-restrictions)]
    (t/option-cfg
     {:name name
      :key key
      :modifiers modifiers
      :prereqs prereqs})))

;; Class Feature - needs class/level context
(defn class-feature-option-from-cfg
  [{:keys [name key props class level] :as cfg}]
  ;; Semantic: This is a CLASS FEATURE
  ;; Signature: Different params again!
  (let [modifiers (plugin-modifiers props key)  ;; ← SAME shared logic
        level-data (build-level-data class level)]
    (t/option-cfg
     {:name name
      :key key
      :modifiers modifiers
      :level level})))
```

**Key points**:
- ✅ Different function names (semantic!)
- ✅ Different signatures (different params!)
- ✅ Different logic where needed (selections, prereqs, etc.)
- ✅ Share internal conversion (`plugin-modifiers`) where it makes sense

### Why Signatures Should Differ

**Feat needs**:
- `language-map` - for language selection feats (Linguist, etc.)
- `spells-map` - for spell-granting feats (Magic Initiate, Ritual Caster)
- `spell-lists` - for spell selection
- `weapons` - for weapon proficiency feats

```clojure
(feat-option-from-cfg language-map spells-map spell-lists weapons cfg)
```

**Fighting Style needs**:
- `plugins` - for merging plugin fighting styles
- Maybe `class-options` - for class-specific restrictions
- Does NOT need language-map, spells-map (usually)

```clojure
(fighting-style-option-from-cfg plugins cfg)
```

**Class Feature needs**:
- `class-key` - which class this belongs to
- `level` - which level it's granted at
- Different context entirely!

```clojure
(class-feature-option-from-cfg class-key level cfg)
```

**Different contexts require different signatures!**

---

## Part 4: What CAN Be Shared

### Shared: The Props → Modifiers Conversion

```clojure
(defn plugin-modifiers [props option-key]
  "Shared by ALL ability types - converts props to modifiers"
  (reduce
   (fn [mods [k v]]
     (let [feat-mods (make-feat-modifiers k v option-key)]
       (if feat-mods
         (concat mods feat-mods)
         mods)))
   []
   props))

(defn make-feat-modifiers [k v option-key]
  "Shared case statement for prop → modifier conversion"
  (case k
    ;; EXISTING props (used by feats)
    :initiative [(modifiers/initiative v)]
    :skill-prof (map skill-proficiency v)
    :damage-resistance (map damage-resistance v)

    ;; NEW props (would enable fighting styles to use props)
    :ranged-attack-bonus [(modifiers/ranged-attack-bonus v)]
    :melee-attack-bonus [(modifiers/melee-attack-bonus v)]
    :critical-range [(modifiers/critical v)]

    nil))
```

**This function is ALREADY shared** between:
- Feats (currently)
- Races (currently)
- Could be used by fighting styles (NEW)

### What's NOT Shared (And Shouldn't Be)

**Selection logic** - different for each type:

```clojure
;; Feat selections - spell choices, language choices, etc.
(defn feat-selections [language-map spells-map spell-lists weapons props]
  ...)

;; Fighting style selections - usually none, but could have spell selections (Blessed Warrior)
(defn fighting-style-selections [spell-lists props]
  ...)

;; Class feature selections - varies wildly
(defn class-feature-selections [class level props]
  ...)
```

**Prerequisite logic** - different for each type:

```clojure
;; Feat prerequisites - ability scores, armor prof, spellcasting
(defn feat-prereqs [prereqs path-prereqs]
  ...)

;; Fighting style prereqs - class restrictions
(defn fighting-style-prereqs [class-restrictions]
  ...)

;; Class feature prereqs - none (automatic at level)
```

---

## Part 5: The Correct Architecture (Revised)

### Layer 1: Shared Utilities

```clojure
;; Shared prop → modifier conversion
(defn plugin-modifiers [props option-key] ...)

;; Shared UI widgets
[modifier-input-widget]
[spell-selection-widget]
[resource-configuration-widget]
```

### Layer 2: Semantic Type Functions

```clojure
;; FEAT - semantic function, specific signature
(defn feat-option-from-cfg [language-map spells-map spell-lists weapons cfg]
  (let [modifiers (plugin-modifiers (:props cfg) (:key cfg))  ;; ← Shared
        selections (feat-selections ...)]                      ;; ← Unique
    (t/option-cfg {...})))

;; FIGHTING STYLE - semantic function, different signature
(defn fighting-style-option-from-cfg [plugins cfg]
  (let [modifiers (plugin-modifiers (:props cfg) (:key cfg))  ;; ← Shared
        prereqs (fighting-style-prereqs ...)]                  ;; ← Unique
    (t/option-cfg {...})))

;; CLASS FEATURE - semantic function, different signature
(defn class-feature-option-from-cfg [class level cfg]
  (let [modifiers (plugin-modifiers (:props cfg) (:key cfg))  ;; ← Shared
        level-data (class-feature-level-data ...)]             ;; ← Unique
    (t/option-cfg {...})))
```

**Each maintains**:
- Its own semantic name
- Its own signature
- Its own unique logic
- But shares conversion where appropriate

### Layer 3: Separate Builders

```clojure
;; Separate routes
/feat-builder
/fighting-style-builder
/class-feature-builder

;; Each with unique UI
(defn feat-builder [] ...)
(defn fighting-style-builder [] ...)
(defn class-feature-builder [] ...)

;; But using shared widgets
[shared/modifier-input]
[shared/spell-selection]
```

---

## Part 6: What I Got Wrong

### Mistake 1: Unclear About Existing vs New

I said `:ranged-attack-bonus` was "NEW" without clarifying:
- The MODIFIER exists (modifiers/ranged-attack-bonus)
- Using it as a PROP would be new
- Caused confusion!

**Correction**:
```clojure
;; Existing (direct use in fighting styles)
:modifiers [(modifiers/ranged-attack-bonus 2)]

;; Would be new (props-based use)
:props {:ranged-attack-bonus 2}

;; Enabled by adding to make-feat-modifiers
```

### Mistake 2: Appeared to Propose Unified Function

Showing `ability->option-cfg` as if it would replace semantic functions.

**What I meant**:
```clojure
;; Internal helper (if useful)
(defn- convert-props-to-modifiers [props key]
  (plugin-modifiers props key))

;; Public semantic functions (different signatures!)
(defn feat-option-from-cfg [lang spell-lists weapons cfg]
  (let [mods (convert-props-to-modifiers (:props cfg) (:key cfg))]
    ...))

(defn fighting-style-option-from-cfg [plugins cfg]
  (let [mods (convert-props-to-modifiers (:props cfg) (:key cfg))]
    ...))
```

**NOT**:
```clojure
;; This would be BAD - loses semantics!
(defn ability-option-from-cfg [context cfg]
  ...)
```

### Mistake 3: Didn't Emphasize Signature Differences

Should have been clearer that different types need different params:

- **Feat**: language-map, spells-map, spell-lists, weapons
- **Fighting Style**: plugins, maybe class-options
- **Class Feature**: class, level, maybe subclass

**These differences are IMPORTANT and should be preserved!**

---

## Conclusion

### What I'm Actually Proposing

**Do this**:
- ✅ Extend `plugin-modifiers` to handle fighting-style-specific props
- ✅ Keep `feat-option-from-cfg` with its current signature
- ✅ Create `fighting-style-option-from-cfg` with appropriate signature
- ✅ Share the prop conversion logic (`plugin-modifiers`)
- ✅ Share UI widgets where it makes sense
- ✅ Keep semantic function names and signatures

**Don't do this**:
- ❌ Create generic `ability-option-from-cfg` that replaces semantic functions
- ❌ Force same signature on different types
- ❌ Lose semantic information
- ❌ Break existing code

### The Value of Semantic Functions

```clojure
;; Good - semantics clear, signature appropriate
(feat-option-from-cfg language-map spells-map spell-lists weapons cfg)
(fighting-style-option-from-cfg plugins cfg)

;; Bad - lost semantics, unclear what it does
(ability-option-from-cfg "feat" context1 cfg)
(ability-option-from-cfg "fighting-style" context2 cfg)
```

**Semantic function names matter!**
**Different signatures matter!**
**We should absolutely preserve both!**

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent
**Status**: Correction/Clarification
