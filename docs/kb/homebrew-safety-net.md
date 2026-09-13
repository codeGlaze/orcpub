# Homebrew safety net

*Verified 2026-09-12 against `integration-local` at `fa57ebbc`. References are by function
name.*

## What it guarantees

Bad homebrew cannot stop the app from starting, and cannot keep a page down. A broken
entry is set aside, and that is saved, so it stays aside after a reload. Everything else
keeps working, and a notice names the entry with a link to My Content.

## Startup reads storage and builds nothing

The top-level forms in `web/cljs/orcpub/core.cljs` run outside every React error
boundary. An uncaught throw there stops the namespace, so the app is never mounted. The
page then stays on the server-rendered spinner from `index.clj`, with only the boot
rescue bar.

That is what a stored race whose `:key` was text did. `init-template-cache!` built the
whole character template at startup, and `compare` threw inside `::races5e/races`.

- **`boot-step`.** Each startup step now runs through it, which logs a failure and
  carries on.
- **Recovering `initialize-db`.** When that step throws, `set-aside-unloadable-library!`
  copies `plugins` to `plugins:corrupt` and clears the slot, but only once the copy is
  saved. It then retries without homebrew and shows a notice with a download. If startup
  fails without homebrew as well, `restore-set-aside-library!` puts the library back.
- **The template cache.** It served autosave only, and now starts on the first save:
  `ensure-template-cache!` runs from the throttled-save fx. `::char5e/save-character`
  retries 20 × 250 ms while the cache fills, then warns. Saves can come from the
  character sheet, the list, parties or the combat tracker, so the cache is not tied to a
  page.

## Sorting never throws

`common/safe-compare` orders mixed types by type rank, then by printed form. It is used
for the sorts that see homebrew keys or names: races, spells, class options, subclass
names, template options and the spell list.

## Repairs come first

`e5/mend-plugin` runs on import and on load. A healthy entry is untouched, reports no
repair, and nothing is written back. It makes these repairs:

- **A `:key` that is not a letter-first keyword** takes the key the entry is filed
  under, so characters pointing at the entry keep working.
- **`:traits` and a selection's `:options`** go through `mend-cards`:
  - text becomes a card with that name
  - anything else in the list that is not a card is dropped
  - a lone card is wrapped in a list
  - a value that is none of these is removed
- **A spell's `:spell-lists`** that is not a map is removed.

## The guard

`orcpub.dnd.e5.homebrew-guard` (cljc) and `orcpub.dnd.e5.homebrew-check` (cljs) do this
work.

**The everyday path.** `guard-entry` wraps each conversion and costs only the conversion
itself. It covers:

- `race-option`, `background-option` and `feat-option-from-cfg` in
  `template-selections`
- `subclass-option` in `class-option`
- plugin races and subraces
- `plugin-spell-lists`, per spell
- the plugin class and subclass catches, which report

**Why nothing is realized there.** Option builders return lazy sequences, so a throw
inside one surfaces later, when a page draws it, outside the guard. Fully realizing
every homebrew entry cost about 2.1 s with a 12-source library, against about 38 ms
unrealized. Realizing all of `template-selections` cost 11–15 s.

| Kind | Entries | Unrealized | Fully realized |
|---|---|---|---|
| Races | 44 | 9 ms | 1031 ms |
| Backgrounds | 61 | 11 ms | 359 ms |
| Feats | 70 | 8 ms | 163 ms |
| Classes | 2 | 10 ms | 575 ms |

**The deep check.** `deep-check` converts and fully realizes each entry, one at a time.
It uses the conversions the subscriptions register with `guard/register-conversions!`,
so `homebrew-check` never requires `spell-subs`. Pulling subscription namespaces into
the events load chain is the "content blowout on page refresh" trap described in
entity-options-architecture.md. The deep check runs in three places:

- after an import, for the sources that import changed (`store-plugins` compares each
  source with `identical?`)
- after a Restore, for that source
- from `app-error-fallback`, over all homebrew. The page is retried when something was
  set aside.

**Setting an entry aside.**

- Every report goes to `::e5/homebrew-entry-broke`. It is deduplicated per session by
  `[content-type key source]`, and the record is cleared on import and on restore.
- `::e5/set-aside-broken-homebrew` then moves the entries into `plugins:rejected`. The
  set-aside copy is saved first, and the library is saved only if that worked.
- A 60-second warning names the entries and links to My Content.

## Gotchas found on the way

- **The error-boundary retry needs `forceUpdate`.** A Reagent `create-class` component
  does not re-render on a React `setState` alone. The retry had never been exercised
  before, so it silently did nothing.
- **A subscription that threw recovers once its input changes.** Verified by putting a
  bad spell in (it throws) and removing it (it builds), with and without a watcher.
- **A sweep that swaps `:plugins` from case to case must clear re-frame's subscription
  cache each case.** Without that, one throw in a long-lived watcher showed up as 634
  false crashes.
- **Building `::char5e/template` does not realize option lists.** On a page that does not
  draw options, it calls `race-option` zero times. A check that only derefs the template
  finds nothing.

## Tests

- **`test/browser/homebrew_safety_net_e2e.js`** (16 checks) covers:
  - damage that throws at conversion, and damage that throws only while the page draws
  - the notice, the set-aside, a reload, Restore, and import

  Against the build without the net, the first 13 checks passed 1.
- **`boot_rescue_e2e.js`**, case 4b: a stored race with a text key, and the app must
  still start.
- **Unit tests:**
  - `homebrew_guard_test.cljc`
  - `events_test.cljs`: set-aside, save retries
  - `db_test.cljs`: startup set-aside
  - `e5_test.clj`: `mend-cards`, entry repairs, spell lists
- **The wrong-type sweep** is a scratch script, not committed. It puts wrong types into
  every field and wrong shapes at every level, through import, reload, template build
  and export. Isolated case by case, on the build with the entry repairs but before the
  safety net, 3 of 697 cases still crashed: a spell whose `:spell-lists` was a number, text
  or a keyword. Those are repaired now. The rerun on the finished build was cut short.

## Related

- [homebrew-fixes-persist.md](homebrew-fixes-persist.md): repairs must reach storage once
- [fail-soft-rendering.md](fail-soft-rendering.md): the error boundaries this builds on
- [entity-options-architecture.md](entity-options-architecture.md): load order, and the
  template cache
