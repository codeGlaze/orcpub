<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch,
     fill it in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold strips the guidance and the
     "Why" section; a "## Highlights" section (if earned) survives. Undated by design. -->

# Branch changelog — `refactor/picks-namespace`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Entries are bullets under ## Added / ## Fixed / ## Changed (Keep a Changelog categories).

Bullets:
  • One change per bullet. Split, don't cram three changes into one line with semicolons.
  • Succinct and plain. Not necessarily terse — but to the point.
  • Cut: AI-jargon ("seamless / robust / comprehensive / powerful / streamlined / leverage"),
    restating the same change twice, and explaining internal wiring the reader doesn't need.
  • Say WHAT changed and WHY it matters. End with the commit(s): (`shorthash`).

Highlights (optional — DELETE the section if this branch doesn't earn one):
  • Allowed ONLY for an impactful branch: a new capability or a behavioral shift that
    didn't exist before — NOT a bugfix bundle or routine polish.
  • ≤ 3 sentences, user-facing, plain. This is the one place prose is allowed, and it
    survives the fold. Decide whether the branch earns it before you open the PR.

No prose intro paragraphs anywhere else — not under this title, not under a category.
────────────────────────────────────────────────────────────────────────────────── -->

## Why this branch exists

<!-- Reviewer context only. STRIPPED at fold — never reaches CHANGELOG.md. Say what problem
     this branch solves and any scope/rationale a reviewer needs. Prose is fine here. -->

One engine over a character's stored picks, shared by the character-rescue work (which must list
and remove picks on the server, without the app) and the hidden-pick work (which moves picks that
stop applying out of the character and back). Three walkers over stored picks already exist; this
moves the heals' one into shared code unchanged and adds the two operations both pieces need,
rather than writing a fourth. The build's walker and the Missing Content walker are not touched.

## Added

- **Remove and put back one stored pick by its address** — `picks/remove-at` and `picks/put-at`, the base for repairing a character and for setting picks aside (`439593dd`).

## Fixed

## Changed

- **The pick walker moved into shared code** — `walk-entries`, `walk-picks`, `picks-of` and `relink-picks` now live in `orcpub.dnd.e5.picks` (as `walk`, `walk-typed`, `keys-of`, `relink`), unchanged, so the server can use them and their tests run in CI (`439593dd`).
