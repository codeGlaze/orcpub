# Branch changelog — `feature/auth-redesign`

## Why this branch exists

A design pass ran over the fourteen auth pages and produced decisions, not code. They sat
undone long enough that the pages were repeatedly mistaken for having been redesigned — they
had not been touched at all. This branch is that pass, built.

The decisions it implements, each settled before a line was written:

- **One heading.** `registration-page` is already the shared shell and all nine pages use it —
  an earlier reading of this branch claimed fourteen hand-copied layouts and was simply wrong,
  and the claim survived into the first version of this file. What IS copied is the heading: the
  same orange drop-shadowed `div`, six times over. The shell takes the heading instead, so the
  new ink-and-amber treatment lands everywhere at once rather than six times.
- **Notched outline labels.** A real `<label for>` riding on the input's border, so the field
  never stops saying what it is — a placeholder disappears the moment somebody types.
- **An ink heading with an amber rule**, replacing the orange drop-shadowed word.
- **The card capped and centred**, with the site's own background as the gutter.
- **Errors in GOV.UK order with a light field:** the label lifts out of the notch, the message
  sits under it, the input follows. A rail down the group is the only container; the field
  carries a 1px border and a soft tint rather than a heavy outline plus a boxed message, which
  is what made the first attempt look pinched. The words stay in ink so only the mark and the
  label are red, and red text never sits on the tint.
- **`#d4351c`** for that red, over the site's wine red, which went muddy over a tint. Measured
  in every position it occupies: 4.86:1 as text on the card, 4.52:1 as a border against its own
  tint.
- **A reveal, and a confirm box that retires when you use it.** NIST asks for the option to
  display a password and we offer none. Two boxes while it is masked, one while it is not.
- **Confirm the email, not the password.** A mistyped password is recoverable; a mistyped
  address creates a dead unverified account holding the username its owner wanted, and mails a
  stranger on the way. A domain-typo suggestion sits under the field.

**Built on what already exists.** `.field-notice` with its `is-error`/`is-warning`/`is-note`
tones and its single `.field-notice-action` is the project's own component, used by the
character builder's image field. `callout` and `notifications/message` likewise. An earlier
version of the mockup reinvented all three beside a codebase that already had them.

**The work that makes reuse possible:** every one of those components is toned for the dark
app — `rgba(255,255,255,.06)`, `red-on-dark`, `color:white`. The auth card is white. Dropping
them in as-is puts white text on a white card. Light-ground variants come first.

**Parent:** `feature/password-rules` @ `934f5aa4` — the strength meter and the shared validator
live there and this builds on both.
**Return path:** merges back to `feature/password-rules`, which merges to `integration-local`.

**Left open deliberately:** the heading's alignment (centred, as now, versus left against the
form's edge) and the breach threshold, neither of which blocks the shell.

<!-- Entries below as work lands. One change per bullet, ending with (`shorthash`). -->

## The order the rest is being built in

Each step lands on its own and leaves the branch working.

1. **The heading moves into the shell.** `registration-page` gains an arity taking a heading and
   an optional lede, rendered as ink with an amber rule. Six copies of the orange drop-shadow
   `div` collapse into it. Done first because every other page benefits without being touched.
2. **The error summary.** A `callout` above the form on a failed submit, counting FIELDS rather
   than groups, so the first fault is not off screen on a long form.
3. **The confirm-password box, retiring on reveal.** Two boxes while it is masked, one while it
   is not — and `display:none` rather than faded, so it leaves the accessibility tree and a
   submit cannot fail pointing at a field nobody can see.
4. **The domain-typo suggestion.** `.field-notice-action`, the same one-action-per-notice shape
   the character builder's image field already uses for its "Use this instead". Bind it to
   mousedown: on click, pressing it blurs the field, the blur rewrites the notice, and the button
   is destroyed between mousedown and mouseup — the fix appears to do nothing.
5. **The sweep.** The legal links printed twice, the phone gutters, and the reset page's help
   link whose clickable target is the word "whitelist" in the middle of a sentence.

Still deliberately open: the heading's alignment, and the breach threshold.
