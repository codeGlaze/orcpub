/* ===========================================================================
 * OrcPub / Dungeon Master's Vault — fix a character that crashes the site
 *
 * WHAT TO DO
 *   1. Log in, and open your CHARACTER LIST page.
 *   2. Press F12 (Cmd+Option+I on a Mac), click the "Console" tab.
 *   3. Paste this whole file and press Enter.
 *   4. Do what it tells you. It is two clicks.
 *
 * That's it. It finds the broken character itself, saves you a backup copy,
 * and opens it. You click EDIT, then SAVE, and it's fixed for good.
 *
 * ── WHAT WENT WRONG (for the curious) ────────────────────────────────────
 * The character has an empty name where a name should be, stored as a lone
 * ":". The site can't read that, and the error happens somewhere it can't be
 * caught, so the whole page dies instead of showing a message. It's saved on
 * the server, which is why a private window and a different computer behave
 * the same. Nothing is lost — the fix below rewrites it cleanly.
 * ======================================================================== */
(() => {
  const R = {};
  // Bump this on every change. It is printed on load and stamped into every
  // report, so a pasted console log always says which build produced it.
  const VERSION = 'v5 (2026-09-13)';

  // The two shapes the reader chokes on. Quoted text is matched first and
  // passed through untouched, so a ':' inside your notes is never altered.
  const BARE = /"(?:[^"\\]|\\.)*"|:(?=[\s,{}\[\]()";]|$)/g;
  const NSEMPTY = /"(?:[^"\\]|\\.)*"|:[^\s,{}\[\]()";/]+\/(?=[\s,{}\[\]()";]|$)/g;

  function heal(text) {
    if (typeof text !== 'string') return { text, count: 0 };
    let n = 0;
    let out = text.replace(BARE, (m) => (m[0] === '"' ? m : ':unnamed-' + ++n));
    out = out.replace(NSEMPTY, (m) => (m[0] === '"' ? m : m + 'unnamed-' + ++n));
    return { text: out, count: n };
  }

  const token = () => {
    try { return ((localStorage.getItem('user') || '').match(/:token\s+"([^"]+)"/) || [])[1]; }
    catch (e) { return undefined; }
  };
  const authHeaders = (extra) => {
    const t = token();
    return Object.assign({}, extra || {}, t ? { Authorization: 'Token ' + t } : {});
  };
  const download = (name, text) => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([text], { type: 'application/octet-stream' }));
    a.download = name; document.body.appendChild(a); a.click(); a.remove();
  };
  const big = (msg, color) =>
    console.log('%c' + msg, `font-size:15px;font-weight:bold;color:${color || '#0a0'}`);
  const step = (msg) => console.log('%c' + msg, 'font-size:14px');

  // ── EDN scanning, depth-aware ────────────────────────────────────────────
  // Deliberately NOT a real reader: the whole problem is that this data can be
  // unreadable. But it must respect nesting — a character summary embeds its
  // classes as nested maps that carry their own :db/id, and grabbing those by
  // a flat regex yields component ids that are not characters at all.

  // The outermost {...} chunks in `text`, strings respected.
  const mapChunks = (text) => {
    const chunks = [];
    let depth = 0, start = -1, inStr = false, esc = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (inStr) {
        if (esc) esc = false; else if (c === '\\') esc = true; else if (c === '"') inStr = false;
        continue;
      }
      if (c === '"') { inStr = true; continue; }
      if (c === '{') { if (depth === 0) start = i; depth++; }
      else if (c === '}') { depth--; if (depth === 0 && start >= 0) { chunks.push(text.slice(start, i + 1)); start = -1; } }
    }
    return chunks;
  };

  // A map's own fields, with everything nested deeper stripped out.
  const shallow = (chunk) => {
    let out = '', depth = 0, inStr = false, esc = false;
    for (let i = 0; i < chunk.length; i++) {
      const c = chunk[i];
      if (inStr) {
        if (depth <= 1) out += c;
        if (esc) esc = false; else if (c === '\\') esc = true; else if (c === '"') inStr = false;
        continue;
      }
      if (c === '"') { inStr = true; if (depth <= 1) out += c; continue; }
      if (c === '{' || c === '[' || c === '(') { depth++; if (depth <= 1) out += ' '; continue; }
      if (c === '}' || c === ']' || c === ')') { if (depth <= 1) out += ' '; depth--; continue; }
      if (depth <= 1) out += c;
    }
    return out;
  };

  const ownId = (chunk) => (shallow(chunk).match(/:db\/id\s+(\d+)/) || [])[1];
  const ownName = (chunk) => (shallow(chunk).match(/character-name\s+"((?:[^"\\]|\\.)*)"/) || [])[1];

  // ── the characters they own ──────────────────────────────────────────────
  R.list = async () => {
    const res = await fetch('/dnd/5e/character-summaries', { headers: authHeaders({ Accept: 'application/edn' }) });
    const raw = await res.text();
    if (res.status !== 200) { console.log('Could not read your character list — HTTP', res.status); return []; }
    return mapChunks(raw)
      .map((chunk) => ({ id: ownId(chunk), name: ownName(chunk) || '(no name)' }))
      .filter((r) => r.id);
  };

  // ── everyone in their parties (a broken party member may not be theirs) ──
  // Only the ids inside ::party/character-ids — NOT the party's own :db/id,
  // which is not a character and would 400.
  R.partyMemberIds = async () => {
    const res = await fetch('/dnd/5e/parties', { headers: authHeaders({ Accept: 'application/edn' }) });
    if (res.status !== 200) return [];
    const raw = await res.text();
    const ids = [];
    mapChunks(raw).forEach((party) => {
      const at = party.indexOf('character-ids');
      if (at < 0) return;
      mapChunks(party.slice(at)).forEach((member) => {
        const id = ownId(member);
        if (id) ids.push(id);
      });
    });
    return [...new Set(ids)];
  };

  // ── look at one character (never parses it, so it cannot crash) ─────────
  R.inspect = async (id) => {
    const res = await fetch(`/dnd/5e/characters/${id}`, { headers: authHeaders({ Accept: 'application/edn' }) });
    const raw = await res.text();
    // The server answers 400 with an empty body when there is no character
    // with that id (or it has no owner) — that is "not found", not "healthy".
    const missing = res.status !== 200 || /^\s*\[\s*\]\s*$/.test(raw);
    return { id, status: res.status, missing, count: missing ? 0 : heal(raw).count, raw };
  };

  R.check = async (id) => {
    const r = await R.inspect(id);
    if (r.status === 401) console.log(`character ${id}: you are not logged in (HTTP 401).`);
    else if (r.missing) console.log(`character ${id}: no character with that id on this account (HTTP ${r.status}). Double-check the id — the number at the end of the character's URL.`);
    else if (r.count) console.log(`character ${id}: BROKEN — ${r.count} unreadable value(s). This is the one killing the page.`);
    else console.log(`character ${id}: reads fine, ${r.raw.length} chars. Not the culprit.`);
    return r;
  };

  // ── stop the crash in this tab ───────────────────────────────────────────
  // The site reads server responses off XMLHttpRequest; repairing the text
  // there means the reader never sees the bad value, so nothing throws.
  R.arm = () => {
    if (R._armed) return;
    const proto = XMLHttpRequest.prototype;
    ['response', 'responseText'].forEach((prop) => {
      const desc = Object.getOwnPropertyDescriptor(proto, prop);
      if (!desc || !desc.get) return;
      Object.defineProperty(proto, prop, {
        configurable: true,
        get() {
          const v = desc.get.call(this);
          return typeof v === 'string' ? heal(v).text : v;
        },
      });
    });
    R._armed = true;
  };

  // ── open a character without reloading (a reload would undo the repair) ──
  R.open = (id) => {
    history.pushState({}, '', '/pages/dnd/5e/characters/' + id);
    window.dispatchEvent(new PopStateEvent('popstate'));
  };

  // ── the whole thing, start to finish ─────────────────────────────────────
  R.auto = async () => {
    console.log(`%cOrcPub rescue ${VERSION}`, 'font-weight:bold');
    console.log('Looking for the broken character…');

    if (!token()) {
      big('You are not logged in.', '#c00');
      step('Log in first, open your character list, then paste this again.');
      return;
    }

    const rows = await R.list();
    // also check everyone in their parties — the character breaking the
    // Parties page is not necessarily one they own
    const extra = (await R.partyMemberIds()).filter((id) => !rows.some((r) => r.id === id));
    const targets = rows.concat(extra.map((id) => ({ id, name: '(from a party)' })));

    if (!targets.length) {
      big('No characters found on this account.', '#c00');
      step('Make sure you are logged in on dungeonmastersvault.com, then paste this again.');
      return;
    }

    // Some accounts have hundreds of characters, so check several at a time
    // and report progress — otherwise this just looks frozen.
    console.log(`Checking ${targets.length} character(s)…`);
    const broken = [];
    let done = 0, nextTick = 0;
    const queue = targets.slice();
    const worker = async () => {
      for (;;) {
        const row = queue.shift();
        if (!row) return;
        try {
          const r = await R.inspect(row.id);
          if (r.count) broken.push(Object.assign({}, row, r));
        } catch (e) { /* one bad fetch must not stop the sweep */ }
        done++;
        const pct = Math.floor((done / targets.length) * 100);
        if (pct >= nextTick) { console.log(`   …${pct}% (${done}/${targets.length})`); nextTick += 25; }
      }
    };
    await Promise.all(Array.from({ length: 6 }, worker));

    if (!broken.length) {
      big('None of your characters have this particular problem.', '#c60');
      console.log(`Checked ${targets.length} character(s) — your list plus everyone in your parties.`);
      console.log('So whatever is crashing the page, it is not the empty-value bug this');
      console.log('script was written for. Let us find out what it actually is:');
      console.log('');
      step('  1. Open the character that crashes, and copy the number at the end');
      step('     of the address bar (…/characters/THISNUMBER).');
      step('  2. Run:   await orcpubRescue.diagnose(THATNUMBER)');
      console.log('');
      console.log('That reproduces the crash on purpose and prints the REAL error behind');
      console.log('the unhelpful "Uncaught mk", plus anything odd in the saved data.');
      console.log('Then  orcpubRescue.report()  copies it all for support.');
      return;
    }

    console.log(`Found ${broken.length} broken character(s):`);
    console.table(broken.map((b) => ({ id: b.id, name: b.name, problems: b.count })));

    // Save a copy before touching anything.
    broken.forEach((b) => {
      download(`${(b.name || 'character').replace(/[^\w -]/g, '')}-${b.id}-backup.edn`, b.raw);
    });
    console.log('Saved a backup copy of each to your Downloads folder.');

    R.arm();
    const first = broken[0];
    R.open(first.id);

    setTimeout(() => {
      console.log('');
      big(`✓ "${first.name}" is open now.`);
      console.log('');
      step('Finish the repair with two clicks:');
      step('    1.  Click  EDIT   (button at the top of the character)');
      step('    2.  Click  SAVE   (button at the top of the builder)');
      console.log('');
      console.log('%cImportant: do not refresh the page until after you click SAVE.',
        'font-size:13px;color:#c60');
      console.log('');
      console.log('Once you have saved, refresh normally — the character and the Parties');
      console.log('page will both work again, for good. You do not need this script again.');
      console.log('');
      console.log('If SAVE seems to do nothing, the character is missing something the');
      console.log('builder requires (usually ability scores — you will see a message say so).');
      console.log('Fill that in, then click SAVE again.');
      if (broken.length > 1) {
        console.log('');
        console.log('Then repeat for the others by running:');
        broken.slice(1).forEach((b) => console.log(`    orcpubRescue.open(${b.id})   // ${b.name}`));
      }
    }, 3000);
  };

  // ── DIAGNOSTIC: for when the sweep finds nothing but a page still dies ──
  // Two halves. First, scan the raw data for anything the reader would reject.
  // Second — and this is the useful half — catch the real error. The site
  // reports these crashes as an opaque minified name ("Uncaught mk"), but the
  // thrown object is an ExceptionInfo carrying .message and .data. Grabbing it
  // from an error handler turns that into an actual sentence.

  const DELIM = new Set([' ', '\t', '\n', '\r', ',', '{', '}', '[', ']', '(', ')', '"', ';']);

  // Every keyword token the EDN reader would choke on, with context.
  const badKeywords = (raw) => {
    const bad = [];
    let i = 0, inStr = false, esc = false;
    while (i < raw.length) {
      const c = raw[i];
      if (inStr) {
        if (esc) esc = false; else if (c === '\\') esc = true; else if (c === '"') inStr = false;
        i++; continue;
      }
      if (c === '"') { inStr = true; i++; continue; }
      if (c === ':') {
        let j = i + 1;
        if (raw[j] === ':') j++;
        const start = j;
        while (j < raw.length && !DELIM.has(raw[j])) j++;
        const name = raw.slice(start, j);
        let why = null, fatal = true;
        if (name === '') why = 'an empty keyword — a lone ":"';
        else if (name.endsWith('/')) why = 'a keyword with nothing after the "/"';
        else if (name.startsWith('/')) why = 'a keyword with an empty namespace';
        else if (/^\d/.test(name.split('/').pop())) { why = 'a keyword whose name starts with a digit'; fatal = false; }
        if (why) bad.push({ token: raw.slice(i, j), at: i, why, fatal, ctx: raw.slice(Math.max(0, i - 60), j + 15).replace(/\s+/g, ' ') });
        i = j; continue;
      }
      i++;
    }
    return bad;
  };

  // Truncation / unterminated strings — the other way data becomes unreadable.
  const structure = (raw) => {
    const notes = [];
    let depth = { '{': 0, '[': 0, '(': 0 }, inStr = false, esc = false;
    const close = { '}': '{', ']': '[', ')': '(' };
    for (let i = 0; i < raw.length; i++) {
      const c = raw[i];
      if (inStr) { if (esc) esc = false; else if (c === '\\') esc = true; else if (c === '"') inStr = false; continue; }
      if (c === '"') { inStr = true; continue; }
      if (depth[c] !== undefined) depth[c]++;
      else if (close[c]) depth[close[c]]--;
    }
    if (inStr) notes.push('the data ends inside an unterminated string — it looks cut off');
    Object.entries(depth).forEach(([k, v]) => {
      if (v > 0) notes.push(`${v} unclosed "${k}" — the data looks truncated`);
      if (v < 0) notes.push(`${-v} extra closing "${k === '{' ? '}' : k === '[' ? ']' : ')'}"`);
    });
    return notes;
  };

  // Catch what the site actually threw, including ExceptionInfo details.
  R._caught = [];
  // ExceptionInfo carries ex-data as a compiled ClojureScript map, so
  // JSON.stringify spits out minified internals. The readable part is the
  // keyword names inside it — pull those out and drop the rest.
  const exData = (d) => {
    if (d === undefined || d === null) return null;
    let s;
    try { s = JSON.stringify(d); } catch (e) { return null; }
    if (!s) return null;
    const names = [...s.matchAll(/"name":"([^"]+)"/g)].map((m) => m[1]);
    if (names.length) return [...new Set(names)].join(', ');
    return s.length <= 160 ? s : null;
  };
  const describe = (err) => {
    if (!err) return '(no error object)';
    const bits = [];
    if (err.message) bits.push(err.message);
    else bits.push(String(err));
    const d = exData(err.data);
    if (d) bits.push('(' + d + ')');
    if (err.cause && err.cause.message) bits.push('caused by: ' + err.cause.message);
    return bits.join(' ');
  };
  R.listen = () => {
    if (R._listening) return;
    window.addEventListener('error', (e) => R._caught.push({ kind: 'error', text: describe(e.error), stack: (e.error && e.error.stack || '').split('\n').slice(0, 4).join(' / ') }));
    window.addEventListener('unhandledrejection', (e) => R._caught.push({ kind: 'promise', text: describe(e.reason) }));
    R._listening = true;
  };

  // Full work-up of ONE character: the one whose page dies.
  R.diagnose = async (id) => {
    if (!id) {
      big('I need the id of the character that crashes.', '#c00');
      step('Open the character that breaks the page, copy the number at the end of');
      step('the address bar, and run:   await orcpubRescue.diagnose(THATNUMBER)');
      return;
    }
    console.log(`%cDiagnostic for character ${id}  (rescue ${VERSION})`, 'font-weight:bold;font-size:14px');
    const r = await R.inspect(id);
    console.log(`  server response : HTTP ${r.status}, ${r.raw.length} characters`);

    if (r.status === 401) { big('You are not logged in.', '#c00'); return; }
    if (r.missing) {
      big('There is no character with that id on this account.', '#c60');
      step('Check the number at the end of the character URL — it is easy to mistype.');
      step('Or run  await orcpubRescue.list()  to see your characters and their ids.');
      return;
    }

    const kws = badKeywords(r.raw);
    const struct = structure(r.raw);
    if (kws.length) {
      console.log(`  unreadable values: ${kws.length}`);
      kws.slice(0, 5).forEach((k) => {
        console.log(`     ${k.fatal ? 'BREAKS THE READER' : 'suspicious'} — ${k.why}`);
        console.log(`        …${k.ctx}…`);
      });
    } else {
      console.log('  unreadable values: none');
    }
    struct.forEach((n) => console.log('  structure       : ' + n));
    if (!kws.length && !struct.length) console.log('  structure       : looks intact');

    // Now let it actually fail, and catch what it really throws.
    R._caught = [];
    R.listen();
    console.log('  reproducing the crash to capture the real error…');
    R.open(id);
    await new Promise((res) => setTimeout(res, 6000));

    console.log('');
    if (R._caught.length) {
      big('The real error behind "Uncaught mk":', '#c00');
      R._caught.slice(0, 4).forEach((c) => {
        console.log('   ' + c.text);
        if (c.stack) console.log('      at ' + c.stack);
      });
    } else {
      big('No error fired — the character loaded without crashing.', '#0a0');
      console.log('   If the page still looks wrong, the problem is in how it renders,');
      console.log('   not in reading the data.');
    }
    R.findings = { version: VERSION, id, status: r.status, size: r.raw.length, badKeywords: kws, structure: struct, errors: R._caught };
    console.log('');
    console.log('Run  orcpubRescue.report()  to copy all of this for support.');
    return R.findings;
  };

  R.report = () => {
    const f = R.findings;
    if (!f) return console.log('Run  await orcpubRescue.diagnose(<id>)  first.');
    const txt = [
      `OrcPub rescue ${VERSION} — diagnostic`,
      `when: ${new Date().toISOString()}`,
      `character: ${f.id}   HTTP ${f.status}   ${f.size} chars`,
      `unreadable values (${f.badKeywords.length}):`,
      ...f.badKeywords.slice(0, 10).map((k) => `   ${k.why}  →  …${k.ctx}…`),
      `structure: ${f.structure.length ? f.structure.join('; ') : 'intact'}`,
      `errors caught (${f.errors.length}):`,
      ...f.errors.slice(0, 6).map((e) => `   [${e.kind}] ${e.text}${e.stack ? '  @ ' + e.stack : ''}`),
    ].join('\n');
    (navigator.clipboard ? navigator.clipboard.writeText(txt) : Promise.reject())
      .then(() => console.log('Copied to your clipboard — paste it to support.'))
      .catch(() => console.log('Copy the text below:\n\n' + txt));
    return txt;
  };

  // ── extras, for support / if something goes sideways ────────────────────
  R.save = (id) => R.inspect(id).then((r) => { download(`character-${id}-backup.edn`, r.raw); console.log('Backed up', id); });

  // Last resort. Deleting also clears the character out of any party.
  R.remove = async (id) => {
    const r = await R.inspect(id);
    if (r.status === 200) download(`character-${id}-backup.edn`, r.raw);
    const res = await fetch(`/dnd/5e/characters/${id}`, { method: 'DELETE', headers: authHeaders() });
    console.log(res.status === 200 ? `Deleted ${id} (backup downloaded first).` : `Delete failed — HTTP ${res.status}`);
  };

  // Unbreak the Parties page without touching the character.
  R.detach = async (partyId, characterId) => {
    const res = await fetch(`/dnd/5e/parties/${partyId}/characters/${characterId}`, { method: 'DELETE', headers: authHeaders() });
    console.log('Removed', characterId, 'from party', partyId, '→ HTTP', res.status);
  };

  R.buildInfo = async () => {
    const src = [...document.querySelectorAll('script[src]')].map((s) => s.src).find((u) => /orcpub\.js/.test(u));
    if (!src) return console.log('No orcpub.js on this page.');
    const js = await (await fetch(src)).text();
    const patched = js.includes(':unnamed-') || js.includes('decode-error');
    console.log(`This site's build ${patched ? 'HAS' : 'does NOT have'} the fix.`);
    return patched;
  };

  window.orcpubRescue = R;
  R.auto();
})();
