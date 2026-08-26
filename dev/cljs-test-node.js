// Runs the compiled ClojureScript test bundle under Node.
//
// The app's namespaces touch browser globals at load time (route parsing reads
// window.location, the storage layer reaches for localStorage, FileSaver wants
// a document), so a bare `node target/test-node/test.js` dies before a single
// test runs. These are the minimum shims to get the bundle booted — not a DOM
// implementation, and not a substitute for testing anything that genuinely
// needs a browser.
//
// Exits 1 if any test failed, so scripts/test-cljs.sh can gate on it.

const path = require('path');

const noop = () => {};

const store = {};
const localStorage = {
  getItem: (k) => (k in store ? store[k] : null),
  setItem: (k, v) => { store[k] = String(v); },
  removeItem: (k) => { delete store[k]; },
  clear: () => { for (const k of Object.keys(store)) delete store[k]; },
};

const element = () => ({
  style: {},
  classList: { add: noop, remove: noop, contains: () => false },
  appendChild: noop,
  setAttribute: noop,
  addEventListener: noop,
  getElementsByTagName: () => [],
  children: [],
});

global.window = {
  location: { pathname: '/', href: 'http://localhost/', search: '', hash: '' },
  localStorage,
  addEventListener: noop,
  removeEventListener: noop,
  matchMedia: () => ({ matches: false, addListener: noop, removeListener: noop }),
  navigator: { userAgent: 'node' },
  setTimeout,
  clearTimeout,
  requestAnimationFrame: (cb) => setTimeout(cb, 0),
};

global.document = {
  location: global.window.location,
  body: element(),
  documentElement: Object.assign(element(), {
    namespaceURI: 'http://www.w3.org/1999/xhtml',
  }),
  createElement: element,
  createElementNS: element,
  getElementById: () => null,
  getElementsByTagName: () => [],
  querySelector: () => null,
  querySelectorAll: () => [],
  addEventListener: noop,
};

global.window.document = global.document;
global.localStorage = localStorage;
global.navigator = global.window.navigator;
global.self = global.window;
// Closure's XhrIo instantiates XMLHttpRequest eagerly. Any subscription that
// reaches a real fetch during the suite would otherwise throw
// "XMLHttpRequest is not defined" and kill the run. This inert stand-in lets
// the request be "sent" and simply never respond — tests that care about HTTP
// stub cljs-http directly.
global.XMLHttpRequest = function XMLHttpRequest() {
  this.readyState = 0;
  this.status = 0;
  this.responseText = '';
  this.open = noop;
  this.send = noop;
  this.abort = noop;
  this.setRequestHeader = noop;
  this.getAllResponseHeaders = () => '';
  this.getResponseHeader = () => null;
  this.addEventListener = noop;
  this.removeEventListener = noop;
};
global.XMLHttpRequest.prototype = {};
global.window.XMLHttpRequest = global.XMLHttpRequest;

// Routing code calls history.pushState on navigation.
global.history = { pushState: noop, replaceState: noop, back: noop, go: noop };
global.window.history = global.history;

global.HTMLElement = function () {};
global.Blob = function () {};
global.URL = { createObjectURL: () => 'blob:', revokeObjectURL: noop };

// cljs.test prints a summary line; scrape it so a failure fails the process.
// The bundle runs its tests on load (test_runner.cljs calls -main at the top
// level), so the counts are known by the time require() returns.
let failed = false;
const originalLog = console.log;
console.log = (...args) => {
  const line = args.join(' ');
  if (/^Ran \d+ tests/.test(line)) originalLog(line);
  else originalLog(...args);
  const m = /(\d+) failures?, (\d+) errors?/.exec(line);
  if (m && (Number(m[1]) > 0 || Number(m[2]) > 0)) failed = true;
};

const bundle = process.argv[2];
if (!bundle) {
  console.error('usage: node dev/cljs-test-node.js <compiled-test-bundle.js>');
  process.exit(2);
}

require(path.resolve(bundle));

// cljs.test/async tests report long after the bundle finishes loading, so
// exiting here would miss them AND everything queued behind them — and exit 0
// while reporting nothing. Wait for the end-of-run signal that
// test_runner.cljs sets, and treat never receiving it as a failure rather than
// a pass.
const TIMEOUT_MS = 120000;
const started = Date.now();

const finish = () => {
  const signalled = globalThis.__cljsTestsDone === true;
  if (!signalled) {
    console.error(
      `\nTIMED OUT after ${TIMEOUT_MS / 1000}s waiting for the test run to ` +
      'finish. An async test probably never called done().');
    process.exit(1);
  }
  process.exit(globalThis.__cljsTestsFailed || failed ? 1 : 0);
};

const poll = () => {
  if (globalThis.__cljsTestsDone === true) return finish();
  if (Date.now() - started > TIMEOUT_MS) return finish();
  setTimeout(poll, 25);
};
poll();
