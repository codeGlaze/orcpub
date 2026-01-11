# Homebrew Class Format Analysis

**Date:** 2026-01-11
**Question:** Does the homebrew class format have enough "room" to clone built-in classes without losing data?

---

## TL;DR: ⚠️ **PARTIAL SUPPORT** - Some data will be lost

The current homebrew format can store **most** but **not all** built-in class data. Critical gaps:

1. **Starting equipment/weapons** - NOT supported in homebrew
2. **Multiclass prerequisites** - NOT supported in homebrew
3. **Complex function-based modifiers** - Partially supported via `:props`
4. **Inline subclass definitions** - NOT supported (subclasses are separate)

**Impact:** Cloning a built-in class will lose starting equipment, multiclass rules, and potentially some complex features.

---

## Detailed Comparison

### ✅ FULLY SUPPORTED Fields

These can be cloned without any loss:

| Field | Built-in Format | Homebrew Format | UI Support |
|-------|----------------|-----------------|------------|
| Name | `:name "Barbarian"` | `:name "..."` | ✅ Text input |
| Key | `:key :barbarian` | `:key :custom-...` | ✅ Auto-generated |
| Hit Die | `:hit-die 12` | `:hit-die 12` | ✅ Dropdown (6/8/10/12) |
| ASI Levels | `:ability-increase-levels [4 8 12 16 19]` | `:ability-increase-levels [...]` | ✅ Checkboxes |
| Saving Throws | `:profs {:save {::char5e/str true}}` | `:profs {:save {...}}` | ✅ Checkboxes |
| Skill Options | `:profs {:skill-options {:choose 2 :options {...}}}` | `:profs {:skill-options {...}}` | ✅ Skill selector |
| Subclass Level | `:subclass-level 3` | `:subclass-level 3` | ✅ Dropdown (1/2/3) |
| Subclass Title | `:subclass-title "Primal Path"` | `:subclass-title "..."` | ✅ Text input |
| Traits | `:traits [{:name "Rage" :level 2 :summary "..."}]` | `:traits [...]` | ✅ Trait editor |
| Spellcasting | `:spellcasting {...}` | `:spellcasting {...}` | ✅ Full spell config |

### ⚠️ PARTIALLY SUPPORTED Fields

These can be cloned but may lose fidelity:

#### Proficiencies (via `:props`)

**Built-in:**
```clojure
:profs {:armor {:light true :medium true :shields false}
        :weapon {:simple false :martial false}}
```

**Homebrew equivalent:**
```clojure
:props {:armor-prof {:light true :medium true}
        :weapon-prof {:simple true :martial true}}
```

**Status:** ✅ Can be converted via `:props`
**Note:** Armor/weapon proficiencies work through the `:props` → `plugin-modifiers` conversion system

#### Class Modifiers

**Built-in:**
```clojure
:modifiers [(mod/vec-mod ?unarmored-defense :barbarian)
            (mod/cum-sum-mod ?unarmored-ac-bonus (?ability-bonuses ::char5e/con) ...)
            (mod5e/bonus-action {:name "Rage" :duration ... :frequency ...})]
```

**Homebrew equivalent:**
```clojure
:props {:speed 30
        :skill-prof {:athletics true}
        :damage-resistance {:bludgeoning true}}
:level-modifiers [{:level 1 :type :armor-prof :value :light}
                  {:level 5 :type :num-attacks :value 2}]
```

**Status:** ⚠️ PARTIAL - Simple modifiers supported via `:props`, complex function-based modifiers NOT supported

**What `:props` CAN handle:**
- `:initiative`, `:speed`, `:flying-speed`, `:swimming-speed`
- `:language`, `:skill-prof`, `:armor-prof`, `:weapon-prof`
- `:damage-resistance`, `:damage-immunity`, `:saving-throw-advantage`
- `:tool-prof`, `:max-hp-bonus`

**What `:props` CANNOT handle:**
- Complex conditional modifiers (e.g., `?unarmored-defense` with custom functions)
- Dynamic modifiers based on character state
- Modifiers with complex predicates (e.g., "only when not wearing heavy armor")

#### Level-Specific Features

**Built-in:**
```clojure
:levels {5 {:modifiers [(extra-attack-trait 49)
                        (mod5e/num-attacks 2)
                        (mod5e/dependent-trait {...})]
             :selections [...]}
         9 {:modifiers [...]}}
```

**Homebrew equivalent:**
```clojure
:level-modifiers [{:level 5 :type :num-attacks :value 2}
                  {:level 5 :type :weapon-prof :value :longsword}]
:level-selections [{:level 3 :type :skill :num 1}]
```

**Status:** ⚠️ PARTIAL - Simple modifiers supported, complex traits may not convert cleanly

**Supported modifier types** (from `level-modifier` function in `spell_subs.cljs:129-146`):
- `:weapon-prof`, `:num-attacks`, `:damage-resistance`, `:damage-immunity`
- `:saving-throw-advantage`, `:skill-prof`, `:armor-prof`, `:tool-prof`
- `:flying-speed`, `:swimming-speed`, `:spell`

**NOT supported:**
- Custom trait definitions with complex logic
- Conditional features (e.g., "Brutal Critical" with die count based on level)
- Features that reference other features

---

### ❌ NOT SUPPORTED Fields

These WILL BE LOST when cloning:

#### 1. Starting Equipment

**Built-in:**
```clojure
:weapons {:javelin 4}
:equipment {:explorers-pack 1}
```

**Homebrew:** NO equivalent field

**Impact:** Every built-in class has starting equipment. Cloned classes will have NONE.

**Workaround:** Would need to manually document starting equipment in description field or add support for these fields to homebrew schema.

#### 2. Starting Equipment Choices

**Built-in:**
```clojure
:weapon-choices [{:name "Martial Weapon"
                  :options {:greataxe 1 :martial 1}}
                 {:name "Simple Weapon"
                  :options {:handaxe 2 :simple 1}}]
```

**Homebrew:** NO equivalent field

**Impact:** Character creation flow won't offer equipment choices for cloned classes.

#### 3. Multiclass Prerequisites

**Built-in:**
```clojure
:multiclass-prereqs [(opt5e/ability-prereq ::char5e/str 13)]
```

**Homebrew:** NO equivalent field

**Impact:** Cloned classes can be multiclassed into without meeting requirements.

**Workaround:** Would need to add `:multiclass-prereqs` field to homebrew schema.

#### 4. Inline Subclass Definitions

**Built-in:**
```clojure
:subclasses [{:name "Path of the Berserker"
              :key :berserker
              :levels {3 {:modifiers [...]}
                       6 {:modifiers [...]}}}]
```

**Homebrew:** Subclasses are SEPARATE items stored in `::e5/subclasses`

**Impact:** Cloning a class WON'T clone its subclasses. Each subclass must be cloned separately.

**Note:** This is actually consistent with how homebrew works - classes and subclasses are separate entities.

---

## What Happens When You Clone?

### Example: Cloning Barbarian

**Source (built-in Barbarian):**
```clojure
{:name "Barbarian"
 :key :barbarian
 :hit-die 12
 :ability-increase-levels [4 8 12 16 19]
 :profs {:armor {:light true :medium true}
         :weapon {:simple true :martial true}
         :save {::char5e/str true ::char5e/con true}
         :skill-options {:choose 2 :options {...}}}
 :multiclass-prereqs [(ability-prereq ::char5e/str 13)]    ; ❌ LOST
 :weapon-choices [{:name "Martial Weapon" ...}]            ; ❌ LOST
 :weapons {:javelin 4}                                     ; ❌ LOST
 :equipment {:explorers-pack 1}                            ; ❌ LOST
 :modifiers [(mod/vec-mod ?unarmored-defense :barbarian)   ; ⚠️ MAY BE LOST (complex)
             (mod5e/bonus-action {:name "Rage" ...})]      ; ⚠️ MAY BE LOST (complex)
 :levels {5 {:modifiers [(extra-attack-trait 49)          ; ⚠️ MAY BE LOST (complex)
                         (mod5e/num-attacks 2)]}          ; ✅ CAN CONVERT
          9 {:modifiers [...]}
          18 {:modifiers [...]}}
 :traits [{:name "Reckless Attack" ...}                   ; ✅ PRESERVED
          {:name "Danger Sense" ...}]                     ; ✅ PRESERVED
 :subclass-level 3                                         ; ✅ PRESERVED
 :subclass-title "Primal Path"                             ; ✅ PRESERVED
 :subclasses [{:name "Path of the Berserker" ...}]}        ; ❌ NOT CLONED (separate)
```

**Result (cloned homebrew):**
```clojure
{:name "Barbarian (Clone)"
 :key :barbarian-clone-abc123
 :hit-die 12                                               ; ✅ PRESERVED
 :ability-increase-levels [4 8 12 16 19]                  ; ✅ PRESERVED
 :profs {:save {::char5e/str true ::char5e/con true}      ; ✅ PRESERVED
         :skill-options {:choose 2 :options {...}}}       ; ✅ PRESERVED
 :props {:armor-prof {:light true :medium true}           ; ✅ CONVERTED from :profs
         :weapon-prof {:simple true :martial true}}       ; ✅ CONVERTED from :profs
 ;; :multiclass-prereqs MISSING                           ; ❌ LOST
 ;; :weapon-choices MISSING                               ; ❌ LOST
 ;; :weapons MISSING                                      ; ❌ LOST
 ;; :equipment MISSING                                    ; ❌ LOST
 :level-modifiers [{:level 5 :type :num-attacks :value 2}]; ⚠️ PARTIAL (simple only)
 :traits [{:name "Reckless Attack" ...}                   ; ✅ PRESERVED
          {:name "Danger Sense" ...}]                     ; ✅ PRESERVED
 :subclass-level 3                                         ; ✅ PRESERVED
 :subclass-title "Primal Path"}                            ; ✅ PRESERVED
 ;; Subclasses NOT included                               ; ❌ SEPARATE ITEMS
```

---

## Implications for Phase 1 (Basic Cloning)

### Data Loss Analysis

**What users WILL get:**
- ✅ Core stats (hit die, ASIs, saves, skills)
- ✅ Traits with descriptions
- ✅ Subclass configuration (level, title)
- ✅ Spellcasting setup
- ✅ Basic proficiencies (armor, weapons, skills)
- ✅ Simple level features (extra attack, damage resistance)

**What users WON'T get:**
- ❌ Starting equipment and weapon choices
- ❌ Multiclass prerequisites
- ❌ Complex class features (Rage mechanics, Unarmored Defense calculations)
- ❌ Subclass options (must clone separately)

### User Experience Impact

**Best case scenario** (Simple class like Champion Fighter):
- Most features clone successfully
- Minor loss: starting equipment, multiclass prereqs
- User can edit and customize easily

**Worst case scenario** (Complex class like Barbarian):
- Major features like Rage may be lost or simplified
- Unarmored Defense calculations won't work
- User would need to manually recreate complex mechanics
- Significant effort to make functional

### Severity by Class

| Class | Complexity | Clone Quality | Lost Features |
|-------|-----------|---------------|---------------|
| Barbarian | High | ⚠️ POOR | Rage mechanics, Unarmored Defense, equipment |
| Fighter | Low | ✅ GOOD | Mostly equipment/multiclass prereqs |
| Wizard | Medium | ⚠️ FAIR | Arcane Recovery mechanics, spellbook rules |
| Cleric | Medium | ⚠️ FAIR | Channel Divinity mechanics, domain spells |
| Rogue | Medium | ⚠️ FAIR | Sneak Attack scaling, Cunning Action |

---

## Recommendations

### Option 1: Clone with Warnings (Recommended for Phase 1)

**Approach:**
1. Implement cloning for fields that CAN be converted
2. Show clear warnings about what will be lost
3. Add notes to description field listing missing features

**UI Flow:**
```
[Clone Barbarian Button]
  ↓
[Warning Modal]
"Cloning will preserve most features, but these will be lost:
 - Starting equipment (Greataxe, Javelins, Explorer's Pack)
 - Multiclass prerequisite (STR 13)
 - Complex features (Rage damage bonus, Unarmored Defense formula)

 You'll need to recreate these manually after cloning.

 [Cancel] [Clone Anyway]"
  ↓
[Class Builder with cloned data + note in description]
```

**Pros:**
- Honest about limitations
- Still provides value (saves time on simple fields)
- Sets correct expectations

**Cons:**
- User may be disappointed
- Still requires manual work for complex classes

### Option 2: Extend Homebrew Schema (Phase 2 Enhancement)

**Add missing fields to homebrew spec:**

```clojure
(spec/def ::homebrew-class
  (spec/keys :req-un [::name ::key ::option-pack]
             :opt-un [::parent-class          ; NEW (Phase 2)
                      ::multiclass-prereqs    ; NEW (equipment support)
                      ::weapons               ; NEW (equipment support)
                      ::equipment             ; NEW (equipment support)
                      ::weapon-choices]))     ; NEW (equipment support)
```

**Update class-builder UI:**
- Add starting equipment editor
- Add weapon choice configurator
- Add multiclass prerequisite selector

**Pros:**
- Full fidelity cloning
- Homebrew classes become feature-complete
- Better user experience

**Cons:**
- More work to implement
- Increases complexity of class-builder
- Need to update character creation flow to use homebrew equipment

### Option 3: Read-Only Clone with Override (Alternative)

**Don't convert to homebrew format - keep reference to parent:**

```clojure
{:name "My Barbarian Variant"
 :key :barbarian-variant-123
 :parent-class :barbarian     ; Reference to built-in
 :overrides {:hit-die 10      ; Only store changes
             :traits [{:name "Custom Rage" :level 2 :summary "..."}]}}
```

**Pros:**
- No data loss (uses parent for missing fields)
- Smaller storage footprint
- Automatic errata updates

**Cons:**
- Requires Phase 2 inheritance system
- More complex to implement
- User can't truly "divorce" from parent

---

## Recommended Path Forward

### Phase 1: Limited Clone with Clear Warnings

1. ✅ Implement cloning for supported fields only
2. ✅ Show detailed warning modal listing what will be lost
3. ✅ Add auto-generated note to description field:
   ```
   "Cloned from Barbarian. Manual recreation needed for:
    - Starting Equipment: Greataxe or martial weapon, 2 handaxes or simple weapon, 4 javelins, explorer's pack
    - Multiclass: Requires STR 13
    - Rage: Bonus action, damage bonus scales with level
    - Unarmored Defense: AC = 10 + DEX + CON when not wearing armor"
   ```
4. ✅ Document limitations clearly in UI

### Phase 2: Extend Schema for Equipment

1. Add `:weapons`, `:equipment`, `:weapon-choices`, `:multiclass-prereqs` to homebrew spec
2. Update class-builder UI to support these fields
3. Re-clone can then preserve equipment data

### Phase 3: Complex Modifier Support

1. Research how to represent complex modifiers in homebrew
2. May require new modifier types or formula language
3. Potentially biggest lift, lowest ROI (most users won't need this)

---

## Testing Checklist

Before implementing clone:

- [ ] Map every built-in class field to homebrew equivalent
- [ ] Identify which fields have no mapping
- [ ] Create conversion function for supported fields
- [ ] Write unit tests for conversion
- [ ] Test clone of each built-in class
- [ ] Document data loss for each class
- [ ] Create warning message templates
- [ ] Add "what was lost" note to cloned class description

---

## Conclusion

**Answer to original question:** No, the current homebrew format does NOT have enough "room" to fully represent built-in classes.

**Critical gaps:**
1. Starting equipment/weapons
2. Multiclass prerequisites
3. Complex function-based modifiers

**Recommended approach:**
- Phase 1: Implement partial cloning with clear warnings
- Phase 2: Extend homebrew schema to support equipment
- Phase 3: Consider complex modifier support (low priority)

**User value:** Even with limitations, cloning provides significant value by:
- Saving time on simple fields (hit die, ASIs, saves, skills, traits)
- Providing structure for customization
- Making variant creation accessible

Users should be clearly informed about limitations and what they'll need to recreate manually.

---

**Document Version:** 1.0
**Status:** Analysis Complete - Ready for Decision
