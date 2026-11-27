// Node-only table test of test/browser/lib/toolbox.js: the checker's output and exit code, the
// bundle test with and without a cache stamp, and the current What's New id. No browser, no server.
// Needs:     nothing. Run: node test/browser/lib/toolbox_test.js
const { checker, APP_BUNDLE, currentWhatsNewId, escapeRegExp, SetupFailed,
  formatDiagnostics, isSameOrigin, watchPage } = require('./toolbox');

let passed = 0, failed = 0;
/**
 * Records one assertion of this test and prints a FAIL line when it does not hold.
 * @param {string} name
 * @param {boolean} ok
 */
function expect(name, ok) {
  if (ok) passed++; else { failed++; console.log('FAIL  ' + name); }
}

/** Runs `fn` with console.log captured; returns the lines it printed. */
function capture(fn) {
  const lines = [], orig = console.log;
  console.log = (...a) => lines.push(a.join(' '));
  try { fn(); } finally { console.log = orig; }
  return lines;
}

const bundleCases = [
  ['/js/compiled/orcpub.js', true],
  ['/js/compiled/orcpub.js?v=abc123', true],
  ['/js/compiled/orcpub.js.map', false],
  ['/js/compiled/other.js', false],
  ['/orcpub.js', false],
];
for (const [u, want] of bundleCases)
  expect(`APP_BUNDLE ${u}`, APP_BUNDLE(new URL(u, 'http://localhost:8890')) === want);

// [name, other string the pattern must NOT match]
const nameCases = [
  ['Javelin (+1)', 'Javelin +1'],
  ['Mace +2', 'Maceee2'],
  ['Dagger.1', 'Daggerx1'],
  ['Sword [Light]', 'Sword L'],
  ['Plain Name', 'Plain Names'],
];
for (const [n, other] of nameCases) {
  let re;
  try { re = new RegExp(`^${escapeRegExp(n)}$`); } catch (e) { expect(`escapeRegExp(${n}) is a valid pattern`, false); continue; }
  expect(`escapeRegExp matches ${n} literally`, re.test(n));
  expect(`escapeRegExp does not let ${n} match ${other}`, !re.test(other));
}

const lines = [];
const exits = [];
lines.push(...capture(() => {
  const c = checker();
  c.check('a passes', true);
  c.check('b fails', false, 'why');
  c.check('c passes', true, 'ok');
  exits.push(c.finish());
}));
expect('PASS line without detail', lines[0] === 'PASS  a passes');
expect('FAIL line with detail', lines[1] === 'FAIL  b fails   [why]');
expect('PASS line with detail', lines[2] === 'PASS  c passes   [ok]');
expect('summary counts passes over total', lines.includes('\n2/3 checks passed'));
expect('any failure exits 1', exits[0] === 1);

const clean = [];
capture(() => { const c = checker(); c.check('x', true); clean.push(c.finish()); });
expect('all passing exits 0', clean[0] === 0);
const empty = [];
const emptyLines = capture(() => empty.push(checker().finish()));
expect('no checks exits 0 and prints 0/0', empty[0] === 0 && emptyLines.includes('\n0/0 checks passed'));

expect('the What\'s New id comes from the app', /^[a-z0-9-]+$/.test(currentWhatsNewId()));

(async () => {
  // attempt records a failed check and carries on; must records it and throws SetupFailed
  const ok = async () => 'fine';
  const bad = async () => { throw new Error('no such tab\nstack line'); };
  let got;
  const c1 = checker();
  const out = [];
  const orig = console.log;
  console.log = (...a) => out.push(a.join(' '));
  try {
    got = [await c1.attempt('open A', ok()), await c1.attempt('open B', bad())];
    var threw = null;
    try { await c1.must('open C', bad()); } catch (e) { threw = e; }
    var mustOk = await c1.must('open D', ok()).then(() => true, () => false);
  } finally { console.log = orig; }
  expect('attempt returns true when the action works', got[0] === true);
  expect('attempt returns false when the action throws, and carries on', got[1] === false);
  expect('attempt records a failed check naming what, first line of the error only',
         c1.results.some(r => r.name === 'could not open B' && !r.pass && r.detail === 'no such tab'));
  expect('attempt records nothing when the action works', !c1.results.some(r => r.name === 'could not open A'));
  expect('must throws SetupFailed when the action throws', threw instanceof SetupFailed);
  expect('must records the failed check before throwing',
         c1.results.some(r => r.name === 'could not open C' && !r.pass));
  expect('must does not throw when the action works', mustOk === true);
  expect('the failed attempt and must print FAIL lines', out.filter(l => l.startsWith('FAIL  could not')).length === 2);

  // diagnostics: same-origin test, formatting, the fake-page collector, and when finish prints them
  expect('isSameOrigin: same host and port', isSameOrigin('http://localhost:8893/a/b?x=1', 'http://localhost:8893'));
  expect('isSameOrigin: another port is not', !isSameOrigin('http://localhost:8890/a', 'http://localhost:8893'));
  expect('isSameOrigin: another host is not', !isSameOrigin('https://fonts.example.com/f', 'http://localhost:8893'));
  expect('isSameOrigin: junk is not', !isSameOrigin('not a url', 'http://localhost:8893'));
  const none = { label: 'p', consoleErrors: [], pageErrors: [], failedRequests: [] };
  expect('formatDiagnostics: nothing collected prints nothing', formatDiagnostics([none]).length === 0);
  const some = { label: 'phone page', consoleErrors: ['boom\nat x'], pageErrors: ['TypeError: y'], failedRequests: ['GET http://h/a -> 404'] };
  const fd = formatDiagnostics([none, some]);
  expect('formatDiagnostics: heading counts the rows, quiet pages skipped', fd[0] === 'Diagnostics for phone page (3):' && fd.length === 4);
  expect('formatDiagnostics: a row is one indented line per finding',
         fd[1] === '  console: boom' && fd[2] === '  page error: TypeError: y' && fd[3] === '  request: GET http://h/a -> 404');
  const handlers = {};
  const fake = { on: (ev, fn) => { handlers[ev] = fn; } };
  const col = watchPage(fake, 'fake', 'http://h');
  const resp = (status, url) => ({ status: () => status, url: () => url, request: () => ({ method: () => 'GET' }) });
  handlers.console({ type: () => 'error', text: () => 'bad' });
  handlers.console({ type: () => 'log', text: () => 'fine' });
  handlers.console({ type: () => 'warning', text: () => 'toolbox: storage blocked' });
  handlers.console({ type: () => 'warning', text: () => 'other warning' });
  handlers.pageerror({ message: 'oops' });
  handlers.requestfailed({ method: () => 'POST', url: () => 'http://h/x', failure: () => ({ errorText: 'net::ERR' }) });
  handlers.response(resp(404, 'http://h/missing'));
  handlers.response(resp(200, 'http://h/ok'));
  handlers.response(resp(500, 'http://other/z'));
  expect('watchPage keeps console errors and toolbox warnings only', col.consoleErrors.join('|') === 'bad|toolbox: storage blocked');
  expect('watchPage keeps page errors', col.pageErrors.join() === 'oops');
  expect('watchPage keeps failed requests and same-origin 4xx/5xx only',
         col.failedRequests.length === 2 && /POST http:\/\/h\/x failed: net::ERR/.test(col.failedRequests[0]) && /missing -> 404/.test(col.failedRequests[1]));
  // cross-origin transport failures are not recorded; same-origin ones are
  const h2 = {};
  const col2 = watchPage({ on: (ev, fn) => { h2[ev] = fn; } }, 'x', 'http://h');
  h2.requestfailed({ method: () => 'GET', url: () => 'https://fonts.example.com/f.woff', failure: () => ({ errorText: 'net::ERR_BLOCKED' }) });
  expect('watchPage ignores a cross-origin requestfailed', col2.failedRequests.length === 0);
  h2.requestfailed({ method: () => 'GET', url: () => 'http://h/a.js', failure: () => ({ errorText: 'net::ERR_FAILED' }) });
  expect('watchPage keeps a same-origin requestfailed', col2.failedRequests.length === 1);

  // diagnostics count as failed checks, one per page with findings, and change the exit code
  const diagCases = [
    ['a page error', 'pageerror', { message: 'late' }, 1],
    ['a console error', 'console', { type: () => 'error', text: () => 'bad' }, 1],
    ['a same-origin 404', 'response', { status: () => 404, url: () => 'http://localhost:8890/x', request: () => ({ method: () => 'GET' }) }, 1],
    ['a cross-origin 404', 'response', { status: () => 404, url: () => 'http://elsewhere/x', request: () => ({ method: () => 'GET' }) }, 0],
    ['a console log', 'console', { type: () => 'log', text: () => 'fine' }, 0],
  ];
  for (const [what, ev, arg, wantFailed] of diagCases) {
    const code = [], c = checker();
    const out2 = capture(() => {
      c.watch({ on: (e, fn) => { if (e === ev) fn(arg); } }, 'pg');
      c.check('fine', true);
      code.push(c.finish());
    });
    const diag = c.results.filter(r => /^diagnostics: pg has 1 /.test(r.name) && !r.pass).length;
    expect(`${what} on a watched page ${wantFailed ? 'is' : 'is not'} a failed check`, diag === wantFailed);
    expect(`${what} sets exit ${wantFailed}`, code[0] === wantFailed);
    expect(`${what} ${wantFailed ? 'prints' : 'does not print'} its detail`, out2.some(l => l.startsWith('Diagnostics for pg')) === !!wantFailed);
    if (wantFailed) expect(`${what} is counted in the summary`, out2.includes('\n1/2 checks passed'));
  }
  const quiet = checker();
  quiet.watch({ on: () => {} }, 'pg');
  const twoPages = [];
  capture(() => {
    const c = checker();
    c.watch({ on: (e, fn) => { if (e === 'pageerror') fn({ message: 'a' }); } }, 'one');
    c.watch({ on: (e, fn) => { if (e === 'pageerror') fn({ message: 'b' }); } }, 'two');
    c.finish(); twoPages.push(c.results.filter(r => /^diagnostics/.test(r.name)).length);
  });
  expect('each watched page with findings adds its own failed check', twoPages[0] === 2);

  console.log(`toolbox_test: ${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
})();
