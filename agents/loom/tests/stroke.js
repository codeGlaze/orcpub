const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
(async () => { const b = await chromium.launch({ executablePath: EXE }); const p = await (await b.newContext({ viewport: { width: 1300, height: 1000 } })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', process.env.PACKZIP);
  await p.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 });
  // art pixel -> screen, for the open editor of a piece
  const toScreen = (name, sel, ax, ay) => p.evaluate(([name, sel, ax, ay]) => {
    const it = items.find(x => x.name === name), box = drawnBounds(it.processed, 0.04);
    const card = [...document.querySelectorAll('.card')].find(c => c.textContent.includes(name));
    const cv = card.querySelector(sel + ' canvas'), r = cv.getBoundingClientRect(), k = cv.width / box.w;
    return [r.left + (ax - box.x) * k * (r.width / cv.width), r.top + (ay - box.y) * k * (r.height / cv.height)]; }, [name, sel, ax, ay]);
  // one quick stroke: a single jump from A to B
  // bring the middle of A-B into view first: zoomed in, it may be scrolled away
  const centre = (name, sel, A, B) => p.evaluate(([name, sel, A, B]) => {
    const it = items.find(x => x.name === name), box = drawnBounds(it.processed, 0.04);
    const card = [...document.querySelectorAll('.card')].find(c => c.textContent.includes(name));
    const sc = card.querySelector(sel + ' .fill-scroll'), cv = sc.querySelector('canvas'), k = cv.width / box.w;
    sc.scrollLeft = ((A[0] + B[0]) / 2 - box.x) * k - sc.clientWidth / 2; sc.scrollTop = ((A[1] + B[1]) / 2 - box.y) * k - sc.clientHeight / 2;
    sc.scrollIntoView({ block: 'center' }); }, [name, sel, A, B]);
  const quick = async (name, sel, A, B) => { await centre(name, sel, A, B); await p.waitForTimeout(100);
    const a = await toScreen(name, sel, ...A), c = await toScreen(name, sel, ...B);
    await p.mouse.move(...a); await p.mouse.down(); await p.mouse.move(...c, { steps: 1 }); await p.mouse.up(); await p.waitForTimeout(300); };
  // how many art pixels along the line A-B are not painted
  const holes = (name, field, A, B) => p.evaluate(([name, field, A, B]) => {
    const it = items.find(x => x.name === name), W = it.processed.width;
    const arr = field === 'patch' ? it.patch.alpha : it.fill.add; let miss = 0, n = 0;
    const len = Math.hypot(B[0] - A[0], B[1] - A[1]);
    for (let t = 0; t <= len; t += 1) { const x = Math.round(A[0] + (B[0] - A[0]) * t / len), y = Math.round(A[1] + (B[1] - A[1]) * t / len); n++; if (!arr[y * W + x]) miss++; }
    return [miss, n]; }, [name, field, A, B]);
  // Paint, in Patch gaps
  const pc = p.locator('.card', { hasText: 'L4_hair_front_02.png' }); await pc.locator('.patch-btn').click();
  const pe = pc.locator('.patch-editor');
  await pe.locator('.fill-row .chip', { hasText: 'Paint' }).click(); await pe.locator('label', { hasText: 'Brush size' }).locator('input').fill('4');
  for (const z of ['1', '2.5']) {
    await p.locator('.card', { hasText: 'L4_hair_front_02.png' }).locator('.patch-editor label', { hasText: 'Zoom' }).locator('input').fill(z);
    const A = [190, 120 + (z === '1' ? 0 : 30)], B = [230, 150 + (z === '1' ? 0 : 30)];
    await quick('L4_hair_front_02.png', '.patch-editor', A, B);
    const [miss, n] = await holes('L4_hair_front_02.png', 'patch', A, B);
    check(miss === 0, `Paint at zoom ${z}: one quick stroke is a solid line (${n - miss} of ${n} px along it painted)`);
  }
  await pe.screenshot({ path: process.env.OUT + '/stroke-patch.png' });
  await pc.locator('.patch-btn').click();   // close it: its piece lists name other files
  // Add fill, in the fill review
  const fc = p.locator('.card', { hasText: 'L2_head_01.png' }); await fc.locator('.fill-btn').click();
  const fe = fc.locator('.fill-editor');
  await fe.locator('.fill-row .chip', { hasText: 'Add fill' }).click(); await fe.locator('label', { hasText: 'Brush size' }).locator('input').fill('4');
  const A = [200, 200], B = [260, 240];
  await quick('L2_head_01.png', '.fill-editor', A, B);
  const [miss, n] = await holes('L2_head_01.png', 'fill', A, B);
  check(miss === 0, `Add fill: one quick stroke is a solid line (${n - miss} of ${n} px along it marked)`);
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
