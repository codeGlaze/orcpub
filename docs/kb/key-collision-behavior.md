# Duplicate / colliding content keys — what actually happens, per layer

Answers a recurring, hard-to-pin-down question: are content keys required to be unique? The honest
answer is **it depends on the layer** — some layers treat a same-key homebrew entry as an intentional
**override** of the built-in, others let duplicates **coexist**, and import has its own **conflict
gate**. This is the map, traced from code (file:line cited) and pinned by `key_collision_test.clj`.

Markers: **VERIFIED** = read from code + test-backed. All cljs paths are in `spell_subs.cljs` /
`import_validation.cljs`.

## TL;DR
- **Top-level class / race / spell:** a homebrew entry with a **built-in's key OVERRIDES the built-in**
  (predictable, homebrew wins). This is the "override a built-in" capability some users rely on.
- **Subraces, pools, list content (backgrounds/languages/selections/monsters):** same-key entries
  **coexist** (both appear) — no override.
- **Import:** duplicate keys are **detected** (within the import + against existing) and routed to a
  conflict-resolution modal (rename / skip / replace) — the "duplicate keys won't just load" behavior.
- So keys are NOT globally unique-or-bust; uniqueness matters in different ways in different places.

## The map (VERIFIED)

| Layer | How content combines | Same-key collision |
|---|---|---|
| **Classes** | `(into (sorted-set-by ::t/key) (concat (reverse plugin-class-options) base-classes))` (`spell_subs.cljs:1016`) | **OVERRIDE** — plugin added first, the sorted set dedupes by key keeping the one already present, so the **homebrew class wins; the built-in is dropped** |
| **Races** | `(into (sorted-set-by compare-keys) (concat (reverse plugin-races) base))` (`:950`; `compare-keys` = `(compare (:key x) (:key y))`, `:936`) | **OVERRIDE** — homebrew race wins |
| **Spells** | `(into (sorted-set-by compare-keys) (concat (reverse plugin-spells) built-in))` (`:1228`); `spells-map` then reduces this deduped set into a map (`:1242`) | **OVERRIDE** — homebrew spell wins |
| **Subraces** | gathered by `mapcat` then `(update race :subraces concat (subraces-map key))` (`:161`, `:954`) | **COEXIST** — both same-key subraces appear under the race |
| **Backgrounds / Languages / Selections / Monsters / Encounters** | `(mapcat (comp vals ::e5/<type>) plugin-vals)` (`:104/:110/:116/:1087/:1093`) | **COEXIST** — a seq; duplicates both appear |
| **Pools** (draconic ancestry, fighting-style) | `(concat built-in homebrew)` (`content_pools.cljc/pool`) | **COEXIST** — both offered in the choice |
| **Spell lists** | `(merge-with merge-spell-lists …)` = `merge-with concat` (`:1277`, `:1253`) | **COEXIST** — keys merged, lists concatenated |
| **Import** | `detect-duplicate-keys` → `find-key-conflicts` (`import_validation.cljs:1120/1097`) → `:internal-conflicts` (within the import) + `:external-conflicts` (vs existing); `import-plugin` shows a resolution modal (`events.cljs:3878`) | **CAUGHT** — rename / skip / replace, not a silent merge |

## Why the override is "plugin wins" (the load-bearing semantics) — VERIFIED by test
`(into (sorted-set-by key-cmp) coll)` adds elements left-to-right; when a new element compares **equal**
(same key) to one already present, `conj` on a set is a **no-op** — the element already in the set
stays. The class/race/spell combines put `(reverse plugin-options)` **before** the built-ins in the
`concat`, so plugin entries are added first and **win** the collision. `key_collision_test.clj` pins
this: a `{::t/key :fighter}` plugin option placed before a built-in `:fighter` yields a 1-element set
containing the **plugin** one; distinct keys both survive; a plain `concat` (the pool/list shape) keeps
**both** same-key entries.

## Open: tagging every minted key with its source

A key carrying its source's abbreviation at MINT time — `:stone-elf-tc` rather than `:stone-elf` —
instead of only when a collision forces it, with deleting the tag being how an author says they mean
to override an SRD item. Decided, not built; the default source tags as `dflt`.
**`source-tagged-keys.md`.**

## Where a save lands — `save-destination` (2026-09-18)

One pure function decides, and the save handler only carries out the verdict. It takes the library,
the origin the builder recorded, the target source, the key, and the item:

| the item | the target slot | verdict |
|---|---|---|
| no key yet | free, key answers nowhere | **create** |
| no key yet | taken **here** | **refuse** — offers *Replace it* |
| no key yet | taken **in another source** | **refuse**, no offer — a key is a global address |
| came from here | — | **in place** |
| came from another source | free | **move** — write here, remove there |
| came from another source | taken | **refuse** — offers *Replace it*, which moves onto the slot |
| origin unknown, key answers nowhere | — | **create** |
| origin unknown, key answers in 2+ sources | — | **refuse** — reopen it from My Content |

**Option Source Name is an instruction.** Retyping it moves the item. Before this, the save could
only ever `assoc-in`, because it did not know where the item had been — so retyping the source left
a COPY, one key answering in two libraries, and that state then refused every later save of either
copy as a collision with its twin. Three bugs, one missing fact.

**A move does not touch the key**, so characters are unaffected and there is no `:former-keys`
breadcrumb to leave: they store the address, not the library. The source the item left is emptied,
and an emptied source drops out of My Content the same way it does when its last item is deleted.

### The origin, and why it is checked rather than trusted

`reg-edit-homebrew` records `{:source :key}` when an item is opened; `reg-new-homebrew` clears it;
the save re-stamps it to where the item now lives. It is **verified against the library before
use** — the recorded address must still answer to that key for that content type. Three reasons,
each a way the record goes stale:

- it survives the move that invalidates it (the next save would dissoc an entry already gone, and
  leave the copy it just made);
- it survives a walk to another builder, and two content types in one source share a key for one
  name (a race and a subrace both called Aarakocra), so an unverified record can name an item
  nobody opened — and the save would delete it;
- the source may have been deleted meanwhile, in which case the dissoc would resurrect it as an
  empty shell.

With no usable record the single source holding the key is the origin. With several there is no
honest answer, so the save refuses and says to reopen the item from My Content, which records one.

Pinned by `save-destination-decides-by-where-the-item-came-from`,
`save-destination-does-not-trust-a-record-the-library-contradicts`, the scenario tests around them,
and `test/e2e/move-between-sources.js` end to end.

### The content type is bound at registration (2026-09-20)

Fourth review round. The stamp reached nine of the ten item-only edit doors — **the subclass pencil
reads through a different subscription** (`::e5/plugins-with-sources`, which filters but does not
stamp), so its item still carried whatever source it declared. With the same key in two sources and
one of them disabled, editing the visible copy wrote over the invisible one and left the edited
entry untouched. That sub stamps now, and passes the row's address like My Content does.

And the gap left open twice is closed, because it turned out to be cheap: the content type a builder
edits is **statically known at registration**, so `reg-edit-homebrew` takes it as a parameter and
every record carries one without any call site supplying it. The check is strict again — a record
that names another builder's content type no longer validates.

Smaller, same round:

- The `:occupied` banner asked consent for one destructive act and performed two. Where the refusal
  carries an origin, it now reads **"Replace it, and move this one out of X"**.
- `::persist-builder-origin` ignored `set-item`'s failure. A failed write leaves the PREVIOUS value
  in the store, which comes back on the next boot beside a draft it does not describe; it clears the
  key instead. No record refuses a move, a wrong one performs the wrong move.
- `compute-plugin-vals` (`compute.cljc`) is a hand-maintained replica of `process-plugin-vals` and
  had silently diverged from it. It stamps too — a replica that drifts is how the next reader gets
  it wrong (D29).
- The selection save validated a placeholder-filled copy rather than the author's input, against the
  rule `reg-save-homebrew` states. It matters more now that the saved item is written back into the
  form, where a placeholder would look like something the author typed.

### The origin is persisted beside the draft (2026-09-20)

Closes the gap the two sections below both leaned on. `:builder-origin` lived only in app-db, so a
page refresh lost it and a restored draft could not be moved until it was reopened from My Content —
which was the entire cost of the `:unrecorded` refusal.

It is persisted now, under its own localStorage key, restored by a cofx on boot like every other
device-local value, and cleared by New. It cannot be derived from the draft: the item's
`:option-pack` is whatever the author currently has typed in the field, so after a refresh nothing
else says where the item came from.

`test/e2e/move-between-sources.js` reloads the page between opening the item and retyping its
source, and the move still works — verified by disabling the restore and watching four of its
eleven checks fail.

The refusal stays for the case where the origin is genuinely absent, which is a Move/copy from My
Content rearranging `:plugins` under an open builder. That is the one the guess would get wrong.

### The READ path stamps the address (2026-09-20)

Third review round, and it found the hole under the section below. That one says identity is
established when the builder fetches the item, and justified deleting the old name-probe on exactly
that basis. **The premise was true at one of eleven doors.** `reg-edit-homebrew` stamps only what
the caller passes, and only My Content passed it — the Spells and Monsters list pages and the
character-builder pencil (background, race, subrace, subclass, feat, invocation, boon, spell) all
dispatch the item alone. A key-less item edited from any of them minted a fresh key and **forked**:
the original left holding the pre-edit data, no `:former-keys`, nothing on screen. Precisely what
the deleted probe existed to prevent.

Fixed one level down instead of at eleven call sites: **`process-plugin-vals` stamps `:key` and
`:option-pack` onto every item on the way out of `:plugins`.** That map is the one place both are
known for certain, so no reader has to guess and no caller has to remember. It also repairs a stale
declared source for free, and it makes the delete button's fallback correct on the list pages, which
otherwise still deleted a same-keyed entry in the wrong library.

Also from that round: the **mint** branch of `save-destination` had the opposite ordering to the
move branch, so it offered *Replace it* where another library also held the key — consent that
destroys the entry here and leaves the duplicate standing. Both branches now test `:elsewhere`
first.

`:content-type` on the record is required only when present: the ten doors that pass the item alone
cannot name it, and a record without one is no weaker than it was before it existed. Threading the
content type through registration would let it be required everywhere — worth doing, not done.

### A move needs a RECORD, not a guess (2026-09-20)

Second review round, on top of the section below. Five more, and the shape is the same one twice:
**a guess is fine for a save that cannot lose anything, and never fine for one that deletes.**

- **An empty Option Source Name was a move.** `::option-pack` is `string?`, so `""` satisfies the
  save spec and never reached the missing-field banner — only the *save-anyway* path guarded it.
  Clearing the box to retype it and pressing Save deleted the item from its library and re-homed it
  under a source named `""`; the next save was a clean in-place, so nothing ever flagged it. It is a
  missing required field and now says so. The source name is also **trimmed**: `" Pak"` is not a
  second library, and treating it as one moved the item into a twin that renders identically.
- **The delete button still read identity off the item** — one line below the edit button that had
  just been fixed. For a pre-keys library it deleted nothing at all; against a stale `:option-pack`
  it conjured an empty source; and where another source answered to the same key it deleted **that**
  entry and left the clicked one in place.
- **`replacing` could leave a cross-source duplicate.** The `:occupied` offer was tested before the
  third-library check, so consent to discarding one entry still left the key answering elsewhere —
  the state the move rule exists to prevent. The `:elsewhere` refusal now comes first.
- **A record left by another builder validated.** `:builder-origin` is one slot for all fourteen
  builders, and one source holds a race and a subrace under one key for one name. The record now
  carries the content type it was made for, and every stamp site sets it.
- **A key change did not re-stamp the origin**, leaving it pointing at a key it could never verify
  again.

**And the rule that came out of it:** with no verified record, the single library holding a key
tells you where an item *lives* — enough to save back into it, never enough to MOVE, because a move
deletes that entry and the builder may be holding an item the library has moved on from. That is not
hypothetical: Move/copy in My Content rewrites `:plugins` under an open builder, and the old
behaviour silently reverted the relocation the author had just performed. A move now refuses with
*"This language is in X — open it from My Content to move it somewhere else"* (`:unrecorded`).

**Open, and the reason the refusal above exists at all:** `:builder-origin` is not persisted, so a
page refresh loses it and a restored draft cannot move until it is reopened. Persisting it alongside
the draft removes that cost entirely, and is the obvious next step.

### Identity is established when the builder FETCHES the item (2026-09-19)

**Corrects the section below.** `address-for` originally identified a key-less stored item by
probing the target source for `name-to-kw` of its name. A review pulled four bugs out of that one
decision, all of them silent:

| | |
|---|---|
| a NEW item whose name matched an untagged entry | was handed that entry's address and overwrote it in place — no banner, no offer |
| renaming a key-less item | minted a key from the NEW name and left the original behind holding the pre-edit data |
| a key-less item with a twin elsewhere | refused forever: the origin recorded `(:key item)`, which is nil, so it never validated |
| an item whose `:option-pack` went stale | a no-op edit read as a move and relocated it |

The common cause: **identity was re-derived at save time, from fields on the item.** A name matches
any entry that happens to share it. `:option-pack` is what the item *declares*, which an import
that renamed the source leaves stale. `:key` is absent on libraries authored before keys were
stored. All three are guesses, and the save only needs to ask once.

`reg-edit-homebrew` now takes the address of the ROW My Content took the item from, and **stamps it
onto the item** — `:key` and `:option-pack` both. Everything downstream then falls out: the form
shows the source that really holds it, a rename cannot re-address it, the save writes back where it
came from, and the draft carries all of it across a refresh, which `:builder-origin` alone does not
because it is not persisted. `address-for` is left with one rule — the item's own key, or a fresh
tagged mint — and a key-less item is now, reliably, a new one.

Two smaller corrections from the same review:

- **A blank Option Source Name is a missing FIELD, not an instruction to move.** "Save anyway with
  placeholders" substituted the placeholder source and handed that to `save-destination`, which read
  the change as a retarget and deleted the item from the library it lived in. It now falls back to
  the source the item came from; only an item from nowhere lands in the placeholder.
- **A move is refused when a third library also answers.** Emptying the origin does not help when
  another source already holds the key — the result is the duplicate refused everywhere else.

Pinned by six regression tests, each verified by removing its fix and watching exactly it fail.

### A stored item may have no `:key` (2026-09-18)

`:key` is OPTIONAL on a stored item — libraries authored before keys were stored do not carry one,
and the read path derives it from the NAME, untagged. Minting a tagged key for such an item on its
next save wrote a SECOND entry and left the original holding the pre-edit data, with no
`:former-keys` to heal it and nothing on screen to say so.

`address-for` resolves this once for all four save paths: the item's own key, else **the address it
is already stored under in this source**, else a freshly minted tagged one. It hands
`save-destination` the item carrying that key, so a legacy item reads as an edit in place rather
than a new item landing on a taken address — and a genuinely new item stays keyless, so the mint
refusals still fire.

The probe is scoped to the source being saved to. An untagged entry of the same name in a different
library must not capture a new item onto its address.

**This fix was made on `feat/source-tagged-keys` during review and never came back to
`feature/grant-rows`**, so the branch carried the bug for five days while the cut branch did not.
Anything found on a cut branch has to land on both. Pinned by the four
`an-old-item-*` / `*-keeps-an-old-address` tests plus two negative controls, verified by removing
the branch and watching exactly those four fail.

### Replacing on purpose, and the two refusals (2026-09-18)

A refusal with no way through is a trap, so `:occupied` asks instead of just saying no. `replacing`
is the pure re-decision: consent turns that refusal into the `:move` or `:create` it would have been.
The other two refusals pass through it unchanged, and that asymmetry is the point.

| reason | what is in the way | the banner |
|---|---|---|
| `:occupied` | ONE item, in this source | *"Tide Pak" already has a language called "Tideward."* → **Replace it** / Or rename this one. |
| `:elsewhere` | nothing here — another source holds the key | *"Tide Pak" already uses the key :tideward-tepk.* → Rename this one, or change its key. No offer |
| `:ambiguous` | two entries the save cannot tell apart | *Two sources have a language with the key …* → Open this one from My Content and save again. No offer |

**A headline and one line, and no word the author has to learn.** These fire mid-task, on somebody
who wants to get back to authoring — an explanation of why keys are global belongs behind the key
row's `?`, not in the way. Pinned by length assertions in `replace-or-refuse.js`, because copy grows
back. Words to keep out: an address "answering", an item "resolving" — internal vocabulary for what
the reader sees as a name clash.

**Consent is to discarding one named thing.** For `:elsewhere` there is nothing in the way to
replace, so a yes would not resolve the collision — it would *create* it, which is the state that
used to make both copies uneditable. For `:ambiguous` the save cannot say which of two entries the
author is looking at, and consent to an unnamed one of two is not consent. Neither gets a button.

**Replacing still moves.** Consent is to the occupant going, not to a copy being left behind: an
item that came from another source is removed there, exactly as an unobstructed move would.

**All four save paths go through this gate.** The ordinary save, its "Save anyway with
placeholders", and both selection saves — the last three used to write with a bare `assoc-in`.
`::selections5e/save-selection` had no collision check of any kind, so a selection could replace
another silently, on an ordinary save, with no banner. `save-anyway` also stamps the key back onto
the builder item now; without it the next save minted a second key and refused as a collision with
its own entry.

"Save anyway" is the **missing-fields** escape hatch, not a collision one — it is offered only from
the spec-validation branch, and placeholders fill the fields, not the address.

Pinned by `consent-only-re-decides-the-refusal-that-named-what-would-be-lost` and the eight
scenario tests after it, and by `test/e2e/replace-or-refuse.js` end to end (both banners, the
replace, and that a refusal writes nothing).

## The builder's own save gate (2026-09-12)

`save-collision` (`events.cljs`) runs before every homebrew save and blocks two cases: `:overwrite`
(the key in THIS source already holds a different item) and `:cross` (another source holds it). It
tells "an edit returning to its own slot" from "a name landing on somebody else's" by comparing the
key it is about to write with `:key` on the item open in the builder.

**Therefore the builder item must carry its key after a save.** It did not: saving stamped `:key`
into the copy written to `:plugins` and left the builder's copy bare, so pressing Save twice on one
item reported *"already uses the name … Saving would replace it"* against itself. Fixed by returning
the stamped item in `:db` and persisting it to the builder's WIP slot (`::persist-builder-wip`, keyed
off `db/builder-wip-stores` so the refresh-restored copy carries it too). Pinned by
`test/e2e/spell-builder.js` (save → add a field → save again) and `test/e2e/feat-grants.js`
(save → remove a grant row → save again).

## The builder's save: a key is minted once (2026-09-13)

`key (or (:key item) (name-to-kw name))`. The key is derived from the name when the item is
created and then fixed; editing the Name field changes the name and nothing else. This is D10 —
`name-to-kw` is a creation-time default, and re-running it on a display name is the footgun that
orphaned saved characters before.

What that removes, rather than what it adds:

- a rename cannot orphan the old entry (there is no old entry — nothing moved);
- a rename cannot collide, so it cannot be refused;
- a character holding the key keeps resolving, with nothing to heal.

**The key and the name can therefore diverge** — "Tidewall" living at `:tideward`. That is the
point: the key is an address, the name is display text. Exports show it.

Changing a key is a separate, deliberate act, and there are three ways in — all through
`rename-key-in-plugin`, all recording `:former-keys`:

| | |
|---|---|
| the builder's **key row** | `::e5/change-builder-item-key`, wired for every builder from `builder-drafts` |
| import conflict resolution | the modal's rename |
| the manual relink | `::char5e/relink-content` |

The builder's row sits **after the form**, under a hairline, and appears only once the item has a
key (an unsaved one has none yet). A key is an address the app mints, not a field an author fills
in, so it reads as a footnote to the form rather than part of it. It refuses a key any other item
already answers to, and it changes the key alone — the NAME is left as it is, because a key change
is not a rename.

It was a collapsed "Advanced" disclosure first, and that was worse: a thing visibly trying not to
be seen is a thing you look at. `.bf-meta` uses the hairline and muted label colour the builder CSS
already defines (`rgba(255,255,255,0.14)` / `rgba(255,255,255,0.55)`) and `var(--accent, …)` for the
link, rather than a bespoke grey — which is what made an earlier attempt read as unstyled.

### What the save refuses

**Only a MINT.** An item that already answers to the key it is saving to is returning to its own
slot, and the check does not run for it at all — whatever else the library holds. That matters for
a library that already has the same key in two sources (an import where someone chose "keep both"):
the duplicate is real and the health card reports it, but this save did not create it, and refusing
the save fixed nothing while trapping the item. Under mint-once the author could not even rename
their way out, because renaming no longer moves the key. Pinned by
`editing-your-own-item-works-even-when-another-source-answers-to-its-key`.


**Minting a key something else already holds — in any source.** A key is an address and the address
space is global: the combines that dedupe by key pick their winner by the hash-iteration order of
source names (see the CORRECTION below), and the ones that don't show both copies. Neither is a
state to create by pressing Save. Changing the name is a real fix, because the key has not been
minted yet.

Wanting both copies is legitimate — a published class and its playtest version — and it arrives
through **import**, where the conflict modal asks and "keep both" is something someone chose.

An item that already owns the key it is saving to is returning to its own slot, and is never
refused however crowded the rest of the library is.

#### Correction (2026-09-13)

This was briefly changed so a key held by another source was reported rather than refused, on the
strength of `save-collision`'s docstring ("informs rather than blocks") over the behaviour two
sections down this page. Reverted the same day. The docstring was the thing that was wrong, and it
now says what the combines actually do. **A duplicate key is a duplicate key; the source it sits in
does not change that.**

## The rename history — `:former-keys` (2026-09-12)

A key move is recorded on the item that moved, and `former-key-index` turns every record into
`{former → current}` so a character's stored key is rewritten on load (once; it persists on the
next save). Both writers use it: the builder's save and `rename-key-in-plugin` (import conflict
resolution and the manual relink).

It was **one slot** (`:former-key`), so a chain kept only its last link: A→B→C healed B and
stranded anyone still on A. It is now a vector, oldest first, capped at
`content-reconciliation/former-key-cap` (4):

- **the first entry is never dropped** — it is the key the item was minted under, and a character
  nobody has opened since then still points at it;
- **overflow comes out of the middle**, the links least likely to be anyone's stored key;
- **`:former-key` is still read** (`former-keys`) — every item already in a library has one — and
  is folded into the vector the next time that item is renamed.

The index's two exclusions are unchanged and do the rest: a former key claimed by more than one
item is dropped, and so is one that is some item's live key.

## Notes / boundaries
- **The import conflict-handling is recent, and its EDGE CASES are explicitly OUT OF SCOPE for this
  branch.** Significant time has already been spent circling them (partial-conflict resolution, how
  rename/skip/replace interact, etc.) without a clean answer. This doc records the *behavior* so it
  stops being re-discovered — it is **not** an invitation to re-chase the edge cases here. Leave them.
- **Within a single content-type map, keys are already unique** — the `.orcbrew` is EDN, so two entries
  with the same key in one map collapse at parse time (last-wins). `find-duplicate-keys-in-content`
  notes this (`import_validation.cljs:1056`: "Since items is a map, keys are inherently unique within
  it"). So intra-map duplicates can't survive to runtime; collisions are **cross-source** (plugin vs
  built-in, or plugin vs plugin).
- **The override is order-dependent and predictable**, but it is the *runtime combine* order
  (plugin-first), not "last loaded." Two homebrew plugins both overriding the same built-in key would
  collide with each other — that is exactly what the import conflict gate is for.
- **CORRECTION / footgun (VERIFIED by later trace):** among *plugins* (cross-source, same key), the
  winner is **NOT predictable**. The combine maps over `(vals plugins)` (`plugin-index` /
  `::e5/plugin-vals`), so the winning copy is decided by the **hash-iteration order of the
  source-name strings** — deterministic for a fixed set of source names, but arbitrary and NOT
  "last-imported" or user-controllable. "Plugin overrides built-in" is predictable; "which plugin wins
  a plugin-vs-plugin key" is effectively a coin flip. Do not build reliable override behavior on it.
- **Spell data and spell-list membership resolve differently for the same key.** A spell's class
  membership lives on the spell (`:spell-lists {class-key true}`). `::spells5e/plugin-spell-lists`
  (`spell_subs.cljs:1495`) reduces over `plugin-spells`, which is **not deduped by key**, while the
  spell itself comes from a set that **is** (`:1228`). For two spells sharing a key:
  - the key is `conj`-ed onto a class list once per copy that names that class — **duplicate
    entries in the list**;
  - membership is the **union** of every copy's `:spell-lists`, so an override can add a class but
    **cannot remove one**;
  - the spell's data is one winner, its membership is all of them.

  Pinned by `two-spells-sharing-a-key-resolve-inconsistently`
  (`homebrew_save_lifecycle_test.cljs`).
- **Design direction (see `content-tiers-and-key-resolution.md`):** the clean fix for all of the above
  is not per-type dedup but a single invariant — **≤1 *enabled* item per key** — enforced by a
  disable-based resolution (disable one side of a collision rather than relying on implicit last-wins).
  With only one enabled copy, pools stop duplicating, the spell-list union collapses to one copy, and
  the nondeterministic winner disappears.
- **NOT-TRACED here:** how a *saved character* that chose an overridden key resolves after the override
  changes (it should resolve to whatever now holds that key — flagged, not yet tested).
- This is the tooling that was missing for "where do duplicate-key problems come from": the answer is
  layer-specific, and now it's one table + a test instead of guesswork.
