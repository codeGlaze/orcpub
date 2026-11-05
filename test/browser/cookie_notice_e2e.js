// Overlays:  NOT suppressed. The cookie notice is what this tests.
// Does dismissing the cookie notice stick: on other pages, and on the next visit?
//
// resources/public/js/cookies.js shows the notice unless its consent cookie is set or the
// localStorage opt-out is on, and "Got it!" writes that cookie. The notice is created while the
// page loads, so whether it is there is settled by DOMContentLoaded; no waits are needed.
//
//   1. a first visit shows the notice, with its policy link
//   2. "Got it!" removes it and writes a consent cookie for the whole site (path /) that
//      outlives the browser session (an expiry a year or more away)
//   3. another page of the site does not show it again
//   4. a new browser session carrying the saved cookies does not show it again
//   5. the localStorage opt-out the other tests rely on hides it
//
// Needs:     server (`lein e2e-server`). No login.
// Runs in:   ~10s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const NOTICE = '#cookie-policy-popup';
const YEAR_S = 365 * 24 * 3600;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Opens `url` in `ctx` and returns the page once the notice has had its chance to appear.
async function open(ctx, url) {
  const page = await ctx.newPage();
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  return page;
}

const showing = page => page.locator(NOTICE).count().then(n => n > 0);

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });

  // ── 1-2: first visit, dismiss ────────────────────────────────────────────────
  const first = await browser.newContext();
  const page = await open(first, `${BASE}/pages/dnd/5e/character-builder`);
  check('a first visit shows the notice', await showing(page));
  const href = await page.locator('#plcy-lnk').getAttribute('href').catch(() => null);
  check('the notice links the cookie policy', href === '/cookies-policy', String(href));

  await page.locator('#cookie-btn').click();
  await page.waitForSelector(NOTICE, { state: 'detached', timeout: 5000 }).catch(() => {});
  check('"Got it!" removes the notice', !(await showing(page)));

  const consent = (await first.cookies()).find(c => c.name === 'flatsome_cookie_notice');
  check('"Got it!" writes the consent cookie', Boolean(consent), consent ? consent.value : 'none');
  check('the consent cookie covers the whole site (path /)',
        Boolean(consent) && consent.path === '/', consent && consent.path);
  check('the consent cookie outlives the browser session (expires a year or more away)',
        Boolean(consent) && consent.expires > Date.now() / 1000 + YEAR_S - 86400,
        consent && (consent.expires < 0 ? 'session cookie' : new Date(consent.expires * 1000).toISOString()));

  // ── 3: another page ──────────────────────────────────────────────────────────
  const other = await open(first, `${BASE}/`);
  check('another page does not show it again', !(await showing(other)));

  // ── 4: next visit ────────────────────────────────────────────────────────────
  // A new context starts from the saved state minus session cookies, as a browser that was closed.
  const saved = await first.storageState();
  const kept = saved.cookies.filter(c => c.expires > 0);
  await first.close();
  const next = await browser.newContext({ storageState: { cookies: kept, origins: [] } });
  const again = await open(next, `${BASE}/pages/dnd/5e/character-builder`);
  check('the next visit does not show it again', !(await showing(again)));
  await next.close();

  // ── 5: the harness opt-out ───────────────────────────────────────────────────
  const optOut = await browser.newContext();
  await optOut.addInitScript(() => { try { localStorage.setItem('orcpub:no-cookie-banner', '1'); } catch (e) {} });
  const quiet = await open(optOut, `${BASE}/pages/dnd/5e/character-builder`);
  check('the localStorage opt-out hides it', !(await showing(quiet)));
  await optOut.close();

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
