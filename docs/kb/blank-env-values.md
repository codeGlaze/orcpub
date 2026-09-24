# A blank environment value is not an unset one

**The rule:** `(or (env :k) "default")` is wrong. Environ returns `""` for a variable that
is exported but empty, `""` is truthy in Clojure, so the default is never reached.

That one line explains an auth bypass, a broken Datomic URI, two silent Docker defects and
three `Integer/parseInt` crashes. It was written independently at five sites by people who
were being careful — which is why this document exists, and why the fix is a lint rule
rather than a helper.

Found across three review rounds on `hotfix/locale-safety` (PR #695), 2026-09-20/21.
See also [`env-and-auth.md`](env-and-auth.md) for how env vars are loaded at all, and
[`locale-safety.md`](locale-safety.md) for the sibling class (locale-dependent case folding)
that the same branch was opened to fix.

## Why it kept happening

Three things had to line up, and all three were present:

1. **The wrong version reads correctly.** `(or (env :app-name) "OrcPub")` looks like it
   applies the default when the variable is not set. Nothing about it looks suspicious.
2. **`.env.example` ships nine keys with empty values.** Blank is the *documented* state of
   an unset optional setting, not an edge case someone has to contrive.
3. **`docker-compose.yaml` passes seven variables as `${VAR:-}`** — explicitly empty —
   whenever the operator configures nothing. That is the default deployment.

So the trigger was shipped, documented and default, and the bug was invisible on inspection.

**The guards that existed were on the wrong line.** Several sites were written as:

```clojure
(or (env :datomic-url)
    (some-> (System/getenv "DATOMIC_URL") not-empty))   ; <- the guard
```

Environ reads environment variables *itself* and answers first, so the `not-empty` sat on a
branch that never runs. Someone had thought about the empty case and guarded the unreachable
path. Care was not the missing ingredient.

## What it cost

Each measured, not inferred.

| trigger | behaviour |
|---|---|
| `SIGNATURE=` | **Auth bypass.** JWTs signed *and verified* against `""`. |
| `DATOMIC_URL=` | `get-datomic-uri` returned `"?password="` — not a URI. |
| `DATOMIC_PASSWORD=` | `?password=` appended to an otherwise valid URI. |
| `CSP_POLICY=` | Silently selected the static permissive policy, not the documented `strict`. |
| `EMAIL_SERVER_PORT=` | `(Integer/parseInt "")` threw at send time. |
| `APP_FIELD_LIMIT_*` | Same, three more `parseInt` sites in `branding.clj`. |
| `EMAIL_FROM_ADDRESS=` | Docker sent mail with an **empty From address** — the branding fallback never applied. |
| `LOAD_HOMEBREW_URL=` | `index.clj` emitted `fetch('')` on **every page load** — an empty URL resolves to the current page, so each load re-fetched itself and tried to parse the HTML as an `.orcbrew` pack. |

### The auth bypass, in detail

`routes.clj` read the secret raw and guarded it with a **nil** check:

```clojure
(def ^:private jwt-secret (environ/env :signature))
(when-not jwt-secret (println "WARNING: SIGNATURE env var is not set ..."))
```

`""` is truthy, so a blank secret walked past the guard written for exactly this case.
`check-auth`'s `(if-not jwt-secret ...)` then never fired, and buddy verifies happily against
`""`. Demonstrated end to end: a token forged with `""` was admitted as `:username "admin"`;
after the fix the same request gets a 500.

**Severity in practice is low.** Reaching it requires deliberately clearing the line —
`.env.example` ships a change-me placeholder, the first-run flow generates a random value,
and `docker-compose.yaml` defaults to `change-me-to-something-unique`. It is a
misconfiguration-triggered bug, not remotely exploitable against a configured instance. The
maintainer was told directly and judged it did not warrant an embargo.

## The fix: a rule with teeth

`orcpub.env` (`src/clj/orcpub/env.clj`) is the one way to read an environment value:

```clojure
(env/value :k)            ; trimmed, or nil
(env/value :port "8890")  ; default applies to BLANK, not just unset
(env/flag? :dev-mode)     ; only the literal "true", matching the server
```

**A helper alone does not work, and this codebase is the proof.** `orcpub.config` already had
a `signature` accessor. `routes.clj` read `(environ/env :signature)` raw anyway, four times.
That is how the token bug survived. A second correct helper would have been the third copy of
a good intention.

So `.clj-kondo/config.edn` marks `environ.core/env` and `System/getenv` as
`:discouraged-var` at **`:level :error`**, with `orcpub.env` the only exempt namespace (plus
the two test namespaces that stub environ with `with-redefs`, exempted individually rather
than exempting all of `test/`). `lein lint` runs `--fail-level error`, so reintroducing the
pattern fails the build. Verified by reintroducing it and watching lint report `errors: 1`.

**Not a higher-order function.** There is no varying behaviour to parameterise — one rule, one
two-arity function, one predicate. That covers all 46 call sites.

`test/clj/orcpub/env_test.clj` pins the rule. Each of its five groups was verified to *fail*
against the naive implementation (8 failures when it was reinstated), because a test that
passes either way would not have caught any of the five original sites.

## The exception, and why it is not a wart

`email-cfg` in `email.clj` deliberately keeps `""`:

```clojure
{:user (env/value :email-access-key "")
 :pass (env/value :email-secret-key "")
 :host (env/value :email-server-url "")
 ...}
```

Because postal is a third-party library with its own opinion about the difference:

```
:host ""   -> MailConnectException: Couldn't connect to host, port: localhost, 587
:host nil  -> NullPointerException
```

Both fail — correctly, email is unconfigured — but one says why, and `""` is what the default
Docker deployment has always produced. The blank rule is right everywhere the *application*
interprets the value; it is not automatically right at a boundary where a library already
assigned the empty string a meaning. **Check the consumer before converting a call site.**

## Verifying a change like this against Docker

The useful technique, because reasoning about compose substitution is unreliable: run the
whole config surface under the exact environment `docker-compose.yaml` provides, at the
commit before and the commit after, and diff.

```bash
git archive <before-sha> | tar x -C /tmp/pre     # non-destructive, no worktree needed
# then in each tree, with the compose defaults exported:
lein run -m clojure.main probe.clj               # prints every config value
```

That is what caught the postal regression and found the two Docker defects. Twelve of
fourteen values were identical; the two that differed were both fixes.

Also worth knowing, and easy to get wrong: **the shell scripts cannot affect Docker.** The app
image is `ENTRYPOINT ["java", "-jar"]` with no `scripts/` copied in, and the transactor runs
`deploy/start.sh`. `scripts/common.sh` and `scripts/start.sh` are host-side dev tooling only.

## Open: registration lockout when SMTP is unset (worse than the bypass)

**Nothing checks whether email is configured.** `.env.example` says *"Leave EMAIL_SERVER_URL
empty to disable email functionality"* and no code implements that — "disabled" currently
means "sending throws".

`do-verification` (`routes.clj:337-354`) transacts the user **before** attempting the email:

```clojure
(try
  @(d/transact conn [... :verified? false :verification-key ...])   ; committed
  (send-verification-email request params verification-key)          ; throws
  {:status 200}
  (catch Exception e
    (throw (ex-info "Unable to complete registration. Please try again or contact support." ...))))
```

Datomic does not roll back. So on any instance without SMTP — **the default Docker state**:

1. The account is created, `:verified? false`
2. The email throws; the user sees *"Unable to complete registration. Please try again."*
3. Retrying fails validation — the username and email are now taken

**Severity, measured rather than assumed.** An earlier version of this document said the
account "can never be verified" and that registration was "permanently broken". Both were
wrong. The resend-verification route operates on exactly this orphaned state and succeeds,
and it is wired to a UI button (`views.cljs:653` dispatches `:re-verify`). Verified against
the pre-fix code:

```
register            -> threw
account left behind -> true
retry registration  -> 400        <- the confusing part
RESEND verification -> 200        <- recovery works
fresh key stored    -> true
```

So the real symptom is a confusing dead end with an escape hatch, not a lockout.

**That is also why seven years of production never surfaced it.** Live SMTP works, so this
branch only runs on a *transient* send failure — provider outage, timeout, greylisting, a
bounced recipient. The handful of users it reaches report *"it says my email already
exists"*, which is indistinguishable from someone who forgot they had an account.
Misattributed, not invisible. Worth remembering as a pattern: a bug whose symptom mimics
ordinary user error can survive any amount of production time.

Fixed on `hotfix/locale-safety` (`e4a69649`), by the rollback the sibling flow already used
— see `registration_rollback_test.clj`. The two callers need *different* rollbacks:
`register` passes no `:db/id` so the entity is retracted, while `re-verify` passes an
existing user and only the attributes that attempt set may be retracted. Retracting the
entity there would delete a real account; the naive fix passes every other test and does
exactly that.

Still open, and genuinely a product decision: an instance with no SMTP now fails cleanly
instead of orphaning an account, but still cannot register anyone. Auto-verify when email is
disabled, refuse up front, or an admin path? `.env.example` already promises "Leave
EMAIL_SERVER_URL empty to disable email functionality" and nothing implements it.

## How the registration piece was resolved (2026-09-23/24)

Settled as **auto-verify when email is unconfigured** — but keyed on an explicit
opt-in, not on the absence of SMTP, because the obvious version fails open.

`ALLOW_UNVERIFIED_REGISTRATION=true` **plus** an empty `EMAIL_SERVER_URL` verifies accounts on
creation and sends nothing. Empty `EMAIL_SERVER_URL` **without** it refuses to register anyone,
with an error naming both remedies.

### Why the opt-in, and what is not reachable

Asked directly whether an attacker could turn the guard against a properly configured
instance. Two things verified rather than argued:

- **The guard keys on configuration, never on send outcome.** A failed send goes to the
  rollback path; `verified? true` is only reachable when SMTP is unconfigured. Knocking the
  mail server over does **not** auto-verify anybody. This is the natural mistake to make and
  the one to keep not making.
- **`environ.core/env` is a static `PersistentHashMap` built once at namespace load.**
  Confirmed with `System/setProperty` after load: `configured?` unchanged. No request, input
  or runtime manipulation can flip it.

The real problem was the other direction — it **failed open**. Keying on "no SMTP" alone means
every way of *losing* the config reads as "the operator wanted no email". Measured, all three
silently produced auto-verify:

```
EMAIL_SERVER_URL=" "    whitespace, e.g. a copy-paste artifact
EMAIL_SERVER_URL=       dropped by a deploy, or a failed secret mount
(absent entirely)       typo'd variable name, renamed compose key
```

A public site would have gone from verified to **open** registration with no error and no
alarm. Not attacker-triggered, but the shape that gets found by scanning: no need to break
the guard, just wait for one operator slip. **A config failure must not downgrade security
silently** — that is the transferable rule here.

Three startup warnings now cover the abnormal states (no SMTP + opt-in; no SMTP + no opt-in;
SMTP configured *with* the flag set, which is inert today and decides policy the day SMTP goes
missing). A correct configuration prints nothing. None are fatal, deliberately: refusing to
boot over "registration is disabled" would take down character building, exports and every
existing user over a feature they are not using.

### OPEN — fold into the boot report when integration merges down

`config/print-report!` exists on `integration` and not on the hotfix branch, so
`report-registration-mode!` prints at namespace load beside the `SIGNATURE` warning instead.
**Move it into the boot report when the code meets it** — registration mode belongs with the
rest of the effective configuration, not three lines above an unrelated warning. The comment
on the function says the same.

### Deploying this to a live instance

Diffed a realistic production config (SMTP set, real secrets, optional extras blank) against
upstream `develop`. Only two values change, and one needs an operator decision:

| | before | after |
|---|---|---|
| `email-from` | `""` | `no-reply@orcpub.com` |
| `homebrew-url` | `""` | `nil` |

**`EMAIL_FROM_ADDRESS` is the one to check.** Blank in production meant outgoing mail carried
an *empty* From header; it now falls back to `no-reply@orcpub.com`, which SendGrid/SES will
refuse to send as unless that domain is authenticated. `.env.example` now ships it set, with
the SPF/DKIM caveat. Everything else — `DATOMIC_URL`, `DATOMIC_PASSWORD`, `SIGNATURE`,
`CSP_POLICY`, `PORT` — resolves identically, because blank-is-absent only differs when a value
is actually blank.

Do **not** add `ALLOW_UNVERIFIED_REGISTRATION` to a public instance's `.env`. With SMTP
configured it is inert, and its presence earns the loaded-gun warning at boot.

## A second finding from the same sweep

`privacy_content.clj` rendered `(env :email-access-key)` as the public contact address on the
privacy page, four times — and `email.clj` uses that same variable as the **SMTP username**,
paired with `EMAIL_SECRET_KEY`. The privacy page was printing half a credential. Fixed to use
`branding/support-email` (`APP_SUPPORT_EMAIL`), which already existed for exactly this.

Unrelated to blankness; found only because converting every call site meant *reading* every
call site. That is an argument for sweeps over pointwise fixes.

## Corrections to other docs

- `AGENTS.md` says twice (lines 51, 58) that *"Clojure code uses `environ` or `dotenv` to read
  `.env`/ENV"*. **There is no `dotenv` dependency and never has been** on `agents/develop`,
  `integration` or `develop`. Clojure never reads `.env`; `scripts/common.sh` sources it with
  `set -a` and environ sees the results as ordinary environment variables. That line is
  probably why more than one person remembers a dotenv approach that was "tried and
  abandoned" — nothing was.
- `env-and-auth.md`'s closing section describes CSP "report-only vs enforcing". **There is no
  Report-Only mode and never has been**; nothing emits `Content-Security-Policy-Report-Only`,
  and `csp_test.clj` asserts its absence. `DEV_MODE=true` sends no CSP header at all.

## Related

- [`env-and-auth.md`](env-and-auth.md) — the three loading paths, `.lein-env` vs `.env`, profile overwrite chain
- [`locale-safety.md`](locale-safety.md) — the sibling class this branch was opened for
- [`verification-discipline.md`](verification-discipline.md) — why every guard here was tested by sabotage
