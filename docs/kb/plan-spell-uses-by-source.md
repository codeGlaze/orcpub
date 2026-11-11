# How a granted spell is cast: uses, slots and charges by source

How the 2014 and 2024 rules let a character cast a spell they got from a race, feat, item or class
feature, what the app stores today, and the plan to show and track it right on the Spells tab and
the PDF. Written 2026-11-11 on `fix/pdf-export` after the owner found race and feat spell pages
printing the class's slots. Builds on [spell-granting-across-silos.md](spell-granting-across-silos.md)
(one primitive, many wrappers) and [plan-charges-and-casting.md](plan-charges-and-casting.md) (a cast
is one act with a different payment). Nothing here is built.

## The rules: a catalogue of casting modes, by edition and era

**Correction (owner, 2026-11-11):** an earlier version of this page said the editions "differ only
in which mode a race or feat uses by default" (2014 free-only, 2024 free plus slots). Wrong. 2014 is
not one default: it changed over the edition's life, and it has a mode 2024 dropped. So no rule may
be keyed on the edition; every grant states its own mode.

**Sources and marks.** *SRD* = checked against `resources/srd/SRD-5.1.readable.txt` or
`SRD-5.2.1.readable.txt` on `srd52/develop` (line numbers there). *Book* = from a non-SRD book,
from memory, not checked against text: confirm before it becomes data. *Ruling* = an official
ruling, not printed text. 2014 eras: PHB (2014), Xanathar's (XGtE, 2017), Tasha's (TCoE, 2020),
Monsters of the Multiverse (MotM, 2022), all 2014 rules.

| Mode | 2014 | 2024 | Overlap |
|---|---|---|---|
| **A. Slots, as a class spell.** Class lists; domain, oath, circle, patron spells; always prepared | SRD | SRD | identical |
| **B. Cantrip, at will.** Race and feat cantrips (Tiefling Thaumaturgy, High Elf, Magic Initiate) | SRD | SRD | identical |
| **C. Free casts per rest; slots cannot be used.** | SRD: Tiefling Infernal Legacy (51:694, "as a 2nd-level spell"); Mystic Arcanum (51:3713). Book: Drow Magic (PHB); Magic Initiate as printed (PHB); XGtE racial feats (Drow High Magic, Wood Elf Magic; Fey Teleportation resets on a short rest) | SRD: Mystic Arcanum (521:6677); Divine Intervention (521:3704, any Cleric spell of level 5 or lower); Signature Spells (521:7309, short or long rest, at level 3) | both. 2014 PHB/XGtE use it for races and feats; 2024 keeps it for class capstones only |
| **D. Free casts per rest, and slots too.** | Book: TCoE Fey Touched, Shadow Touched; MotM races (for example Fairy, Githyanki). Ruling: Magic Initiate | SRD: Elven Lineage (521:7810), Fiendish Legacy (521:7990), Forest Gnome (521:7848), Magic Initiate (521:8062), Paladin's Smite (521:5133), Faithful Steed (521:5181), Favored Enemy (521:5445, twice), Dragon Companion (521:6493), Gift of the Depths (521:6776) | **late 2014 (TCoE, MotM) and 2024 are the same mode.** 2024 makes it the norm and extends it to class features |
| **E. At will, levelled, no slot.** | SRD: invocations (Armor of Shadows 51:3744, Fiendish Vigor "as a 1st-level spell" 51:3803, Mask of Many Faces 51:3825, and others); Spell Mastery (51:4133, lowest level; a slot to upcast) | SRD: the same invocations (521:6713 on); Spell Mastery (521:7296) | identical |
| **F. A slot is spent, but only N per rest.** | SRD: Mire the Mind (51:3836, slow "once using a warlock spell slot"), Sign of Ill Omen (51:3868, bane) | not found in SRD 5.2.1 | **2014 only** |
| **G. Free, with a per-target limit.** | SRD: Chains of Carceri (51:3783, hold monster at will, once per creature per long rest) | SRD: Contact Patron (521:6669, free, auto-succeeds its save) | rare edge cases on both sides; text, not data |
| **H. Charges or uses on an item.** | SRD | SRD | identical; built (`plan-charges-and-casting.md`) |
| **I. Another resource** (ki or sorcery points) | Book: Way of the Four Elements, Shadow Arts (PHB) | none found as spell casting in SRD 5.2.1 | homebrew only for now |
| **J. Ritual only.** | Book: Ritual Caster (PHB); *check* Book of Ancient Secrets in SRD 5.1 | *check* Pact of the Tome | both |

**Attributes that ride on a mode,** each seen in the text above:

- **Count:** a number (1; Favored Enemy: 2), or proficiency bonus (Forest Gnome, 521:7854).
- **Reset:** long rest (most), short or long rest (Signature Spells; Fey Teleportation), dawn (items).
- **Fixed cast level:** Tiefling's Hellish Rebuke at 2nd, Fiendish Vigor at 1st, Signature Spells
  at 3rd, Spell Mastery at lowest. Everyone else casts at the spell's level, or upcasts with a slot
  where slots are allowed.
- **Minimum character level** before it applies (Tiefling: 3rd and 5th; lineages: 3 and 5).
- **"Always prepared" (2024) versus "know" (2014):** 2024 grants say the spell is always prepared,
  which is what lets slots reach it.

**What the overlap means for the data:** modes A, B, C, D, E, H and J exist in both editions; F is
2014-only; D spans late 2014 and 2024. A grant's mode therefore cannot be inferred from its edition,
its source kind (race, feat, class) or its era. **Each grant states its mode.** The modes reduce to
two independent answers plus the attributes:

- **Free casts:** none, N per reset, or at will (with an optional fixed level).
- **Slot casting:** any slots, none, or a slot spent only N per reset (mode F).

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
{:free  {:count 1                          ; or :proficiency-bonus, or :at-will
         :reset ::units5e/long-rest        ; the units5e periods rests already clear
         :level 2}                         ; fixed cast level; absent = the spell's own
 :slots :any}                              ; :any | :none | {:count 1 :reset ::units5e/long-rest}
```

Mode by mode: A is no `:casting` at all (today's behaviour); C `{:free {...} :slots :none}`;
D `{:free {...} :slots :any}`; E `{:free {:count :at-will} :slots :any}` (upcasting spends a slot,
as Spell Mastery says) or `:slots :none`; F `{:slots {:count 1 :reset ...}}`; B needs nothing
(cantrips); G and I stay text; H is the item's own `::mi/casts`.

- **The rule every reader uses** goes in one cljc function (beside `shown-picture`'s kind of rule),
  e.g. `char5e/spell-payment cfg` returning which of free, at-will and slot a grant allows. The PDF and the app ask it;
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
2. **Built-in data:** 2014 SRD content states its `:casting` by mode: Tiefling (C, fixed 2nd),
   Mystic Arcanum (C), the at-will invocations (E), Mire the Mind and Sign of Ill Omen (F), Spell
   Mastery (E), Magic Initiate (C or D, owner's call), Drow (C). 2024 data on `srd52/develop` does the same when its races land.
3. **PDF:** slots only where `spell-payment` says slot; uses after the name. Fixes the reported bug.
4. **Spells tab display:** per-source uses, "remaining" only where slots apply.
5. **Tracking and casting:** the free-use tick, then the cast panel's payment choice.
6. **Homebrew:** an optional mode (free casts: count, reset, level; slot casting: any, none, N per reset) on the `known-spells` row
   (`homebrew_shapes.cljc`, backward compatible: absent means today's behaviour), and a spell row for
   feats (the silos gap), through the same field.

Steps 1-3 are the fix for the reported PDF bug; 4-6 are the app side. Steps 1-4 touch no saved
data; 5 adds per-grant uses to the character (schema); 6 adds homebrew fields (the shape table).

## Open, for the owner

- Which branch carries steps 1-4 (`fix/pdf-export` is the PDF bug's branch; the app side is wider).
- Whether the 2014 Magic Initiate follows its printed text (free use only) or the ruling (slots
  too).
