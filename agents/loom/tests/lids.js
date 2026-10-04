const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
(async () => { const b = await chromium.launch({ executablePath: EXE }); const p = await (await b.newContext({ viewport: { width: 1300, height: 1000 } })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', process.env.PACKZIP);
  await p.waitForFunction(() => document.querySelectorAll('canvas.iris-canvas').length >= 3, null, { timeout: 120000 });
  const ed = p.locator('.iris-editor').first(), cv = ed.locator('canvas');
  await ed.locator('label', { hasText: 'paint whites' }).locator('input').check();
  await cv.scrollIntoViewIfNeeded();
  const item = () => p.evaluate(() => JSON.stringify(items.find(i => /eyes_01/i.test(i.name)).irises[0]));
  // put the lower lid's end exactly on the upper lid's end, the overlap that kept catching
  const up = await p.evaluate(() => document.querySelector('canvas.iris-canvas').__iris.lidPoint(0, 'lid', '0'));
  const lo = await p.evaluate(() => document.querySelector('canvas.iris-canvas').__iris.lidPoint(0, 'lower', '0'));
  check(!!up && !!lo, 'both lids are showing');
  let box = await cv.boundingBox();
  await p.mouse.move(box.x + lo.x, box.y + lo.y); await p.mouse.down(); await p.mouse.move(box.x + up.x, box.y + up.y, { steps: 5 }); await p.mouse.up();
  const before = JSON.parse(await item());
  // unlocked, a tap there grabs the upper lid (selected one wins) -- the problem
  await p.mouse.move(box.x + up.x, box.y + up.y); await p.mouse.down(); await p.mouse.move(box.x + up.x - 12, box.y + up.y + 12, { steps: 4 }); await p.mouse.up();
  const unl = JSON.parse(await item());
  check(unl.lid.x0 !== before.lid.x0, 'unlocked, the overlapping tap catches the upper lid (the problem)');
  await ed.locator('button', { hasText: 'Undo' }).click();
  check(JSON.stringify(JSON.parse(await item()).lid) === JSON.stringify(before.lid), 'Undo restores the upper lid');
  // lock the upper lid: the same tap now moves the lower lid
  await ed.locator('input[data-lock="lid"]').check();
  await p.mouse.move(box.x + up.x, box.y + up.y); await p.mouse.down(); await p.mouse.move(box.x + up.x - 12, box.y + up.y + 12, { steps: 4 }); await p.mouse.up();
  const lk = JSON.parse(await item());
  check(JSON.stringify(lk.lid) === JSON.stringify(before.lid), 'with the upper lid locked, it does not move');
  check(lk.lower.x0 !== before.lower.x0, 'and the tap moves the lower lid instead');
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
