// Browser-driven e2e for the spell-sheet layout option and the PDFs it produces.
//
// Drives the REAL app against `lein e2e-server` on :8890: builds a Warlock 5 /
// Sorcerer 5 through the actual builder (abilities, classes, cantrips, spells),
// opens the PDF options, and exports a sheet in every style under every layout.
// Each download is checked for a PDF header and a page count, and the packed
// layout is required to come out shorter than the per-class one -- which is the
// whole point of packing.
//
// Prerequisites:
//   lein fig:build
//   lein garden once
//   lein e2e-server        (port 8890 free; `fuser -k 8890/tcp` first)
// Run:  node test/browser/spell_layout_pdf_e2e.js
// Exit code 0 = all checks passed.
const fs = require('fs');
const os = require('os');
const path = require('path');
const zlib = require('zlib');
const { chromium } = require('playwright');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const OUT = process.env.ORCPUB_E2E_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'spell-layout-'));

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base)
      .filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) {
      const p = path.join(base, dir, 'chrome-linux', 'chrome');
      if (fs.existsSync(p)) return p;
    }
  } catch (_) {}
  return undefined;
}

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

// The page count, without a PDF library. These files use object streams, so the
// page dictionaries are inside compressed streams and a scan of the raw bytes
// finds nothing -- every FlateDecode stream is inflated and searched too.
function pageCount(file) {
  const buf = fs.readFileSync(file);
  if (buf.slice(0, 5).toString() !== '%PDF-') return -1;
  const count = (text) => (text.match(/\/Type\s*\/Page[^s]/g) || []).length;
  let n = count(buf.toString('latin1'));
  const raw = buf.toString('latin1');
  let at = 0;
  for (;;) {
    const start = raw.indexOf('stream', at);
    if (start < 0) break;
    const end = raw.indexOf('endstream', start);
    if (end < 0) break;
    at = end + 9;
    let from = start + 6;
    if (raw[from] === '\r') from++;
    if (raw[from] === '\n') from++;
    try { n += count(zlib.inflateSync(buf.slice(from, end)).toString('latin1')); } catch (_) {}
  }
  return n;
}

async function pick(page, label) {
  await page.getByText(label, { exact: true }).first().click();
  await page.waitForTimeout(400);
}

async function buildCharacter(page) {
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(3000);
  await page.getByText('Got it!').click().catch(() => {});
  await pick(page, 'Human');

  // Manual entry, because a multiclass caster needs CHA 13 in both classes and
  // the standard array puts 8 there.
  await pick(page, 'Ability Scores / Feats');
  await pick(page, 'Manual Entry');
  const scores = (await page.$$('input[type=number]')).slice(0, 6);
  const values = [13, 14, 14, 13, 13, 16];
  for (let i = 0; i < 6; i++) { await scores[i].fill(String(values[i])); await page.waitForTimeout(150); }
  await page.waitForTimeout(800);

  await pick(page, 'Class / Level');
  const selects = async () => await page.$$('select');
  (await selects())[0].selectOption('warlock'); await page.waitForTimeout(1200);
  (await selects())[1].selectOption('level-5'); await page.waitForTimeout(1200);
  await page.getByText('Add Levels in Another Class').first().click();
  await page.waitForTimeout(1200);
  (await selects())[2].selectOption('sorcerer'); await page.waitForTimeout(1200);
  (await selects())[3].selectOption('level-5'); await page.waitForTimeout(2000);

  await pick(page, 'Spells');
  await page.waitForTimeout(1500);
  // Names that appear on only one of the two class lists, so an exact-text click
  // cannot land in the wrong section: the shared ones (Chill Touch, Misty Step)
  // would all go to the Sorcerer, which is the section rendered first.
  for (const spell of ['Acid Splash', 'Fire Bolt', 'Light', 'Mage Hand', 'Mending',
                       'Eldritch Blast',
                       '1 - Burning Hands', '1 - Magic Missile', '1 - Shield',
                       '2 - Blur', '3 - Fireball',
                       '1 - Unseen Servant', '2 - Ray of Enfeeblement',
                       '3 - Vampiric Touch']) {
    await page.getByText(spell, { exact: true }).first().click().catch(() => {});
    await page.waitForTimeout(250);
  }
  await page.waitForTimeout(1500);
}

// The style dropdown offers only what the build ships; styles beyond it are
// gated by user tier in orcpub.fork.integrations/sheet-styles.
async function offeredStyles(page) {
  const sel = page.locator('div', { hasText: /^Select Character sheet$/ })
    .locator('xpath=following::select[1]').first();
  return (await sel.evaluate(n => [...n.options].map(o => o.value)))
    .filter(v => /^\d+$/.test(v)).map(Number);
}

// The download form posts with target=_blank, and Chromium answers a PDF
// navigation with its own viewer document -- so the bytes are taken off the
// route, which replays the real POST to the real server.
function catchPdf(ctx, file) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('no /character.pdf response')), 90000);
    ctx.route('**/character.pdf', async (route) => {
      const res = await route.fetch();
      const body = await res.body();
      fs.writeFileSync(file, body);
      await route.fulfill({ response: res, body });
      clearTimeout(timer);
      await ctx.unroute('**/character.pdf');
      resolve(file);
    });
  });
}

async function exportPdf(page, style, layout, name) {
  await page.getByText(/^export$/i).first().click();
  await page.waitForTimeout(1200);

  const styleSelect = page.locator('div', { hasText: /^Select Character sheet$/ })
    .locator('xpath=following::select[1]').first();
  await styleSelect.selectOption(String(style));
  await page.waitForTimeout(1200);

  const offered = (await page.innerText('body')).includes('Spell Sheet Layout');
  if (layout !== null) {
    if (!offered) throw new Error(`style ${style}: layout option missing`);
    const layoutSelect = page.locator('div', { hasText: /^Spell Sheet Layout$/ })
      .locator('xpath=following::select[1]').first();
    await layoutSelect.selectOption(layout);
    await page.waitForTimeout(800);
  }

  const ctx = page.context();
  const file = path.join(OUT, `${name}.pdf`);
  const caught = catchPdf(ctx, file);
  await page.getByText(/^create pdf$/i).first().click();
  await caught;
  for (const other of ctx.pages()) if (other !== page) await other.close().catch(() => {});
  return { file, offered };
}

// The spec the builder just posted, so a style the dropdown does not offer can
// still be put through the real route with a real character on it.
async function lastSpec(page) {
  return await page.evaluate(() => document.getElementById('fields-input').value);
}

async function postSpec(page, spec, style, name) {
  const body = spec.replace(/:print-character-sheet-style\? \d+/,
                            `:print-character-sheet-style? ${style}`);
  const res = await page.request.post(`${BASE}/character.pdf`, { form: { body } });
  const file = path.join(OUT, `${name}.pdf`);
  fs.writeFileSync(file, await res.body());
  return { file, status: res.status() };
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1500, height: 1100 } });
  const page = await ctx.newPage();
  const errors = [];
  page.on('console', m => {
    // The dev build's websocket to figwheel is not running under the e2e server.
    if (m.type() === 'error' && !/ERR_CONNECTION_RESET/.test(m.text())) errors.push(m.text());
  });
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));

  try {
    await buildCharacter(page);
    check('a Warlock 5 / Sorcerer 5 builds through the real UI',
          (await page.innerText('body')).includes('Warlock'));

    await page.getByText(/^export$/i).first().click();
    await page.waitForTimeout(1200);
    const styles = await offeredStyles(page);
    check('the builder offers at least one sheet style', styles.length > 0, styles.join(','));
    await page.getByText(/^cancel$/i).first().click();
    await page.waitForTimeout(600);

    // A record of the option as the user meets it, with a sheet style picked --
    // the choice is only shown once a style that can be relabelled is chosen.
    const uiStyle = styles[0];
    await page.getByText(/^export$/i).first().click();
    await page.waitForTimeout(1200);
    await page.locator('div', { hasText: /^Select Character sheet$/ })
      .locator('xpath=following::select[1]').first().selectOption(String(uiStyle));
    await page.waitForTimeout(1200);
    await page.screenshot({ path: path.join(OUT, 'pdf-options.png') });
    await page.getByText(/^cancel$/i).first().click();
    await page.waitForTimeout(600);

    const packed = await exportPdf(page, uiStyle, 'packed', `style-${uiStyle}-packed`);
    check(`style ${uiStyle} offers the layout option to a multiclass caster`, packed.offered);
    const packedSpec = await lastSpec(page);
    const perClass = await exportPdf(page, uiStyle, 'per-class', `style-${uiStyle}-per-class`);
    const perClassSpec = await lastSpec(page);

    const pp = pageCount(packed.file);
    const pc = pageCount(perClass.file);
    check(`style ${uiStyle} packed export is a PDF`, pp > 0, `${pp} pages`);
    check(`style ${uiStyle} per-class export is a PDF`, pc > 0, `${pc} pages`);
    check(`style ${uiStyle} packing saves a page`, pp < pc, `${pp} < ${pc}`);

    // Styles the dropdown does not offer are gated in the UI, not in the route,
    // so they go through the real server with the spec the builder just made.
    for (const style of [1, 2, 3, 4].filter(s => s !== uiStyle)) {
      const a = await postSpec(page, packedSpec, style, `style-${style}-packed`);
      const b = await postSpec(page, perClassSpec, style, `style-${style}-per-class`);
      const ap = pageCount(a.file);
      const bp = pageCount(b.file);
      check(`style ${style} packed export is a PDF`, ap > 0, `HTTP ${a.status}, ${ap} pages`);
      check(`style ${style} per-class export is a PDF`, bp > 0, `HTTP ${b.status}, ${bp} pages`);
      check(`style ${style} packing saves a page`, ap < bp, `${ap} < ${bp}`);
    }

    check('no console errors while exporting', errors.length === 0, errors.join(' | '));
  } catch (e) {
    check('run completed', false, e.message);
  } finally {
    console.log('PDFs in ' + OUT);
    await browser.close();
  }
  process.exit(results.every(r => r.ok) ? 0 : 1);
})();
