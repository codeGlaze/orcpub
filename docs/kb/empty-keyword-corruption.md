# "A single colon is not a valid keyword" — the empty-keyword failure class

**Fixed. This is the record of what the fix defends against, and where it lives**, because the
reasoning is currently only in source comments — findable if you already know which file to open,
invisible if you do not. References are against `integration` at `36766010`, checked 2026-09-12.

## The failure

A user names a piece of homebrew `""` or `"'"`. `name-to-kw` strips apostrophes, then non-word
characters, and what is left is the empty string. `(keyword "")` prints as a bare `:` — which is not
readable EDN.

That bare `:` is then written into saved data. On the next load the **whole** blob fails to read, not
just the bad key: one corrupt token anywhere in a character's EDN takes the entire character with it,
and the user can no longer view, edit, or delete it.

**Why it killed the page rather than surfacing an error:** cljs-http decodes an `application/edn`
response body with `read-string` *inside* `async/map`'s go-loop. The throw happens upstream of the
caller's `(<! ...)`, so a `try` around the take can never catch it. There is no seam at the call
site — the fix had to go where the throw happens.

## The two defences, and where they are

| | what | where |
|---|---|---|
| **Prevention** | A name that reduces to `""` yields `unnamed-<hash>` instead of the empty keyword. Starts with a letter, so it also passes `keyword-starts-with-letter?` and stays visible to the keyword-trap machinery | `common.cljc:65`, inside `name-to-kw-aux` |
| **Self-heal** | `sanitize-edn-colons` rewrites every bare-colon token to a unique `:unnamed-N` so already-corrupt data reads instead of crashing. String-aware — a `:` inside a quoted string is left alone — and returns `{:text … :count …}` so a caller can tell "healed" from "was already clean" and avoid rewriting good data | `common.cljc:16` |
| **Choke point** | `orcpub.dnd.e5.http-safe` is a drop-in replacement for the cljs-http client fns whose only difference is a resilient EDN decoder: it swaps `read-string` for `safe-edn-decode`, which heals via `sanitize-edn-colons` and degrades to a marker instead of throwing | `http_safe.cljs` — `safe-edn-decode:38`, `wrap-safe-edn-response:53` |

Route an HTTP loader through `http_safe` and the whole uncaught-decode class is closed at one point.
**A new loader that calls `cljs-http.client` directly reopens it** — that is the thing to watch for
in review.

## If someone is hitting this right now

The defences are on `develop`; that is not the same as being in the build the user is running.
[character-rescue-console.md](character-rescue-console.md) covers the field side — how to check
whether the served bundle actually carries the fix, the `Uncaught mk` triage tell, and a console
tool that repairs an already-corrupt character through the app’s own save path.

## Related but different

[keyword-trap-name-repair.md](keyword-trap-name-repair.md) covers names that lead with a *number or
symbol* ("9 Lives", "@@@"), which derive a keyword that is valid EDN but fails
`keyword-starts-with-letter?` and gets quarantined for repair. Same family, different failure: that
one produces a readable key the app rejects, this one produces an unreadable token that takes the
whole document down.

## Provenance

Root-caused on `claude/fix-single-colon-keyword` (`bab40288`), verified in a REPL (inputs `""` and
`"'"` both yield `:`; the cljs reader throws exactly this message, the JVM reader says "Invalid
token: :" — use a cljs context to reproduce the reported string) and against a real affected
character. Both defences have since shipped. The branch's 196-line handoff was dropped as
superseded; this is the part worth keeping.

## Revisions

- **2026-09-12.** [rescued/README.md](rescued/README.md) first recorded this as "nothing durable was
  left to lift", on the grounds that `http_safe.cljs`'s ns docstring already explained the
  detonation path better than the handoff did. That was true and still is — but a docstring is only
  reachable by someone already in the file, and nothing pointed there from the KB. The knowledge was
  not lost, it was unfindable. Doc written to close that gap; the source comments remain the
  authority on mechanism.
