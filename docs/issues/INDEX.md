# OrcPub Open Issues Tracker

> **167 open issues** | Synced: 2026-02-24
> Source: [Orcpub/orcpub](https://github.com/Orcpub/orcpub/issues)

## Overview

| Domain | Doc | Bugs | Enhancements | Total |
|--------|-----|------|-------------|-------|
| Homebrew Builders | [homebrew-builders.md](homebrew-builders.md) | 18 | 57 | 75 |
| Application UX | [application-ux.md](application-ux.md) | 15 | 23 | 38 |
| Spellcasting | [spellcasting.md](spellcasting.md) | 13 | 7 | 20 |
| Character Sheet & PDF | [character-sheet-pdf.md](character-sheet-pdf.md) | 4 | 14 | 18 |
| Platform & Data Exchange | [platform-and-data.md](platform-and-data.md) | 1 | 15 | 16 |
| **Total** | | **51** | **116** | **167** |

---

## Triage — Priority Items

### Critical (blocking UX)

| # | Title | Domain | Assessment |
|---|-------|--------|------------|
| [#621](https://github.com/Orcpub/orcpub/issues/621) | Extreme UI Freezing With Excessive Custom Content | Character Builder UX | Core UX blocker — UI freezes with large homebrew collections; needs virtual scroll or lazy rendering |
| [#614](https://github.com/Orcpub/orcpub/issues/614) | Features with the name `Null` break things | Character Builder UX | JS null coercion — feature names matching 'Null' cause silent failures |

### Quick Wins (small effort, clear path)

| # | Title | Domain | Assessment |
|---|-------|--------|------------|
| [#547](https://github.com/Orcpub/orcpub/issues/547) | Remove Non-SRD human subraces | Homebrew Content Requests | Just remove non-SRD human subraces from template data |
| [#520](https://github.com/Orcpub/orcpub/issues/520) | Order Spell Cards by Level Then Alphabet | Spellcasting | Simple sort comparator change for spell cards |
| [#84](https://github.com/Orcpub/orcpub/issues/84) | Submit login info via Enter/Return key press | Account / Platform | Add keydown handler for Enter on login form |
| [#555](https://github.com/Orcpub/orcpub/issues/555) | Are the notes supposed to be printable? | Character Sheet / PDF | Notes field exists but print CSS may exclude it |
| [#304](https://github.com/Orcpub/orcpub/issues/304) | Sort by Level in Class/Level Tab | Character Builder UX | Class/Level tab not sorted by level |
| [#548](https://github.com/Orcpub/orcpub/issues/548) | Bug: Dropdown Selection Don't Recognize '1' unless changed and changed back. | Character Builder UX | Dropdown value '1' treated as truthy/default, not as selection |

### High-Value Bugs (user-facing, reproducible)

| # | Title | Domain | Assessment |
|---|-------|--------|------------|
| [#553](https://github.com/Orcpub/orcpub/issues/553) | Multiclass Requirements not Enforced | Character Builder UX | Multiclass stat requirements exist in data but not enforced in UI |
| [#402](https://github.com/Orcpub/orcpub/issues/402) | Exception when creating PDF's | Character Sheet / PDF | PDF generation throws exception for certain character configs |
| [#267](https://github.com/Orcpub/orcpub/issues/267) | Druid subclass spells granted by modifiers do not show as always prepared | Spellcasting | Druid subclass modifier-granted spells lack always-prepared flag |
| [#202](https://github.com/Orcpub/orcpub/issues/202) | Prepared Spells checkboxes in PDF are cross-linked | Spellcasting | PDF checkbox fields share form names, checking one checks another |
| [#295](https://github.com/Orcpub/orcpub/issues/295) | Long Rest Button not Saving new HP value | Character Builder UX | Long rest HP update not persisted |
| [#310](https://github.com/Orcpub/orcpub/issues/310) |  Short Rest Button Missing (Bards) | Character Builder UX | Bard short rest button missing from character sheet actions |
| [#309](https://github.com/Orcpub/orcpub/issues/309) | "Wearing medium armor doesn't give disadvantage on Stealth checks" does not work | Character Builder UX | Medium armor stealth disadvantage flag not working |
| [#585](https://github.com/Orcpub/orcpub/issues/585) | Schedule 4 Spellcasting not Preparing correct # of Spells | Spellcasting | Schedule 4 prepared spell count calculation wrong |
| [#437](https://github.com/Orcpub/orcpub/issues/437) | Wizard/cleric multi class spell slot issues | Spellcasting | Multiclass spell slot calculation incorrect for Wizard/Cleric combo |
| [#435](https://github.com/Orcpub/orcpub/issues/435) | Artificer multiclassing rounds down instead of up | Spellcasting | Artificer half-caster rounding goes wrong direction |
| [#47](https://github.com/Orcpub/orcpub/issues/47) | [Bug] Prepared Spellcasters Interfere with Multiclass Spell Selection | Spellcasting | Prepared caster spell lists interfere with multiclass selections |
| [#338](https://github.com/Orcpub/orcpub/issues/338) | Combat Tracker / Party editing causes logout | Combat Tracker | Party edit in combat tracker triggers auth state loss |
| [#192](https://github.com/Orcpub/orcpub/issues/192) | PDF features & traits page cutoff | Character Sheet / PDF | Features & Traits section overflows PDF page boundary |
| [#549](https://github.com/Orcpub/orcpub/issues/549) | Cognitect no longer providing Datomic free versions on their website | Account / Platform | Datomic Free no longer downloadable from Cognitect — need alternative |

---

## Homebrew Builders (75 issues)

> Deep dive: [homebrew-builders.md](homebrew-builders.md)

### Homebrew Builders (62)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#636](https://github.com/Orcpub/orcpub/issues/636) | Homebrew class spells | bug | @goofnoodleh | 2025-12-05 | Homebrew class spell list assignment broken |
| [#560](https://github.com/Orcpub/orcpub/issues/560) | Custom Item and Proficiency issue | bug | @lafranst | 2022-03-02 | Custom item proficiency not applied correctly |
| [#501](https://github.com/Orcpub/orcpub/issues/501) | Homebrew button when choosing proficiencys can't be turned off. | bug | @Firebolt752 | 2021-01-08 | Homebrew toggle in proficiency picker stuck on |
| [#453](https://github.com/Orcpub/orcpub/issues/453) | Partial Deletion of Custom Content | bug | @BugReporterGuy | 2020-09-21 | Can't delete individual items from custom content collection |
| [#324](https://github.com/Orcpub/orcpub/issues/324) | Custom items based off weapons display as weapon choices in starting equipment selections | bug | @DivertedCircle | 2020-03-06 | Custom weapon-based items appear in starting equipment weapon choices |
| [#282](https://github.com/Orcpub/orcpub/issues/282) | Custom Magic Weapons are not applying Magical AC Bonus | bug | @DivertedCircle | 2019-12-11 | Magical weapon AC bonus not calculated |
| [#275](https://github.com/Orcpub/orcpub/issues/275) | Class / Level Brew button doesn't seem to do anything | bug | @DivertedCircle | 2019-12-03 | Class/Level Brew button click handler broken or missing |
| [#274](https://github.com/Orcpub/orcpub/issues/274) | Languages section in the Subrace Builder is misleading @datdamnzotz | bug | @DivertedCircle | 2019-12-03 | Subrace builder language section implies wrong behavior |
| [#260](https://github.com/Orcpub/orcpub/issues/260) | Selections don't nest when using Class Builder @codeGlaze | bug | @DivertedCircle | 2019-11-07 | Nested selections in class builder don't render properly |
| [#225](https://github.com/Orcpub/orcpub/issues/225) | Bug with the attunement feature of the item creator | bug | @TrevanBlue | 2019-08-25 | Item attunement flag not saved/applied correctly |
| [#174](https://github.com/Orcpub/orcpub/issues/174) | Problem with Selection Builder @codeGlaze | bug | @nSword0 | 2019-05-05 | Selection builder UI broken for certain option configurations |
| [#55](https://github.com/Orcpub/orcpub/issues/55) | Subclass Proficiencies persist | bug | @ashf | 2019-01-22 | Subclass proficiencies persist after subclass change |
| [#39](https://github.com/Orcpub/orcpub/issues/39) | Issues when making selections | bug | @admanct | 2019-01-19 | Selection UI breaks on certain option combinations |
| [#36](https://github.com/Orcpub/orcpub/issues/36) | Custom Items always filed as magical | bug | @codeGlaze | 2019-01-19 | Custom items always tagged as magical regardless of setting |
| [#579](https://github.com/Orcpub/orcpub/issues/579) | Can't create Bloodwell Vial due to it giving +1 to spellsave DC and spell attack bonus | enh | @potagon | 2022-10-17 |  |
| [#509](https://github.com/Orcpub/orcpub/issues/509) | Better Item Property Integration | enh | @codeGlaze | 2021-01-30 |  |
| [#486](https://github.com/Orcpub/orcpub/issues/486) | Implement the ability to have nested Classes/Items - Enhance Selection Creation | enh | @MasterMcDonald1 | 2020-12-03 |  |
| [#478](https://github.com/Orcpub/orcpub/issues/478) | [Feature Request] - Custom AC Modifiers | enh | @WoernerBro | 2020-11-14 |  |
| [#465](https://github.com/Orcpub/orcpub/issues/465) | [Feature Request] - Add a Darkvision Modifier | enh | @WoernerBro | 2020-10-24 |  |
| [#419](https://github.com/Orcpub/orcpub/issues/419) | Feature Request: Add a 'Description' section for Subclasses | enh | @DivertedCircle | 2020-07-11 |  |
| [#369](https://github.com/Orcpub/orcpub/issues/369) | Class and Subclass Builder Language Selection Feature | enh | @ghul13 | 2020-04-29 |  |
| [#308](https://github.com/Orcpub/orcpub/issues/308) | Add +1 Armor to race builder for homebrew | enh | @tombomb | 2020-02-07 |  |
| [#307](https://github.com/Orcpub/orcpub/issues/307) | Class to override weapon modifer | enh | @tombomb | 2020-02-07 |  |
| [#280](https://github.com/Orcpub/orcpub/issues/280) | Metamagic Builder `2.5.0.?` @marloso2 | enh | @marloso2 | 2019-12-09 |  |
| [#258](https://github.com/Orcpub/orcpub/issues/258) | Add additional options to Subrace Editor | enh | @Jader7777 | 2019-11-01 |  |
| [#246](https://github.com/Orcpub/orcpub/issues/246) | Option to modify SRD content. | enh | @TheWanderingLynx | 2019-10-06 |  |
| [#234](https://github.com/Orcpub/orcpub/issues/234) | Personality Traits as modifiers in Background content | enh | @RobinLefebvre | 2019-09-16 |  |
| [#211](https://github.com/Orcpub/orcpub/issues/211) | [Suggestion] Add 'Spell Attack Bonus' and 'Spell Damage Bonus' to Item Builder | enh | @DivertedCircle | 2019-07-21 |  |
| [#210](https://github.com/Orcpub/orcpub/issues/210) | [Suggestion] Add 'Spells Known' modifier to Class Builder | enh | @DivertedCircle | 2019-07-21 |  |
| [#209](https://github.com/Orcpub/orcpub/issues/209) | [Feature Request] Requirements for Invocations & hide with requirements not met. | enh | @Corprall | 2019-07-21 |  |
| [#200](https://github.com/Orcpub/orcpub/issues/200) | [Suggestion] Tool Expertise for homebrew Class/Subclass | enh | @aceofknits | 2019-07-09 |  |
| [#199](https://github.com/Orcpub/orcpub/issues/199) | [Suggestion] Add Multiclass Stat Requirement to Class Builder | enh | @DivertedCircle | 2019-07-09 |  |
| [#190](https://github.com/Orcpub/orcpub/issues/190) | [Suggestion] Saving Throw Proficiency missing from Subclass Builder | enh | @DivertedCircle | 2019-06-08 |  |
| [#188](https://github.com/Orcpub/orcpub/issues/188) | [Suggestion] Expand Druid and Sorcerer Subclass Builder | enh | @DivertedCircle | 2019-06-03 |  |
| [#184](https://github.com/Orcpub/orcpub/issues/184) | Make feature section for homebrew classes/races more like it is with already added ones. | enh | @NitroFire90 | 2019-05-29 |  |
| [#177](https://github.com/Orcpub/orcpub/issues/177) | [Suggestion] Custom AC Calculations @codeGlaze | enh | @nSword0 | 2019-05-12 |  |
| [#173](https://github.com/Orcpub/orcpub/issues/173) | I would like to request the option to create your own school of magic | enh | @StropeyVonLol | 2019-05-03 |  |
| [#172](https://github.com/Orcpub/orcpub/issues/172) | Add selection support for Feat builder | enh | @app/ | 2019-05-01 |  |
| [#170](https://github.com/Orcpub/orcpub/issues/170) | Add choice support in subclass builder | enh | @app/ | 2019-04-28 |  |
| [#163](https://github.com/Orcpub/orcpub/issues/163) | Add “required” field to all home brew types | enh | @willfaulds | 2019-04-15 |  |
| [#161](https://github.com/Orcpub/orcpub/issues/161) | [Suggestion] Weapon Type Builder | enh | @DivertedCircle | 2019-04-12 |  |
| [#158](https://github.com/Orcpub/orcpub/issues/158) | [Suggestion] Add Choice of Weapon Proficiency to Subrace Builder | enh | @DivertedCircle | 2019-04-12 |  |
| [#157](https://github.com/Orcpub/orcpub/issues/157) | [Suggestion] Add Tool Proficiency Choice to Race and Subrace Builders | enh | @DivertedCircle | 2019-04-12 |  |
| [#132](https://github.com/Orcpub/orcpub/issues/132) | Skill/Tool training homebrew. | enh | @Butterfly-Dragon | 2019-03-29 |  |
| [#131](https://github.com/Orcpub/orcpub/issues/131) | Better Feat Specialization Implementation | enh | @Butterfly-Dragon | 2019-03-29 |  |
| [#130](https://github.com/Orcpub/orcpub/issues/130) | Proficiency retraining | enh | @Butterfly-Dragon | 2019-03-29 |  |
| [#128](https://github.com/Orcpub/orcpub/issues/128) | [Suggestion] Add 'Choice of Ability Score Increase' option to the Race Builder | enh | @DivertedCircle | 2019-03-28 |  |
| [#123](https://github.com/Orcpub/orcpub/issues/123) | Copy/Hide: Alternative to editing Non-homebrew Content | enh | @Rocjawcypher | 2019-03-23 |  |
| [#113](https://github.com/Orcpub/orcpub/issues/113) | Editable race features *without* creating a new race | enh | @Butterfly-Dragon | 2019-03-20 |  |
| [#107](https://github.com/Orcpub/orcpub/issues/107) | Add Subclass to change/add other class spells to spell list. | enh | @TheWanderingLynx | 2019-03-16 |  |
| [#101](https://github.com/Orcpub/orcpub/issues/101) | Allow subclass selections to modify character stats | enh | @chrismoore | 2019-03-05 |  |
| [#88](https://github.com/Orcpub/orcpub/issues/88) | [Suggestion] Add 'Saving Throw Advantage' to the Race Builder | enh | @DivertedCircle | 2019-02-20 |  |
| [#87](https://github.com/Orcpub/orcpub/issues/87) | [Suggestion] Add 'Saving Throw Advantage against Disease' to Subrace Builder | enh | @DivertedCircle | 2019-02-20 |  |
| [#86](https://github.com/Orcpub/orcpub/issues/86) | [Suggestion] Allow Homebrew Language Choice in Race Builder | enh | @DivertedCircle | 2019-02-20 |  |
| [#69](https://github.com/Orcpub/orcpub/issues/69) | add proficiency bonuses to HB items | enh | @MWarrener | 2019-01-30 |  |
| [#58](https://github.com/Orcpub/orcpub/issues/58) | [Suggestion] Eldritch Invocations for Homebrew Classes | enh | @DivertedCircle | 2019-01-22 |  |
| [#57](https://github.com/Orcpub/orcpub/issues/57) | [Suggestion] Prerequisites for Eldritch Invocation Build | enh | @DivertedCircle | 2019-01-22 |  |
| [#56](https://github.com/Orcpub/orcpub/issues/56) | [Suggestion] Feat Prerequisites for Feat Builder | enh | @DivertedCircle | 2019-01-22 |  |
| [#50](https://github.com/Orcpub/orcpub/issues/50) | [Suggestion] Flesh out the Feat Builder | enh | @DivertedCircle | 2019-01-22 |  |
| [#38](https://github.com/Orcpub/orcpub/issues/38) | Homebrew input does not support line breaks or markup | enh | @codeGlaze | 2019-01-19 |  |
| [#37](https://github.com/Orcpub/orcpub/issues/37) | Cannot replace default attributes to hit/dmg | enh | @codeGlaze | 2019-01-19 |  |
| [#34](https://github.com/Orcpub/orcpub/issues/34) | Class Build Spellcasting Upgrade | enh | @Nexusflamehart | 2019-01-18 | Spellcasting upgrade for class builder — longstanding request |

### Homebrew Content Requests (13)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#612](https://github.com/Orcpub/orcpub/issues/612) | Champion, Remarkable Athlete; should affect initiative | bug | @rogue-ronin | 2024-07-19 | Champion's Remarkable Athlete should add half-proficiency to initiative |
| [#189](https://github.com/Orcpub/orcpub/issues/189) | [Bug] Draconic Sorcerer Missing Bonus HP | bug | @DivertedCircle | 2019-06-06 | Draconic Sorcerer HP bonus not applied per level |
| [#182](https://github.com/Orcpub/orcpub/issues/182) | Half-elf with drow heritage | bug | @demidestsp | 2019-05-28 | Half-elf drow heritage subrace options missing/broken |
| [#181](https://github.com/Orcpub/orcpub/issues/181) | Fighter Manuevers | bug | @Micagoldstone | 2019-05-24 | Fighter maneuver selections incomplete or broken |
| [#590](https://github.com/Orcpub/orcpub/issues/590) | Please Add Unarmored Defense to Customization @codeGlaze | enh | @MondaysAreShit | 2023-04-24 |  |
| [#556](https://github.com/Orcpub/orcpub/issues/556) | Subrace Advantage to Poison not documented | enh | @lafranst | 2022-02-17 |  |
| [#547](https://github.com/Orcpub/orcpub/issues/547) | Remove Non-SRD human subraces | enh | @datdamnzotz | 2021-12-09 | Just remove non-SRD human subraces from template data |
| [#487](https://github.com/Orcpub/orcpub/issues/487) | Artificer Spell List | enh | @himjang94 | 2020-12-04 |  |
| [#312](https://github.com/Orcpub/orcpub/issues/312) |  Book of Ancient Secrets Rituals, is missing rituals | enh | @guppy42 | 2020-02-24 | Book of Ancient Secrets pact missing ritual spells from other classes |
| [#272](https://github.com/Orcpub/orcpub/issues/272) | Artificer Spells Known `2.5.0.?` | enh | @marloso2 | 2019-12-02 | Artificer spells known progression not implemented |
| [#216](https://github.com/Orcpub/orcpub/issues/216) | [Enhancment] Hobgoblin Weapon Proficiency | enh | @C77out | 2019-07-30 |  |
| [#112](https://github.com/Orcpub/orcpub/issues/112) | Special Armors @codeGlaze | enh | @Butterfly-Dragon | 2019-03-20 |  |
| [#81](https://github.com/Orcpub/orcpub/issues/81) | Elf with High Elf heritage | enh | @TonyHeflin | 2019-02-11 |  |

## Application UX (38 issues)

> Deep dive: [application-ux.md](application-ux.md)

### Character Builder UX (34)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#621](https://github.com/Orcpub/orcpub/issues/621) | Extreme UI Freezing With Excessive Custom Content | bug | @codeGlaze | 2025-08-24 | Core UX blocker — UI freezes with large homebrew collections; needs virtual scroll or lazy rendering |
| [#614](https://github.com/Orcpub/orcpub/issues/614) | Features with the name `Null` break things | bug | @codeGlaze | 2024-12-15 | JS null coercion — feature names matching 'Null' cause silent failures |
| [#553](https://github.com/Orcpub/orcpub/issues/553) | Multiclass Requirements not Enforced @codeGlaze | bug | @DivertedCircle | 2022-02-03 | Multiclass stat requirements exist in data but not enforced in UI |
| [#548](https://github.com/Orcpub/orcpub/issues/548) | Bug: Dropdown Selection Don't Recognize '1' unless changed and changed back. @codeGlaze | bug | @DivertedCircle | 2021-12-09 | Dropdown value '1' treated as truthy/default, not as selection |
| [#526](https://github.com/Orcpub/orcpub/issues/526) | Eldritch Invocation selector is in the wrong tab | bug | @DivertedCircle | 2021-03-26 | Eldritch Invocation selector renders in wrong builder tab |
| [#340](https://github.com/Orcpub/orcpub/issues/340) | Multiple identical weapons are displayed as one. @sudonotpseudo | bug | @AdamEternal | 2020-03-22 | Multiple copies of same weapon collapsed to single display |
| [#320](https://github.com/Orcpub/orcpub/issues/320) | Display sources  for content | bug | @datdamnzotz | 2020-02-29 | Content source attribution not shown for options |
| [#310](https://github.com/Orcpub/orcpub/issues/310) |  Short Rest Button Missing (Bards) | bug | @AsterITA | 2020-02-13 | Bard short rest button missing from character sheet actions |
| [#309](https://github.com/Orcpub/orcpub/issues/309) | "Wearing medium armor doesn't give disadvantage on Stealth checks" does not work @codeGlaze | bug | @tombomb | 2020-02-07 | Medium armor stealth disadvantage flag not working |
| [#304](https://github.com/Orcpub/orcpub/issues/304) | Sort by Level in Class/Level Tab | bug | @DivertedCircle | 2020-02-01 | Class/Level tab not sorted by level |
| [#296](https://github.com/Orcpub/orcpub/issues/296) | Language Selection Bug in Character Builder | bug | @Roningold | 2020-01-03 | Language picker has selection bug |
| [#295](https://github.com/Orcpub/orcpub/issues/295) | Long Rest Button not Saving new HP value | bug | @DivertedCircle | 2019-12-31 | Long rest HP update not persisted |
| [#138](https://github.com/Orcpub/orcpub/issues/138) | Items duplicating items from equipment pack are not displayed (e.g. rations) | bug | @sehrgut | 2019-04-06 | Equipment pack items duplicated when same item added individually |
| [#108](https://github.com/Orcpub/orcpub/issues/108) | Proficiencies from backgrounds are applied before those from a class can be chosen | bug | @BlckKnght | 2019-03-17 | Background proficiency choices locked before class choices |
| [#546](https://github.com/Orcpub/orcpub/issues/546) | Add XP to whole party - Button | enh | @thelostscout | 2021-12-05 |  |
| [#488](https://github.com/Orcpub/orcpub/issues/488) | No "Add Invocation" button | enh | @Valdimarian | 2020-12-08 |  |
| [#475](https://github.com/Orcpub/orcpub/issues/475) |  Turn Option Source sections on and off | enh | @datdamnzotz | 2020-11-10 |  |
| [#452](https://github.com/Orcpub/orcpub/issues/452) | a humble request and or suggestion | enh | @The60Gunner | 2020-09-20 |  |
| [#442](https://github.com/Orcpub/orcpub/issues/442) | Hit Dice Checkboxes | enh | @BDeveau | 2020-08-31 |  |
| [#441](https://github.com/Orcpub/orcpub/issues/441) | Custom/Agnostic counter | enh | @kailinprime | 2020-08-26 |  |
| [#394](https://github.com/Orcpub/orcpub/issues/394) | Sneak Attack Button | enh | @ashf | 2020-05-19 |  |
| [#335](https://github.com/Orcpub/orcpub/issues/335) | Eliminate Proficiencies | enh | @ivanmbur | 2020-03-15 |  |
| [#254](https://github.com/Orcpub/orcpub/issues/254) | Sort and filter items | enh | @StropeyVonLol | 2019-10-29 |  |
| [#247](https://github.com/Orcpub/orcpub/issues/247) | [Suggestion] Monk Ki Point Tracker | enh | @Aiedail777 | 2019-10-07 |  |
| [#229](https://github.com/Orcpub/orcpub/issues/229) | [Suggestion] Item Quantity on View Screen | enh | @Myrnedraith | 2019-09-07 |  |
| [#207](https://github.com/Orcpub/orcpub/issues/207) | [Feature request] Configurable Collections for "Content List" | enh | @Corprall | 2019-07-20 |  |
| [#206](https://github.com/Orcpub/orcpub/issues/206) | [Feature request] Option to disable SRD content | enh | @Corprall | 2019-07-20 |  |
| [#201](https://github.com/Orcpub/orcpub/issues/201) | [Suggestion] Locking Previous HP Levels | enh | @nSword0 | 2019-07-11 |  |
| [#198](https://github.com/Orcpub/orcpub/issues/198) | Enhancement: Show option sources / fields to set source nicknames | enh | @codeGlaze | 2019-07-09 |  |
| [#191](https://github.com/Orcpub/orcpub/issues/191) | [Suggestion] Toggle Ability Choice for Finesse Weapons | enh | @DivertedCircle | 2019-06-11 |  |
| [#179](https://github.com/Orcpub/orcpub/issues/179) | A separate monk ability/spell window | enh | @sirteabag | 2019-05-13 |  |
| [#165](https://github.com/Orcpub/orcpub/issues/165) | Visual Design Improvements | enh | @pspeter3 | 2019-04-19 |  |
| [#121](https://github.com/Orcpub/orcpub/issues/121) | Search "Orcacle" should also work for homebrew content. | enh | @jonrick | 2019-03-21 |  |
| [#111](https://github.com/Orcpub/orcpub/issues/111) | Starting Packages | enh | @Butterfly-Dragon | 2019-03-20 |  |

### Combat Tracker (4)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#338](https://github.com/Orcpub/orcpub/issues/338) | Combat Tracker / Party editing causes logout | bug | @BillyKings173 | 2020-03-20 | Party edit in combat tracker triggers auth state loss |
| [#534](https://github.com/Orcpub/orcpub/issues/534) | [Feature Request] Add note field for monsters in combat tracker | enh | @Doskilos91 | 2021-05-23 |  |
| [#433](https://github.com/Orcpub/orcpub/issues/433) | [Feature Request] - Add a search function to the combat tracker “add monster” section. | enh | @datdamnzotz | 2020-07-21 |  |
| [#193](https://github.com/Orcpub/orcpub/issues/193) | Combat tracker for players | enh | @mcbloch | 2019-06-12 |  |

## Spellcasting (20 issues)

> Deep dive: [spellcasting.md](spellcasting.md)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#613](https://github.com/Orcpub/orcpub/issues/613) | Fey Touched / Shadow Touched should add to the Spells tab | bug | @rogue-ronin | 2024-07-19 | Feat-granted spells not linked to Spells tab; modifier wiring issue |
| [#585](https://github.com/Orcpub/orcpub/issues/585) | Schedule 4 Spellcasting not Preparing correct # of Spells | bug | @DivertedCircle | 2023-01-29 | Schedule 4 prepared spell count calculation wrong |
| [#576](https://github.com/Orcpub/orcpub/issues/576) | Custom Saved Spells don't show on Character Sheet | bug | @Kothira | 2022-09-18 | Custom spells saved but not displayed on character sheet view |
| [#574](https://github.com/Orcpub/orcpub/issues/574) | Some spellcasters list subclass cantrips in a separate category | bug | @Ghostthathauntsyou | 2022-08-29 | Subclass cantrips rendered in separate UI category instead of merged |
| [#439](https://github.com/Orcpub/orcpub/issues/439) | Created spells don't appear in Signature and Mastery | bug | @maxoku | 2020-08-23 | Created spells missing from Wizard Signature/Mastery selection |
| [#437](https://github.com/Orcpub/orcpub/issues/437) | Wizard/cleric multi class spell slot issues | bug | @SirGallethCooper | 2020-08-20 | Multiclass spell slot calculation incorrect for Wizard/Cleric combo |
| [#435](https://github.com/Orcpub/orcpub/issues/435) | Artificer multiclassing rounds down instead of up | bug | @IGTHORN | 2020-07-28 | Artificer half-caster rounding goes wrong direction |
| [#267](https://github.com/Orcpub/orcpub/issues/267) | Druid subclass spells granted by modifiers do not show as always prepared | bug | @DivertedCircle | 2019-11-28 | Druid subclass modifier-granted spells lack always-prepared flag |
| [#202](https://github.com/Orcpub/orcpub/issues/202) | Prepared Spells checkboxes in PDF are cross-linked | bug | @Trevael | 2019-07-11 | PDF checkbox fields share form names, checking one checks another |
| [#126](https://github.com/Orcpub/orcpub/issues/126) | Mystic Arcanum: Spells bug | bug | @Syegfryed | 2019-03-27 | Mystic Arcanum spell selection broken |
| [#110](https://github.com/Orcpub/orcpub/issues/110) | Leveling Down should cancel selected and prepared spells. | bug | @RobinLefebvre | 2019-03-19 | Leveling down doesn't deselect spells that exceed new known/prepared count |
| [#90](https://github.com/Orcpub/orcpub/issues/90) | Spells are not "refunded" when already known | bug | @BlckKnght | 2019-02-27 | Spell slots not reclaimed when known spell is removed |
| [#47](https://github.com/Orcpub/orcpub/issues/47) | [Bug] Prepared Spellcasters Interfere with Multiclass Spell Selection | bug | @DivertedCircle | 2019-01-21 | Prepared caster spell lists interfere with multiclass selections |
| [#571](https://github.com/Orcpub/orcpub/issues/571) | Race and Invocation Features should be reflected in Spell Damage and Spell Slot Management | enh | @LarsHill | 2022-07-20 | Race/invocation spell modifiers don't flow to damage/slot calculations |
| [#561](https://github.com/Orcpub/orcpub/issues/561) | Feature Request: Add Schedule 5 Spellcaster Progression @datdamnzotz | enh | @DivertedCircle | 2022-04-07 | New spellcasting progression schedule needed for third-casters |
| [#525](https://github.com/Orcpub/orcpub/issues/525) | Add a 'select all' feature for Spell Lists | enh | @Jader7777 | 2021-03-24 | Bulk spell selection UX improvement |
| [#522](https://github.com/Orcpub/orcpub/issues/522) | Create Spell Card PDFs without having to use the Character Builder tool | enh | @Jader7777 | 2021-03-20 | Spell cards currently require full character build flow |
| [#520](https://github.com/Orcpub/orcpub/issues/520) | Order Spell Cards by Level Then Alphabet @datdamnzotz | enh | @Jader7777 | 2021-03-20 | Simple sort comparator change for spell cards |
| [#440](https://github.com/Orcpub/orcpub/issues/440) | [Feature Request] Support spell points for class option per DMG pp. 288-89 | enh | @caewok | 2020-08-23 | DMG variant rule — alternative to spell slots |
| [#355](https://github.com/Orcpub/orcpub/issues/355) | Sort spells by level and filter by level/school/spell list | enh | @marloso2 | 2020-03-30 | Spell list needs filter/sort controls |

## Character Sheet & PDF (18 issues)

> Deep dive: [character-sheet-pdf.md](character-sheet-pdf.md)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#554](https://github.com/Orcpub/orcpub/issues/554) | Number of weapons carried not on PDF | bug | @lafranst | 2022-02-17 | Weapon count not rendered on PDF |
| [#402](https://github.com/Orcpub/orcpub/issues/402) | Exception when creating PDF's | bug | @datdamnzotz | 2020-06-17 | PDF generation throws exception for certain character configs |
| [#192](https://github.com/Orcpub/orcpub/issues/192) | PDF features & traits page cutoff | bug | @RobinLefebvre | 2019-06-12 | Features & Traits section overflows PDF page boundary |
| [#185](https://github.com/Orcpub/orcpub/issues/185) | Apostrophe's in print view have extra spacing | bug | @Rachayz | 2019-05-30 | Apostrophe characters cause extra whitespace in print view |
| [#651](https://github.com/Orcpub/orcpub/issues/651) | Feature Request: PDF Generation Tracker for User Profiles | enh | @datdamnzotz | 2026-02-22 |  |
| [#608](https://github.com/Orcpub/orcpub/issues/608) | #608 Extend Feature Boxes for IceWind/Mythos CS/Optional Variant `2.5.0.26` | enh | @Jessomadic | 2024-03-30 |  |
| [#559](https://github.com/Orcpub/orcpub/issues/559) | Dart stats not showing up properly on PDF | enh | @lafranst | 2022-03-02 |  |
| [#555](https://github.com/Orcpub/orcpub/issues/555) | Are the notes supposed to be printable? | enh | @lafranst | 2022-02-17 | Notes field exists but print CSS may exclude it |
| [#311](https://github.com/Orcpub/orcpub/issues/311) | Bulk print/export of Character List | enh | @CodyDuff | 2020-02-21 |  |
| [#169](https://github.com/Orcpub/orcpub/issues/169) | Better Spellsheet | enh | @Butterfly-Dragon | 2019-04-28 |  |
| [#160](https://github.com/Orcpub/orcpub/issues/160) | Access to the inaccessible note fields on the back side of the page | enh | @Halvo317 | 2019-04-12 |  |
| [#159](https://github.com/Orcpub/orcpub/issues/159) | Include item descriptions on printed sheet | enh | @Halvo317 | 2019-04-12 |  |
| [#118](https://github.com/Orcpub/orcpub/issues/118) | Party/NPC Campaign Sheet | enh | @Butterfly-Dragon | 2019-03-20 |  |
| [#117](https://github.com/Orcpub/orcpub/issues/117) | Add Character Sheet layout to pick from when creating pdf  - Single page character sheets | enh | @Butterfly-Dragon | 2019-03-20 |  |
| [#116](https://github.com/Orcpub/orcpub/issues/116) | Add Character Sheet layout to pick from when creating pdf - layout for skills | enh | @Butterfly-Dragon | 2019-03-20 |  |
| [#115](https://github.com/Orcpub/orcpub/issues/115) | change the temporary hit points field into a special speeds/resistances/immunities field | enh | @Butterfly-Dragon | 2019-03-20 |  |
| [#114](https://github.com/Orcpub/orcpub/issues/114) | Character Sheet checkboxes | enh | @Butterfly-Dragon | 2019-03-20 |  |
| [#96](https://github.com/Orcpub/orcpub/issues/96) | Notate tool expertise on PDF | enh | @sehrgut | 2019-03-02 |  |

## Platform & Data Exchange (16 issues)

> Deep dive: [platform-and-data.md](platform-and-data.md)

### Account / Platform (10)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#549](https://github.com/Orcpub/orcpub/issues/549) | Cognitect no longer providing Datomic free versions on their website | bug | @kapsterman | 2021-12-26 | Datomic Free no longer downloadable from Cognitect — need alternative |
| [#652](https://github.com/Orcpub/orcpub/issues/652) | 2026 Full-Stack Modernization `2.5.0.29` @codeGlaze | enh | @datdamnzotz | 2026-02-22 |  |
| [#569](https://github.com/Orcpub/orcpub/issues/569) | Bypass/disable email verification | enh | @gimzmoe | 2022-05-20 |  |
| [#399](https://github.com/Orcpub/orcpub/issues/399) | Change pop-up notifications to display once per 14-day login session. | enh | @DivertedCircle | 2020-06-11 |  |
| [#253](https://github.com/Orcpub/orcpub/issues/253) | Ability to add notes to characters shared with you | enh | @FredXVI | 2019-10-29 |  |
| [#252](https://github.com/Orcpub/orcpub/issues/252) | Character Sharing more than just the base character | enh | @FredXVI | 2019-10-29 |  |
| [#217](https://github.com/Orcpub/orcpub/issues/217) | [Feature Request] Twitch Stream Overlay similar to D&D Beyond | enh | @patcat127 | 2019-08-03 |  |
| [#208](https://github.com/Orcpub/orcpub/issues/208) | [Feature Request] Linking account to personal Cloud Storage to easily load orcbrew files | enh | @Corprall | 2019-07-20 |  |
| [#84](https://github.com/Orcpub/orcpub/issues/84) | Submit login info via Enter/Return key press @datdamnzotz | enh | @ashf | 2019-02-18 | Add keydown handler for Enter on login form |
| [#27](https://github.com/Orcpub/orcpub/issues/27) | Insecure repository for dependency | enh | @MeanderingCode | 2019-01-11 |  |

### Import / Export (6)

| # | Title | Type | Author | Date | Assessment |
|---|-------|------|--------|------|------------|
| [#567](https://github.com/Orcpub/orcpub/issues/567) | Feature Request: Tableplop Export | enh | @Kickball | 2022-04-25 |  |
| [#565](https://github.com/Orcpub/orcpub/issues/565) | Feature Request: Content List Export in JSON format | enh | @jjak0b | 2022-04-20 |  |
| [#524](https://github.com/Orcpub/orcpub/issues/524) | Add Warning message: This character sheet contains missing content please import your orcbrew file | enh | @datdamnzotz | 2021-03-20 |  |
| [#398](https://github.com/Orcpub/orcpub/issues/398) | Importing characters via DMV .json exports | enh | @Clank8138 | 2020-06-07 |  |
| [#269](https://github.com/Orcpub/orcpub/issues/269) | New filetype compatibility `2.5.0.?` | enh | @marloso2 | 2019-12-01 |  |
| [#204](https://github.com/Orcpub/orcpub/issues/204) | [Feature request] Add ability to load orcbrew files from a url | enh | @datdamnzotz | 2019-07-13 |  |

---

## Milestones

| Milestone | Issues |
|-----------|--------|
| 2.5.0.29 | #652 |
| 2.5.0.26 | #608 |
| 2.5.0.? | #280, #272, #269 |
| *(none)* | 162 issues |

## Label Distribution

| Label | Count |
|-------|-------|
| `area/application` | 144 |
| `enhancement` | 120 |
| `bug` | 34 |
| `area/pdf` | 17 |
| `wishlist` | 10 |
| `good first issue` | 4 |
| `help wanted` | 4 |
| `question` | 4 |
| `breaking-feature` | 3 |
| `area/deployment` | 2 |
| `area/ci` | 1 |
| `area/documentation` | 1 |
| `feature request` | 1 |
| `Feats` | 1 |
| *(unlabeled)* | 9 |
