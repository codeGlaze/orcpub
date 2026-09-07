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
