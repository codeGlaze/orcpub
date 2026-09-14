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

## A loader's 401 logs out

Fixed on `integration-local` in `6f1e6309`, 2026-09-13. Before it, a 401 from a server loader left
the user logged in. The `:user` loader dispatched `:set-user-data` with the token removed from the
map, but `:set-user-data` merges into `db :user-data`, and a merge cannot remove a key. The character
and party loaders routed to login and left the token alone. The header kept showing the user and
every later request failed until they logged in again or reloaded, since `:verify-user-session`
clears a rejected token properly at startup. A tab that outlives the 24-hour token hits it.

`api-subs/rejected-token!` now runs for every loader `reg-api-sub` registers: `:clear-login`, then
the loader's own `:on-401`, or a route to login when it has none. It does nothing when the token
changed while the request was out, so a late 401 cannot log out a login that replaced it.

Log out with `:clear-login`. Anything that merges into `:user-data` cannot remove the token.

Checked with a browser probe on a seeded server: log in as kaylee, swap in a token the server
rejects, let `:user` and `::char5e/characters` fetch. Before the fix the rejected token stayed in
app-db and localStorage with kaylee still shown; after it, both log out on a single 401.

**Not covered: requests made through the `:http` effect** (saves, deletes, follows). Its 401 default
routes to login without logging out, and it cannot do better yet: the server also answers 401 when
someone saves or deletes a character or item they do not own, so a 401 there does not always mean
the token was rejected. Separating the two needs the server to send 403 for "not yours", or an error
code in the body.

## Related

- [reframe-subscription-patterns.md](reframe-subscription-patterns.md): the guard placement rule and
  `reg-api-sub`.
- [env-and-auth.md](env-and-auth.md): the server side, the signing secret and env loading.
- [filtered-list-staleness.md](filtered-list-staleness.md): the #669 defect this audit came out of.
- [../TODO.md](../TODO.md), "Items owned by another account": the item-by-id route and item sharing.
