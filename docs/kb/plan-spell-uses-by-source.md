# How a granted spell is cast: uses, slots and charges by source

How the 2014 and 2024 rules let a character cast a spell they got from a race, feat, item or class
feature, what the app stores today, and the plan to show and track it right on the Spells tab and
the PDF. Written 2026-11-11 on `fix/pdf-export` after the owner found race and feat spell pages
printing the class's slots. Builds on [spell-granting-across-silos.md](spell-granting-across-silos.md)
(one primitive, many wrappers) and [plan-charges-and-casting.md](plan-charges-and-casting.md) (a cast
is one act with a different payment). Nothing here is built.

## The rules: every grant answers the same four questions

Read from the 2014 SRD 5.1 / PHB and the 2024 SRD 5.2 / PHB wording. Rows marked *ruling* rest on
an official ruling, not the printed text; *check* means confirm against the text when its data is
filled.

| How it is cast | 2014 | 2024 | Overlap |
|---|---|---|---|
| **Slots, as a class spell** (class lists; domain, oath, circle and patron spells, always prepared) | yes | yes | identical |
| **Cantrip, at will** (race and feat cantrips: High Elf, Tiefling Thaumaturgy, Magic Initiate) | yes | yes | identical |
| **Free uses per rest, no slots** (Tiefling Infernal Legacy, Drow Magic, Magic Initiate's 1st-level spell as printed) | the norm for races and feats | rare | 2014's default |
| **Free uses per rest, and slots too** (2024 Tiefling Fiendish Legacy, Elf and Gnome lineages, Magic Initiate; Tasha's Fey Touched and Shadow Touched) | Magic Initiate *ruling*; Tasha's feats (not SRD) | the norm for species, feats and some class features (SRD 5.2.1) | 2024's default |
| **At will, levelled, no slot** (Armor of Shadows, Mask of Many Faces invocations; Boots of Levitation) | yes | yes | identical |
| **Slot spent, but limited** (2014 invocations such as Thief of Five Fates: "once using a warlock spell slot", back on a long rest) | yes | *check* | 2014 mostly |
| **Charges or per-dawn uses on an item** | yes | yes | identical, built |
| **Another resource** (ki points, Way of the Four Elements; not SRD) | rare | rare | homebrew only |
| **Ritual only** (Ritual Caster feat, Book of Shadows) | yes | yes | identical |

**Checked against the SRD text** (`resources/srd/SRD-5.1.readable.txt` and
`SRD-5.2.1.readable.txt` on `srd52/develop`):

- 5.1 Tiefling, Infernal Legacy: "cast the hellish rebuke spell as a 2nd-level spell once with this
  trait and regain the ability to do so when you finish a long rest". No slots.
- 5.2.1 Elven Lineage and Fiendish Legacy: "always have that spell prepared. You can cast it once
  without a spell slot ... Long Rest. You can also cast the spell using any spell slots you have of
  the appropriate level." Magic Initiate (5.2.1) says the same.
- 5.2.1 Forest Gnome: Speak with Animals "a number of times equal to your Proficiency Bonus", then
  slots too.
- 5.2.1 class features use the same wording (Draconic Sorcerer's Dragon Companion: "cast it once
  without a spell slot"), so free uses are not only a race and feat thing in 2024.

Attributes that ride on any row above, not rows of their own:

- **Count:** a number (1), or a number from the character: proficiency bonus or an ability modifier
  (common in 2024 and Tasha's).
- **Reset:** short rest, long rest, dawn, day.
- **Fixed cast level:** 2014 Tiefling casts Hellish Rebuke **as a 2nd-level spell**; most cast at
  the spell's lowest level.
- **Minimum character level** before the grant applies (Tiefling: 3rd and 5th).

**The overlap that matters:** the editions share every mode; they differ only in which one a race
or feat uses by default. So one small record answers every row: *how many free uses, reset when,
may slots also be spent, at what level*. Absent, a spell is cast from slots as a class spell, which
is what the app does for every spell today.

## What the app has (checked 2026-11-11, `fix/pdf-export`)

- **One primitive:** `mod5e/spells-known` (`modifiers.cljc:260`) stores `{:key :ability :qualifier
  :class}`. `spells-known-cfg` (`:237`) can store any map (it already carries `:class-key`,
  `:always-prepared?`). **No uses, reset or slot field.** "once per long rest" lives only in the
  free-text `:qualifier`.
- **The source is a display string.** `:class` is "Tiefling" for a race spell, "Wizard" for
  Magic Initiate's (so Magic Initiate cannot be told from a real Wizard spell by `:class`), "Cleric"
  for domain spells with `:qualifier "Domain"`.
- **Homebrew:** the `known-spells` row (`homebrew_shapes.cljc:303`), races and subraces only:
  `{:level :value {:key :ability :level}}`. Feats have no spell row (the silos gap).
- **Uses:** limited-use traits carry a `units5e` frequency (`units.cljc`), and the player ticks
  them in `::char5e/features-used`, which rests clear (`rests.cljc`). Item charges are separate
  pools (built, `plan-charges-and-casting.md`). No spell grant links to either.
- **App display:** `spells-table` (`views.cljs` ~2208) heads each level "N remaining" from the
  shared slots, never per source; `spell-row` drops the `:qualifier`; `cast-spell-component`
  spends a slot for any spell, a race spell included, and is disabled with no slots. So a
  race-only caster reads "0 remaining" and cannot mark the free use.
- **PDF:** pages are grouped by ability, headed by every `:class` on them (`pdf_spec.cljc` ~390),
  and every first page gets the shared slots (`spell-page-fields` ~597; `packing-classes` ~528).
  The qualifier is printed after the name.

## What the refactor line already has (checked 2026-11-11)

Searched `refactor/main`, `feature/grant-rows`, `srd52/develop` and `feature/extras-companions`.
**Nothing for uses, slots or casting by source to lift.**

- `grants.cljc` (`feature/grant-rows`): the generic grant shape, `{:pool p :key k}` fixed or
  `{:pool p :count n :filter ...}` choice, untrusted-input spec. `grant_pools.cljc` registers
  languages, fighting styles, skills, expertise, tools; **no spell pool**.
- Spells as a pool is the **last** step of that branch's plan (`handoff-grant-rows.md` section 8,
  `pool-grant-map.md` "Spells"), not started.
- `srd52/develop` holds the SRD 5.1 and 5.2.1 text used above, nothing that models the uses.

**What this means for the plan:** the `:casting` map below is the per-grant payload a future
`{:pool :spells :key k}` grant would carry, so build it as a plain map on the grant, independent
of how the grant is declared. Today it rides on `spells-known-cfg`; when spells become a pool it
moves onto the grant unchanged.

## The plan

**One field, read by everyone.** Add an optional `:casting` map to a spell grant (through
`spells-known-cfg`, so the primitive is unchanged):

```clojure
{:uses 1 | :proficiency-bonus | ::char5e/cha   ; free uses; absent = none
 :reset ::units5e/long-rest                     ; the units5e periods rests already clear
 :slots? true                                   ; may also be cast from slots; absent = true without :uses
 :level 2}                                      ; fixed cast level for the free use
```

- **The rule every reader uses** goes in one cljc function (beside `shown-picture`'s kind of rule),
  e.g. `char5e/spell-payment cfg` returning `#{:slot :free :at-will}`. The PDF and the app ask it;
  neither guesses from `:class`. This also fixes Magic Initiate, which `:class` alone cannot.
- **Edition defaults live in the data, not the code.** Each built-in race, feat and invocation
  states its own `:casting`; 2014 and 2024 content simply carry different values. A homebrew grant
  with no `:casting` keeps today's behaviour, so nothing old changes meaning.
- **Uses are tracked where limited-use traits already are:** `::char5e/features-used` keyed by the
  grant, cleared by the Rest box. A count above 1 (proficiency bonus) needs counts, which
  `features-used` lacks: reuse the item-charges pool shape for those, not a third mechanism.
- **Casting reuses the item cast panel** (one act, a different payment): a free use, a slot where
  `:slots?`, a charge for an item. Not a new panel.

What the player sees:

- **Spells tab:** "N remaining" only beside levels that hold slot-cast spells, and only when the
  character has slots. A free-use spell shows its own "1/long rest" with a tick; one that also
  takes slots shows both.
- **PDF:** slot boxes only on pages whose spells are cast from slots; a race or feat spell's uses
  print after its name ("Hellish Rebuke (2nd level, 1/long rest)"); packed groups get no slots when
  none of their spells use them.

## Build order (each step small and shippable)

1. **The field and the rule:** `:casting` on `spells-known-cfg`, `spell-payment`, tests over the
   rules table above. No behaviour change.
2. **Built-in data:** 2014 SRD races and feats state their `:casting` (Tiefling, Drow, High Elf,
   Magic Initiate, invocations). 2024 data on `srd52/develop` does the same when its races land.
3. **PDF:** slots only where `spell-payment` says slot; uses after the name. Fixes the reported bug.
4. **Spells tab display:** per-source uses, "remaining" only where slots apply.
5. **Tracking and casting:** the free-use tick, then the cast panel's payment choice.
6. **Homebrew:** optional `uses / reset / slots / level` on the `known-spells` row
   (`homebrew_shapes.cljc`, backward compatible: absent means today's behaviour), and a spell row for
   feats (the silos gap), through the same field.

Steps 1-3 are the fix for the reported PDF bug; 4-6 are the app side. Steps 1-4 touch no saved
data; 5 adds per-grant uses to the character (schema); 6 adds homebrew fields (the shape table).

## Open, for the owner

- Which branch carries steps 1-4 (`fix/pdf-export` is the PDF bug's branch; the app side is wider).
- Whether the 2014 Magic Initiate follows its printed text (free use only) or the ruling (slots
  too).
