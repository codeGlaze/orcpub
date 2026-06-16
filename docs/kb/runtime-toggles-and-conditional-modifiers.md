# Runtime toggles and conditional modifiers — how equipped state feeds the sheet

**What this answers:** can a player-controlled toggle change a *computed* stat on the
character sheet, or is the sheet a pure function of the build? This matters for any
"while in state X you get bonus Y" feature where only the player knows when X is true
(rage, bloodied, a stance). The answer: yes, the channel exists, and equipped armor +
magic items already use it.

All facts below were read from the code on this branch (file:line cited). Verified.

## The mechanism
1. **The toggle is stored in the entity.** `toggle-inventory-item-equipped`
   (`events.cljs:1386`) is just:
   ```clojure
   (update-in character
     [::entity/options selection-key item-index ::entity/value ::char-equip5e/equipped?]
     not)
   ```
   So `::char-equip5e/equipped?` is a flag on the item *inside the character entity*. A
   player flipping "equipped" mutates the entity.

2. **The sheet recomputes from the entity.** The built character is
   `(entity/build character built-template)` (`subs.cljs:310`). When the entity changes,
   the derived sheet is recomputed. There is no separate "apply toggle" path — the toggle
   is build input, and the build re-runs.

3. **Modifiers are gated behind the flag by a deferred modifier.**
   `deferred-magic-item-fn` (`modifiers.cljc:467`):
   ```clojure
   (fn [cfg]
     (let [equipment-mod (equipment-mod-fn cfg)]
       (if (::char-equip/equipped? cfg)              ; the gate
         (concat [equipment-mod]
                 (when (and include-magic-bonus? magical-ac-bonus)
                   [(mods/cum-sum-mod ?magical-ac-bonus magical-ac-bonus)])
                 modifiers)                            ; the item's arbitrary :modifiers
         equipment-mod)))                              ; unequipped: inventory entry only, no bonus
   ```
   When the item is equipped, its `magical-ac-bonus` **and its arbitrary `:modifiers`**
   apply. When not, they don't. This is wrapped in `mods/deferred-modifier`
   (`orcpub/modifiers.cljc:59`, a macro), which defers evaluating the function until the
   item's stored cfg (carrying `equipped?`) is resolved. Used by `magic-item`
   (`modifiers.cljc:517`), `deferred-magic-armor` (`:496`), `deferred-magic-weapon` (`:481`).

## Armor (build-state condition) works similarly but auto-evaluated
`?armor-class-with-armor` is a **function** modifier taking `[armor shield]`
(`options.cljc:3334`, `template.cljc:284`). It's evaluated against the equipped
armor/shield, so AC changes with what's equipped. Fighting styles wrap it — Defense adds
+1 while wearing armor, Mariner adds +1 while not in heavy armor and no shield — by
composing over that same function. Here the condition (armor type) is read from the
build, so it needs no player toggle; the function just inspects the equipped armor.

## What this means for conditional / "while active" features
- **The toggle-gated-modifier pattern exists in production.** A magic item carries
  arbitrary `:modifiers`; they apply only when `equipped?`; the player controls
  `equipped?`; the sheet recomputes. That is exactly "a player toggle that applies a
  mechanical benefit."
- **A homebrew "while X" feature (rage-style) can reuse this pattern**: a feature with
  modifiers gated behind an `active?` flag, a toggle event that flips the flag in the
  entity, and a deferred modifier that reads it. The remaining work to generalize:
  (a) a place to store arbitrary feature-toggle flags not tied to an equipment item,
  (b) a generic toggle event + UI control, (c) a deferred modifier that reads them.
  The engine capability is already there; what's missing is a non-equipment home for the
  flag and a generic authoring path.

## Boundaries (what this does NOT do)
- **Static modifiers only.** Gating changes *which static modifiers apply*. It does not
  execute triggered/reaction behavior ("when you hit, the target makes a save"). The app
  computes a sheet; it has no combat runtime to fire a reaction in.
- **Play-state vs build-state conditions:** build-state (armor type, ability score,
  class/level) auto-evaluates because the build knows it. Play-state (rage, bloodied,
  positioning) needs a toggle because the sheet has no current-HP / battlefield / turn
  state. Bloodied is play-state — the builder computes max HP, not current HP.
- **Current toggles are per-equipment-item** (`equipped?`). A general condition toggle
  needs its own flag storage; it can't ride the equipment `equipped?` field.

## Design implication (the condition/benefit registry idea)
A "benefit + condition" effect splits into three gate types, all sharing one benefit
(modifier) vocabulary:
- build-state condition → auto-predicate (function modifier reads the build), applies for real;
- play-state condition with a static benefit → player toggle (the equipped?/deferred pattern), applies when on;
- play-state condition with no static benefit (positioning, reactions) → description only, nothing to compute.
The first two produce working sheet math today's engine supports; the third is text.
