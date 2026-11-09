// Export as Markdown from My Content, driven through the real page.
//
// Imports test/fixtures/coverage-pak.orcbrew, opens its row and clicks "export as markdown".
// The file must download as "Coverage Pak.md", start with the source's title, hold the Wayfarer
// subclass and a level-table row that names features only (the benefits stay in the "Gained at
// this level" lists).
//
// Needs:     the real app at :8890 (lein e2e-server)
// Run:       node test/browser/export_markdown_e2e.js      exit 0 = all checks passed
const fs = require('fs'), os = require('os'), path = require('path');
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { importPack, suppressOverlays } = require('./lib/orcbrew-import');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const PACK = path.resolve(__dirname, '../fixtures/coverage-pak.orcbrew');
const OUT = process.env.PROBE_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'export-markdown-'));

let failures = 0;
function check(name, ok, detail) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '   [' + String(detail).slice(0, 200) + ']' : ''}`);
  if (!ok) failures++;
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1280, height: 900 } });
  await suppressOverlays(ctx);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));

  await page.goto(BASE + '/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2000);
  const imported = await importPack(page, PACK);
  check('the coverage pak imports', imported.ok, JSON.stringify(imported).slice(0, 200));

  await page.getByText('Coverage Pak', { exact: true }).first().click({ timeout: 20000 });
  const button = page.locator('button:has-text("export as markdown")').first();
  const shown = await button.waitFor({ state: 'visible', timeout: 15000 }).then(() => true, () => false);
  check('the open source has an "export as markdown" button', shown);

  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 30000 }),
    button.click({ timeout: 20000 })
  ]);
  const file = path.join(OUT, 'download.md');
  await download.saveAs(file);
  const text = fs.readFileSync(file, 'utf8');
  const lines = text.split('\n');
  check('the file is named for the source', download.suggestedFilename() === 'Coverage Pak.md', download.suggestedFilename());
  check('it begins with the source title, with no byte-order mark', text.startsWith('# Coverage Pak'), JSON.stringify(text.slice(0, 20)));
  check('it holds the Wayfarer subclass', lines.includes('## Wayfarer'));
  const row = lines.find(l => l.startsWith('| 3rd |'));
  check('it has a 3rd-level table row', !!row, row);
  check('the row names features only, not benefits', !!row && /Wayfarer Lore/.test(row) && !/Weapon Proficiency/.test(row), row);
  check('the benefit is still listed under the level', lines.includes('- Weapon Proficiency: All martial weapons'));
  check('the page threw nothing', errors.length === 0, errors.join(' | '));

  console.log(`download: ${file}`);
  await browser.close();
  process.exit(failures ? 1 : 0);
})().catch(e => { console.log('FAIL  the probe crashed   [' + e + ']'); process.exit(1); });
