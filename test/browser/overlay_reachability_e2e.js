// Browser-driven e2e: can you actually click what an overlay puts on screen?
//
// Drives the REAL app against `lein e2e-server` on :8890.
//
// WHAT IT PINS: for every overlay this can open anonymously, every control it
// shows is HITTABLE — document.elementFromPoint at the control's centre lands on
// the control. Two real bugs looked fine in a screenshot and failed exactly this:
// header dropdowns painting under the sticky button row, and My Content running
// off the bottom of a short window. Visibility is not the check.
//
// It audits whatever is on screen rather than a hardcoded list: every visible
// positioned element stacked at z-index >= 100, and the controls inside it. An
// overlay added later is audited without this file changing.
//
// COST: three lanes in PARALLEL contexts, each with one page load — the app takes
// ~12s to boot on this dev build, so the wall clock is the slowest lane rather
// than their sum. No reloads inside a lane, no networkidle waits, every action
// bounded, and the whole run bounded by BUDGET_S (default 150): past that it
// FAILS with what it had rather than hanging.
//
// WHAT EACH LANE CAN REACH, and what it cannot:
//   1. anonymous — header chrome, Orcacle, the PDF options panel, the release panel.
//   2. signed-in chrome — the user menu, behind a REAL login through the form.
//      Needs ORCPUB_TEST_USER / ORCPUB_TEST_PASSWORD for an account the server
//      will accept; without them the lane skips and says so. Seeding the saved
//      session in localStorage instead does NOT work and should not be tried: the
//      app verifies the stored token with the server on boot and clears it, which
//      is the app being right.
//   3. imported homebrew — the import conflict modal (raised for real by importing
//      the same pack twice) and the delete confirmation. Uses ORCBREW_PACK when the
//      runner passes one, else the checked-in fixture, so it never silently skips.
//      Still uncovered here, and named in the output rather than left implied: the
//      item-level delete confirmation (needs an item selected), and the
//      export-warning and source-name-choice modals (need content with missing
//      fields, and a file whose name disagrees with the source it declares).
//
// Prerequisites:
//   lein fig:build && lein garden once && lein e2e-server
// Run:  node test/browser/overlay_reachability_e2e.js
// Exit code 0 = all checks passed.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const { suppressCookieBanner, importPack } = require('./lib/orcbrew-import');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.ORCPUB_E2E_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'overlays-'));
const BUDGET_MS = +(process.env.BUDGET_S || 180) * 1000;
const PACK = process.env.ORCBREW_PACK || path.join(__dirname, '..', 'fixtures', 'test-pak.orcbrew');
// 720 is where a tall menu first runs off the bottom; 900 is a desk monitor.
const VIEWPORT = { width: 1366, height: 720 };
const started = Date.now();
const left = () => BUDGET_MS - (Date.now() - started);

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base)
      .filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) {
      const p = path.join(base, dir, 'chrome-linux', 'chrome');
      if (fs.existsSync(p)) return p;
    }
  } catch (_) {}
  return undefined;
}

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};
const skip = (name, why) => console.log(`SKIP  ${name}  — ${why}`);

// Is every control this overlay shows actually hittable? One evaluate, no round
// trip per control — a round trip each is what turns a probe into a coffee break.
//
// Two rules, and the second one is where the judgement is:
//   * covered by something else -> always a failure, whatever it is;
//   * outside the window -> a failure only inside a FLOATING overlay, which you
//     cannot scroll the page to reach (the hover menu closes, the modal moves
//     with the viewport). Ordinary page content below the fold is fine.
const audit = (page, root) => page.evaluate((rootSel) => {
  const pageWasAt = { x: window.scrollX, y: window.scrollY };
  const SEL = 'a, button, input, select, textarea, [role=button], .pointer, .link-button, [class*="close"]';
  const visible = el => {
    const s = getComputedStyle(el);
    if (s.display === 'none' || s.visibility === 'hidden' || +s.opacity === 0) return false;
    const r = el.getBoundingClientRect();
    return r.width > 1 && r.height > 1;
  };
  const named = el => el.id ? '#' + el.id
    : (typeof el.className === 'string' && el.className.trim()
       ? '.' + el.className.trim().split(/\s+/).slice(0, 2).join('.')
       : el.tagName.toLowerCase());
  const floating = el => {
    for (let n = el; n && n !== document.body; n = n.parentElement) {
      const p = getComputedStyle(n).position;
      if (p === 'fixed' || p === 'absolute') return true;
    }
    return false;
  };

  const out = [];
  for (const layer of [...document.querySelectorAll(rootSel)].filter(visible)) {
    const controls = [...(layer.matches(SEL) ? [layer] : []), ...layer.querySelectorAll(SEL)]
      .filter(visible);
    const problems = [];
    for (const el of controls) {
      // Scroll the menu the way a wheel over it would, then put it back: an audit
      // that leaves the page moved breaks whatever the probe does next.
      const scroller = el.parentElement && el.parentElement.scrollHeight > el.parentElement.clientHeight + 1
        ? el.parentElement : null;
      const wasAt = scroller ? scroller.scrollTop : 0;
      if (scroller) scroller.scrollTop = Math.max(0, el.offsetTop - (scroller.clientHeight - el.offsetHeight) / 2);
      const b = el.getBoundingClientRect();
      const x = Math.round(b.x + b.width / 2);
      const y = Math.round(b.y + Math.min(b.height / 2, 18));
      const label = (el.innerText || el.value || el.getAttribute('aria-label') || el.tagName).trim().slice(0, 30) || '(control)';
      const off = y < 0 || y > window.innerHeight || x < 0 || x > window.innerWidth;
      if (off) {
        if (floating(el)) problems.push(`${label} is off the window inside a floating layer`);
      } else {
        const top = document.elementFromPoint(x, y);
        if (!top) problems.push(`${label} has nothing at its centre`);
        else if (top !== el && !el.contains(top) && !top.contains(el)) {
          problems.push(`${label} is covered by ${named(top)}`);
        }
      }
      if (scroller) scroller.scrollTop = wasAt;
    }
    out.push({ layer: named(layer), controls: controls.length, problems });
  }
  window.scrollTo(pageWasAt.x, pageWasAt.y);
  return out;
}, root);

async function auditState(page, name, root) {
  const layers = await audit(page, root);
  const busted = layers.filter(l => l.problems.length);
  const controls = layers.reduce((n, l) => n + l.controls, 0);
  // Nothing audited means the selector went stale, not that the overlay is well:
  // the failure where a probe quietly stops asserting and passes forever.
  check(`${name}: every control is reachable`,
        busted.length === 0 && controls > 0,
        controls === 0
          ? `nothing audited — is "${root}" still the right selector?`
          : busted.length
            ? busted.map(l => `${l.layer}: ${l.problems.slice(0, 2).join('; ')}`).slice(0, 3).join(' | ')
            : `${layers.length} layer(s), ${controls} control(s)`);
}

// Each opener returns true if it managed to open its overlay. Bounded: a control
// that isn't there is a SKIP, never a wait.
const click = async (page, locator, ms = 4000) => {
  try { await locator.first().click({ timeout: ms }); return true; }
  catch (e) { if (process.env.DEBUG_OPEN) console.log('   open failed:', String(e).split('\n')[0]); return false; }
};

// One context per lane, each pre-seeded so its page boots into the state it needs.
// `session` seeds a saved login the way a returning browser holds one.
async function openLane(browser) {
  const ctx = await browser.newContext({ viewport: VIEWPORT });
  await suppressCookieBanner(ctx);
  await ctx.addInitScript(() => {
    try { localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {}
  });

  // SELFTEST=1 drops a sheet of glass over the page. Every audited state must then
  // FAIL: a probe nobody has ever seen fail is a probe nobody should trust.
  if (process.env.SELFTEST) {
    await ctx.addInitScript(() => {
      addEventListener('load', () => {
        const glass = document.createElement('div');
        glass.style.cssText = 'position:fixed;inset:0;z-index:999999;background:transparent';
        document.body.appendChild(glass);
      });
    });
  }
  const page = await ctx.newPage();
  return { ctx, page };
}

const TEST_USER = process.env.ORCPUB_TEST_USER;
const TEST_PASSWORD = process.env.ORCPUB_TEST_PASSWORD;

(async () => {
  const browser = await chromium.launch({
    executablePath: findChrome(),
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  // LANE 1 — what anyone can open without signing in or importing anything.
  const anonymous = async () => {
  const { ctx, page } = await openLane(browser);
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#app-main', { timeout: 30000 });
  await page.waitForTimeout(1200);

  // The header chrome is on every page and sits next to the sticky button row —
  // the pairing that produced the dropdown bug.
  await auditState(page, 'header chrome', '#app-header');

  // Orcacle — the search overlay behind the header's magnifier.
  if (left() > 20000) {
    const opened = await click(page, page.locator('#app-header img[src*="magnifying"]'));
    const there = opened && await page.waitForSelector('.orcacle-input', { timeout: 3000 })
      .then(() => true).catch(() => false);
    if (there) {
      await auditState(page, 'Orcacle open', '.orcacle-input');
      await page.screenshot({ path: path.join(OUT, '1-orcacle.png') });
      await click(page, page.locator('.orcacle-close, .fa-times, .fa-close').first(), 2000);
      await page.waitForTimeout(400);
    } else {
      skip('Orcacle open', 'the magnifier did not open a search panel');
    }
  } else skip('Orcacle open', 'out of budget');

  // The PDF options panel, opened from the header's print control.
  if (left() > 20000) {
    const opened = await click(page,
      page.locator('.form-button, button').filter({ hasText: /^(print|export)$/i }));
    await page.waitForTimeout(600);
    const there = opened && await page.getByText(/create pdf/i).first().isVisible().catch(() => false);
    if (there) {
      await auditState(page, 'PDF options open', '.bg-light.m-b-10');
      await page.screenshot({ path: path.join(OUT, '2-pdf-options.png') });
      await click(page, page.getByText(/^cancel$/i).first(), 2000);
      await page.waitForTimeout(400);
    } else {
      skip('PDF options open', 'no print control on this page');
    }
  } else skip('PDF options open', 'out of budget');

  // The release panel — a full-screen overlay, opened from the footer.
  if (left() > 20000) {
    const opened = await click(page, page.locator('a.pointer').filter({ hasText: /what.s new/i }));
    const there = opened && await page.waitForSelector('.whats-new-panel', { timeout: 3000 })
      .then(() => true).catch(() => false);
    if (there) {
      await auditState(page, "What's New open", '.whats-new-panel');
      await page.keyboard.press('Escape').catch(() => {});
      await page.waitForTimeout(400);
    } else {
      skip("What's New open", 'the footer link did not open it');
    }
  } else skip("What's New open", 'out of budget');

    await ctx.close();
  };

  // LANE 2 — the chrome a signed-in reader sees. The menu opens on hover and is
  // absolutely positioned next to the sticky button row: the same shape as the
  // header-dropdown bug, and nothing covered it before this.
  const signedIn = async () => {
    if (!TEST_USER || !TEST_PASSWORD) {
      skip('user menu (LOG OUT / ACCOUNT)',
           'set ORCPUB_TEST_USER / ORCPUB_TEST_PASSWORD to an account the server accepts');
      return;
    }
    const { ctx, page } = await openLane(browser);
    await page.goto(`${BASE}/pages/dnd/5e/login-page`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('input[type=password]', { timeout: 30000 });
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type=password]').fill(TEST_PASSWORD);
    await page.locator('button, .form-button').filter({ hasText: /log ?in/i }).first()
      .click({ timeout: 5000 }).catch(() => {});
    await page.waitForSelector('#app-main', { timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(1200);

    const header = page.locator('#user-header');
    const known = await header.locator(`text=${TEST_USER}`).count().catch(() => 0);
    if (!known) {
      skip('user menu (LOG OUT / ACCOUNT)', `signing in as ${TEST_USER} did not take`);
    } else {
      await header.hover();
      await page.waitForTimeout(400);
      await auditState(page, 'user menu open', '#user-menu');
      await page.screenshot({ path: path.join(OUT, '3-user-menu.png') });
    }
    await ctx.close();
  };

  // LANE 3 — overlays that only exist once there is homebrew. The conflict modal
  // is raised for real: import the same pack twice and its keys collide.
  const withHomebrew = async () => {
    if (!fs.existsSync(PACK)) {
      skip('import conflict modal', `no pack at ${PACK}`);
      skip('delete confirmation', `no pack at ${PACK}`);
      return;
    }
    const { ctx, page } = await openLane(browser);
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#app-main', { timeout: 30000 });
    await page.waitForTimeout(1000);

    const first = await importPack(page, PACK).catch(e => ({ error: e.message }));
    if (first && first.error) {
      skip('import conflict modal', `first import failed: ${first.error}`);
      await ctx.close();
      return;
    }
    await page.waitForTimeout(1200);

    // Second import of the SAME CONTENT UNDER A DIFFERENT SOURCE NAME. Re-importing
    // the identical file raises nothing — the app recognises the source and replaces
    // it. Renaming the source makes every key inside it collide with the copy already
    // in the library, which is the conflict a real user hits when two packs overlap.
    const renamed = path.join(os.tmpdir(), 'overlap-probe.orcbrew');
    fs.writeFileSync(renamed,
      fs.readFileSync(PACK, 'utf8').replace(/Source Collection 04/g, 'Overlap Probe Source'));
    await page.setInputFiles('input[type=file]', renamed).catch(() => {});
    const modal = await page.waitForSelector('.conflict-modal', { timeout: 15000 })
      .then(() => true).catch(() => false);
    if (modal) {
      await auditState(page, 'import conflict modal open', '.conflict-modal');
      await page.screenshot({ path: path.join(OUT, '4-conflict-modal.png') });
      await page.keyboard.press('Escape').catch(() => {});
      await page.locator('.conflict-modal-footer button').last().click({ timeout: 3000 }).catch(() => {});
      await page.waitForTimeout(800);
    } else {
      skip('import conflict modal', 'a second import of the same pack raised no conflict');
    }

    // The delete guard: it is markup on the page, hidden until asked for, so the
    // audit has to wait for the container to lose its `hidden` class.
    // The library's delete control is labelled "DELETE…", not "delete".
    const del = page.locator('button, .form-button, .link-button').filter({ hasText: /^delete/i }).first();
    if (await del.count().catch(() => 0)) {
      await del.click({ timeout: 3000 }).catch(() => {});
      const shown = await page.waitForSelector('.modal-container:not(.hidden) .modal', { timeout: 5000 })
        .then(() => true).catch(() => false);
      if (shown) {
        await auditState(page, 'delete confirmation open', '.modal-container:not(.hidden) .modal');
        await page.screenshot({ path: path.join(OUT, '5-delete-guard.png') });
      } else {
        // The library toolbar's DELETE… is a staged guard, not the item-level
        // confirmation this looks for; that one needs an item selected first.
        skip('delete confirmation', 'the library DELETE… is a staged flow, not the item modal');
      }
    } else skip('delete confirmation', 'no delete control on this page');
    await ctx.close();
  };

  await Promise.all([anonymous(), signedIn(), withHomebrew()]);

  if (left() <= 0) check('finished inside its time budget', false, `${BUDGET_MS / 1000}s exceeded`);
  skip('export-warning and source-name-choice modals', 'need content with missing fields / a renamed file');

  await browser.close();

  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed in ${Math.round((Date.now() - started) / 1000)}s`);
  console.log(`screenshots: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
