# feature/inventory-kits

**Purpose:** equipment packs as kits (ordinary item rows plus a kit that keeps count of what runs
out), and inventory on the character sheet. **Base:** `integration`. **PR into:** `integration`.
**KB:** [`plan-inventory-kits.md`](../../docs/kb/plan-inventory-kits.md) (model, rulings, every build
note, what is deferred).

## State (updated 2026-11-14)

- Done: gear and weapons/armor add up across sources (#138, #340); kits in the builder (Restock,
  Re-bundle, Undo, pack counts, the search box's Packs preset); kits on the sheet with Manage
  inventory (− / +, coins, Add a kit) and backpack Worn | Off; class changes ask only about a used
  kit. Rules in `orcpub.dnd.e5.inventory`, screens in `orcpub.dnd.e5.views.inventory`.
- `integration` merged in 2026-11-14 (622 commits, after #74 `feature/weapons-table`).
- #85 merged into `integration` 2026-11-15 (`fadd99fd4`), after two CodeRabbit rounds. Follow-up #86 (What's New item) merged 2026-11-15 (`074faabe7`); both are in Gitea PR #10 (`c827a8fe2`). Then, on this branch or after: weapons counts on the sheet and kits / "not
  carried" in the PDF, both now unblocked by #74.

## Roadmap on this branch (updated 2026-11-16)

Done, merged as #87 into `integration` on 2026-11-16 (`85e964522`; also in the DMV pre-merge, Gitea PR #10, `0c1ade6a7`): the Packs pill in the
search box with its "Packs filter" way back, the Packs filter on the sheet's filter box, the "Add
equipment" box, the Add a kit tile, and the auth card fix (register header reachable while password
hints show). Decisions: `plan-inventory-kits.md`, "Packs chips and the Add a kit tile".

Follow-up PR #88 into `integration` (opened 2026-11-16, CodeRabbit full review requested): the home
page's What's New cap and glow, stacked pills on one icon column, the BMAC link pre-filled in
`.env.example`, the auth-flows link fix from the DMV pre-merge.

1. Kit icons: a bag per pack plus a corner badge, colour treatment as an account choice
   (`plan-inventory-kits.md`, "Kit icons"); waits on the owner's workbench picks.
2. Wide-screen kits layout (a tile grid at desktop): offered, unanswered.

## Owner decisions

- 2026-10-22/23: the kit model, the designer's v2 handoff with the owner's overrides, pack counts,
  quiet class changes, the Manage inventory switch, backpack Worn | Off: all in the plan page.
- 2026-10-23: one inventory namespace for kits, bags and containers; no file per concept.
- 2026-10-23: camp, mount and wagon, and gear carried on the person, are deferred.
- Armor names (owner, repeated; recorded 2026-11-14): the app keeps today's short names (Padded,
  Leather, Studded, Hide, Splint, Plate); SRD data uses the SRD's full names. Settled: never ask again.

## Open questions for the owner

- None.

## Gotchas for the next agent

- A new character starts as a Barbarian with a fixed Explorer's Pack: tests that switch class see
  that kit first (`test/browser/inventory_kits_*_e2e.js` pick the pack by its option, not its text).
- The sheet's autosave is throttled: a browser test must wait for the save reply before reloading.
- Browser tests: `./scripts/e2e/run.sh test/browser/inventory_kits_builder_e2e.js` and
  `inventory_kits_sheet_e2e.js` (logs in as the seeded user).
