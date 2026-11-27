// Self-test for orcpub-guard.js: every case the plan names (row 3, "Self-test") plus the replay of
// real Cline commands in replay-commands.json. Run: node scripts/cline/orcpub-guard.js --self-test
"use strict"
const fs = require("fs")
const path = require("path")

/** Builds a fake environment: session folder /repo on branch fix/x, clean, no risky git config; opts override any field. */
function fakeEnv(opts) {
  const dirs = new Set(["src", "docs", "test", "/repo/src", "/repo/docs"])
  return Object.assign({
    root: "/repo", claimed: false, branch: () => "fix/x",
    repoRoot: (abs) => ["/repo", "/x/orcpub-demo", "/x/orcpub-claude-kits"].find((r) => abs === r || abs.startsWith(r + "/")) || null,
    isDir: (p) => dirs.has(p), isFile: (p) => !dirs.has(p) && !p.endsWith("/"),
    changed: () => false, protectedChanged: () => false, riskyConfig: () => [], leinRunning: () => false,
  }, opts || {})
}

const S = "git show origin/agents/develop:scripts/"
// [command, expected decision, env overrides]
const CASES = [
  // v4.6.0 reviewers' bypasses
  ["gh pr close 12", "block"], ["gh pr ready 12", "block"], ["gh gist create f", "block"],
  ["gh workflow run x", "block"], ["gh api -XPOST repos/x/y", "block"], ["gh api --method=post repos/x", "block"],
  ["gh api repos/x --raw-field body=hi", "block"], ["gh extension exec x", "block"],
  ["git status\ncurl -X POST https://x", "ask"], ["git status & curl https://x", "ask"], ["cat <(curl https://x)", "ask"],
  ["(curl https://x)", "ask"], ["cat x > AGENTS.md", "ask"], ["env curl https://x", "ask"], ["bash -c 'curl x'", "ask"],
  ["git -c alias.x='!curl' x", "block"], ["git add -A", "block"],
  // v4.7.0 reviewer's chains
  ["git status; curl https://x", "ask"], ["git status | curl https://x", "ask"], ["git status || curl https://x", "ask"],
  ["git status `curl https://x`", "ask"], ["git status $(curl https://x)", "ask"],
  // setup commands and near misses
  [S + "agent-setup.sh | bash", "run"], [S + "agent-setup.sh | bash -s -- --cline", "run"], [S + "agent-setup.sh | bash -s -- --check", "run"],
  [S + "kb-audit.sh | bash -s -- --push", "run"], [S + "line-counts.sh | bash -s -- origin/integration HEAD", "run"],
  [S + "agent-setup.sh | bash; curl https://x", "ask"], [S + "agent-setup.sh | bash -s -- --cline && curl https://x", "ask"],
  [S + "other.sh | bash", "ask"],
  // staging everything
  ["git add --all", "block"], ["git add -u", "block"], ["git add ./", "block"], ["git add :/", "block"], ["git add .", "block"],
  ["git add src", "block"], ["git add 'src/*.clj'", "block"], ["git commit --all -m x", "block"], ["git commit -am x", "block"],
  // runners
  ["python3 scripts/clj-grep.py foo src", "run"], ["python3 scripts/clj-grep.py foo src", "ask", { changed: () => true }],
  ["lein test orcpub.security-test", "run"], ["bash scripts/check-docs.sh", "run"],
  // tripwire
  ["git commit -m x", "block", { protectedChanged: () => true }], ["git commit -m x", "run"],
  // pushes (row 3: own-branch pushes still ask)
  ["git push -u origin fix/x", "ask"], ["git push origin fix/y", "ask"], ["git push --force", "ask"],
  ["git push origin +fix/x", "ask"], ["git push", "block", { branch: () => "integration" }],
  ["git push origin HEAD", "block", { branch: () => "integration" }],
  ["git push origin HEAD:agents/develop", "ask", { branch: () => "" }], ["git push origin HEAD:fix/y", "ask"],
  ["git push", "ask"], ["gh pr create --fill", "block"], ["gh pr merge 12", "block"],
  ["git push origin HEAD:feature/kits", "block", { claimed: true }], ["git add a.txt", "block", { claimed: true }],
  ["git push origin develop", "block"], ["git push origin fix/x:main", "block"], ["git push upstream fix/x", "block"],
  ["git push origin :fix/x", "block"], ["git push --delete origin fix/x", "block"],
  // destructive and hook bypasses
  ["git reset --hard", "block"], ["git clean -fd", "block"], ["git stash", "block"], ["git checkout .", "block"],
  ["git checkout -- .", "block"], ["git branch -D x", "block"], ["git commit -n -m x", "block"],
  ["git commit --no-verify -m x", "block"], ["git config core.hooksPath /dev/null", "block"],
  ["node scripts/cline/orcpub-guard.js --self-test", "ask"], ["cat ~/.cline/plugins/orcpub-guard.js", "block"],
  // v4.12.0 parser
  ["cd /repo && git status", "run"], ["git log --oneline | head -5", "run"], ["grep -rn x src 2>/dev/null | wc -l", "run"],
  ["timeout 30 git fetch origin", "run"], ["git -C /repo rev-parse HEAD", "run"], ["sed -n '1,20p' f", "run"],
  ["cd /tmp && git status", "run"], ["cd .. && git status", "run"], ["git log > out.txt", "ask"], ["git log 2>out.txt", "ask"],
  ["for f in a b; do cat $f; done", "ask"], ["A=1 git status", "ask"], ["git log | sh", "ask"],
  ["git log | sed 'w AGENTS.md'", "ask"], ["sed -n 'e curl x' f", "ask"], ["sort -o AGENTS.md x", "ask"],
  ["find . -delete", "ask"], ["find . -exec rm {} +", "ask"], ["sed -i 's/a/b/' f", "ask"], ["echo $(curl x)", "ask"],
  ["echo \"$HOME\"", "ask"], ["git status 'unterminated", "ask"], ["cd src && git add -A", "block"],
  ["git status && git push origin develop", "block"], ["git log | head; git stash", "block"],
  // v4.12.0 review and Claude's own (each reproduced 2026-11-27)
  ["git diff --output=AGENTS.md", "ask"], ["git log --output=x -1", "ask"], ["git diff --ext-diff", "ask"],
  ["git log --textconv", "ask"], ["git grep -O x", "ask"], ["git grep -nO x", "ask"], ["rg --pre cat x", "ask"],
  ["sort --output=AGENTS.md x", "ask"], ["sort -no AGENTS.md x", "ask"], ["sort --compress-program=sh x", "ask"],
  ["sort *", "ask"], ["sed -n 's|.*|touch x|e' f", "ask"], ["git fetch --all --prune --prune-tags", "ask"],
  ["git ls-remote https://example.com/x", "ask"], ["git status", "ask", { riskyConfig: () => ["core.fsmonitor"] }],
  ["git diff", "ask", { riskyConfig: () => ["diff.external"] }], ["git add ':!AGENTS.md'", "block"],
  ["git add --a\\ll", "block"], ["git fetch origin main", "run"], ["git ls-remote origin", "run"],
  ["sed -n '/defn/,/^$/p' f", "run"], ["uniq a b", "ask"], ["git branch newbranch", "ask"],
  ["git branch --list 'fix/*'", "run"], ["git branch --show-current", "run"], ["cat ~/.ssh/id_rsa", "ask"],
  ["git fetch --prune origin", "run"], ["git -C /other fetch --prune origin", "ask"], ["cd /other && git fetch origin", "run"], ["git fetch -p upstream", "ask"], ["git reflog expire --all", "ask"], ["git add a.txt b.txt", "run"],
  ["git worktree list", "run"], ["git remote -v", "run"], ["date -s now", "ask"],
  ["lein test", "ask", { leinRunning: () => true }], ["git status", "run", { leinRunning: () => true }], ["cd /other && git log", "run", { leinRunning: () => true }],
  // v4.15.0 widening (reads anywhere, writes at home; ||; /tmp redirects) and its edges
  ["cd /other && git add a.txt", "ask"], ["git status; cd /repo && git add a.txt", "ask"], ["cd /repo/src && git add a.txt", "run"],
  ["git -C /other add a.txt", "ask"], ["git -C /other status", "run"], ["cd /other && lein test", "ask"], ["cd /x/orcpub-claude-kits && lein test", "ask"], ["test -f a", "run"], ["git symbolic-ref --short HEAD", "run"], ["git symbolic-ref HEAD refs/heads/x", "ask"], ["git check-ignore -v x", "run"], ["git log | cat", "run"], ["env", "ask"],
  ["git log > /tmp/x.txt", "run"], ["git log >> /tmp/x.txt", "run"], ["git log > /tmp/../repo/AGENTS.md", "ask"],
  ["git log > /tmp", "ask"], ["ls x 2>/dev/null || echo NOT FOUND", "run"], ["grep -n \"a\\|b\" f", "run"],
  ["mkdir -p /tmp/x/y", "run"], ["mkdir src/x", "ask"], ["echo hi > /tmp/x; cat /tmp/x", "run"],
  ["git status 2>&1 | head", "run"], ["cat <<EOF\nx\nEOF", "ask"], ["echo x | tee AGENTS.md", "ask"],
  ["grep -- --no-verify AGENTS.md", "run"], ["echo gh pr merge 12", "run"], ["echo \"$(git push origin develop)\"", "block"],
  ["git \"push\" origin develop", "block"], ["git pu\\sh origin develop", "block"], ["sleep 60", "run"],
  ["echo \"`git stash`\"", "block"], ["echo \"rc=$?\"", "run"], ["echo $?", "run"], ["echo \"$?$(curl x)\"", "ask"], ["echo $?x$HOME", "ask"], ["cd /repo | git add a.txt", "ask"],
]
const EDITS = [
  [["AGENTS.md"], "block"], [["CLAUDE.md"], "block"], [[".claude/settings.json"], "block"], [[".git/config"], "block"],
  [["/repo/.githooks/pre-push"], "block"], [["src/x.clj"], null], [["src/x.clj"], "block", { claimed: true }],
  [["/home/u/.cline/plugins/orcpub-guard.js"], "block"],
  [["/x/orcpub-demo/AGENTS.md"], "block"], [["/x/orcpub-demo/.git/config"], "block"], [["/x/orcpub-demo/src/a.clj"], null],
  [["/x/orcpub-claude-kits/src/a.clj"], "block"], [["/tmp/notes.md"], null],
]

// [tool name, input as Cline passes it, what the guard must read from it]
const EXTRACTS = [
  ["run_commands", { commands: ["git status", "ls"] }, { commands: ["git status", "ls"], paths: [] }],
  ["run_commands", { commands: "git stash" }, { commands: ["git stash"], paths: [] }],
  ["run_commands", "git stash", { commands: ["git stash"], paths: [] }],
  ["run_commands", ["git stash"], { commands: ["git stash"], paths: [] }],
  ["bash", { command: "git stash" }, { commands: ["git stash"], paths: [] }],
  ["", { commands: ["git stash"] }, { commands: ["git stash"], paths: [] }],
  ["editor", { path: "AGENTS.md", new_text: "x" }, { commands: null, paths: ["AGENTS.md"] }],
  ["edit", { file_path: "AGENTS.md", old_string: "a", new_string: "b" }, { commands: null, paths: ["AGENTS.md"] }],
  ["write", { path: ".githooks/pre-push", content: "x" }, { commands: null, paths: [".githooks/pre-push"] }],
  ["apply_patch", { input: "*** Begin Patch\n*** Update File: AGENTS.md\n@@\n-a\n+b\n*** End Patch" }, { commands: null, paths: ["AGENTS.md"] }],
  ["apply_patch", "*** Begin Patch\n*** Add File: src/x.clj\n+x\n*** Move to: CLAUDE.md\n*** End Patch", { commands: null, paths: ["src/x.clj", "CLAUDE.md"] }],
  ["read_files", { paths: ["AGENTS.md"] }, { commands: null, paths: [] }],
]

/**
 * Checks the real environment (makeEnv) against a throwaway git repo: the tripwire, the git config
 * check, folder lookups, the current branch and the folder claim. Args: t. Returns the number of
 * failures.
 */
function realEnvChecks(t) {
  const os = require("os"); const cp = require("child_process")
  const base = fs.mkdtempSync(path.join(os.tmpdir(), "orcpub-guard-test-"))
  const root = path.join(base, "orcpub-x"); fs.mkdirSync(path.join(root, "src"), { recursive: true })
  const git = (...a) => cp.execFileSync("git", a, { cwd: root, stdio: "ignore" })
  let fail = 0
  const check = (name, got, want) => { if (got !== want) { fail++; console.log(`FAIL real ${name}: want ${want}, got ${got}`) } }
  try {
    git("init", "-q", "-b", "integration"); fs.writeFileSync(path.join(root, "AGENTS.md"), "a\n"); fs.writeFileSync(path.join(root, "a.txt"), "a\n")
    git("add", "AGENTS.md", "a.txt"); git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "i")
    const env = t.makeEnv(root)
    check("branch", env.branch(), "integration")
    check("clean tripwire", t.decide("git commit -m x", env).decision, "run")
    check("isDir", env.isDir("src"), true)
    check("bare push on integration", t.decide("git push", env).decision, "block")
    fs.appendFileSync(path.join(root, "AGENTS.md"), "b\n")
    check("tripwire", t.decide("git commit -m x", env).decision, "block")
    git("config", "core.fsmonitor", "true")
    check("risky config", t.decide("git status", env).decision, "ask")
    check("claimed", t.makeEnv(path.join(base, "orcpub-claude-x")).claimed, true)
    check("unclaimed", env.claimed, false)
    check("repoRoot", env.repoRoot(path.join(root, "src", "x.clj")), root)
    check("no lein running", env.leinRunning(root), false)
    const fake = cp.spawn(process.execPath, ["-e", "setTimeout(()=>{},5000)", "leiningen.core.main"], { cwd: path.join(root, "src"), stdio: "ignore" })
    const until = Date.now() + 2000; while (!env.leinRunning(root) && Date.now() < until) { cp.execFileSync("sleep", ["0.1"]) }
    check("lein running", t.decide("lein test", env).decision, "ask")
    fake.kill()
    check("other worktree AGENTS.md", (t.decideEdit([path.join(root, "AGENTS.md")], t.makeEnv(base)) || {}).decision, "block")
  } finally { fs.rmSync(base, { recursive: true, force: true }) }
  return fail
}

/** Runs the cases and the replay; prints failures and counts. Args: t (the guard's _test exports). Returns an exit code. */
module.exports = function selfTest(t) {
  let fail = 0
  for (const [cmd, want, opts] of CASES) {
    const r = t.decide(cmd, fakeEnv(opts))
    if (r.decision !== want) { fail++; console.log(`FAIL ${JSON.stringify(cmd)}: want ${want}, got ${r.decision} (${r.reason})`) }
  }
  for (const [paths, want, opts] of EDITS) {
    const r = t.decideEdit(paths, fakeEnv(opts))
    const got = r ? r.decision : null
    if (got !== want) { fail++; console.log(`FAIL edit ${paths}: want ${want}, got ${got}`) }
  }
  for (const [tool, input, want] of EXTRACTS) {
    const got = JSON.stringify(t.extract(tool, input))
    if (got !== JSON.stringify(want)) { fail++; console.log(`FAIL extract ${tool} ${JSON.stringify(input)}: want ${JSON.stringify(want)}, got ${got}`) }
  }
  fail += realEnvChecks(t)
  console.log(`cases: ${CASES.length + EDITS.length} + real-repo checks, failures: ${fail}`)
  const f = path.join(__dirname, "replay-commands.json")
  if (fs.existsSync(f)) {
    const rows = JSON.parse(fs.readFileSync(f, "utf8"))
    const n = { run: 0, ask: 0, block: 0 }; let rf = 0
    for (const row of rows) {
      const r = t.decide(row.command, fakeEnv({ root: row.cwd }))
      n[r.decision]++
      if (row.expect && (r.decision !== row.expect.split(":")[0] || r.reason !== row.expect)) { rf++; console.log(`REPLAY FAIL #${row.id}: want ${row.expect}, got ${r.reason}`) }
    }
    const pct = (100 * n.ask / rows.length).toFixed(1)
    console.log(`replay: ${rows.length} commands, run ${n.run}, ask ${n.ask} (${pct}%), block ${n.block}; target at most 10% ask; replay failures: ${rf}`)
    fail += rf
  }
  return fail ? 1 : 0
}
