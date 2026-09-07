# Branch changelog — `fix/tall-flyout-and-held-release`

## Why this branch exists

Two things reported from real use of the merged build.

My Content is eleven rows, positioned under its tab with no height limit, so on a
720-tall window its last two items sat below the bottom of the screen and on a
620-tall window four did. A hover menu cannot be scrolled to — reaching for the
page scrollbar moves the pointer off the menu and closes it — so those items were
simply unreachable. It is the tallest menu, which is why it "bugs out the most".

And the What's New panel never appeared for a first-time visitor. It is held back
while the cookie notice is up so a first visit gets one overlay rather than two,
but that decision was made once at boot: accepting the notice released nothing
until the next full reload, which nobody has a reason to perform.

## Fixed

- **Tall header menus stay on screen** — an opening flyout is capped to the room
  below it and scrolls, so every item in My Content is reachable on a short
  window instead of running off the bottom.
- **The What's New panel opens when the cookie notice is dismissed** — it no
  longer waits for a reload the visitor never makes.
