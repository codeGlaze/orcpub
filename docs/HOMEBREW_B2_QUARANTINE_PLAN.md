# B2 — Quarantine-for-repair: plan + foundation audit

> **STATUS: implemented (B2.0–B2.5).** This is the pre-build plan + foundation
> audit; all of it shipped. Kept as the design record — read in past tense. See
> `docs/HOMEBREW_REMEDIATION_ROADMAP.md` for what landed.
>
> Goal (from the roadmap): a homebrew source that fails validation on load is
> **preserved, surfaced with a humanized reason, and repairable in-app** so the
> fix merges back into the live library — never silently discarded.
>
> This note does two things the user asked for before we write code:
> 1. **Plan** — what we reuse vs. build (so B2 is mostly wiring).
> 2. **Foundation audit** — what in the code we'd build on is solid vs. shaky,
>    so we firm up the cracks *first* instead of stacking B2 on them.

---

## Part 1 — The plan (reuse map)

A surprising amount of remediation UI already exists (landed with the
import/orcbrew-validation merge). B2 is largely repointing it at a new data
source.

| B2 need | Reuse | Where |
|---|---|---|
| Slide-out surface + floating badge | `import-log-panel`, `import-log-button` | `views/import_log.cljs:142,267` |
| Collapsible groups | `collapsible-section` | `views/import_log.cljs:120` |
| Humanized reason renderer (icon + text per cause) | `format-change-item` (add a `:quarantined` case) | `views/import_log.cljs:14` |
| Inline per-field editors (text + dropdowns) | `field-editor`, `item-issue-editor`, `plugin-issues-section` | `views/conflict_resolution.cljs:159,207,243` |
| Per-item decision pattern (rename/keep/skip) | `conflict-resolution-modal`, `radio-option` | `views/conflict_resolution.cljs:8,94` |
| Single mount point | `import-log-overlay` | `views/conflict_resolution.cljs:347` |
| Salvage decision (kept vs rejected) | `salvage-plugins` (pure, tested) | `dnd/e5.cljc:30` |

**Data already half-built:** the resilient loader already *writes*
`localStorage["plugins:rejected"]` (`db.cljs`). The **read/surface/repair** side does not exist — no sub,
event, or UI touches `plugins:rejected`. That absence *is* the B2 gap.

### New pieces B2 genuinely needs (small, once the audit fixes below land)
- A cofx + sub to read `plugins:rejected`, **reconciled** against current
  `:plugins` (drop entries that are now present-and-valid).
- A quarantine section in the panel (reusing the shells above).
- A **repair event** that applies the user's fix, **re-keys** if needed, writes
  into `:plugins` (and persists), and removes the entry from quarantine —
  atomically.
- Live validation in the repair editor (reuse `common/starts-with-letter?` +
  the new `spec-field-problems` location messages).
- An "export raw" per-source escape hatch (reuse `::e5/emergency-export-raw`).

---

## Part 2 — Foundation audit (build on rock, not sand)

Severity: **HIGH** = fix before B2; **MED** = fix as part of B2; **LOW** = note.

### F1 (HIGH, design-blocking) — the existing "merge-back" never touches the library
`:export-with-auto-fix` (`events.cljs:4017`) runs `apply-user-edits-to-plugin`
and writes the result to a **file** via `save-orcbrew-blob!`. Its `:db` change
only closes the modal and writes the import-log — **app-db `:plugins` is left
untouched.** So the inline-edit flow we hoped to reuse as "fix-and-merge-back"
actually only fixes the *exported file*, not the stored library.
→ **B2 must add a real persist-to-library event** (apply edits → `:plugins` →
localStorage). We reuse the *transform* and the *editor*, not the wiring.

### F2 (HIGH, design-blocking) — `apply-user-edits-to-plugin` can't re-key
`apply-user-edits-to-plugin` (`orcbrew_validation.cljs:949`) edits the `:name`
field via `assoc-in [content-type item-key :name]` but never renames the **map
key**. The dominant quarantine cause is the keyword trap: the item is stored
under a key derived from a bad name (`:9-lives`), and `::e5/plugin` rejects the
*key* (`keyword-starts-with-letter?`), not just the name. Fixing the name leaves
the key invalid → still rejected.
→ **B2 needs a re-key primitive**: derive the new key via `common/name-to-kw`,
move the item to the new key, drop the old, and resolve collisions. A partial
rename already exists in the conflict path (`:rename-import` →`:new-key`,
`events.cljs:4443`) — extract/generalize it rather than writing a third copy.

### F3 (MED) — editors handle MISSING fields, not present-but-INVALID values
`field-editor` (`conflict_resolution.cljs:159`) and the whole export-warning
flow are built around `missing-fields`. The keyword trap is an *invalid* value,
not a missing one, so `fill-missing-for-export` won't touch it and the editor
has no "this value is invalid, here's why" path.
→ B2 editor reuses the input shells but adds an invalid-value mode with **live
validation** (the meaningful-errors work just landed gives us the predicates and
the location-aware messages).

### F4 (MED) — `plugins:rejected` is overwritten, not keyed; never cleared
The loader writes the *whole* rejected set of the current load
(`db.cljs:357,368`) and only `(when (seq rejected))` — so it's never cleared
when everything becomes valid again, leaving **stale "ghost" quarantine
entries**. The roadmap's decided model is a **name-keyed map merged
latest-wins**.
→ Firm up before surfacing, or the panel shows ghosts: write a name-keyed map,
and on read **reconcile** against `:plugins` (drop entries now present-and-valid)
+ clear when empty.

### F5 (HIGH) — unreadable storage is DELETED, defeating "never destroy data"
`get-local-storage-item` (`db.cljs:250-256`) calls `reader/read-string` and, on
any parse error, **removes the key**. For `plugins` / `plugins:rejected`,
a truncated or corrupt string (e.g. a quota-cut write — see S4)
throws → the data is **deleted before the resilient loader ever sees it to
preserve.** This is a silent data-loss hole in the exact safety net B2 relies
on, and it contradicts the project's first principle.
→ For the plugin slots, on parse failure **preserve the raw string** (to a
`:corrupt` slot) instead of deleting. Add a unit test with a deliberately
truncated blob.

### F6 (LOW–MED) — bad sources live in BOTH `plugins` and `plugins:rejected`
The loader loads `kept` into app-db but never rewrites `localStorage["plugins"]`,
so the bad sources linger there until the next save (when `set-plugins` persists
app-db `:plugins` = kept, dropping them). Until then the bad data is duplicated;
after a save its only home is `plugins:rejected`.
→ Implication: **`plugins:rejected` must be the canonical source for the
quarantine UI** (not the lingering copy in `plugins`), and repair must write
`:plugins` + clear the rejected entry together so the two never disagree.

### F7 (MED) — zero coverage on the read/repair path
Only the *write* side (`salvage-plugins`) is unit-tested. No test exercises
reading `plugins:rejected`, reconciling it, or repairing. Per project DoD, B2
lands with an e2e: bad source A → reload with bad source B → both surfaced →
repair A in the panel (incl. a re-key) → A moves to My Content and exports → B
still quarantined.

---

## What IS solid (safe to trust)
- `salvage-plugins` — pure, tested, e2e-proven; the kept/rejected split is sound.
- The resilient loader's *decision* (keep valid, quarantine invalid) — proven by
  `boot-resilience` / `resilient-loader` scenarios.
- `str` ⇄ `reader/read-string` round-trip for **collections** — fine (the
  original str bug was a type error — a string reaching a `map?` check — not an
  EDN-readability problem; collection `str` prints readably and the everyday
  save/reload path depends on it).
- The panel / collapsible / reason-renderer UI shells — solid; they only need a
  new data source.
- `name-to-kw` + the new location-aware `spec-field-problems` — solid; reuse for
  live repair validation.

---

## Recommended sequence
Tracked as the **B2 sub-roadmap** in `docs/HOMEBREW_REMEDIATION_ROADMAP.md`
(phase IDs below). Each step is a DoD-complete slice (e2e where user-visible,
unit otherwise) so we never stack B2 features on an unproven layer.

1. **B2.0** — firm the foundation: don't delete unreadable plugin slots (**F5**).
2. **B2.1** — name-keyed, reconciled, self-clearing `plugins:rejected` (**F4**).
3. **B2.2** — extract the re-key primitive from the conflict-rename path (**F2**);
   unit-test incl. collision + dedup.
4. **B2.3** — persist-to-library repair event (**F1**) reusing
   `apply-user-edits-to-plugin` + the re-key primitive.
5. **B2.4** — surface quarantine in the panel (reuse shells) with live-validated
   editors (**F3**) and the raw-export hatch.
6. **B2.5** — e2e the full loop (**F7**).
