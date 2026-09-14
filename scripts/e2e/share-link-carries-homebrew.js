// Does a short share link carry a character's homebrew to someone who does not have it?
//
//   ./scripts/e2e/run.sh share-link-carries-homebrew.js
//
// e2e-boot seeds a character for kaylee that knows a homebrew language, and run.sh passes its id on as
// E2E_HOMEBREW_CHARACTER_ID. kaylee's browser gets that language in its local library, where an import
// would leave it. Signed in, her character page makes the share link: the homebrew is uploaded
// encrypted, and the link carries only the snapshot's id and key. The link is then opened logged out
// and as zoe, neither of whom has the homebrew, and both must load it. The same link with a wrong key
// must load nothing and say so.
const { chromium } = require('playwright');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const ID = process.env.E2E_HOMEBREW_CHARACTER_ID;
const EXECUTABLE = process.env.E2E_CHROMIUM || undefined;
const SOURCE = 'E2E Tongues';
const LANGUAGE = 'E2E Cant';
const LIBRARY = `{"${SOURCE}" {:orcpub.dnd.e5/languages {:e2e-cant {:name "${LANGUAGE}" :key :e2e-cant :option-pack "${SOURCE}" :description "Spoken only by test suites."}}}}`;

async function newContext(browser, library) {
  const ctx = await browser.newContext();
  await ctx.addInitScript(lib => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026'));
      if (lib && !localStorage.getItem('plugins')) localStorage.setItem('plugins', lib);
    } catch (e) {}
    // Keep what Copy link writes, so the suite can open it.
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: text => { window.__copied = text; return Promise.resolve(); } },
    });
  }, library || null);
  return ctx;
}

async function login(page, user, pass) {
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill(user);
  await page.locator('input').nth(1).fill(pass);
  await page.locator('button.form-button').click();
  await page.waitForTimeout(3500);
}

function watchShares(page) {
  const seen = [];
  page.on('response', r => {
    if (new URL(r.url()).pathname.includes('/shares/')) seen.push(`${r.request().method()} ${r.status()}`);
  });
  return seen;
}

async function open(browser, link, who) {
  const ctx = await newContext(browser, null);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  const requests = watchShares(page);
  if (who) await login(page, who.user, who.pass);
  await page.goto(link, { waitUntil: 'domcontentloaded', timeout: 120000 });
  // A failure notice is a toast that closes itself, so watch for it from the start instead of looking
  // once at the end.
  const noticed = page.waitForFunction(() => document.body && document.body.innerText.includes('could not be loaded'),
    null, { timeout: 15000 }).then(() => 1, () => 0);
  await page.waitForLoadState('networkidle', { timeout: 120000 });
  await page.waitForTimeout(5000);
  const sources = await page.evaluate(() => {
    const c = window.cljs.core;
    const shared = c.get(window.re_frame.db.app_db.state, c.keyword(null, 'shared-plugins'));
    return shared ? c.clj__GT_js(c.vec(c.keys(shared))) : [];
  });
  const banner = await page.locator('text=Shared with 1 homebrew piece').count();
  const failedNotice = await noticed;
  await ctx.close();
  return { sources, banner, failedNotice, requests, errors };
}

(async () => {
  if (!ID) {
    console.error('No E2E_HOMEBREW_CHARACTER_ID: run this through scripts/e2e/run.sh, which reads it from e2e-boot.');
    process.exit(2);
  }
  let failures = 0;
  const check = (ok, label, detail) => {
    if (!ok) failures++;
    console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  -- ' + detail : ''}`);
  };
  const browser = await chromium.launch({ executablePath: EXECUTABLE });

  console.log('kaylee makes the link:');
  const ownerCtx = await newContext(browser, LIBRARY);
  const owner = await ownerCtx.newPage();
  const ownerRequests = watchShares(owner);
  await login(owner, 'kaylee', 'serenity99');
  await owner.goto(`${BASE}/pages/dnd/5e/characters/${ID}`, { waitUntil: 'networkidle', timeout: 120000 });
  await owner.waitForFunction(
    () => [...document.querySelectorAll('button')].some(b => b.textContent.includes('Copy link') && !b.disabled),
    null, { timeout: 60000 });
  await owner.locator('button', { hasText: 'Copy link' }).first().click();
  await owner.waitForTimeout(500);
  const link = await owner.evaluate(() => window.__copied || '');
  const match = link.match(/#s=([A-Za-z0-9_-]{22})\.([A-Za-z0-9_-]{43})$/);
  check(Boolean(match), 'the link names a snapshot and its key', link);
  check(link.length < 200, 'the link is short', `${link.length} characters`);
  check(ownerRequests.some(r => r === 'PUT 200' || r === 'GET 200'), 'the snapshot is on the server', ownerRequests.join(', '));
  if (match) {
    const stored = await owner.evaluate(async url => {
      const bytes = new Uint8Array(await (await fetch(url)).arrayBuffer());
      return { first: bytes[0], size: bytes.length, text: new TextDecoder().decode(bytes) };
    }, `${BASE}/dnd/5e/characters/${ID}/shares/${match[1]}`);
    check(stored.first === 2 && !stored.text.includes(LANGUAGE), 'what the server stores is not readable',
      `${stored.size} bytes`);
  }

  // The character list's Copy link is the same button. It used to pack whatever character was open in
  // the builder, which in this fresh browser is none, so its link must match the character page's.
  console.log('\nkaylee copies it from the character list:');
  await owner.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'networkidle', timeout: 120000 });
  await owner.evaluate(id => {
    window.__copied = '';
    const c = window.cljs.core;
    window.re_frame.core.dispatch(c.PersistentVector.fromArray(
      [c.keyword(null, 'toggle-character-expanded'), parseInt(id)], true));
  }, ID);
  let listLink = '';
  try {
    await owner.waitForFunction(
      () => [...document.querySelectorAll('button')].some(b => b.textContent.includes('Copy link') && !b.disabled),
      null, { timeout: 60000 });
    await owner.locator('button', { hasText: 'Copy link' }).first().click();
    await owner.waitForTimeout(500);
    listLink = await owner.evaluate(() => window.__copied || '');
  } catch (e) {
    listLink = `(no Copy link in the expanded row: ${e.message.split('\n')[0]})`;
  }
  check(Boolean(match) && listLink === link, "the list row's link is the character page's link", listLink);
  await ownerCtx.close();

  if (match) {
    for (const who of [null, { user: 'zoe', pass: 'washburne7' }]) {
      const name = who ? who.user : 'logged out';
      console.log(`\n${name} opens it:`);
      const r = await open(browser, link, who);
      check(r.sources.includes(SOURCE), 'the homebrew loaded', JSON.stringify(r.sources));
      check(r.banner > 0, 'the page says what was shared');
      check(!r.requests.some(x => x.startsWith('PUT')), 'nothing was uploaded', r.requests.join(', '));
      check(r.errors.length === 0, 'no uncaught page errors', r.errors.join(' | '));
    }
    console.log('\nthe same link with a wrong key:');
    const wrong = await open(browser, link.replace(/\.[A-Za-z0-9_-]{43}$/, '.' + 'A'.repeat(43)), null);
    check(wrong.sources.length === 0, 'loads nothing', JSON.stringify(wrong.sources));
    check(wrong.failedNotice > 0, 'says the homebrew could not be loaded');
  }

  await browser.close();
  console.log(failures ? `\nFAILURES: ${failures}` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})();
