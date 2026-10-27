# feature/guarded-homebrew

**Purpose:** protect the app from malformed homebrew by default: one shape table checks every
write, read and load; what cannot be read is set aside for repair, never silently dropped; a bug
costs the item it is in, not the page. **Base:** `integration` at `d51938c1`. **PR into:**
`integration` (not opened). **Design and progress:** `docs/HOMEBREW_GUARD.md` on the branch
(move it to `docs/kb/` here before the branch merges).

## State (updated 2026-10-27, after the integration merge)

- Done (design doc, "Order of work"): steps 1-6, the consolidation, and the merge of `integration`
  (#69's spreads accepted by the feats row: `[:or [:set :keyword] [:list [:list :any]]]`). The shape table
  (`homebrew_shapes.cljc`) runs in the loader, in a global interceptor on every event's write, and
  on every read (`homebrew-check/checked`); the old `e5/mend-*` is folded into it; set-aside
  homebrew goes to the quarantine panel; event errors show a notice; render guards on builder
  sections, My Content lists and rows, monster rows; the share banner names unusable entries.
- Remote head `06c054f1`: `integration` merged in (with #69), level with it. AI trailers stripped
  from the branch's commits (force-pushed 2026-10-27).
- Next, in order:
  1. **Step 7, enforcement:** the junk audit in CI at zero, the AGENTS.md rules (owner's yes),
     the documents corrected, the `testing/develop` scenarios gone through.

## Owner decisions

- 2026-10-25: a field that cannot be read sets its whole entry aside, as written, for repair.
- 2026-10-25: boons, invocations and subraces are offered under their stored `:key`; picks of the
  name's key heal through `items-with-formers`.
- 2026-10-26: one repair path (mend folded into the table); a ~220-line group is not a namespace's
  worth (the `views/guards.cljs` move was reverted).

## Open questions for the owner

- Rewrite `integration`, `develop` and `agents/develop` to strip their old trailers? Recommended
  no: every later hash changes and every open branch must be rebuilt.
- Keep the set-aside logic in `homebrew_check.cljs` (moved from `events.cljs`, ~100 lines, into a
  namespace that already held this wiring), or move it back?
- Run the `pre-commit` hook's checks over the branch's earlier commits? They were made with no
  hooks armed.
- Prevention for trailers: a `pre-push` block on agents/develop (owner's yes for `.githooks`), a PR
  check on GitHub, and `agent-setup.sh` in the environment's setup script (owner's setting).

## Gotchas for the next agent

- **Open bug:** a homebrew subclass named `"x"` or with no `:name` is never offered in the
  character builder, by any way in (the junk audit's 6 remaining failures). Its class is there.
  Suspect: `class-option` merges subclasses into a set sorted by `:name`. A probe through
  `::classes5e/classes` found no subclass key for any name, so the probe, not the app, was wrong:
  find where the subclass options sit in the class option tree before trusting one.
- **Junk audit:** `lein fig:junk && NODE_PATH=/opt/node22/lib/node_modules node test/e2e/junk-pack-audit.js 4`
  (about 2.5 min; `JUNK_UNGUARDED=1` turns the shape check off, about 7 min). A guard catching
  something counts as a failure marked `contained:`.
- **cljs suite** takes about 32 s; `test/e2e/cljs-harness.js` waits for the totals, not page load.
- A homebrew field holding `nil` reads as absent (the class builder stores `:spell-list-kw nil`).
- A fresh container has no lein: install it as SETUP.md says (`~/bin/lein`); the first `lein deps`
  can drop on the proxy, retry it.
- Run only the browser scripts that cover what changed, in a subagent: a serial run of all of
  them took 30 minutes.
