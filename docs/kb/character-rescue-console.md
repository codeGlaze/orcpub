# Field rescue: recovering a character the deployed build cannot read

**What this is.** A browser-console tool (`scripts/recovery/rescue-console.js`) that repairs a saved
character the site cannot load, and the method used to build and verify it. Reach for this when the
shipped defences are not in the build the user is actually running and they need their character
back today.

The failure class itself — how an empty name produces an unreadable `:`, and where the two shipped
defences live — is [empty-keyword-corruption.md](empty-keyword-corruption.md). This doc is the
*field* side: triage, the tool, and how to adapt it. Do not re-derive the root cause here.

---

## 1. When a console tool is the right answer

Both defences are on `develop`. That does not mean they are on the machine the user is sitting in
front of. In September 2026 a user hit this on the public site, and the served bundle carried
**neither** — so the deployed code could not help them and a release was not imminent.

Check before assuming. The fix's own string literals survive advanced compilation, so their absence
in the served bundle is proof:

```js
orcpubRescue.buildInfo()   // looks for ":unnamed-" / "decode-error" in the loaded orcpub.js
```

If that reports the fix is present, this tool is probably unnecessary — the data self-heals on load.
If it is absent, the user is stuck until a deploy, and the tool is the bridge.

## 2. Triage: what the user actually reports

The report will not mention keywords. It looks like this:

> Every time I click on his sheet he totally crashes out the website and I have to reload… even with
> no content loaded I can not get this character sheet to load so I can delete him. I also cannot
> access the parties page at all any more because he was a part of one of my parties.

Three tells, all of which follow from the failure class:

| symptom | why |
|---|---|
| The whole page dies, no error message | the throw escapes a go block; nothing can catch it at the call site |
| A private window with no homebrew behaves the same | the bad value is stored server-side |
| The Parties page breaks too | it loads its member characters and hits the same one |

In the console it appears as a bare **`Uncaught mk`** and nothing else. `mk` is minified
`cljs.core/ExceptionInfo`; the throw is rethrown by
`cljs.core.async.impl.ioc-helpers/run-state-machine-wrapped`. That symptom is worth memorising —
it identifies the whole uncaught-decode family, not just this bug, and it names nothing useful on
its own. `diagnose()` (below) converts it into a real message.

## 3. What the tool does

Paste it into the console on a page that loads; it sweeps the account, finds the broken characters,
downloads a backup of each, stops the crash, and opens the first one. The user then clicks
**EDIT** then **SAVE**, which is what makes the repair permanent.

Four mechanisms, each of which had to survive contact with real data.

### Reading data that is, by definition, unreadable

The script never calls an EDN reader — the whole problem is that this data breaks readers. It scans
text. But the scan must respect nesting, which is where the first version went wrong.

A character summary embeds its classes as nested maps carrying their own `:db/id`:

```clojure
{:db/id 17592186045420
 ::char5e/classes [{:db/id 17592186045424 ::char5e/class-name "Wizard" …}]
 ::char5e/character-name "Aelar"}
```

A flat `\{[^{}]*:db/id (\d+)[^{}]*\}` only matches maps with **no** nested braces, so it matched the
class map every time and returned component ids that are not characters. Every lookup came back
HTTP 400. On one account it produced 115 "characters" apparently named "Warlock", "Cleric",
"Sorcerer: Favoured Soul" — class names, read out of the wrong maps. The parties sweep had the
mirror bug: a flat scan also picked up each party's own id.

`mapChunks` / `shallow` track brace depth and string literals, so each map's own fields are read and
nested ones ignored.

> Any text scan over this data must be depth-aware. It is the easiest thing to get wrong and it
> fails misleadingly — as a wall of 400s, not as a parse error.

### Stopping the crash

`arm()` redefines the `response` **and** `responseText` getters on `XMLHttpRequest.prototype` and
repairs the text on the way out, so the reader never sees the bad token. Both matter: the app reads
`.response`, and an earlier version hooking only `.responseText` did nothing at all.

**Scope the heal to character URLs.** The repair rewrites a colon followed by a delimiter into
`:unnamed-N`. Applied to every response, that is not a character repair — in JSON a colon followed
by a delimiter describes every key, so `{"username":"bob"}` becomes `{"username":unnamed-1"bob"}`.
Transit survives by luck, because its colons only ever appear inside strings, and the save path is
transit — which is exactly why an unscoped shim can pass a full end-to-end save test and still be
wrong. `arm()` wraps `.open()` to record the URL and heals only `/dnd/5e/characters/<id>`.

It is per-tab and per-document. **A reload undoes it**, which is why the tool navigates in-app
(`history.pushState` + a `popstate` event) instead of asking the user to click something that might
reload. A real click on a character row was verified to keep the document alive and the patch armed.

### Making the repair permanent

The shim only fixes what is in flight. The durable repair uses the app's own save path: once the
character loads, **EDIT → SAVE** in the builder posts the healed entity back and the stored copy
comes back clean. No hand-rolled transit encoding, no database surgery.

Verified by watching the POST — it carries `~:unnamed-1` where the empty keyword was — and then by
loading the character on the unpatched production bundle with no script running at all.

### Saying what actually went wrong

`diagnose(<id>)` is for when the sweep finds nothing but a page still dies. It scans the raw data for
anything a reader rejects (empty keywords, keywords with nothing after the `/`, digit-leading names,
truncation, unterminated strings) and quotes the offending text in context. Then it reproduces the
crash **on purpose** behind a `window.onerror` listener, and because the thrown object is an
`ExceptionInfo` with `.message` and `.data`, prints what was really thrown:

```
Invalid keyword: :. (type, reader-exception, ex-kind, reader-error)
   at Error: Invalid keyword: :. / at new mk (orcpub.js:1090:26) / at ok (…)
```

`report()` copies the lot for a bug report. `ex-data` is a compiled cljs map, so `JSON.stringify`
emits minified internals — only the readable keyword names are kept.

## 4. Gotchas that cost real time

- **Scope any response rewriting.** An unscoped healer corrupts JSON, and the flow you are
  most likely to test (the save) is transit, so it will not catch you.
- **A reload kills the patch.** Say so in every instruction; navigate in-app.
- **Saving is gated.** A character missing ability scores cannot be saved, so SAVE silently does
  nothing and the repair does not stick. Fully-built characters are fine; the tool warns.
- **HTTP 400 with an empty body means "no character with that id"**, not "healthy". An early version
  reported it as "This character decodes fine" and sent a user chasing the wrong thing.
- **Never make anyone retype a 14-digit id.** One user mistyped it twice (`17592852854370`, then
  `17592372854370`). Sweep and find it instead.
- **Version the tool.** Two user logs turned out to be from a build carrying the parsing bug above,
  so neither meant what it appeared to. `VERSION` prints on load and is stamped into every report.
- **Corruption travels through clones.** The affected account held `Dimitry` plus two
  `Dimitry (fixed)` copies — the user's own attempts to escape by cloning, each carrying the empty
  keyword along.

## 5. Verifying a tool like this

`scripts/recovery/e2e-harness.mjs` is the older, narrower harness for this bug. The general
technique needs no deploy and no staging environment:

1. **Fetch the bundle the live site actually serves** and grep it for the fix's string literals.
   Presence or absence is a fact, not an assumption.
2. **Serve that real bundle locally** via Playwright request interception against a real local
   server, intercepting only the one response you need to corrupt.
3. **Establish the baseline first.** Reproduce the user's exact error before claiming anything fixes
   it — here, `Error: mk` with the character never rendering.
4. **Prove the end state without the tool.** The acceptance test is that the character loads on the
   *unpatched* bundle with no script running, not that the script makes it work.

Running the backend locally needs `deps.edn`, which is generated from `project.clj` and gitignored.
Generate it by reading **all** top-level forms and filtering for `defproject` — `project.clj` opens
with a `require`, so reading only the first form fails.

## 6. Adapting it for a different corruption

Most of the tool is incident-agnostic:

- **`heal(text)`** — swap in whatever repair the new corruption needs. Sweeping, backup, the XHR
  shim and the EDIT→SAVE flow are unchanged.
- **`badKeywords` / `structure`** in `diagnose` — add checks for the new shape.
- **`R.list` / `R.partyMemberIds`** — reuse as-is for characters; keep the depth-aware scan wherever
  you point them.
- **The error-capture half of `diagnose` is fully generic.** Any uncaught `ExceptionInfo` becomes a
  readable message — worth keeping for the next opaque `Uncaught <symbol>` report, whatever causes
  it.

Safety properties to preserve if you modify it: a backup downloads before any write, nothing is
deleted without one, `remove()` is opt-in, and the healer is string-aware so colons inside notes,
quoted text and URLs are untouched (verified against a false-positive suite).

## Provenance

| file | from |
|---|---|
| `scripts/recovery/rescue-console.js` | `claude/fix-brave-export-bug-2Tt7j` `e0080b95` |
| `scripts/recovery/e2e-harness.mjs` | `claude/fix-single-colon-keyword` `bab40288` |

Everything above was verified against the bundle the live site served on 2026-09-13, driving a real
browser against a real local server, and confirmed on a real affected account (three corrupt
characters found, backed up and opened).

`scripts/recovery/emergency-console-fix.js` (`claude/fix-single-colon-keyword` `bab40288`) was the
first-generation field patch — two hand-run options, repair-in-flight or delete. `rescue-console.js`
does strictly more, so the predecessor is left on its branch rather than copied here; two similar
rescue scripts in one directory is a trap for whoever grabs one in a hurry.

It was not, however, strictly worse. It scoped its XHR heal to character URLs and `rescue-console.js`
did not, which is a correctness bug the newer script carried until v6 — read the old one before
assuming the new one supersedes it on every axis. Also taken from it: walking the prototype chain for
the getter descriptor, `confirm()` before a delete, and a cookie fallback for the auth token.
