# Before you start

Review lessons indexed by **what you are about to do**, not by topic. The KB is 45+ documents and
every lesson in it was written where it was *learned* — the class-name lesson lives in a
spell-conversion gallery — so nobody about to name a class ever finds it.

**This page stays short by design.** See "How this page stays small" at the bottom; if it has grown
past two screens, that policy is being ignored.

---

## Already enforced — you do not have to remember these

A rule a machine can check is not a paragraph. These fail on their own; the row is here so you know
the gate exists and what it means when it fires.

| If you… | What fails | Why it exists |
|---|---|---|
| add a CSS class in the builder block | `builder_class_names_test` — must be `bf-`/`opt-`/`select-menu` prefixed, or allow-listed deliberately | a global `.field {margin-top:30px}` silently gave every declarative field 30px it never asked for |
| remove or hide a field | `builder-gallery.js` diffs **labels and controls** against `test/e2e/builder-baseline.json` | a control count is blind to its own rendering; three checkboxes once vanished while the count read the same |
| change a field's save shape | the per-builder pins read back what was **stored**, not what was typed | a form looked perfect and saved `[:school] "abjuration"` under a key vector |

If a gate fires and the change is deliberate: re-record the baseline / add the name, **and say why in
the commit**. That is the whole point of it being a decision.

---

## Judgement calls — no test can catch these

### Before designing anything (a control, a palette, a layout)

**List the branches first.** `git for-each-ref --sort=-committerdate refs/remotes/origin | head -25`

A whole design system sat on `port/redesign-on-refactor` for two months while two generations of
builder work invented colours and spacing from scratch. Grep the KB for the *thing* as well as the
code (`grep -rin <term> docs/`), and `git log -S <identifier>`.

### Before borrowing a value from a mock or another branch

**Check what it was designed against.** A card colour is a relationship to its page, not an absolute:
`#1b232f` is a whisper of lift on `#161d27` and a chunky pale block on `rgb(8,10,13)`. Prefer a
relative expression — a translucent overlay, `var(--accent)` — over a literal.

### Before converting a builder

1. Write the pin against the **existing** form and see it green. A pin written after the swap
   describes the new form and protects nothing.
2. Keep the control count **equal**. Any addition or removal is its own step, named — otherwise the
   before/after is uncomparable, and a *drop* gets reported as a win.
3. Check the overlap map (`builder-form-schemas.md` §5b): does this builder **reuse** the vocabulary
   or **extend** it? That, not form length, predicts the cost.

### Before believing a CSS change worked

`lein garden once` **can fail while everything downstream stays green** — `fig:build` succeeds, the
whole e2e suite passes against stale CSS, and only a screenshot shows the truth. Check its exit
code; do not pipe it through `tail` and read past the failure.

(`[:.a:not(.b)]` is not valid Clojure — the reader takes `.b` inside parens for a member expression.)

### Before reporting a UI change as done

**Look at the screenshot, at both widths** (`mobile-compare.js` runs at 390px). Three things here
were invisible to a fully green suite: a layout regression that measured *taller with fewer
controls*, three checkboxes that vanished, and an unanswered "new character" confirm that left every
later check describing the wrong character.

---

## How this page stays small

It will rot into another unread document unless entries leave it. Three rules:

1. **An entry earns prose only if a machine cannot check it.** If it can be checked, write the check
   and the entry becomes one row in the table above. Two already have.
2. **Add only what a review had to supply** — a rule that would have prevented the mistake. Not
   one-off bugs; those belong in the doc where they happened.
3. **Retire on evidence.** If a lesson has not been re-learned in several sessions and the thing it
   guards has changed shape, delete it. A stale caution costs more than the mistake it prevents.

The measure of whether this works is not that the page exists — it is whether the next review finds
**new** problems rather than the same ones.
