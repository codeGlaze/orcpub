# Machine tokens are not prose: locale-dependent failures

**A support session, September 2026.** A self-hoster on Spanish Windows could not see the
ability-score reorder arrows. The cause was not in the character builder, not in CSS, and not in
their browser: **every `/assets/*` webjar request was returning HTTP 200 with a zero-byte body**,
on their machine and no one else's.

One root error, in two places in this codebase: **a linguistic operation applied to a
machine-readable token**, letting the operator's regional settings reach into a code path that must
be deterministic.

| | the linguistic operation | applied to | breaks on |
|---|---|---|---|
| `pedestal.clj` | parse a date using local month/day names | an HTTP header, English by RFC 7231 | any non-English locale |
| `config.clj` | lowercase using local casing rules | an ASCII config token | Turkish, Azerbaijani |

Fixed in `hotfix/locale-safety`, cut from `upstream/develop` because **upstream carried both bugs
verbatim**.

---

## 1. The ETag crash

Two defects, and it took both to produce a silent failure.

**Defect one — the formatter had no locale.**

```clojure
(def rfc822-formatter
  (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z"))   ; no Locale
```

`ofPattern` without a `Locale` parses using the JVM default, which on Windows follows the user's
regional settings. HTTP dates are always English (RFC 7231 IMF-fixdate), so on a Spanish machine:

```
java.time.format.DateTimeParseException:
  Text 'Mon, 06 Jul 2020 14:41:47 +0000' could not be parsed at index 0
```

Index 0 is `"Mon"` — Spanish days are `lun/mar/mié`.

**Defect two — the handler discarded the response.**

```clojure
(catch Throwable t (log/error :msg "ETag interceptor error" :exception t))
```

The `catch` returns the value of `log/error`, **not `context`**. Pedestal then has no response to
write, and Jetty emits a bare `200` with no body and no headers.

This is why it was invisible. The exception *was* logged — but the response was already gone, and
the browser saw a successful empty file rather than an error.

### Why only `/assets/*`

`::http/resource-path "/public"` places Pedestal's resource interceptor **ahead of the router** in
the chain. It serves `/css/*` on `:enter` and terminates, so the ETag interceptor never runs for it.
Only `/assets/*` falls through to the router, reaches the handler, and unwinds through ETag.

That asymmetry is the whole reason this read as a Font Awesome problem: the app's own CSS loaded
fine (442 rules) while every webjar asset was blank.

### The fix

```clojure
(def rfc822-formatter
  (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z" Locale/ENGLISH))
```
```clojure
(catch Throwable t
  (log/error :msg "ETag interceptor error" :exception t)
  context)
```

Either alone is insufficient: fixing only the formatter leaves the next exception silently blanking
responses; fixing only the `catch` leaves a working but ETag-less response on every non-English
machine.

Verified under `tr_TR`, `es_ES`, `ja_JP` and `en_US` — all return 58,935 bytes with
`Content-Type: text/css`, and the ETag value is **unchanged** from what English-locale servers
already produce, so no caches are invalidated.

`DateTimeFormatter/RFC_1123_DATE_TIME` is a cleaner option still: it is locale-independent by
construction, parses the raw `GMT` form directly, and would let the `(s/replace date #"GMT"
"+0000")` string surgery go too. It yields the identical epoch value. Not taken in the hotfix
because the minimal change is what the reporter tested end to end; worth doing as a follow-up.

## 2. The Turkish-I problem in config

```clojure
(str/lower-case policy)   ; get-csp-policy
```

Clojure's `str/lower-case` calls `.toLowerCase()` with no locale. Measured:

```
locale=tr_TR   "STRICT" -> "strıct"   matches "strict"? false
locale=en_US   "STRICT" -> "strict"   matches "strict"? true
```

`ı` is U+0131 LATIN SMALL LETTER DOTLESS I. So `CSP_POLICY=STRICT` on a Turkish machine matches
nothing and **falls through to the permissive policy** — a silent security downgrade, in the one
direction you would least want it to fail.

**This is not a bug in Java or Unicode.** Turkish genuinely has two i's — dotted `İ/i` and dotless
`I/ı` — and lowercasing `I` to `ı` is correct Turkish. Unicode encodes it deliberately; Java's
JavaDoc warns about it explicitly, naming *"programming language identifiers, protocol keys, and
HTML tags"* and giving `"TITLE".toLowerCase()` → `"tıtle"` as its example. Both standards are
behaving correctly. The error is ours, for case-folding a protocol keyword as though it were prose.

### The fix

```clojure
(.toLowerCase ^String policy Locale/ROOT)              ; get-csp-policy: keeps the normalised return
(.equalsIgnoreCase "true" (or (env :dev-mode) ""))     ; dev-mode?: a pure comparison
```

`equalsIgnoreCase` compares per character rather than by locale casing rules, so it is immune.
Two notes:

- **Argument order matters.** `(.equalsIgnoreCase "true" x)` returns false on nil;
  `(.equalsIgnoreCase x "true")` throws NPE. Put the literal first.
- `get-csp-policy` **returns** a normalised string that three callers compare against, so it keeps
  normalising — with the locale pinned — rather than switching to `equalsIgnoreCase` and touching
  every call site.

`PERMISSIVE` has the same two I's but fails safe, since the fallthrough is permissive anyway.
`DEV_MODE` was never affected — no `I` in `TRUE`.

## 3. The rule

> **Pin the locale, or use a locale-independent API, whenever you parse or format a
> machine-readable string.** Protocol dates, config keys, identifiers, header values, file
> extensions — none of them are written in the operator's language.

Reach for `Locale/ROOT` or `Locale/ENGLISH` explicitly, or an API with no locale at all
(`equalsIgnoreCase`, `DateTimeFormatter/RFC_1123_DATE_TIME`). In Clojure this means being wary of
`str/lower-case` and `str/upper-case`, which are locale-sensitive by default and give no hint of it
at the call site.

A survey of `src/clj` and `src/cljc` found four case-folding sites; the two in `config.clj` were the
only ones operating on machine tokens. Worth re-running when adding new ones:

```
grep -rn "str/lower-case\|str/upper-case\|\.toLowerCase\|\.toUpperCase" src/
```

## 4. How this was found, and how long it took

Worth recording, because the shape of the search was the expensive part.

The symptom pointed at Font Awesome, so Font Awesome is where the effort went. Five successive
hypotheses were eliminated — a browser extension, strict MIME checking, a poisoned HTTP cache, CSP,
and the fork-vs-upstream difference — each consistent with every observation available at the time.
Three faithful local reproductions of the server route all returned `text/css` correctly, because
they ran in an English-locale container.

What broke it open was asking a different question. Every probe until then had asked *why the
stylesheet was not applying*. One probe asked **what actually arrived**:

```js
fetch(url,{cache:'reload'}).then(r=>r.text()).then(t=>console.log('len',t.length))
```

`len 0`. There was no styling problem; there was no file.

Two transferable lessons:

- **Confirm delivery before diagnosing presentation.** A 200 with an empty body is
  indistinguishable in the CSSOM from a stylesheet that was fetched and refused — both give a real
  `<link>`, `disabled=false`, `cssRules.length === 0`.
- **A reproduction that passes has only told you about the environment it ran in.** Three green
  runs said "the code is fine" when they meant "the code is fine *in English*". When a bug is
  machine-specific and you cannot reproduce it, enumerate what differs about the machine — locale,
  filesystem, time zone, line endings — rather than re-testing the code.

## Provenance

Diagnosed with a self-hoster on Spanish Windows, September 2026. Reproduced locally by setting
`-Duser.language=es -Duser.country=ES`, which returned `status=200 ctype=nil body=0` against the
real webjar — matching the reporter's console output exactly. The fix was verified across four
locales and then confirmed by the reporter on their own machine before being committed.

See also: [icon-font-failure.md](icon-font-failure.md) for why the symptom was so hard to read, and
[pedestal-csp-history.md](pedestal-csp-history.md) for the CSP machinery touched in §2.
