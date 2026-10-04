const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
(async () => { const b = await chromium.launch({ executablePath: EXE }); const ctx = await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', process.env.PACKZIP);
  await p.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 });
  check(await p.locator('.fill-btn').count() >= 20, 'pieces with see-through insides get a Review fill panel');
  // head 01
  const card = p.locator('.card', { hasText: 'L2_head_01.png' });
  await card.locator('.fill-btn').click();
  const ed = card.locator('.fill-editor'); await ed.waitFor();
  const stat = () => ed.locator('.fill-stats').textContent();
  const s0 = await stat(); check(/2663 px will be filled/.test(s0), 'starts at the automatic pick: ' + s0);
  await ed.screenshot({ path: process.env.OUT + '/review-overlay.png' });
  // brush out a big patch of the fill
  const cv = ed.locator('canvas'); await cv.scrollIntoViewIfNeeded(); const bb = await cv.boundingBox();
  await ed.locator('label', { hasText: 'Brush size' }).locator('input').fill('30');
  await ed.locator('.fill-row .chip', { hasText: 'Remove fill' }).click();
  await p.mouse.move(bb.x + bb.width * 0.3, bb.y + bb.height * 0.3); await p.mouse.down();
  for (let k = 0; k < 20; k++) await p.mouse.move(bb.x + bb.width * (0.3 + k * 0.02), bb.y + bb.height * (0.3 + k * 0.015));
  await p.mouse.up();
  const s1 = await stat(); const n1 = +s1.match(/^(\d+) px/)[1]; check(n1 < 2663 && /removed by you/.test(s1), 'brushing "Remove fill" removes part of it: ' + s1);
  await ed.locator('button', { hasText: 'Undo' }).click();
  check(/2663 px will be filled/.test(await stat()), 'Undo restores it');
  await ed.locator('button', { hasText: 'Redo' }).click();
  check(+(await stat()).match(/^(\d+) px/)[1] === n1, 'Redo removes it again');
  // tighter automatic pick
  await ed.locator('label', { hasText: 'from the edge' }).locator('input').fill('6'); await ed.locator('label', { hasText: 'from the edge' }).locator('input').dispatchEvent('change');
  const s2 = await stat(); check(+s2.match(/^(\d+) px/)[1] < n1, 'a wider edge margin fills less: ' + s2);
  await ed.screenshot({ path: process.env.OUT + '/review-edited.png' });
  // switch off another piece entirely
  const c2 = p.locator('.card', { hasText: 'L4_hair_front_01.png' });
  await c2.locator('.fill-btn').click();
  await c2.locator('.fill-editor label', { hasText: 'Fill this piece' }).locator('input').uncheck();
  // export and check
  await p.check('#fill-insides');
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#save')]);
  await dl.saveAs(process.env.OUT + '/reviewed.zip');
  console.log('HEADSTAT', (await stat()).match(/^(\d+) px/)[1]);
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
