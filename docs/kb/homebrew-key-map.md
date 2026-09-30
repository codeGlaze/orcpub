# Homebrew key map — phase 1 of the authoritative keys fix

**Status: MAP, 2026-09-27.** Input to the design checkpoint in `plan-next.md` §0a. Nothing here is a
design. It records what the code does, the rules a fix has to keep, and every place those rules are
broken today.

**Read against three pinned commits:**

| name | commit | what it is |
|---|---|---|
| INT | `a0d9e1d2` | `integration`, the baseline the fix lands on |
| GR | `a20bf475` | `feature/grant-rows`, with six review rounds of the save gate and the grants layer |
| P34 | `cd82f249` | PR #34 (`f1852203-accounts`), with the paused agent's load and import repairs. **Missing INT's last five commits**, including `ed4f5a4c` (source-tagged minting) |

**Method:** four read-only passes (minting, key changes, references, writers and storage). Each
searched two independent ways and marked every claim VERIFIED (code read) or INFERRED. The
surprising claims were then re-checked by hand. Unmarked statements below are VERIFIED. Anything
not covered is listed in §8.

---

## 0. The model in one paragraph

The library (`:plugins`, shaped `{source {content-type {key item}}}`) lives in one localStorage slot
(`"plugins"`), written whole on every change by the single writer `plugins->local-store`. That
writer checks `set-item` and reports a failed write (`::e5/plugins-save-failed`). An item is
addressed by the **map key it sits under**. It also carries its own `:key` and `:option-pack`
fields, and those can disagree with its address. Characters live on the server and store chosen
content as `::entity/key` values. Other library items point at an item by key through about
fourteen kinds of field. A key change heals characters through `:former-keys`, and heals exactly
**two** kinds of library link.

## 1. Branch baseline — what differs

| piece | INT | GR | P34 |
|---|---|---|---|
| Minting a new key | `source-tagged-key` at 4 inline sites | the same, behind one `address-for` | untagged `name-to-kw` (predates `ed4f5a4c`) |
| Save guard | `save-collision` (overwrite or another source only) | `save-destination` + `:builder-origin` (move / refuse / vanished / ambiguous) | `save-collision` |
| Builder key control (`change-builder-item-key`) | yes | yes, and re-stamps the origin | **absent** |
| Readers stamp `:key`/`:option-pack` from the address | no | yes (`process-plugin-vals`, `process-plugins-with-sources`, `compute-plugin-vals`) | no |
| A renaming Move goes through `rename-key-in-plugin` | **no** | **no** | yes (`054e42c1`, `ec4a3c50`) |
| Load repair written back to storage | no (salvage only, recomputed every boot) | no | yes (`mend-library`, `persist-set-aside!`, from `9ef8bfe5`) |
| Grants (`:grants`, pools) | absent | yes | absent |
| Reference-link probes (`reference_web_test.cljs`) | absent | absent | yes (`d7640dd8`, `cd82f249`) |
| `source-for` / `declared-source` | not found under that name | not found under that name | `e5.cljc:62,77` |

Everything else read was identical across the three, or differed only in line numbers.

## 2. Minting and identity

**Where a key is created or derived.**

| site | inputs | notes |
|---|---|---|
| Builder save, save-anyway, selection save (+anyway) | existing `:key`, else name + source tag | INT/GR keep an existing key. P34 tags nothing |
| `sanitize-item-names` (`orcbrew_validation.cljs`) | **name only** | **Always** sets `:key` from the coerced name, discarding the old one. The save paths on INT/GR overwrite that again with the kept key. **P34's save-anyway uses it directly**, so it re-derives there |
| `coerce-invalid-names` → `sanitize-item-names` | name only | Auto-name & Restore. Runs on **every** item in the source, invalid or not. See §3 |
| `generate-new-identity` → `disambiguated` | name + target source + taken set | Import clash rename, and a Move that lands on a taken key. Same on all three |
| `rekey-content-group` (`e5.cljc`) | name, only when the current map key is invalid | Quarantine repair. Leaves valid keys alone |
| `dedup-options-in-selection` | option name + index | Nested option keys ("X 2"). Also runs on **export**, which writes its corrections back to the library (`export-all-plugins`, `persist`) |
| `magic_items.cljc` `add-key` | name | Custom magic items are server-backed and a separate store. Their key follows their name, so renaming one strands every character that has it equipped |

**What identifies an item.** Validation trusts the map key (`(assoc (validate-item k v) :key k)`).
GR's readers overwrite `:key`/`:option-pack` with the address on every read, so on GR nothing
downstream sees a disagreement. INT and P34 pass items through as stored, so a stale `:key` reaches
edit buttons, pickers and characters. `:option-pack` (required on nearly every type) and
`:plugin-source` (display, homebrew detection) are two source-name fields, and nothing unifies them.

**Validity.** Every content-type spec enforces `keyword-starts-with-letter?` on `::key`. A blank name
mints `unnamed-<hash>`, never an empty keyword. `canonical-key` is a lookup fallback only (it trims a
trailing separator, and only when exactly one item matches). The magic-item `::key` is not checked
this way (INFERRED; only `::type` was seen specced).

## 3. Every way an existing key changes

| path | trigger | asks? | records `:former-keys` | repoints library links | notes |
|---|---|---|---|---|---|
| Builder key control | user edits the key | yes | yes | 2 of 14 kinds | INT, GR only |
| Import clash, "rename existing" (`apply-key-renames` → `rename-key-in-plugin`) | conflict modal | yes | yes | 2 of 14 kinds | all three |
| Move that lands on a taken key (`relocate-content`) | My Content Move | no | **INT/GR: no** · P34: yes | **INT/GR: none** · P34: 2 of 14, classes and races moved first | the INT/GR regression P34 fixed |
| Quarantine repair (`rekey-content-group`) | Restore | yes | no | none | only items whose key was invalid |
| Auto-name & Restore (`coerce-invalid-names`) | button | yes | no | none | **desyncs `:key` from the address on every item in the source.** The address survives. GR's read-stamp hides this; INT and P34 do not |
| Save-anyway | button | yes | no | none | re-derives the key **on P34 only** |
| Export option dedup | Export | no | no | none | nested option keys, persisted silently |

**How `:former-keys` is consumed** (identical on all three): `former-key-index` builds one global
`{old → current}`. It drops an old key claimed by two items, or one that is some item's live key.
So after an import-clash rename, a character keeps pointing at whichever item now holds the old key,
and nothing says so. `reconcile-former-keys` rewrites `::entity/key` values only, never map keys:
spell selections named after a class (`:artificer-spells-known-2`) are healed separately by
`reconcile-spell-selection-keys`, for loaded classes only. The cap is 4, with the first entry kept.
**No path heals library-to-library links through `:former-keys`.**

## 4. Everything that points at a key

**Library to library.** `key-reference-map` knows two links: subclass `:class` and subrace `:race`.
P34's probes (`d7640dd8`) exercise all thirteen link kinds under `rename-key-in-plugin`, and **eleven
strand**:
- a spell's `:spell-lists` → class
- a class's `:spellcasting :spell-list-kw` → class (a borrowed list)
- a class's `:spellcasting :spell-list` → spell
- a subclass's `:paladin-spells` → spell
- a class's `:level-modifiers :spell` → spell
- a race's `:spells` → spell
- a class's `:level-selections` → selection
- a feat's `:path-prereqs :race` → race
- a race's `:props :language` → language
- a race's `:languages`, **by name** → language
- an encounter's `:creatures :monster` → monster

The probes also show that race `:spells` and `:props :language` are read, not dead fields.
GR adds a fourteenth kind: `:grants` `{:pool p :key k}`. It matches the exact key only, **drops
silently** when the key is missing, and is absent from `key-reference-map`.

**A stranded link does not fail. It binds to something else** (`cd82f249`). The realistic case is an
import-clash rename, because the key moved only because another item holds it. After it:
- the stranded spell joins the *library's* class of that key;
- the feat requires the *library's* race;
- the class's `:level-selections` offers the *library's* selection.

In each case the user sees the wrong content, with no error.

**Characters.** Choices resolve through `entity/index-matching-key`: exact match first, then the
`canonical-key` fallback when it is unambiguous. It is two passes rather than one looser `=`, because
a per-element compare cannot tell an unambiguous rebind from a coin flip between two candidates. The missing-content banner covers six types (class,
subclass, race, subrace, background, feat). It finds subclasses through a hard-coded set of fourteen
selection names, so a fifteenth would slip past it.

**Share link.** `share_bundle.cljc` `outgoing-refs` follows:
- subclass → class and subrace → race;
- spell keys on races and classes;
- languages, **by name**;
- `:level-selections`;
- spell-list membership, in reverse.

It has **no case for `:grants`**. On GR, a character whose content reaches a homebrew item only
through a grant shares a link that leaves that item out.

**By source name.** Items carry `:option-pack`, and GR records the builder origin as `{:source …}`.
The disable overlay's section shape was not verified (§8).

## 5. Writers, readers, storage

**Writers.** About eighteen events write `:plugins` on GR. Only **four go through `save-destination`**:
save, save-anyway, selection save and selection save-anyway. The rest:

| writer | guard | can lose data without saying |
|---|---|---|
| `reg-delete-homebrew` | the entry must still exist | removes it outright; any confirmation is in the UI, not traced |
| `::e5/delete-plugin` | exists | removes a whole source |
| `::e5/relocate-selected` | none | renames on collision (§3); overwrite at the destination not traced |
| `.orcbrew` import | conflict modal | **same-source collisions are excluded from detection**, so re-importing a source replaces it silently |
| Export (`export-all-plugins`) | none | writes corrections back (text, options, dedup) |
| `::e5/keep-shared-content` | none | no. It always adds a new `"<name> (shared)"` source |
| `::e5/repair-quarantined-source` | re-runs the spec check | no, but see Auto-name in §3 |
| `::e5/toggle-plugin(-item)` | none | no, it is reversible. GR also disables same-key twins |
| `::char5e/delete-all-plugins` | explicit action | wipes the library, by design |
| `::e5/set-source-abbreviation`, `change-builder-item-key` | their own checks | no |

The earlier figure of "26 write sites across 17 events" re-counted to about 20 sites across 18
events. The gap was not closed (§8).

**Readers.** Every content sub reads through `::e5/plugin-vals` or `::e5/plugins-with-sources`. They
fold demo, then the library, then shared content, so shared content wins for viewing. Raw
`(:plugins db)` reads are export, `save-destination` and reconciliation.

**Storage.** The localStorage slots are:
- `"plugins"` for the library;
- `"plugins:rejected"` for quarantine;
- `"…:corrupt"` salvage slots;
- per-builder **draft** slots, which hold the draft, not the saved entry;
- on GR, `"builder-origin"`.

Writes are whole-map on all three branches; chunked storage was never built. INT and GR salvage on
load but never write the result back, so the same salvage reruns every boot. P34 mends fields and
writes them back, with the set-aside copy written first. The loader in `db.cljs` used to be
all-or-nothing: if any source failed the `::e5/plugins` spec it returned nil, dropping the whole
library.

**Two tabs.** None of the three has a `storage` listener. Both tabs keep `:plugins` in memory and write
the whole map, so the last write wins and the other tab's edits vanish with no warning. The character
version of this problem is documented (`multi-tab-character-contamination.md` on `agents/develop`).
Nothing had been written for the library before this page.

## 6. Invariants a fix must hold — and where each breaks today

| # | invariant | holds | broken |
|---|---|---|---|
| I1 | A key is minted once, from name + source tag, and never re-derived | ordinary saves on INT/GR | P34 minting and save-anyway; Auto-name & Restore; export option dedup (nested); magic items (separate store) |
| I2 | An item's `:key` and `:option-pack` equal its address | GR, at read time only | Auto-name & Restore on all three; INT and P34 readers |
| I3 | Every key change records `:former-keys` | builder key control, import rename | renaming Move (INT/GR), quarantine rekey, Auto-name, export dedup |
| I4 | Every key change repoints every link to it | subclass → class, subrace → race | the other eleven kinds, plus `:grants` |
| I5 | A broken link is shown, never silently rebound or dropped | missing-content banner, six types | clash-rename strands bind to another item; grants drop; `former-key-index` latches onto the other item |
| I6 | Every write that can overwrite, move or remove passes one gate | 4 builder saves on GR | Move, same-source import, export write-back; INT/P34 have no move-aware gate |
| I7 | The builder knows where its open item was read from | GR (`:builder-origin`) | INT and P34; whether a Move or import rename updates an open builder was not traced |
| I8 | Two tabs cannot silently lose each other's writes | — | all three |
| I9 | A failed write is reported | all three (`plugins->local-store`) | — |
| I10 | A share link carries everything the character depends on | the six link kinds `outgoing-refs` walks | `:grants`; any link it does not walk |

## 7. Gaps, ranked by how likely a user is to hit them and how much they lose

1. **Renames strand links, and the stranded link binds to the wrong item** (I4, I5). Eleven of
   thirteen link kinds, plus grants, and the result is wrong content with no error. This is the main
   hole. The save rework never touched it.
2. **A Move that lands on a taken key bypasses `rename-key-in-plugin` on INT and GR** (I3, I4). It
   records no history and repoints nothing. P34 has the fix (`054e42c1`, `ec4a3c50`).
3. **Writers outside the gate** (I6): Move, same-source re-import, and export write-back.
4. **Two tabs** (I8). The last write wins over the whole library, and nothing warns.
5. **Auto-name & Restore desyncs `:key`** (I1, I2). It is hidden on GR, and live on INT once the fix
   lands there without GR's read-stamp.
6. **Grants are invisible to rename, share and the missing-content banner** (I4, I5, I10). GR only
   for now, but it will reach INT when grants land.
7. **Load salvage is never written back** on INT and GR. P34's `mend-library` is the input.
8. **P34's untagged minting and save-anyway re-derivation** go away when P34 pulls INT. Recorded
   here so nobody reads P34's save path as a design.

**#34 inputs this map points at:** `054e42c1`, `ec4a3c50` (Move through the rename path);
`d7640dd8`, `cd82f249` (link probes, which become the regression suite for I4 and I5);
`9ef8bfe5` and its follow-ups (load repair written back); `source-for` / `declared-source`;
`homebrew_guard.cljc` and `homebrew_check.cljs`, which were not analysed (§8).

## 8. Not covered, or uncertain

- The disable overlay's `[source content-type]` shape, and whether every read sub enforces item-level
  `:disabled?`.
- Whether the destination can be overwritten in `relocate-selected`.
- Whether an open builder is updated when a Move or import rename changes its item.
- The pre-modal import merge (`store-imported-sources`): INFERRED not to rename.
- The exact writer count (about 20 sites found against 26 claimed).
- Boot code in `core.cljs`: P34's three-step boot rescue compared with INT/GR's plain loader.
- P34's `homebrew_guard.cljc` / `homebrew_check.cljs`, and how they overlap with GR's read-stamp.
- Consumers of `:spellcasting :spell-list-kw` and `:path-prereqs`, re-read only through P34's probes.
- The magic-item `::key` validity spec.
- `http_safe.cljs`, the empty-keyword choke point.

## 9. Stale claims found in other pages

- `library-management-and-conflicts.md`, "Move does not touch the key": true only when the target key
  is free.
- `homebrew-reference-web.md` (`agents/develop`), "rename and relocation both go through
  `rename-key-in-plugin`": true on P34, false on INT and GR.
- `homebrew-safety-net.md` (`agents/develop`) describes `mend-library`, which exists only on P34.
- `duplicate-key-durability-roadmap.md` (`agents/develop`), "every save re-derives the key": fixed on
  INT and GR, still true of P34's save-anyway. Its underlying worry lives on in Auto-name & Restore.
- `source-tagged-keys.md` reads as a GR deliverable, but source-tagged minting is on INT
  (`ed4f5a4c`).

These pages are left as they are until the design checkpoint. Correcting them is part of the fix.
