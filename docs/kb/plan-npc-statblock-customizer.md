# Plan: NPC statblock customizer

*Not built. Fall Update candidate, a separate leaf from the Extras work. Line references
against `agents/develop` at `24dd15f7`.*

The books tell a DM to "simply" adjust a humanoid statblock's ability scores to reskin an NPC.
It is not simple: one score change cascades through six or more derived values, and the DM is
doing it at the table while five people wait.

## The cascade

Change one ability score and these move:

| Change | Recomputes |
| --- | --- |
| **CON** | HP. The `modifier` is `die-count × CON mod`, not the bare mod — the most commonly botched one |
| **DEX** | AC (unarmoured or light armour only), initiative, DEX saves and skills, ranged attack and damage |
| **STR** | Melee attack and damage, STR saves and skills, carrying capacity |
| **WIS** | Passive Perception, WIS saves and skills |
| **Casting ability** | Save DC (`8 + PB + mod`) and spell attack (`PB + mod`) |

And the part that makes it genuinely hard rather than merely tedious: **proficiency bonus
comes from CR, and those changes move CR**, which moves PB, which moves attacks, saves and
DCs again. It is a small fixed-point problem, which is exactly what a person cannot do
between two initiative counts and a computer does instantly.

## What the app has

- **A monster builder page exists** — `monster-builder` (`views.cljs:7584`), routed, with a
  homebrew monster content type and spec.
- **It derives nothing.** `monsters.cljc` contains one function, `monster-subheader`. AC, HP
  mean, attack bonuses, save DCs and skill modifiers are all typed in by hand and stored
  verbatim.
- The only dice arithmetic near monsters is the encounter tracker rolling individual HP
  (`events.cljs:3353`), which reads `die`/`die-count`/`modifier` rather than computing them.

So the builder is a form, not a calculator. **The feature is derivation, not a new page.**

## Why it fits here

The character side already does exactly this kind of work. `entity/build` and
`modifiers.cljc` derive a character's AC, attack bonuses, save DCs and proficiency-scaled
values from base scores. A monster is a smaller instance of the same problem, and
`?proficiency-bonus` already exists.

It also shares a shape with the Extras work: a customised NPC is a statblock **copied from a
source and then diverged** — same as a table picking "close enough" Wolf for their pet fox and
editing from there. Both want copy-on-adopt with the source recorded, not a live reference.

## Open

- **Which edition's statblock?** 2024 changed the layout and some derivations. Not a gate —
  the site supports both and mixing between them, so both get implemented. Open only as to
  which is default and how a table overrides it.
- **Does CR recompute, or is it advisory?** Full CR derivation (defensive and offensive CR,
  averaged) is a substantial calculation with judgement calls in it. A first version could
  recompute everything downstream of PB but leave CR as a field the DM sets, flagging when
  the numbers have drifted from what the CR implies.
- **Reskin vs build-from-scratch.** Starting from an existing statblock and swapping scores is
  the stated use case; a blank-slate builder is a different and larger feature.
