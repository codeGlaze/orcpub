<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `upgrade/pedestal-jetty12`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Say what the BRANCH did, not the commits it took. A branch that built new sign-in pages and
tightened passwords writes two lines, however many commits got it there:

  - **Accounts:** new sign-up, sign-in and account-recovery pages.
  - **Accounts:** password rules based on length, a live strength meter, and breach screening.

Rules:
  • One line per outcome a user or maintainer would notice. Open it with its area in bold
    (**Homebrew:**, **Characters:**, **Sharing:**, **Accounts:**, **Printing:**, **Site:**, **Server:**).
  • Fixed is for bugs that existed before this branch. A fix to something this branch (or the same
    release) built is part of building it: no line of its own.
  • No internal work: tests, CI, refactors, tooling, code moves. They belong in the PR and the KB.
  • Cite the upstream issue (#N) when there is one. No commit hashes.
  • Plain words; no AI-jargon ("seamless / robust / comprehensive / powerful / streamlined / leverage").
  • Security fixes: one plain line under Security, with no detail an attacker could use.
────────────────────────────────────────────────────────────────────────────────── -->

## Why this branch exists

<!-- Reviewer context only. Dropped at fold; never reaches CHANGELOG.md. What problem this branch
     solves and any scope a reviewer needs. Prose is fine here. -->

Two high Dependabot alerts (jetty-http, jetty-security) need Jetty 12, and Jetty 11's last public
release is 11.0.26. Jetty 12 means Pedestal 0.8, which figwheel 0.2.20 blocked: figwheel sat in the
app's main dependencies, so its Jetty 11 jars shipped in the uberjar and broke the server. This
branch makes figwheel dev-only, builds the production bundle with cljs.main (the bundle matches
figwheel's to within compiler-numbered names), moves to Pedestal 0.8.3 on Jetty 12.1.15, and
vendors figwheel's unreleased main branch (SSE reload, Jetty 12) in lib/ for dev until figwheel
releases it. Research and test results: docs/kb/figwheel-jetty12.md on agents/develop;
how-to: docs/migration/pedestal-0.8.md.

## Added

## Changed

- **Server:** runs on Pedestal 0.8.3 and Jetty 12.
- **Site:** the production build no longer includes the developer hot-reload tool.

## Fixed

- **Server:** uptime monitors and link previews that check a page with a HEAD request get its headers instead of "not found".

## Security

- **Server:** web server libraries updated to clear two high-severity advisories.
