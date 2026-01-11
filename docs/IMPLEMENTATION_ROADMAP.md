# Class Feature Extension - Implementation Roadmap

**Created:** 2026-01-11
**Status:** Ready for Implementation
**Based on:** Comprehensive research of 289 features across 12 classes

---

## Quick Summary

**Goal:** Allow homebrew classes to use the same features as built-in classes (Rage, Ki, Sneak Attack, Unarmored Defense, etc.)

**Approach:** Extend existing `:props` conversion system, NOT build new architecture

**Effort:** Weeks, not months (infrastructure already exists)

---

## Research Documents

All analysis complete and documented:

1. **FEATURE_SYSTEM_ARCHITECTURE.md** - How the system works
2. **BUILT_IN_CLASS_FEATURES_CATALOG.md** - All 289 features cataloged
3. **PROPS_SCHEMA_DESIGN.md** - Complete schemas for 10 prop types
4. **HOMEBREW_FORMAT_ANALYSIS.md** - Format capacity analysis
5. **CLASS_CLONING_AND_EXTENSION_PLAN.md** - Original planning (pre-research)

---

## How It Actually Works

### Current System (Already Exists!)

```
Built-in Classes              Homebrew Classes
      ↓                              ↓
Call modifier functions        Store :props data
(mod5e/bonus-action...)       {:armor-prof {:light true}}
      ↓                              ↓
      |                    plugin-modifiers() converts
      |                    make-feat-modifiers() maps
      |                              ↓
      └──────────┬─────────────────┘
                 ↓
         Same modifier objects
                 ↓
        Applied to character
```

### What's Needed

**Not:** Build new feature system
**Actually:** Add more cases to existing `make-feat-modifiers` function

**Example:**
```clojure
;; In options.cljc:3230
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; Existing 30 cases work fine
    :armor-prof (...)
    :weapon-prof (...)

    ;; Add NEW cases for class features:
    :resource-pool (resource-pool-modifiers v option-key)  ; NEW
    :action-feature (action-feature-modifiers v option-key) ; NEW
    :scaling-damage (scaling-damage-modifiers v option-key) ; NEW
    ;; ... etc

    nil))
```

---

## Implementation Phases

### Phase 1: Foundation (4-6 weeks)

**Goal:** Expose the most valuable features (covers 70% of use cases)

#### 1.1 Resource Pools (2 weeks)
**Features:** Rage, Ki, Bardic Inspiration, Channel Divinity, Action Surge, Sorcery Points

**Files:**
- `src/cljc/orcpub/dnd/e5/options.cljc` - Add `resource-pool-modifiers` function, add case to `make-feat-modifiers`
- `src/cljs/orcpub/dnd/e5/views.cljs` - Add UI controls in class-builder

**Implementation:**
```clojure
;; In :props
:resource-pool {
  :rage {
    :enabled true
    :name "Rage"
    :action-type :bonus-action
    :uses {:scaling :level-breakpoints
           :breakpoints {1 2, 3 3, 6 4, 12 5, 17 6}}
    :recovery :long-rest
    :duration {:amount 1 :unit :minute}
    :page 48
  }
}

;; Converts to:
(mod5e/bonus-action
 {:name "Rage"
  :page 48
  :frequency (units5e/long-rests uses)
  :duration units5e/minutes-1})
```

**Testing:**
- Create homebrew Barbarian variant with custom Rage uses
- Verify it works the same as built-in Rage
- Check backward compatibility with existing characters

#### 1.2 Static Traits (1 week)
**Features:** Danger Sense, Feral Instinct, Evasion, etc.

**Implementation:**
```clojure
:static-trait {
  :danger-sense {
    :enabled true
    :name "Danger Sense"
    :level 2
    :summary "Advantage on DEX saves against effects you can see"
    :page 48
  }
}

;; Converts to:
(mod5e/trait "Danger Sense" nil 2 "Advantage on DEX saves...")
```

#### 1.3 Enhanced Proficiencies (1 week)
**Features:** Skills, saves, expertise, languages (all types)

**Already partially supported - just needs:**
- UI expansion for all-languages toggle
- Better expertise controls
- Save proficiency checkboxes

#### 1.4 Spell Features - Domain Spells (1 week)
**Features:** Always-prepared spells (Cleric domains, Paladin oaths)

**Implementation:**
```clojure
:spell-feature {
  :domain-spells {
    :enabled true
    :grant-type :always-prepared
    :spells {
      1 [:bless :cure-wounds]
      2 [:lesser-restoration :spiritual-weapon]
    }
    :min-class-level 1
  }
}

;; Converts to:
(opt5e/cleric-spell 1 :bless 1)
(opt5e/cleric-spell 1 :cure-wounds 1)
...
```

**Phase 1 Deliverables:**
- [ ] Users can add Rage-like features to any class
- [ ] Users can add static traits
- [ ] Full proficiency customization
- [ ] Domain/oath spell support
- [ ] Comprehensive UI for all 4 prop types
- [ ] Documentation with examples

---

### Phase 2: Core Mechanics (4-6 weeks)

**Goal:** Add the features that define class combat roles

#### 2.1 Action Features (2 weeks)
**Features:** Cunning Action, Flurry of Blows, Deflect Missiles, Wild Shape

```clojure
:action-feature {
  :cunning-action {
    :enabled true
    :name "Cunning Action"
    :action-type :bonus-action
    :level 2
    :cost {:type :none}
    :effect "Dash, Disengage, or Hide"
    :frequency :unlimited
  }
}
```

#### 2.2 Scaling Damage (2 weeks)
**Features:** Sneak Attack, Divine Strike, Martial Arts, Brutal Critical

```clojure
:scaling-damage {
  :sneak-attack {
    :enabled true
    :name "Sneak Attack"
    :damage {
      :dice-count {:type :level-formula
                   :formula :level-divided-rounded-up
                   :divisor 2}
      :dice-size {:type :fixed :size 6}
    }
    :frequency :per-turn
    :level 1
  }
}
```

#### 2.3 Defenses (1 week)
**Features:** Resistances, immunities, condition immunities

```clojure
:defense {
  :damage-resistance {:necrotic true}
  :damage-immunity {:poison true}
  :condition-immunity {:poisoned {:enabled true}}
}
```

**Phase 2 Deliverables:**
- [ ] Bonus actions, reactions configurable
- [ ] Scaling damage formulas work
- [ ] All defense types available
- [ ] Characters can have Sneak Attack-like features
- [ ] Full UI for action configuration

---

### Phase 3: Advanced Features (6-8 weeks)

**Goal:** Complex features and edge cases

#### 3.1 Movement (1 week)
**Features:** Monk speed, Barbarian Fast Movement, Flying

#### 3.2 Dynamic Traits (3 weeks)
**Features:** Jack of All Trades, Song of Rest, Aura of Protection

**Challenge:** Requires template system for dynamic summaries

#### 3.3 AC Calculation (2 weeks)
**Features:** Unarmored Defense, Draconic Resilience

**Challenge:** Conditional modifiers, priority resolution

#### 3.4 Spell Recovery & Advanced Spell Features (2 weeks)
**Features:** Arcane Recovery, Magical Secrets, Metamagic

**Phase 3 Deliverables:**
- [ ] All 10 prop types implemented
- [ ] Complex features work correctly
- [ ] Template system for dynamic text
- [ ] Complete feature parity with built-in classes

---

### Phase 4: Polish & Scaling (2-4 weeks)

#### 4.1 Cloning System
- One-click clone of built-in classes
- Extracts all features to props
- Opens in class-builder pre-filled

#### 4.2 Parent-Child Inheritance
- Reference parent class
- Override specific properties
- Auto-inherit errata updates

#### 4.3 Feature Library
- Browse available features
- Examples from built-in classes
- Copy/paste feature configs

#### 4.4 Validation & Testing
- Comprehensive validation
- Error messages
- Backward compatibility tests

---

## Critical Files

### Must Modify

| File | Purpose | Changes |
|------|---------|---------|
| `options.cljc:3230` | `make-feat-modifiers` | Add 10 new cases |
| `options.cljc` | Conversion functions | Add helper functions for each prop type |
| `views.cljs:5429` | Class builder UI | Add UI controls for each prop type |
| `events.cljs` | Event handlers | Add events for prop management |
| `db.cljs:112` | Default class template | Extend with new optional fields |

### Must NOT Modify (Breaking Changes)

- Built-in class keys (`:barbarian`, `:wizard`, etc.)
- Plugin storage structure root
- Class aggregation subscription keys (without careful validation)

---

## Testing Strategy

### Unit Tests
```clojure
(deftest resource-pool-conversion
  (let [rage-config {...}
        modifiers (resource-pool-modifiers rage-config :custom-class)]
    (is (= 1 (count modifiers)))
    (is (= "Rage" (:name (first modifiers))))))
```

### Integration Tests
```clojure
(deftest homebrew-with-rage
  (let [homebrew-class {:props {:resource-pool {:rage {...}}}}
        character (create-character homebrew-class)]
    (is (has-feature? character "Rage"))
    (is (= 2 (rage-uses character 1)))))
```

### Backward Compatibility Tests
```clojure
(deftest old-characters-still-work
  (let [old-char (load-from-storage "character-id-from-2024")]
    (is (not (nil? old-char)))
    (is (= "Barbarian" (class-name old-char)))
    (is (has-feature? old-char "Rage"))))
```

---

## Success Metrics

### Phase 1 Success
- [ ] Users can create Barbarian variant with custom Rage
- [ ] Users can create Rogue variant with custom traits
- [ ] Domain spell system works for custom "cleric-like" classes
- [ ] No existing characters broken

### Phase 2 Success
- [ ] Users can create class with Sneak Attack scaling damage
- [ ] Bonus action features work (Cunning Action-like)
- [ ] All defense types configurable

### Phase 3 Success
- [ ] All 10 prop types functional
- [ ] Users can recreate ANY built-in class feature in homebrew
- [ ] Dynamic summaries work (Jack of All Trades-style)

### Phase 4 Success
- [ ] One-click clone of any built-in class
- [ ] Parent-child inheritance works
- [ ] Comprehensive validation prevents errors
- [ ] Feature library helps discovery

---

## Risk Mitigation

### Backward Compatibility Risks

**Risk:** Changes break existing characters
**Mitigation:**
- All changes are additive (new optional fields only)
- Comprehensive test suite
- Test with real saved data before release
- Keep old conversion logic intact

### Complexity Risks

**Risk:** Dynamic traits/AC calculations too complex
**Mitigation:**
- Implement in Phase 3 (not MVP)
- Start with simpler approximations
- Can punt to future release if needed
- Focus on high-value features first

### Performance Risks

**Risk:** Additional prop processing slows character creation
**Mitigation:**
- Profile before and after
- Optimize conversion functions
- Cache calculated values where possible

---

## Documentation Requirements

For each prop type, create:

1. **User Guide** - How to use it, with examples
2. **Schema Reference** - Complete data structure
3. **Conversion Logic** - How it maps to modifiers (for advanced users)
4. **Troubleshooting** - Common errors and solutions

---

## Prototype Plan

**Before starting Phase 1 implementation, prototype ONE feature end-to-end:**

**Feature:** Rage (resource pool)

**Steps:**
1. Add `:resource-pool` case to `make-feat-modifiers`
2. Implement `resource-pool-modifiers` function
3. Add UI controls to class-builder
4. Create test homebrew class with Rage
5. Verify it works identically to Barbarian's Rage
6. Test with character creation
7. Validate backward compatibility

**Success Criteria:**
- Homebrew class with `:props {:resource-pool {:rage {...}}}` works
- Character can use Rage X times per long rest
- Rage duration works
- No existing characters broken

**If successful:** Proceed with Phase 1
**If issues:** Revise approach, update docs, retry

---

## Timeline Estimate

| Phase | Duration | Effort | Risk |
|-------|----------|--------|------|
| Prototype (Rage) | 1 week | 1 dev | Low |
| Phase 1 (Foundation) | 4-6 weeks | 1 dev | Low |
| Phase 2 (Core Mechanics) | 4-6 weeks | 1 dev | Medium |
| Phase 3 (Advanced) | 6-8 weeks | 1 dev | High |
| Phase 4 (Polish) | 2-4 weeks | 1 dev | Low |
| **Total** | **17-25 weeks** | **~5 months** | |

**Minimum Viable Product (MVP):** Prototype + Phase 1 = ~7 weeks

**Full Feature Parity:** All phases = ~5 months

---

## Next Steps

**Immediate (Today):**
- [x] Complete research and documentation ✅
- [x] Design prop schemas ✅
- [x] Update planning documents ✅

**This Week:**
- [ ] Review roadmap with team
- [ ] Get approval for Phase 1 scope
- [ ] Set up test environment
- [ ] Create backward compatibility test suite

**Next Week:**
- [ ] Start Rage prototype
- [ ] Implement `resource-pool-modifiers` function
- [ ] Add basic UI controls
- [ ] Test end-to-end

**Following Weeks:**
- [ ] Complete Phase 1 implementation
- [ ] User testing and feedback
- [ ] Iterate based on learnings

---

**Document Version:** 1.0
**Last Updated:** 2026-01-11
**Status:** Approved - Ready to Start Prototype
