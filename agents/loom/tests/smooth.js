const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
const open = async (p) => { await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', process.env.PACKZIP);
  await p.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 }); };
(async () => { const b = await chromium.launch({ executablePath: EXE }); const ctx = await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await open(p);
  const card = () => p.locator('.card', { hasText: 'L4_hair_front_01.png' }); const ed = () => card().locator('.fill-editor');
  await card().locator('.fill-btn').click();
  // speckle: drawn pixels whose green differs sharply from the pixel beside them
  const speckle = () => ed().locator('canvas').evaluate(c => { const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data, w = c.width; let n = 0;
    for (let i = 4; i < d.length - 4 * w; i += 4) { if (d[i] < 60 && d[i+1] > 240) continue;
      const g = d[i+1] - d[i], gr = d[i+5] - d[i+4], gd = d[i+4*w+1] - d[i+4*w]; if (Math.abs(g - gr) > 25 || Math.abs(g - gd) > 25) n++; } return n; });
  const tint = () => ed().locator('canvas').evaluate(c => { const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; let n = 0;
    for (let i = 0; i < d.length; i += 4) { const r = d[i], g = d[i+1], bl = d[i+2]; if (g > 200 && r > 60 && r < 235 && g - r > 15 && Math.abs(r - bl) < 25) n++; } return n; });
  const orange = () => ed().locator('canvas').evaluate(c => { const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; let n = 0;
    for (let i = 0; i < d.length; i += 4) if (d[i] === 255 && d[i+1] === 140 && d[i+2] === 0) n++; return n; });
  const stat = async () => (await ed().locator('.fill-stats').textContent()).trim();
  const box = () => ed().locator('label', { hasText: 'Smooth small holes' }).locator('input');
  check(!(await box().isChecked()), 'smoothing starts off');
  await ed().locator('label', { hasText: 'Show leaks' }).locator('input').check();
  await ed().locator('.fill-row .chip', { hasText: 'After' }).click();
  // grain in the Loom's own result (what the export writes), full size: pixels
  // more see-through than the median of the 5x5 around them by over 10
  const grain = () => p.evaluate(() => { const it = items.find(x => /hair_front_01/i.test(x.name)), e = effectiveFill(it);
    const w = it.processed.width, h = it.processed.height, px = e.px; let n = 0; const win = [];
    for (let y = 2; y < h - 2; y++) for (let x = 2; x < w - 2; x++) { const a = px[4 * (y * w + x) + 3]; if (a === 0 || a === 255) continue;
      win.length = 0; for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) win.push(px[4 * ((y + dy) * w + x + dx) + 3]);
      win.sort((p, q) => p - q); if (win[12] - a > 10) n++; } return n; });
  const off = await grain();
  await box().check();
  const on = await grain(); const s1 = await stat();
  check(/(\d+) smoothed/.test(s1) && +s1.match(/(\d+) smoothed/)[1] > 500, 'ticking it smooths: ' + s1);
  check(on < off * 0.6, `and the grain left after the fill drops (${off} -> ${on} grainy px, full size)`);
  await ed().screenshot({ path: process.env.OUT + '/smooth-after.png' });
  await ed().locator('.fill-row .chip', { hasText: 'Overlay' }).click();
  check(await orange() > 100, 'the overlay shows the smoothed pixels orange');
  // Remove fill keeps a spot as drawn: paint over the whole picture, smoothing goes to zero
  await ed().locator('.fill-row .chip', { hasText: 'Remove fill' }).click();
  await ed().locator('label', { hasText: 'Brush size' }).locator('input').fill('30');
  const bb = await card().locator('.fill-scroll').boundingBox(); const n0 = +s1.match(/(\d+) smoothed/)[1];
  await p.mouse.move(bb.x + bb.width * 0.5, bb.y + bb.height * 0.2); await p.mouse.down();
  for (let k = 0; k < 20; k++) await p.mouse.move(bb.x + bb.width * (0.3 + k * 0.02), bb.y + bb.height * (0.1 + k * 0.01)); await p.mouse.up();
  const s2 = await stat(); check(+s2.match(/(\d+) smoothed/)[1] < n0, 'Remove fill keeps those spots as drawn: ' + s2);
  await card().locator('button', { hasText: 'Undo' }).click();
  check(await stat() === s1, 'Undo restores it');
  // it survives a reload
  await open(p); await card().locator('.fill-btn').click();
  check(await box().isChecked() && await stat() === s1, 'after a reload smoothing is still on: ' + await stat());
  // the downloaded pack carries the smoothed art
  await p.check('#fill-insides'); await p.waitForTimeout(300);
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#save')]); await dl.saveAs(process.env.OUT + '/smooth-pack.zip');
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
