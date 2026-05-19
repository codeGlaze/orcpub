# Audit: `name-to-kw` — Keep, Harden, or Replace

> **Status:** Stage 1 audit complete. Decision document for the repo owner.
> **Scope:** Why `common/name-to-kw` exists, every dependency it feeds, the
> snares it creates, and a cost/benefit verdict on keeping vs. hardening vs.
> replacing it.
> **This document changes no code.** Implementation is a separate, approved
> stage.

---

## 0. Reading conventions

- Every factual claim carries a `file:line` reference.
- Claims that could not be fully proven from code are tagged **`[UNVERIFIED]`**
  with the reason. Do not treat tagged claims as settled.
- "SRD content" = the D&D 5e content hardcoded into the app. "Plugin content" =
  homebrew/orcbrew content loaded at runtime.

---

## 1. What `name-to-kw` is

`src/cljc/orcpub/common.cljc:8-20`:

```clojure
(defn- name-to-kw-aux [name ns]
  (when (string? name)
    (as-> name $
        (s/lower-case $)        ; "Half-Elf"  -> "half-elf"
        (s/replace $ #"'" "")   ; strip apostrophes
        (s/replace $ #"\W" "-") ; non-word chars -> "-"
        (s/replace $ #"\-+" "-"); collapse runs of "-"
        (keyword ns $))))

(def memoized-name-to-kw (memoize name-to-kw-aux))

(defn name-to-kw [name & [ns]]
  (memoized-name-to-kw name ns))
```

It converts a human display string into a normalized keyword. It is **lossy and
non-invertible** (see §7.1).

Siblings in the same file:

- `kw-to-name` (`common.cljc:22-28`) — the inverse direction, keyword → string,
  used purely for UI display. **Not a true inverse** (see §7.1).
- `add-keys` / `add-keys-xform` (`common.cljc:119-124`) — bulk-applies
  `:key (name-to-kw (:name %))` to a collection of maps.

There is also a **second, divergent copy**: `entity.cljc:701-705` defines its
own `name-to-kw` (no apostrophe-strip, no dash-collapse, no `ns` arg). It has
**zero callers** — confirmed dead code (see §7.4).

---

## 2. History — it is original, not recent

**Verified.** At the repository's earliest history root (`d48f4a2`, 2019),
`common.cljc` already contains the **byte-identical** `name-to-kw` — memoization
wrapper, `ns` argument, apostrophe-strip and all.

`git log -S"name-to-kw"` and `git blame` attribute the function to commit
`5817663` (Feb 2026). **This attribution is an artifact.** `5817663` is a
654-file / 89,932-insertion squash/re-import commit; `git`'s `-S` pickaxe and
`--diff-filter=A` simply report the file as "added" there. The function
predates it and is original OrcPub2 code (~2017 upstream).

**Correction to a prior analysis:** any claim that `name-to-kw` was "introduced
in February 2026" is wrong and is caused by this squash commit.

What *is* genuinely recent is `content_reconciliation.cljs` (the
"Add import validation and content reconciliation" feature, commit `b8434a2`,
early 2026) and the `f9015b1` "Fix for multiclass and Human subraces" patch.
That feature is the source of the new wide-scale string manipulation on
keys/names — and it is where the pain is concentrated (see §7.3).

---

## 3. Why it exists — the design rationale

`name-to-kw` is **not** a class-key mechanism. Its reason for existing is one
line, repeated twice, in the template system:

`template.cljc:40` (`selection-cfg`) and `template.cljc:77` (`option-cfg`):

```clojure
::key (or key (common/name-to-kw name))
```

This is the **default key-derivation rule for the entire
template / selection / option tree**. The contract is:

> A content author may write `{:name "..."}` and omit `:key`. The template
> system will synthesize a stable `::key` from the name.

This is an **authoring-ergonomics / DRY mechanism**. The D&D 5e content set is
huge — hundreds of selections and thousands of options (feats, backgrounds,
languages, conditions, alignments, tool proficiencies, spells, items,
monsters). Requiring an explicit hand-assigned keyword on every entry would be
enormous boilerplate, and a hand-typed keyword that disagrees with its name
would be a silent bug. `name-to-kw` makes the name the single source of truth
and derives the key deterministically.

**Crucially, `(or key ...)` means the explicit key always wins.** Any content
entry *may* declare `:key` and bypass derivation entirely. The codebase uses
both modes — see §4.

---

## 4. Classes specifically — they do *not* depend on `name-to-kw`

This matters because the triggering incident involved class names.

- **Built-in classes have explicit `:key`s.** `classes.cljc` defines every SRD
  class with `:key :barbarian`, `:key :wizard`, etc. (e.g.
  `classes.cljc` `barbarian-option` → `{:name "Barbarian" :key :barbarian ...}`).
- **Homebrew classes get `:key` from their plugin map-key**, not from the name.
  `spell_subs.cljs` (~line 468) does `class-with-key (assoc class :key class-key)`
  where `class-key` is the key under which the class sits in the plugin's
  `::e5/classes` map. Comment in that file: *"the map key is the authoritative
  key … All internal lookups use :key, never :name."*
- **Subclasses** carry both an explicit `:key` and an explicit `:class`
  (parent-class keyword) — `import_validation.cljs` `key-reference-map` treats
  `:class` as a cross-reference field.
- **Entity storage** of a chosen class is `{::entity/key class-key ...}`
  (`character.cljc` `set-class`). Multiclass level lookups use raw keywords
  (`template_base.cljc` `(?class-level :barbarian)` …).

**So classes are already on the explicit-key model.** The friction is not in
class definitions — it is in the *machinery around classes* that still assumes
keys can be re-derived from names (§7.1, §7.2, §7.3).

---

## 5. Call-site inventory

`name-to-kw` has **~40 live call sites**; `kw-to-name` has **~23** (UI display
only); `add-keys` is used once. Two call sites in dead/`#_`-discarded blocks
are excluded (`classes.cljc:375`, `options.cljc:397`).

### 5.1 Core — the derivation default (3 sites)

| Site | Role |
|------|------|
| `template.cljc:40` | `selection-cfg` — derive `::key` for a selection when none given |
| `template.cljc:77` | `option-cfg` — derive `::key` for an option when none given |
| `common.cljc:121`  | `add-keys-xform` — bulk `:key` assignment |

### 5.2 SRD content-key derivation (~18 sites)

These run at app-build time, turning hardcoded content names into keys.

| Site | Content |
|------|---------|
| `options.cljc:51`   | alignment key (`"Lawful Good"` → `:lawful-good`) |
| `options.cljc:469`  | spell-selection key from title — also feeds a `:ref` path |
| `options.cljc:606`  | bard spell option key (fallback after `:key`) |
| `options.cljc:635`  | `class-key-name` fallback — **snare, §7.1** |
| `options.cljc:1063` | feat key from feat name |
| `options.cljc:2232` | race option key (fallback) |
| `options.cljc:2266` | language → modifier argument |
| `options.cljc:2302` | tool-proficiency composite key |
| `options.cljc:2474` | background key |
| `options.cljc:2560` | subclass option key (fallback) |
| `options.cljc:2801` | subclass-selection key from archetype title |
| `options.cljc:2889` | class option key (fallback) |
| `classes.cljc:2746` | ritual-spell key (fallback after `:key`) |
| `template_base.cljc:275` | class **name** → key for spell-slot lookup — **snare, §7.2** |
| `magic_items.cljc:2955/3009/3056` | magic item / weapon / armor keys |
| `monsters.cljc:9270` | monster key (`(:refer)`s `common/name-to-kw`) |
| `spells.cljc:4197`  | spell key (fallback after `:key`) |
| `ua_base.cljc:58`   | mystic psionic-discipline `:ref` path segment |

### 5.3 Homebrew / import / dedup (~13 sites)

These run on **user-supplied** strings — the one place name→key derivation is
genuinely unavoidable, since imported content may arrive without keys.

| Site | Role |
|------|------|
| `events.cljs:543`        | homebrew spell save → plugin map key |
| `events.cljs:628`        | homebrew selection save → key |
| `events.cljs:638/645`    | duplicate-option detection within a selection |
| `events.cljs:2284`       | search text → key |
| `import_validation.cljs:146` | similarity matching by derived key |
| `import_validation.cljs:370` | dedup grouping of options |
| `import_validation.cljs:408` | renamed (de-duplicated) option key |
| `import_validation.cljs:1393`| source-name → slug |
| `content_reconciliation.cljs:146` | name-based similarity matching |
| `character_builder.cljs:439` | custom equipment item key |
| `spell_subs.cljs:1209`   | spell-map key (fallback) |
| `views.cljs:6409/6423/6468` | duplicate-option-name highlighting |
| `views.cljs:3865`        | spell-page lookup from a URL key string |

### 5.4 Random-name generation — the only namespaced calls (4 sites)

`events.cljs:1258-1260` and `event_handlers.cljc:145` pass the `ns` argument
`"orcpub.dnd.e5.character.random"` to look up race/subrace/sex name tables.
These are the **only** users of the `ns` parameter.

---

## 6. Persistence — why "just change the keys" is dangerous

**Verified.** A character entity stores chosen options as a tree of
`{::entity/key … ::entity/options …}` maps (`entity.cljc:13-19` spec). When the
entity is written to Datomic it is converted to "strict" form by
`to-strict-option` (`entity.cljc:94-100`):

```clojure
key (assoc ::strict/key key)
```

`to-strict-selections` (`entity.cljc`, just above) does the same for selection
nodes: `(cond-> {::strict/key k} …)` — the **selection** map-key is persisted
too. So **both kinds of key are stored**:

- **Option keys** — the `::entity/key` of each chosen option (subrace, feat,
  background, language, alignment, tool prof, custom item …). Name-derived
  whenever the option's `option-cfg` had no explicit `:key`.
- **Selection keys** — the map-keys under `::entity/options` (e.g. a
  spell-selection key, a subclass-archetype selection key). Name-derived
  whenever the `selection-cfg` had no explicit `:key` — these are the
  *second-order* keys derived from a title/name string at build time.

**What does and does not require a data migration — the key distinction:**

- **Changing the *mechanism* but not the *value* — no migration.** If a
  second-order key is a pure function of an authoritative first-order key (e.g.
  the class `:key`), then *threading that key through* instead of re-deriving
  it from a display string yields the **byte-identical** result for every case
  that is currently correct. Nothing persisted changes. The only outputs that
  change are the ones that were *already wrong* — homebrew classes whose name
  does not round-trip to their `:key` (§7.2), where the old re-derivation
  produced a key that already failed to resolve. This is the §7.1/§7.2 fix and
  it is **migration-free**.
- **Changing a key *value* — migration required.** Renaming a persisted key, or
  assigning an explicit `:key` that differs from the value `name-to-kw`
  previously produced, orphans the stored `::strict/key` in every saved
  character that used it. The rebuilt template no longer has a matching `::key`.

`content_reconciliation.cljs` exists (see `docs/CONTENT_RECONCILIATION.md`) to
detect a character whose stored keys no longer resolve. **`[UNVERIFIED]`** —
reconciliation currently targets *missing homebrew* content; whether it would
also catch/repair SRD options orphaned by a deliberate key-*value* change has
not been confirmed and should be tested before any work that renames keys.

---

## 7. The snares — where the pain actually is

### 7.1 `class-key-name` returns two different types

`options.cljc:632-638`:

```clojure
(defn class-key-name [cls-key cls-nm]
  (if cls-key
    (name cls-key)                 ; STRING  e.g. "barbarian"
    (common/name-to-kw cls-nm)))   ; KEYWORD e.g. :barbarian

(defn spell-selection-key [cls-key-nm]
  (keyword (str cls-key-nm "-spells-known")))
```

`class-key-name` returns a **string** on the `:key`-present branch but a
**keyword** on the fallback branch. `spell-selection-key` then does
`(keyword (str …))`:

- string in: `(str "barbarian" "-spells-known")` → `"barbarian-spells-known"`
  → `:barbarian-spells-known` ✅
- keyword in: `(str :barbarian "-spells-known")` → `":barbarian-spells-known"`
  (note the leading colon) → `(keyword ":barbarian-spells-known")` → a keyword
  whose *name literally begins with a colon* ❌

This is a textbook "dependent property already stringified / makes assumptions"
trap — the consumer assumes a string and `str`-concatenates it.

**`[UNVERIFIED — likely unreachable today]`** the only caller,
`spells-known-selections` (`options.cljc:680`), passes `(:key cls-cfg)`; SRD
classes always have `:key` and homebrew classes are assigned `:key` from their
map-key (§4), so the keyword branch is probably never hit in practice. It
remains a live landmine: any future code path that builds a `cls-cfg` without
`:key` produces a malformed keyword with no error.

### 7.2 `?prepare-spell-count` re-derives a class key from its name

`template_base.cljc:274-284`:

```clojure
?prepare-spell-count (fn [class-name]
                       (let [class-kw (common/name-to-kw class-name)
                             slot-factor (get ?spell-slot-factors class-kw) ...
```

This takes a class **display name** and reconstructs the class key with
`name-to-kw` to index `?spell-slot-factors`. It holds **only when the class key
equals `name-to-kw` of the class name**:

- SRD: `"Wizard"` → `:wizard` = the real `:key` ✅
- Homebrew: a class displayed as `"Artificer (Kibbles Tasty's Version)"` whose
  real `:key` is `:artificer-kibbles-tasty` derives to
  `:artificer-kibbles-tastys-version` (apostrophe stripped) → **no match** →
  `slot-factor` is `nil` → prepared-spell count silently wrong.

The class already carries its real `:key`; this function should be threaded the
key rather than rebuilding it from the name.

### 7.3 `content_reconciliation` hand-maintains mirrors of `name-to-kw` output

`content_reconciliation.cljs:163-198` defines `builtin-classes`,
`builtin-races`, `builtin-subraces`, `builtin-subclasses`,
`builtin-backgrounds`, `builtin-feats` — hardcoded sets of SRD keys used to
suppress false "missing content" warnings (the SRD content is not in the plugin
subscription, so without these sets it would be flagged as missing — see
`docs/CONTENT_RECONCILIATION.md`).

The module's own comment (`content_reconciliation.cljs:171-173`) states the
problem outright:

> *"PHB subrace keys auto-generated from their names via common/name-to-kw …
> defined in spell_subs.cljs with only :name, so their keys are derived from
> the name."*

The maintainer had to **manually run `name-to-kw` in their head** for every SRD
subrace/feat/background and hardcode the results. The `f9015b1` patch is exactly
this: it expanded `builtin-subraces` with `:calishite`, `:chondathan`, …,
`:variant-human` — nine human cultural variants plus variant-human options —
all of which exist *only* as `{:name "..."}` in `spell_subs.cljs` and whose keys
are produced by the §3 derivation default.

This is the structural cost: **the derived-key scheme has no machine-readable
registry**, so any code that needs to enumerate "the set of SRD keys" must
re-derive or hardcode them, and that mirror silently rots whenever content is
added or renamed.

### 7.4 Dead, divergent duplicate in `entity.cljc`

`entity.cljc:701-705` defines a second `name-to-kw` with a *different* algorithm
(no apostrophe-strip, no dash-collapse, no `ns`). **Verified: zero callers.**
`monsters.cljc:4` `:refer`s `name-to-kw` from `orcpub.common`, not this one. It
is pure dead code — but it is a trap for anyone grepping `name-to-kw`, and if
ever wired up it would produce keys inconsistent with the rest of the app.

---

## 8. Dependency trains (summary)

```
name-to-kw
 ├─ template.cljc selection-cfg/option-cfg  ──► ::key on every key-less option
 │     └─ persisted into Datomic as ::strict/key  (§6 — migration hazard)
 │     └─ stored in character ::entity/options    (§6)
 │           └─ content_reconciliation reads these back  ──► builtin-* sets (§7.3)
 ├─ options.cljc class-key-name ──► spell-selection-key ──► (keyword (str …)) (§7.1)
 ├─ template_base.cljc ?prepare-spell-count ──► ?spell-slot-factors lookup (§7.2)
 ├─ SRD content build (alignments, feats, backgrounds, languages, items,
 │     monsters, spells) — ~18 sites, mostly benign
 ├─ homebrew/import — ~13 sites — derivation genuinely required here
 └─ random-name generation — 4 sites — namespaced, isolated, benign
```

The **benign** majority (SRD build, homebrew import, random names) is ~35 of
~40 sites. The **damaging** part is the small set where a key is *re-derived*
from a name that may not round-trip (§7.1, §7.2) and where the derived-key set
must be *mirrored by hand* (§7.3).

---

## 9. Options weighed

### Option A — Keep `name-to-kw`, change nothing

- **Cost:** zero.
- **Risk:** zero new risk; the §7 snares remain and will keep biting whoever
  touches class/spell-selection or reconciliation code.
- **Verdict:** rejected — it does not address the reported pain.

### Option B — Keep `name-to-kw`, harden the snares  ★ recommended

Targeted fixes, no behavioral change to the derivation contract:

1. **`class-key-name` (§7.1):** make it return one type. Simplest:
   `(defn class-key-name [cls-key cls-nm] (name (or cls-key (common/name-to-kw cls-nm))))`
   so `spell-selection-key` always concatenates a plain string.
2. **`?prepare-spell-count` (§7.2):** thread the class **key** in instead of the
   name, or look the key up from the class config rather than re-deriving it.
3. **`builtin-*` sets (§7.3):** replace the six hand-maintained sets with a
   single source of truth — derive them at load time from the actual SRD
   template/option data (the same data `spell_subs.cljs` already builds), so the
   "what is SRD" set can never drift from what `name-to-kw` produces.
4. **`entity.cljc:701` (§7.4):** delete the dead divergent copy.

- **Cost:** small, localized; ~4 files; no schema or data change.
- **Risk:** low, and **provably migration-free.** Fixes 1 and 2 only change
  *how* a second-order key is obtained — by threading the authoritative key in
  rather than re-deriving it from a display string. Per §6, that yields the
  byte-identical key for every currently-correct case; the only outputs that
  change are homebrew cases that were already producing a non-resolving key. No
  saved character with valid data is touched.
- **Benefit:** eliminates every snare in §7, including the `f9015b1`-class of
  manual-mirror maintenance that triggered this audit.

### Option C — Replace name-derivation with explicit `:key` everywhere

Make `:key` mandatory on all content and drop the `(or key (name-to-kw name))`
default.

- **Cost — three-front:**
  1. Assign explicit keys to *all* key-less SRD content (the ~18 §5.2 sites'
     worth of data — alignments, feats, backgrounds, languages, conditions,
     subraces, tool profs, items, monsters, spells). Large but mechanical.
  2. **A data migration of every saved character in Datomic — but only if key
     *values* change** (§6). If C is done conservatively, freezing each new
     explicit `:key` to *exactly* the value `name-to-kw` currently produces,
     it is migration-free — it merely inlines the derivation. A migration
     becomes mandatory the moment C is used to *rename* any key to something
     nicer; then every saved character that stored the old value must be
     remapped or it breaks. The temptation to clean up ugly derived keys is
     precisely what makes C risky in practice.
  3. `name-to-kw` **still cannot be deleted** — homebrew/orcbrew import (§5.3)
     receives user content that legitimately may lack keys, and random-name
     generation (§5.4) needs string→keyword. So Option C removes the *default*
     but not the *function*.
- **Risk:** medium-to-high — bounded if values are frozen, severe if a wrong
  rename-migration silently corrupts user characters.
- **Benefit over B:** marginal. The §7 snares are all fixable without C; C's
  only unique gain is a machine-enforced "every key is explicit" invariant.
- **Verdict:** not justified by the evidence. The conservative (migration-free)
  form of C is just B plus a large mechanical edit; the aggressive form buys a
  cleaner key namespace at the price of a character-data migration. Neither is
  worth it now.

---

## 10. Recommendation

**Adopt Option B — keep `name-to-kw`, harden the four snares.**

Rationale, grounded in the trace:

- The function's purpose (§3) is sound and load-bearing; ~35 of ~40 call sites
  are benign or genuinely require name→key derivation.
- The reported pain is **not** caused by `name-to-kw` itself but by a handful
  of consumers that *re-derive* keys from names (§7.1, §7.2) or *mirror* the
  derived-key set by hand (§7.3). Those are local defects, fixable in place.
- Option C's headline benefit — "explicit keys everywhere" — is already true
  for the part that matters (classes, §4), and buying it for the rest costs a
  full character-data migration (§6) for marginal gain, while still not
  letting you delete the function.

**Guiding principle for the fix:** *a second-order key (a key built from
another key — spell-selection keys, archetype-selection keys, slot-factor
lookups) must be computed from the authoritative first-order `:key`, threaded
in as an argument. Never reconstruct it by running `name-to-kw` on a display
string at the call site.* Because the first-order key does not change, every
threaded second-order key is identical to today's value — so the whole
punch-list is migration-free (§6, Option B).

**Punch-list for the Stage 2 implementation branch** (each independently
shippable):

1. `options.cljc` — normalize `class-key-name` return type. *(low risk)*
2. `template_base.cljc` — pass the class key into `?prepare-spell-count`.
   *(low risk; fixes a real homebrew prepared-caster bug)*
3. `content_reconciliation.cljs` — derive the `builtin-*` sets from SRD data
   instead of hardcoding. *(medium risk; needs a test that a fresh SRD
   character produces zero false "missing content" warnings)*
4. `entity.cljc` — delete the dead `name-to-kw` (lines 701-705). *(trivial)*

Do **not** undertake Option C unless a future requirement (e.g. the 2024 SRD
mix-and-match work — see `docs/kb/srd-2024-integration.md`) forces a key-scheme
change; if it does, the §6 migration must be designed first.

---

## 11. Appendix

### 11.1 `[UNVERIFIED]` items to close before any Option C work

- Exact breadth of saved-entity paths whose keys are name-derived, and whether
  `content_reconciliation` would detect SRD options orphaned by a key change
  (§6).
- Whether `class-key-name`'s keyword branch is truly unreachable at runtime, or
  reachable via some homebrew path that yields a `cls-cfg` without `:key`
  (§7.1).

### 11.2 Key references

- Definition: `src/cljc/orcpub/common.cljc:8-20`
- Derivation default: `src/cljc/orcpub/template.cljc:40,77`
- Persistence: `src/cljc/orcpub/entity.cljc:94-100`
- Snares: `src/cljc/orcpub/dnd/e5/options.cljc:632-638`,
  `src/cljc/orcpub/dnd/e5/template_base.cljc:274-284`,
  `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs:163-198`,
  `src/cljc/orcpub/entity.cljc:701-705`
- Related docs: `docs/CONTENT_RECONCILIATION.md`,
  `docs/kb/srd-2024-integration.md`, `docs/kb/entity-options-architecture.md`

### 11.3 Method

Three parallel exploration passes: full `name-to-kw`/`kw-to-name`/`add-keys`
call-site sweep across `src/` and `web/`; class data-model trace; git-history
trace. Findings cross-checked by direct file reads. The git-history pass
initially mis-dated the function to 2026; that was caught and corrected against
the 2019 history root (§2).
