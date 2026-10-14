const { chromium } = require(process.cwd() + '/node_modules/playwright');
const fs = require('fs'); const S = process.argv[2];
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  for (const [vw, vh, tag] of [[1366, 900, 'desktop'], [390, 844, 'phone']]) {
    for (const dir of ['baseline', 'a-compact', 'b-quiet']) {
      const ctx = await b.newContext({ viewport: { width: vw, height: vh }, deviceScaleFactor: tag === 'phone' ? 2 : 1 });
      await ctx.addInitScript(() => { localStorage.setItem('orcpub:no-cookie-banner','1'); localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026')); });
      const p = await ctx.newPage(); await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load' }); await p.waitForTimeout(3000);
      if (dir !== 'baseline') { await p.addStyleTag({ content: fs.readFileSync(`${S}/mocks/common.css`, 'utf8') + fs.readFileSync(`${S}/mocks/${dir}.css`, 'utf8') }); await p.waitForTimeout(400); }
      await p.screenshot({ path: `${S}/mocks/${tag}-${dir}.png` });
      await ctx.close();
    }
  }
  await b.close();
})();
