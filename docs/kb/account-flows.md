# Account flows: signing up, signing in, recovering

What was learned rebuilding registration, verification, login and password reset on
`integration-local`, September 2026. Checked against `integration-local` at `bbf33288`.
Symbols are named rather than line numbers, because those move.

The code says what these flows do. This says what was **traced, measured, and got
wrong** — including four defects that a green build, a passing suite and a design pass
all failed to catch, and why.

---

## 1. The reason none of it had coverage

**The verification key and the password-reset key exist only inside an email.** No page
prints them, and `do-verification` transacts the user and then sends the mail *inside the
same try* — so with no SMTP the send throws and registration returns "Unable to complete
registration". You cannot finish signing up in a browser.

Every browser suite worked around this by starting from a user `e2e-boot` seeds straight
into Datomic. So signup, verification and reset had **no coverage and no route to any**,
and a reset form that refused every ordinary password in silence reached a merge.

`scripts/e2e/lib/mail-sink.js` is a dependency-free SMTP server (~100 lines, no package)
that accepts anything and hands the message back. `run.sh` starts it and points
`EMAIL_SERVER_URL`/`EMAIL_SERVER_PORT` at it, so a suite follows the link a person clicks.
`scripts/e2e/auth-flows.js` is the result: 36 checks over register → verify → login →
reset → back in.

**Generalisable:** when a flow's only output is an email, the absence of a mail server is
not a testing inconvenience — it is a hole in the product's coverage that hides real bugs
indefinitely. The fix is cheap and should have existed for years.

### Traps inside the harness itself

Two of these cost a false failure each, and both looked like app bugs:

- **`mail-sink.waitFor` returns a message ALREADY HELD.** Clear the sink before the action,
  or you match the previous account's mail — which re-verified somebody else, left the new
  account uncreated, and surfaced three steps later as reset mail that never arrived.
- **A signed-in session redirects the register page.** A block that registers after one
  that logs in registers nobody, silently. Sign out between blocks.
- A long-lived e2e server hits `registrations-per-host-hourly` (10) and later registrations
  quietly do nothing. Restart it before a capture run.

---

## 2. The stale-artifact asymmetry

**The single most expensive trap here, and it generalises past this repo.**

`lein e2e-server` is `lein run`: the **server compiles from source at every boot and is
never stale**. The **bundle and the stylesheet are artifacts on disk** that nothing
rebuilds. Edit a `.cljs`, boot a genuinely fresh server, and the page runs the OLD code
while every signal says it is new.

It cost real time in this batch: a suite reported the generic error message, and several
steps went into reading correct client code looking for a fault in it. The code was right;
the bundle was from before the fix.

`scripts/e2e/run.sh` now compares artifact mtimes against sources, names what is newer, and
rebuilds. Three details that make it hold:

- **It names the newer file**, so you see why it rebuilt rather than just waiting.
- **It rebuilds the kind already on disk.** Dev and prod bundles are not interchangeable —
  the CSP branch keys off which one it is, so silently swapping trades this trap for a
  subtler one.
- **`E2E_SKIP_BUILD=1`** keeps the artifacts and says loudly that you are testing old code,
  so the escape hatch cannot be taken by accident.

The same check covers `styles.css` against `src/clj/orcpub/styles`.

**The lesson:** this trap exists wherever **some parts compile from source and others are
prebuilt artifacts**. The fresh half is what makes the stale half invisible. Anywhere that
split exists, the runner must check, not the human.

---

## 3. The password rules, and what was decided

One shared validator, `orcpub.registration` (`.cljc`), is the only rule set. Both places a
password is set run it server-side: `register` and `reset-password`.

| Rule | Value |
|---|---|
| Minimum length | **12** |
| Composition rules (symbols, capitals) | **none, deliberately** |
| Shape | no 3-in-a-row, no 4-char runs or keyboard rows, ≥5 distinct characters |
| Identifier | the password may not contain the username or the email's local part |
| Breach corpus refusal | **≥ 1000 appearances** |

### Why no composition rules

NIST SP 800-63B-4 says character-class requirements SHALL NOT be imposed: they push people
to `Password1!` and no further. Length plus refusing obvious shapes is what the guidance
asks for instead.

### Why 1000 and not "any hit" — a decision with a rejected alternative

It originally refused on **any** corpus appearance. That was never the design, which was
three layers: local rules block the low-effort, the meter dissuades from common shapes, the
corpus refuses only the egregious. The first and third had collapsed into one veto.

The corpus measures **commonness, not danger to this person**. A password appearing once
leaked in somebody else's dump; one appearing four figures of times ships in every cracking
wordlist. On a site whose worst loss is a character sheet, refusing on a single appearance
turns a measure into a veto.

Measured: `password-!` = 297 appearances (now passes, the meter's to argue with),
`letmeinletmein` = 2298 (refused). `aaaaaAAaaa!` = 0 corpus hits but fails the local rules —
which is why **the two are complementary, not redundant**.

The mechanism was already built and unread: `pwned/check` returns the actual count and both
call sites flattened it to a boolean. `breach-message` even took the count and discarded it
as `_n`.

### Separator stripping

`repeated-run?` and `sequential-run?` both look at NEIGHBOURS, so `5 5 5 5 5 5` is not three
in a row and `a-b-c-d` does not step by one — while each is exactly what it looks like.
Both run against the raw string AND a separator-stripped copy. `too-few-distinct?` catches
what neither can see: two characters taking turns.

**`\p{L}` and `\p{N}` are Java-only.** The JVM suite passed and 21 CLJS assertions failed.
The character class is spelled out as explicit ASCII ranges to match.

---

## 4. Four defects a green build did not catch

Every one was found by **rendering the page**, not by reading a diff or trusting a suite.

1. **The password-reset form refused every ordinary password in silence.**
   `:messages password-messages` had been commented out since the dual-build era, while
   `invalid?` still counted those same messages toward dimming the button. The rules gated
   the form and their reasons were suppressed. Survivable at a minimum of 8; at 12 it bites
   constantly — on account recovery, the last door somebody has.

2. **The server's reason never left the server.** `reset-password` answered
   `{:status 400 :message "..."}`, but **`:message` is not a Pedestal response key** — it is
   dropped, and the reply is a 400 with an empty body. Proved on the wire. Field-keyed
   messages in `:body` now, the shape registration already used.

3. **Two verdicts that disagreed.** A password the corpus refused was drawn as a red field
   error directly above a meter reporting UNCOMMON, in green, about the same string. The
   corpus verdict now has its own key (`:password-common`), the fail colours, a badge and
   the reasoning under the bar.

4. **The auth card's text was set solid.** The CSS reset sets `body{line-height:1}`, and a
   **unitless line-height inherits as a NUMBER** — so every element that did not set its own
   rendered at its own font size, descenders into the next line's ascenders. Measured: 11
   elements under a 1.25 ratio, **nine at exactly 1.00**. Set once on
   `.registration-content`; `.notch` and `.lift` pin their own, being positioned to the
   pixel.

Also: three of the nine auth pages had never been redesigned at all, despite a changelog
claiming step one covered every page — and they were the three somebody locked out of their
account actually sees.

---

## 5. Components shared, composition not — the recurring shape

This happened **twice**, the same way, and is the most repeatable lesson here.

`auth-page` shared the card and the heading and took the body as an **opaque blob**. Four
pages then hand-assembled the identical
`[:div [:div.m-t-10.auth-form …] [:div.m-t-10.auth-tail …]]`, one nested it wrongly and gave
its own submit a double gutter, and login used a container of its own that made its fields
350px where every other page's are the column's width.

`password-pair` and `password-meter` were shared components, but **the composition of them
was not** — each page wrote out the same pair, meter, mismatch derivation and reveal
handling for itself, in two state idioms.

**That is why every fix had to be applied once per page.** The parts were extracted; the
assembly was not. Now: `auth-form-page`, `auth-outcome`, `password-fields`, and
`registration/password-pair-faults` for the derivation.

**The tell:** if a fix has to be applied N times, the abstraction stopped one level too
high. Extracting the visibly-duplicated thing (a heading copied six times) while leaving the
invisibly-duplicated thing (an assembly written four times) is the failure mode.

---

## 6. Security decisions, with their rejected alternatives

### The reset page's identifier check

The page judges against the **username only**, read from the session token's `:user` claim.
The server judges against the username **and the email's local part**.

**Rejected:** putting the email in the token to match. That is a real new disclosure and a
worse one than the gap it closes. The server still catches it, and its reason now reaches
the page.

**On the oracle question** — the "not your name" chip confirms a guess, it does not reveal
one. Measured: `k`, `ka`, `kay`, `kayl`, `kayle` and `kaylea` are all **silent**; only the
whole username flags, because `contains-identifier?` tests containment. And reaching the
page needs the emailed key, whose cookie **already carries the username in plain base64** —
`jwt/sign` is JWS, signed and not encrypted. The chip is the slower route to a fact the
cookie hands over.

### Login says nothing about which half was wrong

A wrong password and an unknown username answer **identically**, asserted in the suite.
Naming the username as unknown made the login form the same membership test the reset
endpoint was, and a cheaper one — it needs no mail sent.

Reset answers 200 for any address, **including once throttled**: an endpoint that responds
differently under a limit is an oracle for whether the limit was reached, which is the
membership test the uniform 200 exists to close.

### The account limit sits on the FAILURE path

Five wrong guesses turn away the sixth **wrong** guess; the real password still gets the
owner in. Locking the account would hand anybody a way to lock anybody out. The suite pins
both halves, because the tempting "fix" is the DoS.

### Notifications

Six outbound mails existed and **none fired on a credential change**. A takeover was silent
until the owner tried to log in, and then all they learned was that they could not. Now:

- a completed reset tells the account, with the time and the browser
- an email change tells the address **losing** the account — the verification goes to the
  new address, which is the one place the owner cannot read if it was not them
- moving an account takes the **password**, not just a session, checked before the address
  is looked at so an unauthenticated caller cannot learn which addresses are taken

Both notices run on another thread and swallow their own failures: each fires after the
thing it reports has been committed, and a mail failure must not report an error for
something that happened — somebody would simply do it again.

Neither notice carries a working credential. An unexpected mail holding one teaches people
to click exactly what a phishing mail sends them.

---

## 7. Open, and deliberately so

- **The reset token is a full session token.** Measured: `/user` returns 200 with it, 401
  without. Following a reset link signs you in for an hour without a password being entered.
  Pre-existing. It grants no more than the key already grants, but the silent-session path
  is **stealthier** than the change-the-password path, which the owner notices. Two fixes
  offered and not taken: a scoped claim (`{:scope :password-reset}`), or a shorter life.
- **Deploy note:** the reset window went 24h → 2h AND keys are stored as SHA-256 digests, so
  any reset link already in an inbox stops matching on deploy. It fails gracefully onto the
  expired page, which offers a new one.
- **`ORCPUB_PWNED_CHECK=off`** is the kill switch for the corpus call. It already fails open
  on a timeout; this is for the service being slow rather than down.

---

## 8. Corrections — claims made here that were wrong

Recorded because the confident wrong version is what survives otherwise.

- **"Fourteen hand-copied layouts."** `registration-page` was always the shared shell and all
  nine pages used it. What was copied was the heading, six times.
- **"The phone gutters are broken."** Read off the CSS, not measured. At 390px the card
  renders fine. The item was invented.
- **"432 CLJS tests / 1949 assertions."** A stale `target/test`. Clean builds give a stable
  number; quote it from a clean run or not at all.
- **`text-wrap: balance` on the help line made it worse** — with an inline link spanning a
  wrap it turned two clean lines into three ragged ones. Applied, measured, reverted.
- **The `pw-fill-N` / `pw-name-N` classes look unused to grep.** They are built at runtime
  with `(str "pw-fill-" suffix)`. Not dead — check before deleting.
- **A zero-conflict merge is not a correct merge.** Merging into `integration-local` twice
  silently replaced its `docs/branch-changelog.md` with the incoming branch's, because
  integration's tip is the merge base for every branch cut from it. Check `head -1` after
  every merge.
