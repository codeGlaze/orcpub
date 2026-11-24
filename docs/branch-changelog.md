<!-- Branch changelog. Fold into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. House style: docs/branch-changelog.template.md. -->

# Branch changelog — `fix/sheet-polish`

## Why this branch exists

Tester reports from the dev site after the Long Rest Update reached `develop` (#95): notices washed
in amber in Firefox, a leave-page prompt and a trip to sign-in after changing a weapon on a shared
character, a squashed pasted portrait in the builder, and an attack roll on every spell. The style
order test also found the opened weapon panel losing its bottom strip, from the same cause as the
Firefox notices.

## Added

## Changed

## Fixed

- **Character sheet:** only spells that make a spell attack show an attack roll; Bless and Cure Wounds no longer offer 1d20.
- **Character sheet:** changing something on someone else's shared character shows the change without trying to save it, so there is no leave-page warning and no trip to sign-in.
- **Character sheet:** the opened weapon panel keeps its coloured bottom strip.
- **Builder:** a pasted portrait picture fills its frame instead of being squashed.
- **Site:** notices such as Unsaved drafts show their thin amber ring in Firefox, instead of an amber wash that hid their text.

## Security
