# Branch changelog — `feature/password-rules`

## Why this branch exists

Password rules here disagreed with themselves: `password-strength` scored against eight
characters and four character classes, while `validate-password` enforced six characters and
nothing else, so the form graded people against one standard and admitted them on another.

Current guidance (NIST SP 800-63B-4, OWASP Authentication Cheat Sheet) says character-class
requirements shall not be imposed — they push people toward predictable shapes — and asks for
length plus screening against known-bad passwords instead. This branch follows that: a longer
minimum, a few cheap pattern rules, and an optional breach check that must never be able to take
signup down with it.

**Parent:** `integration-local` @ `593cb50c`.
**Return path:** merges back to `integration-local`. Nothing is stacked on this branch.

## Added

- **Passwords are checked for the obvious patterns** — three or more of the same character in a
  row, runs like `1234`, `abcd` or `qwerty` in either direction, and passwords carrying the
  account's own username or email. Shared by the browser and the server, so both agree.

- **A new password is checked against known breaches** — the password itself never leaves the
  server: it is hashed here and only the first five characters of that hash are sent, so the
  service answers with a few hundred candidates and the match is made locally. A slow or missing
  service is not an objection — signup carries on. `ORCPUB_PWNED_CHECK=off` turns the call off
  entirely. The result is worded as a
  strength verdict — "too common" — not a security warning. The corpus only tells us a password
  is common; saying "breach" implies this person was breached, and naming attackers conjures
  someone coming for them. Neither happened. An explanation of how the check runs without the
  password leaving in readable form is available for anyone who asks.

## Changed

- **The minimum password length is eight, not six** — and it is named once, so raising it later is
  one edit. Existing passwords keep working at sign-in; the new floor applies when one is set or
  reset.
- **No character-class requirements are imposed**, per NIST SP 800-63B-4. The strength meter still
  counts them as encouragement; nothing is gated on them.

## Still open

Everything below was raised during this branch's work and deliberately not done
here. Listed so it is tracked rather than remembered.

**Needs a decision from you, not work from me**

- **The breach threshold.** Screening currently refuses a password on ANY
  appearance in the corpus — the NIST-strict reading, and a default rather than
  a choice. Measured alternatives: blocking at 10 changes nothing in practice;
  blocking at 100 lets `Password-!` through at 44 hits; blocking at 1000 lets
  `Dragon7!` through at 488. The softness probably belongs in the wording rather
  than the number, since everyone a low threshold stops chose something
  thousands of other people also chose.
- **The registration throttle notice.** New copy and a new red style
  (`.registration-notice`). Neither has been reviewed.

**Designed, specified by the preview, not built**

- **The browser-side breach lookup.** The preview shows a chip that sits dashed
  while a lookup is out and then resolves. Nothing like it exists: the check is
  server-side at submit only, so there is no live feedback and no waiting state.
  The design is settled — SHA-1 in the browser, five hex characters through our
  own server so the page's CSP needs no new origin, compare locally, fire on
  blur rather than per keystroke, and bind the verdict to the exact string it
  was asked about so it is discarded rather than shown against text that has
  changed.
- **The in-app sign-in notice.** OWASP's guidance is that a failed-sign-in
  warning "should be displayed next time they login, and optionally emailed to
  them as well". Only the email exists. The in-app half needs somewhere to
  persist the event and a surface after login, and it is the better channel:
  no mail-bomb surface, so the 24-hour cooldown exists only to protect the
  weaker one.
- **The screened-at timestamp.** A positive `:orcpub.user/password-screened`
  instant, absence meaning never screened. It is worth having only alongside
  the nudge it enables, and the nudge's audience is every account that exists
  today, since a bcrypt hash cannot be screened retroactively. NIST supports
  the shape: no scheduled expiry, but force a change on evidence of compromise.
  Explicitly NOT paired with a re-check at login — the password is handed over
  to sign in, and screening it there is a second use of a credential given for
  one purpose.

**From the auth design pass, none started**

- The `auth-page` higher-order function. Fourteen pages are variations on one
  shell and each carries its own copy.
- Notched outline labels, `<label for>`, `aria-invalid` and `aria-describedby`
  on `base-input`/`form-input`.
- **Stop dimming the submit button.** The design pass decided a submit button
  should never be disabled. It still dims while validation is non-empty, which
  is why the throttle notice had to be routed around it under `:general` rather
  than keyed to a field.
- The tablet gutter (`width:435px` inline → garden) and the +2px box-sizing fix.
- Copy rewrites across the fourteen auth pages.

**Smaller**

- The login form still answers `:unverified` distinctly, which is correct for
  usability but is a narrower version of the oracle closed elsewhere.
- NIST asks that at least 64 characters be accepted and that nothing be
  truncated. Not audited.
