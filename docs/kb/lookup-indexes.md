# Lookups by value read every record: measured (2026-10-22)

**What this is for:** why sign-in, the account lookup on every page and the list pages slow down
and churn memory as the site grows, with numbers, and what fixes it. Prompted by a live
`OutOfMemoryError: Java heap space` inside `lookup-user-by-email` during `GET /user`.

## The cause

Datomic (the On-Prem / Pro edition the site runs) keeps a value-to-entity index (AVET) only for
attributes marked `:db/index true` or `:db/unique`. The 2017 schema marks none of the fields the
site looks up by value, so a query like `[?e :orcpub.user/username ?username]` reads every
username to find one. Attributes added since 2026 (`share/owner`, `user/artist`, `outbox/status`)
are indexed. Adding `:db/index true` to an existing attribute is a supported online change:
Datomic builds the index in the background, rewrites no data.

Unindexed and looked up by value (`feature/email-and-alerts`, 2026-10-22):
`:orcpub.user/username` (11 queries, incl. `find-user-by-username-or-email` on every page load),
`:orcpub.user/email` (8), `:orcpub.entity.strict/owner` (character lists),
`:orcpub.dnd.e5.party/owner`, `:orcpub.dnd.e5.folder/owner`, `:orcpub.dnd.e5.magic-items/owner`,
`:orcpub.user/verification-key`, `:orcpub.user/password-reset-key`.

**Email has a second problem an index does not fix:** `email-query` and `lookup-user-by-email`
apply `clojure.string/lower-case` to every stored address so legacy mixed-case emails match. That
reads and allocates for every user whatever the indexes.

## Measured

`dev/measure_lookups.clj` (`JVM_OPTS=-Xmx2500m lein with-profile +tools run -m measure-lookups
10000 100000`): an in-memory database with N users (one in ten mixed-case email), 3 characters,
a party, a folder and an item each; the real queries from `routes.clj`; median of 15 runs after
5 warm-up; allocation summed over all threads (Datomic queries use its own threads too).

| Lookup | 10k users | 100k users | 100k, indexed |
|---|---|---|---|
| page load account lookup (`find-user-by-username-or-email`) | 7.6 ms | 48.9 ms | 0.8 ms |
| sign in by username | 3.7 ms | 25.0 ms | 0.4 ms |
| **by email (lower-case scan)** | 13.0 ms, **12 MB** | 151 ms, **120 MB** | 135 ms, 120 MB |
| character list (by owner) | 13.1 ms | 127.9 ms | 0.6 ms |
| magic item list (by owner) | 2.6 ms | 23.3 ms | 0.4 ms |
| email exact match (what a fix runs) | 2.5 ms | 20.9 ms | 0.35 ms |

- Every unindexed lookup grows with the user count; indexed ones stay flat below 1 ms.
- **The email lookup allocates about 1.2 KB per user per call** (~120 MB at 100k). Ten sign-ins or
  resets at once is over a gigabyte of short-lived garbage, which makes it a strong candidate for
  the live heap error. Not proven to be the cause: an OOM is raised wherever the JVM is when the
  heap fills; a heap dump on OOM (stage 4 of [plan-email-and-alerts.md](plan-email-and-alerts.md))
  would show what held the memory.
- The in-memory database flatters the scans: on the live storage a scan also pulls every segment
  of the attribute into the object cache.

## After the fix (measured 2026-10-26, `fix/lookup-indexes`)

`dev/measure_lookups.clj` on the branch's schema (indexes and `:orcpub.user/email-key`; the email
lookup is given an upper-case address, so it proves the match ignores case):

| Lookup | 10k users | 100k users |
|---|---|---|
| page load account lookup (`find-user-by-username-or-email`) | 1.8 ms | 3.0 ms |
| sign in by username | 0.8 ms | 1.1 ms |
| **by email (`user-for-email`, the key)** | 0.8 ms, 0.12 MB | **1.1 ms, 0.12 MB** (was 151 ms, 120 MB) |
| character list (by owner) | 0.5 ms | 0.9 ms |
| magic item list (by owner) | 0.6 ms | 0.6 ms |

The account lookup's `or-join` over username and email key costs a little more than one indexed
clause, still flat. And it now runs once per page load: `GET /user` is no longer sent when a view
mounts (`scripts/e2e/user-fetched-once.js`).

## Why `GET /user` runs so often (found 2026-10-22)

`subs.cljs` registers `:user` with `reg-api-sub` (`api_subs.cljs`), a `reg-sub-raw` that issues the
GET whenever a new subscription instance is created, i.e. each time a component that subscribes to
`:user` (or `:following-users`, built on it) mounts after the last one unmounted. Navigation does
that on most pages. **The response is discarded** (`:db-key :user` holds only the locally built
`:following` list; the comment says "The response is not stored"), so the call acts as a token
check that costs a full `find-user-by-username-or-email` scan each time. This breaks the
"nothing loads until needed; then once, and cached" rule in AGENTS.md. Fix: fetch once per session
(or per login) and keep it; a 401 on any other call already logs out.

## Why one email lookup allocates ~1.2 KB per user

`[?e :orcpub.user/email ?email-2] [(clojure.string/lower-case ?email-2) ?email]` cannot be answered
from an index: the query engine first materialises every (entity, email) pair as a relation, then
calls `lower-case` on each (a new String per user), binds the results into another relation and
only then filters to the one match. All of it is garbage a few milliseconds later: churn, not a
leak. A burst of sign-ins or resets with the heap near its cap is when that tips into an OOM.

## Normalised email: separate field (decided direction, 2026-10-22)

Common practice: keep the address as the person typed it (for display and sending) and look up and
enforce uniqueness on a normalised copy (trimmed, lowercased). In SQL that is a functional index
on `lower(email)` or a `citext` column; Datomic has no functional indexes, so a separate indexed
attribute is the Datomic form. Rewriting stored addresses in place (e.g. Devise's downcase on save)
also works but loses the original and is a live data rewrite. The separate field can later carry
`:db/unique` (email has no uniqueness constraint today; see `docs/email-system.md` known issues),
once existing duplicates are found and resolved.

## Fix, done on `fix/lookup-indexes`: [plan-lookup-indexes.md](plan-lookup-indexes.md) (owner approved the approach 2026-10-22)

1. `:db/index true` on the attributes above: a schema alteration added to `schema.clj`, applied on
   start. No data change.
2. Email needs one consistent case so it can be matched exactly: lowercase the stored addresses
   once (a live data change), or keep a lowercase copy in a new indexed attribute. Either way the
   queries then use `[?e :orcpub.user/email ?email]` with the lowercased input.
