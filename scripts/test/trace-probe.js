#!/usr/bin/env node
// Where is a browser probe actually spending its time?
//
// Runs one probe under DEBUG=pw:api, pairs each "=> X started" with its "<= X succeeded"
// or "failed", and reports the slowest calls and the totals by operation.
//
// WHY THIS EXISTS: character_image_capture ran ~400s and the cause was guessed wrong four
// times by reasoning about it -- the network, blind sleeps, the PDF renders, the PDF
// parsing, all refuted by measurement. This trace found it in one run: nine locator.click
// FAILURES at exactly 30.0s, from an untimed `.click().catch(() => {})` waiting the full
// default for an element that was not there. 270s of a 400s run, invisible, while the probe
// passed every assertion.
//
// READ IT LIKE THIS:
//   * A ROUND NUMBER REPEATED IS A TIMEOUT, NOT WORK. 30.0s, 10.0s, 5.0s appearing several
//     times means something is waiting out a default, not doing something expensive.
//   * Sort by "failed" first. A failed call that took its full timeout is pure dead time,
//     and if it is wrapped in .catch() nothing will ever tell you.
//   * page.waitForTimeout totals are fixed sleeps -- honest cost, but the easiest to trim.
//   * A big total against few calls is real work; a big total against many identical
//     durations is a timeout.
//
// Usage:
//   node scripts/test/trace-probe.js <probe-file> [-- extra args for the probe]
//   node scripts/test/trace-probe.js sticky_header_e2e.js
//
// The probe needs whatever it normally needs (server, pack) -- see its own header.

const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const ROOT = path.resolve(__dirname, '../..');
const arg = process.argv[2];
if (!arg) {
  console.error('usage: node scripts/test/trace-probe.js <probe-file> [-- args]');
  process.exit(2);
}
const rel = arg.includes('/') ? arg : path.join('test/browser', arg);
if (!fs.existsSync(path.join(ROOT, rel))) {
  console.error(`no such probe: ${rel}`);
  process.exit(2);
}
const extra = process.argv.slice(3).filter(a => a !== '--');

// Honour the runner's own opt-out list. Tracing whats_new_e2e.js WITH suppression measured
// 35s against its real 110s -- the panel it exists to test never appeared, so the trace
// described a run that does not happen. A tool that quietly measures the wrong configuration
// is worse than no tool.
const preload = path.join(ROOT, 'test/browser/lib/suppress-overlays-preload.js');
const runnerSrc = fs.readFileSync(path.join(__dirname, 'run-browser-probes.js'), 'utf8');
const optedOut = new RegExp(`file:\\s*'${path.basename(rel).replace('.', '\\.')}'[^}]*suppress:\\s*false`).test(runnerSrc);
const env = { ...process.env, DEBUG: 'pw:api',
              NODE_OPTIONS: `${process.env.NODE_OPTIONS || ''} --require ${preload}`.trim() };
if (optedOut || process.env.PROBE_SUPPRESS === '0') {
  env.PROBE_SUPPRESS = '0';
  console.log(`(${path.basename(rel)} opts out of overlay suppression — tracing it that way)`);
}

const t0 = Date.now();
const p = spawn('node', [rel, ...extra], { cwd: ROOT, env });
let buf = '';
const open = [];
const done = [];

const LINE = /^(\S+)\s+pw:api\s+(=>|<=)\s+(.*?)\s*(started|succeeded|failed)?\s*$/;

function handle(line) {
  const m = LINE.exec(line.trim());
  if (!m) return;
  const [, ts, dir, name, status] = m;
  const at = Date.parse(ts);
  if (dir === '=>') open.push({ at, name });
  else if (open.length) {
    const started = open.pop();
    done.push({ ms: at - started.at, name: started.name, status: status || 'ended' });
  }
}

const onData = d => {
  buf += d;
  const lines = buf.split('\n');
  buf = lines.pop();
  lines.forEach(handle);
};
p.stdout.on('data', onData);
p.stderr.on('data', onData);   // DEBUG output goes to stderr

p.on('close', code => {
  if (buf) handle(buf);
  const wall = (Date.now() - t0) / 1000;
  if (!done.length) {
    console.log('No pw:api lines captured. Is playwright the driver for this probe?');
    process.exit(code);
  }
  done.sort((a, b) => b.ms - a.ms);

  console.log(`\n=== ${rel} — wall ${wall.toFixed(0)}s, ${done.length} playwright calls ===\n`);

  const failed = done.filter(d => d.status === 'failed');
  if (failed.length) {
    const dead = failed.reduce((a, d) => a + d.ms, 0) / 1000;
    console.log(`FAILED calls: ${failed.length}, ${dead.toFixed(0)}s of dead time` +
                `${dead > wall * 0.2 ? '   <-- this is most of the runtime' : ''}`);
    for (const d of failed.slice(0, 8)) console.log(`  ${(d.ms / 1000).toFixed(1)}s  ${d.name}`);
    const round = failed.filter(d => d.ms % 1000 === 0);
    if (round.length > 1) {
      console.log(`  ${round.length} of them are exact whole seconds — that is a TIMEOUT being`);
      console.log('  waited out, not work. Find the call and give it a shorter one, or skip it.');
    }
    console.log('');
  }

  console.log('Slowest calls:');
  for (const d of done.slice(0, 12)) {
    console.log(`  ${(d.ms / 1000).toFixed(1).padStart(6)}s  ${d.status.padEnd(9)} ${d.name.slice(0, 80)}`);
  }

  const agg = {};
  for (const d of done) {
    const k = d.name.replace(/\(.*/, '').trim().slice(0, 44);
    agg[k] = (agg[k] || 0) + d.ms;
  }
  console.log('\nTotal by operation:');
  for (const [k, v] of Object.entries(agg).sort((a, b) => b[1] - a[1]).slice(0, 12)) {
    const pct = (v / 1000 / wall * 100).toFixed(0);
    console.log(`  ${(v / 1000).toFixed(1).padStart(6)}s  ${String(pct).padStart(3)}%  ${k}`);
  }
  console.log('');
  process.exit(code);
});
