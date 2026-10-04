// The save banner's export link must produce a file holding BOTH the content
// that was already in that source and the item just saved. Documented history
// (docs/HOMEBREW_DATA_LOSS.md, S1) is that this link has broken twice by
// mishandling what it was handed, and each time the failure was invisible until
// someone opened the file. So this asserts on the FILE, not on the click.
//
// Also covers the developer-mode toggle, because the raw dump behind it is the
// fallback when this link refuses.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import.js');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.PROBE_OUT || '/tmp/builder-card-export';
const SRC = 'Long Lived Source';

// Already in the store before this session starts: the "existing for a while"
// half. Complete per docs/HOMEBREW_REQUIRED_FIELDS.md (name, key, option-pack,
// level, school, spell-lists) so the export gate has no reason to stop — the
// point of this case is what lands in the file, not whether the gate fires.
const EXISTING = `{"${SRC}" {:orcpub.dnd.e5/spells {:old-faithful {:key :old-faithful :option-pack "${SRC}" :name "Old Faithful" :level 3 :school "evocation" :spell-lists {:wizard true}}}}}`;

// The same source with one incomplete entry, for the case that must NOT write a file.
const WITH_A_GAP = `{"${SRC}" {:orcpub.dnd.e5/spells {:old-faithful {:key :old-faithful :option-pack "${SRC}" :name "Old Faithful" :level 3 :school "evocation" :spell-lists {:wizard true}} :half-done {:key :half-done :option-pack "${SRC}" :name "Half Done"}}}}`;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.CHROME || '/opt/pw-browsers/chromium' });
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1280, height: 900 } });
  // Seed ONCE. addInitScript runs on every document, so an unconditional write
  // re-seeds on reload and silently discards whatever the session saved — which
  // is exactly the "the new half went missing" symptom this test exists to catch.
  await ctx.addInitScript(v => {
    try {
      if (!localStorage.getItem('plugins')) { localStorage.setItem('plugins', v); }
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  }, EXISTING);
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });

  // --- save a brand-new spell into the source that already holds one ---------
  await page.goto(BASE + '/pages/dnd/5e/spell-builder', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#plugins-choice', { timeout: 180000 });
  await page.waitForTimeout(800);
  await page.evaluate(() => {
    const lab = [...document.querySelectorAll('div')].find(e => e.textContent.trim() === 'Name');
    const box = lab && lab.parentElement.querySelector('input');
    const set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    if (box) { set.call(box, 'Brand New Bolt'); box.dispatchEvent(new Event('input', { bubbles: true })); }
  });
  await page.fill('#plugins-choice', SRC);
  // Level and school are spec-required for a spell; leaving them at nothing is
  // what made an earlier run of this test measure the gate instead of the file.
  await page.selectOption('select', { label: '3rd-level' }).catch(() => {});
  const schoolSel = page.locator('select').nth(1);
  await schoolSel.selectOption({ index: 1 }).catch(() => {});
  await page.waitForTimeout(400);
  await page.locator('button, .form-button').filter({ hasText: /^save/i }).first().click();
  await page.waitForSelector('.message', { timeout: 15000 });
  await page.waitForTimeout(700);

  const link = page.locator('.message .pointer.underline').first();
  check('the save banner offers a working link', await link.count() > 0,
        await link.innerText().catch(() => 'NONE'));
  await page.screenshot({ path: path.join(OUT, '1-save-banner.png') });

  // --- the file is the claim ------------------------------------------------
  const [dl] = await Promise.all([
    page.waitForEvent('download', { timeout: 30000 }),
    link.click(),
  ]);
  const file = path.join(OUT, 'export.orcbrew');
  await dl.saveAs(file);
  const text = fs.readFileSync(file, 'utf8');

  check('the pre-existing spell is in the file', /Old Faithful/.test(text),
        'the half that was already stored');
  check('the just-saved spell is in the file', /Brand New Bolt/.test(text),
        'the half saved seconds ago');
  check('the file is the source, not its printed source code',
        !/^\s*\[:/.test(text) && text.includes(SRC), text.slice(0, 60));
  console.log('   file: ' + dl.suggestedFilename() + ' ' + text.length + 'B');

  // --- the file has to parse back ------------------------------------------
  //  Contents matching is not the claim that matters; re-importable is. Checked
  //  in a CLEAN store so nothing already present can make it look successful.
  {
    const fresh = await browser.newContext({ acceptDownloads: true });
    await suppressCookieBanner(fresh);
    await fresh.addInitScript(() => { try { localStorage.setItem('whats-new-seen','"summer-patch-2026"'); } catch (e) {} });
    const fp = await fresh.newPage();
    await fp.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
    await fp.waitForSelector('text=/My Content|MY CONTENT/i', { timeout: 180000 });
    await importPack(fp, file).catch(() => {});
    await fp.waitForTimeout(2500);
    const back = await fp.evaluate(() => { try { return localStorage.getItem('plugins') || ''; } catch (e) { return ''; } });
    check('the file re-imports into a clean store',
          /Old Faithful/.test(back) && /Brand New Bolt/.test(back),
          back ? back.slice(0, 80) : '(nothing stored)');
    check('and the source keeps its name', back.includes(SRC),
          (back.match(/"[^"]+" \{/g) || []).join(' '));
    await fp.screenshot({ path: path.join(OUT, '5-reimported.png') });
    await fresh.close();
  }

  // --- what the file does NOT carry ----------------------------------------
  //  A per-source export drops the {"Source" {...}} wrapper the store uses, so
  //  the source name survives only inside each item's :option-pack. When items
  //  agree, import reconstructs it (the check above). When they disagree, the
  //  name comes from the FILENAME instead — rename the file, rename the source.
  //  Pinned as CURRENT BEHAVIOUR, not as desirable: if the export is ever
  //  wrapped like the raw dump is, this check fails and should be deleted.
  check('the file itself names its source nowhere but inside its items',
        !text.trimStart().startsWith('{"'),
        text.trimStart().slice(0, 40));

  // --- developer mode -------------------------------------------------------
  await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.dev-mode-switch', { timeout: 180000 });
  const toolsWhenOff = await page.locator('.dev-mode-tool').count();
  check('switch is visible, tools are not', toolsWhenOff === 0, 'tools: ' + toolsWhenOff);
  await page.locator('.dev-mode-switch').first().scrollIntoViewIfNeeded();
  await page.screenshot({ path: path.join(OUT, '2-dev-mode-off.png') });

  await page.locator('.dev-mode-switch').first().click();
  await page.waitForTimeout(400);
  const labels = await page.locator('.dev-mode-tool').allInnerTexts();
  check('switching on reveals the named tools', labels.length === 2,
        JSON.stringify(labels));
  await page.screenshot({ path: path.join(OUT, '3-dev-mode-on.png') });

  // The point of persisting it: someone reaching for these is about to refresh.
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.dev-mode-switch', { timeout: 180000 });
  await page.waitForTimeout(1200);
  check('the choice survives a refresh', await page.locator('.dev-mode-tool').count() === 2,
        'tools after reload: ' + await page.locator('.dev-mode-tool').count());

  const [rawDl] = await Promise.all([
    page.waitForEvent('download', { timeout: 30000 }),
    page.locator('.dev-mode-tool', { hasText: 'Dump library' }).click(),
  ]);
  const rawFile = path.join(OUT, 'dump.orcbrew');
  await rawDl.saveAs(rawFile);
  const raw = fs.readFileSync(rawFile, 'utf8');
  check('the raw dump carries both halves too',
        /Old Faithful/.test(raw) && /Brand New Bolt/.test(raw),
        rawDl.suggestedFilename() + ' ' + raw.length + 'B');
  await page.screenshot({ path: path.join(OUT, '4-dev-mode-after-reload.png') });

  console.log('CONSOLE ERRORS: ' + (errors.length ? errors.join(' | ') : 'none'));
  await browser.close();
  const failed = results.filter(r => !r.pass);
  console.log('\n' + (results.length - failed.length) + '/' + results.length + ' checks passed');
  process.exitCode = failed.length ? 1 : 0;
})().catch(e => { console.error('PROBE ERROR', e.message); process.exit(1); });
