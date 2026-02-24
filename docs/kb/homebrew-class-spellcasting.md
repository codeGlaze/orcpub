# Homebrew Class Spellcasting Architecture

How spellcasting is configured for homebrew classes, what the builder exposes,
and what's hardcoded vs generic.

---

## Builder UI

`views/builders.cljs:1870-1930` — four dropdowns when spellcasting is enabled:

| Dropdown | Values | Sets |
|----------|--------|------|
| "Does this class have spell slots?" | Yes/No | `:spellcasting` map (or nil) |
| "What spell list does this class use?" | Custom / copy existing class | `:spellcasting :spell-list-kw` |
| "Spellcasting ability" | STR/DEX/CON/INT/WIS/CHA | `:spellcasting :ability` |
| "At what level does this class first gain spell slots?" | 1, 2, 3 | `:spellcasting :level-factor` |

The level dropdown label means: level 1 = full caster, level 2 = half caster,
level 3 = third caster. Maps directly to when the class first gains slots.

Enabling spellcasting defaults to `{:level-factor 3, :known-mode :schedule, :ability ::cha}`.

### Not exposed in builder

- `:pact-magic?` — no toggle exists
- `:slot-schedule` — custom slot tables can't be defined
- `:round-up?` — doesn't exist in data model yet (needed for #435)
- `:prepares-spells?` — hardcoded per `known-mode`, not user-selectable
- `:cantrips-known` beyond first level — only level-1 cantrip count is exposed

---

## Data Model

Built-in class spellcasting config (`classes.cljc`):

```clojure
;; Standard caster (Ranger):
:spellcasting {:level-factor 2
               :known-mode :schedule
               :spells-known half-caster-spells-known-schedule
               :ability ::char5e/wis}

;; Prepared caster (Cleric):
:spellcasting {:level-factor 1
               :known-mode :all
               :ability ::char5e/wis
               :prepares-spells? true}

;; Pact Magic (Warlock):
:spellcasting {:cantrips-known {1 2 4 1 10 1}
               :spells-known warlock-spells-known
               :slot-schedule t-base/warlock-spell-slot-schedule
               :known-mode :schedule
               :pact-magic? true
               :ability ::char5e/cha}
:modifiers [(mod/modifier ?pact-magic? true)]
```

### Known modes

| Mode | Behavior | Used by |
|------|----------|---------|
| `:schedule` | Fixed spells-known table by level | Bard, Ranger, Sorcerer, Warlock |
| `:all` | Access entire spell list, prepare subset | Cleric, Druid, Paladin |
| `:acquire` | Learn spells incrementally (spellbook) | Wizard |

---

## Compilation Paths (homebrew class → entity template)

Two paths in `options.cljc` compile class configs into entity modifiers:

### Class option builder (`options.cljc:~2970-2981`)

```clojure
(when level-factor [(modifiers/spell-slot-factor kw level-factor)])
(when (:known-mode spellcasting)
  [(modifiers/spells-known-mode name (:known-mode spellcasting))])
```

Emits: `spell-slot-factor`, `spells-known-mode`, class/save/armor/weapon profs.
Does NOT emit: `?pact-magic?` modifier, `:slot-schedule`, `:round-up?`.

### Subclass option builder (`options.cljc:~2620-2633`)

Same pattern — emits slot factor and known-mode but not pact-magic.

### Slot resolution (`options.cljc:655`)

```clojure
slots (or (when slot-schedule (slot-schedule cls-lvl))
          (total-slots cls-lvl level-factor))
```

Custom `:slot-schedule` takes priority over calculated slots. This means
if a homebrew class config includes `:slot-schedule`, it would already be
used — the compilation path just needs to pass it through.

---

## Spell Slot Calculation

`template_base.cljc:263-299`:

```clojure
;; Caster level calculation (line 263-269):
?total-spellcaster-levels (apply + (map (fn [[cls-kw factor]]
                                          (-> ?levels cls-kw :class-level
                                              (/ factor) int))  ;; BUG: always rounds down
                                        ?spell-slot-factors))

;; Spell slot merge (line 285-299):
?spell-slots (merge-with +
               (cond
                 ;; multiclass: use combined level with factor 1
                 (> (count ?spell-slot-factors) 1)
                 (total-slots ?total-spellcaster-levels 1)
                 ;; single class: use class level with its factor
                 (= 1 (count ?spell-slot-factors))
                 (total-slots class-level factor)
                 :else {})
               ;; pact magic: ADDED SEPARATELY, hardcoded to :warlock
               (when ?pact-magic?
                 (warlock-spell-slot-schedule (?class-level :warlock))))
```

### Hardcoded `:warlock` references

| File | Line | Code | Impact |
|------|------|------|--------|
| `template_base.cljc` | 135 | `?warlock-level (?class-level :warlock)` | Convenience binding, check if used elsewhere |
| `template_base.cljc` | 299 | `(?class-level :warlock)` | Pact magic slot lookup ignores homebrew classes |

### What's generic (already works for any class)

| File | What | Status |
|------|------|--------|
| `character.cljc:525-526` | `pact-magic?` reads from built char props | Generic |
| `subs.cljs:677` | `::char5e/pact-magic?` subscription | Generic |
| `views.cljs:1739-1767` | `spell-slots-table` hides expansion for pact magic | Generic |
| `views.cljs:2074-2086` | Subscribes to `pact-magic?`, passes to slot table | Generic |

---

## Pact Magic Slot Schedule

`template_base.cljc:13-33` — hardcoded as `warlock-spell-slot-schedule`:

```
{level {max-spell-level num-slots}}
 1  → {1 1}     2  → {1 2}     3  → {2 2}     4  → {2 2}
 5  → {3 2}     6  → {3 2}     7  → {4 2}     8  → {4 2}
 9  → {5 2}    10  → {5 2}    11  → {5 3}    12  → {5 3}
13  → {5 3}    14  → {5 3}    15  → {5 3}    16  → {5 3}
17  → {5 4}    18  → {5 4}    19  → {5 4}    20  → {5 4}
```

Any homebrew pact magic class would reuse this same table. Custom pact
schedules are a future expansion concern.

---

## Fix Plan: Enable Homebrew Pact Magic

> **Status: UNVERIFIED PROPOSAL** — line counts are estimates. Items marked
> [NEEDS VERIFICATION] have not been traced end-to-end.

| File | Change | Verified? |
|------|--------|-----------|
| `builders.cljs:1920` | Add "Pact Magic" to progression dropdown | Verified: read current dropdown code |
| `options.cljc:~2973` | Emit `(mod/modifier ?pact-magic? true)` when flag set | Verified: read compilation path, confirmed modifier not emitted |
| `options.cljc:~2632` | Same for subclass compilation path | Verified: read path, same gap |
| `template_base.cljc:299` | Replace `(?class-level :warlock)` with pact-magic class lookup | Verified: read hardcoded `:warlock` |
| `options.cljc:655` | Verify `:slot-schedule` flows through homebrew path | [NEEDS VERIFICATION] — seen in resolution code but not traced from homebrew class config to this call site |

### Open questions (not yet verified)

1. Does a homebrew class's `:slot-schedule` key survive compilation into the
   entity template? The resolution at `options.cljc:655` checks for it, but
   the homebrew compilation paths at lines ~2632 and ~2973 may not pass it through.
2. Can `:level-factor` be omitted for pact magic classes, or does other code
   assume it's always present when `:spellcasting` exists?
3. Does the `?pact-magic?` entity modifier need any special handling in the
   entity build pipeline beyond what `(mod/modifier ?pact-magic? true)` provides?

### Builder dropdown expansion (rides with #435 round-up fix)

Current: `(range 1 4)` → values 1, 2, 3

Proposed items (design sketch, not final):
```
"1st level (Full Caster)"
"2nd level (Half Caster)"
"2nd level (Half Caster, Round Up)"
"3rd level (Third Caster)"
"3rd level (Third Caster, Round Up)"
"Pact Magic"
```

---

## Related Issues

- #435 — `int` rounding bug (Batch 2 fix)
- #34 — Class Build Spellcasting Upgrade (open since 2019, umbrella)
- #272 — Artificer Spells Known (UA definition commented out)
- #561 — Schedule 5 Spellcaster Progression
- #440 — Spell points (DMG variant, future)
- #636 — Homebrew class spell list assignment broken
