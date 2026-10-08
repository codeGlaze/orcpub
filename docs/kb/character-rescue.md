# Character rescue: a way to fix a character that works without the app

*DESIGN, owner-approved direction 2026-10-04. Built: layer 1 (`picks`, #47) and the ledger (the Character data page) with Remove, Undo and Save. Not built: key editing and the entry points. Code on `feature/character-rescue`, from `integration`.*

## The ledger: the Character data page (decided 2026-10-06; commits 1 and 2 built)

The owner's picture of the rescue: a structured view of the stored character, like a spreadsheet
or a data table with a few buttons, that never asks the app to draw it. It replaces both the bare
repair page (layer 3) and the later in-app list (layer 4): one page, saved and browser-only alike.

| decided | what |
|---|---|
| how it reads | its own script, `ledger.js` (`ledger.cljs.edn`, `src/cljs/orcpub/ledger.cljs`), built from the shared code (`picks/addresses`, the EDN reader); never `orcpub.js`. Loaded only on that page |
| where | `/pages/dnd/5e/characters/<id>/data`; `/repair` opens the same page; `/pages/dnd/5e/character-data` for this browser's draft (`orcpub.ledger-page`) |
| names | on screen "Character data" (character page, list) and "Repair this character" (rescue bar, won't-load panel, support email). "Ledger" is internal only: the spellbook print options already show a "Ledger" layout |
| rows | one per stored pick, children indented under their parent, with how many sit under each (`orcpub.dnd.e5.ledger/rows`). Removing a parent removes its children (they are stored inside it) |
| who | owner only (stored owner matches the login's username or email). Anyone else, or logged out: "Only the owner can open this". This is not privacy: `GET /dnd/5e/characters/:id` is public |
| support | no admin or moderator role (none exists; the two-person team does not want one). "Copy for support" copies the rows as text; the player makes the fix. A share link (`routes/share.clj`, the character's homebrew by token) lets a mod see it with the right homebrew |
| later commits | 2: Remove, Undo, Save. 3: editing a stored key, only with dev mode on (localStorage `"dev-mode"`). 4: the links in, and the rows in the support email. 5, if wanted: keep a copy of a browser draft the app cannot read |
| unreadable data | shown raw with a Download button, so nothing is lost |

**How Save works (commit 2):** `ledger/save-data` replaces only the stored map's selections, from
`entity/to-strict` of the edited character; values, summary and owner go back exactly as read
(`char5e/to-strict` is not used: it rewrites values the page never touches). A saved character goes
through the app's own save route as transit, with the login's token; the browser draft is written
back to localStorage `"character"`. Measured in `ledger_save_test.clj`: a removal retracts exactly the
removed pick's records.

- **The summary is not rebuilt:** the page cannot build the character, so the character list may
  show the old race or class until the app next saves it.
- **Emptied lists stay:** removing the last pick of a multi-pick selection leaves the selection,
  empty, as unticking it in the builder does; a one-pick selection is removed whole.
- **A new id is possible:** when the stored character fails the spec, the save route replaces it
  under a new id, and the page opens the new id's data page. Measured below, "Save replaces an
  invalid character": the character also leaves its folders and parties.

**Removing an essential choice (measured, `test/e2e/remove-essentials.js`):** neither breaks the app.

| removed on the data page | the app opens it | Save as opened | the builder's way back |
|---|---|---|---|
| the class (with its 4 levels) | yes, no errors; sheet at level 0 | saves (200) | "Add Levels in Another Class", then saves |
| the ability scores | yes, no errors; scores show 0 | refused before sending: "You must provide values for all ability scores" | pick a method (Standard Scores, Point Buy...), then saves |

So the data page warns, it does not block: the warning says what goes and what the builder will ask
for. Removing the race was not measured.

**Measured:** `ledger.js` is 1.2 MB, 264 KB gzipped; the app's `orcpub.js` is 3.2 MB, 813 KB gzipped
(`integration` 63d63add; `develop` 15e1fe04: 2.9 MB, 725 KB). Most of either is built-in game data;
`picks` brings it into `ledger.js` through `library.cljc` (without `picks`: 96 KB gzipped). In the
browser with no app loaded, `ledger.js` read the saved Tide Pak wizard and listed all 11 choices.

**Found on the way, not this branch's:** the server gzips `orcpub.js` (812 KB sent), but sends no
`Cache-Control` and no `ETag` for it (the `etag-interceptor` in `pedestal.clj` is wired in yet none
comes out), and answers `If-Modified-Since` with the full file. A browser that checks before reusing
its copy downloads 812 KB on every visit. Fixing it needs its own branch.

## Save replaces an invalid character (research, 2026-10-06; nothing changed)

`routes/update-character` has two paths. When the STORED character passes `::se/entity`, a save
diffs ids: the character keeps its id. When it fails, the save retracts the whole character and
creates the new one under a NEW id (`"INVALID CHARACTER FOUND, REPLACING"`, upstream code from
2025-09). Pinned in `test/clj/orcpub/save_replace_research_test.clj` (each deftest asserts today's
behaviour) and `test/e2e/digit-key-save.js`.

| claim | verdict | evidence |
|---|---|---|
| R1 valid stored character: same id, folder, party and share intact | CONFIRMED | r1 |
| R2 invalid stored character, a save that removes the bad part: 200, new id, old address answers 400 | CONFIRMED | r2, both causes below |
| R5 then: dropped from its folder and party (references), share records left on the dead id (numbers) | CONFIRMED | r2 |
| R3 invalid stored, and the save still invalid: refused (400), nothing changes | CONFIRMED | r3 |
| R4 the save route refuses to create an invalid character | CONFIRMED | r4 |
| R7 the app's own load-then-save merges duplicate selections, so the app's autosave takes the replace path too; a digit key survives the round trip and the app's save is refused | CONFIRMED | r7 |
| R8 a tab still holding the old id is then refused: 401 "You do not own this character" | CONFIRMED | r8 |
| R9 the save check refuses a key starting with a digit; the app cannot make one today (the loader quarantines such homebrew in `plugins:rejected`; `name-to-kw` itself does not repair) | CONFIRMED | r9, e2e 4/4 |
| R10 two saves made from the same older copy, each adding the same selection, store two selections with one key: the stored character then fails the check | CONFIRMED (simulated race) | r10 |

**How invalid data gets stored.** Only the save route writes characters, and it refuses invalid
input (R4). So invalid stored data comes from (a) data saved before today's checks, or (b) two saves
racing from the same older copy (R10): two tabs, or a double save. Its NEXT valid save, by the app's
autosave (R7) or by the data page's Save, moves it to a new id (R2).

**Why the data page makes it likelier:** repairing means removing the bad part, which is exactly
"stored invalid, incoming valid".

**Shown falsifiable:** with the invalid branch forced off, r2, r7 and r8 fail (12 assertions) and the
folder and party links survive. That hints at a fix; it is not one, and needs its own tests.

**Not known:** how many stored characters fail the check in production. Read-only, on a copy of the
database:

```clojure
(require '[datomic.api :as d] '[clojure.spec.alpha :as spec] '[orcpub.entity.strict :as se])
(let [db (d/db conn)]
  (frequencies (for [id (d/q '[:find [?e ...] :where [?e ::se/owner]] db)]
                 (spec/valid? ::se/entity (d/pull db '[*] id)))))
```

## Settled by test (phase 1, 2026-10-06)

Measured on the seeded server (Tide Pak wizard from `orphan-clear.js`, clicks only) and in a JVM
probe against an in-memory Datomic with the real schema.

| question | answer | evidence |
|---|---|---|
| how picks are stored | every selection and every option is its own component entity with a `:db/id` (`::strict/selections` → `::strict/option` or `::strict/options` → `::strict/selections` …). A `picks` address maps to exactly one option entity | `GET /dnd/5e/characters/:id` returns `d/pull [*]`: feat at `[:feats :tidebreaker]`, spell at `[:class :wizard :wizard-spells-known :brine-lash]`, race at `[:race :tidefolk]` |
| removing one | `[:db/retractEntity <option id>]` removes it and its subtree; the character still passes `::se/entity` | JVM probe: 19 keyed entities → 16, no trace of the three picks |
| empty selection | a one-pick selection left with no option reads back through `from-strict` as `[]` (`:race []`) | JVM probe. `picks/remove-at` avoids it: it dissocs a one-pick selection |
| auth from a page without the app | works unchanged: a script reads `:token "…"` from localStorage `"user"` (EDN) and sends `Authorization: Token <jwt>`; `check-auth` gives 200, and 401 for no token or a bad one | Playwright on `/privacy-policy` (no `orcpub.js`) after a real login. Zoe's token on Kaylee's character: `DELETE` 401 |
| does the builder's address know the id | **no.** It is always `/pages/dnd/5e/character-builder`; the server's HTML has no id. The id is only in the localStorage draft (`"character"`, strict EDN). A regex cannot pick it out safely: the first `:db/id` printed belongs to a selection | Playwright: builder URL and draft read after Open → Edit |
| what the app sends on save | `application/transit+json` (`:transit-params`, `http_safe.cljs` `wrap-transit-params`); the response is `application/edn` | request captured from a real Save click |

**Consequences for the build:**
- Layer 2 remove = `from-strict` → `picks/remove-at` → `to-strict` → the existing `update-character`,
  which already owner-checks and retracts the ids that are gone. No new transaction code.
- The rescue routes take JSON (or the address in the URL) and answer JSON: the page script never
  ships transit or parses EDN.
- The builder's rescue bar cannot get the id from the server; it needs the draft read by something
  that parses EDN (see the browser-only question below).

**`picks/addresses`** (added on this branch, the owner's call relayed from the planning session):
every stored pick as `[address entry]`, the address `remove-at` and `put-at` take, a parent before
its children. A separate function, not a change to `walk`: `walk-typed` and the heal rely on
`walk`'s selection-keys-only path. An entry with no `::entity/key` has no address and is left out.

**Found on the way, not this branch's to fix:** `save-character` reads `:transit-params` only. A
body in any other format reaches it as nil, passes the spec as `{}`, and creates a blank character
owned by the caller, with 200.

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

**Built** on `refactor/picks-namespace` and **merged into `integration`** as `d2137952` (#47, 2026-10-04).
As built: `walk` (public), `walk-typed` (private), `keys-of`, `relink` (the four moved, token-identical
bar the names), plus `remove-at` / `put-at`. `remove-at` returns `{:character :removed}`; `:removed` is
`{:address :entry :multiselect?}`, which `put-at` takes. Address: `[selection key, entry key]` pairs, the
app's option-path format.

**Storage shapes, and why `:multiselect?` is recorded.** A selection stores its picks as a vector when
it is `::t/multiselect?`, otherwise as the one entry map; absent means never chosen. `remove-at`
leaves a multiselect's vector in place, empty if it took the last pick: the same state unticking
the last pick leaves (`event_handlers.cljc` `update-multi-select`), so it is not a new state, and
saving strips empty collections (`entity/remove-empty-fields`), so it never reaches storage. A single
pick's selection is dissoc'd, as if never chosen. Neither shows its shape afterwards, so the removed
record carries `:multiselect?` (the template's own word, `::t/multiselect?`) for `put-at`: true appends
to the vector, false makes it the selection's entry.

**`put-at` writes only into an empty place** (`453aff1d`). A multiselect already holding the key, or a
one-pick selection already holding any pick, is left alone and `put-at` returns nil, as when the path is
gone. That is the decided rule (`decision-gate-hidden-picks.md`): a new pick in that slot retires the held
one, so the player's later choice is never overwritten or duplicated. Nil means "retire the held copy". JVM 542, cljs 546, lint 0 errors and no new warnings, dev build clean.

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
  only. The namespace never depends on the iteration order of a set or map; its docstring says so (`keys-of` returns a set, used only for membership).
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

## Open, for the owner

- Commit 5: keep a copy of a browser draft the app cannot read (today `db.cljs` `handle-unreadable`
  deletes it on boot; homebrew gets a `:corrupt` copy, characters do not). Build it, or not.

The builder's rescue bar needs no character id: it links to the draft page, `/character-data`.

## Decided

- No "Reset all choices" on the repair page: it amounts to a new character (owner, 2026-10-06).
- Changelog: Summer Patch, on integration.

## History

- 2026-10-06: the separate list/remove server routes (layer 2) dropped: the ledger reads through
  the existing character read route and will save through the existing save route.

- 2026-10-06: the three open questions (storage, auth, builder id) settled by test; see the table
  at the top.
