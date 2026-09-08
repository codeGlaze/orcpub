# Duplicate keys: why they keep coming back, and the plan to stop them

Status: roadmap, nothing built. Supersedes nothing; extends
`RECONCILIATION-LOG.md` phase 3 and `name-to-kw-audit.md` section 6.

## The problem, stated properly

Import conflict resolution works. It finds duplicate keys, renames them, rewrites
cross-references, and handles the internal (within one import) and external
(against existing content) cases. That machinery is shipped and correct.

The renames do not last. `reg-save-homebrew` re-derives the key from the name on
every save and ignores the key the item already carries:

    key           (common/name-to-kw name)
    item-with-key (assoc normalized-item :key key)
    new-plugins   (assoc-in plugins [option-pack plugin-key key] item-with-key)

Replaying those lines against an item that conflict resolution had renamed:

    stored key before   : :artificer-2
    derived key on save : :artificer
    keys AFTER save     : (:artificer :artificer-2)
    rename reverted?    : true
    orphan left behind? : true

So editing a renamed item and saving it -- changing nothing -- reverts the key
AND leaves the old entry behind. One item becomes two and the collision returns.
`sanitize-item-names` does the same thing in the repair paths, and it runs over
every item in a source, so one Fix and Restore can undo a whole library's worth of
conflict resolution.

That is why a library gets de-duplicated and is dirty again weeks later. Nobody
did anything wrong; the fix was never durable.

## The invariant to establish

For homebrew items, make this true and keep it true:

    :key == name-to-kw(:name)

The disambiguation goes into the NAME, and the key is derived from the
disambiguated name rather than minted separately. Re-derivation then reproduces
the same key instead of reverting, and the entire class of bug above becomes
impossible rather than guarded against.

This does not conflict with the architectural pivot in `RECONCILIATION-LOG.md`
("identity must flow from class-key, never from :name"). Consumers still never
re-derive identity from a display string. The only code deriving a key from a
name is the editor's own save path, and under this invariant that derivation is
a no-op.

## The layers

Each layer removes work from the next, so the common case stays silent and a
person is asked only where a person actually has to decide.

**A. Abbreviation into both :key and :name** -- at import-conflict time.
`RECONCILIATION-LOG.md` phase 3, designed and never built. Compute a source
abbreviation and append it to the name, then derive the key from that name. Not
two functions that agree; one derived from the other, so round-tripping holds by
construction.

**B. Ask before the save path overrides a key** -- in `reg-save-homebrew`.
With A in place, derived and stored agree and B stays quiet. It fires only when
someone genuinely edits a name, which is exactly when a person should be asked,
because per `name-to-kw-audit.md` section 6 a key VALUE change orphans the stored
`::strict/key` in every saved character that used it. Default to keeping the
existing key. Changing it is the deliberate, warned choice.

**C. Duplicate check on every save, without exception.**
Catches what import-time resolution cannot: duplicates created later by editing.
Offers the two resolutions the user already knows from import -- turn one off
(the four-level disable hierarchy) or fix it (the conflict-resolution modal) --
so the builder and the importer stop describing the same problem in different
words.

**Free renaming while nothing references the item.**
A brand new item has no stored `::strict/key` pointing at it, so changing its key
costs nothing. Renaming stays free for the whole creation session rather than
only the first mint, because otherwise someone is punished for naming a thing
before they have finished thinking about it.

Two things this must not be mistaken for:

- Free means do not ASK. It does not mean skip the bookkeeping. A rename still
  has to MOVE the entry, not copy it, or a single session accumulates the exact
  orphans described above: create, save, rename, save, and both keys are present.
  `HOMEBREW_B2_QUARANTINE_PLAN.md` already states the rule -- move the item to
  the new key, drop the old.
- The gate is "nothing references it yet", not "still in a builder view". An
  existing item opened for editing is never free however long you sit there.

Whether a key is referenced is answerable on the client with what already exists:
the character list is pulled with `d/pull-many db '[*]` (`routes.clj:1506`) so
full option trees are already present, and `extract-content-keys`
(`content_reconciliation.cljs:97`) already walks one character's tree. The
referenced set is the union across loaded characters.

Its limits, which decide the fallback: it sees only the CURRENT USER's characters,
so a party member's or another user's character is invisible to it; and the list
has to actually be loaded, which it is not if someone deep-links into a builder.
When the answer is unavailable, assume referenced and ask.

## Build order

The order is a constraint, not a preference.

1. **A first.** Until re-save reproduces the disambiguated key, any bulk cleanup
   is Sisyphean -- the same conflicts return as items get touched.
2. **Reconciler updates and the relink UI next**, before B ever offers to change
   a key. Both are designed and unbuilt in `RECONCILIATION-LOG.md`. A key change
   orphans character selections, and the builder resets downstream choices when a
   class changes, so an orphaned class is expensive rather than cosmetic.
3. **B and C** once there is a way to repair what they permit.
4. **Bulk cleanup of existing libraries last**, with a dry run that reports what
   would change and which characters reference each key before anything is
   written. Libraries in the wild carry 60 or more conflicts in pairs, triplets
   and larger groups.

## Prerequisite

`name-to-kw-audit.md` section 6 carries an explicit `[UNVERIFIED]`: whether
reconciliation catches or repairs options orphaned by a deliberate key VALUE
change has never been confirmed, and it says this should be tested before any
work that renames keys. That test gates step 2 and is small. Do it first.

## Open questions

- **Abbreviation format against `name-to-kw`.** A's value is that re-derivation
  reproduces the key exactly, so the format has to survive the round trip.
  Measured: `name-to-kw "Artificer (UA)"` yields `:artificer-ua-`, with a
  trailing dash. Parenthesised suffixes need either a different shape or a
  deliberate decision to live with it.
- **Whether to store the abbreviation** as its own field, left undecided in
  `RECONCILIATION-LOG.md`. If the key is derived from the name it may not be
  needed.
- **Scope of C's duplicate check**: the whole library is the honest answer for a
  60-conflict pak, but it runs on every save. The memoized library-health index
  built for My Content is the obvious place to hang it.

## Attempted 2026-09-07: trimming name-to-kw. Reverted. Read this first.

The trailing dash is real and worth fixing -- measured on two shipped paks, 247 of
661 keys in MegaPak Unearthed Arcana and 257 of 1320 in MegaPak WotC Books carry
one, almost all from parenthesised qualifiers ("Eladrin (Cha)" -> :eladrin-cha-).
Seven names in the WotC pak also carry literal trailing spaces ("Samurai ").

Changing `name-to-kw` to trim was attempted and reverted. Three things it broke,
each worth knowing before anyone tries again.

**The leading dash is a guard, not noise.** `name-to-kw` deliberately does not
sanitise a leading non-letter so that the derived keyword fails
`keyword-starts-with-letter?` and the keyword-trap machinery catches the item.
`e5_test/name-to-kw-does-not-sanitise-leading-non-letter` says so in its name.
Trimming the lead, and trimming `starts-with-letter?` to match, flipped 13
assertions in `tricky-names-are-rejected-everywhere` from reject to accept. It
silently disabled a validation gate.

**Trimming a trailing dash can empty the string.** "@@@" reduces to "-", and
stripping that leaves "", which trips the blank-name placeholder and yields
`:unnamed-<hash>` -- which starts with a letter and so PASSES the trap check.
Any future attempt needs a guard for the all-separator case.

**Trailing-only still orphans saved characters.** This is the one that matters.
`warlock_test.cljc` holds a raw entity for a level 10 Drow Warlock:

    {:subrace {:orcpub.entity/key :dark-elf-drow-}}

A saved character storing the trailing dash. With the derivation trimmed the
subrace stops resolving: `subrace` returns nil and the Drow +1 drops CHA from 16
to 15. That is `name-to-kw-audit.md` section 6 demonstrated rather than argued,
and it applies to every character that ever selected one of those 247 keys.

So the trim is not a hotfix. It needs the compatibility layer below first.

## The shim that unblocks it (user's design, not built)

Rather than migrating stored keys, resolve them. On a lookup MISS -- never on the
happy path -- retry the match with the key normalised, and accept it only when
exactly one candidate matches. Then `:dark-elf-drow-` continues to resolve after
the derivation changes, and nothing stored has to be rewritten.

Properties that make this the right shape:

- Costs nothing normally; it only fires where the result today is already nil.
- Cannot create a false match, because it only resolves what is already broken.
- Self-healing: the reconciler already has this shape
  (`reconcile-spell-selection-keys` auto-rebinds unambiguous orphans and reports
  `:rewrote`), so a rebind can be persisted on the next save.
- It inverts the ordering problem. With the shim in place the derivation change
  orphans nothing, so the shim is the prerequisite for the trim rather than the
  trim needing a migration.

Two constraints on it:

- **Normalise exactly what the derivation trims, no more.** Ignoring all dashes
  would make `:fire-bolt` and `:firebolt` equivalent and match content that was
  never related.
- **It is permanent, not transitional.** A character nobody opens again is never
  re-saved, so its old key never syncs. Build it as a compatibility layer that
  stays, not a bridge that gets removed.

Where it goes is not yet established. Matching is exact key equality in several
places -- `get-modifiers` (entity.cljc:583) filters `selection-options` directly,
while `make-template-option-map` (entity.cljc:606, single caller at 626) builds a
`[path.. key]` map. Aliasing the map alone would not cover the filter. Trace every
consumer of a stored `::entity/key` before claiming a single seam.

`warlock_test.cljc` is the ready-made test: `:dark-elf-drow-` must resolve to the
Drow subrace and give CHA 16 both before and after the derivation changes.

## Where trimming belongs, and where it does not

Trimming a NAME and changing a KEY are different acts. A name is display text; a
key is identity that saved characters point at. The sites differ accordingly:

| site | name | key |
| --- | --- | --- |
| creation | trim | derive clean -- free, nothing references it yet |
| editing | trim | keep, unless the user consents |
| import | trim | leave as the file has them; the shim absorbs mismatches |
| export | leave | leave -- see below |
| lookup miss | n/a | normalised match |

Export stays faithful on purpose. A file whose keys differ from the library it
came from does not round-trip: re-importing it produces content that no longer
matches the user's own, which manufactures the duplicates this whole document is
about. If names are trimmed at save there is nothing left to trim on the way out.

The rule underneath all of it: **a key may change freely only while nothing
references it.** Creation satisfies that by definition. Nothing else does.

## Fixing characters forward: lazy, with an eager count

Two ways to repair characters after a key changes.

**Eager** -- at edit time, find every affected character and rewrite it. N server
writes from one edit, partial-failure states if it dies halfway, and it reaches
only characters the client can see.

**Lazy** -- `reconcile-spell-selection-keys` already rebinds on `:set-character`
and persists on the next save. No bulk write, no partial failure, and it catches
characters that were not visible when the edit happened.

Lazy wins and is already built. The eager scan still earns its place, but as the
COUNT rather than the repair: the character list is pulled with `[*]` so full
option trees are client-side, and `extract-content-keys` already walks one, so
"how many of my characters use this key" is a cheap read. That makes layer B's
prompt concrete:

    This is saved as :artificer-2. Renaming the key will affect 3 of your
    characters -- they'll be updated next time you open them.

A person can act on that. The abstract version -- "this may affect saved
characters" -- is not a decision, it is a warning nobody can price.

What this does NOT do is retire the shim. The count and the rebind both see only
the CURRENT USER's characters; a party member's, or another user's referencing
shared content, are invisible. Forward-fixing reduces what the shim carries. It
never lets us delete it.

## Built 2026-09-07 (integration 0790b3cd)

The shim and the trim shipped together, shim first.

- `common/canonical-key` -- reduces a key to the form derived today, trailing
  separator only. Leading dash preserved (it is the trap detector); an
  all-separator key preserved (stripping it empties into the blank placeholder,
  which starts with a letter and would pass the trap check).
- `entity/index-matching-key` -- exact match across the whole collection, then
  canonical, and only when exactly ONE candidate matches. Applied at the five
  `keep-indexed` matchers and `get-modifiers`.
- `collect-modifiers-2` -- resolves by map lookup, which cannot fall back, so the
  LOOKUP canonicalises the stored key. Canonicalising the template would be a
  no-op; its keys are already current.
- `name-to-kw` -- drops a trailing separator, guarded as above.

`warlock_test` is the standing proof: a saved level 10 Drow storing
`:dark-elf-drow-` still resolves after the derivation changed, keeping CHA 16.
490 tests, 0 failures.

### What the paks turned out to contain

The seven "merge" groups are not accidental duplicates. They are playtest and
published versions of the same subrace:

    "Human, Mark of Passage"    Eberron - Rising from the Last War
    "Human (Mark of Passage)"   UA - Dragonmarks

Kept apart only by comma versus parenthesis, which is what gave one a trailing
dash. Per the user, curators may well have leaned on that quirk deliberately --
the app gave them no explicit way to say "this is the UA one", so they encoded
source identity in punctuation. Those curators have moved on and the app has
changed underneath the trick, so preserving it is not a constraint worth carrying.

Two things follow. The conflict detector has never seen these pairs, because it
matches exact keys and these differ by one character -- so any "N conflicts"
figure for a large pak UNDERCOUNTS, and near-collisions hide beneath it. And this
is the strongest argument yet for A: people resorted to punctuation because there
was no mechanism for deliberate source distinction. A is that mechanism.

### Residual, accepted knowingly

Those seven pairs stay distinct on import, where file keys are used as they are.
Re-saving either one now derives the same key as its twin. There is no duplicate
check at save yet, so that collision would be silent.

That makes **C the next piece**, ahead of A: a duplicate check at save, reading
the memoized `collision-twin-index` the My Content health card already computes.
It closes this residual and is the smaller build.

## Built 2026-09-07 (integration 4b61904b): layer C

`save-collision` in events.cljs, checked before the write:

- same source, key held by a DIFFERENT item -> blocked, `:name` flagged, message
  names the entry that would have been lost. `:key` on the item distinguishes an
  edit returning to its own slot from a rename landing on an occupied one.
- another source -> saved with a note; both copies survive and the disable
  hierarchy decides which is live.

This closes the residual the trim left: the seven Eberron/UA pairs now derive the
same key, so re-saving either was a silent overwrite and is now a question.

Tested in `events-test` (CLJS runner, not `lein test` -- `save-collision` is a pure
function over the plugins map and is called directly): save over yourself, replace
a different item, new item onto an occupied key, other source, free key.

## Remaining

1. **A -- source abbreviation into `:key` and `:name`.** The only piece that gives
   genuinely different content deliberately different identity rather than relying
   on punctuation. Still designed and unbuilt (`RECONCILIATION-LOG.md` phase 3).
   Settle the format against `name-to-kw` first: with the trim in place
   "Artificer (UA)" now derives `:artificer-ua` cleanly, so the round trip that
   blocked this is no longer blocked.
2. **Reconciler updates + relink UI.** Designed, unbuilt. Needed before any bulk
   cleanup, since the builder resets downstream choices when a class changes.
3. **The `[UNVERIFIED]` reconciliation test** from `name-to-kw-audit.md` section 6.
   Still the gate on anything that renames keys in bulk.
4. **Bulk cleanup of existing libraries**, last, with a dry run reporting which
   characters reference each key.
