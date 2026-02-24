# Application UX — Issue Deep Dive

> 38 issues (15 bugs, 23 enhancements) | [Back to Index](INDEX.md)

## Summary

Application UX issues center on two areas: the character builder workflow (34 issues) and the combat tracker (4 issues). The character builder has long-standing calculation bugs (rest buttons, multiclass requirements, proficiency ordering) alongside a large wishlist of resource-tracking and filtering enhancements. The combat tracker is underbuilt relative to user expectations, with requests for search, notes, and player-facing views. A critical performance issue (#621) with excessive custom content affects the entire app and should be prioritized.

## Character Builder UX

### Bugs

| # | Title | Complexity | Codebase Area | Related Issues | Notes |
|---|-------|-----------|---------------|----------------|-------|
| [#621](https://github.com/Orcpub/orcpub/issues/621) | Extreme UI Freezing With Excessive Custom Content | L | `views`, `events`, `subs` | #475, #206 | Core perf issue. Likely caused by re-frame subscription recomputation over large homebrew datasets. Needs profiling and memoization/virtualization strategy. |
| [#614](https://github.com/Orcpub/orcpub/issues/614) | Features with the name `Null` break things | S | `character`, `events` | -- | String "Null" likely compared against `nil` somewhere in keyword conversion. Straightforward guard fix. |
| [#553](https://github.com/Orcpub/orcpub/issues/553) | Multiclass Requirements not Enforced | M | `character`, `views/builders.cljs` | #108 | Multiclass ability-score prerequisites (PHB p.163) are not validated. Needs a prereq check in the class-selection event handler. |
| [#548](https://github.com/Orcpub/orcpub/issues/548) | Dropdown Selection Don't Recognize '1' unless changed and changed back | S | `views/common.cljs` | -- | Default-value initialization bug in the dropdown component. The first option is visually selected but not dispatched to the app-db until changed. |
| [#526](https://github.com/Orcpub/orcpub/issues/526) | Eldritch Invocation selector is in the wrong tab | S | `views/builders.cljs`, `character` | #488 | Invocation selection renders under the wrong builder tab. Tab-routing logic in `builder-plugin` or `selection-section` needs correction. |
| [#340](https://github.com/Orcpub/orcpub/issues/340) | Multiple identical weapons are displayed as one | M | `character`, `views/builders.cljs` | #138 | Weapons are keyed by type, collapsing duplicates. Needs per-instance identity (index or UUID) in the equipment model. |
| [#320](https://github.com/Orcpub/orcpub/issues/320) | Display sources for content | S | `views/builders.cljs`, `subs` | #198, #475 | Content options lack source attribution (SRD vs. homebrew file). Related to the enhancement in #198. |
| [#310](https://github.com/Orcpub/orcpub/issues/310) | Short Rest Button Missing (Bards) | S | `views/builders.cljs`, `character` | #295, #442 | Bard Inspiration uses short-rest recovery but the short-rest button is absent from the play view. Likely a missing condition in the rest-button visibility logic. |
| [#309](https://github.com/Orcpub/orcpub/issues/309) | Medium armor Stealth disadvantage modifier does not work | M | `character`, `subs` | -- | The "no stealth disadvantage in medium armor" trait (e.g., Medium Armor Master) is not reflected in the stealth calculation. Requires adding a modifier path in the AC/stealth computation chain. |
| [#304](https://github.com/Orcpub/orcpub/issues/304) | Sort by Level in Class/Level Tab | S | `views/builders.cljs` | -- | Class levels display unsorted. Simple sort-by on the level list before rendering. |
| [#296](https://github.com/Orcpub/orcpub/issues/296) | Language Selection Bug in Character Builder | S | `views/builders.cljs`, `character` | #108 | Duplicate or phantom language selections. Possibly related to the proficiency-ordering bug in #108 where background choices preempt class choices. |
| [#295](https://github.com/Orcpub/orcpub/issues/295) | Long Rest Button not Saving new HP value | M | `events`, `character` | #310, #442 | Long rest resets HP in the UI but does not persist the new value to the character entity. Event handler likely missing a save dispatch after HP update. |
| [#138](https://github.com/Orcpub/orcpub/issues/138) | Items duplicating equipment-pack items not displayed | M | `character`, `views/builders.cljs` | #340 | Items that overlap with an equipment pack (e.g., extra rations) are suppressed because equipment is deduped by key. Same root cause as #340 -- equipment needs instance-level identity. |
| [#108](https://github.com/Orcpub/orcpub/issues/108) | Proficiencies from backgrounds applied before class choices | M | `character`, `character-builder` | #296, #553 | Ordering problem: background proficiencies are committed first, blocking class proficiency selection when they overlap. Needs a conflict-resolution or reordering step in the build pipeline. |

### Enhancements

| # | Title | Complexity | Codebase Area | Related Issues | Notes |
|---|-------|-----------|---------------|----------------|-------|
| [#546](https://github.com/Orcpub/orcpub/issues/546) | Add XP to whole party -- Button | S | `views/lists.cljs`, `events` | -- | Bulk XP addition for all party members. Straightforward event that iterates party characters and adds XP delta. |
| [#488](https://github.com/Orcpub/orcpub/issues/488) | No "Add Invocation" button | S | `views/builders.cljs` | #526 | UI affordance missing for adding invocations outside initial class selection. May just be a missing button wired to existing selection logic. |
| [#475](https://github.com/Orcpub/orcpub/issues/475) | Turn Option Source sections on and off | M | `views/builders.cljs`, `subs`, `events` | #206, #198, #621 | Allow toggling visibility of content sources (SRD, specific homebrew files). Would help with #621 performance and #206 SRD disable request. |
| [#452](https://github.com/Orcpub/orcpub/issues/452) | General UX improvement suggestions | S | `views` | #165 | Vague community request. Needs triage to extract specific actionable items. Low priority without clarification. |
| [#442](https://github.com/Orcpub/orcpub/issues/442) | Hit Dice Checkboxes | M | `views/builders.cljs`, `events`, `character` | #295, #310, #441 | Track hit dice usage during short rests with checkboxes. Requires new state in the character model and a UI component in the play view. |
| [#441](https://github.com/Orcpub/orcpub/issues/441) | Custom/Agnostic counter | M | `views/builders.cljs`, `events`, `character` | #247, #442 | Generic resource counter (ki points, sorcery points, etc.). Would subsume #247 and complement #442. Needs a flexible counter model in character state. |
| [#394](https://github.com/Orcpub/orcpub/issues/394) | Sneak Attack Button | S | `views/builders.cljs`, `character` | -- | Quick-roll or toggle for sneak attack damage in the play/combat view. Small UI addition with damage calculation from rogue level. |
| [#335](https://github.com/Orcpub/orcpub/issues/335) | Eliminate Proficiencies | S | `views/builders.cljs`, `character` | #108 | Allow removing unwanted proficiencies (e.g., from multiclass overlap). Needs a "remove proficiency" action in the builder. |
| [#254](https://github.com/Orcpub/orcpub/issues/254) | Sort and filter items | M | `views/builders.cljs`, `subs` | #229 | Item list in equipment tab needs sort/filter controls. Moderate UI work plus subscription-level filtering. |
| [#247](https://github.com/Orcpub/orcpub/issues/247) | Monk Ki Point Tracker | S | `views/builders.cljs`, `events` | #441 | Class-specific resource tracker. Would be better solved by the generic counter in #441. |
| [#229](https://github.com/Orcpub/orcpub/issues/229) | Item Quantity on View Screen | S | `views/builders.cljs` | #254, #340 | Show item counts on the character view. Simple display enhancement, but depends on fixing the duplicate-item identity issue (#340). |
| [#207](https://github.com/Orcpub/orcpub/issues/207) | Configurable Collections for Content List | M | `views/content.cljs`, `events`, `subs` | #475, #206 | Let users organize homebrew into named collections. Moderate data-model addition with UI for collection management. |
| [#206](https://github.com/Orcpub/orcpub/issues/206) | Option to disable SRD content | M | `views/builders.cljs`, `subs`, `character` | #475, #621 | Toggle SRD content off so only homebrew shows. Needs a filter flag in subscriptions. Related to source-toggling in #475. |
| [#201](https://github.com/Orcpub/orcpub/issues/201) | Locking Previous HP Levels | S | `views/builders.cljs`, `events` | #295 | Prevent accidental changes to HP rolls from earlier levels. UI lock toggle on per-level HP selections. |
| [#198](https://github.com/Orcpub/orcpub/issues/198) | Show option sources / set source nicknames | M | `views/builders.cljs`, `subs` | #320, #475 | Display which file/source each option comes from and allow renaming. Requires tracking source metadata through the option pipeline. |
| [#191](https://github.com/Orcpub/orcpub/issues/191) | Toggle Ability Choice for Finesse Weapons | S | `views/builders.cljs`, `character` | -- | Let users pick STR or DEX for finesse weapons instead of auto-selecting the higher stat. Small toggle in weapon display. |
| [#179](https://github.com/Orcpub/orcpub/issues/179) | Separate monk ability/spell window | M | `views/builders.cljs` | #247, #441 | Dedicated section for monk features (ki, martial arts). Would benefit from the generic counter system (#441). |
| [#165](https://github.com/Orcpub/orcpub/issues/165) | Visual Design Improvements | L | `views`, CSS | #452 | Broad visual overhaul request. Large scope, best tackled incrementally alongside other UI work. |
| [#121](https://github.com/Orcpub/orcpub/issues/121) | Search "Orcacle" should also work for homebrew content | M | `views/builders.cljs`, `subs` | #475 | The global search (Orcacle) only searches SRD content. Needs to index homebrew options in the search subscription. |
| [#111](https://github.com/Orcpub/orcpub/issues/111) | Starting Packages | M | `views/builders.cljs`, `character`, `events` | -- | Pre-configured equipment/spell/skill packages for quick character creation. Requires a package data model and a one-click apply mechanism in the builder. |

## Combat Tracker

### Bugs

| # | Title | Complexity | Codebase Area | Related Issues | Notes |
|---|-------|-----------|---------------|----------------|-------|
| [#338](https://github.com/Orcpub/orcpub/issues/338) | Combat Tracker / Party editing causes logout | M | `views/combat.cljs`, `events` | #193 | Editing a party or encounter triggers an auth state reset. Likely a side-effect in the save/update event chain that clobbers the session. Needs careful event-flow tracing. |

### Enhancements

| # | Title | Complexity | Codebase Area | Related Issues | Notes |
|---|-------|-----------|---------------|----------------|-------|
| [#534](https://github.com/Orcpub/orcpub/issues/534) | Add note field for monsters in combat tracker | S | `views/combat.cljs`, `events` | #433 | Per-monster text field in encounter entries. Small model addition (`:notes` key) and a text input in the monster row component. |
| [#433](https://github.com/Orcpub/orcpub/issues/433) | Add search function to combat tracker "add monster" section | M | `views/combat.cljs`, `subs` | #534 | Monster list is unsearchable when adding to an encounter. Needs a filter-as-you-type input wired to the monster subscription. Similar pattern to spell search. |
| [#193](https://github.com/Orcpub/orcpub/issues/193) | Combat tracker for players | L | `views/combat.cljs`, `events`, `subs` | #338, #534, #433 | Player-facing combat view (see initiative, HP, conditions for your own character). Major feature: requires shared encounter state, permissions model, and a read-only player UI. |
