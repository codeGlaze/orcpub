// Browser end-to-end check of the PDF export.
//
//   ./scripts/e2e/run.sh
//
// Drives Chromium through the real character builder, clicks the real download
// button, and inspects the PDF that comes back. The point is to exercise the
// whole path -- pdf_spec building the field map in the browser, the form POST,
// then routes.clj and pdf.clj -- rather than calling the endpoint directly.
//
// Console output is collected throughout and any error or warning fails the run.

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const OUT = process.env.E2E_OUT || '/tmp/e2e-pdf';
// The bundled Chromium build can be older than the one this playwright release
// expects, so point at what is actually installed rather than letting playwright
// look for a version it would have to download.
const EXECUTABLE = process.env.E2E_CHROMIUM
  || ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
      '/opt/pw-browsers/chromium/chrome-linux/chrome']
       .find(p => fs.existsSync(p));

const noise = [
  /favicon/i,
  // This sandbox has no outbound network, so the webfont never loads. That is
  // the environment, not the app; the page renders in its fallback stack.
  /fonts\.googleapis\.com/i,
  /ERR_CONNECTION_RESET/i,
];

function record(page, sink, label) {
  page.on('console', msg => {
    const type = msg.type();
    if (type !== 'error' && type !== 'warning') return;
    const text = msg.text();
    if (noise.some(re => re.test(text))) return;
    sink.push(`${label}: console.${type}: ${text}`);
  });
  page.on('pageerror', err => sink.push(`${label}: pageerror: ${err.message}`));
  page.on('requestfailed', req => {
    const url = req.url();
    if (noise.some(re => re.test(url))) return;
    sink.push(`${label}: request failed: ${url} (${req.failure()?.errorText})`);
  });
}

// Several nodes can carry the same label and some are hidden -- the mobile
// layout renders its own copy -- so .first() often lands on one nothing can
// click. Return the first a user could actually reach.
async function firstVisible(page, selector) {
  const all = page.locator(selector);
  const count = await all.count();
  for (let i = 0; i < count; i++) {
    const el = all.nth(i);
    if (await el.isVisible()) return el;
  }
  return null;
}

async function clickVisible(page, selector) {
  const el = await firstVisible(page, selector);
  if (el) await el.click();
  return Boolean(el);
}

async function shot(page, name) {
  fs.mkdirSync(OUT, { recursive: true });
  await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: false });
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const problems = [];
  const failures = [];
  if (!EXECUTABLE) {
    console.error('no chromium found under /opt/pw-browsers; set E2E_CHROMIUM');
    process.exit(1);
  }
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const context = await browser.newContext({ acceptDownloads: true });
  const page = await context.newPage();
  record(page, problems, 'signed out');
  // The browser posts the field map to a new tab and the PDF is handed to the
  // viewer, so playwright cannot read the response body. Keep the payload the
  // client built and re-issue it to inspect the bytes -- the map under test is
  // still the one pdf-spec/make-spec produced in the page.
  let postedBody = null;
  context.on('request', req => {
    if (req.method() === 'POST' && req.url().includes('character.pdf')) {
      postedBody = req.postData();
    }
  });
  page.on('response', async res => {
    if (!res.url().includes('character.pdf')) return;
    console.log(`  POST ${res.url()} -> ${res.status()} ${res.headers()['content-type'] || ''}`);
    if (res.status() >= 400) {
      const body = await res.text().catch(() => '');
      console.log(`  body: ${body.slice(0, 400)}`);
    }
  });

  const check = (ok, message) => {
    console.log(`  ${ok ? 'ok  ' : 'FAIL'}  ${message}`);
    if (!ok) failures.push(message);
  };

  try {
    console.log('opening the character builder');
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`,
                    { waitUntil: 'networkidle', timeout: 60000 });
    await page.waitForTimeout(2500);

    // The cookie banner sits over the controls at the bottom of the viewport.
    await clickVisible(page, 'text=Got it!');
    await shot(page, '1-builder');

    // "Export" opens the PDF options panel; "Create PDF" inside it submits the
    // hidden download-form that export-pdf fills from pdf-spec/make-spec.
    console.log('opening the export panel');
    check(await clickVisible(page, 'text=Export'), 'the builder offers an Export control');
    await page.waitForTimeout(2000);
    await shot(page, '2-export-panel');

    // "Create PDF" stays disabled until a sheet is chosen -- print-button-enabled
    // in views.cljs gates on print-character-sheet-style? being set.
    const styles = await firstVisible(page, 'select');
    check(styles !== null, 'the panel offers a character sheet dropdown');
    const options = await styles.locator('option').allTextContents();
    console.log(`  sheet options: ${JSON.stringify(options)}`);
    await styles.selectOption({ index: 1 });
    await page.waitForTimeout(500);
    await shot(page, '3-sheet-selected');

    const createPdf = await firstVisible(page, 'button:has-text("Create PDF")');
    check(createPdf !== null, 'the export panel offers a clickable "Create PDF"');
    check(await createPdf.isEnabled(), 'and it is enabled once a sheet is chosen');

    // The sticky header overlays whatever playwright scrolls to, so centre the
    // button in the viewport first and fall back to dispatching the click.
    await createPdf.evaluate(el => el.scrollIntoView({ block: 'center' }));
    await page.waitForTimeout(500);

    // download-form targets _blank and the route answers with
    // Content-Disposition: inline, so the PDF opens in a new tab rather than
    // downloading. Watch the context, which sees responses on every page.
    console.log('requesting the PDF');
    const [response] = await Promise.all([
      context.waitForEvent('response',
        { predicate: r => r.url().includes('character.pdf'), timeout: 90000 }),
      createPdf.click({ timeout: 10000 })
        .catch(() => createPdf.click({ force: true })),
    ]);
    check(response.status() === 200, `the export answers 200 (got ${response.status()})`);
    check(/pdf/.test(response.headers()['content-type'] || ''),
          `the response is a PDF (${response.headers()['content-type']})`);
    check(postedBody !== null && postedBody.length > 500,
          'the client posted a populated field map');

    const refetch = await context.request.post(`${BASE}/character.pdf`, {
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      data: postedBody,
    });
    const bytes = await refetch.body();
    const pdfPath = path.join(OUT, 'character.pdf');
    fs.writeFileSync(pdfPath, bytes);
    console.log(`  received ${Math.round(bytes.length / 1024)} KB`);

    check(bytes.slice(0, 5).toString() === '%PDF-', 'the response is a PDF');
    check(bytes.length > 50000, 'with real content, not an error page');
    check(/\/AcroForm/.test(bytes.toString('latin1')),
          'the form is still interactive, not flattened');

    // Field names sit in compressed object streams, so they cannot be checked
    // here -- pdf_test.clj covers those with PDFBox. Size is the signal this
    // run can give: the prepared template plus the export-time prune lands well
    // under 600 KB, where an unprepared one produced about 1.2 MB.
    check(bytes.length < 600000,
          `the served template is the prepared one (${Math.round(bytes.length / 1024)} KB)`);

    await shot(page, '4-after-download');
  } catch (err) {
    failures.push(`threw: ${err.message}`);
    await shot(page, 'failure').catch(() => {});
  }

  await browser.close();

  console.log('\nconsole output while signed out:');
  if (problems.length === 0) {
    console.log('  none');
  } else {
    problems.forEach(p => console.log(`  ${p}`));
  }

  const bad = failures.length + problems.length;
  if (failures.length) {
    console.log('\nfailures:');
    failures.forEach(f => console.log(`  ${f}`));
  }
  console.log(`\n${failures.length} check(s) failed, ${problems.length} console problem(s)`);
  process.exit(bad === 0 ? 0 : 1);
})();
