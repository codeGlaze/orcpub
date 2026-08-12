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

## In flight — duplicate-key resolution (steps 2–3 re-open the deliberately-parked edge cases)
- **Step 2 — pick which side keeps the key.** Today you can only rename the *import*; the existing
  item always stays base. Add "rename the existing one instead" (mod's "decide what stays base"),
  and handle the *internal/peer* case (all one import, nothing loaded → nothing is base → user picks;
  default keeper = first source alphabetically, overridable). Touches the decision model + apply
  logic (rename an EXISTING plugin item, not just import-data).
- **Step 3 — "keep both, turn one off."** Import the loser with `:disabled?` set so a chosen winner
  is deterministic (reuses the disable mechanism). Replaces plain "keep both" for risky types.
- **"Check my content for conflicts" button** in My Content — reuses export's existing analysis
  (`correct-library` / `detect-duplicate-keys`) on demand, because the import popup never re-checks
  already-loaded libraries (where the random winner is silently in effect today).
- Open Qs: internal keeper default (first-alphabetical, override) — OK'd in principle; off-state for
  "keep both, turn one off" on import = loser imported with `:disabled?` (clean).

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

### Mutual-exclusion UX — when one item is off because its same-key twin is on
When two items share a key, only one may be enabled (the ≤1-enabled invariant). Make that legible,
not magic:
- **Computed, not stored.** A disabled item that has an ENABLED twin sharing its key shows an inline
  note "off because <twin> (in <source>) is on"; the enabled twin shows "on — its duplicate <this>
  (in <source>) is off." Only shows when a same-key enabled sibling exists (a real conflict), NOT for
  plain user-disabled items. Derived at display time — no new storage.
- **Library-level summary.** A note in the main homebrew / My Content area: "N items are off because
  a duplicate is on — [review]."
- **Swap on enable — ordered + explained.** Clicking enable on the off twin turns the on-twin OFF
  FIRST, a brief visible pause, THEN turns this ON, with a message "Turned off <twin> to turn on
  <this>." Off-first is deliberate: only-one-on stays true the whole way through, so the
  random-winner never even flickers (a technical reason that matches the intuitive ordering).
- DECISION NEEDED: auto-swap with an undoable message (low friction), or a quick "this will turn off
  <twin> — ok?" confirm first (safer)? Lean auto-swap; maybe confirm only for the risky types.

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
