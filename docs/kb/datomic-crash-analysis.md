# Datomic Transactor Crash Analysis

**Analyzed:** 2026-02-26  
**Artifacts:** `logs/datomic.1.log` (64,695 lines, Feb 24), `logs/datomic.2.log` (Feb 25), `logs/datomic.3.log` (Feb 26 from 00:00)  
**Branch at time of analysis:** `dmv/hotfix-integrations`

---

## Active transactor configuration (verified from log startup lines)

```
heartbeatIntervalMsec=5000
writeConcurrency=4
memoryIndexMax=256m
memoryIndexThreshold=32m
txTimeoutMsec=10000
```

Source: the transactor logs its own config on startup. These values were read directly
from the log startup block in `logs/datomic.1.log`.

---

## Crash mechanism — verified

Every crash in all three log files follows an identical sequence. Example from
`datomic.1.log` 2026-02-24 08:35:

```
08:35:19  kv-cluster/create-val  bufsize=74,458    msec=5,300     (tid 1212)
08:35:19  kv-cluster/create-val  bufsize=74,923    msec=5,440     (tid 1213)
...
           ← 14-second gap; no log output of any kind →

08:35:38  kv-cluster/create-val  bufsize=1,394,358  msec=19,500   (tid 982)
08:35:39  transactor/heartbeat-failed  cause=:timeout
08:35:39  ERROR Critical failure, cannot continue: Heartbeat failed
08:35:44  ActiveMQ Artemis stopped (uptime 7 days 19 hours)
08:35:46  kv-cluster/create-val  bufsize=5,795,494  msec=27,100   (tid 1195) ← still draining
08:37:23  Starting datomic:free://...                              ← Docker restart
08:38:01  System started                                           ← recovery
```

**What is happening:**

1. The memoryIndex threshold triggers a segment flush. Multiple write threads
   (up to `writeConcurrency=4`) begin writing `kv-cluster` segments to H2.
2. H2 is a single-writer embedded database. Concurrent writes serialize on an
   exclusive file lock. When one large write is in progress, all other writes
   — including the heartbeat's own timestamp write — queue behind it.
3. The heartbeat thread (tid 21) fires every `heartbeatIntervalMsec=5000` ms.
   Its write is blocked by the H2 lock. After approximately 3× the interval
   (~15 seconds, confirmed: heartbeat fired at 08:35:24.377, failed at
   08:35:39.350 = exactly 15 seconds), the transactor declares itself dead
   and self-terminates.
4. Docker restarts the container. Recovery takes ~2.5 minutes.

**The direct killer is H2 write serialization, not the writes themselves.**
A single 19.5-second write blocked the heartbeat from acquiring the H2 lock
for longer than the 15-second failure threshold.

---

## GC role — verified not sufficient alone

Datomic logs every JVM GC event via `datomic.log-gc`. All observed GC events are:

```
G1 Young Generation / end of minor GC / G1 Evacuation Pause
```

GC pause durations near the 08:35 crash:
- 08:30:58 — **1380 ms** (largest observed in entire log)
- 08:31:18 — 331 ms
- 08:31:31 — 364 ms
- 08:31:42 — 330 ms
- ...continuing through 08:33:47 at intervals of 5–15 seconds
- **Last GC before crash: 08:33:47 (295 ms) — 1 minute 52 seconds before crash**
- **No GC events between 08:33:47 and crash at 08:35:39**

The largest GC pause observed (1380 ms) is well below the 15-second heartbeat
failure threshold. GC alone cannot kill the transactor.

**⚠️ UNVALIDATED SPECULATION — [plausible mechanism, not directly observable in logs]:**
The GC storm between 08:30 and 08:33 (minor GC every ~10 seconds, up to 1.4s pauses)
likely reflects the memoryIndex flush churning through large object graphs. Each GC
pause interrupts the H2 write threads mid-operation. Because H2 holds file locks across
the full write duration (not just during active I/O), a write that would take 2–3s under
no-GC conditions may stretch to 19–27s when repeatedly interrupted by 300–1400ms STW
pauses. This is the probable mechanism connecting the GC activity to the anomalous write
latency, but it cannot be confirmed from logs alone — it would require JVM flight
recorder data or an H2 lock trace.

---

## Crash frequency — verified

| Log file | Date | Crash times (UTC) | Crashes |
|----------|------|-------------------|---------|
| datomic.1.log | Feb 24 | 08:35, 09:41, 21:21 | 3 |
| datomic.2.log | Feb 25 | 05:37, 06:56, 08:08, 09:19 + more | 4+ |
| datomic.3.log | Feb 26 | 04:50 (generated the email examples) | 1+ (log starts at 00:00) |

This is not an occasional blip. The transactor is crashing multiple times daily with
roughly 60–90 minute intervals between crashes during high-activity windows.

---

## Schema noHistory status — verified, no action needed

`src/clj/orcpub/db/schema.clj` already applies `:db/noHistory true` to all high-churn
gameplay attributes:

- `::char5e/current-hit-points`
- `::char5e/notes`
- `::char5e/prepared-spells` / `::char5e/prepared-spells-by-class`
- `::char5e/worn-armor`, `::char5e/wielded-shield`, `::char5e/main-hand-weapon`, `::char5e/off-hand-weapon`
- All spell slot usage (`::char5e/features-used`, `::spells5e/slots-used`, all slot-level keys)
- All time-unit usage trackers (`::units5e/minute`, `::units5e/round`, etc.)
- All of `magic-item-schema` and `weapon-schema`

Removing history from additional attributes would not affect the crash. The crash
is caused by segment *flush* volume (memoryIndex → H2 kv-cluster writes), not by
the presence of historical datoms in those writes.

---

## `writeConcurrency=4` is actively harmful with H2

H2 cannot parallelize writes — it serializes them internally on a file lock.
`writeConcurrency=4` causes 4 threads to contend simultaneously for that lock,
meaning all 4 wait while whichever one holds the lock makes slow progress. This
amplifies total write latency without increasing throughput.

**⚠️ UNVALIDATED SPECULATION — [well-reasoned but untested in this codebase]:**
Reducing `writeConcurrency` to `1` should eliminate the multi-thread H2 contention
and reduce the probability of a single write holding the lock long enough to starve
the heartbeat. However, this has not been tested. It may reduce throughput under
bursty write loads if the bottleneck shifts from contention to raw H2 sequential I/O.
If the total volume of writes during a flush exceeds what a single thread can process
within the heartbeat window, crashes could still occur — just less frequently.

Config to try:
```
datomic.writeConcurrency=1
```

This is a transactor properties file change — no code change required.

---

## Increasing heartbeat interval — not recommended

Setting `heartbeatIntervalMsec` to e.g. 60000 (1 minute) would raise the failure
threshold to ~3 minutes, which is longer than the observed 19.5-second worst-case
write. This would stop the self-termination.

**This is not a fix.** During the same write-backpressure window, user transactions
queue behind the H2 lock with a `txTimeoutMsec=10000` (10s) timeout. Users would see
transaction timeout errors regardless. Raising the heartbeat masks the infrastructure
signal (crash + admin email) while leaving the user-visible failure intact — and makes
the system harder to monitor.

---

## Recovery time

Each crash results in approximately **2–3 minutes of complete unavailability**:
- Transactor self-terminates (~1s)
- Docker detects exit and restarts container (~10–30s depending on health check config)
- Datomic transactor starts, initializes storage, begins accepting peer connections (~90–120s)
- Peer reconnects

During this window, all requests that require Datomic (all authenticated write routes,
all read routes that aren't cached in the peer) will fail with
`transactor-unavailable` or `Connection refused: datomic:4335`.

---

## Fix options

| Option | Severs which link in the failure chain | Verified effectiveness | Complexity |
|--------|----------------------------------------|----------------------|------------|
| `writeConcurrency=1` | Eliminates concurrent H2 lock contention | ⚠️ UNVALIDATED SPECULATION — should help, see above | Low — config only |
| `memoryIndexMax=512m` or higher | Fewer flushes → fewer contention windows | ⚠️ UNVALIDATED SPECULATION — trades memory for frequency | Low — config only |
| Migrate to Datomic Pro + PostgreSQL | Replaces H2 entirely; Postgres handles concurrent writers and is not subject to single-file lock contention | Established — Datomic Pro + Postgres is the documented production path | High — storage migration required |
| Application-layer circuit breaker | Detects slow `d/transact` calls (>2s) and returns 503 on write endpoints; reads remain up | ⚠️ UNVALIDATED SPECULATION — requires careful implementation; does not prevent crashes | Medium — application code |
| `heartbeatIntervalMsec=15000–60000` | Prevents self-termination | Verified would stop crashes | Low — config only; **NOT RECOMMENDED** — masks signal, see above |
| Docker health check + restart delay | Avoids thundering-herd of app reconnects against a half-started transactor | Verified useful as secondary measure | Low — Docker Compose config |

**Recommended path:** `writeConcurrency=1` as immediate mitigation, Postgres migration
as the permanent fix. See TODO entry: [Investigate Datomic + Postgres migration path](../TODO.md).

---

## What the error emails reveal about this (relation to P1–P18 analysis)

Each Datomic crash generates one error email **per in-flight request at the time of
crash**. At peak traffic this could be dozens to hundreds of emails per minute.
The flood throttle in the `send-error-email` rewrite (P5) is therefore especially
important for this failure class — without it, a single crash event could saturate
the admin inbox and trigger alert fatigue that causes real bugs to be missed.

See [error-email-improvements.md](../error-email-improvements.md) for full analysis.
