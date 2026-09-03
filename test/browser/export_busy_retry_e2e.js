// Export busy page: turned-away sheets retry themselves, then hand over.
//
// Run against a server started with tight limits so the queue is reachable:
//
//   ORCPUB_PDF_CONCURRENCY=1 ORCPUB_PDF_QUEUE_TIMEOUT_MS=250 \
//   ORCPUB_PDF_MAX_RETRIES=2 lein e2e-server
//   node test/browser/export_busy_retry_e2e.js
//
// Exits non-zero on the first failed check.

const { chromium } = require('playwright');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const MAX_RETRIES = parseInt(process.env.ORCPUB_PDF_MAX_RETRIES || '2', 10);

const SPEC = (() => {
  const f = [':character-name "Tallis"', ':class-level "Wizard 9"',
             ':spellcasting-class-1 "Wizard"'];
  for (let lvl = 0; lvl < 4; lvl++) {
    for (let j = 1; j <= 12; j++) {
      f.push(`:spells-${lvl}-${j}-1 "Protection from Energy"`);
    }
  }
  return '{' + f.join(' ') + '}';
})();

let failures = 0;
function check(label, ok, detail) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  -- ' + detail : ''}`);
  if (!ok) failures++;
}

// Hold every export slot until `until`, so an export arriving now is turned away.
function saturate(until) {
  const one = async () => {
    while (Date.now() < until) {
      await fetch(BASE + '/character.pdf', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'body=' + encodeURIComponent(SPEC)
      }).catch(() => {});
    }
  };
  return Promise.all([one(), one(), one(), one()]);
}

(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.PLAYWRIGHT_CHROMIUM || '/opt/pw-browsers/chromium'
  });
  const page = await browser.newPage();

  const pageErrors = [];
  page.on('pageerror', e => pageErrors.push(String(e)));

  const navigations = [];
  page.on('framenavigated', f => {
    if (f === page.mainFrame()) navigations.push(f.url());
  });

  const busyUntil = Date.now() + 7000;
  const load = saturate(busyUntil);

  await page.setContent(
    `<form id="f" method="POST" action="${BASE}/character.pdf">` +
    `<input type="hidden" name="body" value="${SPEC.replace(/"/g, '&quot;')}">` +
    `</form><script>document.getElementById('f').submit()</script>`);

  await page.waitForTimeout(1500);

  const heading = await page.textContent('h1').catch(() => null);
  check('a turned-away export lands on the busy page',
        heading && /sheets are being made/i.test(heading), heading);

  const countdown = await page.textContent('#countdown').catch(() => null);
  check('the page says when it will try again',
        countdown && /trying again in \d+ second/i.test(countdown), countdown);

  check('a manual escape is offered too',
        (await page.locator('#retry-form button').count()) === 1);

  // It should resubmit on its own while the server is still saturated.
  const before = navigations.length;
  await page.waitForTimeout(9000);
  await load;
  await page.waitForTimeout(6000);
  const selfSubmits = navigations.length - before;

  check('the page retries itself without being touched', selfSubmits > 0,
        `${selfSubmits} self-submission(s)`);
  check('it stops at the configured limit', selfSubmits <= MAX_RETRIES,
        `${selfSubmits} vs max ${MAX_RETRIES}`);

  // Load has stopped, so a retry should have got through to a real sheet.
  const stillBusy = await page.locator('#countdown').count();
  check('once the rush passes the sheet is delivered', stillBusy === 0,
        stillBusy ? 'still on the busy page' : 'left the busy page');

  check('no script errors on the busy page', pageErrors.length === 0,
        pageErrors.join(' | '));

  await browser.close();
  console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})();
