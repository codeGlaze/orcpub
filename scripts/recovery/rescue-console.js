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
      console.log('So whatever is crashing the page, it is not an unreadable character value.');
      console.log('Send a screenshot of this console to support, including the list below.');
      console.table(targets);
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
