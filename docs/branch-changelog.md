<!-- Branch changelog for feat/starting-equipment. Folds into CHANGELOG.md at
     merge-to-integration via scripts/fold-branch-changelog.sh. House style + the
     fold rules live in docs/branch-changelog.template.md. Undated by design. -->

# Branch changelog — `feat/starting-equipment`

## Why this branch exists

Homebrew classes can't define starting equipment from the UI. The `.orcbrew` format
already supports it — you can hand-edit starting-equipment selections into a class —
but the homebrew class builder exposes no field for it, so the only safe path (the
shape the SRD classes already use) is unreachable without editing raw EDN. This branch
retrofits the base class builder with a starting-equipment section that writes the same
structure the SRD classes and manual edits use, so a custom class grants equipment the
way an official one does — and it round-trips through save/import/export.

## Highlights

Homebrew classes can now define their **starting equipment** from the builder UI —
fixed items and "player chooses one" groups — instead of only through hand-edited
`.orcbrew` files. It writes the same shape the SRD classes use, so the equipment
applies on the character sheet and round-trips through save, export, and import.

## Added

- **Starting Equipment section in the class builder** — a homebrew class can grant fixed
  items (`:weapons`/`:armor`/`:equipment`) and typed choice groups (`:*-choices`), picked
  from the real weapon/armor/equipment vocabulary (including "any simple/martial weapon").
  Writes the shorthand keys `class-option` already consumes, so it applies to a character
  with no new wiring and round-trips through save/export; empty categories are dropped so
  exports stay clean (`a4f13086`).

## Fixed

## Changed
