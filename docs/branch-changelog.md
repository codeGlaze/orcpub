<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `feature/guarded-homebrew`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Say what the BRANCH did, not the commits it took. A branch that built new sign-in pages and
tightened passwords writes two lines, however many commits got it there:

  - **Accounts:** new sign-up, sign-in and account-recovery pages.
  - **Accounts:** password rules based on length, a live strength meter, and breach screening.

Rules:
  • One line per outcome a user or maintainer would notice. Open it with its area in bold
    (**Homebrew:**, **Characters:**, **Sharing:**, **Accounts:**, **Printing:**, **Site:**, **Server:**).
  • Fixed is for bugs that existed before this branch. A fix to something this branch (or the same
    release) built is part of building it: no line of its own.
  • No internal work: tests, CI, refactors, tooling, code moves. They belong in the PR and the KB.
  • Cite the upstream issue (#N) when there is one. No commit hashes.
  • Plain words; no AI-jargon ("seamless / robust / comprehensive / powerful / streamlined / leverage").
  • Security fixes: one plain line under Security, with no detail an attacker could use.
────────────────────────────────────────────────────────────────────────────────── -->

## Why this branch exists

Homebrew was checked where code remembered to check it, and trusted everywhere else: a value of
the wrong shape in one field could break a page, stop the app from starting, or make an entry
vanish without a word. This branch checks every change to the homebrew library in one place,
whichever way it came in, against one table of what each field holds; and a test loads junk
into every field of every content type, by every way in, to prove it (docs/HOMEBREW_GUARD.md).

## Added

## Changed

- **Homebrew:** a value in the wrong form whose meaning is plain (`"25"` for a speed, one trait
  where a list belongs) is corrected once, on load, import, save or from a share link, and the
  correction is what is stored and exported. A library saved before is backed up first.
- **Homebrew:** an entry with a field that cannot be read (a number where a list of traits
  belongs) is set aside in My Content for repair, as it was written, instead of breaking pages.
  From a share link it is left out.
- **Homebrew:** checking homebrew after an import or a Restore takes a fraction of the time for
  classes: each shared part of a class is checked once, not once per level.

## Fixed

- **Homebrew:** a library with a content section stored as `true` no longer stops the app from
  starting.
- **Homebrew:** a monster from a share link with a broken name, size or type no longer breaks the
  monster list.
- **Homebrew:** a subrace, subclass, boon or invocation whose name could not be read no longer
  disappears without a word; it is set aside for repair.
- **Homebrew:** a feat whose ability score increases were stored as a list no longer breaks its
  builder.
- **Homebrew:** a homebrew subrace, pact boon or eldritch invocation that was renamed, or given a
  new key on import to settle a conflict, stays picked on the characters that chose it.
- **Homebrew:** a background, feat or class granting a skill that does not exist no longer stops
  the character options from loading.

## Security
