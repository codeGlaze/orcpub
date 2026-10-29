# feature/guarded-homebrew

**Purpose:** protect the app from malformed homebrew by default: one shape table checks every
write, read and load; what cannot be read is set aside for repair, never silently dropped; a bug
costs the item it is in, not the page. **Base:** `integration` at `d51938c1`. **PR into:**
`integration` (not opened). **Design and progress:** [docs/kb/homebrew-guard.md](../../docs/kb/homebrew-guard.md) (moved here 2026-10-29).

## State (updated 2026-10-27, after the integration merge)

- Done (design doc, "Order of work"): steps 1-6, the consolidation, and the merge of `integration`
  (#69's spreads accepted by the feats row: `[:or [:set :keyword] [:list [:list :any]]]`). The shape table
  (`homebrew_shapes.cljc`) runs in the loader, in a global interceptor on every event's write, and
  on every read (`homebrew-check/checked`); the old `e5/mend-*` is folded into it; set-aside
  homebrew goes to the quarantine panel; event errors show a notice; render guards on builder
  sections, My Content lists and rows, monster rows; the share banner names unusable entries.
- Remote head `04e153bbd` (2026-10-29): subclass-key bug fixed (junk audit 0 failures), `integration` (with #74) merged, branch changelog written, PR next. Earlier `c878e9887` (2026-10-28): `integration` merged in again (after #65), checks green. Earlier `06c054f1`: merged with #69. AI trailers stripped
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

- None open. Answered 2026-10-28: no rewrite of the trunks (none was needed here: this branch has no
  trailers); the set-aside logic stays in `homebrew_check.cljs`; the hook replay over the earlier
  commits (done: missing docstrings and one history comment, fixed in a later commit); trailer
  prevention yes (the `pre-push` block is on agents/develop; the PR check and the setup-script
  line still to place).

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
