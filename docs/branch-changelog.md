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

Homebrew classes can now define their **starting equipment** from the builder UI — the
**full SRD form**, not just the easy half. Fixed items, plus choice groups where each
option can grant a **bundle** of items and/or a **nested weapon sub-choice**, so
"(a) chain mail, or (b) leather + a longbow + 20 arrows" and "a martial weapon and a
shield" are all buildable without hand-editing `.orcbrew`. It applies on the character
sheet and round-trips through save, export, and import.

## Added

- **Starting Equipment section in the class builder** — a homebrew class can grant fixed
  items (`:weapons`/`:armor`/`:equipment`) and rich choice groups. Each choice option is a
  label + one-or-more item grants (from the real weapon/armor/equipment vocabulary) + an
  optional "any simple/martial weapon" sub-choice — i.e. the full SRD equipment form
  (bundles and nested picks), via a serializable `:equipment-selections` shape that
  `class-option` compiles to the same structure the SRD classes use. Applies with no new
  engine path, round-trips through save/export/import, and imported legacy simple choices
  convert to the editable form in one click (`a4f13086`, `5a5f65a8`).

## Fixed

## Changed
