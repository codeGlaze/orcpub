<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `fix/srd-2014-data-defects`

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

<!-- Reviewer context only. Dropped at fold; never reaches CHANGELOG.md. -->

The second round on this branch, after #58 brought the Rules reference. Three follow-ups the owner
asked for together: weapons and armor get the prices and weights the SRD tables give them (the
last gap from #58's data fixes); our own clarification notes on two rules, in our words and linked
to where Wizards published the rulings, kept apart from the SRD text; and the conditions that
spell, magic item and monster text names link to their pages, with the previews the Rules pages
have. Weapon and armor values were checked against the SRD 5.1 tables item by item; the open5e
errors found on the way are fixed upstream in open5e PR #999.

## Added

- **Equipment:** weapons and armor show their SRD price and weight.
- **Rules:** clarification notes on rules, in our own words, linked to where Wizards published
  the ruling.
- **Spells:** conditions named in spell and magic item text link to their page, with a preview.
- **Monsters:** conditions named in monster traits and actions link to their page, with a preview.
