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

<!-- Decide at PR time (likely earns one — a new authoring capability the UI didn't
     have). Draft ≤3 sentences, user-facing, once the work lands. -->

## Added

## Fixed

## Changed
