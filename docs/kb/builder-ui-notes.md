# Builder and My Content UI notes

Why controls in the character builder, My Content, the header and the notification area look and
behave as they do, with the measurements behind them.

Related: `test-suite-state.md` (the What's New backdrop), `library-management-and-conflicts.md`;
on `agents/develop` only: `equipment-option-picker.md`, `perf-homebrew-builder-loop.md`,
`perf-entity-build.md`, `garden-inline-styles-harvest.md`.

## Boot and messages — `events.cljs`

- **The release panel's hold is bounded both ways.** Waiting for a reload means a visitor may never
  see the release; waiting for the cookie notice to go means never, for a visitor who ignores it.
- **`builder-error-message`** once opened with a "Spell:" label line; it was dropped as redundant.

## Character build — `subs.cljs` build watch

- The build watches both the character and the template. Building on the first notification paired
  the NEW character with the OLD template, and the correct result arrived only from the trailing
  rebuild 500 ms later.

## Builder views — `character_builder.cljs`, `views.cljs`

- **`inventory-picker`** replaces the native `<select>` (1037 `<option>` elements across the tab) and
  the inline option-menu grid (~700 checkboxes rendered inline). At rest the tab costs 7 buttons.
  Its query text lives in a local atom, which avoids a re-frame round trip per keystroke.
- **`fit-flyout`** exists because the My Content menu (11 rows) ran off a 720px-tall screen.
- **Sticky header**: one copy. Two copies put the PDF options panel in the DOM, and in the tab
  order, twice.
- **`update-character-fx`** (open TODO): its `:dispatch` might be replaceable with
  `{:db (set-character db (update-fn (:character db)))}`.

## Notifications — `views/notifications.cljs`

- The import banner's "Export a backup" button was removed: it duplicated Export All on My Content.
- The legacy string message shape goes away as producers move to the map shape.

## Styles — `styles/core.clj`

- **Conflict modal width.** Its 600px was inherited from a short warning. At that width "Keep
  Eberron … :quori -- rename the other source(s)" wrapped, and a list of 27 read as a wall. On a
  phone the padding is reduced: 20px a side is a fifth of the screen.
- **Header flyout z-index** also keeps an adjacent tab from intercepting the dropdown.
- **Save-healed glint**: a load-time heal lives only in memory until saved. The toast saying so is
  gone in eight seconds and leaving the page drops the fix, so the Save button glints until saved.
- **Dev-mode switch**: a labelled switch replaced two unlabelled orange icons.
- **Source row**: search once sat on its own line under the buttons, edge to edge.
