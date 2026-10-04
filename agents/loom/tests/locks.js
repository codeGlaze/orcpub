const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
(async () => {
  const b = await chromium.launch({ executablePath: EXE }); const ctx = await b.newContext({ viewport: { width: 1300, height: 1000 } }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + process.env.LOOM);
  await p.setInputFiles('#file', process.env.PACKZIP);
  await p.waitForFunction(() => document.querySelectorAll('canvas.iris-canvas').length >= 3, null, { timeout: 120000 });
  check(true, 'loaded, ' + await p.locator('canvas.iris-canvas').count() + ' eye editors');
  // state of the first editor's item via its api
  const state = () => p.evaluate(() => { const c = document.querySelector('canvas.iris-canvas'); const e = c.__iris; return JSON.stringify(c.__iris ? null : null); });
  const cv = p.locator('canvas.iris-canvas').first();
  await cv.scrollIntoViewIfNeeded();
  const box = await cv.boundingBox();
  // find the selected lid's middle handle, via the editor helpers if present, else the item through a probe
  const lidM = await p.evaluate(() => {
    const c = document.querySelector('canvas.iris-canvas');
    return c.__iris && c.__iris.lidAt ? c.__iris.lidAt(0, 'm') : null; });
  const getItem = () => p.evaluate(() => { const it = window.__loomItems ? window.__loomItems() : null; return it; });
  // drag the centre of iris 0
  const c0 = await p.evaluate(() => document.querySelector('canvas.iris-canvas').__iris.centreAt(0));
  const before = await p.evaluate(() => document.querySelector('canvas.iris-canvas').toDataURL());
  await p.mouse.move(box.x + c0.x, box.y + c0.y); await p.mouse.down(); await p.mouse.move(box.x + c0.x + 15, box.y + c0.y + 5, { steps: 4 }); await p.mouse.up();
  const moved = await p.evaluate(() => document.querySelector('canvas.iris-canvas').toDataURL());
  check(moved !== before, 'dragging the iris moves it');
  const undo = p.locator('.iris-editor').first().locator('button', { hasText: 'Undo' });
  check(!(await undo.isDisabled()), 'Undo lights up after a change');
  await undo.click();
  const undone = await p.evaluate(() => document.querySelector('canvas.iris-canvas').toDataURL());
  check(undone === before, 'Undo puts it back exactly');
  await p.locator('.iris-editor').first().locator('button', { hasText: 'Redo' }).click();
  check(await p.evaluate(() => document.querySelector('canvas.iris-canvas').toDataURL()) === moved, 'Redo moves it again');
  await p.keyboard.press('Control+z');
  check(await p.evaluate(() => document.querySelector('canvas.iris-canvas').toDataURL()) === before, 'Ctrl+Z undoes too');
  // lock the iris: a drag in the middle does nothing
  await p.locator('.iris-editor').first().locator('input[data-lock="iris"]').check();
  check(await p.locator('input[data-lock="iris"]:checked').count() === await p.locator('canvas.iris-canvas').count(), 'locking one editor locks the iris in every editor');
  await p.locator('.iris-editor').first().locator('input[data-lock="pupil"]').check();
  await cv.scrollIntoViewIfNeeded();
  const box3 = await cv.boundingBox(); Object.assign(box, box3);
  const locked0 = await p.evaluate(() => document.querySelector('canvas.iris-canvas').toDataURL());
  await p.mouse.move(box.x + c0.x, box.y + c0.y); await p.mouse.down(); await p.mouse.move(box.x + c0.x + 15, box.y + c0.y + 5, { steps: 4 }); await p.mouse.up();
  check(await p.evaluate(() => document.querySelector('canvas.iris-canvas').toDataURL()) === locked0, 'with iris and pupil locked, a drag on the iris does nothing');
  check(await p.locator('.iris-editor').first().locator('input[type=range]').first().isDisabled(), 'and its rotate slider is disabled');
  // locks survive a reload
  await p.reload(); await p.setInputFiles('#file', process.env.PACKZIP);
  await p.waitForFunction(() => document.querySelectorAll('canvas.iris-canvas').length >= 3, null, { timeout: 120000 });
  check(await p.locator('input[data-lock="iris"]').first().isChecked(), 'the lock is remembered after a reload');
  await p.locator('input[data-lock="iris"]').first().uncheck();
  await p.locator('input[data-lock="pupil"]').first().uncheck();
  console.log('step: unlocked');
  // edits survive a Height change
  await p.locator('canvas.iris-canvas').first().scrollIntoViewIfNeeded();
  const box2 = await p.locator('canvas.iris-canvas').first().boundingBox();
  // grab the iris itself: halfway to its rim, clear of the pupil dot at the centre
  const c1 = await p.evaluate(() => { const a = document.querySelector('canvas.iris-canvas').__iris;
    const c = a.centreAt(0), h = a.handleAt(0, 'x'); return { x: (c.x + h.x) / 2, y: (c.y + h.y) / 2 }; });
  await p.mouse.move(box2.x + c1.x, box2.y + c1.y); await p.mouse.down(); await p.mouse.move(box2.x + c1.x + 20, box2.y + c1.y, { steps: 4 }); await p.mouse.up();
  const exportManifest = async () => { const [dl] = await Promise.all([p.waitForEvent('download', { timeout: 15000 }), p.click('#manifest')]); const f = process.env.OUTDIR + '/m.json'; await dl.saveAs(f); return JSON.parse(fs.readFileSync(f)); };
  const cxOf = (m) => { for (const l of m.layers) for (const a of l.assets) if (a.iris && /eyes_01/i.test(a.file)) return a.iris[0].cx; };
  console.log('step: dragged'); const editedCx = cxOf(await exportManifest()); console.log('step: exported', editedCx);
  await p.fill('#height', '600'); await p.dispatchEvent('#height', 'change');
  await p.waitForFunction(() => /reprocessing/.test(document.getElementById('dropmsg').textContent), null, { timeout: 30000 }).catch(() => {});
  await p.waitForFunction(() => document.querySelectorAll('canvas.iris-canvas').length >= 3 && !/reprocessing/.test(document.getElementById('dropmsg').textContent), null, { timeout: 120000 });
  await p.waitForTimeout(500);
  console.log('step: after height', JSON.stringify({ msg: await p.locator('#dropmsg').textContent(), manifestDisabled: await p.locator('#manifest').isDisabled(), editors: await p.locator('canvas.iris-canvas').count(), errs }));
  p.on('console', m => console.log('console:', m.type(), m.text().slice(0, 200)));
  const afterCx = await p.evaluate(() => items.find(i => /eyes_01/i.test(i.name)).irises[0].cx);
  check(Math.abs(afterCx - editedCx) < 1e-4, `an edit survives changing Height (${editedCx} -> ${afterCx})`);
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0);
})();
