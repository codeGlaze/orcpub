# The homebrew reference web: what points at what, and who keeps it intact

Homebrew items point at other items: a subclass names its class, a spell names the class lists it
belongs to, a class names the custom selections its levels grant. Anything that changes, removes or
moves an item has to know every one of those links, or it leaves the others pointing at nothing, with
no error and no warning.

This page is the complete map, checked against `integration-local` at `ec4a3c50` (September 2026),
and **executed** by `test/cljs/orcpub/dnd/e5/reference_web_test.cljs` (`d7640dd8`): one probe per
link for whether a rename carries it, and probes that the disputed links are actually read.
Symbols are named rather than line numbers, because those move. Read it before touching rename,
relocation (move/copy), delete, disable, import conflict resolution or share bundling.

---

## 1. Why this page exists

It took four review rounds on PR #34 to see the shape of the problem, one link at a time:

1. Moving a class into a source that already held its key renamed it, but left the source's
   subclasses on the old key, where they joined the other source's class. Fixed by routing the move
   through `rename-key-in-plugin`.
2. A bulk move could carry a subclass across before its class was renamed, because the selection is a
   set. Fixed by moving classes and races first.
3. Asked "what else points at things?", the answer was: at least ten more link kinds that nothing
   repoints, and one of them (spells to classes, classes to spells) forms a cycle, so the
   parents-first ordering from step 2 cannot be extended to cover it.

Each fix was correct for the links it knew about. The failure was that no single place listed the
links, so every consumer carried its own partial list. That is what this page replaces.

---

## 2. The links

"Matched by" matters as much as the target: a link matched by **key** breaks when the target's key
changes; a link matched by **name** breaks when the target's name changes, which a clash-driven rename
also does (`"Artificer"` becomes `"Artificer (KsTy)"`).

### Between homebrew items

| Holder | Field and shape | Points at | Matched by | Read by |
|---|---|---|---|---|
| subclass | `:class` keyword | class | key | `subclass-option`, `make-levels`, `class-binding-report` |
| subrace | `:race` keyword | race | key | `race-option` subrace grouping |
| spell | `:spell-lists {class-key true}` | class | key | `plugin-spell-lists` (spell_subs) |
| class | `[:spellcasting :spell-list-kw]` keyword | class (borrowed list) | key | `spells-known-selections`: `(spell-lists (or spell-list-kw class-key))` |
| class | `[:spellcasting :spell-list] {level #{spell-key}}` | spells | key | `spellcasting-template` |
| subclass | `:paladin-spells` / `:cleric-spells` / `:warlock-spells` `{level {i spell-key}}` | spells | key | `make-levels` |
| class, subclass, race, subrace | `:level-modifiers [{:type :spell :value {:key k}}]` | spell | key | `level-modifier` (spell_subs); the builder offers "Spell" as a modifier type |
| race, subrace | `:spells [{:level n :value {:key k :ability a}}]` | spell | key | `spell-modifiers` (spell_subs), for every plugin race and subrace; written by `set-race-spell-value` / `set-subrace-spell-value` |
| class, subclass | `:level-selections [{:type selection-key}]` | custom selection | key | `level-selection` via `::selections5e/selection-map` |
| feat | `[:path-prereqs :race race-key]` | race | key, **then name** | `feat-prereqs` looks the key up in `race-map` for its name; `race-prereq` compares that name to the character's race |
| feat, race, subrace, class, subclass | `[:props :language {lang-key true}]` | language | key | `plugin-modifiers`, the `:language` case of `make-feat-modifiers` |
| race | `:languages #{"Name"}` | language | **name** | `race-option`: `(modifiers/language (common/name-to-kw language))` |
| encounter | `:creatures [{:creature {:monster k}}]` | monster | key | combat tracker via `::monsters/monster-map` |

Spell membership is the one link held on the far end: a homebrew spell says which classes it belongs
to, the class does not list it (built-in spells are the reverse, see section 3).

### From outside the plugin map

| Holder | What it stores | Points at | Matched by | Repaired on rename? |
|---|---|---|---|---|
| character | `::entity/key` values in the options tree: race, subrace, background, class, subclass, feats, and every nested choice | any content | key | Yes, when the old key is on the item as a former key and nothing else still owns it (`reconcile-former-keys`) |
| character | spell choices, under a selection named `<class-key>-spells-known-N` / `-cantrips-known` (`spell-selection-key`) | class, spell | key, **in the selection name** | Not by the former-key walk, which rewrites values and deliberately leaves map keys alone. `reconcile-spell-selection-keys` heals a narrow historical case by suffix match. |
| character | equipped custom items by `::entity/key` | server-side custom item | key **derived from the item's name** (`add-key`) | No. Renaming a custom item changes its key. |
| share link, party row | a point-in-time bundle of the homebrew the character needs (`extract-bundle`) | any content | key | Not applicable: a snapshot, rebuilt when the link is made |

### What is NOT a link (checked)

- **Warlock invocations and pact boons** hold only name, key and source; the warlock options pull all
  of them in by type. Nothing points at a particular one.
- **Custom selection options** are name and description only.
- **Backgrounds**: language grants are counts, tool and vehicle proficiencies come from fixed built-in
  lists.
- **Feat `:props :language-choice`** is a count over all languages, not a link to one.
- **Editing a name in a builder** does not change the key. `reg-save-homebrew` mints the key once, from
  the name at creation, and keeps it: "The key is an address, not a label." Only import conflict
  resolution, relocation and the manual relink change keys, and those record `:former-keys`.

---

## 3. Spell lists, in full

The part that has confused agents before, because each of five docs held one piece of it.

A class's spell list is the union of up to three sources, assembled into one
`{class-key {level [spell-keys]}}` map (`::spells5e/spell-lists`):

1. **Built-in lists**, `spell_lists.cljc` (`sl5e/spell-lists`). Built-in spells carry no list
   membership of their own; it lives here.
2. **Homebrew spells declaring membership**, `:spell-lists {class-key true}` on the spell, folded in by
   `plugin-spell-lists`. The spell builder only offers the eight built-in caster classes as
   checkboxes, so through the UI this only ever adds to built-in lists.
3. **A homebrew class's own list**, `[:spellcasting :spell-list] {level #{spell-key}}`, registered by
   `spellcasting-template` under the class's own key.

A homebrew class can instead **borrow** another class's list with `[:spellcasting :spell-list-kw]`;
`spells-known-selections` reads `(spell-lists (or spell-list-kw class-key))`.

What a character stores: each chosen spell sits under a selection whose name is built from the class
key and level (`spell-selection-key`: `:artificer-spells-known-2`, `:artificer-cantrips-known`), holding
`{::entity/key <spell-key>}`. The class key is therefore baked into a map key, which is why a class
rename strands a character's spell choices even when the class itself rebinds.

### The subclass path, and a correction

`subclass-option` passes `:class-key (or (:spell-list spellcasting) kw)`, where `kw` is the
**subclass's** key. `homebrew-class-spellcasting.md` attributed this to `class-option`; it is only in
`subclass-option`, and `class-option` passes the class's own keyword.

A subclass's `:spell-list` was designed as a **class keyword** ("casts from the wizard list"): the
event that wrote it, `::class5e/set-spell-list`, did `(assoc-in subclass [:spellcasting :spell-list]
class-kw)` and is now `#_`-discarded as never dispatched. Nothing in the UI writes the field today.

Two consequences, both read from code and not run:

- The path is reachable only from a hand-authored file: a subclass of a **homebrew** class. The
  builder gates subclass spellcasting to built-in parents (see `decision-vocabulary.md`, "subclass
  spellcasting IS gated"), and plugin subclasses of built-in classes compile through `make-levels`
  instead, which gates `:spellcasting` to fighter and rogue.
- Hand-authored, both shapes misbehave there. As a map, `spell-selection-key` calls `(name class-key)`
  on it and throws, for cantrips and leveled spells alike. As a keyword, `spellcasting-template` sees a
  truthy `spell-list` and does `(assoc spell-lists class-key spell-list)`, replacing the borrowed
  class's list with the keyword itself.

### The cycle

Spells point at classes (`:spell-lists`) and classes point at spells (`:spell-list`). No ordering of a
batch puts every target before everything that points at it, so any operation that moves or renames
several items at once has to repoint across the whole batch after the fact, not rely on order.

---

## 4. Who walks the web, and what each one misses

| Consumer | What it does | Links it knows |
|---|---|---|
| `key-reference-map` → `rename-key-in-plugin`, `apply-key-renames` | import conflict rename | subclass→class, subrace→race only |
| `relocate-content` | move/copy between sources | the same two, via `rename-key-in-plugin`, and only inside the item's own source |
| `share_bundle` `outgoing-refs` / `closure` / `add-reverse-spell-lists` | what a shared character needs | subclass→class, subrace→race, spell grants on subclasses and level modifiers, `:level-selections`, `:props :language`, race `:languages` by name, spell membership. Misses `:spell-list-kw`, a class's own `:spell-list`, feat race prerequisites, encounters→monsters |
| `reg-delete-homebrew`, `::e5/delete-plugin` | delete an item or a whole source | **none**: a plain `dissoc` |
| `::e5/toggle-plugin`, `::e5/toggle-plugin-item` | disable a source or item | none; twins (same key across sources) are handled, dependents are not |
| `reconcile-former-keys` | rebind a character after a rename | any `::entity/key` value; never map keys (spell selections), never item-to-item links |
| `generate-missing-content-report` (missing-content banner) | tell a player what their character lost | class, subclass, race, subrace, background, feat. Not spells, languages, selections, invocations, boons |
| `class-binding-report` | report a class or subclass that failed to bind | subclass→class only; reports, does not repair |

`key-reference-map` and `share_bundle`'s `outgoing-refs` are two independent models of the same graph,
and they already disagree. It is the same failure `documentation-discipline.md` records for the old
topic-index generator and its gate: two definitions of one corpus drift silently. **The fix is one
shared model that both import.**

---

## 5. Gaps, most harmful first

1. **Delete checks nothing.** Deleting a class, spell, race or selection strands everything that
   pointed at it, silently. It is the most common destructive action in the app.
2. **Rename and relocation know two of thirteen item-to-item links**, proven: eleven rename probes
   are pinned as GAP in `reference_web_test`. Every other link is left on the old
   key.
3. **The missing-content banner covers six content types**, so most stranded links never surface to
   the player.
4. **Name-matched links**, race `:languages` and feat race prerequisites, break on a clash-driven
   rename even though the key-matched links would be repaired, because the rename changes the name
   too. A stranded feat race prerequisite does not ungate the feat: the stale key looks up no name,
   so `race-prereq` matches `#{nil}` and the feat locks for every character with a race, labelled
   `" Only"` (tested).
5. **Custom items are keyed by their names**, so renaming one strands it on every character that
   equipped it.
6. **Monsters in encounters** have no repair and no banner.
7. **Disabling a class leaves its subclasses in other sources enabled**; `class-binding-report` shows it
   afterwards as an unbound class, nothing prevents it.
8. **Share bundles can omit content** reached only through `:spell-list-kw`, a class's own list, a feat
   race prerequisite or an encounter.

---

## 6. How this was established, and what was rejected

Four read-only sweeps (spells; selections, feats, races, backgrounds; characters, encounters, items,
shares; every consumer), each asked for write and read sites per link. Every row above was then
re-read in code before it was written here. Rejected on re-reading:

- **"`[:props :language]` has no consumer for subraces."** Wrong: `spell_subs` passes `:props` through
  `plugin-modifiers` for races, subraces, classes and subclasses, and `make-feat-modifiers` has a
  `:language` case.
- **An earlier sweep audited `/home/codeglaze/projects/orcpub` (develop)** after deciding the given
  checkout "did not exist". Every later sweep was made to run `pwd` first and stop on a mismatch. When
  delegating, give the absolute path and make that the agent's first step.

- **"Race and subrace `:spells` is legacy: nothing writes or reads it."** An earlier version of this
  page said so, from a sweep. Wrong on both halves: the race builder writes it and `spell-modifiers`
  reads it. Caught by `a-race-spell-grant-is-read`, which is why every row now has a probe.

### What is proven, and what is only read

Executed (`reference_web_test`): all thirteen item-to-item links under a rename (two follow, eleven
stranded); spell membership joining the named class list; race `:props :language` and race `:spells`
being read; the feat race prerequisite's lookup and its stranded outcome.

Only read, not executed: everything in "From outside the plugin map", the consumer table in section 4
apart from rename, delete's plain `dissoc`, the missing-content banner's scope, and the subclass
`:spell-list` failures in section 3.

Not yet run, only read: the two subclass `:spell-list` failures in section 3, and whether
`reconcile-spell-selection-keys` rescues spell choices after a clash-driven class rename.
