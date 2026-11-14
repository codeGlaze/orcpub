'use strict';
// Static file server for the browser scripts in this folder: serves a built app from a root
// directory, with the single-page-app fallback (anything not on disk gets the host page).
const http = require('http');
const fs = require('fs');
const path = require('path');

const MIME = {
  '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png',
  '.svg': 'image/svg+xml', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf',
  '.map': 'application/json'
};

/**
 * Builds an http.Server that serves files under `root`.
 * `hostPage` is the HTML sent for the root URL and for any path with no file behind it.
 * A request that resolves outside `root` (or carries a NUL byte or bad %-encoding) gets
 * an empty 404 and never reaches the filesystem.
 * Returns the server; the caller calls `.listen`.
 */
function createStaticServer(root, hostPage) {
  const base = path.resolve(root);
  const sendHost = res => { res.setHeader('Content-Type', 'text/html'); res.end(hostPage); };
  return http.createServer((req, res) => {
    let requested;
    try { requested = decodeURIComponent(req.url.split('?')[0]); } catch (e) { requested = null; }
    if (requested === null || requested.includes('\0')) { res.statusCode = 404; return res.end(); }
    const file = path.resolve(base, '.' + requested);
    if (file === base) return sendHost(res);
    if (!file.startsWith(base + path.sep)) { res.statusCode = 404; return res.end(); }
    fs.readFile(file, (err, data) => {
      if (err) return sendHost(res);
      res.setHeader('Content-Type', MIME[path.extname(file)] || 'application/octet-stream');
      res.end(data);
    });
  });
}

module.exports = { createStaticServer };
