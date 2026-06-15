# Homebrew Decision Vocabulary & Compile Paths

**The model:** the app is the **tools + forms + logic** (server-side). An `.orcbrew` stores the
creator's **decisions** (data), not logic. On import the app's **compile paths** turn those
decisions into modifiers/selections. What a creator can express is bounded by the **forms**
(input) and the **compile paths** (what data the app knows how to realize). Goal: grow that
vocabulary, give it **cross-silo** reach, and make growing it **cheap/sustainable**.

> **STATUS: cycles 1–2 done (compile paths, verified). Remaining: builder forms (input side),
> exact subclass-spell paths, race/subclass prereqs, and synthesis into in-app docs.** Every
> entry below is checked against code; `TODO`/`?` marks unverified. (Cycle 1 understated feats;
> this version corrects it.)

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

### Subclass spell-granting — FRAGMENTED, class-name-gated ⚠️ (the bespoke thicket)
- `spell_subs.cljs:382` (`make-levels`). A homebrew subclass grants spells only via:
  - `:spellcasting` — **only if parent `class ∈ #{:fighter :rogue}`** (1/3-caster).
  - `:paladin-spells` — only if `class = :paladin`.
  - `:cleric-spells` — only if `class = :cleric`.
  - `:warlock-spells` — only if `class = :warlock` (→ `warlock-subclass-spell-selection`).
- **A subclass of a *custom* class (or any other built-in) CANNOT grant spells.** Four bespoke
  paths for one capability, each hardcoded to a built-in class key. Not serialization — gating.

### `:level-selections` → `level-selection` — TEXT-trait choices only ⚠️
- `spell_subs.cljs:341`. Homebrew class/subclass level-selections resolve a `:type` to a homebrew
  **Selection** whose options compile to `trait-cfg` (**name + description only** — mechanical
  data dropped). A homebrew "choice" here = named descriptions, not real grants.

### Resources (Axis B) — NO homebrew data path ✅ (confirmed gap)
- `used-resource` (`options.cljc:2034/2280/2636/2980`) tracks *which* limited-uses are spent, but
  only in **built-in** content. `:props` has no resource key. No data path for a homebrew creator
  to declare a trackable resource (ki/rage/sorcery). Matches the ki-is-text finding.

---

## THE CENTRAL FINDING — cross-silo asymmetry

The same capability is available in some silos and not others, because each silo runs a different
subset of compile paths. (✅ verified, ❌ no path, ❓ TODO.)

| Capability | Feat | Race/Subrace | Class | Subclass |
|---|---|---|---|---|
| Fixed mechanics (`:props`) | ✅ | ✅ | ✅ | ✅ |
| Prof/skill **choice** (`make-feat-selections`) | ✅ | ❌ | ❓ | ❌ |
| Fixed known spell | (via template) | ✅ `:spells` | ✅ | ⚠️ gated |
| **Spell choice** | ✅ 3 templates | ❌ | ✅ full caster | ⚠️ class-gated |
| ASI (fixed/choice) | ✅ `:ability-increases` | ⚠️ inline-menu only | n/a | n/a |
| Prereqs | ✅ (limited vocab) | ❓ | ❓ | ❓ |
| Trackable resource | ❌ | ❌ | ❌ | ❌ |
| Custom/expanded spell list | ❌ | ❌ | ✅ `:spell-list` | ❓ |

**Reading:** **feats** are the richest silo (choices, ASI, prereqs, spell templates). **Classes**
own full spellcasting + custom lists. **Races/subraces** are nearly fixed-only. **Subclass spell
grants** are a hardcoded per-class thicket. **Resources** exist nowhere as a decision. This
asymmetry — *a feat can offer a choice/ASI-option/prereq but a custom race can't* — is the core
of the "cross-silo dipping" frustration.

## What's genuinely missing (the creator vision → gaps)
- **A general, cross-silo grant vocabulary**: `make-feat-selections` (choices) is feat-only;
  races/subraces/subclasses can't reach it. The cross-silo win is making the choice/ASI/prereq
  vocabulary available to *every* silo, not just feats.
- **A general parameterized spell-choice** (any list/levels/count/filter) instead of 3 fixed
  templates (feats) + full-caster (classes) + fixed-known (`:spells`).
- **A general subclass spell-grant** that isn't gated to 4 built-in class keys.
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
