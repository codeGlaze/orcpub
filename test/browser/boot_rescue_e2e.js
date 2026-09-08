// Does the boot-shell rescue survive a broken view? Drives the REAL server on
// :8890 and breaks the app in the ways it actually breaks — bundle missing,
// bundle throwing on init, a component throwing after a clean mount — checking
// each time that the control is VISIBLE and that clicking it yields the exact
// bytes that were stored. "A button is on the page" is not the claim.
//
// The two negative cases matter as much: a healthy app must clear the control,
// and a visitor with no homebrew must never see it. Without those, a control
// that is simply always on would pass everything above.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const BASE = 'http://localhost:8890';
const OUT = process.env.PROBE_OUT || '/tmp/boot-rescue';
const PLUGINS = '{"Rescue Me Pack" {:orcpub.dnd.e5/spells {:witchbolt {:option-pack "Rescue Me Pack" :name "Witch Bolt" :level 1}}}}';
// What the store looks like after the user works for a while. The rescue must
// hand over THIS, not the snapshot that existed when the page opened.
const PLUGINS_LATER = '{"Rescue Me Pack" {:orcpub.dnd.e5/spells {:witchbolt {:option-pack "Rescue Me Pack" :name "Witch Bolt" :level 1} :firebolt {:option-pack "Rescue Me Pack" :name "Fire Bolt" :level 0}}}}';

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Seeds localStorage before any app code runs, so the control has something to save.
async function seeded(browser, { breakage } = {}) {
  const ctx = await browser.newContext({ acceptDownloads: true });
  await ctx.addInitScript(v => {
    try {
      localStorage.setItem('plugins', v);
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  }, PLUGINS);
  const page = await ctx.newPage();
  if (breakage) await breakage(page);
  return { ctx, page };
}

const visible = page => page.evaluate(() => {
  const el = document.getElementById('boot-rescue');
  if (!el) return { present: false };
  const cs = getComputedStyle(el);
  const r = el.getBoundingClientRect();
  return { present: true, display: cs.display, w: r.width, h: r.height,
           text: (document.getElementById('boot-rescue-note') || {}).textContent };
});

// The whole point: not just "a button is there" but "the bytes come back out".
async function rescues(page, label) {
  const [dl] = await Promise.all([
    page.waitForEvent('download', { timeout: 15000 }),
    page.click('#boot-rescue-btn'),
  ]);
  const f = path.join(OUT, label + '.orcbrew');
  await dl.saveAs(f);
  const got = fs.readFileSync(f, 'utf8');
  return { name: dl.suggestedFilename(), intact: got === PLUGINS, bytes: got.length };
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME || '/opt/pw-browsers/chromium' });

  // ---- 1. bundle never arrives (bad deploy / CDN failure) -----------------
  {
    const { ctx, page } = await seeded(browser, {
      breakage: p => p.route('**/js/compiled/**', r => r.abort()),
    });
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3000);
    const v = await visible(page);
    check('bundle 404s -> control visible', v.present && v.display === 'flex' && v.h > 0,
          JSON.stringify(v));
    // The size is the only evidence a panicking user has that the file about to
    // download is really their content. Math.round(bytes/1024) reported "0 KB"
    // for anything under a kilobyte, which reads as "there is nothing here".
    check('size reads as a real quantity',
          /\((\d+ bytes|\d+(\.\d)? KB|\d+ MB)\)/.test(v.text || '') &&
          !/\(0 (KB|MB|bytes)\)/.test(v.text || ''),
          v.text);
    if (v.present && v.display === 'flex') {
      const r = await rescues(page, 'bundle-404');
      check('bundle 404s -> homebrew comes out intact', r.intact, r.name + ' ' + r.bytes + 'B');
    }
    await page.screenshot({ path: path.join(OUT, '1-bundle-404.png') });
    await ctx.close();
  }

  // ---- 2. bundle loads but throws on evaluation ---------------------------
  {
    const { ctx, page } = await seeded(browser, {
      breakage: p => p.route('**/js/compiled/orcpub.js', r =>
        r.fulfill({ status: 200, contentType: 'application/javascript',
                    body: 'throw new Error("simulated CLJS init failure");' })),
    });
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3000);
    const v = await visible(page);
    check('bundle throws on init -> control visible', v.present && v.display === 'flex', JSON.stringify(v));
    if (v.present && v.display === 'flex') {
      const r = await rescues(page, 'init-throw');
      check('bundle throws on init -> homebrew comes out intact', r.intact, r.name + ' ' + r.bytes + 'B');
    }
    await page.screenshot({ path: path.join(OUT, '2-init-throw.png') });
    await ctx.close();
  }

  // ---- 3. a component throws AFTER a clean mount -------------------------
  //  The app boots fine and clears the control; only then does a render throw
  //  into the app-root error boundary. This is the case a naive "remove on
  //  mount" gets wrong, and the reason the control hides rather than deletes.
  //  reduce-kv is poisoned because My Content calls it per source on every
  //  render, so the next render is a real throw through React's own error path.
  {
    const { ctx, page } = await seeded(browser);
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('text=Rescue Me Pack', { timeout: 180000 });
    await page.waitForTimeout(1200);

    const cleared = await visible(page);
    check('clean render -> control cleared', cleared.display === 'none', JSON.stringify(cleared));

    // Poison the function My Content calls while rendering each source row, so
    // the next render is a genuine throw through React's own error path.
    const poisoned = await page.evaluate(() => {
      var v = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5
              && window.orcpub.dnd.e5.views;
      if (!v || !v.source_disabled_counts) return 'views ns unreachable';
      v.source_disabled_counts = function () { throw new Error('simulated render failure'); };
      return 'ok';
    });
    check('could poison a render-path fn', poisoned === 'ok', String(poisoned));

    // Force the next render of that row.
    const expand = page.locator('text=expand').first();
    const clicked = await expand.click({ timeout: 10000 }).then(() => 'clicked')
                                .catch(e => e.message.split('\n')[0]);
    check('could force a re-render', clicked === 'clicked', String(clicked));
    await page.waitForTimeout(2500);

    const boundary = await page.locator('text=Something went wrong on this page').count();
    check('render throws -> app-root boundary catches', boundary > 0, 'fallback elements: ' + boundary);
    const v = await visible(page);
    check('view errored -> control comes back', v.display === 'flex' && v.h > 0, JSON.stringify(v));
    if (v.display === 'flex') {
      const r = await rescues(page, 'view-error');
      check('view errored -> homebrew comes out intact', r.intact, r.name + ' ' + r.bytes + 'B');
    }
    await page.screenshot({ path: path.join(OUT, '3-view-error.png'), fullPage: true });
    await ctx.close();
  }

  // ---- 3b. content created DURING the session ----------------------------
  //  The failure this exists for: an export that snapshots localStorage at page
  //  load hands back an empty or hours-old file, because everything the user
  //  built after the page opened never reached the copy it kept.
  {
    const ctx = await browser.newContext({ acceptDownloads: true });
    // Deliberately opens with NOTHING stored, so a load-time read would both
    // fail to arm the control and have nothing to give.
    await ctx.addInitScript(() => {
      try {
        localStorage.setItem('orcpub:no-cookie-banner', '1');
        localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
      } catch (e) {}
    });
    const page = await ctx.newPage();
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('text=/My Content|MY CONTENT/i', { timeout: 180000 });
    await page.waitForTimeout(1200);

    // The user builds something. Then the view falls over.
    await page.evaluate(v => localStorage.setItem('plugins', v), PLUGINS_LATER);
    await page.evaluate(() => {
      var v = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5
              && window.orcpub.dnd.e5.views;
      if (v && v.source_disabled_counts) {
        v.source_disabled_counts = function () { throw new Error('simulated render failure'); };
      }
      if (window.orcpubBootRescue) { window.orcpubBootRescue(); }
    });
    await page.waitForTimeout(800);

    const v = await visible(page);
    check('opened empty, built later -> control arms anyway',
          v.display === 'flex' && v.h > 0, JSON.stringify(v));
    if (v.display === 'flex') {
      const [dl] = await Promise.all([
        page.waitForEvent('download', { timeout: 15000 }),
        page.click('#boot-rescue-btn'),
      ]);
      const f = path.join(OUT, 'session-work.orcbrew');
      await dl.saveAs(f);
      const got = fs.readFileSync(f, 'utf8');
      check('rescue hands over what is stored NOW, not at page load',
            got === PLUGINS_LATER,
            got === PLUGINS ? 'STALE: got the page-load snapshot'
                            : got.length + 'B vs expected ' + PLUGINS_LATER.length + 'B');
      check('size label reflects the current store',
            (v.text || '').indexOf(String(PLUGINS_LATER.length) + ' bytes') !== -1, v.text);
    }
    await page.screenshot({ path: path.join(OUT, '3b-session-work.png') });
    await ctx.close();
  }

  // ---- 4. unreadable blob: the app quarantines it and boots fine ----------
  //  Recorded as the boundary of the mechanism, not as a rescue case: the app
  //  works, so the control correctly stays hidden and My Content's own repair
  //  panel is what handles this.
  {
    const ctx = await browser.newContext();
    await ctx.addInitScript(() => {
      try {
        localStorage.setItem('plugins', '{:this is not (valid edn at all');
        localStorage.setItem('orcpub:no-cookie-banner', '1');
      } catch (e) {}
    });
    const page = await ctx.newPage();
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(14000);
    const v = await visible(page);
    check('unreadable blob -> app still boots, control stays hidden',
          v.display === 'none', JSON.stringify(v));
    await page.screenshot({ path: path.join(OUT, '4-unreadable-blob.png') });
    await ctx.close();
  }

  // ---- 5. sensitivity: no homebrew stored means no control ----------------
  {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);
    const v = await visible(page);
    check('nothing stored -> control never shows', v.display === 'none', JSON.stringify(v));
    await ctx.close();
  }

  await browser.close();
  const failed = results.filter(r => !r.pass);
  console.log('\n' + (results.length - failed.length) + '/' + results.length + ' checks passed');
  process.exitCode = failed.length ? 1 : 0;
})().catch(e => { console.error('PROBE ERROR', e); process.exit(1); });
