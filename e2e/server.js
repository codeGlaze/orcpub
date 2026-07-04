// Backend-free SPA server for orcpub frontend e2e.
// Serves real assets from resources/public; for "/" and unknown (client-route)
// paths it returns a minimal index that mounts the dev-compiled app. No backend,
// no Datomic — the homebrew/loader flows are entirely client-side (localStorage).
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', 'resources', 'public');
const PORT = process.env.E2E_PORT ? parseInt(process.env.E2E_PORT) : 8899;

const MIME = {
  '.js': 'application/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.map': 'application/json', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.ico': 'image/x-icon', '.woff': 'font/woff',
  '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.html': 'text/html; charset=utf-8',
};

// Stub window.start (cookie-consent lib normally injected by the backend page) so
// core.cljs' window.start.init(...) doesn't throw. No cookie bar => no
// click-intercepting overlay (deterministic tests).
const INDEX = `<!doctype html><html><head><meta charset="utf-8">
<title>orcpub e2e</title></head>
<body><div id="app"></div>
<script>window.start={init:function(){}};</script>
<script src="/js/compiled/orcpub.js"></script>
</body></html>`;

http.createServer((req, res) => {
  const urlPath = decodeURIComponent(req.url.split('?')[0]);
  const filePath = path.join(ROOT, urlPath);
  if (filePath.startsWith(ROOT) && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    res.writeHead(200, { 'Content-Type': MIME[path.extname(filePath)] || 'application/octet-stream' });
    fs.createReadStream(filePath).pipe(res);
    return;
  }
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(INDEX);
}).listen(PORT, 'localhost', () => console.log(`SPA server on http://localhost:${PORT}`));
