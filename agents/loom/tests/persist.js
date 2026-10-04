const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
const open = async (p, files) => { await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', files);
  await p.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 }); };
const head = (p) => p.locator('.card', { hasText: 'L2_head_01.png' });
const stat = async (p) => { const c = head(p); if (!(await c.locator('.fill-editor').count())) await c.locator('.fill-btn').click(); return head(p).locator('.fill-stats').textContent(); };
(async () => { const b = await chromium.launch({ executablePath: EXE });
  const errs = [];
  const ctx = await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } }); const p = await ctx.newPage(); p.on('pageerror', e => errs.push(String(e)));
  await open(p, process.env.PACKZIP);
  await head(p).locator('.fill-btn').click();
  const ed = head(p).locator('.fill-editor');
  // a wider edge margin, a keep stroke, and switch hair_front_01 off
  const em = ed.locator('label', { hasText: 'from the edge' }).locator('input'); await em.fill('5'); await em.dispatchEvent('change');
  await ed.locator('label', { hasText: 'Brush size' }).locator('input').fill('30');
  await ed.locator('.fill-row .chip', { hasText: 'Remove fill' }).click();
  const bb = await head(p).locator('.fill-scroll').boundingBox();
  await p.mouse.move(bb.x + bb.width * 0.3, bb.y + bb.height * 0.2); await p.mouse.down();
  for (let k = 0; k < 15; k++) await p.mouse.move(bb.x + bb.width * (0.3 + k * 0.02), bb.y + bb.height * (0.2 + k * 0.03)); await p.mouse.up();
  const s1 = await stat(p); console.log('edited:', s1);
  const c2 = p.locator('.card', { hasText: 'L4_hair_front_01.png' }); await c2.locator('.fill-btn').click();
  await c2.locator('.fill-editor label', { hasText: 'Fill this piece' }).locator('input').uncheck();
  // 1. a reload brings it back
  await open(p, process.env.PACKZIP);
  check(await stat(p) === s1, 'after a reload the marks and settings are back: ' + await stat(p));
  check(await p.locator('.card', { hasText: 'L4_hair_front_01.png' }).locator('.fill-state', { hasText: 'Off' }).count() === 1, 'and a piece switched off stays off');
  // 2. the manifest carries it to a fresh browser
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#manifest')]); const mf = process.env.OUT + '/manifest-review.json'; await dl.saveAs(mf);
  const ctx2 = await b.newContext({ viewport: { width: 1300, height: 1000 } }); const p2 = await ctx2.newPage(); p2.on('pageerror', e => errs.push(String(e)));
  await open(p2, process.env.PACKZIP);
  check(/2663 found automatically · 0 removed by you · 0 added by you$/.test((await stat(p2)).trim()) , 'a fresh browser starts clean: ' + await stat(p2));
  await open(p2, [process.env.PACKZIP, mf]);
  check(await stat(p2) === s1, 'dropping the manifest with the art restores the review: ' + await stat(p2));
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
