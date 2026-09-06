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

// These probes do NOT all want the same world, and running them as though they did is
// wrong in both directions:
//
//   needs: 'server'      drives the real app at :8890 (`lein e2e-server`)
//   needs: 'standalone'  serves resources/public from its own throwaway http server and
//                        expects NO usable backend -- it treats connection-refused as
//                        benign noise
//   needs: 'busy-server' drives :8890 but only passes under `lein e2e-server-busy`, the
//                        profile that holds every export slot so the busy page appears
//
// needsPack: imports a homebrew library and asserts against its content.
const PROBES = [
  { file: 'character_image_capture_e2e.js',    needs: 'server' },
  { file: 'class_handlers_functional_e2e.js',  needs: 'server', needsPack: true },
  { file: 'equipment_add_functional_e2e.js',   needs: 'server', needsPack: true },
  { file: 'export_busy_retry_e2e.js',          needs: 'busy-server' },
  { file: 'notification_flows_e2e.js',         needs: 'standalone' },
  { file: 'notifications_acceptance_e2e.js',   needs: 'standalone' },
  { file: 'spell_help_laziness_e2e.js',        needs: 'server' },
  { file: 'spell_layout_pdf_e2e.js',           needs: 'server' },
  { file: 'starting_equipment_browser_e2e.js', needs: 'standalone' },
  { file: 'starting_equipment_ledger_e2e.js',  needs: 'standalone' },
  { file: 'sticky_header_e2e.js',              needs: 'server' },
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
  const serverUp = await get(SERVER) !== 0;
  const pack = process.env.ORCBREW_PACK;
  const only = (process.env.ONLY || '').split(',').filter(Boolean);
  const jobs = Math.max(1, parseInt(process.env.JOBS || '1', 10));

  let queue = PROBES.filter(p => !only.length || only.some(o => p.file.includes(o)));
  const skipped = [];
  const skip = (p, why) => { skipped.push({ ...p, why }); };
  queue = queue.filter(p => {
    if (p.needs === 'server' && !serverUp) { skip(p, `no server at ${SERVER} — run \`lein e2e-server\``); return false; }
    // Not merely unnecessary: this profile is the whole point of the probe, and against the
    // ordinary server the busy page never appears and every check fails.
    if (p.needs === 'busy-server' && !process.env.BUSY_SERVER) { skip(p, 'needs `lein e2e-server-busy` + BUSY_SERVER=1'); return false; }
    if (p.needsPack && !pack) { skip(p, 'needs ORCBREW_PACK'); return false; }
    return true;
  });

  if (!serverUp) {
    console.log(`note: nothing listening at ${SERVER}, so only the standalone probes will run.`);
    console.log('      (Do NOT run other lein commands while e2e-server boots: the .lein-env');
    console.log('       race makes it come up against the wrong database. See docs/kb/fast-browser-probes.md.)\n');
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
  for (const p of skipped) console.log(`SKIP  ${p.file}  (${p.why})`);

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
