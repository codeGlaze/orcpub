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

// A large, noisy PNG. Noise is the point: it compresses badly, so it stays over
// the ceiling through the quality attempts and forces the pixel ones.
function bigNoisyPng(size) {
  const raw = Buffer.alloc(size * (size * 3 + 1));
  let o = 0;
  for (let y = 0; y < size; y++) {
    raw[o++] = 0;                                    // filter byte per scanline
    for (let x = 0; x < size * 3; x++) raw[o++] = (Math.random() * 256) | 0;
  }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const tb = Buffer.from(type, 'latin1');
    const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32(Buffer.concat([tb, data])) >>> 0);
    return Buffer.concat([len, tb, data, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 2;                          // 8-bit, truecolour RGB
  return Buffer.concat([
    Buffer.from('\x89PNG\r\n\x1a\n', 'latin1'),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0))]);
}
const BIG_PNG = bigNoisyPng(1400);

// Serves the picture, optionally with the header that lets a page read it back
// off a canvas. `hits` records who asked, which is how the run shows the browser
// fetched it and the server did not.
function imageOrigin() {
  const state = { cors: true, delayMs: 0, hits: [] };
  const server = http.createServer((req, res) => {
    state.hits.push({ url: req.url, agent: req.headers['user-agent'] || '',
                      mode: req.headers['sec-fetch-mode'] || '?', origin: req.headers.origin || '-' });
    const body = req.url.includes('big') ? BIG_PNG : PNG;
    const headers = { 'Content-Type': 'image/png', 'Content-Length': body.length };
    if (state.cors) headers['Access-Control-Allow-Origin'] = '*';
    // A slow host is how the read becomes observable; a local one is otherwise
    // finished before anything can look at it.
    setTimeout(() => { res.writeHead(200, headers); res.end(body); }, state.delayMs);
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
async function exportSheet(page, name, { alreadyOpen = false } = {}) {
  if (!alreadyOpen) {
    await page.getByText(/^export$/i).first().click();
    await page.waitForTimeout(1500);

    // Create PDF carries pointer-events: none until a sheet style is chosen, so
    // this is a precondition of the click and not decoration.
    const styleSelect = page.locator('div', { hasText: /^Sheet style$/ })
      .locator('xpath=following::select[1]').first();
    await styleSelect.selectOption('1');
    await page.waitForTimeout(1200);
  }

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

async function setImageUrl(page, url, settleMs = 3500) {
  const input = page.locator('span', { hasText: /^Image URL/ })
    .locator('xpath=following::input[1]').first();
  await input.fill(url);
  await input.blur();
  // The thumbnail has to load before the capture starts, and the capture then
  // scales and encodes off the canvas.
  await page.waitForTimeout(settleMs);
}

// Whether Create PDF can actually be pressed. It is held by pointer-events rather
// than the disabled attribute, so the style is what has to be read.
async function exportPressable(page) {
  return await page.getByText(/^create pdf$/i).first().evaluate(
    el => getComputedStyle(el).pointerEvents !== 'none');
}

(async () => {
  const origin = imageOrigin();
  await origin.start();

  // --ssl-version-max=tls1.2 is what lets a browser here reach the real internet:
  // Chrome's TLS 1.3 handshake is reset by this environment's egress relay, while
  // TLS 1.2 negotiates fine. Harmless for the local origin this test uses, and
  // required by anything that talks to a real host.
  const browser = await chromium.launch({
    executablePath: findChrome(), args: ['--ssl-version-max=tls1.2'] });
  const ctx = await browser.newContext({ acceptDownloads: true,
    viewport: { width: 1500, height: 1100 },
    permissions: ['clipboard-read', 'clipboard-write'] });
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

    // Setting the URL writes through the character path interceptor. Getting that
    // wrong rebuilds the character from nothing, so the build has to still be
    // there afterwards.
    const summary = await page.innerText('body');
    check('setting an image URL does not disturb the character',
          /Human/.test(summary) && /Barbarian/i.test(summary),
          'race and class still on the sheet');

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

    // ---- the ceiling holds on the way out -----------------------------------
    // The browser must never send what the server would refuse on arrival.
    await setImageUrl(page, `${IMG_ORIGIN}/big.png`, 6000);
    const big = await exportSheet(page, 'oversized');
    const bigData = imageDataIn(big.spec);
    const bigBytes = bigData ? Buffer.from(bigData, 'base64').length : -1;
    check('an oversized picture is shrunk under the 128k ceiling, not dropped',
          bigBytes > 0 && bigBytes <= 128 * 1024,
          `${BIG_PNG.length} bytes at the host -> ${bigBytes} bytes carried`);
    check('and still reaches the sheet', hasImage(big.file));

    // ---- the host allows nothing --------------------------------------------
    // Same server, no Access-Control-Allow-Origin. The picture still displays --
    // <img> never needed permission -- but nothing may read it back.
    origin.cors = false;
    await setImageUrl(page, `${IMG_ORIGIN}/refused.png`);

    // The read asks for the same URL with crossOrigin set. If that request runs
    // ahead of the thumbnail's, its CORS failure takes the thumbnail down with it
    // and the picture stops displaying -- so the thumbnail must still be fine on
    // a host that allows no read.
    check("a host that refuses the read still shows its picture",
          !/Image failed to load/i.test(await page.innerText('body')));

    // The offer is identified by its button; the sentence above it varies with
    // the reason the server gave.
    const prompt = /Use copied image/i;
    // The builder asks the server about this URL the moment the browser gives up,
    // and says nothing until that answer comes back: the server fetches plenty of
    // pictures the page may not read. Here it cannot -- the URL is loopback, which
    // the server refuses by design -- so the answer is no and the offer appears
    // WITHOUT an export having been tried.
    const waitForText = async (re, ms) => {
      const until = Date.now() + ms;
      while (Date.now() < until) {
        if (re.test(await page.innerText('body'))) return true;
        await page.waitForTimeout(200);
      }
      return false;
    };
    check('the offer waits for the server to answer, then appears without an export',
          await waitForText(prompt, 15000));

    // Not just THAT it failed but WHY, and which thing is worth fixing. The test
    // origin is loopback, which this server refuses by design.
    const shown = await page.innerText('body');
    check('it says what went wrong, and what to do about it',
          /That address cannot be fetched/i.test(shown) &&
          /point straight at an image file/i.test(shown),
          'reason + the fix that matches it');

    await page.screenshot({ path: path.join(OUT, 'host-refused.png'), fullPage: true });
    const refused = await exportSheet(page, 'cors-refused');
    check('nothing is sent when nothing could be read',
          imageDataIn(refused.spec) === null);
    check('the sheet prints without the picture', !hasImage(refused.file),
          'the server refuses loopback, so there is no second route to it');


    // ---- a read in flight holds the export ----------------------------------
    // Exporting mid-read would send the address and let the server fetch what the
    // browser was already holding, which is the race the hold exists to lose.
    // The host is made slow so the read is observable; the test waits for the
    // read to start rather than assuming when, so it does not race the app.
    origin.cors = true;
    origin.delayMs = 5000;
    await setImageUrl(page, `${IMG_ORIGIN}/slow.png`, 300);
    await page.getByText(/^export$/i).first().click();
    await page.waitForTimeout(600);
    const styleSelect = page.locator('div', { hasText: /^Sheet style$/ })
      .locator('xpath=following::select[1]').first();
    await styleSelect.selectOption('1');

    const noteShows = async () =>
      /Reading the character's picture/i.test(await page.innerText('body'));
    const waitFor = async (pred, ms) => {
      const until = Date.now() + ms;
      while (Date.now() < until) { if (await pred()) return true; await page.waitForTimeout(200); }
      return false;
    };

    const sawNote = await waitFor(noteShows, 15000);
    check('the export waits while the picture is still being read',
          sawNote && !(await exportPressable(page)),
          sawNote ? '' : 'the read was never reported as in flight');

    await waitFor(async () => !(await noteShows()), 20000);
    check('and is pressable once the read finishes', await exportPressable(page));

    const slow = await exportSheet(page, 'slow-host', { alreadyOpen: true });
    check('a read that finished in time is carried, not re-fetched',
          !!describeImage(imageDataIn(slow.spec)),
          describeImage(imageDataIn(slow.spec)) || 'no :image-data');

    origin.delayMs = 0;
    origin.cors = false;
    await setImageUrl(page, `${IMG_ORIGIN}/refused.png`);
    await exportSheet(page, 'refused-again');

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
    const stillPrompting = prompt.test(await page.innerText('body'));
    check('the upload prompt goes once the file is read', !stillPrompting);
    if (stillPrompting) await page.screenshot({ path: path.join(OUT, 'upload-stuck.png'), fullPage: true });

    await page.screenshot({ path: path.join(OUT, 'after-upload.png'), fullPage: true });
    const uploaded = await exportSheet(page, 'uploaded');
    const upShape = describeImage(imageDataIn(uploaded.spec));
    check('an uploaded file supplies the bytes the host would not',
          !!upShape, upShape || 'no usable :image-data');
    check('the sheet is drawn with the uploaded picture', hasImage(uploaded.file));

    // ---- paste, for a host that lets nobody read -----------------------------
    // The clipboard carries the decoded picture, put there by the browser's own
    // Copy image, so none of the host's rules reach it. This is the route out for
    // a host that refuses the page AND the server.
    origin.cors = false;
    await setImageUrl(page, `${IMG_ORIGIN}/refused-2.png`);
    await exportSheet(page, 'refused-2');

    await page.evaluate((b64) => {
      const bin = atob(b64);
      const arr = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
      const dt = new DataTransfer();
      dt.items.add(new File([arr], 'pasted.png', { type: 'image/png' }));
      const label = [...document.querySelectorAll('span')]
        .find(el => /^Image URL/.test(el.textContent));
      const target = label.parentElement.querySelector('input') || label.parentElement;
      target.dispatchEvent(new ClipboardEvent('paste',
        { clipboardData: dt, bubbles: true, cancelable: true }));
    }, PNG.toString('base64'));
    await page.waitForTimeout(2500);

    const pasted = await exportSheet(page, 'pasted');
    const pastedShape = describeImage(imageDataIn(pasted.spec));
    check('a pasted picture supplies the bytes no host would give',
          !!pastedShape, pastedShape || 'no :image-data');
    check('and reaches the sheet', hasImage(pasted.file));

    // ---- the button, for a picture already copied ----------------------------
    // The button cannot do the copying: a page-initiated copy of a cross-origin
    // image yields its markup, not its pixels. It reads what the VIEWER copied.
    origin.cors = false;
    await setImageUrl(page, `${IMG_ORIGIN}/refused-3.png`);
    await waitForText(prompt, 15000);

    await page.evaluate(async (b64) => {
      const bin = atob(b64);
      const arr = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
      await navigator.clipboard.write([
        new ClipboardItem({ 'image/png': new Blob([arr], { type: 'image/png' }) })]);
    }, PNG.toString('base64'));

    await page.getByText('Use copied image').first().click();
    await page.waitForTimeout(2500);

    const copied = await exportSheet(page, 'copied');
    const copiedShape = describeImage(imageDataIn(copied.spec));
    check('the button takes a picture the viewer copied',
          !!copiedShape, copiedShape || 'no :image-data');
    check('and it reaches the sheet', hasImage(copied.file));

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
