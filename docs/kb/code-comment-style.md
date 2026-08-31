# Code comment style

House style for comments across the repo. The rule of thumb: **comments are for clarity of
the code — a technical manual for how things work, not a journal.** Describe how the code
functions and the non-obvious constraints that keep it working; leave out backstory,
justification, and narration.

## Docstrings carry the *what*

Use Clojure's native docstring — it's the documenting mechanism, it's extractable
(`clojure.repl/doc`, editor hover, cljdoc), and it belongs on non-obvious public `defn`/
`def`/`ns` forms. Keep it to a concise prose sentence or three: what the thing does and any
edge-case behavior a caller must know.

```clojure
(defn expand-class
  "On import, rebuilds the full equipment list from a class stored as base-class-plus-changes,
   and records which class it was based on. If the base class isn't loaded, returns the class
   unchanged."
  [class] …)
```

**Skip the docstring on trivial/obvious functions.** No ceremony where the code already reads
plainly.

```clojure
(defn- by-key [coll k] (first (filter #(= k (:key %)) coll)))
```

Do **not** hand-write Google Closure Compiler JSDoc (`@param {type}` / `@return`) in CLJS
source — it's compiler-level type machinery, not read by Clojure tooling, and it bloats a
function to several times its size. Docstrings are the ClojureScript equivalent, minus the
ceremony.

## Inline `;;` comments carry the *why* — but only when it's a constraint

Inline comments annotate a specific line where a docstring can't reach. Keep a *why* **only
when it stops someone breaking the code** — an ordering that matters, an edge case that
bites, a spec that will reject the data. Drop a *why* when it's history or self-justification.

```clojure
;; keep — this is an operating constraint:
;; Expand back to the full equipment list here, before import validation runs — a class
;; stored as base-plus-changes won't pass validation, which expects a full equipment list.

;; drop — backstory / defending the choice:
;; I chose .bg-warning here instead of a custom box because it matches the other banners …
```

- Plain sentences. Name the **real symbol / class / file** the reader should look at
  (`.bg-warning` in `styles/core.clj`, `expand-class`), and lean on grep + go-to-definition.
- **No decorative markers or coined jargon.** No `!`/symbol prefixes, no invented
  abbreviations (a reader — human or agent — shouldn't have to decode a private vocabulary).
- **No links to KB docs from code comments.** KB docs live on a different branch and get
  reorganized; a code comment pointing at one is a dead link. Point at code instead.

## Relating scattered code

When related code can't sit in one namespace, the strongest tools — in order — are:

1. **A shared naming convention** so the pieces grep themselves (the message events are all
   `*-message`; starting-equipment code shares `starting-equipment` in its names).
2. **Naming the real symbol** in a comment and relying on grep / find-references.
3. **A namespace**, when the pieces *can* be lifted (prefer this over a tag when possible).

Standard codetags — `TODO`, `FIXME`, `HACK`, `NOTE`, `BUG` — are fine for those categories
(editors highlight and list them). A **custom grep tag** is a last resort, justified only for
a genuine cross-cutting concern that can't be a namespace or a shared name; if you add one,
make it one plain word and document it in one place. It carries an ongoing consistency cost,
so don't reach for it when a name or a namespace would do.

## Quick checklist

- Docstring on non-obvious public forms; skip trivial ones. Concise prose, no JSDoc.
- Inline *why* only when it's a constraint that keeps the code working.
- Plain language; name real symbols/files; no markers, no coined abbreviations.
- No KB-doc links in code.
- Relate scattered code by names and namespaces first; codetags for their categories; a
  custom tag only as a documented last resort.
