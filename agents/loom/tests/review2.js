const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
(async () => { const b = await chromium.launch({ executablePath: EXE }); const ctx = await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', process.env.PACKZIP);
  await p.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 });
  check(await p.locator('#viewbar .chip', { hasText: 'See-through' }).count() === 1, 'a See-through filter chip');
  await p.locator('.summary .pill.tap').click();
  const cards = await p.locator('.card').count(); const eds = await p.locator('.fill-editor').count();
  check(cards >= 20 && eds === cards, `tapping the count shows only those pieces (${cards}), reviews open (${eds})`);
  check(await p.locator('.fill-state', { hasText: 'Reviewed' }).count() === cards, 'opened reviews are marked Reviewed');
  const card = p.locator('.card', { hasText: 'L2_head_01.png' }); const ed = card.locator('.fill-editor');
  const sc = ed.locator('.fill-scroll'); const before = await sc.boundingBox(); const edBefore = await ed.boundingBox();
  const z = ed.locator('label', { hasText: 'Zoom' }).locator('input');
  for (const v of ['1.5', '2.5', '4']) { await z.fill(v); }
  const after = await sc.boundingBox(); const edAfter = await ed.boundingBox();
  check(Math.abs(after.width - before.width) < 1 && Math.abs(after.height - before.height) < 1, `zooming keeps the box the same size (${Math.round(before.width)}x${Math.round(before.height)} -> ${Math.round(after.width)}x${Math.round(after.height)})`);
  check(Math.abs(edAfter.height - edBefore.height) < 2, 'and the panel does not reflow');
  check(await sc.evaluate(e => e.scrollWidth > e.clientWidth * 3), 'the picture zooms inside it');
  // the brush starts Off: a drag scrolls the zoomed picture and changes nothing
  const cv = ed.locator('canvas'); const bb = await sc.boundingBox();
  check(await ed.locator('.fill-row .chip.on').filter({ hasText: 'Off' }).count() === 1, 'the brush starts Off');
  const st0 = await ed.locator('.fill-stats').textContent(); const sl0 = await sc.evaluate(e => e.scrollLeft);
  await p.mouse.move(bb.x + bb.width / 2, bb.y + bb.height / 2); await p.mouse.down(); await p.mouse.move(bb.x + bb.width / 2 - 60, bb.y + bb.height / 2 - 30, { steps: 5 }); await p.mouse.up();
  check(await ed.locator('.fill-stats').textContent() === st0 && await p.locator('.card', { hasText: 'L2_head_01.png' }).locator('.fill-state', { hasText: 'Edited' }).count() === 0, 'and dragging with it changes nothing');
  check(await sc.evaluate(e => e.scrollLeft) > sl0 + 30, 'but scrolls the picture');
  // brush an edit while zoomed, then check zoom survived the rebuild
  await ed.locator('.fill-row .chip', { hasText: 'Remove fill' }).click();
  await p.mouse.move(bb.x + bb.width / 2, bb.y + bb.height / 2); await p.mouse.down(); await p.mouse.move(bb.x + bb.width / 2 + 20, bb.y + bb.height / 2 + 10); await p.mouse.up();
  await p.waitForTimeout(200);
  const ed2 = p.locator('.card', { hasText: 'L2_head_01.png' }).locator('.fill-editor');
  check(await ed2.locator('label', { hasText: 'Zoom' }).locator('input').inputValue() === '4', 'zoom survives an edit');
  check(await p.locator('.card', { hasText: 'L2_head_01.png' }).locator('.fill-state', { hasText: 'Edited' }).count() === 1, 'and the piece shows Edited');
  await ed2.screenshot({ path: process.env.OUT + '/review-zoom.png' });
  await p.screenshot({ path: process.env.OUT + '/review-page.png' });
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
