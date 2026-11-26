// Shared basics for browser suites: the base address, the cache-stamped bundle test, one
// pass/fail checker, a phone context and the overlay suppression. Screens' controls live in
// test/browser/places/. Table test: test/browser/lib/toolbox_test.js.
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';

const PHONE_UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 '
  + '(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';

/**
 * True when `url` (a URL object) is the app's compiled bundle, whatever cache stamp (?v=...) it carries.
 * @param {URL} url
 * @returns {boolean}
 */
const APP_BUNDLE = url => url.pathname === '/js/compiled/orcpub.js';

/**
 * Makes a checker: `check(name, pass, detail)` records and prints one PASS or FAIL line;
 * `finish()` prints the "n/m checks passed" line run.sh reads and returns the exit code
 * (1 when any check failed, else 0).
 * @returns {{check: function(string, boolean, string=): void, results: Array, finish: function(): number}}
 */
function checker() {
  const results = [];
  const check = (name, pass, detail) => {
    results.push({ name, pass, detail });
    console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
  };
  const finish = () => {
    const failed = results.filter(r => !r.pass).length;
    console.log(`\n${results.length - failed}/${results.length} checks passed`);
    return failed ? 1 : 0;
  };
  return { check, results, finish };
}

/**
 * Opens a 390x844 phone context: touch, mobile, an iPhone user agent (the phone layout is chosen
 * by user agent as well as width). `opts` overrides any context option.
 * @param {import('playwright').Browser} browser
 * @param {object=} opts
 * @returns {Promise<import('playwright').BrowserContext>}
 */
const phoneContext = (browser, opts) => browser.newContext({
  viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
  userAgent: PHONE_UA, ...opts });

/**
 * The id of the newest What's New release, read from the app's own list so it is never stale;
 * PROBE_WHATS_NEW_RELEASE overrides it. Throws when neither gives one.
 * @returns {string}
 */
function currentWhatsNewId() {
  if (process.env.PROBE_WHATS_NEW_RELEASE) return process.env.PROBE_WHATS_NEW_RELEASE;
  const src = fs.readFileSync(path.join(__dirname, '../../../src/cljc/orcpub/whats_new.cljc'), 'utf8');
  const m = src.match(/\(def releases\s*\[\s*\{:id "([^"]+)"/);
  if (!m) throw new Error('toolbox: no release id found in src/cljc/orcpub/whats_new.cljc');
  return m[1];
}

/**
 * Stamps the cookie bar and the current What's New release as seen before any page loads, so
 * neither overlay covers a control. Call before the first page.goto.
 * @param {import('playwright').BrowserContext} ctx
 * @returns {Promise<void>}
 */
const quietOverlays = ctx => ctx.addInitScript(id => {
  try {
    localStorage.setItem('orcpub:no-cookie-banner', '1');
    localStorage.setItem('whats-new-seen', JSON.stringify(id));
  } catch (e) {}
}, currentWhatsNewId());

module.exports = { BASE, APP_BUNDLE, checker, phoneContext, quietOverlays, currentWhatsNewId };
