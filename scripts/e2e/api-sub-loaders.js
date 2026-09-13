// Do the five API-backed subscriptions still load the same way?
//
//   ./scripts/e2e/run.sh api-sub-loaders.js
//
// P5 of the #669 branch moves ::mi5e/custom-items, ::char5e/characters,
// ::party5e/parties, ::folder5e/folders and :user onto one reg-api-sub HOF.
// Nothing in either test suite covers four of those five, and a refactor's
// failure mode is a SILENT behaviour change -- so this is a DIFFERENTIAL probe,
// not a pass/fail one. Run it on integration, run it on the merge, diff the
// TRACE blocks. Identical trace = the refactor changed nothing observable.
//
// What it watches: every request to the five endpoints -- method, path, whether
// it carried an Authorization header, and its status. Those four facts are
// exactly what the HOF took over from the five hand-written sites.
//
// It also asserts RESPONSE handling, which the first version could not: the
// seeded accounts own items, so a 200 now carries a body and the list is the
// evidence that :set-event -> db-population still works. And it asserts
// ISOLATION -- signing in as one account must not surface the other's items.
// Run it as both:  E2E_USER=kaylee (default)  and  E2E_USER=zoe E2E_PASS=washburne7
//
// SELFTEST=1 logs in with a wrong password. The endpoints must then NOT be
// requested at all, because every one of them is guarded on the auth token.
const { chromium } = require('playwright');
const fs = require('fs');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const EXECUTABLE = process.env.E2E_CHROMIUM
  || ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
      '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
const USER = process.env.E2E_USER || 'kaylee';
const PASS = process.env.SELFTEST ? 'wrong-on-purpose' : (process.env.E2E_PASS || 'serenity99');

// dev/e2e_boot.clj seeds two accounts, each owning its own items. One account
// cannot show isolation: a query returning EVERYBODY's items would look exactly
// like a correct one. These are the names it seeds.
const OWNED = { kaylee: ['Kaylee Seeded Alpha', 'Kaylee Seeded Beta', 'Kaylee Seeded Gamma'],
                zoe:    ['Zoe Seeded Only'] };
const OTHERS = Object.entries(OWNED).filter(([u]) => u !== USER).flatMap(([, v]) => v);

// The five subs' endpoints. There is no /api/ prefix -- the route tree is rooted
// at "/", so the API lives at /dnd/5e/... and the SPA pages at /pages/dnd/5e/...
// The discriminator is therefore the ABSENCE of /pages/. A tail-only match
// counted an HTML page load as an unauthenticated API call; the control run
// caught that, which is the point of having one.
const isApi = url => !/\/pages\//.test(new URL(url).pathname);
const WATCH = {
  items:      /\/items(\?|$)/,
  characters: /character-summaries(\?|$)/,
  parties:    /\/parties(\?|$)/,
  folders:    /\/folders(\?|$)/,
  user:       /\/user(\?|$)/,
};

let failures = 0;
const check = (n, ok, d) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${n}${d ? '  — ' + d : ''}`); if (!ok) failures++; };

(async () => {
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026'));
    } catch (e) {}
  });
  const page = await ctx.newPage();

  const hits = [];      // {name, method, auth, status}
  let itemsBody = '';   // raw body of the items response, to tell a server-side
                        // miss from a render-side one when the list looks empty
  const pageErrors = [];
  page.on('pageerror', e => pageErrors.push(e.message));
  page.on('response', async res => {
    const url = res.url();
    if (!isApi(url)) return;
    for (const [name, re] of Object.entries(WATCH)) {
      if (!re.test(url)) continue;
      const req = res.request();
      hits.push({ name, method: req.method(),
                  auth: !!(req.headers()['authorization']), status: res.status() });
      if (name === 'items' && res.status() === 200) {
        try { itemsBody += await res.text(); } catch (e) {}
      }
      break;
    }
  });

  // Log in.
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill(USER);
  await page.locator('input').nth(1).fill(PASS);
  await page.locator('button.form-button').first().click();
  await page.waitForTimeout(6000);

  const loggedIn = await page.evaluate(() => {
    try { return !!localStorage.getItem('user'); } catch (e) { return false; }
  });
  console.log(`\nsigned in: ${loggedIn}${process.env.SELFTEST ? ' (SELFTEST: expected false)' : ''}`);

  // Visit the pages that mount the five subs.
  for (const path of ['/pages/dnd/5e/magic-items',
                      '/pages/dnd/5e/characters',
                      '/pages/dnd/5e/parties']) {
    await page.goto(BASE + path, { waitUntil: 'load', timeout: 120000 });
    await page.waitForTimeout(7000);
  }

  // --- response handling + isolation -------------------------------------
  // The first version of this probe ran against an account with NO content, so
  // every 200 carried an empty body and the :set-event -> db-population path
  // never ran. That is exactly where a wrong :db-key hides. With seeded content
  // the list itself is the assertion.
  // /my-content is the HOMEBREW SOURCES page (orcbrew import, quarantine panel).
  // Custom magic items render on /magic-items -- route dnd-e5-item-list-page-route,
  // the page whose list is ::char5e/filtered-items, which is the chain P1 fixes.
  // Asserting against my-content reported "not rendered" for items that were in
  // the API response the whole time.
  await page.goto(BASE + '/pages/dnd/5e/magic-items', { waitUntil: 'load', timeout: 120000 });
  await page.waitForTimeout(9000);
  const listed = await page.evaluate(() => document.body.innerText);

  // The loading overlay is a COUNTER, not a boolean (reframe-subscription-patterns.md).
  // P5 adds an increment/decrement pair to :user that did not exist before, so the
  // thing to assert is that it still settles.
  const overlayVisible = await page.evaluate(() => {
    const el = document.querySelector('.loading-overlay, [class*=loading]');
    if (!el) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && el.offsetParent !== null;
  });

  console.log('\nchecks:');
  if (process.env.SELFTEST) {
    check('no endpoint was requested while unauthenticated', hits.length === 0,
          `${hits.length} request(s): ${[...new Set(hits.map(h => h.name))].join(',')}`);
  } else {
    for (const name of Object.keys(WATCH)) {
      const got = hits.filter(h => h.name === name);
      check(`${name}: requested`, got.length > 0, `${got.length} request(s)`);
      if (got.length) {
        check(`${name}: every request carried auth`, got.every(h => h.auth));
        check(`${name}: no 401`, !got.some(h => h.status === 401),
              `statuses ${[...new Set(got.map(h => h.status))].join(',')}`);
      }
    }
  }
  if (!process.env.SELFTEST) {
    const mine = OWNED[USER] || [];
    // Two separate assertions on purpose. The API one says the server found and
    // returned the rows; the page one says the sub populated db and the list
    // rendered them. Collapsing them into "the list is empty" cannot tell a
    // :db-key regression from a tab that simply is not open.
    const apiMissing = mine.filter(n => !itemsBody.includes(n));
    check(`${USER}'s items came back from the API`, apiMissing.length === 0,
          apiMissing.length ? `missing from response: ${apiMissing.join(', ')}`
                            : `all ${mine.length} in the response body`);
    const missing = mine.filter(n => !listed.includes(n));
    check(`${USER}'s own items are rendered`, missing.length === 0,
          missing.length ? `missing from page: ${missing.join(', ')}` : `all ${mine.length} present`);
    const leaked = OTHERS.filter(n => listed.includes(n) || itemsBody.includes(n));
    check("no other account's items leaked in", leaked.length === 0,
          leaked.length ? `LEAKED: ${leaked.join(', ')}` : `none of ${OTHERS.length} other-owner items`);
  }
  check('the loading overlay settled', !overlayVisible);
  check('no uncaught page errors', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '));

  // Normalised, order-independent trace. This is the artefact to diff between runs.
  const trace = {};
  for (const h of hits) {
    const k = `${h.name} ${h.method} auth=${h.auth} status=${h.status}`;
    trace[k] = (trace[k] || 0) + 1;
  }
  console.log('\nTRACE');
  Object.keys(trace).sort().forEach(k => console.log(`  ${trace[k]}x  ${k}`));
  console.log('END-TRACE');

  console.log(`\n${failures ? 'FAILURES: ' + failures : 'all checks passed'}`);
  await browser.close();
  process.exit(failures ? 1 : 0);
})();
