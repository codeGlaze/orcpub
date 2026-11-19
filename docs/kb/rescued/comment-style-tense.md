> **Rescued verbatim** from `origin/docs/comment-style-tense` @ `39546b46`: the lines it added to `docs/kb/code-comment-style.md` (2026-11-19).
> Preserved before branch deletion; **not verified against the current tree**. Its
> status lines, `file:line` refs and links describe that branch, not `agents/develop`.
> Provenance and triage: [README.md](README.md).

---

# Comments: past tense goes, present tense stays (rescued)

### Past tense goes, present tense stays

Stripping narration must not strip a live warning. The line between a journal entry and a
warning is **tense**, not topic — both can sit in the same comment, describing the same bug.

```clojure
;; drop — past tense, the bug is fixed and git holds the story:
;; The call parens were missing, so this returned the atom's map, which is truthy even when
;; empty. It always answered "yes". clj-kondo flagged it as two "Unused value" warnings.

;; keep — present tense, still true, and the reason that bug survived unnoticed:
;; NOTE unused: nothing calls this. Its sibling too-many-attempts-for-username? is wired
;; into the login handler in routes.clj; this one is not, so a fault in it fails open
;; silently and no test of the wiring would catch it.
```

Ask whether the sentence describes something a reader will still hit. *"This was broken
because X"* is history — delete it. *"This is unwired and fails open"* is an operating
hazard, and warning about it is exactly what a comment is for.

The same test applies to measurements and results. *"Cut the file from 2679 KB to 1313 KB"*
belongs in the commit message; *"a widget on no page can neither render nor be filled"* is
the constraint that stops someone reintroducing the problem.

- Past tense out, present tense in — deleting narration must not delete a live warning.
