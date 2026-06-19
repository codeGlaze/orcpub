# Homebrew Decision Vocabulary & Compile Paths

**The model:** the app is the **tools + forms + logic** (server-side). An `.orcbrew` stores the
creator's **decisions** (data), not logic. On import the app's **compile paths** turn those
decisions into modifiers/selections. What a creator can express is bounded by the **forms** (input)
and the **compile paths** (what data the app knows how to realize). Goal: grow that vocabulary, give
it **cross-silo** reach, and make growing it **cheap/sustainable**.

> **Scope:** this is a WIRING map — which decision keys each builder emits and which assembly fn they
> reach. "✅"/"rich" means a code path EXISTS, not that it was exercised on a built character; "❌"
> means no path was found in the code read, not a proven limit. The one finding taken all the way to
> behavior (and confirmed against a real `.orcbrew`) is the subclass-spellcasting gate below.
>
> **Deep-dives (canonical homes — this doc summarizes, those verify):** spells →
> `spell-granting-across-silos.md`; armor class → `armor-class-computation.md`; runtime toggles /
> conditional effects → `runtime-toggles-and-conditional-modifiers.md`; proposed grant/select
> builder vocabulary → `declarative-grant-vocabulary.md` (design).

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
  (`options.cljc:2129/2176`). Resolved (backward trace below): the race *builder* exposes ASI as
  **fixed per ability**, not a choose-which-to-bump option — ASI *options* remain feat-only.

### `:prereqs` / `:path-prereqs` → `feat-prereqs` — FEAT, LIMITED vocab ✅
- `options.cljc:3195`. Supported prereqs: an **ability** keyword → "≥ 13" (hardcoded threshold),
  `:spellcasting` → can-cast, else **armor proficiency**; `:path-prereqs {:race …}` → must be a
  given race.
- **GAPS:** no **level**, **N class-levels**, or **alignment** prereqs. Resolved (backward trace
  below): prereqs are **feat-only** — not exposed in the race/subrace/class/subclass forms.

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
- **`:spell` grants an INNATE KNOWN SPELL, not spellcasting.** `(:spell …)` calls
  `mod5e/spells-known` (`modifiers.cljc:260`), which adds one spell to `?spells-known` castable via
  the chosen ability — the **same mechanism races use for racial spells**. It does **not** grant
  spell slots or a casting progression. Any class/subclass (incl. custom) can do this, but it's
  bolt-on innate spells, not "this subclass makes you a caster."

### ⚠️ CORRECTION (this doc was wrong before) — subclass spellcasting IS gated
An earlier version of this doc claimed "a subclass of a custom class can grant spells, Divine-Soul
style, the gated UI is just convenience." **That was wrong** (caught by the maintainer). The truth:
- **Real (slot-based) spellcasting via a subclass is GATED** to `#{:fighter :rogue :warlock :cleric
  :paladin}` in the subclass builder (`views.cljs:5975`). A **custom non-caster base class gets no
  spellcasting UI at all**, so you cannot make its subclass a real caster through the builder.
- `make-levels` offers class-name-gated shortcuts behind that UI: `:spellcasting` (1/3-caster, only
  `class ∈ #{:fighter :rogue}` — Eldritch Knight / Arcane Trickster), `:paladin-spells`,
  `:cleric-spells` (domain), `:warlock-spells` (expanded list choice).
- The path that *would* have let a custom subclass grant real spellcasting —
  `custom-subclass-spellcasting-selection` (adds `spell-slot-factor` + per-level spell selections,
  `options.cljc:2735`) — is **`#_`-commented out / disabled**.
- **Divine Soul is not a counterexample:** it's a *sorcerer* subclass, and sorcerer is already a
  full caster, so its expanded list rides on the base class's existing spellcasting. It does not
  demonstrate granting spellcasting to a non-caster.
- **The gate is in the COMPILE PATH, not just the UI — so an `.orcbrew` cannot bypass it.**
  ✅ statically verified in `make-levels` (`spell_subs.cljs:392`, the exact fn that compiles
  imported plugin subclasses via `::classes5e/plugin-subclasses`, `:448`):
  `add-spellcasting? (and spellcasting (#{:fighter :rogue} class))` (`:396`) — a hand-authored
  `:spellcasting` map is **ignored** unless `class ∈ #{:fighter :rogue}`; `:paladin-spells` only when
  `class = :paladin` (`:411`), `:cleric-spells` only `:cleric` (`:424`), `:warlock-spells` only
  `:warlock` (`:429`). So crafting EDN by hand does not get around the gate; the only ungated spell
  path remains `:level-modifiers {:type :spell}` (innate known spells). **No runtime test needed —
  the compile function is the authority and it gates.**
- **Net:** a subclass can add fixed *innate known spells* (`:spell` modifier) to any base class, but
  **cannot grant a spellcasting progression to a non-caster base class** via the builders.

#### ✅ Confirmed by real community data — the actual Divine Soul `.orcbrew`
Inspected the community "Xanathar's Guide to Everything" pack (the real Divine Soul the maintainer
remembered). It validates every point above:
- The whole 285 KB file has **exactly one** `:spellcasting` map and **one** `:spell-list` — both on a
  standalone custom **class** keyed `:sorcerer-divine-soul-` (under `:orcpub.dnd.e5/classes`), with
  `:level-factor 1` (full caster), `:ability ::char/cha`, a `:spells-known` schedule, cantrips, and
  the merged sorcerer+cleric **custom `:spell-list`**. I.e. they rebuilt sorcerer as a custom class
  so the class-builder's custom spell-list could carry the expanded list.
- The "affinities" (Law, etc.) are **subclasses parented to that custom class** (`:class
  :sorcerer-divine-soul-`). **None** of them carry `:spellcasting`; they only add an innate themed
  spell via `:level-modifiers [{:type :spell :value {:ability …cha :level 1 :key :bless}}]` + traits.
- So the community did **not** grant spellcasting via a subclass — they put spellcasting on a custom
  class and used subclasses for flavor + innate spells. This is the empirical answer to "can a
  custom base class get spellcasting from a subclass?": no — work around it at the class level.

### TWO PARALLEL grant vocabularies — overlapping, divergent (the real duplication) ⚠️
- `make-feat-modifiers` (`:props`) and `level-modifier` (`:level-modifiers :type`) both grant
  mechanics, with overlapping coverage (weapon/skill/armor prof, resist/immunity, save-adv,
  fly/swim speed) but **different extras**: `level-modifier` has **`:spell`**, `:num-attacks`,
  `:tool-prof`; `make-feat-modifiers` has `:language`, `:initiative`, `:max-hp-bonus`, passive
  senses, the spell-choice templates. So the *same* capability lives in two places with uneven
  reach — one grants a spell, the other a language, neither both. This drift (rushed dev) is the
  "built different" inconsistency; unifying the two vocabularies is a prime sustainability target.
- **✅ VERIFIED (traced up+down, 2026-06-19; supersedes a wrong first pass): they share an effect set
  but differ in APPLICATION MODE.** The first version of this note said "no useful distinction; B does no
  level-gating" — WRONG (I read the leaf compiler, not its caller). Corrected:
  - **Shared / duplicated:** the ~9 overlapping keys compile to the *same* `mod5e/*` primitive in both A
    and B (verified down — both call e.g. `mod5e/damage-resistance`). The effect arms are reimplemented
    in two `case`s. Real redundancy.
  - **Distinct / load-bearing:** A (`:props`) is **flat + unconditional** (an attribute map, "has X"). B
    (`:level-modifiers`) is **level-gated** — each entry carries a `:level`, and `make-levels`
    (`spell_subs.cljs:392`) `(group-by :level …)` places it at that class level ("gain X at level N"). So
    B can express level progression and A cannot. The value-shape difference (A map-of-flags via
    `collect-map-modifiers`; B single value + `:level`) reflects this — it is not arbitrary.
  - **Better:** factor out ONE shared effect vocabulary (the type→`mod5e/*` arms, once) used by BOTH a
    flat path and a level-gated path, or generalize to "effect + optional level/condition." NOT collapse
    to one compiler (loses the gating). (The "vocabulary A/B" labels are this KB's framing, not source
    comments — those comments were added by this work.)

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
  flying/swimming-speed. So a subclass of ANY class can add a fixed **innate known spell** via a
  `:spell` level-modifier — but that's `spells-known` (racial-style innate spell), **NOT a
  spellcasting progression**. **Real slot-based spellcasting via a subclass is GATED** to the 5
  classes above; a custom non-caster base class cannot be made a caster by its subclass through the
  builder (see the CORRECTION section above — my earlier "just convenience" claim was wrong).

### Class — `class-builder` (views `:5643`) → `level-option` (`options.cljc:2771`) ✅ rich (with a plugin gap)
Form sections: name/source/description; **hit die** (6/8/10/12); **subclass** pick-level/title/flavor;
**saving-throw** proficiencies; **ASI levels** (which levels 4–20 grant an increase — a *schedule*,
not a stat choice); **full spellcasting** — slots y/n, caster fraction (`:level-factor` full/half/
third), spell list (one of the existing class lists **or Custom**, which opens per-level spell
checkboxes = a **custom `:spell-list`**), spellcasting ability, cantrip schedule, and a per-level
"spells this class can choose from" grid (→ real spell **choices** via `spells-known-selections`,
`options.cljc:647`); `option-skill-proficiency-choice` + `option-skill-expertise-choice` (CHOICES);
**`option-level-modifiers`** (B vocab, incl `:spell`); `option-level-selections` (TEXT traits);
`option-traits`. So the class silo is the **richest** — only place with full-caster spellcasting +
custom spell list.
- **`(not plugin?)` gate — investigated, NOT a homebrew gap.** In `level-option` the standard
  **ASI selection**, **hit-points selection**, and per-level `modifiers/level` are gated
  `(when (not plugin?) …)` (`options.cljc:2817/2819/2833/2841`). I initially read this as a
  homebrew gap; it is **not**. `:plugin? true` is set **only on hardcoded UA template overlays**
  (`templates/ua_*.cljc`) — partial add-ons that layer new subclasses/options onto an *existing*
  class and must not re-emit core scaffolding. A homebrew class from the **builder** never sets
  `:plugin?` (verified: not in the builder events, export, or `::classes5e/plugin-classes`
  assembly at `spell_subs.cljs:464`, which only assocs `:plugin-source`/`:modifiers`/`:levels`).
  So builder classes flow through `class-option`→`level-option` with `plugin?` falsey and **do**
  get ASI selection + hit points normally. (Lesson reinforced: `:plugin?` ≠ "came from a plugin.")

### Subrace — `subrace-builder` (views `:6090`) → `subrace-option` (`options.cljc:1984`) ✅ rich (≈ race)
Form sections: name, **parent race**, source; size/speed/darkvision; **ASI** (fixed per ability,
−2..+2, shown as race+subrace total); modifiers (`option-hps`, damage-resist/immunity, save-adv,
weapon/armor/tool/skill prof, **`option-skill-proficiency-choice`** = CHOICE, languages); **spells**
(`option-spells`, fixed-only); `option-traits`. Essentially the same vocabulary as race: fixed
mechanics + skill-prof choice + fixed spells + traits + fixed ASI. No spell choice, no prereqs.

### Background — `background-builder` (views `:6368`) → `background-option` (`options.cljc:2456`) ✅ minimal
Form sections: name/source/description; **fixed** skill proficiencies (checkboxes, no "choose N");
languages (`language-choice-checkboxes`); tool proficiencies; starting equipment; `option-traits`.
The simplest mechanical silo — fixed profs + equipment + traits. No ASI, no spells, no
level-modifiers, no `:props` mechanics, no prereqs. (Backgrounds in 5e are light by design.)

### Simple types (boon/invocation/language/…) → `simple-content-builder` (views `:6547`) ✅ descriptive
**Verified:** these use `simple-content-builder` = **Name + Option Source + Description** plus an
optional list of declarative `extra-fields` (rendered via `render-builder-field`, validated by the
same `bf/validate-fields` used for import/export). Boons and invocations grant warlock features in
play, but the homebrew **decision** is name + descriptive text only — no mechanical grant vocabulary
(consistent with the `level-selection` text-only finding). Spell/monster/encounter/selection are
their own structured forms (stat blocks / option lists), not part of the cross-silo *grant* story.
`draconic-ancestry` is the one simple type with a real field schema (`field_schemas.cljc`).

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
subset of compile paths *and* a different builder form. **All six mechanical silos below are now
verified** per silo via the backward trace (builder form → assembly fn).
(✅ verified present, ❌ no path, ⚠️ partial/limited.)

| Capability | Feat | Race | Subrace | Class | Subclass | Background |
|---|---|---|---|---|---|---|
| Fixed mechanics (`:props`/`:level-modifiers`) | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ profs/equipment only |
| Prof/skill **choice** | ✅ | ✅ | ✅ skill-prof-choice | ✅ skill-prof/-expertise | ✅ skill-prof/-expertise | ⚠️ language/tool choice only |
| Fixed **innate** known spell (`spells-known`) | ✅ via template | ✅ `:spells` | ✅ `:spells` | ✅ `:level-modifiers :spell` | ✅ `:level-modifiers :spell` (any base class) | ❌ |
| **Spellcasting progression (slots)** | ❌ | ❌ | ❌ | ✅ full/half/third caster | ⚠️ **gated** to `#{fighter rogue warlock cleric paladin}` — custom non-caster base class **cannot** | n/a |
| **Spell choice** | ✅ 3 templates | ⚠️ fixed-only | ⚠️ fixed-only | ✅ full caster | ⚠️ class-gated UI only | ❌ |
| ASI | ✅ fixed **or** choice | ✅ fixed | ✅ fixed | ✅ standard at chosen levels | n/a | ❌ |
| Traits | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Prereqs | ✅ limited vocab | ❌ not in form | ❌ | ❌ not in form | ❌ not in form | ❌ |
| Trackable resource (Axis B) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Custom/expanded spell list | ❌ | ❌ | ❌ | ✅ `:spell-list` | ⚠️ via `:spellcasting` | ❌ |

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
- **Subclass-granted spellcasting for non-caster base classes** — gated; the fix is re-enabling the
  commented-out `custom-subclass-spellcasting-selection` (`options.cljc:2735`). See the CORRECTION
  section above for the full finding (don't restate it).
- **Mechanical** homebrew choices (vs `level-selection`'s text-only).
- **Trackable resources** (Axis B) — no path at all.
- **Richer prereqs** — level / N-class-levels / alignment (engine likely supports the checks;
  the *decision vocabulary* doesn't).

## Sustainability note
Adding a *fixed* mechanic to the shared vocabulary = one `case` arm in `make-feat-modifiers` + a
form field, and it reaches every silo for free. The **choice** side (`make-feat-selections`) is
*not* shared — wiring it into races/subraces/subclasses (and generalizing the spell-choice
templates) is the highest-leverage cross-silo + sustainability work.

## Status / next cycles
**Done (backward trace, verified):** Feat, Race, Subrace, Class, Subclass, Background — builder
form → assembly fn, plus the cross-silo capability table and the four-place duplication finding.
Confirmed: prereqs are feat-only (not in race/subclass/class forms); subclass spell *choice* is
class-gated UI only (fixed spells via `:level-modifiers :spell` work for any class); the
`(not plugin?)` gate is not a homebrew gap.

**Also done:** simple/descriptive silos confirmed non-mechanical (`simple-content-builder`).

**Done:** in-app source comments added at the key assembly fns (`make-feat-modifiers`,
`make-feat-selections`, `feat-option-from-cfg`, `race-option`, `subrace-option`, `level-option`,
`level-modifier`, `spellcasting-template`) cross-linking this KB.

**Remaining:** the design/build work tracked in `declarative-grant-vocabulary.md` (uniform
data-path → reusable controls → generated builder UI), and the behavioral test plan (build
characters and observe; see that plan) to convert the ✅ "path exists" rows into observed behavior.
