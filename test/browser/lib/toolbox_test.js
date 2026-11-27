// Node-only table test of test/browser/lib/toolbox.js: the checker's output and exit code, the
// bundle test with and without a cache stamp, and the current What's New id. No browser, no server.
// Needs:     nothing. Run: node test/browser/lib/toolbox_test.js
const { checker, APP_BUNDLE, currentWhatsNewId, escapeRegExp, SetupFailed } = require('./toolbox');

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

  console.log(`toolbox_test: ${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
})();
