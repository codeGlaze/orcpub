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

## Fix, not yet done (owner decides after these numbers)

1. `:db/index true` on the attributes above: a schema alteration added to `schema.clj`, applied on
   start. No data change.
2. Email needs one consistent case so it can be matched exactly: lowercase the stored addresses
   once (a live data change), or keep a lowercase copy in a new indexed attribute. Either way the
   queries then use `[?e :orcpub.user/email ?email]` with the lowercased input.
