// The character data page under the production settings it has not otherwise been run with:
//   1. CSP_POLICY=strict (DEV_MODE unset): a per-request nonce and an enforcing
//      Content-Security-Policy, as production serves it;
//   2. a phone: Android Chrome user agent, touch, 412 px wide; buttons at least 44px, the save
//      notice at the bottom of the screen and the save popup on it.
// The page reads this browser's draft from localStorage, so the draft is placed there before the
// page loads (test setup for a page that reads storage; no app is loaded and no app-db written).
// A saved character's path is exercised with an unknown id, which still fetches.
//
// Needs `lein fig:ledger` and a server with the strict policy:
//   DATOMIC_URL=datomic:mem://orcpub-e2e SIGNATURE=e2e-test-signature PORT=8890 CSP_POLICY=strict \
//   lein with-profile init-db run -m e2e-boot
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/character-data-strict.js
const { chromium } = require('playwright');
const { BASE, findChrome, checker } = require('./lib');
const { dvRows, dvButton, dvSave } = require('./rescue-lib');

const DRAFT = `{:orcpub.entity.strict/values {:orcpub.dnd.e5.character/character-name "Strict Sam"}
 :orcpub.entity.strict/selections
 [{:orcpub.entity.strict/key :race :orcpub.entity.strict/option {:orcpub.entity.strict/key :tidefolk}}
  {:orcpub.entity.strict/key :class
   :orcpub.entity.strict/options [{:orcpub.entity.strict/key :wizard
     :orcpub.entity.strict/selections [{:orcpub.entity.strict/key :wizard-spells-known
       :orcpub.entity.strict/options [{:orcpub.entity.strict/key :brine-lash} {:orcpub.entity.strict/key :a-very-long-spell-key-that-takes-room}]}]}]}
  {:orcpub.entity.strict/key :feats :orcpub.entity.strict/options [{:orcpub.entity.strict/key :tidebreaker}]}]}`;
const wait = ms => new Promise(r => setTimeout(r, ms));
const PHONE_UA = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';

async function open(browser, opts) {
  const ctx = await browser.newContext(opts);
  await ctx.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: BASE });
  await ctx.addInitScript(d => { if (!localStorage.getItem('character')) localStorage.setItem('character', d); }, DRAFT);
  const page = await ctx.newPage();
  const blocked = [];
  page.on('console', m => { if (/Content Security Policy|Refused to/i.test(m.text())) blocked.push(m.text().slice(0, 160)); });
  page.on('pageerror', e => blocked.push('pageerror ' + String(e).slice(0, 160)));
  const resp = await page.goto(`${BASE}/pages/dnd/5e/character-data`, { waitUntil: 'load' });
  await page.waitForFunction(() => !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
  return { ctx, page, blocked, csp: resp.headers()['content-security-policy'] || '' };
}

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    // ---- 1. strict policy, desktop
    const { page, blocked, csp } = await open(browser, { viewport: { width: 1280, height: 900 } });
    check('the server sends an enforcing policy with a nonce', /script-src 'strict-dynamic' 'nonce-/.test(csp), csp.slice(0, 80));
    const nonce = (csp.match(/'nonce-([^']+)'/) || [])[1];
    // Browsers hide a nonce attribute from selectors once loaded; the element's property keeps it.
    check("the page's script carries that nonce", nonce && (await page.evaluate(() => (document.querySelector('script[src="/js/compiled/ledger.js"]') || {}).nonce)) === nonce);
    const rows = async () => (await dvRows(page)).map(r => r.choice);
    check('the script runs and lists the draft', (await rows()).includes('Tidebreaker'), (await rows()).join(', '));
    await dvButton(page, 'Tidebreaker').click();
    await dvSave(page);
    check('Remove and Save work', !(await page.evaluate(() => localStorage.getItem('character'))).includes(':tidebreaker'));
    await page.getByRole('button', { name: 'Copy for support' }).click();
    await wait(300);
    check('Copy for support works', (await page.evaluate(() => navigator.clipboard.readText())).startsWith('Browser draft (Strict Sam)'));
    await page.goto(`${BASE}/pages/dnd/5e/characters/999999/data`, { waitUntil: 'load' });
    await page.waitForFunction(() => !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
    check("the saved path's request is allowed", /no saved character with this number/.test(await page.locator('#ledger').innerText()));
    check('nothing was blocked by the policy', blocked.length === 0, blocked.join(' || '));

    // ---- 2. a phone
    const phone = await open(browser, { viewport: { width: 412, height: 915 }, userAgent: PHONE_UA, hasTouch: true, isMobile: true });
    const p = phone.page;
    const widths = await p.evaluate(() => ({ page: document.documentElement.scrollWidth, screen: window.innerWidth }));
    check('the page itself does not scroll sideways', widths.page <= widths.screen, JSON.stringify(widths));
    await dvRows(p);
    const box = await p.locator('#ledger [data-row] button').first().boundingBox();
    check("a row's Remove button is on screen", box && box.x >= 0 && box.x + box.width <= widths.screen, JSON.stringify(box));
    const sizes = await p.evaluate(() => [...document.querySelectorAll('#ledger button')].map(b => { const r = b.getBoundingClientRect(); return [b.textContent, Math.round(r.width), Math.round(r.height)]; }));
    const small = sizes.filter(([, w, h]) => w < 44 || h < 44);
    check('every button is at least 44px each way', sizes.length > 3 && small.length === 0, small.length ? JSON.stringify(small) : `${sizes.length} buttons`);
    await dvButton(p, 'Brine Lash').tap();
    check('Remove works by touch', (await p.locator('.dv-notice').innerText()).replace(/\s+/g, ' ').startsWith('1 removed, not saved'));
    const screen = p.viewportSize().height;
    const notice = await p.locator('.dv-notice-box').boundingBox();
    check('on a phone the notice is at the bottom of the screen', notice && notice.y + notice.height <= screen && notice.y + notice.height > screen - 40, JSON.stringify(notice));
    const last = await p.evaluate(() => { window.scrollTo(0, document.body.scrollHeight); const c = [...document.querySelectorAll('.dv-card')].pop().getBoundingClientRect(); return c.bottom; });
    const top = (await p.locator('.dv-notice-box').boundingBox()).y;
    check('scrolled to the end, the notice covers no card', last <= top, `last card ends ${Math.round(last)}, notice starts ${Math.round(top)}`);
    await p.locator('.dv-notice').getByRole('button', { name: 'Save', exact: true }).tap();
    const dialog = await p.locator('.dv-dialog').boundingBox();
    check('the save popup fits the phone screen', dialog && dialog.x >= 0 && dialog.x + dialog.width <= widths.screen && dialog.y >= 0 && dialog.y + dialog.height <= screen, JSON.stringify(dialog));
    check('nothing was blocked on the phone', phone.blocked.length === 0, phone.blocked.join(' || '));
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
