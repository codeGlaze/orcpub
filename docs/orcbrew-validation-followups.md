# orcbrew-validation Follow-ups

Cleanup tasks identified while extending the export warning modal
(branch `claude/fix-orcbrew-errors-bLkFJ`). These are out of scope for
that branch but worth tracking. Caveat: my detection used grep
heuristics, not a real call-graph or linter — verify each item with
clj-kondo or actual tooling before deleting code.

## Module: `src/cljs/orcpub/dnd/e5/orcbrew_validation.cljs`

### 1. Tighten visibility — convert internal-only `defn` to `defn-`

The module exposes 65 public functions. Roughly 10 are called from
production code (`events.cljs`); the rest are called only by other
functions inside the module or from tests. Tightening visibility
shrinks the public API surface and makes the actual intended entry
points obvious.

**Approach:** for each `defn` whose only callers are inside
`orcbrew_validation.cljs`, change to `defn-`. Tests that need access
can use `#'private-fn` or get migrated to call through the public API.

**Risk:** low. Mechanical change. Compile error is the failure mode.

**Tooling:** `clj-kondo --linter '{unused-private-var {:level :warning}}'`
will surface anything actually unused after the change.

### 2. Audit `*-with-log` parallel implementations

Several functions exist in pairs:

- `clean-data` / `clean-data-with-log`
- `clean-nil-in-map` / `clean-nil-in-map-with-log`
- `fix-empty-option-pack` / `fix-empty-option-pack-with-log`
- `rename-empty-plugin-key` / `rename-empty-plugin-key-with-log`

This pattern looks like an incremental evolution where the with-log
version was added later but the original wasn't deleted. Either the
non-log siblings are dead, or there's still a reason both exist.

**Approach:** trace callers of each pair. If the non-log sibling has
no callers, delete it. If both have callers, decide whether the
non-log version should be implemented in terms of the with-log one
(call it and discard the log) to keep behavior in sync.

**Risk:** medium — silent behavior drift if the two implementations
have diverged.

### 3. Verify reachability of `levenshtein-distance` and `count-non-ascii`

Both have many test callers but my grep showed zero production
callers. Possibly:

- Used through some indirect path (HOF, dynamic dispatch, .cljc file
  not searched)
- Genuinely dead, written for a feature that didn't ship or got removed
- Used by tests only, retained for future feature work

**Approach:** real call-graph analysis (clj-kondo, or load and run
`(clojure.tools.namespace.find/find-namespaces)` style traversal). If
genuinely dead, delete. If retained for a planned feature, add a
docstring noting that.

**Risk:** low. The functions are pure, removing them is reversible.

## Verification gap

The numbers above (65 public, ~10 used) come from a grep pattern that
had a bug (word boundary `\b` doesn't behave correctly after `?`),
which means functions whose names end in `?` were undercounted. Don't
trust the specific counts — use them only as an indication that the
ratio is significant. A real linter run is the right next step.
