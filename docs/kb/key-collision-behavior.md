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
