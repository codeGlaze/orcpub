// A throwaway SMTP server that accepts anything and writes what it was given to
// a JSON file. Dependency-free on purpose: it is under a hundred lines of the
// protocol and adding a package to run the tests is how test setups rot.
//
// It exists because the e2e harness had no mail server, so registration,
// verification and password reset could not be driven end to end AT ALL -- the
// verification key and the reset key only ever exist inside an email. That is
// why those flows were never covered, and why a broken reset form reached a
// merge. Point the app at this and the keys become readable:
//
//   EMAIL_SERVER_URL=127.0.0.1 EMAIL_SERVER_PORT=2525
//
// Usage:
//   const sink = await startMailSink({ port: 2525, file: '/tmp/mail.json' });
//   ... drive the app ...
//   const msg = await sink.waitFor(/verification/i);
//   sink.stop();

const net = require('net');
const fs = require('fs');

function parseMessage(raw) {
  const split = raw.indexOf('\r\n\r\n');
  const head = split === -1 ? raw : raw.slice(0, split);
  const body = split === -1 ? '' : raw.slice(split + 4);
  const headers = {};
  // Unfold continuation lines before splitting on the colon.
  for (const line of head.replace(/\r\n[ \t]+/g, ' ').split('\r\n')) {
    const i = line.indexOf(':');
    if (i > 0) headers[line.slice(0, i).toLowerCase().trim()] = line.slice(i + 1).trim();
  }
  return { headers, body, raw };
}

function startMailSink({ port = 2525, file = null } = {}) {
  const messages = [];
  const waiters = [];

  const deliver = (msg) => {
    messages.push(msg);
    if (file) fs.writeFileSync(file, JSON.stringify(messages, null, 2));
    for (let i = waiters.length - 1; i >= 0; i--) {
      if (waiters[i].match(msg)) { waiters[i].resolve(msg); waiters.splice(i, 1); }
    }
  };

  const server = net.createServer((sock) => {
    let buf = '';
    let inData = false;
    let data = '';
    const say = (s) => sock.write(s + '\r\n');
    say('220 mail-sink ready');

    sock.on('data', (chunk) => {
      buf += chunk.toString('utf8');
      let nl;
      while ((nl = buf.indexOf('\r\n')) !== -1) {
        const line = buf.slice(0, nl);
        buf = buf.slice(nl + 2);

        if (inData) {
          // A lone dot ends the message; a leading dot on any other line is stuffed.
          if (line === '.') {
            inData = false;
            deliver(parseMessage(data));
            data = '';
            say('250 2.0.0 Ok: queued');
          } else {
            data += (line.startsWith('..') ? line.slice(1) : line) + '\r\n';
          }
          continue;
        }

        const verb = line.split(/\s+/)[0].toUpperCase();
        switch (verb) {
          case 'EHLO':
            // No STARTTLS and no AUTH advertised: the client then sends neither,
            // which is what keeps this short.
            say('250-mail-sink');
            say('250 8BITMIME');
            break;
          case 'HELO': say('250 mail-sink'); break;
          case 'MAIL':
          case 'RCPT': say('250 2.1.0 Ok'); break;
          case 'AUTH': say('235 2.7.0 Accepted'); break;
          case 'RSET': say('250 2.0.0 Ok'); break;
          case 'NOOP': say('250 2.0.0 Ok'); break;
          case 'DATA': inData = true; say('354 End data with <CR><LF>.<CR><LF>'); break;
          case 'QUIT': say('221 2.0.0 Bye'); sock.end(); break;
          default: say('250 2.0.0 Ok'); break;
        }
      }
    });
    sock.on('error', () => {});
  });

  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => resolve({
      port,
      messages,
      clear: () => { messages.length = 0; if (file) fs.writeFileSync(file, '[]'); },
      // Resolves with the first message already held or next delivered that matches.
      waitFor: (re, timeoutMs = 15000) => new Promise((res, rej) => {
        const match = (m) => re.test(m.raw);
        const already = messages.find(match);
        if (already) return res(already);
        const w = { match, resolve: res };
        waiters.push(w);
        setTimeout(() => {
          const i = waiters.indexOf(w);
          if (i !== -1) { waiters.splice(i, 1); rej(new Error(`no mail matching ${re} in ${timeoutMs}ms`)); }
        }, timeoutMs);
      }),
      stop: () => new Promise((res) => server.close(res)),
    }));
  });
}

module.exports = { startMailSink };
