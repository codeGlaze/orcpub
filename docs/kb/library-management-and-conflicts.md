# Library management + import/export conflicts — status & backlog

Living ledger for the `feature/content-library-management` branch (forked from `develop`), so
nothing in flight is lost. Full design rationale lives in `content-tiers-and-key-resolution.md`
(arrives on develop via the ASI branch); this is the branch's own status list.

## Shipped (this branch)
- **Hide empty content-type categories** + data-drive the My Content list (one `my-content-types`
  table + an add-content menu for missing types). — `ec39303b`
- **Disabled-item visibility**: per-category "· N disabled", dimmed disabled rows, a source-level
  "show disabled (N)" toggle, and a per-source name search (categories self-hide when nothing
  matches). — `dc4d6ddc`
- **Import conflict popup: severity split** — loud amber warning for the *collapse* types
  (spells/races/classes/monsters/encounters/selections) where the winner is nondeterministic, quiet
  for the *pool* types where a duplicate merely shows twice; plus honest "keep both" labeling
  (was mislabeled "imported will override existing"). — `5be9b135`
- **Duplicate-key resolution — step 3, "keep both, turn one off."** Import the loser with
  `:disabled?` set (+ `:disabled-reason :compat`) so a chosen winner is deterministic; replaces plain
  "keep both" for the risky/collapse types. — `3d54a3b0`
- **Duplicate-key resolution — step 2, "rename the EXISTING one."** You can now keep the import's key
  and rename the already-loaded item instead of always renaming the newcomer; also fixes a stale
  `:key` field on rename that let a renamed item re-collide. — `68ac5ccb`
- **Internal keeper-picker + "Check my content for conflicts."** Handles the all-one-import / peer
  case (nothing loaded is base → user picks the keeper, default = first source alphabetically), and
  adds a My Content button that runs the same `correct-library` analysis on demand — the import popup
  never re-checks already-loaded libraries, where a random winner is silently in effect. — `c0054275`
- **Library-header disabled badges, color-coded by reason.** A pill next to each source name: blue
  "N off" for user-disabled (benign), amber "N off · compatibility" for items app-disabled to resolve
  a conflict (`:disabled-reason :compat`). Soft tinted pills by default, a `prefers-contrast` solid
  variant, and a light-theme re-tone. — `258bbb35`

## OPEN — UX direction (novice vs power user)  ← ACTIVE DISCUSSION
Concern: the conflict panel + the growing library controls are great for devs/mods but can
overwhelm tech-illiterate users. Direction under consideration (not yet decided):
- Make the conflict panel **opt-in, not a mandatory gate**. On import, auto-resolve with an
  opinionated *safe* default, then show a one-line summary + a "Review / change" link that opens the
  full panel for anyone who wants control.
- Safe default = severity-driven: **risky clash → import the newcomer DISABLED** (existing content
  untouched, deterministic, nothing breaks); **harmless clash → keep both**. The summary says so in
  plain language.
- Mirrors the EXISTING export-warning modal, which is already opinionated ("Export & Auto-Fix"
  primary + a hidden "export raw" dev hatch) → consistency, not a new pattern.
- The full panel (steps 2–3) becomes the **advanced / Review** view; power users + mods keep total
  control and analysis.
- DECISION: adopt opinionated-default + opt-in-panel? → **YES (agreed).** Is "import risky
  duplicates disabled" the right safe default? → leaning yes, pending the mutual-exclusion UX below.

### Mutual-exclusion UX — when one item is off because its same-key twin is on — ✅ SHIPPED `<pending>`
When two items share a key, only one may be enabled (the ≤1-enabled invariant). Now made legible,
not magic. All three parts landed; the whole thing is COMPUTED from a `collision-twin-index` over the
live plugins (canonical `collision-risk-types` + `twin-note` / `enabled-twin-paths` /
`mutual-exclusion-off-count` now live in `orcbrew-validation`, read by both events and views):
- **Computed, not stored.** A disabled item with an ENABLED same-key twin shows an inline note
  `off — "<twin>" in <source> is on`; the enabled winner shows `on — duplicate "<this>" in <source>
  is off`. Only fires for a genuine cross-source collision in a collision-risk type, never for a plain
  user-disable with no live twin. Derived at display time — no new storage.
- **Library-level summary.** A banner at the top of My Content: `N items off because a duplicate is
  on — [review]`; the review link opens the same conflict modal (`::e5/check-content-conflicts`).
- **Swap on enable — ordered + explained.** `::e5/toggle-plugin-item` is now exclusivity-aware:
  enabling a collision-risk item turns its live same-key twins OFF FIRST (marked `:disabled-reason
  :compat` → amber badge), THEN turns this ON, in one atomic `set-plugins`, with a success toast
  `Turned off "<twin>" (<src>) so "<this>" (<src>) is the one that's on.` Off-first keeps only-one-on
  true throughout, so the random winner never flickers. A user toggle also clears this item's
  `:disabled-reason` (once you choose, the app's earlier compat-disable no longer applies).
- DECISION (was: auto-swap vs confirm) → **auto-swap with an explanatory toast.** Low friction,
  matches the intuitive ordering; the toast + the recomputed notes make the swap self-evident, and the
  off item is one click from being restored, so no confirm gate was warranted.

## Disable hierarchy (agreed shape, not yet built) — FORMAT-SAFE
- Levels: **global** (overlay) / **source** (`:disabled?` in plugin data — exists) / **section**
  (new) / **item** (`:disabled?` in plugin data — exists).
- Global + section live in a **local overlay store** (db/localStorage), NOT in the plugin/`.orcbrew`
  data — so zero format/spec change and existing libraries are untouched. `plugin-vals` ORs all four
  when filtering. Show effective (inherited) disabled state (dim descendants of a disabled ancestor).
- Trade-off: section/global disable is a local "view" preference and does NOT travel with exported
  packs (source/item disable still do). Section-disable must NOT be stored inside the content-type
  map — a `:disabled?` there fails `::plugin` (value must be an item) and quarantines the source.

## Parked ideas (with reasons — do not lose)
- **Account backup/restore** of libraries + prefs: blocked NOT by code (the `share_url` codec already
  does gzip + fail-closed decode) but by **legal** (hosting user-uploaded, often copyrighted content)
  + **database/scale** (3–5 MB × every user) + standing **admin resistance**. If ever entertained:
  server-side at-rest/transport encryption, NOT end-to-end (E2E = lost key → lost backup); and
  backup-restore (last-write-wins) *before* multi-device sync (sync needs conflict resolution).
- **Compress localStorage** plugins with the existing gzip codec to fit more under the ~5 MB browser
  ceiling — no cloud, no legal exposure. Caveats: makes stored content opaque to inspection, and a
  hard cap is still needed (compression moves the ceiling, doesn't remove it).
- **Example/demo content tier** + per-account version marker + copy-on-edit graduation (full design
  in `content-tiers-and-key-resolution.md`).
- **Move/copy content between sources** — reuses `detect-duplicate-keys` / `apply-key-renames` for
  collisions.
- **Native `<select>` → custom popover**: the add-content menu uses a native select; adopt
  `port/redesign-on-refactor`'s Phase 7 custom select popover when branches converge (NOT a cheap
  early crib — it's coupled to that branch's theme-token infrastructure).
- **Nondeterministic-override** is a latent bug affecting users today (winner = source-name hash
  order); the disable-based resolution fixes it. Worth its own issue.
