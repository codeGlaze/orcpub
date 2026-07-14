# Handoff: "A single colon is not a valid keyword" — durable fix

You are picking up an investigation that is **complete**; what remains is
implementing the durable fix and verifying it. Everything below is verified
against real code and real data unless marked OPEN. Read it fully before coding.

## Mission

A saved character built with a custom class crashes on load with the uncaught
error **"A single colon is not a valid keyword."** The user cannot view, edit,
or delete it. An emergency console tool already exists (see §7). Your job is the
**permanent fix**: (1) prevent the corruption, (2) self-heal existing corruption
on load. This is NOT the console script — that's an ambulance, not a cure.

## 1. Root cause (VERIFIED)

`orcpub.common/name-to-kw-aux` (`src/cljc/orcpub/common.cljc:8`) derives a
keyword from a user name. For a name that is `""` or apostrophe-only (`"'"`,
`"''"`), the pipeline strips it to empty and returns the **empty keyword**
`(keyword "")`, which prints as a bare `:`:

```clojure
(s/replace $ #"'" "")   ; "'" -> ""   (apostrophes stripped first)
(s/replace $ #"\W" "-")
(keyword ns $)          ; "" -> :     THE BUG
```

Verified in a REPL: inputs `""` and `"'"` both yield `:`; `(str (keyword ""))`
is `":"`; the cljs reader throws exactly "A single colon is not a valid
keyword." on a bare `:` (JVM reader says "Invalid token: :" — same failure,
different message; use a **cljs** context to see the exact string).

## 2. How it detonates (VERIFIED)

- Homebrew/plugins persist to localStorage via `(str plugins)` and are read back
  with `cljs.reader/read-string` (`db.cljs:247`, `get-local-storage-item`).
- Characters are stored server-side and returned as **EDN** (Content-Type
  `application/edn`), read by cljs-ajax with `read-string`.
- A single bare `:` anywhere in that EDN makes the **whole** read throw. The
  throw is uncaught (the read happens in a cljs-ajax/core.async path, not inside
  the localStorage try/catch), so the page dies.

The reported character (id `17592342813688`, owner `MorkovGun`, char name
"Dymethrion") has exactly **one** bad token, confirmed by fetching it:
```
:orcpub.entity.strict/key :, :orcpub.entity.strict/option {…}
```
An option's `::strict/key` is the empty keyword — from a custom element named
`""`/`"'"` at creation.

## 3. Repos / target

- **Affected users run upstream `orcpub/orcpub`** (sits at the fork base
  `d42e05d`). No June patch there.
- **`codeglaze/orcpub` is the WIP target** — build the fix here. Its `develop`
  has the June `feature/fix-orcbrew-errors` patch (see §4), which you must
  integrate with, not duplicate.
- Do NOT put this on `claude/fix-brave-export-bug-2Tt7j` — that branch is an
  unrelated export fix. Use a fresh branch off `codeglaze/develop`.

## 4. Prior art you MUST reconcile with (the June patch, in codeglaze/develop)

The June error-correction machinery (`orcbrew_validation.cljs`) **fills field
values but never re-derives keys.** This is the central oversight:

- Fill templates (`orcbrew_validation.cljs:119–162`) only fill values — a blank
  `:name` becomes `"[Missing Name]"`.
- `fill-missing-in-content-group` re-stores the filled item under the **existing
  map key** (`~:294–298`), which is already `:` for a blank/`"'"` name.
- `name-to-kw` is **never called** in orcbrew_validation — keys are never
  regenerated after a name is filled.
- `common.cljc:222 feature-name` is **display-only** (`"[Unnamed feature]"`); it
  does not touch key generation.

So even the machine built to repair bad data leaves the corrupt `:` key intact,
and the character-load path never runs that machine at all.

Alignment requirements when you build the fix on codeglaze:
- Reuse the `"[Unnamed feature]"` / `feature-name` convention — don't invent a
  parallel "unnamed" scheme.
- Extend the existing fill machinery to add the missing **re-key** step, rather
  than bolting on a separate repairer.

## 5. The durable fix (design)

### A. Prevent
1. `name-to-kw-aux` (`common.cljc:8`): never emit `:`. When the reduced string
   is blank, produce a stable, non-colliding key, e.g.
   `(keyword ns (str "unnamed-" (hash name)))`. Must stay pure (it's memoized)
   and work in both `:clj` and `:cljs`.
2. Make the fill/error-correction **re-derive `:key` (and the map key) from the
   name after filling** — the step currently missing (§4). A blank item should
   come out keyed from its dummy name, not `:`.
3. (Optional, stronger) sanitize/reject unusable names at the homebrew builder
   **save** path.

### B. Self-heal on load
The already-corrupt data (server characters, localStorage) must be healed on
read, not crash:
- **Character read (cljs-ajax):** register a hardened EDN response format /
  reader that repairs bare-colon tokens before/at parse instead of throwing;
  on repair, **re-save the cleaned character** so it's permanently fixed and
  the user never sees it again.
- **localStorage `get-local-storage-item` (`db.cljs:247`):** it currently
  `.removeItem`s unreadable data — i.e. **silently deletes the user's
  homebrew.** Change to repair-in-place with the same scanner.

Use the SAME string-aware bare-colon scanner in all repair sites (§7 has the
tested implementation).

## 6. Key files

- `src/cljc/orcpub/common.cljc:8` — `name-to-kw-aux` (prevention)
- `src/cljc/orcpub/common.cljc:222` — `feature-name` (reuse convention)
- `src/cljs/orcpub/dnd/e5/orcbrew_validation.cljs:119–162, 290–331` — fill
  machinery (add re-key)
- `src/cljs/orcpub/dnd/e5/db.cljs:247` — `get-local-storage-item` (self-heal +
  stop the destructive removeItem)
- character read path: cljs-ajax response handling in
  `src/cljs/orcpub/dnd/e5/events.cljs` (search the char-load `:http`/on-success)

## 7. Assets already built (recreate these; they are the anti-ignorance kit)

### 7a. The bare-colon EDN scanner (tested: 10 unit cases + real character)
```js
function fixEdn(s){const D=new Set([" ","\t","\n","\r","\f",",","{","}","[","]","(",")",'"',";"]);
  let o="",i=0,c=0;while(i<s.length){const ch=s[i];
    if(ch==='"'){o+=ch;i++;while(i<s.length){const d=s[i];o+=d;i++;if(d==="\\"){if(i<s.length){o+=s[i];i++;}}else if(d==='"')break;}continue;}
    if(ch===":"){let j=i+1;while(j<s.length&&!D.has(s[j]))j++;const t=s.slice(i,j);if(t===":"){c++;o+=":orphaned-name-"+c;}else o+=t;i=j;continue;}
    o+=ch;i++;}return {text:o,count:c};}
```
Clojure equivalent for the real fix: walk the parsed structure OR sanitize the
raw string before read-string; the string-scan above is the reference behavior
(preserves colons inside "strings", leaves `:foo`/`::x`/`#:ns{}` untouched).

### 7b. The e2e harness — THE key asset
The sandbox **browser cannot reach external HTTPS** (verified: example.com and
DMV both reset in Chromium, though `curl` reaches them — browser egress is
blocked; localhost works). Solution: **node is the tunnel.** node's fetch works
through the proxy with `NODE_USE_ENV_PROXY=1 NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt`.
Run the real app in Chromium against localhost; Playwright `page.route`
intercepts every request and either (a) serves the corrupt character, or (b)
bridges the real asset via node fetch.

Chromium is at `/opt/pw-browsers/chromium-1194/chrome-linux/chrome`; load
Playwright via `createRequire('/opt/node22/lib/node_modules/')`.

Harness skeleton (baseline vs shim), proven working:
```js
// launch chromium (args:['--no-sandbox']); page.route('**/*', bridge):
//   char API  -> route.fulfill corrupt EDN
//   ad/analytics -> route.abort
//   else -> node fetch(url) through proxy, route.fulfill real bytes
// navigate to https://www.dungeonmastersvault.com/pages/dnd/5e/characters/17592342813688?frame=true
// capture page.on('pageerror'); compare with/without the fix injected via addInitScript
```
Run with: `NODE_USE_ENV_PROXY=1 NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node harness.mjs`

**Proven results with this harness:**
- Baseline: character fails to load (pageerror `mk`, blank "New Character").
- It caught a real bug: the app reads `xhr.response`, NOT `xhr.responseText`.
- With the corrected repair (hook `.response`): crash gone, real character
  ("Dymethrion … STR 16 …") renders. THIS is how you validate your fix.

### 7c. Emergency console scripts (already delivered to the user; FYI)
- Option A (shim, keep character): overrides `XMLHttpRequest.prototype`
  **`.response` AND `.responseText`** getters, runs `fixEdn` on char responses.
  Must be installed BEFORE the character is fetched (paste on the char-list
  page, then click the character).
- Option B (delete): `DELETE /dnd/5e/characters/:id` with
  `Authorization: Token <jwt>`. Token lives in **`localStorage["user"]`** (EDN,
  extract `:token "…"`), NOT a cookie. buddy `jws` backend → `Token` scheme.
- Endpoints: `GET|POST /dnd/5e/characters(/:id)` (POST upserts by `:db/id`, is
  fail-safe: bad shape → 400, no change).

## 8. Verified vs OPEN

VERIFIED: root cause; corrupt token location in the real character; June patch
behavior (fills values, never re-keys); the app reads `.response`; the corrected
shim eliminates the crash and renders the character (full e2e).

OPEN: the durable code fix is not written. The permanent server-side re-save of
the specific character was not executed (no auth token available to this
sandbox). The exact cljs-ajax insertion point for a hardened EDN reader needs to
be located in code (start from the char-load `:http` on-success in events.cljs).

## 9. First steps for you

1. `git fetch origin develop && git checkout -B claude/<name> origin/develop`
   (codeglaze).
2. Rebuild the e2e harness (§7b) — confirm you can reproduce the baseline crash
   and the corrected-shim pass. This is your regression oracle.
3. Implement §5A (name-to-kw + fill re-key, aligned with `feature-name`).
4. Implement §5B (hardened char read + self-heal re-save; localStorage
   repair-in-place instead of removeItem).
5. Validate each with the harness. Add cljs tests mirroring the REPL cases.
