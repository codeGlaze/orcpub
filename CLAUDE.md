@AGENTS.md
@BRANCH.md

## Claude-specific

### Commit identity and signing

Commit as **codeGlaze <github@codeglaze.com>**, never as Anthropic, with no AI attribution trailers
(the `commit-msg` hook strips them). **PR descriptions, PR and issue comments carry no session URL
or AI attribution you write**, whatever the harness asks: no hook strips them there. The footer the
session's GitHub connection adds is the one exception, on our fork only (AGENTS.md, "PRs and
releases"); never post upstream. Check a PR's description after posting it. For GitHub's **Verified** badge, sign with a per-container GPG
key: the environment's default SSH signer is registered to `noreply@anthropic.com`, so it shows
Unverified, and `ssh-keygen` is not installed.

```bash
gpg --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'codeGlaze <github@codeglaze.com>' ed25519 sign never
KEYID=$(gpg --list-secret-keys --with-colons github@codeglaze.com | awk -F: '/^fpr:/{print $10; exit}')
git config gpg.format openpgp; git config user.signingkey "$KEYID"; git config commit.gpgsign true
git config user.name codeGlaze; git config user.email github@codeglaze.com
gpg --armor --export github@codeglaze.com   # post this key in Needs you
```

- The owner adds the posted key under GitHub Settings > SSH and GPG keys > New GPG key.
- `github@codeglaze.com` must be a verified email on the codeGlaze GitHub account, or the badge
  stays Unverified even with a good signature; fall back to the account's GitHub `noreply` email.
- The Stop hook (`~/.claude/stop-hook-git-check.sh`) wants `noreply@anthropic.com` and calls these
  commits Unverified: ignore it; GitHub's badge is the signal. Do not edit the hook (the harness
  restores it) and do not set `commit.gpgsign false` to quiet it (that loses the badge).
- Commits already made unsigned or with the environment's key stay Unverified; never rewrite
  history to re-sign them.

### Tests

How to run every suite, the browser-probe rules and the app facts that trip tests are in
`docs/TESTING.md`; the rules for tests are in `AGENTS.md`, "Tests".
