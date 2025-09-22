This repository is a Clojure / ClojureScript app that runs a D&D 5e builder site (orcpub). The file below gives focused instructions for AI coding assistants (and humans) to make safe, valuable edits without introducing regressions.

Repository overview
- Languages: Clojure (backend), ClojureScript (frontend with Re-frame / Reagent).
- Important folders: src/cljc (shared code), src/clj (server), src/cljs (frontend), resources/public (compiled JS), test/ (unit tests), dev/ (dev REPL helpers).
- Key runtime pieces: Datomic for persistence, Pedestal + Jetty for server, Figwheel for CLJS dev reload.

High-level conventions and gotchas for automated edits
1. Re-frame subscriptions
- reg-sub-raw handlers must return a Reagent reaction (e.g., reagent.ratom/make-reaction or deref of a reaction). They must NOT return core.async channels or promises. Side-effects (HTTP calls, put! on channels) should be triggered from inside go blocks or event handlers, but the reg-sub-raw must synchronously return a reactive value.
- If a subscription currently uses a go block to fetch remote data, ensure you change the pattern to run the async side-effect but still return a reaction immediately. See src/cljs/orcpub/dnd/e5/equipment_subs.cljs for examples and previous bugs.

2. Authentication state
- The app-db stores auth under the key :user-data (not :user). Many places depend on (:token (:user-data @app-db)). Automated changes that touch login, headers, or auth validation MUST preserve this key structure.

3. RNG & randomness
- The project contains many random generators under src/cljc/orcpub/dnd/e5/character/random.cljc and dice utilities in src/cljc/orcpub/dice.cljc. These use Clojure's core/random functions (rand, rand-int, rand-nth, shuffle).
- When making changes to randomness, prefer introducing a seedable RNG wrapper in src/cljc/orcpub/random.cljc (or similar) that exposes deterministic pure functions (take RNG state in and return new state + value). Keep a short, backward-compatible wrapper (e.g., rand-nth, shuffle) that uses the default RNG so existing call sites keep working.

4. Files and heavy-touch areas
- Frontend views: src/cljs/orcpub/dnd/e5/views.cljs is a very large file. Small edits are fine; large refactors risk regressions in UI behavior (orcacle, character builder pages). Avoid wholesale auto-formatting of this file.
- Subscriptions/events: src/cljs/orcpub/dnd/e5/subs.cljs and events.cljs are central to app state. Changes here require careful testing.
- Shared code: src/cljc contains logic used by both client and server; prefer port-safe code (no direct Java interop) in .cljc files.

5. Tests and validation
- Unit tests exist under test/ (clj/cljc/cljs). After changes, run tests (lein test, cljs-test runner) when possible.
- For frontend changes, run the Figwheel REPL to smoke-test UI flows (open-orcacle, save custom items, etc.).

How to run locally (short)
- Backend: lein run or use the provided start scripts. Datomic transactor requires local data; see README.md for env var settings.
- Frontend: lein figwheel or the npm-like scripts in package.json (if present). Use the browser to access http://localhost:8080 (or configured port).

Data migration pattern (move-first, verify, then remove)
- When extracting large static data (name lists, tavern lists, etc.), prefer a conservative, three-step approach: Move → Verify → Remove.
- Steps to follow:
	1. Create the new data file/namespace under `src/cljc/orcpub/data/names/` (or another appropriate location). Put the full data map there and keep the original data in place for now.
	2. Add a small shim or require in the original file that points to the new namespace (for example, require `orcpub.data.names.mylist` and use a local var that references it). Do NOT delete the original data until verification passes.
	3. Run the unit tests (`lein test`) and a quick CLJS smoke test (start Figwheel and exercise pages that touch the data, e.g., orcacle, character creation). Verify there are no compile-time or runtime errors and the UI behavior is unchanged.
	4. Optionally add a short test that loads the new data namespace to ensure it is available in both JVM and CLJS builds.
	5. Once verified, remove the original in-file data and keep only the new data namespace/shim. Run tests and smoke checks again.

	Note about in-file TODO comments
	- Before removing the original data from its source file, add a clear TODO comment in that file stating the data has been moved and referencing the new namespace/file. This makes the intent discoverable for reviewers and future contributors and prevents accidental deletion without verification.
	- Example comment to add in the original file:

		;; TODO (move-first-verify): `turami-names` moved to `src/cljc/orcpub/data/names/turami.cljc`.
		;; Keep this original data/alias in place until tests and Figwheel verification pass.
		;; After verification, remove this block and update callers to use the new namespace directly.


- Verification checklist (minimal):
	- [ ] `lein test` passes (or at least compiles without namespace errors).
	- [ ] Figwheel CLJS build compiles and the pages that use the data don't throw console errors.
	- [ ] An example generated name or UI element that depends on the moved list behaves the same before and after the move.
	- [ ] If possible, add a short unit test to cover loading the new data namespace.


Do's and Don'ts for AI edits
- Do: Make small, focused changes; add tests for new behaviors; follow existing naming and namespace patterns.
- Do: Preserve authentication keys (:user-data) and re-frame patterns.
- Do: When converting asynchronous subs to side-effect + reaction pattern, follow the explicit pattern: create a reagent/make-reaction returning the current value, and perform fetches in side-effecting go blocks only.
- Don't: Replace large files (like views.cljs) wholesale or auto-format them without running the app.
- Don't: Return core.async channels from reg-sub-raw handlers.

Relevant example locations
- orcacle UI & related views: src/cljs/orcpub/dnd/e5/views.cljs
- Subscriptions (frontend): src/cljs/orcpub/dnd/e5/subs.cljs and src/cljs/orcpub/dnd/e5/equipment_subs.cljs
- Events (frontend): src/cljs/orcpub/dnd/e5/events.cljs
- Shared RNG & dice code: src/cljc/orcpub/dnd/e5/character/random.cljc and src/cljc/orcpub/dice.cljc

If you are an automated agent making edits, run the project tests and do a quick smoke-test of the orcacle flow and saving custom items to ensure subscriptions and auth headers work.

Contact / PR guidance
- Create small PRs with focused changes and include a short test plan in the PR body: which pages/actions to manually verify (e.g., open Orcacle, save a custom item, login/logout flows).
- Reference this file in PR descriptions when relevant.

This document is intentionally concise. For broader architecture and contribution details, see ARCHITECTURE.md and CONTRIBUTING.md in the repository.
