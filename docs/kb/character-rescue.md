# Character rescue: a way to fix a character that works without the app

*DESIGN, owner-approved direction 2026-10-04. Nothing built. Code on `feature/character-rescue`, from `integration`.*

## Why

Characters have carried data that broke the builder on load, with no way for the player to fix
them. Measured examples (`hidden-selection-picks.md`, "Trapped picks"; `test/e2e/orphan-clear.js`
on `fix/hidden-multiclass-skill-pick`, `ab89f88e`): with a homebrew pack not loaded, a feat pick
cannot be cleared by any route, and a spell pick only by changing class. "Missing Content" lists
them and offers no action.

**Owner's rules:**
- Nothing stored on a character may be trapped with no way to clear it.
- Clearing is an option the player takes, never automatic: homebrew lives in one browser, so a pick
  missing here may be fine in another.
- Only the character the player decides on is saved: rescue is a view over stored data, never new
  stored data.

## The principle: an emergency path at the bottom, nicer layers on top

If bad data can stop the app from loading, anything that lives inside the app can be taken out by
the same bug. The foundation works without the app running; each layer above works if everything
above it is broken.

| layer | what | depends on |
|---|---|---|
| 1. engine | two pure functions over the stored character: list every stored pick with its path; remove the pick at a path. No template, homebrew or rendering. Shared code, JVM-tested. Shows nothing by itself | the stored data only |
| 2. server routes | list the picks on a saved character; remove one. Behind the existing login and owner checks, like saving a character | layer 1, the database |
| 3. emergency page | server-rendered, at a plain address (e.g. `/characters/:id/repair`), with its own tiny script and none of the app bundle. Prints the raw list, Remove per line. Reached from the boot-rescue bar, the "won't load" recovery panel, or typed directly | layers 1–2 |
| 4. in-app choices list | the friendly version: active / planned / missing, Clear, Reset all (mocked: `inactive-picks` review, round 4 "m8"). Same engine, so it cannot disagree with layer 3 | the app |
| 5. builder markers | "Planned: …", the "1st" badge (`decision-gate-hidden-picks.md`) | the builder |

**Browser-only characters** (made logged out, never saved) are not on the server, so layers 2–3
cannot reach them. They take time to make, so they get rescue too: the same layer-1 engine behind
a client-side page in the boot-rescue style, which depends on nothing the app owns.

## Layer 1 spec: the shared `picks` namespace (decided 2026-10-04)

One engine over a character's stored picks, used by this rescue work AND the hidden-pick work
(`decision-gate-hidden-picks.md`). Built ONCE, as its own small PR from `integration`, first.

**It is a consolidation, not new code.** Three walkers over stored picks already exist; a fourth
would be the duplication to avoid:

| existing | where | this PR |
|---|---|---|
| `walk-entries`, `walk-picks`, `picks-of`, `relink-picks` | `content_reconciliation.cljs` (the heals) | **move** into `src/cljc/orcpub/dnd/e5/picks.cljc`, bodies unchanged; callers updated, no aliases left behind |
| `flatten-options`, `build-option-paths` | `entity.cljc` (the build, hot path) | **not touched** |
| `extract-content-keys` | `content_reconciliation.cljs` (Missing Content) | **not touched** |

**New, and only this:** `remove-at` and `put-at`. A pick is addressed by its selection path plus
its own key, never by its index in a vector (indices shift when another pick is removed). Planned
later on top, by the hidden-pick work: `disqualified`, `overflow`, `to-planned`, `from-planned`,
and `::picks/planned` (the in-memory hold).

**Why `.cljc`:** the rescue server path (layers 2–3) must walk and remove picks without the app;
the server's stored format converts with `entity/from-strict` / `to-strict`, both already shared.
Bonus: the heal-walker tests then also run in the JVM suite, which CI gates on.

**The `.cljc` traps, and the rules that keep this PR safe** (`testing-infrastructure.md` on
`agents/develop`; `clojurescript-type-tolerance.md`):
- JVM and browser can disagree: `(into #{} …)` once diverged on 159 of 808 graphs in the browser
  only. The namespace builds no sets and relies on no iteration order; its docstring says so.
- CI runs only the JVM suite, so this PR also runs the browser suite by hand
  (`lein fig:test` + `node test/e2e/cljs-harness.js`).
- No browser calls; one broken `.cljc` blocks the whole browser test build.
- **Proof of a pure move:** the moved bodies tokenize identically to the originals (comments
  stripped, strings masked); the heal tests pass unchanged on both platforms.

**Not built, on purpose:** a general tree library, history, or an undo framework. Undo for an
automatic change is keeping the previous character value (immutable, free to keep) until the notice
is dismissed; Redo keeps the undone one.

## Layer 0: the hatch finds the player

A hatch nobody can find is not one. Entry points, most robust first:

1. **A rescue bar on every character page, boot-rescue style.** The server already renders each
   character's page (`routes.clj` `character-page`, which knows the id before any app code runs).
   It writes a hidden one-line bar with that character's repair link, shown unless the app reports
   the character rendered within a few seconds. A white screen or an endless spinner still shows
   "This character didn't load. Repair it." Check whether the builder's own address knows the
   character id server-side; if not, it needs the same.
2. **The "won't load" recovery panel** links to the repair page.
3. **The character list:** a "Repair" item per character (the list reads summaries, so it usually
   survives one broken character).
4. **Support:** the "report a character that won't load" email carries the repair address.
5. **The plain address** (`/characters/<id>/repair`), last resort.

Browser-only characters: the same bar and repair page, run from the browser's own storage.

## Precedents to build on, not beside (D29)

- `orcpub.index/boot-rescue` (`index.clj`): the homebrew dead-man's switch. Server-rendered, inline
  styles, plain `localStorage`, shown unless the app reports it booted. The model for layer 3 and
  for the browser-only page.
- The "character won't load" recovery panel and its report action (CHANGELOG, Summer Patch:
  `d50eaf87`, `c2bc7d03`): the natural in-app entry point to layer 3.
- Character routes (`routes.clj`): `get-character` (`:get` on `dnd-e5-char-route`, no auth),
  `save-character` (owner-checked), `delete-character` (`check-auth`). Layer 2 follows their auth.
- The login token is in `localStorage` under `"user"` (`db.cljs`, `local-storage-user-key`), as an
  EDN string, so a page without the app reads it the way boot-rescue reads `plugins`.

## Limits, stated up front

- **An open builder tab can undo a repair:** its next autosave can write the removed pick back
  (`multi-tab-character-contamination.md`, on `agents/develop`). Until that is fixed, the repair
  page says to close the character elsewhere first.
- **Layer 3's Remove is raw:** it deletes a stored entry by path without knowing the rules. Right
  for an emergency tool; the friendly layers sit above it.

## Open, for the branch to settle by test

- How options are stored in Datomic (entity per option?), so layer 1's "path" and layer 2's remove
  map to real retractions, with the owner check.
- Auth for layer 3: the page script reads the token and calls the routes; confirm the existing
  `check-auth` accepts that unchanged.
- Whether "Reset all choices" is in scope for layer 3 or only layer 4.
