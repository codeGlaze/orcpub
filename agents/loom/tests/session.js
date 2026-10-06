const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
const ready = (p) => p.waitForFunction(() => !document.getElementById('save').disabled && items.length > 0, null, { timeout: 120000 });
(async () => { const b = await chromium.launch({ executablePath: EXE });
  const ctx = await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', process.env.PACKZIP); await ready(p);
  const n0 = await p.locator('.card').count();
  // an edit of each kind: an eye placement, a fill review, a patch, the pack version
  const eye = await p.evaluate(() => { const it = items.find(x => /eyes_01/i.test(x.name)); remember(it); it.irises[0].cx += 0.01; render(); return it.irises[0].cx; });
  await p.locator('.card', { hasText: 'L2_head_01.png' }).first().locator('.fill-btn').click();
  await p.locator('.card', { hasText: 'L2_head_01.png' }).first().locator('.fill-editor label', { hasText: 'Fill this piece' }).locator('input').uncheck();
  await p.evaluate(() => { const it = items.find(x => /hair_front_02/i.test(x.name)); const pt = patchState(it); for (let i = 1000; i < 1100; i++) pt.alpha[i] = 255; savePatch(it); render(); });
  await p.fill('#ver', 'v9.99'); await p.dispatchEvent('#ver', 'input');
  await p.waitForTimeout(900);
  // refresh
  await p.reload(); await ready(p);
  check(await p.locator('.card').count() === n0, `after a refresh the pack is back without dropping it again (${await p.locator('.card').count()} of ${n0} pieces)`);
  check(/Restored/.test(await p.locator('#dropmsg').textContent()), 'and it says so: ' + (await p.locator('#dropmsg').textContent()).slice(0, 80));
  check(Math.abs(await p.evaluate(() => items.find(x => /eyes_01/i.test(x.name)).irises[0].cx) - eye) < 1e-4, 'the eye placement edit is back');
  check(await p.locator('.card', { hasText: 'L2_head_01.png' }).first().locator('.fill-state', { hasText: 'Off' }).count() === 1, 'the fill review is back');
  check(await p.evaluate(() => patchedPx(items.find(x => /hair_front_02/i.test(x.name)))) === 100, 'the patch is back');
  check(await p.inputValue('#ver') === 'v9.99', 'the pack version is back');
  // dropping the pack again on top: replaced, not doubled
  await p.setInputFiles('#file', process.env.PACKZIP); await ready(p); await p.waitForTimeout(500);
  check(await p.locator('.card').count() === n0, 'dropping it again replaces rather than doubles');
  await p.reload(); await ready(p);
  check(await p.locator('.card').count() === n0, 'and a refresh after that still has one of each');
  // Clear forgets it
  await p.click('#clear'); await p.waitForTimeout(500);
  await p.reload(); await p.waitForTimeout(2500);
  check(await p.locator('.card').count() === 0, 'after Clear, a refresh starts empty');
  check(await p.evaluate(() => getComputedStyle(document.body).overscrollBehaviorY) === 'contain', 'pull-to-refresh is off');
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
