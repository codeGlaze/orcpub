# Rule preview cards

The card that opens when a player hovers or taps a rule, condition or spell named in text. One
component for the whole app (`views.cljs` `srd-text-link`, `srd-preview-box`, `srd-body`,
`srd-linked-text`). Read this before adding a new kind of preview or changing how one opens.

## How it works today (verified 2026-10-20, `feature/weapons-table`)

- **Opening:** a mouse hover opens it and leaving closes it after 250 ms unless the pointer moved
  into it; a first tap on a phone pins it, a second tap on the same link opens the page. Enter opens
  the page.
- **Content:** `:condition`, `:spell` and `:rule` kinds (`srd-preview-content`). Each loads its SRD
  file on first use (`:load-srd`).
- **Where:** fixed position beside the link, below it or above when there is no room, at most 440px
  wide and 60% of the window high (`preview-style`).
- **One at a time:** a single atom, `srd-preview`, holds the open card; the body that owns the link
  draws it.
- **Closing:** Close, a tap outside, or **any scroll** (a fixed card would drift from its link).
- **Links inside a card are dead:** the card draws its content with no owner, so a condition named
  in a rule's text is plain text there.
- **Linking free text:** `srd-linked-text` turns condition words ("falls Prone") into links with no
  markup in the data.

**Bug, measured 2026-10-20:** on a 390px phone the card sticks out 12px past the right edge (Charm
Person's "charmed": left 16, right 402). `preview-style` sizes the width as `innerWidth - 32`, but
`.srd-preview` has 14px side padding and no `box-sizing: border-box`, so the padding is added on top.
Fix: `box-sizing: border-box` on `.srd-preview` (`styles/core.clj`).

**Mockups** of the agreed changes (phone and desktop) were drawn into the real app with
`mastery-mockups.js` (job scratch, 2026-10-20); the owner reviews them before any of this is built.

## Agreed changes (owner, 2026-10-20)

From the Weapon Mastery design ([weapon-mastery-plan.md](weapon-mastery-plan.md)); they apply
wherever the card is used.

1. **A `?` helper opens it**, not only a link in text: the mastery band's `?`, with a 44px tap area.
2. **A pop-up, never an in-place expansion of the table:** the table does not move, and the row's
   arrow keeps its one job (opening the row).
3. **A rule inside the card opens inside the card** (owner: "really cool if it's done right"):
   - in place, under the word tapped, so the text it came from stays readable beside it;
   - a second tap on the word closes it;
   - one level only: links inside the nested part are plain text;
   - the same in the roll result ("DC 13 Con save or Prone") and in spell, item and monster text.
4. **Long text stays readable:** the card scrolls inside itself, and a page scroll no longer closes a
   pinned card. (Today's scroll-to-close exists because the card is fixed-position; a pinned card
   needs to follow its anchor or sit as a bottom sheet on a phone. To decide in the mockups.)

Not decided: whether a hover-opened card (desktop) follows the same nesting, or nests only once pinned.
