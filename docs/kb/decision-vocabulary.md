# Homebrew Decision Vocabulary & Compile Paths

**The model:** the app is the **tools + forms + logic** (server-side). An `.orcbrew` stores the
creator's **decisions** (data), not logic. On import the app's **compile paths** turn those
decisions into modifiers/selections. What a creator can express is bounded by the **forms**
(input) and the **compile paths** (what data the app knows how to realize). Goal: grow that
vocabulary, give it **cross-silo** reach, and make growing it **cheap/sustainable**.

> **STATUS: methodology corrected — see this banner.** Cycles 1–2 traced individual *leaf*
> compile functions (`plugin-modifiers`, `spell-modifiers`, …) forward. That approach
> **systematically UNDERSTATES capability**, because the richness lives in the per-silo
> **assembly functions** — `race-option` (`options.cljc:2210`), `subrace-option` (`:1984`),
> `make-levels` (`spell_subs.cljs:382`), `feat-option-from-cfg` (`options.cljc:3396`),
> `template-selections` (`template.cljc:1480`) — which combine many leaf paths *plus* handle
> `:abilities`, `:profs`/choices, `:selections`, `:traits`. Tracing leaves missed all of that
> (corrected 4×: feats' spell-choices, subclass `:spell`, the two vocabularies, and now races).
>
> **Switched to the right method: trace BACKWARD from each builder form → its assembly fn.**
> The per-silo COMPILE-PATHS inventory below is accurate per function, and the cross-silo
> capability table has now been **REBUILT** from the backward trace (feat/race/subclass verified
> rich; class/subrace/background still ⏳). Verified corrections:
> - **Races are NOT fixed-only.** `race-option` compiles `:abilities` (ASI), `:profs` →
>   `:skill-options`/`:language-options`/`:weapon-proficiency-options` (CHOICES via
>   `skill-selection`/`language-selection`), `:subraces`, `:traits`, `:spells`, `:selections`,
>   plus the `:props` modifiers. The race **builder** exposes all of these. So races have
>   choices + ASI + spells — comparable to feats.

---

## Compile paths (load-time: decision data → content), verified

### `:props` has TWO sides
**(a) `make-feat-modifiers` — FIXED mechanics, SHARED across silos** ✅
- `options.cljc:3287`, driven by `plugin-modifiers` (`:3345`).
- Run for **races/subraces/classes/subclasses/feats/draconic-ancestries** (despite the "feat"
  name — `spell_subs.cljs:144/157/447/481/769`, feats via `feat-option-from-cfg`). This is the
  one genuinely cross-silo path today.
- Vocabulary (fixed grants): movement (`:speed`,`:flying-speed`,`:flying-speed-equals-walking-speed`,
  `:swimming-speed`), profs (`:skill-prof`,`:skill-prof-or-expertise`,`:tool-prof-or-expertise`,
  `:armor-prof`,`:weapon-prof`), defenses (`:damage-resistance`,`:damage-immunity`,
  `:saving-throw-advantage`/`-traps`), `:language`,`:initiative`,`:max-hp-bonus`,
  `:passive-investigation-5`/`-perception-5`, + hardcoded one-offs (`:two-weapon-*`,
  `:medium-armor-*`,`:lizardfolk-ac`,`:tortle-ac`).

**(b) `make-feat-selections` — CHOICES, but FEAT-ONLY** ⚠️
- `options.cljc:3261`, invoked only via `feat-selections` ← `feat-option-from-cfg` (feats).
  **Not** called from the race/subrace/subclass compile paths (grep confirms none in
  `spell_subs.cljs`). So only feats get `:props` *choices*.
- Vocabulary (choices): `:weapon-prof-choice`, `:language-choice`, `:skill-tool-choice`, and
  **spell-choice templates** `:ritual-casting`, `:magic-novice`, `:attack-spell` (→ the
  ritual-caster / magic-initiate / spell-sniper selections).
- **CORRECTION to cycle 1:** homebrew **feats CAN grant spell choices** — via these 3 templates.
  But only those fixed templates; there is **no general parameterized spell-choice decision**
  (e.g. "choose 2 from the bard list, levels 1–2").

### `:ability-increases` → ASI (fixed OR choice) — FEAT ✅
- `feat-modifiers`/`feat-selections` (`options.cljc:3355`/`3372`): one ability → fixed `+1`;
  a *set* of abilities → an `ability-increase-selection` (CHOICE of which to bump). `:saves?`
  adds save proficiency. So feats express "two stats / a set to choose from."
- The **inline "Custom" race menu** also offers `homebrew-ability-increase-selection`
  (`options.cljc:2129/2176`), but a homebrew **race plugin** compiled via `plugin-modifiers`
  gets only fixed `:props` — `TODO` confirm whether the race *builder* exposes ASI options.

### `:prereqs` / `:path-prereqs` → `feat-prereqs` — FEAT, LIMITED vocab ✅
- `options.cljc:3195`. Supported prereqs: an **ability** keyword → "≥ 13" (hardcoded threshold),
  `:spellcasting` → can-cast, else **armor proficiency**; `:path-prereqs {:race …}` → must be a
  given race.
- **GAPS:** no **level**, **N class-levels**, or **alignment** prereqs. And `TODO`: do
  races/subclasses support prereqs at all, or feats only?

### `:spells` → `spell-modifiers` — FIXED known spells ✅
- `spell_subs.cljs:124`. Used by races/subraces (`:146/:159`). Compiles to `spells-known`
  ("you know spell X"). No choice.

### `:spellcasting` → `spellcasting-template` — CLASSES, full caster, custom list ✅
- `options.cljc:697`. A homebrew **class** can be a full caster: cantrips/spells known by level
  (`spells-known-selections` → real spell **choices**), known vs prepared, AND a **custom
  `:spell-list`** (`:707` — `(assoc spell-lists class-key spell-list)`), so custom/expanded spell
  lists work. This is the richest spell path — but it's **class-level only**.

### `:level-modifiers` → `level-modifier` — SECOND grant vocabulary (classes/subclasses), incl. `:spell` ✅
- `spell_subs.cljs:163`, run for classes AND subclasses via `make-levels` (`:396`), **no class gate**.
- Type vocabulary: `:weapon-prof`, `:num-attacks`, `:damage-resistance`, `:damage-immunity`,
  `:saving-throw-advantage`, `:skill-prof`, `:armor-prof`, `:tool-prof`, `:flying-speed`,
  `:swimming-speed`, `:flying-speed-equals-walking-speed`, **`:spell`** (→ `spells-known`).
- **CORRECTION:** **any class/subclass — including subclasses of a CUSTOM class — can grant
  fixed spells** via `:level-modifiers {:type :spell :value {:level :key :ability}}` (e.g. a
  Divine-Soul-style expanded list = many `:spell` entries). My earlier "subclass of a custom
  class can't grant spells" was WRONG.

### Subclass spell-granting — the class-gated convenience paths (NOT the only way) ⚠️
- `make-levels` ALSO offers class-name-gated shortcuts for specific patterns: `:spellcasting`
  (1/3-caster, only `class ∈ #{:fighter :rogue}`), `:paladin-spells` (paladin), `:cleric-spells`
  (cleric), `:warlock-spells` (warlock → choice selection). These are *additional* convenience
  wrappers; the general path is `:level-modifiers :spell` above. (Expanded-CHOICE lists — pick
  from cleric+sorcerer — likely need `:spellcasting :spell-list` or a selection; `TODO` confirm.)

### TWO PARALLEL grant vocabularies — overlapping, divergent (the real duplication) ⚠️
- `make-feat-modifiers` (`:props`) and `level-modifier` (`:level-modifiers :type`) both grant
  mechanics, with overlapping coverage (weapon/skill/armor prof, resist/immunity, save-adv,
  fly/swim speed) but **different extras**: `level-modifier` has **`:spell`**, `:num-attacks`,
  `:tool-prof`; `make-feat-modifiers` has `:language`, `:initiative`, `:max-hp-bonus`, passive
  senses, the spell-choice templates. So the *same* capability lives in two places with uneven
  reach — one grants a spell, the other a language, neither both. This drift (rushed dev) is the
  "built different" inconsistency; unifying the two vocabularies is a prime sustainability target.

### `:level-selections` → `level-selection` — TEXT-trait choices only ⚠️
- `spell_subs.cljs:341`. Homebrew class/subclass level-selections resolve a `:type` to a homebrew
  **Selection** whose options compile to `trait-cfg` (**name + description only** — mechanical
  data dropped). A homebrew "choice" here = named descriptions, not real grants.

### Resources (Axis B) — NO homebrew data path ✅ (confirmed gap)
- `used-resource` (`options.cljc:2034/2280/2636/2980`) tracks *which* limited-uses are spent, but
  only in **built-in** content. `:props` has no resource key. No data path for a homebrew creator
  to declare a trackable resource (ki/rage/sorcery). Matches the ki-is-text finding.

---

## Backward trace (the CORRECT method) — verified per silo: builder form → assembly fn

For each silo: what the **builder form** exposes, and the **assembly fn** that compiles it.

### Feat — `feat-builder` (views `:5264`) → `feat-option-from-cfg` (`options.cljc:3396`) ✅ rich
Form sections: name/source/description, **prereqs** (`feat-prereqs`), **ASI** (fixed or choice),
skill/language/weapon/armor/tool prof, HP, damage-resistance, speed, initiative, misc, and
**spellcasting** (the magic-initiate/ritual/spell-sniper templates). All compile via
`feat-option-from-cfg` (`:props`→modifiers + `:props`-choices via `make-feat-selections` +
`:ability-increases` + `:prereqs`).

### Race — `race-builder` (views `:6219`) → `race-option` (`options.cljc:2210`) ✅ rich
Form sections: name/source/description, size/speed/flying/swimming/darkvision, AC checkboxes,
**ASI** (fixed per ability), **prof CHOICES** (`option-skill-proficiency-choice`,
`option-language-proficiency-choice`, `option-weapon-proficiency-choice`), fixed profs
(weapon/armor/tool/skill/resistance/immunity/languages), and **spells** (`option-spells`).
`race-option` compiles `:abilities`, `:profs`→`:skill-options`/`:language-options`/
`:weapon-proficiency-options` (→ `skill-selection`/`language-selection` CHOICES), `:subraces`,
`:traits`, `:spells`, `:selections`, `:props`. **Not fixed-only.**

### Subclass — `subclass-builder` (views `:5946`) → `make-levels` (`spell_subs.cljs:382`) ✅ rich
Form sections: name, **parent class**, source; **spellcasting** UI *gated to
`#{:fighter :rogue :warlock :cleric :paladin}`* (fighter/rogue 1/3-caster toggle; paladin/cleric/
warlock fixed-spell editors — convenience for built-in patterns); `option-skill-proficiency-choice`
+ `option-skill-expertise-choice` (CHOICES); **`option-level-modifiers`** (generic, shown for ALL
subclasses); `option-level-selections` (→ TEXT-trait choices); `option-traits`.
- **`option-level-modifiers` dropdown (`modifier-values`, views `:5374`) INCLUDES `:spell`** —
  plus weapon/skill/armor/tool-prof, damage-resist/immunity, saving-throw-adv, num-attacks,
  flying/swimming-speed. So **a subclass of ANY class (incl. custom) grants spells** via a
  `:spell` level-modifier (Divine-Soul style). The class-gated spellcasting UI is just convenience.

### Class — `class-builder` (views `:5643`) → `make-levels` + `spellcasting-template` ⏳ TODO trace form
Compile side is rich (full caster + custom `:spell-list` + level-modifiers + level-selections).
Form-side trace pending.

### Subrace / Background / simple types (boon/invocation/language/spell/monster/encounter/selection) ⏳ TODO

## SHARPENED duplication finding — grant types live in up to FOUR places
There are **two grant vocabularies**, and **each is split across UI + compile**:

| Vocab | Compile | UI | Used by |
|---|---|---|---|
| **A — `:props`** | `make-feat-modifiers` (`options.cljc:3287`) | `feat-*` / `option-*` form sections | feats, races, subraces |
| **B — `:level-modifiers`** | `level-modifier` (`spell_subs.cljs:163`) | `modifier-values` dropdown (`views:5374`) | classes, subclasses |

They overlap heavily (weapon/skill/armor prof, resist/immunity, save-adv, fly/swim speed) but
diverge (A: language, initiative, max-hp, spell-choice templates; B: **`:spell`**, num-attacks,
tool-prof). **To make one grant type available everywhere you may edit up to four sites**
(A-compile, A-UI, B-compile, B-UI). That fan-out is the sustainability tax and the "built
different" feel — unifying to one grant vocabulary (single source → compile + UI) is the prime target.


## Cross-silo capability table — REBUILT from the backward builder→assembly trace ✅

The same capability is available in some silos and not others, because each silo runs a different
subset of compile paths *and* a different builder form. Rows below are verified per silo via the
backward trace (feat/race/subclass confirmed; class/subrace/background still ⏳).
(✅ verified present, ❌ no path, ⚠️ partial/limited, ⏳ not yet traced.)

| Capability | Feat | Race | Subclass | Class |
|---|---|---|---|---|
| Fixed mechanics (`:props` / `:level-modifiers`) | ✅ | ✅ | ✅ | ✅ |
| Prof/skill **choice** | ✅ `make-feat-selections` | ✅ `race-option` (`skill`/`language`/`weapon-proficiency-options`) | ✅ `option-skill-proficiency-choice`/`-expertise` | ⏳ |
| Fixed known spell | ✅ via template | ✅ `:spells` | ✅ `:level-modifiers :spell` (any class) | ✅ `:level-modifiers :spell` |
| **Spell choice** | ✅ 3 templates (magic-novice/ritual/attack) | ⚠️ fixed-only (`option-spells`) | ⚠️ class-gated convenience UI | ✅ full caster (`:spellcasting`) |
| ASI | ✅ fixed **or** choice (`:ability-increases`) | ✅ fixed per ability (`:abilities`) | n/a | ⏳ |
| Traits | ✅ | ✅ `:traits` | ✅ `option-traits` | ⏳ |
| Prereqs | ✅ limited vocab (`feat-prereqs`) | ❓ not in form | ❓ not in form | ⏳ |
| Trackable resource (Axis B) | ❌ | ❌ | ❌ | ❌ |
| Custom/expanded spell list | ❌ | ❌ | ⚠️ via `:spellcasting` only | ✅ `:spell-list` |

**Reading (corrected):** **feat, race, and subclass are all fairly rich** — each gets fixed
mechanics, prof/skill **choices**, traits, and at least fixed spells. The asymmetries are narrower
than the old leaf-trace suggested:
1. **Spell *choice* is fragmented**, not a general decision: feats have 3 fixed templates, classes
   have full-caster spellcasting, subclasses have only class-gated convenience UI, races have
   fixed-only. There is **no general parameterized spell-choice** (pick N from list L, levels a–b)
   anywhere.
2. **ASI options (choose-which-to-bump) are feat-only** — race ASI is fixed-per-ability in the form.
3. **Prereqs are feat-only** (and a limited vocab even there); races/subclasses don't expose them.
4. **Trackable resources** exist nowhere as a decision.
5. **Two parallel grant vocabularies** (`make-feat-modifiers`/`:props` vs `level-modifier`/
   `:level-modifiers`) overlap but diverge, so the *same* fixed mechanic is unevenly reachable and
   must be added in up to four places (see fan-out table above).

These five — especially the spell-choice fragmentation and the two-vocabulary split — are the
concrete "cross-silo dipping" frustration, restated precisely.

## What's genuinely missing (the creator vision → gaps)
- **A general, cross-silo grant vocabulary**: `make-feat-selections` (choices) is feat-only;
  races/subraces/subclasses can't reach it. The cross-silo win is making the choice/ASI/prereq
  vocabulary available to *every* silo, not just feats.
- **A general parameterized spell-choice** (any list/levels/count/filter) instead of 3 fixed
  templates (feats) + full-caster (classes) + fixed-known (`:spells`).
- **Unify the two grant vocabularies** (`make-feat-modifiers` ↔ `level-modifier`) so coverage is
  even (a feat could grant `:spell`; a subclass level-modifier could grant `:language`) — prime
  duplication-removal + sustainability target.
- **A general subclass spell-*choice*** (subclasses can grant *fixed* spells freely now, but
  *choices* are still the class-gated convenience paths only).
- **Mechanical** homebrew choices (vs `level-selection`'s text-only).
- **Trackable resources** (Axis B) — no path at all.
- **Richer prereqs** — level / N-class-levels / alignment (engine likely supports the checks;
  the *decision vocabulary* doesn't).

## Sustainability note
Adding a *fixed* mechanic to the shared vocabulary = one `case` arm in `make-feat-modifiers` + a
form field, and it reaches every silo for free. The **choice** side (`make-feat-selections`) is
*not* shared — wiring it into races/subraces/subclasses (and generalizing the spell-choice
templates) is the highest-leverage cross-silo + sustainability work.

## Next cycles
1. Builder **forms** per silo — what each builder actually lets a creator enter (the input side).
2. Exact subclass-spell paths + whether a general subclass-spell decision is feasible.
3. Prereqs in race/subclass silos (feat-only, or broader?).
4. Synthesize into in-app documentation (`*-options`/builder/compile comments + a top-level guide).
