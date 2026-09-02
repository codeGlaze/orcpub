# Homebrew fighting-style authoring

**Branch:** `feature/fighting-style-authoring`, cut from `refactor/content-extensibility`.

**Status.** The **feat-grant** half is wired: a pack authors a `::e5/fighting-styles` item → it
folds into an open pool → a feat's `:grant {:from :fighting-styles}` offers it. This is the
"first real pool/grant expansion" the backfill-ledger watch-list scheduled — the hardcoded
`{:fighting-styles {… opt5e/fighting-style-options}}` block in `template.cljc` now draws from
`::classes5e/fighting-style-pool` (built-in ++ homebrew). Spec via `classes/homebrew-fighting-style`
(field-schema) + `content_specs` + `field_schemas`; classifier marks the type v2; demo item
`Demo: Tidewarden` + build tests. The **class-selection** half (a homebrew style appearing when a
Fighter/Paladin/Ranger picks their own fighting style) is DECIDED and in progress — below. Phase B
(in-app builder UI) remains.

## The divvying rule (decided) — which classes can take a homebrew style

A style declares `:classes #{:fighter :paladin …}` → eligible for exactly those. **Absent
`:classes` → eligible for all** fighting-style classes (the fallback). Built-in styles keep their
existing per-class key whitelist (Fighter all; Paladin/Ranger their sets) UNCHANGED (D29 — don't
churn proven behavior). So class C's choice = *(built-in filtered by C's whitelist)* ++
*(homebrew where no `:classes`, or C ∈ `:classes`)*. Field precedent: magic items already carry a
`:classes` class-restriction field.

## Verified findings — don't re-derive these

- **The extension hook is `opt5e/fighting-style-selection-2 [class-kw num options]`.** It takes an
  arbitrary options list, and EVERY fighting-style choice funnels through it — the built-in
  `fighting-style-selection`, the feat grant, and the class path all build their options and hand
  them here. Extend fighting styles by widening the options passed in, never by forking this.
- **Mariner is the old worked example** — `ua_base.cljc:690`, the `#_`-commented `mariner-class-option`.
  The pre-refactor way to add a UA style was a per-class `class-option` variant calling
  `fighting-style-selection-2` with a custom `[Mariner]` list. The pool + `:classes` eligibility
  SUBSUMES that (a homebrew Mariner just declares `:classes #{:fighter :paladin :ranger}`); reusing
  the per-variant shape would be a regression.
- **Conditional fighting styles ARE engine-expressible.** Mariner's "+1 AC only while unarmored / no
  shield" is `mod5e/ac-bonus-fn` with a predicate (`ua_base.cljc:701`). So the "`:props` covers only
  flat mechanics" limit is a DECLARATIVE-vocabulary gap, not an engine one: a bounded `:props` key
  compiling to `mod5e/ac-bonus-fn` would let authors write Mariner-class styles. (Same shape as the
  built-in Dueling's cljs-only condition.) Deferred unless prioritized.
- **The `:ref` distinction (a footgun a test caught).** A class's OWN fighting-style selection is
  top-level and MUST keep `:ref [:class class-kw :fighting-style]` — that's where the character
  stores the pick. A CROSS-SILO grant (a feat granting a style) carries NO top-level `:ref`: it
  re-roots the option path and zeroes the granted style's mechanic. Same pool, two selection shapes
  — don't unify them. (roadmap "verified finding"; D30.)

## Where it maps in the design record

The feat grant is the grant-matrix track (`grant-selection`, 4 modes ALL/FILTERED/SPECIFIC/CUSTOM;
`fighting_style_grant_matrix_test`), a **thin compiler** to `selection-cfg` (D30), the standard for
cross-silo grants (D29). The class path is D17(i): point the EXISTING constructor's `:options` at
the open pool, keeping `:ref`/`:tags` — NOT `grant-selection` (it drops the `:ref` the class needs).
Migration is D34-disciplined: characterize current per-class output first
(`fighting_style_class_characterization_test`), then thread the pool's homebrew entries through
`fighting-style-selection`'s vestigial `additional-options` param for Fighter/Paladin/Ranger, keep
green, tick the backfill-ledger watch-list item.

## Phase B — in-app builder (remaining)

The registry `:homebrew-builder?` entry in `content_types.cljc` (generates events/db/routes), the
builder view + the hand-wired `core.cljs` route→view binding, and the route-keyword def. Mirror the
draconic-ancestry builder trio.

## References

- Hook: `opt5e/fighting-style-selection-2` / `fighting-style-selection` (`options.cljc`).
- Old worked example: `ua_base.cljc:690` (Mariner, `#_`-commented).
- Pool: `::classes5e/fighting-style-pool` (`spell_subs.cljs`), `content_pools/pool`.
- Grant compiler: `opt5e/grant-selection` + `fighting_style_grant_matrix_test.cljc`.
- Decisions: D17 (no generic wrapper; point constructors at pools), D29 (one mechanism per job),
  D30 (grant = thin compiler), D34 + `backfill-ledger.md` (migration discipline).
