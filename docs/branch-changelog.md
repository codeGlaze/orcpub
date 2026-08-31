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

You can also **start from an SRD class**: the builder fills in that class's equipment for
you to tweak, taken from the live class definition rather than a hand-copied table. If you
only change a few things, the exported file stores **just those changes** against the base
class instead of a full copy.

## Added

- **Starting Equipment section in the class builder** — a homebrew class can grant fixed
  items (`:weapons`/`:armor`/`:equipment`) and rich choice groups. Each choice option is a
  label + one-or-more item grants (from the real weapon/armor/equipment vocabulary) + an
  optional "any simple/martial weapon" sub-choice — i.e. the full SRD equipment form
  (bundles and nested picks), via a serializable `:equipment-selections` shape that
  `class-option` compiles to the same structure the SRD classes use. Applies with no new
  engine path, round-trips through save/export/import, and imported legacy simple choices
  convert to the editable form in one click (`a4f13086`, `5a5f65a8`).
- **"Start from an SRD class"** — a dropdown fills the builder with any SRD class's starting
  equipment. It's read from the live class (by applying the class's own modifier functions),
  so it always matches what the class actually grants. All 12 classes (`2470e1d2`, `3fc5585d`).
- **Save only the changes** — a class filled from an SRD class stores a small "based on
  <class> plus these changes" form instead of a full copy. This lives only in the exported
  file; everything in the running app stays the full form the existing functions already use.
  A "Based on <Class>" banner shows the link, with a **Detach** button to save a full copy
  instead (`6f494788`, `8172e915`, `be43690b`, `bcc05aa1`).

## Fixed

- **Filling from a class keeps the SRD's own names** — a grouped focus pick stayed "Arcane
  Focus" instead of being renamed "Starting Equipment: Arcane Focus" (the rename would have
  changed the key it maps to) (`d6213b6e`).
- **A choice we don't recognise is never silently dropped** — an unrecognised sub-choice is
  listed out option-by-option instead of vanishing; a genuinely empty one raises an error
  with context (`2950117c`).

## Changed

- **"Start from a class" reads the live class, not a hand-written table** — nothing to drift
  out of sync; verified by a round-trip test against all 12 classes (`3fc5585d`, `f44324b3`).
- **Notification view components collected into `orcpub.dnd.e5.views.notifications`** — the
  message banner, a reusable callout box, and the shared-content banner now live in one
  namespace; the health/legacy banners render through the shared callout instead of
  hand-rolled boxes (`4621b4c2`, `25f5d9a1`).
- **`lein e2e-server`** boots the full app (Pedestal + in-memory Datomic, no transactor) on
  :8890 for browser tests (`47c413b0`).
