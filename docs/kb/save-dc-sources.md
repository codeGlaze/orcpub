# Save DCs: which features use which

Two template props compute a DC (`template_base.cljc`, fixed on `feature/weapons-table` in `d1403c75`):

- `?save-dc (fn [ability])` = 8 + proficiency + that ability's modifier.
- `?spell-save-dc (fn [ability])` = `?save-dc` + `?spell-save-dc-bonus`.

`?spell-save-dc-bonus` is written only by magic items (`mod5e/spell-save-dc-bonus`, from
`magic_items.cljc`: the item builder's spell DC field, and the Robe of the Archmagi). An item that
raises "your spell save DC" therefore reaches exactly the features that use `?spell-save-dc`.

**Rule:** a feature uses `?spell-save-dc` only when its own text says "your spell save DC".
Otherwise it uses `?save-dc`. Before the fix every feature used `?spell-save-dc`, so a robe raised
a monk's ki DC. `save_dc_test.clj` pins both sides (Stunning Strike, Turn Undead).

| Feature | Uses | Source text |
|---|---|---|
| Channel Divinity: Turn Undead, Turn the Unholy | spell | "equals your cleric / paladin spell save DC" |
| Nature's Sanctuary | spell | "against your spell save DC" |
| Draconic Presence | spell | "against your sorcerer spell save DC" |
| Breath Weapon (dragonborn, `spell_subs.cljs`) | own | 8 + Con + proficiency |
| Intimidating Presence | own | 8 + proficiency + Cha |
| Stunning Strike, Open Hand Technique, Quivering Palm, Fist of Unbroken Air, Water Whip | own | ki save DC = 8 + proficiency + Wis |
| Tranquility | own | 8 + Wis + proficiency |
| Weapon Mastery: Topple (2024) | own, by the attack | "DC 8 plus the ability modifier used to make the attack roll and your Proficiency Bonus" (SRD 5.2.1) |

**Not checked, left on `?spell-save-dc`:** UA Mystic disciplines (`templates/ua_mystic.cljc`, about
25 uses) and UA Arcane Archer shots (`templates/ua_options.cljc`). Their source text is not in the
repo. If either defines its own DC, move it to `?save-dc`.

**Known gap, not fixed:** an item bonus limited to one class's spells (Rod of the Pact Keeper:
"your warlock spells") still raises every spell DC, because `?spell-save-dc-bonus` has no class.
