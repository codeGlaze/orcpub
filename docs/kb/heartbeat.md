# The heartbeat: when the server was running

Built on `integration-local`, 2026-09-14, first inside share link pruning (`share-links.md`), then moved
into its own namespace so anything else that measures time can use it.

## What it records

`orcpub.heartbeat` beats hourly, starting a minute after the server starts. Each beat writes the time on
the entity `:orcpub.heartbeat/clock` (`:orcpub.heartbeat/beat`, no history kept). When the previous beat
is more than 70 minutes back, the server was off or its clock jumped ahead, and that stretch is stored as
an outage (`:orcpub.outage/from`, `:orcpub.outage/to`). A clock set back records nothing.

It costs one small write an hour, plus one record per outage.

## Using it

- `(heartbeat/outages db)` gives every outage as `[from to]`; `(heartbeat/last-beat db)` gives the last
  beat.
- `(heartbeat/running-ms outages from to)` is the time between two dates that the server was running.
  Anything that expires by time should measure with it, so time the server was off never counts against
  a user. A server switched off for six months then deletes nothing when it comes back.
- A scheduled job goes in the map `system.clj` passes to `new-heartbeat`: a name for the log, then
  `(fn [conn])`. Jobs run after the beat. When the beat cannot be written they wait for the next tick,
  since an unrecorded outage would count against whatever they measure, and one failing job does not stop
  the others. Share link pruning (`routes.share/prune-job`) is the first.

## Limits

- It is a record of the past. It cannot warn anyone while the server is down; that needs an outside
  monitor on `/health`, which answers OK without touching the database.
- A restart shorter than about an hour is not recorded, so each one can count up to about an hour as
  running.
- Servers sharing one database share the clock, so an outage is recorded only when all of them were off.

## Checks

`test/clj/orcpub/heartbeat_test.clj`: beats about an hour apart are no outage, a three-day gap is one, a
clock set back is not; running time leaves outages out; a tick beats before its jobs, a failing job stops
no other, and no job runs when the beat fails.
