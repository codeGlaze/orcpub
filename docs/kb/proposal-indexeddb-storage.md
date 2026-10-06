# Proposal: store homebrew (and SRD data) in IndexedDB

**Status: idea, parked, not scheduled.** Written down 2026-10-06 so it can be picked up later
without rediscovering it.

**Before anyone works on this:** the owner recalls drawbacks that were judged non-starters at some
point in the past. They are **not recorded anywhere found** in the KB, the TODO or commit history
as of this writing — the recorded position is the opposite (below). Recover those drawbacks and add
them to this page first. They may settle the question before any spike is needed.

## The idea

Store the homebrew library in IndexedDB, one record per source, instead of as one large
localStorage string, and load each source only when it is needed. The same per-source, on-demand
loader then serves SRD edition data files under the long-term pure-SPA goal
([srd-2024-roadmap.md](srd-2024-roadmap.md), "Long-term goal: a pure SPA").

## Why it is worth looking at

- **Capacity.** localStorage holds about **5.18 M characters**; the measured IndexedDB origin quota
  is about **916 MB** — roughly 180 times more. `db.cljs` already carries guards for quota
  failures that used to drop saved homebrew. This is the strongest reason, and on its own.
- **The SPA goal.** Per-key, asynchronous storage is exactly what on-demand SRD files need, so one
  loader serves both.
- **It removes the complexity that sank the alternative.** The localStorage chunking plan
  ([plan-chunked-library-storage.md](plan-chunked-library-storage.md)) grew a hand-built yielding
  scheme and a migration dance; IndexedDB is natively per-key and async, so neither is needed.
- **The KB's recorded position**, in that plan: IndexedDB is "the eventual right tier and the only
  answer to capacity", to be scheduled on capacity grounds regardless of the perf work.

## What it is not

**Not the fix for the builder freeze users report.** That freeze happens while *using* the
builder — switching race, subrace, class, subclass — and was traced to retained memory, blocking
class switches and a double build per change
([perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)). No storage read happens on a class
switch. A storage move speeds only the one-time library parse at builder open (~700–800 ms), which
that investigation ranked last.

## Known costs, already recorded

From `docs/TODO.md`:

- It is **a feature, not a fix**: the stored format splits per source, and every existing user's
  library needs a one-time conversion. Change the storage format **once**, straight to the target,
  rather than through an intermediate format.
- **A split library's save stops being all-or-nothing.** A partial write must be detectable on the
  next load — an index key written last — and never shown as a silently truncated library.
- **One giant single source still freezes** for as long as that one source takes to load. This
  fixes the common shape of a library, not that one.

## Risks a trial must cover

General risks of this kind of move; **none yet recorded as findings for this project**:

- **Startup becomes asynchronous.** Homebrew is read synchronously during startup today
  (`get-local-storage-item` in `db.cljs`, via the `::e5/plugins` coeffect on `:initialize-db`).
  The app would have to start, or draw, before the library arrives.
- **Migrating existing libraries without ever losing homebrew.** Copy, verify, and only then
  retire the old copy. The chunking plan withdrew a copy-then-delete migration; do not repeat it.
- **Eviction.** Browsers may clear IndexedDB under storage pressure unless persistent storage is
  granted (`navigator.storage.persist()`).
- **Safari and private browsing**, historically the weakest IndexedDB environments.
- **Cross-tab coordination.** The refactor fixed cross-tab localStorage bugs; IndexedDB has
  different semantics and those fixes do not carry over automatically.
- **Testing.** `db-test` runs against a real localStorage in headless Chromium; an IndexedDB
  version needs the same kind of real-storage test.

## How to trial it

A **throwaway spike**, decided on measurements, not reasoning — the perf investigation's own rule.
Measure capacity with a real large library, builder-open time, a full migration of a real library
with a verified result, behaviour with two tabs open, and Safari. Off the **refactor trunk**: it is
edition-neutral machinery, and the storage write path was reworked there.
