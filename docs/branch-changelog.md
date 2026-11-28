<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `hotfix/image-link-requests`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Say what the BRANCH did, not the commits it took. A branch that built new sign-in pages and
tightened passwords writes two lines, however many commits got it there:

  - **Accounts:** new sign-up, sign-in and account-recovery pages.
  - **Accounts:** password rules based on length, a live strength meter, and breach screening.

Rules:
  • One line per outcome a user or maintainer would notice. Open it with its area in bold
    (**Homebrew:**, **Characters:**, **Sharing:**, **Accounts:**, **Printing:**, **Site:**, **Server:**).
  • In CHANGELOG.md, lines under each heading are grouped by area, in this order: Characters,
    Builder, Character sheet, Inventory, Spells, Warlock, Magic items, Items, Equipment, Homebrew,
    Sharing, Rules, Orcacle, Reference, Monsters, Content, Printing, Accounts, Site, Server, Docker,
    Docs. The fold appends at the end of a heading: move each folded line into its area's group.
  • Fixed is for bugs that existed before this branch. A fix to something this branch (or the same
    release) built is part of building it: no line of its own.
  • No internal work: tests, CI, refactors, tooling, code moves. They belong in the PR and the KB.
  • Cite the upstream issue (#N) when there is one. No commit hashes.
  • Plain words; no AI-jargon ("seamless / robust / comprehensive / powerful / streamlined / leverage").
  • Security fixes: one plain line under Security, with no detail an attacker could use.
────────────────────────────────────────────────────────────────────────────────── -->

## Why this branch exists

<!-- Reviewer context only. Dropped at fold; never reaches CHANGELOG.md. -->

A player was banned by the server's intrusion filter after pasting text into a picture-link field:
the app used the text as an image address on every keystroke, each edit became a request for a
page on our own server that does not exist, and the burst of 404s looked like scanning. Pictures
now use an address only when it is a real http or https address.

## Added

## Changed

## Fixed

- **Characters:** typing or pasting text that is not a web address into a picture or faction image link no longer sends a request to the server with every keystroke, which could get a player blocked; the picture shows once the address is a real http or https link.

## Security
