# Support-session ledger — September 2026

Where the work from the September support sessions actually lives, what is proven, and what is
still open. **This is a record, not a plan** — it exists so none of it evaporates, and so the next
person does not have to reconstruct it from chat.

Two user reports drove all of it: a self-hoster who could not start the server on Windows, and the
same person finding the ability-score arrows missing in the character builder.

---

## Shipped

| what | where | state |
|---|---|---|
| Windows port detection (`common.sh`, `start.sh`, `stop.sh`) + `windows-latest` CI | `fix/windows-port-detection` @ `8fbf98f5` | green, 4 files, +406/−11, **unmerged** |
| Character-load rescue console (v6) | `agents/develop` and `claude/fix-brave-export-bug-2Tt7j` — byte-identical | landed on both |
| `docs/kb/icon-font-failure.md` | `agents/develop` @ `2e50537d` | done |
| `docs/kb/character-rescue-console.md` | `agents/develop` | done |
| Field notes: Java 25 + netty `sun.misc.Unsafe`; placeholder `SIGNATURE` | `JAVA-COMPATIBILITY.md`, `kb/env-and-auth.md` | done |

## Built, in no branch

Recoverable from the pre-trim tip `87d9bba3`, otherwise only in session scratch:

- **`orcpub-start.sh`** (327 lines) — one-command launcher: finds the repo, checks Java/lein, frees
  8890/7888, checks Windows reserved ranges, starts Datomic if down, runs the server headless, and
  prints a redacted report on failure. **Deliberately not merged** — once
  `fix/windows-port-detection` lands, `check_port_available` and `explain_bind_failure` do the same
  job through the project's own entry point. Until then it is the only thing that works against an
  unfixed checkout, and it is what the reporter is running.
- **`orcpub-port-doctor.sh`** (257 lines) — read-only diagnostic. Superseded by the above; kept for
  reference.

## Open — needs a decision, not just work

☐ **Merge `fix/windows-port-detection`.** Finished and green; the only question is when.

☐ **Untangle `claude/fix-brave-export-bug-2Tt7j`.** It carries two unrelated things: three export
commits (`25c4a371`, `1002cc5c`, `3a1f5bfb` — what the branch is named for) and seven
character-load rescue commits (`daf32bb9`…`f1bda173`). One branch, two concerns, one PR if it is
ever opened.

☐ **Does `orcpub-start.sh` have a home?** It dies with the session otherwise. Options: merge it,
keep handing it out until the Windows fix ships, or let it go once that fix is in.

☐ **First-run path.** `start.sh server` needs Datomic already running — documented
([GETTING-STARTED.md](../GETTING-STARTED.md) steps 4–7) but it catches every newcomer. `init-db` is
safe to re-run and `create-user!` throws a recognisable "already exists", so a `--fresh` one-shot is
feasible if wanted.

## Open — small, self-contained

☐ `actions/checkout@v4` → `v5` — Node 20 deprecation warns on all five workflows
☐ `<meta name="darkreader-lock">` in `index.clj` — one line
☐ Missing-icon-font detection + a visible fallback — see [icon-font-failure.md](icon-font-failure.md)
☐ `title` + `aria-label` on icon-only controls, starting with the two ability-score arrows
☐ SVG for interactive icons — a project with a design call in it, not a loose end

## Unverified — do not report these as done

- **`start_all` Ctrl+C.** `start_server`/`start_all` now `return $rc` explicitly, and `start_all`
  runs under `trap cleanup_on_exit INT TERM`. Reasoned about, never executed — no Leiningen on the
  runner.
- **macOS.** Untested. It takes the `lsof` branch so it never reaches the Windows code, but BSD
  `netstat` does not understand `-tln` either — the same bug class is merely masked by `lsof` being
  present. No macOS job in the workflow.
- **The arrows themselves.** Root cause is not confirmed. Everything points at Dark Reader
  (computed `#e89b00` against a source `#f0a100`), but the one test that settles it — toggle the
  extension off for `localhost:8890`, hard refresh — has not been run.
- **`darkreader-lock`.** An open upstream issue reports the tag broke in v4.9.86. Strong
  mitigation, not a guarantee.

## Corrections worth keeping

Things believed and then disproved during these sessions, recorded so they are not re-learned:

- `document.fonts.check()` returns **`true`** for a font family that does not exist. It reads like a
  pass and is not one.
- The glyph-width comparison — the technique most articles recommend for detecting a missing icon
  font — **did not discriminate** when measured (60.22px either way).
- Windows `netstat` prints `LISTENING`; Microsoft's own reference documents the state as `LISTEN`.
  Neither survives a non-English Windows, which is why the code matches the local-address column
  instead.
- `start.sh datomic` reports failure ~30s **after a successful start** on Git Bash, because its
  readiness check used the broken port test.
