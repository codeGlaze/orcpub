# Plan: indexed lookups, a lowercase email field, and GET /user once per session

**Status:** in progress on `fix/lookup-indexes` (from `integration`, approved by the owner). Steps 2
(indexes) and 3 (`email-key`) done 2026-10-26. Owner decisions: indexes yes; a separate lowercase email field yes;
`GET /user` once per session yes. Why: [lookup-indexes.md](lookup-indexes.md) (measured: at 100k
users the email lookup costs 151 ms and 120 MB per call; indexed lookups stay under 1 ms).

Live context: the live site runs roughly upstream `develop` on **Datomic Pro with Postgres storage**.
Postgres holds Datomic's segments only; queries run in the app JVM, so SQL indexes do not apply.
The owner reports Postgres storage was faster than dev storage; a scan still fetches every segment
of the attribute and fills the peer's object cache.

## 1. Indexes (schema only, no data change)

- In `src/clj/orcpub/db/schema.clj`, add `:db/index true` to: `:orcpub.user/username`,
  `:orcpub.user/email`, `:orcpub.user/verification-key`, `:orcpub.user/password-reset-key`,
  `:orcpub.entity.strict/owner`, `:orcpub.dnd.e5.party/owner`, `:orcpub.dnd.e5.folder/owner`,
  `:orcpub.dnd.e5.magic-items/owner`. Re-audit first with the query grep in lookup-indexes.md:
  anything else queried by value goes on the list.
- Schema is transacted on every start (`datomic.clj`), so adding the flag to the existing maps is
  the alteration. **Check first** that Datomic Pro 1.0.7482 accepts `:db/index true` in an
  `{:db/ident …}` map for an existing attribute without `:db.alter/_attribute` (it does on recent
  Pro; prove it in a test against a database created with the old schema, then started with the
  new one).
- **Done 2026-10-26.** Confirmed: Datomic Pro accepts `:db/index true` on an existing attribute when
  the ident map is transacted again, no `:db.alter/_attribute` needed
  (`test/clj/orcpub/db/lookup_indexes_test.clj` upgrades an old-schema database in place). The
  re-audit added `:orcpub.party-share/character` (looked up by value when a share is pruned);
  `folder/character-ids` is a ref, which Datomic already serves from VAET.
- The transactor builds the AVET index in its next indexing job. Large databases: index build time
  and transactor memory; note it for the admin in the changelog / DOCKER.md upgrade notes.

## 2. Lowercase email field

- **Done 2026-10-26** in `orcpub.user-email` (`email-key`, `email-attrs`, `backfill!`, the
  `:email-keys` component). The key is lowercased in `Locale/ROOT`, and every lookup normalises its
  input with the same function: `str/lower-case` on a Turkish JVM would store `i` and look up `ı`.
  `manage-user.clj` inlines the normaliser so it never depends on app code. During the first
  backfill after the upgrade (background, seconds to a minute at 100k accounts) older accounts can
  sign in by username but not by email. Tests: `user_email_test.clj`.

- New attribute `:orcpub.user/email-key`: string, `:db/index true`, the trimmed, lowercased address.
  Not `:db/unique` yet: live data may hold duplicate emails (`docs/email-system.md`, known issues).
- **Every writer sets it** (one helper, e.g. `routes/email-attrs` returning both attributes):
  - `routes.clj` register (~line 639) and the email-change swap in `verify` (~709; also retract the
    old key);
  - `artist_accounts.clj` (~187, ~197);
  - `dev/user.clj` (~214, add-test-user);
  - **`docker/scripts/manage-user.clj`** (line ~69; the script `docker-user.sh` copies into the
    container; it has its own queries at ~46 and ~59 that must use the new field too);
  - `dev/e2e_boot.clj` seeding, and test fixtures that create users directly (many; grep
    `:orcpub.user/email ` in `test/`).
- **Backfill on start:** a component (or a step after schema) that finds users with
  `:orcpub.user/email` and no `:orcpub.user/email-key` and transacts the key in batches of ~500.
  Idempotent; logs a count; never blocks the site starting (same pattern as artist accounts).
  Runs every start, so users created by old code (e.g. an old manage-user.clj) are caught.
- **Lookups switch to the exact match** on `:orcpub.user/email-key` with the lowercased input:
  `email-query`, `lookup-user-by-email`, `find-user-by-username-or-email` (its `or` clause),
  `user-for-email`, the duplicate checks in register / email change, `pending-email` checks.
  No fallback scan: a fallback would let anyone trigger the old scan with an unknown address.
- Optional later: duplicates report, then `:db/unique` on `email-key`.

## 3. GET /user once per session

- `src/cljs/orcpub/dnd/e5/subs.cljs` `:user` uses `reg-api-sub` (`api_subs.cljs`): a
  `reg-sub-raw` that fetches on every new subscription instance; the response is discarded.
- Change: fetch when the app starts with a token and after login; not on each mount. Keep the 401
  handling: any authenticated call that answers 401 still logs out (`rejected-token!`), so a
  password reset that withdraws sessions still signs other browsers out on their next call.
- Before changing: list every subscriber of `:user` and `:following-users` (subs.cljs, views.cljs,
  events.cljs; 9 references on 2026-10-22) and confirm none needs the refetch itself.
- Prove: a browser test counting `GET /user` requests across a navigation of five pages (expect 1,
  today N); fits `test/page-load-budget`'s request allowances if that has landed.

## Tests (each seen failing first)

- Old-schema database upgraded in place: start with the new schema, indexes present
  (`(d/attribute db :orcpub.user/username)` reports `:indexed true`), data unchanged.
- Mixed-case legacy account (`User1@Example.Test`, no key) signs in with any casing after the
  backfill; reset and sign-up duplicate checks still catch it.
- Every writer sets the key: register, email change, artist account, manage-user.clj (run the
  script's functions in a test), dev add-test-user.
- No query in `src/clj` filters `:orcpub.user/email` through `lower-case` any more (a grep test,
  like `settings_lists_test`).
- `dev/measure_lookups.clj` re-run: email lookup flat under 1 ms; record the numbers in
  lookup-indexes.md.
- Browser: auth-flows, the soak walkthrough, and the GET /user count.

## Order of work

1. Branch from integration (owner approves the name).
2. Indexes + the old-schema upgrade test. Commit.
3. `email-key`: writers, backfill, lookups, tests. Commit.
4. GET /user once per session + browser count. Commit.
5. Measure, update lookup-indexes.md and `docs/email-system.md`, changelog (Fixed: sign-in and lists
   slowing down and using memory as the site grows), PR into integration.
