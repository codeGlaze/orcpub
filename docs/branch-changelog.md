# Branch changelog — `feature/auth-redesign`

## Why this branch exists

A design pass ran over the fourteen auth pages and produced decisions, not code. They sat
undone long enough that the pages were repeatedly mistaken for having been redesigned — they
had not been touched at all. This branch is that pass, built.

The decisions it implements, each settled before a line was written:

- **One shell.** Fourteen pages each carry their own copy of the header, gutters, gryphon and
  legal footer. They have drifted apart accordingly. An `auth-page` function replaces them.
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
