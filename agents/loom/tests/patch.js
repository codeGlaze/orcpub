const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
const open = async (p, files) => { await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', files);
  await p.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 }); };
(async () => { const b = await chromium.launch({ executablePath: EXE });
  const errs = [];
  const ctx = await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } }); const p = await ctx.newPage(); p.on('pageerror', e => errs.push(String(e)));
  await open(p, process.env.PACKZIP);
  const cards = await p.locator('.card').count(), btns = await p.locator('.patch-btn').count();
  check(btns === cards && cards >= 28, `every piece has Patch gaps (${btns} of ${cards})`);
  const card = () => p.locator('.card', { hasText: 'L4_hair_front_02.png' }); const ed = () => card().locator('.patch-editor');
  await card().locator('.patch-btn').click();
  check(/L2_head_01/i.test(await ed().locator('label', { hasText: 'Show under it' }).locator('select').inputValue()), 'it shows a head under it to start');
  await ed().locator('label', { hasText: 'Show over it' }).locator('select').selectOption({ label: 'bangs · L9_bangs_03.png' });
  const stat = async () => (await ed().locator('.fill-stats').textContent()).trim();
  check(/^0 px patched/.test(await stat()), 'nothing patched to start: ' + await stat());
  // brush Off: a drag changes nothing
  const toScreen = async (ax, ay) => p.evaluate(([ax, ay]) => {
    const it = items.find(x => /hair_front_02/i.test(x.name)), box = drawnBounds(it.processed, 0.04);
    const cv = [...document.querySelectorAll('.patch-editor canvas')][0], r = cv.getBoundingClientRect(), k = cv.width / box.w;
    return [r.left + (ax - box.x) * k * (r.width / cv.width), r.top + (ay - box.y) * k * (r.height / cv.height)]; }, [ax, ay]);
  const stroke = async () => { const pts = []; for (let y = 132; y <= 184; y += 3) pts.push(await toScreen(205, y));
    await p.mouse.move(...pts[0]); await p.mouse.down(); for (const q of pts) await p.mouse.move(...q); await p.mouse.up(); };
  await stroke();
  check(/^0 px patched/.test(await stat()), 'with the brush Off, a drag paints nothing');
  await ed().locator('.fill-row .chip', { hasText: 'Paint' }).click();
  await ed().locator('label', { hasText: 'Brush size' }).locator('input').fill('12');
  await stroke();
  const s1 = await stat(); const n1 = +s1.match(/^(\d+)/)[1];
  check(n1 > 300, 'painting down the gap patches it: ' + s1);
  await ed().screenshot({ path: process.env.OUT + '/patch-editor.png' });
  await ed().locator('button', { hasText: 'Undo' }).click();
  check(/^0 px patched/.test(await stat()), 'Undo takes it off');
  await ed().locator('button', { hasText: 'Redo' }).click();
  check(await stat() === s1, 'Redo puts it back');
  check(await card().locator('.fill-state', { hasText: `Patched · ${n1} px` }).count() === 1, 'the card says it is patched');
  // reload
  await open(p, process.env.PACKZIP);
  check(await card().locator('.fill-state', { hasText: `Patched · ${n1} px` }).count() === 1, 'after a reload the patch is still there');
  // export
  await p.check('#fill-insides'); await p.waitForTimeout(300);
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#save')]); const zp = process.env.OUT + '/patch-pack.zip'; await dl.saveAs(zp);
  const [dm] = await Promise.all([p.waitForEvent('download'), p.click('#manifest')]); const mf = process.env.OUT + '/manifest-patch.json'; await dm.saveAs(mf);
  check(JSON.parse(fs.readFileSync(mf, 'utf8')).layers.some(l => l.assets.some(a => a.patch && a.patch.px === n1)), 'the manifest carries the patch');
  // a fresh browser, art plus manifest
  const ctx2 = await b.newContext({ viewport: { width: 1300, height: 1000 } }); const p2 = await ctx2.newPage(); p2.on('pageerror', e => errs.push(String(e)));
  await open(p2, [process.env.PACKZIP, mf]);
  check(await p2.locator('.card', { hasText: 'L4_hair_front_02.png' }).locator('.fill-state', { hasText: `Patched · ${n1} px` }).count() === 1, 'dropping the manifest with the art brings the patch to a fresh browser');
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
