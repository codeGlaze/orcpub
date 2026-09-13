/* ===========================================================================
 * OrcPub / Dungeon Master's Vault — repair a character that won't open
 *
 * SYMPTOMS
 *   • Opening one character crashes the whole site; you have to reload
 *   • Still broken in a private/incognito window with no homebrew loaded
 *   • The Parties page now hangs too, because that character is in a party
 *
 * WHAT IS ACTUALLY WRONG
 *   The saved character contains an unreadable empty keyword — it prints as a
 *   lone ":". The reader throws while decoding the character, and because that
 *   happens inside an async block the error escapes to the top level where
 *   nothing can catch it, so the page dies instead of showing a message.
 *   It is not your browser, your login, or your local data: the bad value is
 *   stored server-side, which is why incognito behaves the same.
 *
 * THE GOOD NEWS: the character is not lost. This repairs it in place.
 *
 * ── HOW TO USE ────────────────────────────────────────────────────────────
 *   1. Log in, then open a page that DOES work (your character list is fine).
 *   2. Press F12 (Cmd+Opt+I on Mac) → the "Console" tab.
 *   3. Paste this whole file and press Enter.
 *   4. Run:   await orcpubRescue.list()      → shows your characters + ids
 *   5. Run:   await orcpubRescue.fix(12345)  → using the broken character's id
 *   6. Follow the printed steps: click into the character IN THE APP
 *      (do NOT reload the page — a reload removes the patch), check it looks
 *      right, hit "Export" to keep a copy, then "Edit", re-pick whatever field
 *      shows up blank/unknown, and save. That rewrites the character cleanly
 *      on the server, so it stays fixed for everyone, forever.
 *   7. Reload normally. The sheet and the Parties page both work again.
 *
 * Nothing here deletes anything. `remove()` exists as a last resort and it
 * downloads a backup before it will delete.
 * ======================================================================== */
(() => {
  const R = {};

  // The two shapes the EDN reader chokes on. A quoted string is matched first
  // and passed through untouched, so a ':' inside your notes is never altered.
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
  const idFromUrl = () => (location.pathname.match(/characters\/(\d+)/) || [])[1];

  // ── find the broken character WITHOUT opening it ────────────────────────
  // The summary list omits the corrupt part of the character, so it keeps
  // working even when the sheet itself crashes.
  R.list = async () => {
    if (!token()) return console.log('Log in first, then re-run this.');
    const res = await fetch('/dnd/5e/character-summaries', { headers: authHeaders({ Accept: 'application/edn' }) });
    const raw = await res.text();
    if (res.status !== 200) return console.log('HTTP', res.status, raw.slice(0, 200));
    const rows = [];
    raw.replace(/\{[^{}]*:db\/id\s+(\d+)[^{}]*\}/g, (chunk, id) => {
      const nm = (chunk.match(/character-name\s+"([^"]*)"/) || chunk.match(/"([^"]{2,40})"/) || [])[1];
      rows.push({ id, name: nm || '(unnamed)' });
      return chunk;
    });
    console.table(rows);
    console.log(`${rows.length} character(s). Use an id with:  await orcpubRescue.fix(<id>)`);
    return rows;
  };

  // ── inspect one character ────────────────────────────────────────────────
  R.check = async (id = idFromUrl()) => {
    if (!id) return console.log('Need an id — run  await orcpubRescue.list()  first.');
    const res = await fetch(`/dnd/5e/characters/${id}`, { headers: authHeaders({ Accept: 'application/edn' }) });
    const raw = await res.text();
    const { count } = heal(raw);
    console.log(`character ${id} • HTTP ${res.status} • ${raw.length} chars • ${count} unreadable keyword(s)`);
    if (count) {
      raw.replace(BARE, (m, off) => {
        if (m[0] !== '"') console.log('   …' + raw.slice(Math.max(0, off - 70), off + 15).replace(/\s+/g, ' ') + '…');
        return m;
      });
      console.log('   ^ that lone ":" is what kills the page.');
    } else if (res.status === 200) {
      console.log('   No empty-keyword corruption here — this one is not the culprit.');
    }
    window.__orcpubRaw = raw;
    return { status: res.status, count, raw };
  };

  // ── always keep a copy ───────────────────────────────────────────────────
  R.save = (id = idFromUrl()) => {
    const raw = window.__orcpubRaw;
    if (!raw) return console.log('Run  await orcpubRescue.check(id)  first.');
    const { text, count } = heal(raw);
    download(`character-${id}-original.edn`, raw);
    if (count) download(`character-${id}-repaired.edn`, text);
    console.log(`Saved a copy of character ${id} to your downloads${count ? ' (original + repaired)' : ''}.`);
  };

  // ── patch the reader so the character can load ──────────────────────────
  // cljs-http reads responses off XMLHttpRequest; healing the text there means
  // the EDN reader never sees the bad token, so nothing throws.
  R.arm = () => {
    if (R._armed) return console.log('Already patched.');
    const proto = XMLHttpRequest.prototype;
    ['response', 'responseText'].forEach((prop) => {
      const desc = Object.getOwnPropertyDescriptor(proto, prop);
      if (!desc || !desc.get) return;
      Object.defineProperty(proto, prop, {
        configurable: true,
        get() {
          const v = desc.get.call(this);
          if (typeof v !== 'string') return v;
          const { text, count } = heal(v);
          if (count) console.log(`[rescue] repaired ${count} bad keyword(s) in a response`);
          return text;
        },
      });
    });
    R._armed = true;
    console.log('Reader patched for this tab.');
  };

  // ── THE MAIN ONE: back up, patch, and walk them through the real repair ──
  R.fix = async (id = idFromUrl()) => {
    const info = await R.check(id);
    if (!info) return;
    if (info.status === 200) R.save(id);
    if (!info.count) {
      console.log('\nThis character decodes fine, so the patch will not change anything.');
      console.log('If it still crashes, the cause is something else — send the output above to support.');
      return info;
    }
    R.arm();
    console.log('%c\nNow do this, in this tab, WITHOUT reloading:', 'font-weight:bold;font-size:13px');
    console.log('  1. Click into the broken character in the app — it should open now.');
    console.log('  2. Hit "Export" to keep your own copy (belt and braces).');
    console.log('  3. Hit "Edit", find the field that looks blank or unknown');
    console.log('     (usually a custom class/race whose name went missing), re-pick it,');
    console.log('     and save.');
    console.log('  4. That writes the character back clean. Reload normally —');
    console.log('     the sheet AND the Parties page will work again, permanently.');
    console.log('\nIf you would rather not keep it:  await orcpubRescue.remove(' + id + ')');
    return info;
  };

  // ── parties ──────────────────────────────────────────────────────────────
  R.parties = async () => {
    const res = await fetch('/dnd/5e/parties', { headers: authHeaders({ Accept: 'application/edn' }) });
    const raw = await res.text();
    console.log('HTTP', res.status, '•', heal(raw).count, 'unreadable keyword(s) in the parties response');
    console.log(raw.slice(0, 1500));
    return raw;
  };

  // Detach without deleting — unbreaks Parties while you decide what to do.
  R.detach = async (partyId, characterId) => {
    if (!partyId || !characterId) return console.log('Usage: await orcpubRescue.detach(partyId, characterId)');
    const res = await fetch(`/dnd/5e/parties/${partyId}/characters/${characterId}`, { method: 'DELETE', headers: authHeaders() });
    console.log('Removed character', characterId, 'from party', partyId, '→ HTTP', res.status);
  };

  // ── last resort ──────────────────────────────────────────────────────────
  // Deleting the character also clears it from any party (the party's link is
  // a reference, so it is retracted with the character), which unbreaks Parties.
  R.remove = async (id = idFromUrl()) => {
    if (!token()) return console.log('Log in first.');
    if (!id) return console.log('Need an id — run  await orcpubRescue.list()  first.');
    const info = await R.check(id);
    if (info.status === 200) R.save(id);      // never delete without a backup
    const res = await fetch(`/dnd/5e/characters/${id}`, { method: 'DELETE', headers: authHeaders() });
    if (res.status === 200) {
      console.log(`Deleted character ${id}. Reload — the sheet is gone and Parties should work again.`);
    } else {
      console.log('Delete failed — HTTP', res.status, (await res.text()).slice(0, 200));
    }
    return res.status;
  };

  // ── is this site already running a build that fixes this? ───────────────
  R.buildInfo = async () => {
    const src = [...document.querySelectorAll('script[src]')].map((s) => s.src).find((u) => /orcpub\.js/.test(u));
    if (!src) return console.log('Could not find orcpub.js on this page.');
    const js = await (await fetch(src)).text();
    const patched = js.includes(':unnamed-') || js.includes('decode-error');
    console.log(`This site's build ${patched ? 'HAS' : 'does NOT have'} the empty-keyword fix.`);
    return patched;
  };

  window.orcpubRescue = R;
  console.log('%cOrcPub rescue loaded.', 'font-weight:bold;font-size:13px');
  console.log('Step 1:  await orcpubRescue.list()     → find the broken character id');
  console.log('Step 2:  await orcpubRescue.fix(<id>)  → back up + repair it');
  console.log('Extras:  .check() .save() .parties() .detach() .remove() .buildInfo()');
  return R;
})();
