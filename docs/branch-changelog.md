<!-- Branch changelog for srd52/develop. Undated by design: intended to be folded into the
     top-level CHANGELOG.md at merge time with `scripts/fold-branch-changelog.sh "<release>"`
     (matches its `## [branch]` / `### Category` / `- **Title** (hash)` format).
     Forked from `refactor/content-extensibility` at f97567fb. -->

# Branch changelog — `srd52/develop`

## Why this branch exists

The alpha trunk for SRD 5.2 ("5.5e") support. It watches
`refactor/content-extensibility`, and through it `integration`; leaves live under `srd52/*`.

The gitea branch `srd-2024-intergration` already imported SRD 5.2.0/5.2.1 from open5e, but
as two duplicated namespace trees (`orcpub.dnd.e520` / `e521`, +58,026/-12) dispatched by a
hardcoded `case` on a `:selected-edition` keyword. That is the opposite direction from the
content-extensibility spine, and it silently drops homebrew: the edition branch calls
`t520/template` with an empty `plugin-subclasses-map`. The decision was to take the data and
the open5e import tooling, and leave the architecture.

The work is modelled as two independent dials rather than one edition switch, because users
want to mix: purity, 5.1-with-5.2-parts, and 5.2-with-5.1-parts are all settings of

  - a CONTENT dial — which sources and items are enabled, and
  - a RULES dial — what the engine computes (exhaustion, grapple, where ASI comes from).

A single edition keyword welds them together and can only express purity.

Blocking everything else: the content dial's gate is live but its input is dead, and it fails
closed rather than open. `?option-sources` has had no writer since 2017 (`2bad9a6d`
`#_`-discarded both), so `using-source?` reduces to "untagged or `:phb` passes, everything
else is dropped" — silently, because `mods/apply-modifiers` skips a modifier whose condition
is false. Tagging content before reviving the source picker would present as a broken import.

## Added

- Characterization test pinning the option-source gate: three otherwise identical level-1
  cleric spells differing only by `:source`, built through `entity/build`, proving untagged
  and `:phb` are known while `:srd-520` is dropped. Expected to flip when the picker is
  revived; that flip is the fix landing (`e3c9210`).

## Fixed

## Changed
