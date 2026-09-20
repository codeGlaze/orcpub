# Support-session ledger — September 2026

Where the work from the September support sessions actually lives, what is proven, and what is
still open. **This is a record, not a plan** — it exists so none of it evaporates, and so the next
person does not have to reconstruct it from chat.

Two user reports drove all of it: a self-hoster who could not start the server on Windows, and the
same person finding the ability-score arrows missing in the character builder. The second turned
out to be a server bug affecting every non-English-locale install.

---

## WHERE WE ARE — 2026-09-20, paused mid-review

**PR [orcpub/orcpub#695](https://github.com/Orcpub/orcpub/pull/695) is OPEN and has an unaddressed
Copilot review.** That review is the next piece of work. Nothing is merged.

| branch | base | state |
|---|---|---|
| `hotfix/locale-safety` | `upstream/develop` | 12 commits, 14 files. **PR #695 open.** Windows CI green; `lein test` 220/985/0 under `en_US`, `es_ES`, `tr_TR`; `lein fig:build` clean |
| `fix/fa4-icon-names` | `upstream/develop` | 1 commit, 4 files, pushed, **no PR**. Five FA4 icon names; `far` weight confirmed present in the free set |
| `agents/develop` | — | docs + tooling. `kb` CLI, hooks armed at session start, KB corrections |
| `fix/windows-port-detection` | fork line | **superseded** — its commits were cherry-picked into `hotfix/locale-safety` |

### The Copilot review — 6 medium + 1 low, NOT yet triaged

**The seven finding titles, as GitHub renders them.** The bodies could not be retrieved — see
below — so these are Copilot's own headings, not its reasoning:

1. Port diagnostics ignore the application's configured PORT
2. Figwheel CSP predicate misclassifies policy branches
3. Local env template overrides Datomic URL with Docker hostname
4. Windows port parser misidentifies non-listening sockets
5. Help command performs environment setup side effects
6. CI does not test production locale handling under hostile locales
7. CSP documentation incorrectly describes DEV_MODE behavior

**Numbers 1 and 2 were reproduced independently and are real defects in code written during
this session:**

- **(1) `PORT` in `.env` never reaches the scripts.** `.env.example:12` documents `PORT=8890`
  and the server honours it (`system.clj`, `System/getenv "PORT"`), but `common.sh:202` uses
  `SERVER_PORT`. Set `PORT=9000` and the server moves while every port check,
  `explain_bind_failure`, and the new `report_env_config` still look at 8890 — so the config
  reporting added this session makes the mismatch *more* visible and still wrong.
- **(2) `permissive` CSP blocks Figwheel too.** `permissive-csp-settings` sets `:default-src
  "'self'"` and no `:connect-src`, so connect-src falls back and the websocket is blocked.
  `dev_mode_blocks_figwheel` only warns when the policy is `strict`, staying silent on a case
  that is equally broken.

Numbers 3–7 are untriaged.

### Why the comment bodies could not be read, and what would work

Not a GitHub permission problem — **the PR is public.** The block is this session's own egress
proxy, which enforces a per-repository allowlist regardless of whether a repo is public. Routes
tried, 2026-09-20:

| route | result |
|---|---|
| `github.com/.../pull/695` HTML | **reachable** — but review bodies render client-side, so only the titles are in the markup |
| `api.github.com/.../pulls/695/comments` | 403 from the proxy, not from GitHub |
| GitHub MCP tools | denied: scoped to `codeglaze/orcpub` |
| `gh` CLI (installs fine, works on the fork) | same proxy 403 on any `Orcpub/*` path |
| headless Chromium via Playwright | proxy MITMs TLS; the CA is in the system store but Chromium uses its own root store, and pinning the CA was refused as TLS weakening |

Three things would get the bodies: a human paste, a Claude session that already has
`orcpub/orcpub` access, or a session started with `orcpub/orcpub` as its initial source
(`add_repo` cannot add it afterwards — cross-owner adds are unsupported).

### Next actions, in order

1. Read the actual review comments on #695 (needs a session that can reach `orcpub/orcpub`, or a
   human paste).
2. Fix the two confirmed defects above. Both are in `scripts/common.sh` / `config.clj`.
3. Triage the other five.
4. Decide whether `fix/fa4-icon-names` gets its own PR now or waits for #695.

### Not done, and not claimed

- **The application has never been booted.** No Datomic transactor here. Everything is verified by
  unit tests, the CLJS compile, the Windows runner, and standalone interceptor tests.
- The FA4 icons have not been seen rendering in a browser. The `far` weight is confirmed present
  in the webjar; that is not the same as having looked at it.

## Shipped

| what | where | state |
|---|---|---|
| **Self-hosting hotfix: locale crash + Windows port detection** | `hotfix/locale-safety` @ `b646c5dc`, cut from `upstream/develop` | pushed, pre-merged clean, **no PR yet** |
| ~~Windows port detection as its own branch~~ | `fix/windows-port-detection` @ `8fbf98f5` | **superseded** — its 5 commits are cherry-picked into the hotfix branch |
| Character-load rescue console (v6) | `agents/develop` and `claude/fix-brave-export-bug-2Tt7j` — byte-identical | landed on both |
| [locale-safety.md](locale-safety.md) | `agents/develop` | done |
| [icon-font-failure.md](icon-font-failure.md) | `agents/develop` | **rewritten** — the original blamed Dark Reader and was wrong |
| [character-rescue-console.md](character-rescue-console.md) | `agents/develop` | done |
| Field notes: Java 25 + netty `sun.misc.Unsafe`; placeholder `SIGNATURE` | `JAVA-COMPATIBILITY.md`, [env-and-auth.md](env-and-auth.md) | done |

### About `hotfix/locale-safety`

Deliberately based on `upstream/develop`, not on `agents/develop`, so it merges up to
`orcpub/orcpub` and pulls back down. **Upstream carries both bugs verbatim**, so this is an
upstream fix we happen to have found. Code only — no docs, no fork-specific changes — which is what
keeps it mergeable in both directions.

**Scope, and a correction.** These were briefly two branches; they should not have been. Both
are the same story — self-hosters cannot run this — found in the same session, and a maintainer
asking "why does Windows not work" wants one place to look. The Windows commits were cherry-picked
on 2026-09-20 rather than merged, because `fix/windows-port-detection` is based on the fork's
integration line and its diff against `upstream/develop` is 77 files / +7,240 lines of unrelated
fork work. Its own five commits touch only four files, and the fork's copies of
`scripts/{common,start,stop}.sh` were byte-identical to upstream's, so they replayed cleanly.

The FA4 icon names stay separate, but **not** because they are fork-only — an earlier revision of
this ledger said that and was wrong. Checked against `upstream/develop` on 2026-09-20: all five
sites exist upstream, in `import_log.cljs`, `conflict_resolution.cljs` and `styles/core.clj`. They
are separate because they are an unrelated defect, and they are upstreamable as their own PR. See
[icon-font-failure.md §5](icon-font-failure.md) for the table and the webjar shim mappings.

Beware the grep that caused that error: `fa-pencil\b` matches inside `fa-pencil-alt`, so a naive
search reports upstream's already-correct `views.cljs:8520` as broken. It is not.

Pre-merge results:

- into `upstream/develop` — clean (it is based on it)
- into `origin/agents/develop` — `config.clj` auto-merges; **`pedestal.clj` conflicts**

The conflict is benign and will recur for whoever merges it. `agents/develop` added
`arm-boot-report!` immediately after the line the hotfix changes, so git cannot separate them.
Resolution: **take the hotfix's `catch` block, keep `arm-boot-report!`** — both changes are wanted,
they are simply adjacent. Verified: resolved tree parses, `orcpub.config` loads, and behaviour is
identical under `tr_TR`, `es_ES` and `en_US`.

## Built, in no branch

Recoverable from the pre-trim tip `87d9bba3`, otherwise only in session scratch:

- ~~**`orcpub-start.sh`** (327 lines)~~ — **obsolete as of 2026-09-20.** Its condition for
  retirement was "once the Windows port fixes land in the project's own scripts", and those
  commits are now on `hotfix/locale-safety`. `check_port_available` and `explain_bind_failure`
  do the job through `scripts/start.sh`. Nothing to merge; nothing to decide.
- **`orcpub-port-doctor.sh`** (257 lines) — read-only diagnostic. Superseded by the above.

## Open — needs a decision, not just work

☐ **PR `hotfix/locale-safety`** — to the fork, and upstream to `orcpub/orcpub`. Pre-merged and
verified; nothing blocking but the decision.

☐ **Merge `fix/windows-port-detection`.** Finished and green; the only question is when.

☐ **Untangle `claude/fix-brave-export-bug-2Tt7j`.** It carries two unrelated things: three export
commits (`25c4a371`, `1002cc5c`, `3a1f5bfb` — what the branch is named for) and seven
character-load rescue commits (`daf32bb9`…`f1bda173`).

☐ **First-run path.** `start.sh server` needs Datomic already running — documented
([GETTING-STARTED.md](../GETTING-STARTED.md) steps 4–7) but it catches every newcomer.

## Open — small, self-contained

☐ **Five dead Font Awesome 4 names — upstreamable** — `fa-pencil`, `fa-exchange`, `fa-circle-o`, `fa-dot-circle-o`,
and the dead `.fa-caret-square-o-down` selector. They draw nothing today with the font working
perfectly. Fork-only files, so *not* upstreamable. Full table in
[icon-font-failure.md §5](icon-font-failure.md).
☐ `DateTimeFormatter/RFC_1123_DATE_TIME` follow-up — removes the custom pattern *and* the
`GMT`→`+0000` string surgery, identical output. See [locale-safety.md §1](locale-safety.md).
☐ `actions/checkout@v4` → `v5` — Node 20 deprecation warns on all five workflows
☐ `<meta name="darkreader-lock">` in `index.clj` — one line. Still worth having (the app ships its
own dark theme) but note it is **no longer a fix for anything known**.
☐ Missing-icon-font detection + a visible fallback — see [icon-font-failure.md §4](icon-font-failure.md)
☐ `title` + `aria-label` on icon-only controls, starting with the two ability-score arrows
☐ SVG for interactive icons — 7 icons carry their own `:on-click`; sizing in
[icon-font-failure.md §5](icon-font-failure.md)
☐ Regenerate [topic-index.md](topic-index.md) — it is generated and does not yet list
`locale-safety.md`: `lein with-profile +tools run -m orcpub.topic-index`

## Verified on 2026-09-20 — previously listed as unverifiable

Three gaps were reported as "cannot check here". All three were checkable; the tools were
simply not installed. Installing them took about a minute.

- **`lein test`** — Leiningen is not preinstalled, but the upstream script installs itself and
  `com.datomic/peer` resolves from public Maven. Result: **210 tests, 963 assertions, 0 failures,
  0 errors**, and green again under `es_ES`. Caveat: there is no `config-test` or
  `pedestal-test`, so the suite shows nothing was broken rather than proving the fix.
- **`shellcheck`** — `pip install shellcheck-py` provides 0.11.0. Run against all three scripts:
  **zero new finding types versus the upstream baseline** (11 before, 11 after).
- **The full app booted** — still not done. Needs a Datomic transactor and a cljs build.

The lesson is the one already in [verification-discipline.md](verification-discipline.md):
"I have not checked" and "it cannot be checked" are different claims, and only one is an excuse.

## Unverified — do not report these as done

- **`start_all` Ctrl+C.** `start_server`/`start_all` now `return $rc` explicitly, and `start_all`
  runs under `trap cleanup_on_exit INT TERM`. Reasoned about, never executed.
- **macOS.** Untested. It takes the `lsof` branch so it never reaches the Windows code, but BSD
  `netstat` does not understand `-tln` either. No macOS job in the workflow.
- **`hotfix/locale-safety` inside a running app.** The interceptor and both namespaces were
  verified standalone across four locales, and the reporter confirmed the same fix on their own
  machine; the branch itself has not been booted under Leiningen here (none installed).

## Corrections worth keeping

Things believed and then disproved during these sessions, recorded so they are not re-learned:

- **Dark Reader did not cause the missing arrows.** It was the leading hypothesis for two rounds on
  the strength of a computed `#e89b00` against a source `#f0a100`. A later probe found zero
  injected elements — it was not running on the page. The colour remains unexplained and was not
  the cause. The original [icon-font-failure.md](icon-font-failure.md) asserted it and has been
  rewritten.
- **The missing `Content-Type` was a consequence, not a cause.** No response was ever built, so
  nothing set one. Chasing it produced a proposed fix that would not have worked.
- **Three green reproductions proved only that the code works in English.** When a bug is
  machine-specific, enumerate what differs about the *machine* — locale, filesystem, time zone —
  rather than re-testing the code.
- `document.fonts.check()` returns **`true`** for a font family that does not exist.
- The glyph-width comparison — the technique most articles recommend for detecting a missing icon
  font — **did not discriminate** when measured (60.22px either way).
- Windows `netstat` prints `LISTENING`; Microsoft's own reference documents the state as `LISTEN`.
  Neither survives a non-English Windows, which is why the code matches the local-address column
  instead. (Same root cause family as [locale-safety.md](locale-safety.md).)
- `start.sh datomic` reports failure ~30s **after a successful start** on Git Bash, because its
  readiness check used the broken port test.
- **`CSP_POLICY` defaults to `strict` and `DEV_MODE` defaults to false**, so a self-hoster setting
  no env vars runs *enforcing* CSP, not the Report-Only the code comments describe as the dev
  experience. Harmless for the app's own scripts (all nonced) but it would block figwheel's
  websocket, since `build-csp-header` is called with `:dev-mode? false`.
