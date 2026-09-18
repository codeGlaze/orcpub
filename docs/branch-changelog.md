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

## Changed

- **The minimum password length is eight, not six** — and it is named once, so raising it later is
  one edit. Existing passwords keep working at sign-in; the new floor applies when one is set or
  reset.
- **No character-class requirements are imposed**, per NIST SP 800-63B-4. The strength meter still
  counts them as encouragement; nothing is gated on them.
