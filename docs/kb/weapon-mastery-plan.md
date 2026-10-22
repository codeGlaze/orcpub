# Weapon Mastery as data: analysis and plan

How 2024 Weapon Mastery reaches the weapons table and the result sheet without mastery-specific
code. Researched 2026-10-19 on `feature/weapons-table` after the owner caught special-cased Graze,
Vex and Cleave buttons. Read this before touching mastery, or any "effect" a feature or item adds to
an attack. The weapon effect vocabulary itself is in [weapon-damage-model.md](weapon-damage-model.md);
the shared character machinery it builds on is in
[class-features-and-mechanization.md](class-features-and-mechanization.md).

## The rule

**No code names a mastery.** A mastery is data in the same vocabulary as a magic weapon's extras and
effects. The table and the result sheet render a handful of generic kinds (on-miss damage, a save
after a hit, an effect after damage, an extra attack, text). A new mastery, a homebrew one, or a feat
with the same mechanics then needs data, not code.

What went wrong first: `display.cljc` `mastery-summary` is a `case` on the eight keys, and the
uncommitted sheet code on the branch has a branch each for `:graze`, `:vex` and `:cleave`
(`views.cljs` around 3899-3980). Those are to be replaced, not extended.

## What exists, and where (verified 2026-10-19)

### On this branch (`feature/weapons-table`)

| Piece | Where | Use for mastery |
|---|---|---|
| Trigger `::mi/on :hit / :miss / :crit` | `magic_items.cljc` specs; `weapon_damage.cljc` `applies?` | Graze is an extra with `on :miss` |
| Save effects `::mi/effect-type :save`, `::save-dc`, `::save-ability`, `::save-fail` | `magic_items.cljc`; `display.cljc` `effect-summary` | Topple is a save effect; `effect-summary` already writes "DC 13 Con or prone" |
| Text effects `::effect-type :condition` + `::text` | `magic_items.cljc` | Push, Sap, Slow |
| Per-turn and per-rest limits `::recharge` with the app's periods (`::units/turn` ...) | `magic_items.cljc`, `units.cljc` | Cleave and Nick are once per turn |
| Roll engine with modes `:normal :adv :dis` | `weapon_rolls.cljc` `roll-attack` | Vex arms advantage on the next attack |
| Result sheet and attack chain | `views.cljs` `weapon-roll-result` | Generic "Missed?", after-damage and extra-attack buttons |
| Training set `char5e/weapon-masteries` (prop `:weapon-masteries`, nothing sets it yet) | `character.cljc:606`, `subs.cljs:964` | Gate stays as built |
| Definitions subscription `::weapons/mastery-definitions` (returns `{}`) | `subs.cljs:996` | Replaced by data, see plan step 4. **Its comment cites `edition-machinery-split.md`, which does not exist; fix in step 4.** |

### In the shared character code (all branches)

| Piece | Where | Note |
|---|---|---|
| `?weapon-ability-modifier (fn [weapon finesse?])`: the modifier an attack uses | `template_base.cljc:202` | **Not exposed** (no `character.cljc` accessor, no sub). The views approximate it as damage modifier minus magic bonus (`views.cljs` ~4260), which is wrong with Dueling or any damage-bonus fn |
| `?spell-save-dc (fn [ability])` = 8 + prof + mod + `?spell-save-dc-bonus` | `template_base.cljc:249`; `char5e/spell-save-dc-fn` | The app's de facto general DC: 12 live non-spell features use it (Stunning Strike, Turn Undead, Quivering Palm, Water Whip, Breath Weapon ...) |
| `?spell-save-dc-bonus` | written only by magic items (`magic_items.cljc:408, 416, 2346`, robe/ring style) | **Existing bug:** those items raise every non-spell DC above too. Not mastery's to fix silently |
| Attacks per turn `?num-attacks` / `char5e/number-of-attacks`, written by `mod5e/num-attacks` (Extra Attack) | `template_base.cljc:176-178`, `classes.cljc` | The sheet's counter already reads it. `mod5e/extra-attack` (cum-sum) exists with zero callers |
| Attack advantage | none | Prose only (Reckless Attack, Grappler). The only machine-readable advantage is the new roll engine's `:mode` |
| `?saving-throw-advantage` | written by many, read by nobody (accessor discarded, `character.cljc:626`) | Dead end; noted, out of scope |

### On other branches

- **`srd52/develop`** (the 5.5 line): `resources/public/srd/2024/equipment.edn` tags all 38 weapons
  with `:mastery :topple` and so on (commit `823bb936`). No definitions, no reader, no training.
  The SRD 5.2 text is in `resources/srd/SRD-5.2.1.readable.txt` (Mastery Properties at 8344; class
  "Weapon Mastery" features at 2919, 4560, 5114, 5456, 5765).
- **open5e** (`~/projects/open5e-api`, `data/v2/wizards-of-the-coast/srd-2024/WeaponProperty.json`):
  the eight masteries with verbatim SRD descriptions, `type "Mastery"`.
- **`feature/grant-rows`**: the grant-pool registry (`grant_pools.cljc`) is where "choose N weapon
  kinds to master" belongs; its `:weapons` pool grants proficiency only today.
- **gitea `srd-2024-intergration`**: an older open5e import repeating the mastery text on every weapon.
  Not a source.
- No branch has attack-advantage vocabulary or mastery code.

## The eight masteries in the vocabulary

| Mastery | As data | Fits today? |
|---|---|---|
| Topple | effect `{:effect-type :save :save-ability :con :save-dc <attack DC> :save-fail :prone}`, on hit | needs a **computed DC** (decision 1) |
| Graze | extra damage `{:on :miss :damage-bonus <ability mod> }`, weapon's type | needs a **computed amount** (`:ability-mod`) |
| Vex | effect, after damage: next attack against it has advantage | **new generic kind** `:next-attack :adv` |
| Sap | text effect, on hit | yes |
| Push | text effect, on hit | yes |
| Slow | text effect, on hit, needs damage | yes |
| Cleave | once per turn, one extra attack whose damage leaves out a positive ability modifier | **new generic kind** `:extra-attack {:ability-mod :omit}` + existing `::recharge ::units/turn` |
| Nick | once per turn, the Light extra attack joins the Attack action | text; the counter needs no change (it already counts past the total) |

So the new vocabulary is small: two value references and two effect kinds. Everything else reuses
fields that exist.

### Value references

A number field may hold a keyword resolved when rolling, from a context the view builds from existing
character functions:

- `:ability-mod`: the attack's ability modifier, from `?weapon-ability-modifier` (to be exposed).
- `:attack-save-dc`: the attack's DC, see decision 1.

One resolver, used by the formatter and the roll engine. Feats and class features can use the same
references later; the class-feature catalogue already asks for this (`:uses` from ability
modifiers, class-feature-catalogue.md "Findings").

## Decided

- **Masteries are filtered by edition like other content** (owner, 2026-10-19). The SRD masteries
  are 2024 content. A homebrew mastery **defaults to tagged 2024**; its author can retag it for both
  editions or for 2014. The version filter ([srd-2024-roadmap.md](srd-2024-roadmap.md)) then removes
  them from a 2014-only game with no mastery-specific code. The filter is designed, not built.
- **Still open:** whether masteries are content only, or also a rule the rules setting can switch
  off (2014 content with 2024 mastery rules, or the reverse). Can wait for the filter.
- **Masteries get a homebrew builder like fighting styles** (owner, 2026-10-19: "as long as there's
  no logical reason" not to). None found: a homebrew mastery needs a weapon that names it (the custom
  item builder gets a mastery picker; orcbrew does not carry items, so a pack's custom weapons cannot
  ship with it yet) and a feature that grants masteries (none in either edition's data yet). Both are
  needed for SRD masteries too, so neither argues against the builder.
- **Topple's DC is the SRD 5.2.1 rule:** 8 + the attack's ability modifier + proficiency, no item
  spell bonus. That is `?save-dc` with the attack's ability ([save-dc-sources.md](save-dc-sources.md)).
- **The item spell DC leak is fixed** (`d1403c75`): decision 2 below is closed.
- **Band text: our own summary for a multi-sentence entry, the SRD sentence for a one-sentence
  entry**, each with a link to the full SRD text (owner, 2026-10-20). The summary goes in a
  `:summary` field beside the verbatim `:text` and must keep every rule. **Cutting the SRD text at a
  sentence is wrong**: it drops rules (Topple loses "or Prone", Cleave and Nick "once per turn", Cleave
  "no ability modifier", Graze "same type, raised only by the modifier", Slow "never more than 10 ft").
  Drafts, awaiting owner review:

  | Mastery | Band text |
  |---|---|
  | Cleave | *summary:* On a melee hit, attack a second creature within 5 ft. of the first and in your reach. Its damage leaves out a positive ability modifier. Once per turn. |
  | Graze | *summary:* On a miss, deal damage equal to the attack's ability modifier, of the weapon's type. Only a higher modifier raises it. |
  | Nick | *summary:* The Light property's extra attack can be part of the Attack action instead of a Bonus Action. Once per turn. |
  | Push | *SRD:* If you hit a creature with this weapon, you can push the creature up to 10 feet straight away from yourself if it is Large or smaller. |
  | Sap | *SRD:* If you hit a creature with this weapon, that creature has Disadvantage on its next attack roll before the start of your next turn. |
  | Slow | *summary:* On a hit that deals damage, reduce its Speed by 10 ft. until the start of your next turn. Several Slow hits still total 10 ft. |
  | Topple | *summary:* On a hit, the target makes a Con save (DC 8 + the attack's ability modifier + proficiency) or falls Prone. |
  | Vex | *SRD:* If you hit a creature with this weapon and deal damage to the creature, you have Advantage on your next attack roll against that creature before the end of your next turn. |

## Mastery text: where it is stored and how it is shown (analysis, 2026-10-20)

Asked by the owner before building: should our summaries ride with the SRD entries, and should the
full text expand in the row or open in a card? Findings first, then a recommendation.

### What the app already has

- **Our words beside SRD data: one precedent, kept apart.** Rule clarifications (Sage Advice in our
  words) are their own file, `resources/public/notes/rules-clarifications.edn`, keyed by rule anchor
  and merged into the rule's text when shown (`srd_rules.cljc` `with-notes`). They render with "Our
  note, not SRD text" and a source link. The SRD files stay generated and verbatim, which
  `check-srd-data.py` enforces.
- **A preview card for SRD text** (`views.cljs` `srd-text-link`, `srd-preview-box`): hover with a
  mouse shows it, a first tap on a phone pins it, with Open and Close. It loads its data on first use.
  It covers conditions, spells and rules; a new `:kind` adds a content type.
- **Condition links in any text** (`srd-linked-text`): "falls Prone" becomes a link with a preview,
  with no markup in the data.
- **The agreed layout** (`weapons-table-spec.md` rule 10, UX guide): small spaces get the short
  summary, the opened row gets the full text. The band is in the row's quick view, and the opened
  row opens from its arrow, so the band itself is free to take a tap.
- **No short summaries for items exist yet.** The Net's "Restrains" (spec rule 6) is designed, not
  built. So whatever masteries use becomes the pattern for weapon properties and item summaries too.

### Storage: separate on disk, one shape in use (recommended)

| | Summary inside the SRD entry | Summary in its own file (recommended) |
|---|---|---|
| SRD files stay verbatim and regenerable | no: generated files carry our text, and a regeneration must merge it back | yes |
| Whose words is it, visible in the data | by field name only | by file, like clarifications |
| Homebrew mastery | the author fills both fields | the author fills both fields: homebrew has no SRD file |
| What the views read | one entry | one entry: the summary is merged in at load, as `with-notes` does |

So the SRD file holds `:text`, our file holds `{:topple {:summary "..."}}` for each multi-sentence
entry, and the loaded mastery is `{:name :text :summary}` either way. A homebrew mastery stores both
fields on itself; with no summary, the band shows its `:text`. One file of our summaries is also one
place to review them, and the same file can hold weapon property and item summaries later.

### Display: the summary in the band, the full text one hover or tap away (recommended)

| Option | For | Against |
|---|---|---|
| A. Expand the band in place | no new control | pushes the next rows down; the full text then lives in two places (band and opened row) |
| B. A `?` button opening a card | familiar | a new 44px control on every band; a new pattern beside the app's existing preview |
| **C. The mastery name is a preview link (recommended)** | the app's own pattern: hover on desktop, tap on a phone, Open and Close; no new control | needs a `:mastery` preview kind; Open has no 2024 Rules page to go to yet |

With C, the band reads `TOPPLE  on hit  Con save DC 13 or falls Prone`, and Prone is a
condition link of its own. The preview shows the full SRD 5.2 text, labelled as SRD text. The opened
row keeps a single mastery line (name, trained or not), not a second copy of the text, so the full
text has one home. Until a 2024 Rules page exists, the preview shows only Close.

## Mastery from the player's side (owner, 2026-10-20)

The owner's test case: a player picks up a new weapon whose mastery they never read while building the
character. Design from what they see, not from the data. Three moments; mockups before building.

1. **The weapon arrives.** Trained: the band shows, with a small "New" mark until the band has been
   opened once on this character. Not trained but has Weapon Mastery: the 2024 class feature lets
   them change one weapon choice after a Long Rest (SRD 5.2.1, Barbarian level 1 "Weapon Mastery"),
   so the row says so: "Topple: you can master Battleaxes in place of one of your choices when you
   finish a Long Rest." No Weapon Mastery: nothing.
2. **They read it.** The whole band is the tap target (not only the name: a small link is hard to
   hit at the table). The card leads with what it means for this character ("When you hit with
   this: the target makes a DC 13 Constitution save or falls Prone. After a hit, your roll result
   offers Topple."), then the SRD text, labelled as SRD.
3. **It applies.** The roll result says what the mastery allows at that moment: after a hit "Topple:
   DC 13 Con save or Prone"; after a miss "Missed? Graze for 3 slashing"; after a damaging hit "Vex:
   your next attack against it has advantage". A player who never read the rule is told when it
   applies.

**Summaries use the character's numbers** (DC 13, 3 slashing), never the formula, which is our side.
So a stored summary has value references filled in when shown (`:attack-save-dc`, `:ability-mod`).
This supersedes the formula wording in the draft table above and the "name as preview link"
recommendation (option C) in the display section: the whole band opens the card.

**Superseded on display (owner, 2026-10-20):** the band gets a `?` helper (44px tap area) that opens
a pop-up card; the table never expands in place. Card behaviour, including rules opening inside the
card: [rule-preview-cards.md](rule-preview-cards.md).

**Band joined to its row** (owner, 2026-10-20: it read as part of the row below): the v5 design wraps
the row and its band in one box with 4px rounded corners; in the app's striped table that means
rounded bottom corners on the band and a gap before the next row. **"New" tag in question:** the owner
asked what hole it fills and how to keep it from reading as "new to the site". Shown beside it: no
tag, the `?` glowing like the 1H / 2H pulse (`weapons-table-spec.md` rule 14) until opened once.

## Swapping masteries (SRD 5.2.1, checked 2026-10-21)

Only a **Long Rest** allows a change; a Short Rest does not. How much changes depends on the class:
Barbarian and Fighter "change one of those weapon choices"; Paladin, Ranger and Rogue "change the kinds
of weapons you chose" (all of them). The number of kinds grows with level (each class table's Weapon
Mastery column). So a swap control must know which rule its class uses.

**Proposed by the owner, mocked up 2026-10-21:** a Weapon Mastery panel above the Weapons table with
two rows: what is mastered ("3 of 3", a chip per weapon kind with its mastery), and the swap planned for
the next Long Rest (Fighter: "swap one", Handaxe to Glaive). **Owner, 2026-10-21: it needs a resting
state where nothing changes, and it took too much space.** Round 4 mockups: two compact lines under
the Weapons heading ("Mastery 3 of 3 · Battleaxe, Longsword, Handaxe" / "Next Long Rest: no change ·
Change"), Change opening a bottom sheet (Keep all / Swap at next Long Rest), Undo once planned; and a
one-line variant, since the bands already show which weapons are mastered. Open: two lines or one.

**Untrained weapons (owner, 2026-10-21):** a closed row showed nothing, so the mastery was missed.
Proposed by the owner: a thin line with a grey cap and light grey text ("GRAZE · not trained · Swap it
in at a Long Rest"), and a switch carrying the app's homebrew icon (`beer-stein.svg`) to use the
mastery anyway as a house rule; on, the line becomes the full band with the stein lit. Open: the thin
line always, or behind a display setting. The switch needs a 44px tap area when built. Choices are build selections (the 5.5 line's class features via grant
pools), so a swap writes to the build; it is not sheet-only state.

## Decided, round 5 (owner, 2026-10-21)

- **Untrained weapons: the thin grey line shows by default**, and a display setting turns it off.
  Its stein switch uses the mastery anyway (house rule).
- **No "New" tag** (ux-guide.md decisions log). The band's `?` glows instead: a few pulses, slowing to
  a stop, as the 1H / 2H switch does; opening it once stops it.
- **Clicking inside a hover card pins it** (desktop): opening a rule inside a hovered card is a click,
  and that click pins the card.
- **The panel needs to stop the eye** (owner: the title looked like the band tags, and the list had no
  breaks). Round 5: a bordered box, a plain bold title (amber caps stay on the bands), one chip per
  mastered weapon kind with the mastery in small amber letters, "3 of 3" at rest.
- **A homebrew switch on the panel** (the stein) lets the player add or remove masteries freely, any
  number, without resting: chips get a ×, an "+ Add" chip appears, the count reads "4 · house rule".
  In addition to the Long Rest swap, not instead of it.
- **The Long Rest asks.** The app has no Long Rest dialog today: `finish-long-rest-button`
  (`views.cljs` ~3036) rests at once. For a character with Weapon Mastery it opens a sheet with a
  Weapon Mastery section ("swap one" or all, by class), "Keep all" chosen, or the planned swap filled
  in; Cancel changes nothing. Recommended (awaiting owner): open it only when the rest holds a choice
  (a mastery swap now; later others, such as a 2024 wizard's prepared spells, as sections of the same
  sheet), and keep today's one-tap rest for every other character.
- **The panel is approved** (owner, 2026-10-21: "looks good"), as in round 6.
- **No pop-up when a rest holds no choice** (owner, 2026-10-21: "what would a popup even show?").
  Found: today a Long Rest is silent (`events.cljs` `::char5e/finish-long-rest` clears hit points,
  slots and uses; nothing tells the player, and a mis-tap cannot be undone). Proposed: a quiet notice
  "Long Rest finished · Undo" in the app's notice style for every rest without a choice; the sheet only
  for a rest with one. Awaiting owner.
- **Round 6:** three masteries fit one line at 390px (12px chips, 9.5px mastery letters, 4px gaps); the
  box takes the table's full width. The house-rule state may wrap. Chip × buttons need 44px tap areas
  when built.
- **Wording is parked**: the owner will review every player-facing line in one pass, as a list.

## Undo for a rest: plan (2026-10-21, awaiting owner)

**What exists.** One Undo in the app: picks that came back on their own (`character_builder.cljs`
`picks-notice`; `events.cljs` `undo-restore-db` / `redo-restore-db`). The event that changed things
records what it changed in the db (`::e5/restore-notice {:restored ...}`); Undo applies the inverse and
the notice turns to "Undone · Redo"; × closes it. The rest copies that pattern. Rests themselves go
through `update-character-fx`: a saved character is updated in `::char5e/character-map` and saved by
the throttled save; the builder's unsaved character is replaced with `:set-character`.

**Plan.**
1. **Record exactly what the rest changed.** `finish-long-rest` / `finish-short-rest` keep, beside the
   change, the values they cleared, as they were: the `::char5e/features-used` entries for the cleared
   periods, `::spells/slots-used`, `::char5e/current-hit-points`, and a mastery swap if one was made
   (in `::e5/rest-notice {:id :kind :before {...}}`). Only those keys, not the whole character.
2. **Undo puts back only those keys** (assoc the old value, or dissoc where there was none), through
   `update-character-fx`, so a saved character saves the undo like any edit.
3. **(Revised below: how long Undo lasts.)** Undo is offered only until the character next changes. If the player uses a slot or takes
   damage after resting, putting the old values back would silently lose that, so any later change
   to the character ends the Undo: the notice becomes plain "Long Rest finished" until closed. This
   is the one design choice for the owner (the other way is to merge, which can be wrong both ways).
4. **Redo runs the rest again**, the same event, like Redo in the picks notice.
5. **Where:** beside the rest buttons (ux-guide "Show it beside the control that changes it"), using
   the same `notifications/callout` box as the picks notice.
6. **Tests, seen failing first:** events tests (rest then Undo gives back the exact character; a
   change after the rest ends the Undo; Redo equals the rest; a saved character emits the save); one
   browser check on the sheet (rest, Undo, values back).

**How long Undo lasts (owner, 2026-10-21):** a time limit and "until they click something else" are
both poor signals, and many groups rest at the end of a session and log off. Proposed: Undo lasts
until the character changes (a slot used, damage, any edit), however long that is; scrolling, cards and
tabs do not count. The last rest's record (what it cleared, and when) is saved with the character, so
it survives logging off: beside the rest buttons a quiet line reads "Long Rest finished · Undo", or
"Last Long Rest: Oct 21 · Undo" in a later session, and goes away when the character changes. Needs
one new schema attribute for the record. **Agreed by the owner, 2026-10-21.** Not built yet.

**Fixed 2026-10-21 (feature/weapons-table), was a bug found on this branch:** the schema stores used uses per period
(`schema.clj` `features-used-schema`: minute, hour, turn, day, round, rest, long-rest). This branch
added `::units5e/dawn` (and the weapon `::mi/recharge` field also allows `::units5e/short-rest`); a
saved character that uses a once-per-dawn weapon power records it under `::units5e/dawn` through
`toggle-feature-used`, an attribute the schema lacks, so its save should fail. Fix: add both periods
to the schema (additive; `datomic.clj` transacts `all-schemas` at every start, so existing databases
get them with no migration). `features_used_save_test.clj` saves a use under every period
`::mi/recharge` allows; it failed for dawn and short-rest ("Unable to resolve entity") before the fix.

**Dawn and the rest buttons (owner, 2026-10-21: "a checkbox or something next to the rest buttons so
I can include Dawn or not").** On this branch a Long Rest always clears dawn uses
(`units5e/long-rest-clears`), but a rest by day passes no dawn, and a dawn can pass without a rest.
Proposed: beside Long Rest, "Dawn passed", checked by default, clearing dawn uses only while checked;
a small "Dawn" button that clears dawn uses alone; both shown only for a character holding something
that recharges at dawn; Undo covers either. No checkbox on Short Rest. **Agreed by the owner,
2026-10-21**; mocked up (round 7) on the Features tab. **Round 7 looked bad** (owner, 2026-10-22):
right-aligned amber buttons, a checkbox wedged between them, ragged wrapping, a Dawn button as heavy
as a rest. Redone: two equal full-width buttons (Long Rest, Short Rest), under them "Dawn passes during
the Long Rest" (checked) and a "Dawn only" link; the Undo line at the foot. Option A puts it in a Rest
box like the mastery panel, option B has no box. **Owner, 2026-10-22: the dawn line was easy to skim
past.** Now: "☀ Dawn passes" with its box, on an amber tint, beside an outlined "☀ Dawn only" button,
both 44px tall. (The mockup tried `fa-sun-o`, the Font Awesome 4 name, which 5.13.1 lacks; the right one is `fa-sun`.) **Then, owner:
too big for this app.** Now: today's two `.form-button.p-5` rest buttons unchanged; one small row
above them, a tinted "☀ dawn passes" pill with its box and an outlined "☀ dawn only" pill (26px drawn,
44px tap area); a small Undo line below. **Rejected (owner, 2026-10-22):** it overcorrected (the
full-width layout was fine; only the size needed to come down) and drew "Dawn only", an action, like
the toggle.

**Rest controls, analysis before the next mockup (2026-10-22, awaiting owner's go):**
- **Order Dawn, Short Rest, Long Rest** (owner's idea): smallest reset to largest, and the primary,
  most used action last, at the right end (the usual position for the main action; under a
  right-handed thumb). Dawn an outlined secondary button, Short and Long solid; Long widest.
- **A sun on Dawn's left only, and the same sun on the toggle,** so the two read as one family; no
  icons on the rest buttons. The app loads Font Awesome 5.13.1 (`index.clj`), which has `fa-sun` in
  two styles: `fas fa-sun` (solid; the app's `fa fa-…` classes are solid too) and `far fa-sun`
  (regular, outline).
- **The "dawn passes" toggle directly above Long Rest, in its column.** Convention for an option that
  changes one button ("Remember me" over Sign in, "Save this card" over Pay): above (seen before the
  commit; not hidden under the thumb on a phone) and adjacent (proximity ties it to that button, not
  the row). Spanning the row would suggest it changes Dawn and Short Rest too.
- **Size:** between today's 27px and the 44px mockup, about 36px drawn, 14px text; tap areas 44px.
- **Undo line** below the row, unchanged.
**For every review:** include a shot of the screen as it is today, so the owner can compare.

**Found while mocking it up:** the rest buttons show only when a trait resets on a rest
(`views.cljs` `features-details`, `(some freqs units/long-rest-clears)` over traits); a weapon's
recharge is not a trait, so a character whose only reset is a once-per-dawn weapon gets no Long Rest
button. The weapons' recharges must count when the buttons decide to show. The spells section has a
second Long Rest button (`views.cljs` ~3088), which needs the same controls.

**Dawn: an option, never a default (owner, 2026-10-22: "dawn by DEFAULT doesn't sit right... Not that
people shouldn't have the option").** Decided by the owner: the V5 bar (Dawn | Short Rest | Long Rest),
no toggle. Long Rest alone does not clear dawn uses (`units5e/long-rest-clears` drops `::dawn`; the
events test that a long rest clears dawn flips). The Dawn button clears them. After a Long Rest taken
without pressing Dawn, for a character with dawn items, the result line asks "Did dawn pass during the
rest? ☀ Yes"; one tap clears dawn uses (and joins the rest's Undo). Pressed Dawn first: no question.

**Rest bar: V5, the joined bar, stays** (owner, 2026-10-22). Noted only, not a change: a joined bar
can read as a pick-one segmented control (the owner asked whether Dawn was a toggle). Owner: only a
fraction of players use dawn items, so stop polishing here.

**Other reset times, checked 2026-10-22:** no item resets at midnight in either SRD (Midnight Tears is a
poison that takes effect at midnight). One resets at dusk: the Robe of Stars ("Daily at dusk, 1d6
removed stars reappear"), both editions. All other recharges are at dawn (36 mentions in the 2014
items). No Dusk button; the robe's recharge stays text.

**Idea from the owner, not yet decided (2026-10-22):** a slim, full-width time-of-day bar on mobile,
"☀ Dawn | ☾ Dusk", to make those resets visible and prompt homebrewers to make items that use them.
Needs: a `::units5e/dusk` period (spec, schema entry like dawn's, clear event), dawn and dusk offered as
recharge choices in the item builder, and the Robe of Stars moved to dusk. Open: where it sits, and
whether it shows for every character or only those with dawn or dusk items. Owner: after a Long Rest, a dusk item gets
the same reminder as dawn ("During the rest, did ☀ Dawn pass / ☾ Dusk pass"). Mocked 2026-10-22:
T1 bar above the rests, T2 below, T3 sky tint, T4 one bar Dawn | Short | Long | Dusk, T5 the bar names
what each resets. The mockup's lilac is not a palette colour; use the themes' purple.

## Decisions for the owner

1. **Topple's DC.** (a) A new template prop `?weapon-save-dc (fn [weapon])` = 8 + prof +
   `?weapon-ability-modifier`, exposed like its neighbours, with no spell bonus (the SRD rule); or
   (b) reuse `?spell-save-dc` with the weapon's ability, matching the 12 features that do, bonus bug
   included. Recommended: (a), since the SRD defines it by the attack's modifier, not an ability.
2. **The `?spell-save-dc-bonus` leak.** Fix now (a base DC without item spell bonuses, spells adding
   them on top) or file it. It changes printed DCs for characters wearing those items.
3. **Where definitions live.** Data on the 5.5 line (`srd52/develop`), in the app's data shape, read
   by a subscription; this branch carries only test data. Recommended, per
   [srd-2024-roadmap.md](srd-2024-roadmap.md) (mastery belongs on the 5.5 trunk).
4. **Band summary wording.** Derived from the data (`effect-summary`, `extra-damage-str`), so no
   per-mastery strings; Push, Sap and Slow need a short `:summary` field in our words beside the SRD
   `:text`.

## Plan (after the decisions)

Each step: tests seen failing first, scoped checks, own commit, KB updated.

1. **Done 2026-10-21 (`feature/weapons-table`).** The parked diff is discarded (a copy was kept only in
   the session's scratch space); the desktop result-card check was committed on its own. **Was:** Discard the special-cased code. Revert the uncommitted Graze/Vex/Cleave sheet branches
   (`views.cljs`), the `:weapon-roll/arm` event and sub only if step 5 does not reuse them, and the
   `case` in `mastery-summary`. Keep the desktop ctrl-click card check.
2. **Expose the weapon's ability modifier** (`character.cljc` accessor + sub, like
   `best-weapon-damage-modifier-fn`), and the DC chosen in decision 1. Replace the views' approximation.
3. **Value references**: spec `:ability-mod` and `:attack-save-dc` as allowed values of
   `::damage-bonus` and `::save-dc`; one resolver; formatter and engine call it.
4. **Mastery definitions as data**: `{key {:name :text :summary? :extra-damage [...] :effects [...]}}`
   in the item vocabulary; `mastery-band` builds the band from them; delete `mastery-summary`. Fix the
   subs comment to point here.
5. **Generic sheet kinds**: "Missed?" for any `on :miss` extra; after-damage effects as buttons
   (`:next-attack :adv` arms the chain's next roll); `:extra-attack` turns the corner tile into that
   attack once per turn. None names a mastery.
6. **Tests with made-up data** for all eight (custom items, writing-browser-tests.md rule 6), plus a
   homebrew item using the same kinds, to prove nothing is mastery-specific.

Out of scope here: setting `:weapon-masteries` from class features (5.5 line, grant pools), and the
dead `?saving-throw-advantage`.
