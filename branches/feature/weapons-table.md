# feature/weapons-table

**Purpose:** the character sheet's Weapons table rebuilt around a roll engine, and the item charges
it led to: charges on the sheet, the Rest box, casting from charged items, item builder fields.
**Base:** integration (merged in 2026-10-25). **PR into:** integration, #74 (open, not reviewed;
the owner keeps CodeRabbit for backend work). **KB:** `plan-charges-and-casting.md`,
`handoff-weapons-table.md`, `handoff-rest-controls.md`, `weapon-mastery-plan.md`.

## State (updated 2026-10-25)

- Done: the Weapons table; charges model, data and pips; the Rest box and floating Rest button;
  casting from items; the item builder's Charges group; SRD spell rolling data (`spell-rolls.edn`,
  cherry-picked from `feature/srd-pipeline`); changelog written, not folded.
- Done 2026-10-25: Roll damage on a cast result (`weapon-rolls/roll-spell`, `spell-entries`); shown in
  the cast panel, not the weapon result sheet (that sheet is built around attack chains). Parts
  marked `:mod?` get no modifier yet (nil passed): items rarely cast those spells. Healing not rolled.
- Mastery (2026-10-26): steps 2, 3 done; step 5 half done (Vex and Cleave left, with pointers in
  `weapon-mastery-plan.md` step 5); step 4 waits for the 2024 line; step 6 after 5. Upstream #713 filed
  (low-Strength monk), cited in the changelog.
- Done 2026-10-26: attack mode both ways (header switch, long press; `weapon-rolls/attack-mode`,
  `:weapon-roll/mode`; `attack_mode_e2e.js`). Vex's preset can set `:weapon-roll/set-mode`.
- Mastery step 5 done 2026-10-26 (effect sentences, Missed?, Vex, Cleave).
- Mastery step 6 done 2026-10-26. Mastery now waits on its 2024 data (step 4, `srd52/develop`) and
  the panel (designed, round 6).
- Done 2026-10-26: Weapon powers group in the item builder (`views/charges_builder.cljs`
  `powers-group`; schema; `mi/complete-powers` drops half-filled rows). Light theme shots taken
  and sent. Then (same day) switches, While, Only against, Attack, Next attack and Extra attack
  (`add-toggle`, `remove-toggle`); `applies?` reads targets as a set (saved items return vectors).
  The mode switch shows a d20 for Normal (owner).
- Wording list published 2026-10-27: https://claude.ai/artifact/FmY7pE4i1K8NoaLGWxGcF3 (280 lines,
  collected by hand from the diff, may miss some). The owner's edits land in its `db` collection
  `edits` (doc id = line id, `{text, original}`); read them with ArtifactData `list` and apply.
- Owner (2026-10-27): the wording pass is busy work; one edit applied (the two-hands message), the
  page kept for later reference, not a task. Next: merge #74. Follow-ups: [weapons-table-follow-ups.md](../../docs/kb/weapons-table-follow-ups.md).

## Owner decisions

- 2026-10-24: spell data built on `feature/srd-pipeline`, cherry-picked here.
- 2026-10-25: on a full spell card the SRD text shows; a summary only as an optional section at the
  bottom (`srd-2024-roadmap.md`).
- 2026-10-25: the PR is not sent to CodeRabbit for now; another agent uses the allowance.
- 2026-10-26: attack mode both ways, header switch and long-press menu (above).
- 2026-10-26: on phones every weapon picker opens in the bottom sheet overlay (`weapon-menu-sheet`): the
  + menu and the long-press modes; desktop keeps the dropdown. The mode switch shows "—" for Normal.

## Gotchas for the next agent

- Stop `lein e2e-server` before any `lein` build here; kill it by pid (`ps`, then check
  `/proc/<pid>/cwd`): `pkill -f` and `pgrep -f` match your own shell and kill it.
- Garden CSS is a separate build (`lein garden once`); a style change is invisible until it runs.
- Browser scripts outside `scripts/e2e` need `NODE_PATH=$PWD/scripts/e2e/node_modules`; the server
  runs on `PORT=8897 E2E_PORT=8897`.
- A namespace `views.x` clashes with a var `views/x` in compiled JS (a compile warning).
- Fold the changelog (`scripts/fold-branch-changelog.sh`) only when the owner says #74 is about to merge.
