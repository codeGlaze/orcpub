# Should every minted key carry its source's abbreviation?

**Status: OPEN.** Recorded 2026-09-13 because the question kept coming up in chat and was nowhere on
this page. Nothing here is built. The *mechanism* is built and shipped; only the always-on policy is
undecided.

## What exists today

`common/disambiguated` turns an item name plus a source name into `{:name :key}` with the source's
abbreviation applied — `("Artificer" "Kibbles Tasty")` → `{:name "Artificer (KsTy)" :key
:artificer-ksty}`. `common/source-abbreviation` does the deriving, with an override table for
sources the two-shape rule gets wrong (UA, MM, PHB, DMG, EB, and the settings whose initials skip
words), and passes an already-abbreviated source through unchanged so SRD stays SRD.

It runs in exactly three places, all of them **reactions to a collision**:

| | |
|---|---|
| import conflict resolution | the modal's rename (`orcbrew_validation.cljs`) |
| move | only when the target source already holds the key |
| copy | always — a copy is a new variant by definition |

Provenance: `8d689572` (derive the key from the tagged name), `436152e8` (the abbreviation people
actually write, and relocation following it).

## The proposal

Tag **every** key at mint time, not just the colliding ones. "Stone Elf" authored in *Tidewater
Curios* mints `:stone-elf-tc` rather than `:stone-elf`. The author can delete the tag in the key
control, and deleting it is how they say *"I mean to replace the SRD one."*

## What it would buy

- **The duplicate-key class mostly stops happening.** Two authors' "Stone Elf" are
  `:stone-elf-tc` and `:stone-elf-ksty` — no coin-flip winner among plugins
  (`key-collision-behavior.md`), no health-card pair, no cross-source refusal on a new mint.
- **Override becomes deliberate.** Today, replacing an SRD class is what you get by *accident* when
  you name yours "Fighter". With tagging it is what you get by *choosing* to strip the tag — the
  dangerous thing becomes explicit and the safe thing becomes the default.
- **It is finally separable from the name.** `disambiguated` tags the name too, because when it was
  written the save re-derived the key from the name and an untagged name would revert the key. D10a
  (mint once) removed that coupling. Tagging the KEY and leaving the NAME alone is only possible
  now — and nobody wants their homebrew listed as "Stone Elf (TC)".

## What is unresolved — these block building it

1. **The default source.** What does "Default Option Source" abbreviate to? `:stone-elf-dos` is
   worse than no tag. Probably: no tag when the source is the default placeholder — which leaves the
   collision class alive for exactly the people least likely to notice.
2. **Existing libraries keep their untagged keys.** Retro-tagging is a migration, and D9 says no.
   So the same content authored before and after this change differs, and old-vs-new duplicates stay
   possible. Acceptable, but it should be said out loud rather than discovered.
3. **Sources get renamed.** The tag would then be stale. Harmless under D10a — a key is an address
   and staleness is already accepted — but it means the tag is a *mint-time fact*, not a live
   statement about where the item lives.
4. **Does an author see it before it happens?** Minting silently and letting them edit it off after
   the fact means the key control has to be discoverable, which is a different bar from where it is
   now (a muted line under the form).

## Recommendation

Do it, key only, tag removable — with (1) settled first, since the default source is where most
first-time homebrew lands. Then `:cross` collisions become rare enough that the remaining ones are
worth a modal rather than a banner.

Not started. When it is, it wants a D-number in `content-extensibility-decisions.md` and a
characterization test beside the lifecycle suite.
