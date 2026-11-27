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
 * True when `url` (a string) is on the same origin as `base` (a string); false for anything that
 * is not a parseable URL.
 * @param {string} url
 * @param {string} base
 * @returns {boolean}
 */
function isSameOrigin(url, base) {
  try { return new URL(url).origin === new URL(base).origin; } catch (e) { return false; }
}

/**
 * Formats collected page diagnostics as printable lines: one heading per page that has any, then
 * one indented line per console error, page error and failed request. Returns [] when there are none.
 * @param {Array<{label: string, consoleErrors: string[], pageErrors: string[], failedRequests: string[]}>} collectors
 * @returns {string[]}
 */
function formatDiagnostics(collectors) {
  const lines = [];
  for (const c of collectors) {
    const rows = [
      ...c.consoleErrors.map(m => `console: ${m}`),
      ...c.pageErrors.map(m => `page error: ${m}`),
      ...c.failedRequests.map(m => `request: ${m}`),
    ];
    if (!rows.length) continue;
    lines.push(`Diagnostics for ${c.label} (${rows.length}):`);
    for (const r of rows) lines.push('  ' + r.split('\n')[0]);
  }
  return lines;
}

/**
 * Starts collecting a page's console errors (and this toolbox's own console warnings), uncaught
 * page errors, failed requests and same-origin responses with status 400 or more.
 * @param {import('playwright').Page} page
 * @param {string=} label  names the page in the printout (default "page")
 * @param {string=} base   the app's address, for the same-origin test (default BASE)
 * @returns {{label: string, consoleErrors: string[], pageErrors: string[], failedRequests: string[]}}
 */
function watchPage(page, label, base) {
  const c = { label: label || 'page', consoleErrors: [], pageErrors: [], failedRequests: [] };
  const home = base || BASE;
  page.on('console', m => {
    const t = m.text();
    if (m.type() === 'error' || (m.type() === 'warning' && t.startsWith('toolbox:'))) c.consoleErrors.push(t);
  });
  page.on('pageerror', e => c.pageErrors.push(e.message));
  page.on('requestfailed', r => {
    if (isSameOrigin(r.url(), home)) c.failedRequests.push(`${r.method()} ${r.url()} failed: ${(r.failure() || {}).errorText}`);
  });
  page.on('response', r => {
    if (r.status() >= 400 && isSameOrigin(r.url(), home)) c.failedRequests.push(`${r.request().method()} ${r.url()} -> ${r.status()}`);
  });
  return c;
}

/** Thrown by `must` after it records the failed step: ends a journey whose setup did not happen. */
class SetupFailed extends Error {}

/**
 * Makes a checker: `check(name, pass, detail)` records and prints one PASS or FAIL line;
 * `attempt(what, action)` awaits `action` and, when it throws, records a failed check "could not
 * <what>" and carries on (returns whether it worked); `must(what, action)` does the same but then
 * throws SetupFailed, so a journey whose setup failed ends instead of checking the wrong state;
 * `watch(page, label)` starts a `watchPage` collector; `finish()` records one failed check per watched
 * page that has findings ("diagnostics: <label> has N console errors, page errors or failed requests")
 * and prints their lines; `finish()` prints the "n/m checks passed" line run.sh reads and returns the exit code
 * (1 when any check failed, else 0).
 * @returns {{check: function(string, boolean, string=): void, attempt: function(string, Promise): Promise<boolean>,
 *            must: function(string, Promise): Promise<void>, watch: function(object, string=): object, results: Array, finish: function(): number}}
 */
function checker() {
  const results = [];
  const watched = [];
  const check = (name, pass, detail) => {
    results.push({ name, pass, detail });
    console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
  };
  const attempt = async (what, action) => {
    try { await action; return true; } catch (e) {
      check(`could not ${what}`, false, String(e.message).split('\n')[0]);
      return false;
    }
  };
  const must = async (what, action) => {
    if (!await attempt(what, action)) throw new SetupFailed(`setup failed: could not ${what}`);
  };
  const watch = (page, label) => { const c = watchPage(page, label); watched.push(c); return c; };
  const finish = () => {
    for (const c of watched) {
      const n = c.consoleErrors.length + c.pageErrors.length + c.failedRequests.length;
      if (n) check(`diagnostics: ${c.label} has ${n} console errors, page errors or failed requests`, false, 'listed below');
    }
    const failed = results.filter(r => !r.pass).length;
    for (const l of formatDiagnostics(watched)) console.log(l);
    console.log(`\n${results.length - failed}/${results.length} checks passed`);
    return failed ? 1 : 0;
  };
  return { check, attempt, must, watch, results, finish };
}

/**
 * Escapes the characters a regular expression treats as syntax, so `new RegExp(escapeRegExp(s))`
 * matches `s` literally (weapon names such as "Mace +2" or "Javelin (+1)").
 * @param {string} s
 * @returns {string}
 */
const escapeRegExp = s => String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

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
 * neither overlay covers a control. Call before the first page.goto. When the page blocks storage,
 * the init script logs a console warning (collected by `watchPage`) instead of failing silently.
 * @param {import('playwright').BrowserContext} ctx
 * @returns {Promise<void>}
 */
const quietOverlays = ctx => ctx.addInitScript(id => {
  try {
    localStorage.setItem('orcpub:no-cookie-banner', '1');
    localStorage.setItem('whats-new-seen', JSON.stringify(id));
  } catch (e) {
    console.warn('toolbox: could not stamp the cookie bar and What\'s New as seen (' + e + '), so an overlay may cover a control');
  }
}, currentWhatsNewId());

module.exports = { formatDiagnostics, isSameOrigin, watchPage, SetupFailed, BASE, APP_BUNDLE, escapeRegExp, checker, phoneContext, quietOverlays, currentWhatsNewId };
