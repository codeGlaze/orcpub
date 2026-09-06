#!/usr/bin/env node
// Runs every ASSERTING browser probe and exits non-zero if any fails.
//
// Why this exists: the browser probes under test/browser/ carry real assertions, but
// neither `lein test` nor the CLJS runner invokes them, so "both suites green" says
// nothing about them. equipment_add_functional_e2e.js sat failing three assertions and
// exiting 1 for several commits because nothing ran it, and screenshots_e2e.js went stale
// the same way but had guarded lookups, so it silently stopped taking shots instead.
//
// A probe that targets a control by class name goes stale the moment that control is
// swapped. This is the thing that notices.
//
// Only probes that ASSERT are listed. The measurement probes (tab_switch_freeze,
// freeze_cpu_profile, combobox_scroll, select_option_census, ...) report numbers rather
// than pass/fail and are run by hand; adding them here would turn timing noise into
// build failures.
//
// Usage:
//   lein fig:build && lein e2e-server        # in another shell
//   node scripts/test/run-browser-probes.js
//
//   ORCBREW_PACK=/path/to/pack.orcbrew  runs the two probes that need imported homebrew
//   JOBS=3                              run N probes at once (default 1)
//   ONLY=equipment,sticky                substring filter

const { spawn } = require('child_process');
const fs = require('fs'), path = require('path');
const http = require('http');

const ROOT = path.resolve(__dirname, '../..');
const SERVER = 'http://localhost:8890';
const PER_PROBE_TIMEOUT_MS = 10 * 60 * 1000;

// needsPack: the probe imports a homebrew library and asserts against its content, so it
// cannot run without one. The rest assert against the app's own bundled content.
const PROBES = [
  { file: 'character_image_capture_e2e.js' },
  { file: 'class_handlers_functional_e2e.js', needsPack: true },
  { file: 'equipment_add_functional_e2e.js', needsPack: true },
  { file: 'export_busy_retry_e2e.js' },
  { file: 'notification_flows_e2e.js' },
  { file: 'notifications_acceptance_e2e.js' },
  { file: 'spell_help_laziness_e2e.js' },
  { file: 'spell_layout_pdf_e2e.js' },
  { file: 'starting_equipment_browser_e2e.js' },
  { file: 'starting_equipment_ledger_e2e.js' },
  { file: 'sticky_header_e2e.js' },
];

const get = url => new Promise(res => {
  const r = http.get(url, x => { x.resume(); res(x.statusCode); });
  r.on('error', () => res(0));
  r.setTimeout(4000, () => { r.destroy(); res(0); });
});

function run(probe, pack) {
  return new Promise(resolve => {
    const args = [path.join('test/browser', probe.file)];
    if (probe.needsPack) args.push(pack);
    const t0 = Date.now();
    const p = spawn('node', args, { cwd: ROOT });
    let out = '';
    p.stdout.on('data', d => { out += d; });
    p.stderr.on('data', d => { out += d; });
    const timer = setTimeout(() => { p.kill('SIGKILL'); }, PER_PROBE_TIMEOUT_MS);
    p.on('close', code => {
      clearTimeout(timer);
      const secs = ((Date.now() - t0) / 1000).toFixed(0);
      const fails = (out.match(/^\s*FAIL/gm) || []).length;
      const passes = (out.match(/^\s*PASS/gm) || []).length;
      resolve({ probe, code, out, secs, fails, passes });
    });
  });
}

(async () => {
  if (!fs.existsSync(path.join(ROOT, 'resources/public/js/compiled/orcpub.js'))) {
    console.error('No dev build — run `lein fig:build` first.');
    process.exit(2);
  }
  if (await get(SERVER) === 0) {
    console.error(`No server at ${SERVER} — run \`lein e2e-server\` in another shell.`);
    console.error('(Do NOT run other lein commands while it boots: the .lein-env race makes');
    console.error(' it come up against the wrong database. See docs/kb/fast-browser-probes.md.)');
    process.exit(2);
  }

  const pack = process.env.ORCBREW_PACK;
  const only = (process.env.ONLY || '').split(',').filter(Boolean);
  const jobs = Math.max(1, parseInt(process.env.JOBS || '1', 10));

  let queue = PROBES.filter(p => !only.length || only.some(o => p.file.includes(o)));
  const skipped = [];
  if (!pack) {
    for (const p of queue.filter(p => p.needsPack)) skipped.push(p);
    queue = queue.filter(p => !p.needsPack);
  }

  console.log(`running ${queue.length} probe(s), ${jobs} at a time\n`);
  const results = [];
  const workers = Array.from({ length: jobs }, async () => {
    for (;;) {
      const probe = queue.shift();
      if (!probe) return;
      const r = await run(probe, pack);
      results.push(r);
      const tag = r.code === 0 ? 'PASS' : 'FAIL';
      console.log(`${tag}  ${probe.file}  (${r.passes} checks, ${r.fails} failing, ${r.secs}s)`);
      if (r.code !== 0) console.log(r.out.split('\n').filter(l => /FAIL|Error|error:/.test(l)).slice(0, 12).map(l => '      ' + l.trim()).join('\n'));
    }
  });
  await Promise.all(workers);

  // A probe that could not run is reported loudly. Silence is how the last one hid.
  for (const p of skipped) console.log(`SKIP  ${p.file}  (needs ORCBREW_PACK)`);

  const failed = results.filter(r => r.code !== 0);
  console.log(`\n${results.length - failed.length}/${results.length} probes passed` +
              (skipped.length ? `, ${skipped.length} skipped` : ''));
  if (failed.length) {
    console.log('failed: ' + failed.map(r => r.probe.file).join(', '));
    process.exit(1);
  }
  if (skipped.length && process.env.STRICT) {
    console.log('STRICT: skipped probes count as failures');
    process.exit(1);
  }
})();
