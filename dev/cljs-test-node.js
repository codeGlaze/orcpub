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

// The bundle can leave async work queued (a subscription firing an XHR that
// nothing here implements). Tests have already reported by now, so exit on the
// result rather than waiting for a quiet event loop.
process.exit(failed ? 1 : 0);
