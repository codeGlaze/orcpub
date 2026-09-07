# Before you start — review lessons, indexed by what you are about to do

**The problem this solves.** The KB is 45+ documents indexed by *topic*. Every lesson in it was
written where it was *learned* — a class-name collision is recorded in a spell-conversion gallery —
so an agent about to name a CSS class never reads it. This page is indexed by **task**, and is the
one file worth reading before touching anything here.

Each entry is short on purpose. It says what to check, and points at the evidence.

---

## Before designing ANYTHING (a control, a palette, a layout)

1. **List the branches.** `git for-each-ref --sort=-committerdate refs/remotes/origin | head -25`.
   A whole design system existed on `port/redesign-on-refactor` for two months while two generations
   of builder work invented colours and spacing from scratch. It takes one second.
2. **Grep the KB for the thing, not just the code.** `grep -rin <term> docs/`.
3. **`git log -S <identifier>`** for the code.

> Evidence: `frontend-redesign-parallel-work.md`. The KB audit added rules 2 and 3 in September and
> they were applied to code identifiers only; nobody ran rule 1 before choosing a palette.

## Before adding a CSS class

- **Namespace it.** `styles/core.clj` is 3,000+ lines of utility classes. `field`, `row`, `tag`,
  `chip`, `card`, `input` are all live names. Builder-framework classes are `bf-`; OMV's are `opt-`
  and `select-menu-`.
- **The failure is silent**: the form renders, it just spaces wrong.
- `test/cljc/orcpub/dnd/e5/builder_class_names_test.cljc` gates this — a new unprefixed name fails
  until someone adds it to the allow-list deliberately.

> Evidence: a global `.field { margin-top: 30px }` gave every declarative field 30px it never asked
> for; the toggle pair became a 106px box holding two 16px rows.

## Before changing how a control is RENDERED

Checkbox → chip, `<select>` → popover, anything of that shape. **Every measurement written against
the old rendering goes blind, silently, and keeps passing.**

- Search the e2e helpers and metrics for the old tag/class: `grep -rn "select\|fa-check\|\.chip" test/e2e/`.
- Four separate control-count metrics missed this in one session. Twice the count did not move while
  controls *disappeared*.
- **Prefer a representation-independent measure.** `builder-gallery.js` counts visible field
  **labels** as well as controls, because a label survives a control changing shape.

> Evidence: `builder-conversion-gallery.md`, "two bugs this round, and one was in the measurement".

## Before converting a builder to the declarative framework

1. **Write the pin against the EXISTING form first, and see it green.** Then swap, then re-run
   unchanged. A pin written after the swap describes the new form and protects nothing.
2. **Pin on what is SAVED, not on what you typed.** A form can look perfect and store
   `[:school] "abjuration"` under a key vector.
3. **Keep the control count equal.** Any deliberate addition or removal is its own step, named.
   Otherwise the before/after is uncomparable — and a *drop* can be reported as a win.
4. Check the overlap map first (`builder-form-schemas.md` §5b): does this builder **reuse** the
   vocabulary or **extend** it? That, not form length, predicts the cost.

## Before styling with a value taken from a mock or another branch

- **Check what it was designed against.** A card colour is a *relationship* to its page, not an
  absolute. `#1b232f` is a whisper of lift on `#161d27` and a chunky pale block on `rgb(8,10,13)`.
- Prefer a relative expression (a translucent overlay, `var(--accent)`) over a literal.

## Before believing a CSS change worked

- **`lein garden once` can fail while everything downstream stays green.** `fig:build` succeeds, the
  whole e2e suite passes against stale CSS, and only a screenshot shows the truth.
- Check its exit code. Do not pipe it through `tail` and read past the failure.
- `[:.a:not(.b)]` is not valid Clojure — the reader takes `.b` inside parens for a member
  expression. Use `garden.selectors`, or restructure.

## Before reporting a UI change as done

- **Look at the screenshot.** Three things this session were invisible to a green test suite: a
  layout regression that measured *taller with fewer controls*, three checkboxes that vanished, and
  a "new character" confirm that was never answered so every later check described the wrong
  character.
- Measure both widths. `mobile-compare.js` at 390px.

---

## Adding to this page

Add an entry when a review catches something a *rule* would have caught — not for one-off bugs. Keep
it to the check and one line of evidence; the story belongs in the doc where it happened.
