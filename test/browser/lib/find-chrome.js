// Which Chromium a browser probe launches. One copy, because a dozen had drifted:
// most looked only for chrome-linux/chrome, a folder newer Playwright builds name
// chrome-linux64; five threw when the folder was missing; and eight probes hardcoded
// /opt/pw-browsers or read an env var no other probe read.
//
// Order: a path in any env var a probe or runner has used; then the newest
// chromium-<n> under PLAYWRIGHT_BROWSERS_PATH or /opt/pw-browsers (the web sandbox);
// then undefined, which lets Playwright launch the browser it installed itself.
const fs = require('fs');
const path = require('path');

const ENV_VARS = ['CHROME', 'CHROME_PATH', 'PLAYWRIGHT_CHROMIUM', 'E2E_CHROMIUM'];
const LAYOUTS = [['chrome-linux64', 'chrome'], ['chrome-linux', 'chrome']];

// A path may name the binary itself or an install folder holding one.
function executableAt(p) {
  try {
    const st = fs.statSync(p);
    if (st.isFile()) return p;
    if (st.isDirectory()) {
      for (const parts of LAYOUTS) {
        const bin = path.join(p, ...parts);
        if (fs.existsSync(bin)) return bin;
      }
    }
  } catch (_) {}
  return undefined;
}

// Numeric, not lexicographic: chromium-999 is older than chromium-1200.
function newestInstall(base) {
  let dirs = [];
  try {
    dirs = fs.readdirSync(base)
      .filter(d => /^chromium-\d+$/.test(d))
      .sort((a, b) => b.split('-')[1] - a.split('-')[1]);
  } catch (_) {}
  for (const d of dirs) {
    const bin = executableAt(path.join(base, d));
    if (bin) return bin;
  }
  return executableAt(path.join(base, 'chromium'));
}

const warned = new Set();

function findChrome() {
  for (const v of ENV_VARS) {
    if (!process.env[v]) continue;
    const bin = executableAt(process.env[v]);
    if (bin) return bin;
    if (!warned.has(v)) {
      warned.add(v);
      console.error(`find-chrome: ignoring ${v}=${process.env[v]}, no browser there`);
    }
  }
  for (const base of [process.env.PLAYWRIGHT_BROWSERS_PATH, '/opt/pw-browsers'].filter(Boolean)) {
    const bin = newestInstall(base);
    if (bin) return bin;
  }
  return undefined;
}

// What a probe will actually launch, or null when there is nothing to launch --
// for the runner, which reports that as a skip rather than a wall of failures.
function resolveBrowser() {
  const found = findChrome();
  if (found) return found;
  try {
    const bin = require('playwright').chromium.executablePath();
    if (bin && fs.existsSync(bin)) return bin;
  } catch (_) {}
  return null;
}

module.exports = { findChrome, resolveBrowser };
