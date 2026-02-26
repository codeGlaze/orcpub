# Error Email Improvements — Analysis & Handoff

Live document. Updated as each email example is analyzed. When all examples are covered, convert the plan section into implementation tasks and commit.

**Branch:** `dmv/hotfix-integrations` (keep in sync with `breaking/`)  
**Files in scope:** `src/clj/orcpub/email.clj`, `src/clj/orcpub/routes.clj`, `docs/ERROR_HANDLING.md`, `docs/email-system.md`

---

## How error emails work today

1. Any unhandled exception in a Pedestal interceptor chain hits `service-error-handler` in `routes.clj:1378`.
2. That calls `email/send-error-email ctx ex` (`email.clj:238`).
3. `send-error-email` fires only when `EMAIL_ERRORS_TO` env var is set.
4. Subject is always the hard-coded string `"Exception"`.
5. Body is `pprint` of `(:request context)` + `pprint` of `(or (ex-data exception) exception)`.

---

## Known problems (from codebase review, before example analysis)

| # | Problem | Impact |
|---|---------|--------|
| P1 | Subject is always `"Exception"` | Inbox is untriadgeable; every email looks the same |
| P2 | Body uses `(or (ex-data exception) exception)` — for `ExceptionInfo` this prints the data map only, no stack trace | Root cause is invisible |
| P3 | For plain Java exceptions, pprinting the Java object is not a stack trace | Same — no frames |
| P4 | Full `(:request context)` is dumped — includes `Authorization` headers, session cookies, POST bodies | Security exposure + very noisy |
| P5 | No flood throttle — a bad code path firing in a loop sends unlimited emails | Admin inbox spam, alert fatigue |
| P6 | No cause-chain traversal — only the outermost exception is shown | Wrapped exceptions hide the real error |
| P7 | `:json-params` (parsed POST body) is included in request dump — login requests contain `:password` | **Critical: plaintext credentials in admin email** |
| P8 | `:db` and `:conn` fields (live Datomic objects) are in request context and get dumped — they pprint to internal DB identifiers, t-values, index-rev | Noise + exposes internal DB metadata |
| P9 | Pedestal wraps the real exception under an `:exception` key inside `ex-data` — current format buries the actual stack trace one level deep | Hard to read; actionable frames require digging into nested map |
| P10 | `:cookie` header is fully included — exposes CloudFlare clearance tokens, analytics IDs, consent UUIDs | User session data leaked to admin email |
| P11 | Java object refs (`:servlet-request`, `:servlet-response`, `:servlet`, `:url-for`, `:body` stream) are dumped as `#object[...]` strings | Pure noise, adds length with zero signal |

---

## Example emails

### Example 1 — Raw Jetty infrastructure stack

**Full body received:**

```
{"ServletHolder.java" 845]
  [org.eclipse.jetty.servlet.ServletHandler doHandle "ServletHandler.java" 583]
  [org.eclipse.jetty.server.handler.ScopedHandler handle "ScopedHandler.java" 143]
  [org.eclipse.jetty.server.handler.gzip.GzipHandler handle "GzipHandler.java" 399]
  [org.eclipse.jetty.server.handler.ContextHandler doHandle "ContextHandler.java" 1162]
  [org.eclipse.jetty.servlet.ServletHandler doScope "ServletHandler.java" 511]
  [org.eclipse.jetty.server.handler.ContextHandler doScope "ContextHandler.java" 1092]
  [org.eclipse.jetty.server.handler.ScopedHandler handle "ScopedHandler.java" 141]
  [org.eclipse.jetty.server.handler.HandlerWrapper handle "HandlerWrapper.java" 134]
  [org.eclipse.jetty.server.Server handle "Server.java" 518]
  [org.eclipse.jetty.server.HttpChannel handle "HttpChannel.java" 308]
  [org.eclipse.jetty.server.HttpConnection onFillable "HttpConnection.java" 244]
  [org.eclipse.jetty.io.AbstractConnection$ReadCallback succeeded "AbstractConnection.java" 273]
  [org.eclipse.jetty.io.FillInterest fillable "FillInterest.java" 95]
  [org.eclipse.jetty.io.SelectChannelEndPoint$2 run "SelectChannelEndPoint.java" 93]
  [org.eclipse.jetty.util.thread.strategy.ExecuteProduceConsume produceAndRun "ExecuteProduceConsume.java" 246]
  [org.eclipse.jetty.util.thread.strategy.ExecuteProduceConsume run "ExecuteProduceConsume.java" 156]
  [org.eclipse.jetty.util.thread.QueuedThreadPool runJob "QueuedThreadPool.java" 654]
  [org.eclipse.jetty.util.thread.QueuedThreadPool$3 run "QueuedThreadPool.java" 572]
  [java.lang.Thread run "Thread.java" 750]]}}
```

**What this tells us:**

- Every single frame is `org.eclipse.jetty.*` or `java.lang.Thread`. Zero `orcpub.*` frames are visible.
- The body snippet begins mid-structure (the `{` at the start is a truncated pprint of the exception map). The actual exception type and message are not shown — they were in the part of the pprint that was cut off or came before.
- This is `(ex-data exception)` being pprinted for an `ExceptionInfo` whose `:cause` is a Jetty-wrapped exception. The entire call graph is infrastructure scaffolding — completely unactionable.
- The email format (P3, P2) is the direct cause: because `(or (ex-data exception) exception)` selects the data map over the Java exception, the `.getStackTrace()` frames are never rendered at all. What appears here is a Clojure/EDN representation of the stack frames stored inside the exception data — not a filtered Java stack trace.

**What's needed to make this actionable:**

- Walk `(.getCause exception)` chain to find the deepest cause.
- Render `.getStackTrace()` as text, filtered to keep only `orcpub.*` frames (with infrastructure count appended).
- Label the exception type and message clearly at the top.

---

## Plan (current state)

### Send-error-email rewrite (`email.clj`)

1. **Subject:** `[AppName] ExceptionClassName: message-preview @ METHOD /path`  
   Example: `[DMV] NullPointerException: Cannot read field on nil @ GET /character/12345`

2. **Stack trace rendering:**
   - Walk `.getCause` chain (for Java exceptions) and `:via` chain (for `ExceptionInfo`); render each level.
   - For each, print: `ExceptionClass: message` then indented frames.
   - Filter frames: keep `orcpub\.` frames; suppress `org.eclipse.jetty`, `io.pedestal`, `clojure.lang`, `java.lang.Thread`, `sun.reflect`, `java.util.concurrent`. Append `... N frames suppressed`.
   - **Fallback (P15):** if zero `orcpub.*` frames exist after filtering, include the deepest non-suppressed frame from the innermost cause (e.g., `datomic.sql/connect`) rather than rendering nothing. Label it `← deepest non-infrastructure frame`.

3. **Request scrub:** extract only safe fields from `(:request context)`:
   - Keep: `:request-method`, `:uri`, `:query-string`, `:remote-addr`, `:route-name`, `:username` (if present — it's useful context and not a secret)
   - Drop from headers: `authorization`, `cookie`, `x-auth-token`, `x-session`
   - Drop entirely: `:json-params`, `:transit-params`, `:form-params`, `:body`, `:db`, `:conn`, `:servlet-request`, `:servlet-response`, `:servlet`, `:url-for`, `:async-supported?`, `:identity`

4. **`ex-data` block:** keep but exclude from Pedestal wrapper — extract `(-> ex-data :exception ex-data)` (the inner exception's data), not the outer Pedestal context map. The Pedestal context fields (`:execution-id`, `:stage`, `:interceptor`) go in a separate "Interceptor context" section.

5. **Flood throttle (P5, P16):**
   - Primary fingerprint: `exception-class + first-orcpub-frame`
   - Fallback fingerprint (no orcpub frames): `root-cause-class + first 60 chars of root-cause-message`
   - Skip send (log instead) if same fingerprint seen within 5 minutes.
   - Log: `"Suppressed duplicate error email (fingerprint: %s, last sent: %s ago)"`.

### Docs updates

- `docs/ERROR_HANDLING.md` — update "Error Notification" section with new email format.
- `docs/email-system.md` — update § 4 with throttle behaviour and scrubbed fields list.

---

### Example 2 — Transactor unavailable during login (full email)

**What arrived:** Two pprinted blocks — the full Pedestal request map, then the Pedestal interceptor error context map.

**What the exception is:**

```
clojure.lang.ExceptionInfo
:db.error/transactor-unavailable Transactor not available
  at datomic.peer$transactor_unavailable (peer.clj:185)
     datomic.peer.Connection transact (peer.clj:331)
     datomic.api$transact (api.clj:94)
  → orcpub.routes$create_login_response (routes.clj:205)
  → orcpub.routes$login_response (routes.clj:233)
  → orcpub.routes$login (routes.clj:237)
     io.pedestal.interceptor.chain ... [infrastructure]
     org.eclipse.jetty ... [infrastructure]
```

A user hit `POST /login` while the Datomic transactor was down. The three `orcpub.routes` frames tell us exactly what code path was live. This is completely actionable — with the current email format you have to hunt for it inside a deeply nested pprint.

**New problems surfaced by this example:**

- **P7 (critical):** The request dump includes `:json-params {:username "...", :password "<redacted>"}`. The `<redacted>` was done by whoever forwarded us the email — **the app itself sent the plaintext password**. Login, registration, and password-reset routes all POST credentials that would appear here.
- **P8:** `:db datomic.db.Db@82bd5625` and `:conn #object[datomic.peer.Connection ... {:db-id "orcpub-e1a68122-...", :next-t 181480137, ...}]` — both are live Java objects that Pedestal injects into the request map. They pprint to internal connection metadata including the full DB UUID and transaction counters.
- **P9:** The exception is nested as `(-> (ex-data interceptor-error) :exception)`. The current `pprint` of `ex-data` does output the full #error map (so the trace IS technically present), but it's buried under `:execution-id`, `:stage`, `:interceptor`, `:exception-type`, then `:exception`, then `:trace`. In Example 1 the email was likely truncated before those frames appeared.
- **P10:** The full `:cookie` string is in the headers dump — CloudFlare clearance token, Matomo `_pk_id`/`_pk_ses`, Google Analytics `_ga`, IAB consent UUIDs. These belong to individual users.
- **P11:** `:servlet-request`, `:servlet-response`, `:servlet`, `:url-for`, `:body`, `:async-supported?` are all Java/fn object refs — `#object[...]` strings with zero diagnostic value.

**Flood throttle is important for this class of error:** Datomic going down means every authenticated request will throw the same exception. Without throttling, a 60-second Datomic blip at peak traffic could send hundreds of identical emails before anyone can react.

**What a good email for this error would look like:**

```
Subject: [DMV] ExceptionInfo: :db.error/transactor-unavailable @ POST /login

Request: POST /login  (10.0.38.3 via 2605:a601:..., Firefox/147.0 Windows)

Exception chain:
  clojure.lang.ExceptionInfo: :db.error/transactor-unavailable Transactor not available
    data: {:db/error :db.error/transactor-unavailable}
    at orcpub.routes/create-login-response  (routes.clj:205)
       orcpub.routes/login-response          (routes.clj:233)
       orcpub.routes/login                   (routes.clj:237)
    ... 40 infrastructure frames suppressed (datomic.*, io.pedestal.*, org.eclipse.jetty.*)

Interceptor context:
  {:execution-id 1329, :stage :enter, :interceptor :orcpub.routes/login}
```

---

### Example 3 — H2 storage backend refused connection on `GET /dnd/5e/items`

**What the exception is — 3-level cause chain:**

```
java.util.concurrent.ExecutionException
  wraps → org.h2.jdbc.JdbcSQLException
    "Connection is broken: java.net.ConnectException: Connection refused: datomic:4335"
  wraps → java.net.ConnectException
    "Connection refused (Connection refused)"
    at java.net.PlainSocketImpl.socketConnect (native)
       org.h2.engine.SessionRemote.connectServer (SessionRemote.java:395)
       datomic.sql/connect (sql.clj:16)
       datomic.kv_sql.KVSql.get (kv_sql.clj:60)
       datomic.kv_cluster ... [retry logic]
       java.util.concurrent.FutureTask ... [thread pool]
```

**What this tells us:**

- Port 4335 is the H2 SQL storage backend that Datomic Free uses underneath the transactor. This is a different failure layer from Example 2 (port 4334, transactor unreachable). Both happened within ~1 minute of each other (04:50:57 and 04:51:48 on the same day) with identical DB identifiers (`orcpub-e1a68122-...`, `next-t 181480137`). **This was a single Datomic outage event that generated at least two emails in under a minute** — possibly many more across all concurrent requests.
- Zero `orcpub.*` frames anywhere in the trace. The failure is entirely inside Datomic's storage layer. With the current frame-filter plan (keep only `orcpub.*`), this email would render an empty stack trace. The plan needs a fallback.
- The request is authenticated — `:identity {:user "millennialdoomer", :exp 1772560158}` is the decoded JWT payload injected by the auth interceptor, and `:username "millennialdoomer"` is an additional field. Both are in the request map.

**New problems surfaced:**

- **P12 (critical):** `authorization: Token eyJhbG...` — a live signed JWT is in the headers dump. It's still valid until `:exp 1772560158`. Anyone with this email can impersonate that user until expiry.
- **P13:** `:identity` (decoded JWT) is in the request map — exposes username and token expiry.
- **P14:** `:username` field (added by auth interceptor) echoes the username again — minor, but confirms auth-enriched fields are present on all authenticated routes.
- **P15 (plan gap):** When zero `orcpub.*` frames exist, the filtered stack trace should fall back to showing the deepest non-boilerplate frame — e.g., `datomic.sql/connect (sql.clj:16)` — rather than rendering nothing. The cause message alone (`Connection refused: datomic:4335`) is enough to diagnose layer but a single anchor frame is more useful than silence.
- **P16 (throttle fingerprint design):** Fingerprinting by `exception-class + first-orcpub-frame` won't work when there are no orcpub frames. Fingerprint should fall back to `root-cause-class + root-cause-message-prefix (first 60 chars)`. For this incident both Example 2 and Example 3 would get separate fingerprints (different root cause classes/messages) — which is correct, they're different failure modes. But many copies of Example 3 across concurrent `/items` requests would correctly collapse to one.

**What a good email for this error would look like:**

```
Subject: [DMV] ExecutionException: Connection is broken "Connection refused: datomic:4335" @ GET /dnd/5e/items

Request: GET /dnd/5e/items  (10.0.38.3 via 2600:4040:..., Chrome/143 Opera GX Windows)
User: millennialdoomer

Exception chain:
  java.util.concurrent.ExecutionException
    wraps → org.h2.jdbc.JdbcSQLException: Connection is broken: "java.net.ConnectException: Connection refused: datomic:4335"
    wraps → java.net.ConnectException: Connection refused (Connection refused)
      at datomic.sql/connect  (sql.clj:16)   ← deepest non-infra frame
    ... 38 infrastructure frames suppressed (java.net.*, org.h2.*, org.apache.tomcat.*, datomic.kv_*)

Interceptor context:
  {:execution-id 1286, :stage :enter, :interceptor :orcpub.routes/item-list}
```

---

### Example 4 — `IllegalArgumentException: No matching clause` during character save

**Timestamp:** 04:50:25 — 32 seconds *before* Example 3, same DB connection (`next-t 181480137`, same `db-id`). Same Datomic outage window.

**What the exception is:**

```
java.lang.IllegalArgumentException: No matching clause: 
  at orcpub.routes/do-save-character (routes.clj:755)
     orcpub.routes/save-character    (routes.clj:761)
     io.pedestal ... [infrastructure]
     org.eclipse.jetty ... [infrastructure]
```

**What actually caused it — a secondary bug:**

`do-save-character` (currently at `routes.clj:932`) has this pattern:

```clojure
(catch clojure.lang.ExceptionInfo e
  (let [data (ex-data e)]
    (case (:error data)
      :character-problems {:status 400 :body (:problems data)}
      :not-user-character {:status 401 ...})))
      ; ← NO DEFAULT CLAUSE
```

The Datomic transactor-unavailable exception is an `ExceptionInfo` with `{:db/error :db.error/transactor-unavailable}` — note `:db/error`, not `:error`. So `(:error data)` returns `nil`. Clojure's `case` with no default clause throws `IllegalArgumentException: No matching clause: ` (empty, because `(str nil)` is `""`).

**The Datomic outage caused a secondary application bug to fire.** The actual Datomic error was swallowed by the wrong catch block and transformed into a misleading `IllegalArgumentException`. Without this email (and the effort to trace it), the admin would have seen an obscure character-save crash with no obvious connection to the infrastructure failure already reported by Examples 2 and 3.

**This is a separate fixable bug independent of the email improvements:** add a default `case` clause that re-throws:

```clojure
(case (:error data)
  :character-problems {:status 400 :body (:problems data)}
  :not-user-character {:status 401 :body "You do not own this character"}
  (throw e))   ; ← re-throw unrecognized ExceptionInfo
```

**New problems surfaced:**

- **P17:** `:transit-params` contains the full parsed request body. For character saves, that's the entire character entity — deeply nested, including DB entity IDs, all equipment, stats, ability scores, class/race selections. ~5KB of user character data per email. Not credentials, but user data that has no business in admin emails.
- **P18:** `:content-length 5388` and `:content-type "application/transit+json"` confirm the email includes the full parsed transit body even for large POST requests. No size cap.

**On "we still don't know WHY Datomic dropped":**

Correct, and the error emails can never tell you. `send-error-email` fires at the HTTP request layer — it knows a request failed because Datomic was unreachable, but the transactor itself logs its own shutdown reason separately in `logs/datomic.log`.

**However:** when the Datomic container drops, Docker restarts it and clears the container — `logs/datomic.log` is gone. **The error emails are the only post-mortem signal available.** This makes fixing them higher priority than previously assessed, and also means any Datomic-level diagnostics (restart count, OOM, OOD, etc.) must come from Docker/orchestration tooling, not the app. Out of scope for these improvements but worth tracking separately.

---

## Pending examples

*Paste additional error emails below as they come in. Each gets its own subsection with the same structure: raw body → what it tells us → any new problems identified → plan additions.*

---

## Datomic crash root cause (from log analysis)

### Logs examined
- `logs/datomic.1.log` — Feb 24 (64,695 lines)
- `logs/datomic.2.log` — Feb 25
- `logs/datomic.3.log` — Feb 26 from 00:00 (the crash at 04:50 that generated the emails is past the visible excerpt)

### Pattern — confirmed across all crashes

Every crash follows the same sequence immediately before `"Critical failure, cannot continue: Heartbeat failed"`:

```
08:35:24 kv-cluster/create-val  bufsize=74,458   msec=5,300
08:35:24 kv-cluster/create-val  bufsize=74,923   msec=5,440
08:35:24 kv-cluster/create-val  bufsize=1,394,358  msec=19,500   ← large write
08:35:39 transactor/heartbeat-failed  cause=:timeout
08:35:39 ERROR Critical failure, cannot continue: Heartbeat failed
08:35:44 ActiveMQ Artemis stopped (uptime 7 days 19 hours)
08:37:23 Starting datomic:free://...   ← Docker restart
08:38:01 System started               ← recovery ~2.5 min after crash
```

**Root cause:** H2 write latency under concurrent load spikes (5–27 seconds per `kv-cluster/create-val`). Datomic's heartbeat must write to storage every 5 seconds. When a storage write saturates H2's I/O, the heartbeat misses its deadline and the transactor self-terminates with `cause: :timeout`.

This is a known limitation of **Datomic Free + H2**: H2 is a single-file embedded database that cannot handle concurrent write contention. Under heavy character save traffic (large transit payloads — the 5 KB character from Example 4 becomes 400KB–5MB of kv-cluster segments), H2 latency spikes and the heartbeat dies.

### Frequency

| Log file | Date | Crashes observed |
|----------|------|-----------------|
| datomic.1.log | Feb 24 | 3 (08:35, 09:41, 21:21) |
| datomic.2.log | Feb 25 | 4+ (05:37, 06:56, 08:08, 09:19, plus more) |
| datomic.3.log | Feb 26 | ongoing (04:50 crash generated the example emails) |

**This is not an occasional blip — Datomic is crashing multiple times per day.**

### Why the container-restart wipes the log

Docker restarts the Datomic container on crash. The container's log volume is written to rotated files (`datomic.N.log`) but the *current* container's live log is lost on kill. The rotated files are what we have. The restart cycle (crash → down ~1 min → restart → ready ~2 min) means 2–3 minutes of unavailability per crash.

### Fix options (out of scope for this PR, but should be tracked)

| Option | What it does | Complexity |
|--------|-------------|------------|
| Migrate to Datomic Pro + PostgreSQL | Replaces H2 with a proper concurrent database; eliminates the write-contention crash mode entirely | High — requires Datomic Pro license and storage migration |
| Tune H2 write-ahead log / connection pool | May reduce contention but doesn't eliminate it | Medium — requires Datomic Free config experimentation |
| Add Docker restart delay + health check | Avoids thundering-herd of app requests hitting a half-started transactor | Low — Docker Compose `healthcheck` config |
| Add `DATOMIC_TRANSACTOR_OPTS` memory tuning | More JVM heap → larger write buffers → fewer contention spikes | Low |

The most important near-term mitigation is the **flood throttle** in the email fix: a Datomic outage currently generates one error email per in-flight request (potentially hundreds). With throttling, each distinct failure mode sends one email per 5 minutes.

- [x] Fix missing `case` default in `do-save-character` catch block (`routes.clj:~947`) — **done**
- [ ] Fix bracket error in `email.clj:~390` (one extra `)`) — **in progress by another agent**
- [ ] Verify `email.clj` compiles clean (`lein check` or start REPL)
- [ ] Update `docs/ERROR_HANDLING.md` — see spec below
- [ ] Update `docs/email-system.md` — see spec below
- [ ] Smoke-test — see spec below
- [ ] Commit both files + docs to `dmv/hotfix-integrations`
- [ ] Sync commit to `breaking/`

---

## Implementation guide (for the agent doing the work)

### Current state of `email.clj`

The rewrite of `send-error-email` has been applied but has a bracket imbalance at the closing of the function (~line 390). The intended final structure of the closing is:

```clojure
              nil))))))    ; catch / try / do / if / let / when
```

That is 6 closing parens after `nil`:
1. `)` closes `catch Exception`
2. `)` closes `try`
3. `)` closes `do`
4. `)` closes `if (throttled?)`
5. `)` closes `let [data-map ...]`
6. `)` closes `when (not-empty ...)`

Do **not** add a 7th. Run `lein check` after fixing to confirm zero errors before proceeding.

### `routes.clj` — already done

The `case` default clause (`(throw e)`) is in place at `routes.clj:~947`. No further changes needed there.

### `docs/ERROR_HANDLING.md` — what to change

Find the "Error Notification" subsection under "Email Operations" and replace the description with:

- Function: `email/send-error-email ctx ex`
- Triggered by: `service-error-handler` in `routes.clj` on any unhandled interceptor exception
- Subject format: `[AppName] ExceptionClassName: message-preview @ METHOD /path`
- Body sections: `=== Request ===` (scrubbed), `=== Exception ===` (cause chain + filtered frames), `=== Exception Data ===` (ex-data if present), `=== Interceptor Context ===` (Pedestal metadata)
- Scrubbed from request: all body params (`:json-params`, `:transit-params`, `:form-params`), credentials headers (`authorization`, `cookie`), Datomic objects (`:db`, `:conn`), Java object refs, `:identity`
- Throttle: one email per fingerprint per 5 minutes; duplicates logged as `INFO: Suppressed duplicate error email`

### `docs/email-system.md` — what to change

Update § 4 "Error Notification" (currently just "Called from exception handlers..."):

- Add the scrubbed fields list (same as above)
- Add the throttle window (5 min) and log message
- Add a note that `EMAIL_ERRORS_TO` must be set; if unset, function is a no-op
- Add a note that `logs/datomic.log` is cleared on container restart — error emails are the only post-mortem signal for Datomic outages

### Smoke test

Since `EMAIL_ERRORS_TO` won't be set in dev, test the helper functions directly in a REPL:

```clojure
(require '[orcpub.email :as email])

;; 1. Scrubbing — confirm no creds leak
(email/scrub-request {:uri "/login"
                      :request-method :post
                      :json-params {:username "foo" :password "secret"}
                      :headers {"authorization" "Token xyz"
                                "cookie" "cf_clearance=abc"
                                "user-agent" "Mozilla/5.0"}
                      :db (Object.)
                      :conn (Object.)})
;; Expected: {:uri "/login", :request-method :post, :headers {"user-agent" "Mozilla/5.0"}}
;; Must NOT contain :json-params, :db, :conn, authorization, cookie

;; 2. Subject line
(email/email-subject (Exception. "boom") {:request-method :get :uri "/test"})
;; Expected: "[DMV] Exception: boom @ GET /test"

;; 3. Throttle — second call suppressed
(let [ex (Exception. "test")]
  (email/record-sent! (email/throttle-fingerprint ex))
  (email/throttled? (email/throttle-fingerprint ex)))
;; Expected: true (a Long timestamp, truthy)
```

Note: `scrub-request`, `email-subject`, `throttle-fingerprint`, `throttled?`, and `record-sent!` are `defn-` (private). Either make them `defn` temporarily for testing, or test via `#'orcpub.email/scrub-request`.

### Commit message

```
fix: improve error notification emails and fix save-character exception masking

- send-error-email: scrub credentials/cookies/body params from request dump
- send-error-email: render filtered stack trace (orcpub.* frames only,
  fallback to deepest non-infra frame)
- send-error-email: walk full cause chain
- send-error-email: readable subject line with exception type + route
- send-error-email: 5-minute flood throttle per error fingerprint
- do-save-character: add case default clause to re-throw unrecognised
  ExceptionInfo (previously masked Datomic errors as IllegalArgumentException)

Fixes P1-P18 documented in docs/error-email-improvements.md
```
