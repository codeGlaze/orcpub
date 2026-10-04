# Character rescue: a way to fix a character that works without the app

*DESIGN, owner-approved direction 2026-10-04. Nothing built. Code branches from `integration`.*

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
