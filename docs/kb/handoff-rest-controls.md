# Rest controls: design handoff and how it meets the code

**Start here to build the Rest controls** (Short Rest, Long Rest, Dawn, Dusk, Undo). The design came
from a dedicated layout agent the owner briefed on 2026-10-22 with goals, not mockups; this page says
where the package is and what the code already has or lacks. The earlier rest mockups and rulings
are in [weapon-mastery-plan.md](weapon-mastery-plan.md) ("Undo for a rest", "Dawn: an option, never a
default"); where they differ, this handoff wins.

## Where it is

`/home/codeglaze/projects/rest-design-v1/handoff_rest_controls/` (local, beside `weapons-design-v5`).
`Rest Controls Final.dc.html` is the source of truth, `Rest Handoff.md` the spec, `Rest Decisions.dc.html`
the record of options D1-D7. Open the `.dc.html` files in a browser; they need their sibling files.

## The design in one paragraph

A **Rest box** is the first section of the Features tab: heading (`fa-bed` + "Rest"), then a
**time-of-day** radio group (None, Dawn, Dusk, Both; default None, back to None after any action), a
**Now** button that applies the chosen time without resting, and **Short / Long** rest buttons. A rest
with a time chosen applies both. A rest with None, for a character with a spent dawn or dusk item,
asks "Did dawn or dusk pass?" after **both** rests (Dawn, Dusk, Both, Neither, Undo rest). One result
line with one Undo. Characters with no dawn or dusk items see only the heading and the two rests.
Other tabs get a floating Rest button (56px, bottom right) opening the same box as a bottom sheet;
the Import log button moves above it and becomes dismissible when empty (its dismiss control is
not drawn yet). The Spells tab's old "finish long rest" stays until the new box has settled, then
moves behind a Dev mode setting. The item builder gains a Charges group.

## Against the code (checked 2026-10-23, `feature/weapons-table`)

| Handoff says | The code has | To do |
|---|---|---|
| `fa-bed`, `fa-sun`, `fa-moon`, `fa-code` | all in Font Awesome 5.13.1 (`index.clj`) | none |
| Light theme accent `#33658A` | `core.clj` `accent-on-light` | none |
| Long rest never implies dawn | this branch's `units5e/long-rest-clears` includes `::dawn`, and an events test checks it | remove `::dawn`; flip the test |
| Recharge values incl. dusk | `::units5e/dawn`, `short-rest`, `long-rest` (schema fixed `20b65614`); no dusk | add `::units5e/dusk` (spec, schema entry, clear) |
| Item fields `charges-max`, `charges-used`, `recharge`, `regains` | `::mi/charges` and `::mi/charges-recharge` are specced, used by no item; uses are tracked per period in `::char5e/features-used` | reuse `::mi/charges` and `::mi/charges-recharge` (renaming user-facing keys breaks homebrew); `regains` is new; where charges-used lives is a decision |
| Undo reverses the whole action | Undo agreed earlier: lasts until the character changes and survives logging off | the handoff does not say how long Undo lasts; ask |
| Import log button | `views/import_log.cljs` | move, plus dismissible when empty (needs a drawing first) |
| Settings Dev mode section | not checked yet | find the settings page before building |

## Open, for the owner

- Which branch carries this. It reaches past the weapons table (Features tab, floating button,
  import log, item builder, settings).
- How long Undo lasts (until the next change and across sessions, as agreed earlier, or less).
- Where a used charge is stored.
