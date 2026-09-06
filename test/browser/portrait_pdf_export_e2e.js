// A composed portrait must reach the exported PDF.
//
// The compositor draws with CSS masks, which PDFBox cannot read, so the client
// bakes the layers to a PNG and posts it with the export spec. This drives the
// real builder: compose a portrait, export, and assert the request carried
// portrait-png and the server answered with a PDF that actually embeds it.
//
//   lein fig:build && lein e2e-server
//   node test/browser/portrait_pdf_export_e2e.js
//
// Exits non-zero on the first failed check.

const { chromium } = require('playwright');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';

let failures = 0;
function check(label, ok, detail) {
  if (ok) console.log(`  ok   ${label}`);
  else { failures++; console.log(`  FAIL ${label}${detail ? ` -- ${detail}` : ''}`); }
}

(async () => {
  const browser = await chromium.launch(
    process.env.ORCPUB_CHROME ? { executablePath: process.env.ORCPUB_CHROME } : {});
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();

  console.log(`\nportrait -> pdf export e2e -- ${BASE}\n`);

  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await page.waitForSelector('#app', { timeout: 30000 });
  const cookieBtn = page.locator('#cookie-btn');
  if (await cookieBtn.count()) { await cookieBtn.click().catch(() => {}); await page.waitForTimeout(200); }

  // --- compose and save a portrait -------------------------------------
  const descTab = page.locator('.builder-tab', { hasText: /^Description$/ }).first();
  if (await descTab.count()) { await descTab.click(); await page.waitForTimeout(300); }

  await page.locator('.pl-launcher').click();
  await page.locator('.pl-drawer').waitFor({ state: 'visible', timeout: 10000 });
  await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
  await page.waitForTimeout(400);
  const composed = await page.locator('.pl-portrait-frame .portrait-layer').count();
  check('portrait composed before export', composed > 0, `${composed} layers`);
  await page.locator('.pl-btn-primary', { hasText: 'Save portrait' }).click();
  await page.waitForTimeout(600);

  // --- capture the export POST -----------------------------------------
  // The form targets _blank, so the POST belongs to the new tab, not the
  // opener -- listen at context level or it is never seen.
  // The form targets _blank, so the POST belongs to the new tab, not the
  // opener -- listen at context level or it is never seen. The response body
  // is captured here too: a POST result cannot be re-fetched by navigating.
  let posted = null;
  let pdfBuf = null;
  ctx.on('request', r => {
    if (r.url().includes('/character.pdf') && r.method() === 'POST') {
      posted = r.postData() || '';
    }
  });
  let pdfContentType = null;
  ctx.on('response', r => {
    if (r.url().includes('/character.pdf') && r.request().method() === 'POST') {
      pdfContentType = r.headers()['content-type'] || '';
    }
  });

  // Same flow the export-busy e2e drives: Export -> pick a sheet style
  // (Create PDF stays pointer-events:none until one is chosen) -> Create PDF.
  const visible = async (loc) => {
    const n = await loc.count();
    for (let i = 0; i < n; i++) {
      const el = loc.nth(i);
      if (await el.isVisible()) return el;
    }
    throw new Error('no visible match');
  };

  (await visible(page.locator('button:has-text("Export")'))).click();
  await page.waitForTimeout(1200);
  (await visible(page.locator('select'))).selectOption('1');
  await page.waitForTimeout(600);

  // The form targets _blank, so the PDF lands in a new tab.
  const pdfPagePromise = ctx.waitForEvent('page', { timeout: 30000 });
  (await visible(page.locator('button:has-text("Create PDF")'))).click();

  const pdfPage = await pdfPagePromise.catch(() => null);
  await page.waitForTimeout(2500);

  // --- the request carried the baked portrait --------------------------
  check('export POSTed to /character.pdf', posted !== null);
  check('request carried :portrait-png', !!posted && posted.includes('portrait-png'),
        posted ? `${posted.length} bytes of body` : 'no body');

  // The body is form-urlencoded EDN, so decode before measuring.
  const decoded = posted ? decodeURIComponent(posted.replace(/\+/g, ' ')) : '';
  const b64 = (decoded.match(/:portrait-png\s+"([A-Za-z0-9+/=]+)"/) || [])[1] || '';
  check('baked PNG is a non-trivial payload', b64.length > 1000, `${b64.length} b64 chars`);

  // --- and the server produced a PDF that embeds it --------------------
  // Chrome renders a PDF navigation in its own viewer, so response.body()
  // yields the viewer's HTML shell rather than the file. Replay the exact
  // body the browser posted to get the real bytes back.
  check('response was served as a PDF', /application\/pdf/i.test(pdfContentType || ''),
        pdfContentType);

  const replay = await ctx.request.post(`${BASE}/character.pdf`, {
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    data: posted,
  }).catch(() => null);
  pdfBuf = replay ? await replay.body().catch(() => null) : null;

  const pdfBytes = pdfBuf ? pdfBuf.length : 0;
  const pdfOk = !!pdfBuf && pdfBuf.slice(0, 4).toString() === '%PDF';
  // PDFBox writes an embedded raster as an XObject with subtype /Image.
  const embedsImage = !!pdfBuf && (pdfBuf.includes(Buffer.from('/Subtype /Image'))
                                || pdfBuf.includes(Buffer.from('/Subtype/Image')));
  check('server returned a PDF', pdfOk, `${pdfBytes} bytes`);
  check('PDF embeds an image XObject', embedsImage);

  // Differential check: the same request minus the portrait must come back
  // materially smaller. Without this, "embeds an image" could be satisfied by
  // artwork already in the sheet template rather than by the posted portrait.
  const stripped = posted.replace(/%3Aportrait-png\+%22[A-Za-z0-9%2B/%3D]+%22/, '')
                         .replace(/:portrait-png\s+"[A-Za-z0-9+/=]+"/, '');
  const bare = await ctx.request.post(`${BASE}/character.pdf`, {
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    data: stripped,
  }).catch(() => null);
  const bareBuf = bare ? await bare.body().catch(() => null) : null;
  const bareBytes = bareBuf ? bareBuf.length : 0;

  check('portrait payload was actually stripped for the control',
        stripped.length < posted.length,
        `${posted.length} -> ${stripped.length}`);
  check('PDF without the portrait is materially smaller',
        bareBytes > 0 && pdfBytes > bareBytes,
        `with=${pdfBytes} without=${bareBytes}`);

  await browser.close();
  console.log(`\n${failures === 0 ? 'PASS' : `FAIL (${failures})`}\n`);
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.error('\nharness error:', e); process.exit(1); });
