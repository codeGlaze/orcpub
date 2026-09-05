// Browser-driven e2e for reading a character's picture in the browser.
//
// Drives the REAL app against `lein e2e-server` on :8890. A second origin on
// :8899 serves the test picture, and what it does with Access-Control-Allow-Origin
// is what each case turns on.
//
// The proof rests on a property of the server: validated-addresses refuses
// loopback and private addresses, so /character.pdf CANNOT fetch a picture from
// 127.0.0.1 no matter what. An image that reaches the PDF from this origin can
// only have arrived as bytes the browser read.
//
// Note on CSP: the dev server sends Content-Security-Policy-Report-Only, so a
// cross-origin <img> here is reported and not blocked. Production enforces
// `img-src 'self' data: https:`, which allows any https host -- the same shape
// this exercises. Report-Only console lines are collected separately below and
// do not fail the run.
//
// Prerequisites:
//   lein fig:build
//   lein garden once
//   lein e2e-server        (ports 8890 and 8899 free)
// Run:  node test/browser/character_image_capture_e2e.js
// Exit code 0 = all checks passed.
const fs = require('fs');
const os = require('os');
const http = require('http');
const path = require('path');
const zlib = require('zlib');
const { chromium } = require('playwright');

const BASE = process.env.ORCPUB_E2E_URL || 'http://localhost:8890';
const IMG_PORT = Number(process.env.ORCPUB_E2E_IMG_PORT || 8899);
const IMG_ORIGIN = `http://127.0.0.1:${IMG_PORT}`;
const OUT = process.env.ORCPUB_E2E_OUT || fs.mkdtempSync(path.join(os.tmpdir(), 'image-capture-'));

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

// A 4x4 PNG with correct chunk CRCs. It has to satisfy the STRICTEST decoder in
// play: <img> renders a malformed PNG that createImageBitmap refuses outright.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAEElEQVR4nGP478AARwzEcQAWohPx' +
  '03ZM6QAAAABJRU5ErkJggg==', 'base64');

// Serves the picture, optionally with the header that lets a page read it back
// off a canvas. `hits` records who asked, which is how the run shows the browser
// fetched it and the server did not.
function imageOrigin() {
  const state = { cors: true, hits: [] };
  const server = http.createServer((req, res) => {
    state.hits.push({ url: req.url, agent: req.headers['user-agent'] || '' });
    const headers = { 'Content-Type': 'image/png', 'Content-Length': PNG.length };
    if (state.cors) headers['Access-Control-Allow-Origin'] = '*';
    res.writeHead(200, headers);
    res.end(PNG);
  });
  state.start = () => new Promise(r => server.listen(IMG_PORT, '127.0.0.1', r));
  state.stop = () => new Promise(r => server.close(r));
  return state;
}

// Whether the file embeds a picture. Object streams put the XObject dictionary
// inside a compressed stream, so every FlateDecode stream is inflated and
// searched as well as the raw bytes.
function hasImage(file) {
  const raw = fs.readFileSync(file).toString('latin1');
  if (raw.indexOf('/Subtype/Image') >= 0 || raw.indexOf('/Subtype /Image') >= 0) return true;
  let at = 0;
  for (;;) {
    const start = raw.indexOf('stream', at);
    if (start < 0) return false;
    const end = raw.indexOf('endstream', start);
    if (end < 0) return false;
    at = end + 9;
    let from = start + 6;
    while (raw[from] === '\r' || raw[from] === '\n') from++;
    try {
      const text = zlib.inflateSync(Buffer.from(raw.slice(from, end), 'latin1')).toString('latin1');
      if (text.indexOf('/Subtype/Image') >= 0 || text.indexOf('/Subtype /Image') >= 0) return true;
    } catch (_) {}
  }
}

async function pick(page, label) {
  await page.getByText(label, { exact: true }).first().click();
  await page.waitForTimeout(400);
}

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

// Exports through the real UI and returns both the file and the spec the builder
// posted, which is where :image-data either is or is not.
async function exportSheet(page, name) {
  await page.getByText(/^export$/i).first().click();
  await page.waitForTimeout(1500);

  // Create PDF carries pointer-events: none until a sheet style is chosen, so
  // this is a precondition of the click and not decoration.
  const styleSelect = page.locator('div', { hasText: /^Sheet style$/ })
    .locator('xpath=following::select[1]').first();
  await styleSelect.selectOption('1');
  await page.waitForTimeout(1200);

  const ctx = page.context();
  const file = path.join(OUT, `${name}.pdf`);
  const caught = catchPdf(ctx, file);
  await page.getByText(/^create pdf$/i).first().click();
  await caught;
  const spec = await page.evaluate(() => document.getElementById('fields-input').value);
  for (const other of ctx.pages()) if (other !== page) await other.close().catch(() => {});
  // The options panel overlays the builder; leaving it open makes everything
  // underneath unclickable for the next case.
  await page.getByText(/^cancel$/i).first().click().catch(() => {});
  await page.waitForTimeout(800);
  return { file, spec };
}

function imageDataIn(spec) {
  const m = spec.match(/:image-data "([^"]*)"/);
  return m ? m[1] : null;
}

// What the payload has to be is an image, not a particular size: a small picture
// inside both ceilings is carried untouched, a large one arrives re-encoded to
// JPEG, and both are correct.
function describeImage(b64) {
  if (!b64) return null;
  const b = Buffer.from(b64, 'base64');
  if (b.slice(0, 8).toString('latin1') === '\x89PNG\r\n\x1a\n') return `PNG, ${b.length} bytes`;
  if (b[0] === 0xFF && b[1] === 0xD8 && b[2] === 0xFF) return `JPEG, ${b.length} bytes`;
  return null;
}

async function setImageUrl(page, url) {
  const input = page.locator('span', { hasText: /^Image URL/ })
    .locator('xpath=following::input[1]').first();
  await input.fill(url);
  await input.blur();
  // The thumbnail has to load before the capture starts, and the capture then
  // scales and encodes off the canvas.
  await page.waitForTimeout(3500);
}

(async () => {
  const origin = imageOrigin();
  await origin.start();

  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1500, height: 1100 } });
  const page = await ctx.newPage();

  const errors = [];
  const expected = [];
  // Two kinds of console output are the browser reporting a rule, not the app
  // misbehaving, and neither can be suppressed from script:
  //   - CSP Report-Only lines, which dev sends and prod enforces;
  //   - the CORS block a host without Access-Control-Allow-Origin produces. Any
  //     attempt to read such an image logs this, which is the cost of trying at
  //     all -- and trying is what finds the hosts that do allow it.
  const expectedLine = (t) =>
    /\[Report Only\]|Content Security Policy/i.test(t) ||
    (/CORS policy|ERR_FAILED|ERR_CONNECTION/i.test(t) && /8899|refused\.png/.test(t)) ||
    /Failed to load resource/i.test(t);
  page.on('console', m => {
    if (m.type() !== 'error' && m.type() !== 'warning') return;
    (expectedLine(m.text()) ? expected : errors).push(m.text());
  });
  page.on('pageerror', e => errors.push(String(e)));

  try {
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(3000);
    await page.getByText('Got it!').click().catch(() => {});
    await pick(page, 'Human');
    await pick(page, 'Description');
    await page.waitForTimeout(800);

    // ---- the host allows the read -------------------------------------------
    await setImageUrl(page, `${IMG_ORIGIN}/portrait.png`);

    const browserHits = origin.hits.length;
    check('the browser reads the picture itself', browserHits > 0,
          `${browserHits} request(s) to the picture's host`);

    await page.screenshot({ path: path.join(OUT, 'read-by-the-browser.png'), fullPage: true });
    const allowed = await exportSheet(page, 'cors-allowed');
    const shape = describeImage(imageDataIn(allowed.spec));
    check('the export carries the bytes, not just the address',
          !!shape, shape || 'no usable :image-data');
    check('the sheet is drawn with the picture', hasImage(allowed.file),
          'an image XObject the server could not have fetched: it refuses loopback');

    // ---- the host allows nothing --------------------------------------------
    // Same server, no Access-Control-Allow-Origin. The picture still displays --
    // <img> never needed permission -- but nothing may read it back.
    origin.cors = false;
    await setImageUrl(page, `${IMG_ORIGIN}/refused.png`);

    const bodyText = await page.innerText('body');
    check('the builder says the host refused, and offers upload',
          /does not let the page read the picture/i.test(bodyText));

    await page.screenshot({ path: path.join(OUT, 'host-refused.png'), fullPage: true });
    const refused = await exportSheet(page, 'cors-refused');
    check('nothing is sent when nothing could be read',
          imageDataIn(refused.spec) === null);
    check('the sheet prints without the picture', !hasImage(refused.file),
          'the server refuses loopback, so there is no second route to it');

    // ---- the upload stands in for it ----------------------------------------
    const local = path.join(OUT, 'uploaded.png');
    fs.writeFileSync(local, PNG);
    const fileInput = page.locator('span', { hasText: /^Image URL/ })
      .locator('xpath=following::input[@type="file"][1]').first();
    await fileInput.setInputFiles(local);
    await page.waitForTimeout(2500);

    // The prompt is rendered from the capture state, so its disappearance is the
    // signal that the file was read -- and separates a failed read from a failure
    // to carry what was read.
    const stillPrompting = /does not let the page read the picture/i
      .test(await page.innerText('body'));
    check('the upload prompt goes once the file is read', !stillPrompting);
    if (stillPrompting) await page.screenshot({ path: path.join(OUT, 'upload-stuck.png'), fullPage: true });

    await page.screenshot({ path: path.join(OUT, 'after-upload.png'), fullPage: true });
    const uploaded = await exportSheet(page, 'uploaded');
    const upShape = describeImage(imageDataIn(uploaded.spec));
    check('an uploaded file supplies the bytes the host would not',
          !!upShape, upShape || 'no usable :image-data');
    check('the sheet is drawn with the uploaded picture', hasImage(uploaded.file));

    check('no unexpected console errors', errors.length === 0, errors.slice(0, 3).join(' | '));
    if (expected.length) {
      console.log(`note: ${expected.length} expected line(s) — CSP Report-Only, and the CORS`);
      console.log('      refusal the browser logs when a host will not allow the read.');
    }
  } catch (e) {
    check('run completed', false, String(e));
  } finally {
    await browser.close();
    await origin.stop();
  }

  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  console.log(`artifacts: ${OUT}`);
  process.exit(failed.length ? 1 : 0);
})();
