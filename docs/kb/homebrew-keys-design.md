# Homebrew keys — the design

**Status: APPROVED to build, 2026-09-27. Steps 1–4 done (`port/save-gate` `b5f2a1b6`); the §9 decisions are still open and are
needed before steps 5–8.** It was the second checkpoint of `plan-next.md` §0a. It answers the map (`homebrew-key-map.md`): its ten invariants (§6) and its ranked gaps
(§7). Link integrity is in scope (owner, 2026-09-27).

**Priorities, in order (owner):**
1. **Backward compatibility** (D9: non-negotiable, zero-migration).
2. **Ruggedness.** No code that gets more fragile with every patch.

**The approach in one line:** replace every scattered, partial answer with three single mechanisms:
- one list of every kind of link;
- one way to change a key;
- one gate every library write passes through.

Then write tests that fail when someone goes around them.

---

## 1. What does not change (compatibility promises)

Each promise is tested (§7), not just assumed.

| # | promise |
|---|---|
| C1 | **No existing key is changed**, and no required field is added. Keys change only through the four actions that change them today (§3). The only stored corrections are the two quiet fixes in §4a, each saved once after a backup copy of the library is kept. |
| C2 | Every `.orcbrew` file already in the wild imports. Old untagged keys, the legacy `:former-key`, and races naming languages by display name all still work. |
| C3 | Files exported after the fix still import into older versions of the app. **No new field is required, and no existing field changes shape.** |
| C4 | Characters on the server are never rewritten by the library code. They heal at load through `:former-keys`, as today. |
| C5 | A key that works today keeps working. Nothing is re-keyed just for being untagged or old. |
| C6 | Nothing a user did before the fix is undone. Where old data is already damaged (links stranded by past renames), the fix **reports it and offers a repair**. It never repairs silently. |
| C7 | **Overriding built-in content keeps working.** A homebrew item keyed `:cleric` still replaces the built-in Cleric (D10b: deleting the tag is how an override is asked for). The builder key control and the source-abbreviation control in My Content both stay. |

## 2. One list of every kind of link

**New: `library_links.cljc`**, a dependency-leaf namespace (D7). It holds one data entry per kind of
link from one library item to another:

```clojure
{:id :subclass->class  :from ::e5/subclasses :to ::e5/classes :at [:value :class]}
{:id :spell->class     :from ::e5/spells     :to ::e5/classes :at [:map-keys :spell-lists]}
{:id :race->spell      :from ::e5/races      :to ::e5/spells  :at [:each :spells [:value :value :key]]}
{:id :race->language   :from ::e5/races      :to ::e5/languages :at [:each-name :languages]}
;; ...one entry for each of the fourteen kinds in homebrew-key-map.md §4
```

`:at` uses a small, closed vocabulary of five **locators**:
- `:value`: a key at a path;
- `:map-keys`: the keys of a map at a path;
- `:each`: every element of a vector, with an optional `:when` filter and a sub-locator;
- `:nested-vals`: the values of a map of maps, as in `{level {slot spell}}`;
- `:each-name`: names, not keys.

Each locator implements exactly two functions, **read the links** and **replace a link**. That is the
whole engine.

**What reads this list:**

| reader | today | after |
|---|---|---|
| Key change (§3) | `key-reference-map` (two links) | every entry |
| Share-link closure | `share_bundle` `outgoing-refs` (its own list of six) | every entry |
| Move ordering (targets before what points at them) | #34's `ec4a3c50`, classes and races only | every entry |
| Broken-link report (§5) | nothing | every entry |
| Link probe tests | #34's `reference_web_test` (its own list of thirteen) | generated from this list |

That is three hand-kept lists replaced by one (D29). Adding a kind of link is one entry: grants add
theirs when they reach `integration`, and rename, share, move and the report all cover them at once.
That is the registration rule this branch runs on.

## 3. One way to change a key

**New: `library/rekey`**, a pure function that is **the only code that changes an existing item's
key**:

```
(rekey library content-type old-key new-key repoint-within) → library
```

It does three things together:
- moves the item to its new address and records `old-key` in `:former-keys` (existing mechanism,
  cap 4);
- **repoints every link to `old-key`**, found through §2, among the items in `repoint-within`;
- never touches characters. They follow through `:former-keys` at load (C4).

**`repoint-within` is the decision that makes clash renames correct.** When a key changes because
another item holds it, the key names two items, and each link means the one it came with:

| why the key changes | the other holder of the old key | links repointed | links left pointing at the other holder |
|---|---|---|---|
| The user edits the key in the builder | none (the gate refuses a taken key) | **the whole library** | — |
| Import clash, the **incoming** item is renamed | the library's item | **the incoming file's sources** | the existing library |
| Import clash, the **existing** item is renamed | the incoming item | **the existing library** | the incoming file's sources |
| A Move lands on a taken key | the target source's item | **the moved item's origin source** | everything else |
| Quarantine repair of an invalid key | none | **the quarantined source** | — |

**Built-in content counts as a holder of its key.** An override is a homebrew item holding a
built-in key:
- **Making an override** (`:cleric-tc` → `:cleric`): no library item holds `:cleric`, so links
  repoint across the whole library. Links that meant the built-in Cleric now mean the override, which
  is what an override is. The report says so in one line: "Replaces the built-in Cleric."
- **Undoing an override** (`:cleric` → `:cleric-tc`): the built-in still holds `:cleric`, so only the
  item's own source is repointed. Everything else goes back to the built-in.

This is the fix for the map's worst finding: a stranded link binding to the wrong item. Today an
import-clash rename leaves the incoming file's own spells, feats and selections pointing at the
library's item. With this, they follow the item they were written for.

Every path that changes a key today is rewired onto `rekey` (§6). `rename-key-in-plugin` and
`key-reference-map` are retired under D34: struck through with a date, and removed about three
months later.

## 4. One gate for every library write

**Today:** `::e5/set-plugins` sends writes through one interceptor, but four events call
`plugins->local-store` directly, and some write `(assoc db :plugins …)` themselves. Only the four
builder saves check anything.

**New: every write goes through `::library/commit`**, as an **operation** rather than a finished
library:

```
[::library/commit {:op (fn [library] → library) :says "…"}]
```

### 4a. The two quiet fixes

These are the only stored corrections this work makes, and **neither changes a key or touches a
character**. Before the first one is written, the untouched library is copied into its own slot
(`"plugins:pre-fix"`), and My Content offers "Restore the previous library".
1. **An item's own `:key`/`:option-pack` fields are made to match where it is stored.** grant-rows
   already does this in memory on every read; this makes it stick. What a character resolves is
   unchanged, because resolution already goes by where the item is stored.
2. **The repair the loader already makes on every visit is saved once**, so it stops re-running.

### 4b. The gate, step by step

The gate does five things, always in this order:

1. **Reads the library fresh** if another tab changed it since this tab loaded it (see below), then
   **runs the op on it**. Ops are pure functions of the library, so replaying one is safe. That is
   what lets two tabs stop losing each other's work.
2. **Normalises addresses.** Every item's `:key` and `:option-pack` are set from where it is stored.
   What grant-rows does at read time becomes true in storage. It closes the Auto-name desync and
   the stale-field problem on `integration` for any writer, including a future one. It is
   idempotent and adds no field (C1, C3).
3. **Checks the invariants:**
   - every key is valid;
   - every address is consistent;
   - **no link that resolved before the write is broken after it**, unless the op deleted its
     target on purpose.

   A violation refuses the write and says why. It never half-writes.
4. **Writes storage once,** and bumps a revision counter in a new `"plugins:rev"` slot. The slot is
   optional: absent means 0, which covers C1.
5. **Reports** the op's message, and any links the write left pointing at nothing (a delete, a
   same-source re-import).

**Two tabs.** A `storage` listener reloads the library when another tab writes it. The revision
check in step 1 catches the race the listener can miss. A save in this tab then runs against the
library as it really is, and grant-rows' origin check (vanished, moved) does the rest. An older
version of the app open in another tab doesn't bump the revision, but the listener still sees its
write.

**The builder save gate from grant-rows** (`save-destination`, `:builder-origin`, six review rounds)
becomes the op for builder saves. It runs inside step 1, so it sees the fresh library.

## 5. Reads that don't lie

- **Broken links are shown, never silent.** My Content marks an item whose links point at nothing,
  and the builder shows which link is broken and where. Grants currently grant nothing when their
  key is missing; they get the same mark. This does not replace the character missing-content
  banner, which keeps its own scope.
- **Links by display name resolve by name.** A race names its languages by display name, and the
  link is resolved by deriving a key from that name (`name-to-kw`). A source-tagged language
  (`:elvish-tc`) can't be found that way, so tagged keys may already have broken this link. That is **unverified**: the share closure breaks
  for certain, and the character-side resolution is checked in build step 7.
  The fix is read-side only: match the language's `:name` first and keep the derived key as a
  fallback. No stored shape changes (C2, C3).
- **A character is never rebound away from built-in content.** *Live bug, found 2026-09-27, on
  `integration` and grant-rows.* `former-key-index` treats only **library** keys as live, so undoing an
  override (`:cleric` → `:cleric-tc`) leaves `:cleric` looking unclaimed. The next time any character
  that took the **built-in** Cleric loads, it is rebound to the homebrew class. The same happens with
  any old untagged key that shadows a built-in one (`:fireball`). The fix: built-in keys count as live
  in the index. It is the first code change after step 1 (§8) because it damages characters.
- **An ambiguous character link is asked once, not guessed.** A character may point at a key that
  one item holds now and another item held before a rename. Today `former-key-index` silently picks
  the current holder. That is right after an incoming item is renamed, and wrong after an existing
  item is renamed. The gate records which side was renamed, and in the second case the character
  gets the existing relink choice (`::char5e/relink-content`) once, instead of a silent guess
  (§9, Q3).

## 6. Every existing path, rewired

| path | today | after |
|---|---|---|
| Builder saves (4) | `save-destination` on grant-rows; `save-collision` on `integration` | grant-rows' gate, as an op through the commit gate |
| Builder key control | `rename-key-in-plugin`, two links | `rekey`, whole library |
| Import clash rename | `apply-key-renames` → `rename-key-in-plugin`, own source, two links | `rekey`, scoped as in §3 |
| Move onto a taken key | inline rename; no history, no repointing (`integration`, grant-rows) | `rekey`, origin source; targets moved before what points at them (§2) |
| Quarantine repair | `rekey-content-group`, no history | `rekey`, quarantined source |
| Auto-name & Restore | `sanitize-item-names` re-derives every key | stops deriving keys; renames only. The gate normalises anyway |
| Save-anyway | fixed on `integration` and grant-rows | unchanged |
| Delete (item, source, everything) | removes, no word on dependants | op through the gate; the report names what now points at nothing |
| Same-source re-import | replaces silently | op through the gate; the report says what was replaced and what now dangles (§9, Q4) |
| Export write-back | writes corrections silently | op through the gate with a one-line notice, or export stops writing back (§9, Q5) |
| Keep shared content | direct write | op through the gate |
| Load salvage | recomputed every boot, never written | committed once through the gate; #34's `mend-library` is the input (§9, Q6) |
| Toggles, abbreviation, reset | `set-plugins` | ops through the gate |

## 7. Tests that make going around it fail

- **Link table test**, generated from §2. For every entry it builds an item carrying the link, runs
  `rekey` on the target and asserts the link followed, then runs it in clash mode and asserts only
  the scoped side followed. A new entry gets its test for free. #34's probes (`d7640dd8`,
  `cd82f249`) become the regression cases for the wrong-item binding.
- **No-undeclared-link test.** It walks every content type's spec and fails on any keyword-valued
  field that names other content and is neither in §2 nor on a short, explicit "not a link" list.
  A new link field added without an entry fails the build.
- **Only-the-gate-writes test.** A source scan that fails if anything but `::library/commit` writes
  `:plugins` in app-db or the `"plugins"` slot in storage. This encodes the map's writer enumeration
  as a check, so the next writer can't skip it.
- **Invariant test over every op.** Each op runs against a fixture library (tagged, untagged,
  legacy, clashing), and the test asserts the gate's invariants hold afterwards.
- **Compatibility fixtures.** A frozen set of real old libraries and `.orcbrew` files that must load
  and import byte-for-byte equivalent (C1, C2). A file exported after the fix is checked against the
  **old** import specs (C3).
- **Two-tab e2e.** Two pages against `lein e2e-server`: save in one tab, save in the other, and both
  edits survive.

Every test is checked falsifiable by removing its fix and watching it fail.

## 8. Build order on `port/save-gate`

Each step lands green, with its tests, before the next begins:

1. Bring `port/save-gate` level: grant-rows' round six, plus #34's `054e42c1`, `ec4a3c50`,
   `d7640dd8`, `cd82f249` as inputs. The probes land first, marked as known gaps.
2. **Stop characters being rebound off built-in content** (§5): built-in keys count as live in
   `former-key-index`. Moved ahead of everything else because it damages characters.
   **Done `1d804217`.** The template watcher (`autosave_fx`, the one already mirroring
   `::char5e/template` into app-db so events never subscribe) also stores `offered-keys`: every
   key the builder can offer. `set-character` never redirects a key on that list and redirects
   nothing until it exists; the first list heals a character that loaded before it.
   **2b, done in the same commit:** the missing-content warning reads the same list instead of
   hand-kept built-in sets. Those sets were wrong: seven subclass keys that do not exist
   (`:lore`, `:life`, …; the real keys come from names, `:college-of-lore`) and five subraces the
   app does not offer (Drow, Stout, Wood Elf, Mountain Dwarf, Forest Gnome, all commented out of
   `spell_subs.cljs`). A character using one of those five now gets the warning, which is true:
   the builder cannot build it.
3. **Done `1042c746`.** The link list (§2) and its table test. `share_bundle` reads it; no behaviour change except the
   share closure growing to cover every link.
4. **Done `b5f2a1b6`.** `rekey` (§3), including the built-in holder rule, and every key-changing path moved onto it (§6, rows 2–6). The probe gaps flip to
   passing.
5. The commit gate (§4), steps 2–5, with every writer moved onto it and the only-the-gate-writes
   test.
6. Two tabs: the replay in step 1, the revision slot, and the listener, with the two-tab e2e.
7. Reads (§5): broken-link marks, languages resolved by name, ambiguous character links asked.
8. A one-time report of damage already in a library (C6), with an offered repair.

Then review against the map's invariants, land on `integration`, and pull down as `plan-next.md`
§0a orders.

## 9. Decisions for the owner

| # | question | recommendation |
|---|---|---|
| Q1 | Races link languages by display name. Should new saves also store the key? | No. Resolve by name at read time only; adding a field risks C3 for no gain |
| Q2 | Links **from other sources** during a clash rename stay with the other holder of the key. Tell the user? | Yes, one line in the rename report: "3 items in other sources still point at the other X" |
| Q3 | After an import renames an **existing** item, should characters be asked which one they meant? | Yes, once per character, through the existing relink |
| Q4 | Same-source re-import replaces entries. Keep replacing? | Keep it (it is how a pack gets updated), but the import summary says what it replaced |
| Q5 | Should export keep writing its corrections back into the library? | No. Export corrects the file only; the library is fixed through the gate when the user asks |
| Q6 | Load-time repair: adopt #34's `mend-library` field repairs, or only commit what salvage already does? | Commit salvage only in this fix. `mend-library` changes item shapes and deserves its own review |
| Q7 | Deleting an item that others point at: confirm first, naming them? | Yes. The report alone comes after the damage |

## 10. What this replaces (D17 audit)

| retired | replaced by | how |
|---|---|---|
| `key-reference-map` | §2 | D34 strike |
| `rename-key-in-plugin`, `apply-key-renames`' inner rename | `rekey` | D34 strike |
| `share_bundle` `spell-key-refs`, `language-name-refs`, `prop-language-refs`, `selection-refs` | §2 locators | D34 strike |
| inline rename in `relocate-content` | `rekey` | replaced |
| key derivation in `sanitize-item-names` | the gate's normalisation | removed; branch-local behaviour |
| direct `plugins->local-store` calls, direct `(assoc db :plugins …)` | `::library/commit` | replaced |
| read-time stamping on grant-rows | stamping in storage | kept as a cheap second line until one release proves the gate |

Nothing new duplicates an existing path. The three new pieces each absorb several old ones.

## Corrections

- **2026-09-27, the same day.** The first draft said "no migration" in C1, and a follow-up spoke of a
  "one-time migration". Both overstated it. No key is migrated. The only stored corrections are the
  two quiet fixes in §4a, saved once after a backup. C7 (overrides keep working) and the built-in
  holder rule were added after the owner asked about overriding `cleric`. That question is what
  turned up the `former-key-index` bug in §5.

## As built (steps 3–4)

- **`library_links.cljc` has fifteen entries**, not fourteen: the three spell-grant maps
  (`:paladin-spells`, `:cleric-spells`, `:warlock-spells`) are one entry each. Each also carries
  `:bundle` — `:follow`, `:reverse` (a spell's `:spell-lists`) or `:none` (a feat's race
  prerequisite, which is not something a character depends on). The locator vocabulary gained
  `:*true-keys`, so a spell whose entry for a list is `false` stays off that list, as it did.
- **`rekey` is not a new function.** `rename-key-in-plugin` was already the one rename path for the
  key control, import clashes and a renaming Move; it now repoints through the link list (D29, D17).
  `apply-key-renames` repoints across every source of the data it is given, which is the design's
  scope for both import sides. `key-reference-map` and the per-field updaters are struck (D34).
- **The built-in holder rule is applied after the change, not before.** Nothing can say "built-in
  content holds this key" while a homebrew override replaces it in the template. So the builder key
  control repoints its own source immediately and records a pending repoint; the template watcher
  settles it once the new key is offered — other sources follow only if the old key stopped
  answering (`content-recon/settle-repoints`). The pending entry lives in app-db: a reload in the few
  milliseconds before it settles leaves those links for step 8's damage report.
