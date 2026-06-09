# HANDOFF — "Black screen on the Features tab" (character section render crashes)

**Status:** fixed on branch `claude/character-black-screen-feature-i8lvk3` (3 commits + this doc). Not yet merged; **not yet compiled/verified in a real cljs build** (see Verification).

**Audience:** future Claude agents and maintainers. This documents the bug class, the
specific cases found, the fixes, and — most importantly — **how to diagnose any future
occurrence** so nobody has to rediscover this.

---

## 1. Symptom

A character renders fine, but clicking the **Features** tab (or sometimes other tabs)
turns the whole page black/blank. Historically "random," never tracked down, reported
across years and across the codebase's "big update." No console recovery — the page is dead.

## 2. Root cause (the bug *class*)

The character sheet (`character-display` in `src/cljs/orcpub/dnd/e5/views.cljs`) renders
each section via `details-tabs`. The Features section (`features-details`) builds lists of
**actions / bonus-actions / reactions / traits** and sorts each with
`common/aloof-sort-by :name` (`src/cljc/orcpub/common.cljc`):

```clojure
(defn aloof-sort-by [sorter coll]
  (sort-by (comp s/lower-case sorter) coll))   ; OLD — throws on nil
```

If **any** feature in those lists has a `nil`/blank `:name`, `clojure.string/lower-case`
calls `.toLowerCase()` on `null` in ClojureScript → **TypeError** thrown *inside
`Array.sort`* during render. There was **no React error boundary**, so the exception
unmounted the entire React tree → black screen.

**Why "random":** it only fires when a character includes a feature whose *definition*
omits `:name`. That depends on specific build choices and/or which custom content is loaded.

## 3. The two concrete cases we found

### (a) Built-in: Hunter Ranger "Evasion"  → crashes for everyone
- The feature: Hunter conclave, level-15 **Superior Hunter's Defense → Evasion**
  (`src/cljc/orcpub/dnd/e5/classes.cljc`, the Hunter subclass `:levels` map).
- The Evasion option added a descriptive trait via `(mod5e/trait-cfg {:page 93 :summary "When you are subjected to an effect..."})` **with no `:name`** — unlike its siblings
  (Whirlwind Attack, Stand Against the Tide, Uncanny Dodge), which all name their trait.
- **Introduced:** commit `30e9c71` (2020-05-09, *"Fix rangers traits - omg."*, DatDamnZotz),
  in **upstream OrcPub**. The 2019 version (`d48f4a2`) was correct
  (`(mod5e/trait-cfg (opt5e/evasion 15 93))`, where `opt5e/evasion` returns a *named* map).
  That commit unwrapped `opt5e/evasion` (turning it into a no-op bare map in `:modifiers`)
  and added the nameless inline trait-cfg.
- Repro character (level-20 Wood Elf Ranger/Hunter, owner `annadarkheart`):
  `https://www.dungeonmastersvault.com/pages/dnd/5e/characters/17592226989362?frame=true`

### (b) Custom/imported: a Druid → crashes only when the content is loaded
- Character (Druid 16, Circle of the Land, owner `annadarkheart`):
  `https://www.dungeonmastersvault.com/pages/dnd/5e/characters/17592248048004?frame=true`
- References **custom content not in the repo**: `:centaur` (playable race — only a *monster*
  exists built-in), `:the-perceptive-acolyte` (0 matches in code), and a `:war-caster` feat key.
- **Renders fine anonymously** (custom content not loaded → feature never added → no crash),
  but black-screens for the owner (content loaded → a custom feature with no `:name` is added).
  This is the "app shouldn't choke on imported content" case. We could **not** name the exact
  culprit remotely because it isn't loaded in an anonymous session — needs the owner's logged-in
  session or their orcbrew file (use the console snippet in §6).

**Scan result:** after fixing Evasion, a full scan of all built-in feature constructors
(`trait-cfg`, `trait`, `dependent-trait(-2)`, `action`, `bonus-action`, `reaction`, `attack`)
and all `:traits [...]` vectors found **zero** remaining nameless features in built-in content.
Remaining risk is purely **custom/imported** content — which the defensive fix covers.

## 4. Fixes applied (branch `claude/character-black-screen-feature-i8lvk3`)

1. **`2944c5a` — null-safe sort (defensive, covers ALL sources).**
   `src/cljc/orcpub/common.cljc`: `aloof-sort-by` coerces the key via `str`:
   `(sort-by (comp s/lower-case str sorter) coll)`. A nil/blank/non-string name now sorts
   as `""` instead of throwing. This is the durable guarantee: the app no longer chokes on a
   nameless feature regardless of whether it's built-in or imported.

2. **`e6f124f` — source fix for Evasion.**
   `src/cljc/orcpub/dnd/e5/classes.cljc`: added `:name "Evasion"` to the Hunter Evasion
   `trait-cfg`. (The stray bare `(opt5e/evasion 15 93)` in `:modifiers` is now redundant; a
   separate, *non-crashing* nitpick — see §7.)

3. **`718e176` — error boundary + recovery panel (the "let users fix it" feature).**
   `src/cljs/orcpub/dnd/e5/views.cljs`: new `error-boundary` (React boundary via
   `r/create-class` + `:component-did-catch` + an `r/atom`) and `feature-render-error`
   recovery panel. The per-tab content of `character-display` is wrapped, **keyed by tab**
   (so switching tabs auto-clears the error). On any render crash it shows a panel instead of
   blanking, with: a **best-effort diagnosis** (lists features missing a name + descriptions),
   **Edit this character** (dispatches `:edit-character` → loads it in the builder to fix),
   **Try again**, **Reload**, and collapsible technical details. Because it wraps *inside*
   `character-display`, the sheet, builder preview, and party view all benefit.

## 5. Still open

- **Druid custom-content culprit unnamed** — needs the owner's session or orcbrew (see §6).
- **PR not opened** (do not open without the user asking).
- **No live cljs build verification** of `718e176` (see Verification).

## 6. HOW TO DIAGNOSE A NEW OCCURRENCE (reusable playbook)

This environment has **no browser tooling preinstalled and no cljs build**, but network egress
works. The winning approach was a headless browser that loads the live character and catches the
exact failure. Scripts live in `/tmp/browsedmv/` during a session (ephemeral — recreate as needed).

**Setup (once):**
```bash
cd /tmp && mkdir -p browsedmv && cd browsedmv && npm init -y && npm i playwright@latest
npx playwright install chromium
```
**Gotchas:** the network proxy uses its own cert → Playwright needs `ignoreHTTPSErrors: true`.
The character data is **not** in localStorage; it's fetched as **EDN** from
`GET /dnd/5e/characters/<id>` and held in the in-memory re-frame app-db.

**Catch the exact nameless map (the key technique):** monkeypatch `Array.prototype.sort` to
catch the comparator throw and dump the two items being compared — one has the nil `:name`.
Decode cljs maps in JS via `.arr`/`.cnt` (PersistentArrayMap) and keyword `.fqn`. The
ready-made console snippet is below (also used as `diagnose.js`); paste it on a **fresh load,
before clicking the tab**, in the **owner's logged-in session** for custom-content cases:

```js
(() => {
  function kw(v){ if(v==null) return 'nil'; if(v.fqn!==undefined) return ':'+v.fqn; if(typeof v==='string') return JSON.stringify(v); if(typeof v==='number'||typeof v==='boolean') return String(v); return undefined; }
  function show(x,d){ d=d||0; if(d>4) return '…'; if(x==null) return 'nil'; var k=kw(x); if(k!==undefined) return k;
    try{ if(x.arr && typeof x.cnt==='number'){ var p=[]; for(var i=0;i<x.arr.length;i+=2){ p.push(show(x.arr[i],d+1)+' '+show(x.arr[i+1],d+1)); } return '{'+p.join(', ')+'}'; } }catch(e){}
    try{ var s=x.toString(); if(s && s!=='[object Object]') return s; }catch(e){}
    return '?'; }
  const _sort = Array.prototype.sort;
  Array.prototype.sort = function(cmp){
    if (typeof cmp === 'function') {
      return _sort.call(this, function(a,b){ try { return cmp(a,b); }
        catch(e){ console.warn('NAMELESS FEATURE FOUND:'); console.warn('  A:', show(a&&a.value!==undefined?a.value:a)); console.warn('  B:', show(b&&b.value!==undefined?b.value:b)); return 0; } });
    }
    return _sort.call(this, cmp);
  };
  window.addEventListener('error', e => console.warn('UNCAUGHT:', e.message, e.error && e.error.stack));
  console.log('[diagnose] armed — now click the tab.');
})();
```

**Static scans (in repo):**
- Nameless built-in features: scan `(mod5e|modifiers)/(trait-cfg|trait|dependent-trait|dependent-trait-2|action|bonus-action|reaction|attack)` call sites + `:traits [...]` vectors for maps lacking `:name`. (Currently zero.)
- Identify custom content a character uses: fetch its EDN, extract `:orcpub.entity.strict/key` values, and grep the repo — keys with **no built-in definition** are imported/homebrew.

**Find when a bug was introduced:** this is a **shallow clone** (`.git/shallow`, ~23 cut points)
and blame/pickaxe on the current path may dead-end at a re-import commit. Pickaxe the *exact
text* across all objects and sort by date: `git log --all --oneline --format='%h %ad %s' --date=short -S "<unique string>"` then inspect the oldest, and confirm with `git show <c> -- <file>`.

## 7. Related, NON-crashing note (low priority)
`(opt5e/evasion ...)` sits **bare** in a `:modifiers` vector (a no-op) at
`classes.cljc` (Hunter ~1926, Rogue ~2039) and `templates/ua_revised_ranger.cljc` ~199.
Effect: Evasion may **silently not display** as a trait for Rogue / UA-Ranger. Not a black
screen. Fix by wrapping in a constructor (e.g. `(mod5e/dependent-trait (opt5e/evasion ...))`)
if/when desired.

## Verification status
- Reproduced the crash live (headless Chromium) and captured the exact stack
  (`lower-case` on null inside `Array.sort`) and the exact nameless map (Evasion).
- Verified the in-memory `Array.sort` guard renders the Features tab (zero errors, tree intact).
- `views.cljs` is **delimiter-balanced** across the whole file after edits.
- **NOT** done: a real cljs compile of the error-boundary change (no lein/deps/build in this
  env). Build it / run CI before shipping `718e176`.
