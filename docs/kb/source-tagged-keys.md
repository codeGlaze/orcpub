# Should every minted key carry its source's abbreviation?

**Status: BUILT 2026-09-13.** (D10b) Written down because the question kept coming up in chat
and was nowhere in the KB. The *mechanism* ships already and runs only on collisions; what is
decided and unbuilt is applying it to every mint.

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
Curios* mints `:stone-elf-trcs` rather than `:stone-elf`. The author can delete the tag in the key
control, and deleting it is how they say *"I mean to replace the SRD one."*

## What it would buy

- **The duplicate-key class mostly stops happening.** Two authors' "Stone Elf" are
  `:stone-elf-trcs` and `:stone-elf-ksty` — no coin-flip winner among plugins
  (`key-collision-behavior.md`), no health-card pair, no cross-source refusal on a new mint.
- **Override becomes deliberate.** Today, replacing an SRD class is what you get by *accident* when
  you name yours "Fighter". With tagging it is what you get by *choosing* to strip the tag — the
  dangerous thing becomes explicit and the safe thing becomes the default.
- **It is finally separable from the name.** `disambiguated` tags the name too, because when it was
  written the save re-derived the key from the name and an untagged name would revert the key. D10a
  (mint once) removed that coupling. Tagging the KEY and leaving the NAME alone is only possible
  now — and nobody wants their homebrew listed as "Stone Elf (TC)".

## Settled

**The default source tags as `dflt`** (decided 2026-09-13). `:stone-elf-dflt`. It goes in
`source-abbreviation-overrides` beside UA and MM: the derivation would otherwise turn "Default
Option Source" into `DtOnSe` (three words, so first-letter-plus-last-letter each), and *no* tag
there leaves the collision class alive for exactly the people least likely to notice it — most
first-time homebrew lands in the default source.

**Discoverability of the key control stays as it is** (decided 2026-09-13). A muted line under the
form is the right weight for something an author touches once. Finding it a spot up top is the only
alternative worth considering, and it is not a priority — it is certainly not a reason to hold this
up.

## Still to be said out loud, but not blocking

- **Existing libraries keep their untagged keys.** Retro-tagging is a migration, and D9 says no. So
  the same content authored before and after differs, and old-vs-new duplicates stay possible.
- **Sources get renamed**, and the tag then reads stale. Harmless under D10a — a key is an address,
  and drift is already accepted — but it makes the tag a *mint-time fact*, not a live statement
  about where the item lives.

## As built

`common/source-tagged-key` — `(:key (disambiguated item-name source-name))`, so a minted key and an
import-disambiguated one can never drift apart, and the NAME is discarded rather than tagged. Wired
into the three mint sites: the save, its save-anyway, and the selection save. An item that already
has a key is untouched, so nothing in an existing library moves.

The abbreviation rule, in order: the override table where the world already has a spelling
(`dflt` for the placeholder source); a source that IS an abbreviation passes through (`SRD`); a
source that CONTAINS one keeps it whole and takes initials of the rest, because that is how a
release in a series is named (`"UA - Heroes of Krynn"` → `UAHoK`, `"UA - Giant Options"` → `UAGO`);
then first-letter-plus-last-letter per word for three words or fewer (`"Kibbles Tasty"` → `KsTy`,
`"Tidewater Curios"` → `TrCs`) and initials beyond that.

A year is not an initialism — `"Unearthed Arcana 2022: Heroes of Krynn"` is `UA2HoK`, not
`UA2022HoK`. Pinned by
`a-minted-key-carries-its-sources-tag`; the rest of the lifecycle suite asks for keys through a
helper so a change to the rule fails in one place.

**The key control does not re-tag.** A key the author types is exactly what they typed — deleting
the tag is how an SRD override is asked for, and a control that put one back would make that
impossible.
