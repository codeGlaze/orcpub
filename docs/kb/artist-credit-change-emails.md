# Artist credit change emails: batched, never postponed

*Verified 2026-10-14 on `claude/artist-profile-pages` at `dbf63877`.*

An artist's credit edit (name, links, page switches) is emailed to the artist and to the admin, so
a change the artist did not make is noticed. Each save sending its own email let a burst of edits
flood the admin; this is how that was settled.

## The rule (owner's decision)

- The **first change after a quiet spell is emailed at once** and opens a **10-minute window**.
- Changes inside the window are **collected** and sent **together, once, when the window ends**:
  one email from the credit before the first of them to the credit now (nothing, if they undid
  themselves).
- The window **starts at that first change and never extends**, so editing more cannot postpone
  an email. A change after the window closes is a first change again.
- Saves are capped at **60 an hour** per account, against abuse rather than as the batching tool.

## Rejected first: one change a minute

The first version refused a second change within a minute (`429`). It stopped the flood but got
in the way of an artist fixing a typo, and still sent one email per change. Replaced by the rule
above in `711b2729`.

## How it is built

| Piece | Where |
| --- | --- |
| window start, the instant of the last immediate email | `:orcpub.user/artist-credit-window`, `db/schema.clj:202` |
| the credit before the first unsent change, EDN; `""` when none waits | `:orcpub.user/artist-credit-pending`, `db/schema.clj:207` |
| on save: email now and open a window, or mark pending | `artist-credit-notices/saved!`, `artist_credit_notices.clj:26` |
| send due batches | `flush-due!`, `:52`, run every minute by its own component (`:88`), not the hourly heartbeat |
| one send per batch across containers | `claim!`, `:45`: `:db/cas` pending -> `""`; only the commit that wins sends |
| the hourly cap | `artist-credit/save-cap` and `wait-secs`, `artist_credit.clj:159`, `:172` |

Constraints a reader would get wrong:

- **State is in the database, not memory,** so a batch survives a restart and several containers
  share it. `""` rather than a retraction marks "nothing pending", because `:db/cas` needs a new
  value and a retraction of an absent datom does not fail (it cannot arbitrate between senders).
- **The cap is per container** (a bounded cache keyed by `[connection account]`). Keyed by account
  id alone, every in-memory test database reused the same ids and one test file's saves counted
  against the next; the connection in the key fixes that and is the same thing in production.
- **Clock**: everything reads `orcpub.heartbeat/now`, so tests set the time with `with-redefs`.

Tests (`artist_credit_test.clj`): a single change emails at once; a burst gives one email now and
one batch at minute 10, sent once; edits at 0/4/8 cannot hold the batch past 10, and a change at 12
is emailed at once; three concurrent flushes send once; the 61st save in an hour is refused.
