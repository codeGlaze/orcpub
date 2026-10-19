# Writing a browser test: the checklist

Read this before writing or changing anything in `test/browser/*_e2e.js` or a screenshot probe.
Each rule below cost a session real time. They come from `feature/weapons-table` (2026-10-17 to
-19) unless marked otherwise. How the runner itself works is in [e2e-runner.md](e2e-runner.md);
keeping runs fast is in [fast-browser-probes.md](fast-browser-probes.md).

## Start from a working test

Copy `test/browser/weapons_table_e2e.js` (phone, dark and light, data swaps, menus) or
`test/browser/srd_equipment_e2e.js` (desktop, smallest). Both already load a character, open the
sheet, report `PASS`/`FAIL` lines and exit non-zero on a failure, which is what the runner needs.

## 1. Load the character with the app's own event, not by clicking the builder

Clicking through the builder costs a minute and breaks when a label changes. With
`// Needs: dev bundle` on the first line, dispatch the strict character directly (copy `load` from
either test above):

```js
re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
  [rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
```

Click through the builder only when the builder is what you are testing.

## 2. Check after the page has changed, never right after the action

A click, key or dispatch re-renders on the next frame. `innerText()` or `count()` straight after
it reads the old page and fails at random. Wait for the change you expect:

```js
const shows = (loc, text) => loc.filter({ hasText: text }).waitFor({ timeout: 5000 })
  .then(() => true, () => false);
check('Ablaze adds +2d6', await shows(tile, '+2d6'));
// gone:  loc.waitFor({ state: 'detached', timeout: 5000 })
```

This was the commonest failure on the weapons branch, six times. A check that fails once and
passes on a rerun is this, not a flaky app.

## 3. A missing control is a FAIL, never a crash

A step that throws (a 30s click timeout, `getAttribute` on nothing) ends the run with exit 2,
and every check after it never runs. One broken control then hides the rest. Give every step a
short timeout and turn a miss into a named failure:

```js
await btn.click({ timeout: 5000 }).catch(() => {});
check('it says menu', await plus.getAttribute('aria-haspopup', { timeout: 3000 }).catch(() => null) === 'menu');
```

## 4. A phone is not a narrow desktop window

The app decides phone or desktop partly from the user agent (`:device-type`, `user_agent.cljs`).
A 390px desktop context gets desktop behaviour: ctrl/shift tooltips around every roll button, and
a weapons table that overflowed by 34px when a phone's did not.

```js
browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true,
  hasTouch: true, userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ...' });
```

On a phone the sheet has no "combat" text tab: click Details, then the icon tab whose HTML holds
`sword-clash` (see `weapons_table_e2e.js`).

## 5. With no saved character, send one update at a time

On the builder's unsaved character (`id` nil), sheet events go through `update-character-fx`
(`events.cljs`), which queues a `:set-character` built from the character as it was. Two such
events back to back (wield main hand, then off hand) lose the first. Dispatch one, wait for its
effect (rule 2), then the next.

## 6. Standing in for data the branch does not have yet *(not yet proven in a run)*

To test UI that only appears with data from another line (Weapon Mastery from the 5.5 line), swap
the subscription on the dev bundle with `re_frame.core.reg_sub(...)`, then make the view
subscribe again:

- **Do not call `clear_subscription_cache_BANG_`.** It disposes the live subscriptions, so the
  view stops re-rendering at all and keeps showing the old data.
- **Remount the view instead.** The sheet keys each tab's view by tab name (`views.cljs`,
  `character-display`), so leaving the tab disposes its subscriptions and coming back builds
  fresh ones with your stand-in. Switch with the app's own event,
  `[:orcpub.dnd.e5.character/set-selected-display-tab "summary"]` then `"combat"`, and check that
  the tab really changed. Clicking "some other tab" by guesswork did not.

## 7. Run on your own port

`./scripts/e2e/run.sh` serves on 8890. Another worktree's server there makes yours refuse to
start, or worse, a test you run by hand checks someone else's code. Use
`E2E_PORT=8897 ./scripts/e2e/run.sh test/browser/<name>.js`. A script outside `scripts/e2e/`
needs that folder's Playwright: `NODE_PATH=$PWD/scripts/e2e/node_modules`.

## 8. Prove each check can fail, and keep it affordable

AGENTS.md requires every check to be seen failing first. A browser run is 3 to 4 minutes, so:

- Break the code, not the test: replace one exact string in the source with a script that
  asserts it matched once, run, and copy the file back. Confirm with `cmp` that it is restored.
- Breaks that hit different checks can share a run. Each must fail its own check by name; if a
  break also takes down other checks, run it alone.
- A break that fails nothing means the check is weak, not that the code is right. The pulse test
  "touching it stops the pulse" passed with the stop broken, because touching 2H also ends the
  pulse; it now touches the already-chosen 1H.

## 9. Screenshots for review

- Hide sticky and fixed bars before a full-page capture, or the builder header is painted across
  the middle of the image. `weapons-rows-shots.js` in `scripts/design/` does this.
- Close menus before the next click: an open menu's backdrop catches the next click outside it.
- Measure before sending (fits 390px, headings within 2px, nothing clipped, 44px tap targets),
  then look at the image yourself.
