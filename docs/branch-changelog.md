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

Found on the way and fixed here too, rather than on branches of their own: the monster stat block
never showed reactions, and the app held none of the SRD's rules or conditions as text. The rules
and conditions are static data files (`resources/public/srd/2014/`) generated from open5e's data
with corrections, the first of the SRD files the 2024 work produces. open5e lacked about 2,500
words of the SRD rules (Resting, Movement and Position, and death saving throws with the rest of
Damage and Healing), which were added from the SRD itself.
Clear typos in the SRD itself are corrected and noted, not preserved: Grappled's "thunder-wave" is
"thunderwave".

Left out: weapons and armor carry no price at all, with nothing that would display one.

## Added

- **Rules:** a Rules tab with the full SRD 5.1 rules reference, by section, and the 15 conditions.
- **Rules:** links in rules text to the spells, conditions and rules they name, with a preview
  on hover, or on a first tap on a phone.
- **Orcacle:** finds rules and conditions by name.
- **Monsters:** each condition in a monster's condition immunities links to its page.

## Fixed

- **Monsters:** stat blocks show reactions, such as the Marilith's Parry, and the monster builder
  can add one.

- **Monsters:** the Adult and Ancient Gold Dragons, Adult Green Dragon, Ancient Brass Dragon,
  Succubus/Incubus and Spy show all their skills; Stealth and Persuasion were missing.
- **Monsters:** the Flying Snake shows its Flyby trait.
- **Equipment:** tools, musical instruments, gaming sets and equipment packs show their SRD prices,
  and tools and instruments their weights.
- **Magic items:** the Alchemy Jug shows that its liquid can be created once a day.
