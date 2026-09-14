// Helpers the scripts/e2e suites share: a browser context with the overlays out of the way, logging in,
// watching share requests, pressing and reading the share buttons, and the homebrew the share suites seed.
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const EXECUTABLE = process.env.E2E_CHROMIUM || undefined;

// The homebrew language e2e-boot's E2E_HOMEBREW_CHARACTER_ID character knows. The server never has it;
// a suite puts it in the owner's local library.
const HOMEBREW = {
  source: 'E2E Tongues',
  language: 'E2E Cant',
  library: description =>
    `{"E2E Tongues" {:orcpub.dnd.e5/languages {:e2e-cant {:name "E2E Cant" :key :e2e-cant :option-pack "E2E Tongues" :description "${description}"}}}}`,
};

// A context whose pages skip the cookie banner and What's New panel, keep what Copy link writes in
// window.__copied, and start with `library` in local storage when given.
async function newContext(browser, library) {
  const ctx = await browser.newContext();
  await ctx.addInitScript(lib => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026'));
      if (lib && !localStorage.getItem('plugins')) localStorage.setItem('plugins', lib);
    } catch (e) {}
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

// Every request to a share route this page makes, as "METHOD status".
function watchShares(page) {
  const seen = [];
  page.on('response', r => {
    if (new URL(r.url()).pathname.includes('/shares/')) seen.push(`${r.request().method()} ${r.status()}`);
  });
  return seen;
}

async function waitForButton(page, text) {
  await page.waitForFunction(
    t => [...document.querySelectorAll('button')].some(b => b.textContent.includes(t) && !b.disabled),
    text, { timeout: 60000 });
}

async function copyLink(page) {
  await waitForButton(page, 'Copy link');
  await page.evaluate(() => { window.__copied = ''; });
  await page.locator('button', { hasText: 'Copy link' }).first().click();
  await page.waitForTimeout(300);
  return page.evaluate(() => window.__copied || '');
}

// The sources in the page's shared-homebrew overlay, and the seeded language's description there.
async function sharedHomebrew(page) {
  return page.evaluate(src => {
    const c = window.cljs.core;
    const shared = c.get(window.re_frame.db.app_db.state, c.keyword(null, 'shared-plugins'));
    const path = c.PersistentVector.fromArray(
      [src, c.keyword('orcpub.dnd.e5', 'languages'), c.keyword(null, 'e2e-cant'), c.keyword(null, 'description')], true);
    return {
      sources: shared ? c.clj__GT_js(c.vec(c.keys(shared))) : [],
      description: shared ? c.get_in(shared, path) : null,
    };
  }, HOMEBREW.source);
}

function checker() {
  let failures = 0;
  const check = (ok, label, detail) => {
    if (!ok) failures++;
    console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  -- ' + detail : ''}`);
  };
  return { check, failures: () => failures };
}

module.exports = { BASE, EXECUTABLE, HOMEBREW, newContext, login, watchShares, waitForButton, copyLink,
                   sharedHomebrew, checker };
