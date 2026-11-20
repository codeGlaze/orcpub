# Personal-data scan

What stops real IP addresses, email addresses, keys, tokens, password hashes and private commit
identities from reaching GitHub. Added 2026-11-19 (owner) after a scan found what had already gone
public with nothing to stop it.

## What runs, and when

- **Pre-push hook** (`.githooks/pre-push`, armed by `agent-setup.sh`): runs the branch's own
  `scripts/personal-data-scan.py --pushed <remote> <sha>` on the commits the push adds, for every
  remote (gitea too). A branch without the script (cut before #89) prints "not checked".
- **CI** (`continuous-integration.yml`, job "Personal data scan", from #89): the same script over the
  PR's commits (`origin/<base>..HEAD`) or the pushed range, then gitleaks 8.24.3 over the same range.
- **By hand:** `scripts/personal-data-scan.py --tree <ref>` scans every file at a ref;
  `--range a..b` a range of commits.

## Rules

- Public IPv4 addresses. Private, loopback, reserved and the documentation ranges
  (`192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24`) pass, as do obvious placeholders
  (`9.9.9.9`, `1.2.3.4`) and dotted numbers on a line that reads as a version. **Tests use the
  documentation ranges.**
- Email addresses not on the allow list; names like `icon@2x.png` are skipped.
- Private keys, AWS, GitHub, OpenAI/Anthropic-style, Slack and Google keys, bcrypt and buddy
  password hashes, JWTs. Their values are hidden in the output.
- **Commit author and committer emails** must be `github@codeglaze.com`, `noreply@github.com` or a
  `users.noreply.github.com` address. The owner's private address is deliberately not named
  anywhere in the repo: the rule is an allow list, so it needs no mention.
- Exceptions: `.personal-data-allow` (kinds `email`, `email-domain`, `ip`, `author`, `path`, each
  with a comment saying why it is safe), or the marker `personal-data: ok` at the end of a line.

## Verified 2026-11-19

Caught every known leak in history (the commits are listed in #89's commit message for the scan):
the player address in `client_address_test.clj` and `client-ip-behind-proxies.md`, the owner's
private address as commit author and in `branching-model.md`, the 2017 AWS key in `system.clj` and
`myserver.key`. Clean on #89's tree and range. The hook blocked a planted address and passed a
clean push in a scratch clone.

## What was already public when it landed

- The owner's private address: author of 1 commit on `develop`/`integration` (2026-09-16), 6 on
  agents/develop, up to 13 on some feature branches (most of the 96 such commits are local only);
  in `branching-model.md` history (2026-08-26 to 2026-09-06). Not upstream.
- A player's IP address, 2026-10-22 to 2026-11-19 (redacted on #89 and in the KB).
- The original author's AWS key and a server private key from 2017, in upstream's own history.
- GitHub's account setting "Block command line pushes that expose my email" covers author emails
  registered on the account; this scan covers the rest.

## Rules tightened by #89's reviews (2026-11-19)

- A dotted number passes as a version only when a label sits right before it (`version=2.5.0.27`, `release 2.5.0.26`); a public address after `RELEASE_HOST=` is flagged. Published versions that appear bare are on the allow list.
- Only names ending in an image type (`icon@2x.png`) skip the email check; `.md` is a country TLD, so an address at a `.md` domain is flagged.
- Merge commits are scanned with `git show --remerge-diff`: only what the conflict resolution wrote, not the merged branch's lines.
- IPs in URLs, after a colon and at a sentence end are caught; diff headers are read only before the first hunk; quoted paths are scanned.

