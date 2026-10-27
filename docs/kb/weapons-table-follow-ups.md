# Follow-ups from `feature/weapons-table` (PR #74)

What the branch left for later, and where each piece stands. Recorded 2026-10-27 at merge prep.
Branch state and gotchas: [branches/feature/weapons-table.md](../../branches/feature/weapons-table.md).

## Waiting on 2024 data (the owner keeps 2024 content off this branch)

- **The eight Weapon Mastery definitions** as data on `srd52/develop` (mastery plan step 4,
  [weapon-mastery-plan.md](weapon-mastery-plan.md)). The engine, band and roll-result plumbing are
  merged with #74 and tested with made-up data for all eight (`weapon_rolls_test.cljc`,
  `masteries`); real data switches the band on.
- **The mastery panel** (designed and approved, round 6, in the mastery plan). It needs characters
  trained in masteries, which comes from the 2024 class features (grant pools on
  `feature/grant-rows`). Until then it would be empty.

## For the refactor's homebrew vocabulary

- **Attacks with another ability** (a feat, custom class or custom weapon saying "use Charisma"):
  the plumbing is `?weapon-abilities` (each entry names an ability), with the weapon's save DC
  following it. Missing: a data entry in the authoring vocabulary. Upstream asks #665 and #191.
  Detail: [fighting-style-vocabulary-gap.md](fighting-style-vocabulary-gap.md), "Attacks with another
  ability".

## SRD text, Stage 1

- **Spell text replaces the app's PHB summaries** for seven SRD spells (Branding Smite and others);
  summaries move to `:summary`, shown at most as an optional section at the bottom of the full card
  (owner, 2026-10-25). Branding Smite is skipped by name in `srd_spell_rolls_test.clj` until then.
  Detail: [srd-improvements.md](srd-improvements.md), "Spell rolling data".

## Smaller gaps

- **Healing from item casts** is not rolled, and spell parts marked `:mod?` get no modifier from an
  item cast (items rarely cast those).
- **Cleave's + menu pick** has unit tests, no browser check (no SRD item uses it before mastery data).
- **The wording page** (owner: reference, not a task): https://claude.ai/artifact/FmY7pE4i1K8NoaLGWxGcF3,
  280 player-facing lines with an edit box each; edits land in its `db` collection `edits`.
- **Upstream #713** (low-Strength monks attacked at +0) is fixed on the branch and cited in the
  changelog; it closes when the release reaches upstream.
