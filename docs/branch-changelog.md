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

Defects in the 2014 SRD data the app already ships, found while checking that data against the SRD
5.1 PDF for the SRD 2024 work. Every value added here was read from the SRD 5.1: monster skills from
each stat block (pp. 285-402), prices and weights from the rendered page 70.

The data fixes change no code. A misnamed key is not an error anywhere; its reader skips it and
the value never shows, which is how these went unnoticed. A new test pins every monster and gear
entry to the keys their readers know, so the next misnamed key fails the build instead.

Two display gaps found on the way are fixed here too, rather than on branches of their own: the
monster stat block never showed reactions, and the app had none of the SRD's conditions. The
conditions are read from a static data file generated from corrected open5e data
(`resources/public/srd/2014/conditions.edn`), the first of the SRD files the 2024 work produces.
Clear typos in the SRD itself are corrected and noted, not preserved: Grappled's "thunder-wave" is
"thunderwave".

Left out: weapons and armor carry no price at all, with nothing that would display one.

## Added

- **Rules:** pages for the 15 SRD conditions, with links to them from each monster's condition
  immunities, and the Orcacle finds them by name.

## Fixed

- **Monsters:** stat blocks show reactions, such as the Marilith's Parry, and the monster builder
  can add one.

- **Monsters:** the Adult and Ancient Gold Dragons, Adult Green Dragon, Ancient Brass Dragon,
  Succubus/Incubus and Spy show all their skills; Stealth and Persuasion were missing.
- **Monsters:** the Flying Snake shows its Flyby trait.
- **Equipment:** tools, musical instruments, gaming sets and equipment packs show their SRD prices,
  and tools and instruments their weights.
- **Magic items:** the Alchemy Jug shows that its liquid can be created once a day.
