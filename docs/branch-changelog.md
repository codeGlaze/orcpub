<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch,
     fill it in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold strips the guidance and the
     "Why" section; a "## Highlights" section (if earned) survives. Undated by design. -->

# Branch changelog — `port/save-gate`

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

Homebrew content is addressed by key, and before this branch many paths wrote the library on their
own: saves, imports, moves, key changes and a second open tab could overwrite each other, leave links
pointing at nothing, or strand a character's picks, often without a word. This branch puts every
write behind one gate, makes links follow key changes, and heals characters wherever they are read.
Backward compatibility first: stored libraries and characters load unchanged, and nothing is
migrated. Design, invariants and reviews: `homebrew-keys-design.md` on `feature/grant-rows`.

## Highlights

Homebrew edits can no longer quietly lose data. Every change to the library goes through one check
that keeps links intact, merges edits made in two tabs, and asks before touching another pack, and
characters keep their homebrew through renames on every page.

## Added

- **One gate for every library write** — saves, imports, moves, restores and key changes pass one
  check that refuses a write which would strand a link or lose an entry, and says what it refused
  (`31329bcd`, `5209eb97`).
- **Two tabs no longer overwrite each other** — a write from a tab holding an old copy is merged onto
  the newer library, and a real conflict is reported instead of lost (`bf1ca6a5`, `f3578592`,
  `4f7ebf3b`).
- **Links follow a key change** — changing an item's key moves the links in its own pack and offers
  to move the ones in other packs; those move only when the author clicks (`1042c746`, `b5f2a1b6`,
  `7c199b20`).
- **A copy of the library before its one-time tidy**, with Restore on My Content (`c0e38458`).
- **Repairs for links a past rename left behind** — My Content marks items that link to nothing and
  suggests the renamed target; nothing changes until the author picks (`8e76bbf3`, `c0e38458`).
- **A character is asked which item it meant** when an import renames an item it used, once, in a
  banner that waits for an answer (`c50e6536`).
- **Asking before deleting what other items use**, and saying what a re-import updated (`12eb5a23`).
- **A check on comments and docstrings** — a test flags history, long docstrings and long comment
  blocks; each hit is fixed or approved with a reason, and field notes are kept by hash
  (`90334b7a`, `d34bdb2f`, `76395a16`, `48f945b3`, `332ae1dd`).

## Fixed

- **Saves land on the item the builder opened** — a stale or ambiguous address is refused rather
  than guessed, and an older untagged entry is replaced only with consent (`b5b36d29`, `b9a98f4f`,
  `4968a3be`).
- **Characters keep homebrew renamed since they were saved** — picks heal on every page, not only in
  the builder, and a key two kinds of content share (the Dragonborn "Blue" ancestry and a background
  picked as `:blue`) no longer blocks the heal (`43f93777`, `38dbaa91`).
- **The missing-content warning** also reports missing spells and languages, and a background whose
  key another type holds (`4e88b04c`, `43f93777`).
- **"Link to nothing" is judged by content type** — a built-in language `Orc` no longer answers a
  race's link to `:orc` (`7c199b20`, `a376470b`).
- **A move takes an item's subclasses and subraces along** (`3fb06300`, `ab8b0393`).
- **Restore shows what is still set aside, and keeps what the author typed** when its write fails
  (`796e15dd`, `6cfccce3`).
- **Every on/off switch on My Content works from the keyboard** (`6cfccce3`).
- **Share links bundle a picked key only as the content types its choice can hold** (`4f7ebf3b`,
  `a90fe7c6`).
- **The server's first-load `.orcbrew` fetch** writes only into a library that is still empty
  (`4f7ebf3b`).

## Changed

- **Homebrew backgrounds are offered under their stored key**; characters that picked one under its
  name's key heal to it (`5209eb97`, `43f93777`).
- **Notices and import modals share one design** — each decision in one framed callout, names
  instead of keys, no decorative icons (`b1feb5b3`, `7ae78fc6`).
