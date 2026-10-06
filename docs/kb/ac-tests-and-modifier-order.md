# AC tests must order modifiers the way the app does

**Not a defect.** Corrected 2026-10-06, the same day this page first said otherwise.

## What it looked like

While testing the Robe of the Archmagi fix (codeGlaze/orcpub PR #55), a lizardfolk character with a
+1 ring came out at 15 instead of 16 when the lizardfolk modifiers were listed before the ring. The
lizardfolk rule (`:lizardfolk-ac`, `options.cljc`) replaces `?armor-class-with-armor` with a
function that takes the higher of its own base and the previous function, so a bonus applied after
it is not in the function it kept.

## Why it is not a bug

The test applied modifiers in list order with `mods/apply-modifiers`. The app never does that:
`entity/apply-options` sorts them by property dependency first (`kahn-sort`, then
`order-modifiers`). The template's `?armor-class-with-armor` reads `?ac-bonus-fns`, so every
`:ac-bonus-fns` modifier applies before any `:armor-class-with-armor` one. With that order the
lizardfolk character is 16 whichever way the modifiers are listed (measured).

**For tests:** run modifiers through the same ordering as `apply-options`.
`test/cljc/orcpub/dnd/e5/robe_ac_test.clj` has an `ac` helper that does. `bracers_ac_test.clj`
applies in list order, which is safe only because none of its modifiers wraps
`?armor-class-with-armor`.

## What was a real bug, found at the same time

Tortle (`:tortle-ac`) replaced the AC calculation with a flat 17 + shield, ignoring every bonus,
so a Ring of Protection or Bracers of Defense never counted. Fixed in PR #55: 17 + shield whatever
armor is worn, bonuses added, a competing calculation (the Robe) wins when higher.
`feature/grant-rows` rewrites tortle and lizardfolk on its own AC engine; keep its versions when it
merges `integration`.
