const { chromium } = require('playwright'); const fs = require('fs');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
(async () => { const b = await chromium.launch({ executablePath: EXE }); const p = await b.newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + process.env.LOOM);
  console.log('title:', await p.title(), '| badge:', await p.locator('#ver-badge').textContent(), '| errors:', errs.length); await b.close(); })();
