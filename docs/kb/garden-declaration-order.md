# Garden writes big style maps in no fixed order

*Found 2026-11-23 on `fix/sheet-polish`, from a tester's Firefox screenshot.*

## The trap

A Clojure map literal with more than 8 keys is a hash map, so Garden writes its declarations
in hash order, not the order they were typed. When a rule sets a longhand (`mask-composite`,
`border-bottom`) and its shorthand (`mask`, `border`) in the same big map, the shorthand can
land after the longhand and reset it.

Two cases were live on `integration`:

- `.callout-accent-brand:before`, the notice ring: `mask` came after `mask-composite: exclude`.
  Firefox reset the composite and painted the whole gradient over the notice, hiding its text.
  Chrome looked right only because `-webkit-mask-composite: xor` happened to come last.
- `.weapon-opened` (`styles/weapons.clj`): `border` came after `border-bottom`, so the opened
  weapon panel lost its 3px strip in every browser.

## The fix and the guard

Put properties whose order matters in a map of their own: Garden writes several maps in one rule
in sequence, and a map of 8 keys or fewer keeps its typed order (`styles/portrait.clj` already
did this for its mask rules).

`test/clj/orcpub/styles/shorthand_order_test.clj` compiles the app stylesheet and fails on any
rule that sets a property before a shorthand that resets it. Its table names each shorthand and
the `name-*` properties it leaves alone (`flex-direction`, `outline-offset`, `overflow-wrap`); no border
shorthand resets a corner radius, so `border-top-left-radius` before `border-top` passes. It reads
rules inside `@media` blocks too: each body must end at a `}`, or the first rule of a block is
swallowed as the block's own body and never checked (CodeRabbit on #96).

