# Where login state lives in app-db

Checked against `integration-local` on 2026-09-13, after the #669 merge (`185d703e`). Symbols are
named instead of line numbers, because those move.

## The shape

```
db
├── :user-data       login state; user->local-store-interceptor saves it to localStorage
│   ├── :token       the JWT
│   ├── :user-data   the account record: :username :email :pending-email :send-updates? ...
│   └── :theme       survives logout
└── :user            only {:following (...)}, built locally
```

## Read the token with get-auth-token

`orcpub.dnd.e5.event-utils/get-auth-token` is the one place the path `[:user-data :token]` is
written. Use it for the token itself (an Authorization header, an `:http :auth-token`) and as the
logged-in check; `auth-headers` calls it. Having a token and being logged in are the same thing
today: `:login-success` installs the token and the account record in one update, and `:clear-login`
removes both.

A guard that reads the wrong key fails silently. `::mi5e/remote-item` guarded on
`(:token (:user db))`, a key that has never held a token, so its fetch never fired from the day it
was written (`45ef969`). The same move had a louder failure: `:report-character-problem`, added after
#669 was written, still called the old local `get-auth-token` after the merge and threw on click
until `476e948c`.

## Why :user-data is nested twice

The login response body is `{:user-data <account record> :token <jwt>}`
(`routes/create-login-response`), and `:login-success` merges the whole body into `db :user-data`.
So the account record sits at `[:user-data :user-data]`. It is read there by the `:username`,
`:email`, `:pending-email` and `:send-updates?` subs in subs.cljs, written there by two `assoc-in`s in
events.cljs, and read from `@app-db` directly in views.cljs.

Flattening it means changing the merge in `:login-success`, `:set-user-data` and `:clear-login`, every
reader above, and the shape saved in localStorage, with a migration for sessions already stored in the
old shape. Not done; nothing is broken by it, it only makes the paths confusing.

## Why db :user holds only the follow list

`:user` is registered with `reg-api-sub` and fetches `GET /user`, but it has no `:set-event`, so the
response is discarded. The only writers of `db :user` are `:follow-user` and `:unfollow-user`, which
build `{:following (...)}` from nil through `:set-user`; `:following-users` reads it. So the follow
list starts empty on every page load and holds only follows made in this session.

Two ways to fix it, neither done: store the `GET /user` response with a `:set-event`, or move
`:following` under the account record and drop `db :user`.

## The :user 401 handler cannot log anyone out

`user-sub-on-401-actions` dispatches `[:set-user-data (dissoc user-data :user-data :token)]`.
`:set-user-data` merges into `db :user-data`, and a merge cannot remove keys, so the token and the
account record stay. The tests in subs_test.cljs check that the payload has no `:token`, which is
true, not what db holds after the dispatch. It behaved the same before #669.

The damage is small. The only subscriber, `:following-users`, subscribes without `required?`, so a
401 never routes to login. At startup `:verify-user-session` clears a stale token with `:clear-login`,
which does work. What remains: if `GET /user` returns 401 mid-session, the app keeps the token and
still shows the user as logged in, and later requests keep failing until they log in again.

The fix is to dispatch `:clear-login` (it removes both keys and keeps `:theme`), and to test db after
the dispatch rather than the payload.

## Related

- [reframe-subscription-patterns.md](reframe-subscription-patterns.md): the guard placement rule and
  `reg-api-sub`.
- [env-and-auth.md](env-and-auth.md): the server side, the signing secret and env loading.
- [filtered-list-staleness.md](filtered-list-staleness.md): the #669 defect this audit came out of.
- [../TODO.md](../TODO.md), "Items owned by another account": the item-by-id route and item sharing.
