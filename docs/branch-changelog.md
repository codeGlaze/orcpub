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

## Progress

Four of five done. Each was verified in a BROWSER, not by compiling — every one
of them hid a bug that a green build said nothing about.

1. **Heading into the shell** (`3b1ac947`). `auth-page` takes a heading, an
   optional lede and the body. Six copies of the orange drop-shadowed `div`
   gone; ink with a 54px amber rule instead. Copy tightened since the shell
   owns it. *Caught by looking:* every field was running out past the 435px
   column and over the gryphon — there is no global `border-box` here, so
   `width:100%` plus padding plus a border overflows. +16px before, -16px after.
2. **Error summary** (`70b6a410`). Counts FIELDS, not messages. Each line is a
   link that focuses its field, carrying the message rather than the field name
   — "Username — Username is required" says it twice and the link text is what
   a screen reader announces alone. *Also fixed:* converting to `auth-page`
   dropped the wrapper whose `text-align:center` everything below the fields
   relied on, so the submit sat hard against the left edge. `.auth-tail` owns
   that now, and the button is full width.
3. **Confirm-password retiring on reveal** (`141894fd`). Verified through the
   whole cycle: masked with the box, revealed without it, masked again with its
   old value intact. *Two bugs, both mine:* the submit check read the reveal
   state from the db while the component kept its own atom, so the confirmation
   would have been demanded even when revealed; and `:verify-password` was
   missing from the summary's field list, so a mismatch blocked submit while the
   summary sat empty.
4. **Domain-typo correction** (`bd434342`). Two edits on plain Levenshtein,
   where a transposition costs two — so one transposition is offered a fix and a
   transposition plus a wrong letter is not. Lives in `registration.cljc` for
   the shared suite. Binds to MOUSEDOWN: on click, pressing it blurs the field,
   the blur re-renders the notice, and the button dies between mousedown and
   mouseup.
5. **The sweep** — NOT STARTED. The legal links printed twice, the phone
   gutters, and the reset page's help link whose clickable target is the word
   "whitelist" in the middle of a sentence.

### The garden-harvest merge

`refactor/garden-harvest` landed on `integration-local` first, and integration was
then brought forward into this branch, so the 8 conflicts got resolved here — where
the auth decisions live — rather than by whoever integrates later.

All 8 are the same shape: harvest converted an inline style inside a block this
branch had already deleted and replaced. Seven take this branch's side outright, and
six classes harvest introduced go orphaned as a result (`success-header`, `m-t-100`,
and the four `password-strength-*`) because `auth-page` and `.pw-slots` replaced
every call site.

**The eighth was not a take-ours**, and taking one would have been a silent
regression: harvest also put the updates checkbox on `.t-a-l.m-l-15` and gave the
tick a `.checkbox-border` this branch never had. Resolved as a real merge — this
branch's meter, harvest's classed wrapper and border.

### The one out-of-scope commit

`042ee596` stops `.m-b-10` — a MARGIN utility — carrying `font-weight:bold`,
which it did because a garden selector group read as a descendant chain and was
not one. Measured three ways rather than guessed:

| | |
|---|---|
| source uses | 194 |
| actually render (7 pages, logged out) | 11 |
| compute a different weight | 7 |
| **look different** | **5** |

The two that do not are the character-list filter bar and the builder tab strip:
the container's weight changes, every child sets its own, the pixels are
byte-identical. The five real ones are the legal footer on register/login/forgot,
the forgot-password lede and the monster sort bar — all accidental bold making
ordinary text insist on itself. Logged-out pages, so the true count is higher,
but the ratio holds.

### Still open, and not blocking

- **The heading's alignment** — centred as now, or left against the form's edge.
- **The breach threshold** on `feature/password-rules` — refuses on ANY corpus
  hit, which is a default rather than a decision.
- **The registration throttle notice copy**, which has had no review.

### The design record

Mockups and measurements this was built from, all published artifacts:

- Target mockup, live: <https://claude.ai/artifact/SyGom3JjPLRAzUPxY9Wet8>
- Auth pages as built, before/after: <https://claude.ai/artifact/AoD2LtGs4MPZaQtYtxzmQG>
- Error treatments, five options: <https://claude.ai/artifact/FepUHoE9bnkKvxcfCip8PU>
- Message catalogue, twelve surfaces: <https://claude.ai/artifact/HdxzkGSDJ3dsxKjJX6pt38>
- Password meter: <https://claude.ai/artifact/TwQXi2roVmtVuLqitXBPzZ>

### Running any of this again

- `DATOMIC_URL="datomic:mem://orcpub" lein e2e-server` — the `:e2e` profile's
  env reaches the app through `.lein-env`, which every lein task rewrites, so a
  concurrent task can strip it. Passing it explicitly is immune.
- Playwright scripts must run from the project root; `node_modules` is there.
- `lein test` takes longer than a 120s window. Background it.

## The order it was built in

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
