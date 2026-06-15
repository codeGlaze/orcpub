# Homebrew Decision Vocabulary & Compile Paths

**The model (corrected):** the app is the **tools + forms + logic** (server-side, we build them).
An `.orcbrew` library stores the creator's **decisions** (data) — *not* logic. On import, the
app's **compile paths** read those decisions and build the actual modifiers/selections. So what
a creator can express is bounded by **(a) the forms** (input vocabulary) and **(b) the compile
paths** (what data the app knows how to turn into content). Growing those — and making them cheap
to grow, with cross-silo reach — is the goal.

This doc maps that vocabulary so we stop re-deriving it. **Verify before trusting** — each entry
below is checked against the code; `TODO`/`?` marks unverified.

> **STATUS: in progress.** Cycle 1 = the compile paths (done below). Next cycles: the builder
> *forms* (input vocabulary per silo), the caster/spellcasting path, prereqs/gating, and the
> cross-silo gaps. Intended to grow into real in-app documentation.

---

## Compile paths (load-time: decision data → content)

### 1. `:props` → `make-feat-modifiers` — the SHARED mechanical-grant vocabulary ✅ verified
- **File:** `options.cljc:3287` (`make-feat-modifiers`), driven by `plugin-modifiers` (`:3345`).
- **Used by (cross-silo!):** plugin races (`spell_subs.cljs:144`), subraces (`:157`), subclasses
  (`:447`), classes (`:481`), draconic ancestries (`:769`), and feats (via `feat-option-from-cfg`).
  Despite the name, it is **not** feat-specific — it's the common "fixed mechanical grant" path.
- **Decision shape:** an item's `:props` map `{:prop-key value}`.
- **Vocabulary it can compile (the whole `case`):**
  - movement: `:speed`, `:flying-speed`, `:flying-speed-equals-walking-speed`, `:swimming-speed`
  - proficiencies (map-valued, FIXED sets): `:skill-prof`, `:skill-prof-or-expertise`,
    `:tool-prof-or-expertise`, `:armor-prof`, `:weapon-prof`
  - defenses: `:damage-resistance`, `:damage-immunity`, `:saving-throw-advantage` (+ `-traps`)
  - misc: `:language`, `:initiative`, `:max-hp-bonus`, `:passive-investigation-5`,
    `:passive-perception-5`, the medium-armor-master pair, `:two-weapon-*`, `:lizardfolk-ac`,
    `:tortle-ac` (these last several are hardcoded one-off feat effects)
- **GAPS (not expressible via `:props`):** spells (any), ability-score increase (ASI),
  granting a feat, granting a resource (ki/rage/sorcery — Axis B), **choices** (every `:props`
  entry is a FIXED grant — "choose N skills" is not here), cross-silo *pulls* (grant an
  invocation / fighting style / subclass feature), and prereqs/gating.
- **Sustainability note:** adding a new *fixed* mechanic to the shared vocabulary = one `case`
  arm here + a form field. That's the cheap extension point — and it already reaches every silo.

### 2. `:spells` → `spell-modifiers` — FIXED known spells ✅ verified
- **File:** `spell_subs.cljs:124`. **Used by:** plugin races (`:146`), subraces (`:159`). (Other
  silos: `TODO` confirm feats/classes.)
- **Decision shape:** `:spells` = seq of `{:level L :value {:ability A :key spell-kw}}`.
- **Compiles to:** `mod5e/spells-known` per entry — i.e. "you know spell X" (fixed).
- **GAP:** no spell **choice** ("choose 1 cantrip from the wizard list"). Fixed grants only.

### 3. `:level-selections` → `level-selection` — TEXT-trait choices only ⚠️ verified (this is the gap)
- **File:** `spell_subs.cljs:341` (`level-selection`), via `make-level-selections` (`:358`).
- **Used by:** homebrew classes/subclasses (per-level selections).
- **Decision shape:** `{:type <homebrew-Selection-key> :level L :num N}`; the `:type` resolves to
  a homebrew **Selection** (the content type) whose `:options` are `{:name :description}`.
- **Compiles to:** a `selection-cfg` whose options are each `(mod5e/trait-cfg {:name :summary})`
  — **text traits only. Any mechanical data on an option is dropped** (it destructures just
  `{:keys [name description]}`).
- **GAP (load-bearing):** a homebrew "choice" can only offer **named descriptions**, not real
  grants. This is *not* a serialization limit — it's that this compile path was never built to
  turn a choice-decision into mechanics. Prime target for the cross-silo/grant work.

### 4. Caster spellcasting → `spellcasting-template` / `spells-known-selections` — TODO (next cycle)
- **File:** `options.cljc:697` / `:647`. Homebrew classes can declare `:spellcasting`. This IS a
  rich mechanical spell-CHOICE path (for casters). Need to verify exactly what a homebrew class's
  `:spellcasting` decision can express (known vs prepared, list, custom expanded list, cantrips),
  and whether subclasses can add an expanded spell list. **Unverified — do not rely yet.**

---

## Cross-silo reach — early read (verify next cycle)
- `:props` (path 1) is **already cross-silo**: the same vocabulary compiles for race/subrace/
  class/subclass/feat/ancestry. So "any silo grants any *fixed* mechanic" largely works **today**.
- What's *not* cross-silo / not expressible anywhere as a decision: spell **choices**, **ASI
  options** ("two stats / any / any-but-one"), granting a **feat**, granting a **resource**, and
  pulling from *another silo's pool* (e.g. a feat granting a warlock **invocation**, a subclass
  granting a **fighting style**). These need new vocabulary + compile logic.
- Prereqs/gating (alignment, level, N class levels): `option-prereq`/`:prereq-fn` exist in the
  engine; whether a *homebrew decision* can express one is `TODO`.

## Open gaps (the creator vision → what's missing)
| Creator wants | Path today | Status |
|---|---|---|
| Fixed mechanic (speed, prof, resist, language, …) | `:props` | ✅ works, cross-silo |
| Fixed known spell | `:spells` | ✅ works |
| **Spell choice** ("pick 1 cantrip from list") | — | ❌ missing (paths 3/4 don't cover homebrew) |
| **ASI options** (two stats / any / any-but-one) | — | ❌ missing (ASI is a source selection) |
| **Grant a feat** (background→feat) | — | ❌ `TODO` confirm |
| **Grant a resource** (ki/rage/sorcery) | — | ❌ missing (Axis B; not modeled) |
| **Cross-silo pull** (feat→invocation, subclass→fighting style) | — | ❌ missing |
| **Mechanical choice** (not text) | `:level-selections` | ⚠️ text-only |
| **Prereq/gate** (alignment/level/class-levels) | engine has `prereq-fn` | ❓ `TODO` for homebrew |

---

## Next cycles
1. Caster `:spellcasting` decision vocabulary (path 4) — verify fully.
2. The builder **forms** — what decisions each builder (race/feat/class/subclass/background/
   selection) actually lets a creator author (the input side of the vocabulary).
3. Prereqs/gating — can a homebrew decision carry one, and how is it compiled?
4. Resources (Axis B) — confirm there is no data path for trackable resources.
5. Synthesize into app-facing documentation.
