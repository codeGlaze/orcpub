// Run design captures against the app on ORCPUB_BASE (default http://localhost:8890):
//   NODE_PATH=$(npm root -g) NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node docs/design/capture/capture.js <round>...
// With no round, lists them. OUT=dir writes there instead of beside the round's review page.
const path = require('path');
const { Capture } = require('./lib');
const rounds = require('./rounds');

(async () => {
  const names = process.argv.slice(2);
  if (!names.length) {
    for (const [name, r] of Object.entries(rounds)) console.log(`${name.padEnd(26)} ${r.what}`);
    return;
  }
  const unknown = names.filter(n => !rounds[n]);
  if (unknown.length) throw new Error(`no round ${unknown.join(', ')}; run with no arguments for the list`);
  for (const name of names) {
    const out = process.env.OUT || path.join(__dirname, '..', rounds[name].out);
    console.log(`== ${name} -> ${path.relative(process.cwd(), out) || '.'}`);
    const cap = await Capture.launch(out);
    try { await rounds[name].run(cap); } finally { await cap.close(); }
  }
})().catch(e => { console.error(e.message || e); process.exit(1); });
