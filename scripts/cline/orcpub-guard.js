// orcpub guard: a Cline plugin that blocks dangerous commands, approves known-safe ones itself,
// and leaves everything else to Cline's own Approve dialog (plan row 3; agent-harnesses.md).
// Cline's global command auto-approve must be OFF, or nothing ever asks (plan fact 12).
// Self-test: node scripts/cline/orcpub-guard.js --self-test
"use strict"
const fs = require("fs")
const os = require("os")
const path = require("path")
const cp = require("child_process")

const PROTECTED = ["AGENTS.md", "CLAUDE.md", "BRANCH.md", ".claude/", ".githooks/", "scripts/agent-setup.sh"]
const PROTECTED_BRANCHES = ["develop", "integration", "main"]
const GUARD_PATHS = [".cline/plugins", ".cline/orcpub-guard"]
const FILTERS = new Set(["head", "tail", "grep", "rg", "wc", "sort", "uniq", "cut", "tr", "sed", "cat"])
const RISKY_GIT_CONFIG = [/^core\.fsmonitor$/, /^core\.pager$/, /^pager\./, /^diff\.external$/,
  /^diff\..*\.command$/, /\.textconv$/, /^filter\./, /^alias\./, /^core\.sshcommand$/]
const SETUP = /^git show origin\/agents\/develop:scripts\/(agent-setup\.sh|kb-audit\.sh|line-counts\.sh) \| bash( -s -- (--check|--cline|--push|[A-Za-z0-9._\/-]+( [A-Za-z0-9._\/-]+)?))?$/

/** Result helpers: each decision is {decision: "run"|"ask"|"block", reason: "<code>: <text>"}. */
const run = (r) => ({ decision: "run", reason: r || "run" })
const ask = (r) => ({ decision: "ask", reason: "ask:" + r })
const block = (r) => ({ decision: "block", reason: "block:" + r })

/**
 * Reads a double-quoted string starting after the opening quote, as bash does: `\` escapes only
 * $ ` " \ and newline. Args: cmd, i (index after the quote). Returns {s, end, expands} where end
 * is the closing quote's index (-1 if unterminated) and expands is true when $ or ` appears
 * (except $?, the last exit status, which is always a number).
 */
function readDouble(cmd, i) {
  let s = ""; let expands = false
  for (; i < cmd.length && cmd[i] !== '"'; i++) {
    if (cmd[i] === "\\" && i + 1 < cmd.length && '$`"\\\n'.includes(cmd[i + 1])) { s += cmd[++i]; continue }
    if (cmd[i] === "$" && cmd[i + 1] === "?") { s += "$?"; i++; continue }
    if (cmd[i] === "$" || cmd[i] === "`") expands = true
    s += cmd[i]
  }
  return { s, end: i < cmd.length ? i : -1, expands }
}

/**
 * Splits a command the way bash would, refusing anything it does not fully understand.
 * Args: cmd (string). Returns {ok: true, segments: [{op, words, redirs}]} where op joined the
 * segment to the previous one ("start", "&&", "||", ";", "|", "\n") and redirs lists
 * {op: ">"|">>"|"<", target} (2>&1-style duplications are dropped), or {ok: false, why}.
 */
function parse(cmd) {
  const segs = []; let words = []; let redirs = []; let w = null; let op = "start"
  const fail = (why) => ({ ok: false, why })
  const endWord = () => { if (w !== null) { words.push(w); w = null } }
  const endSeg = (next) => { endWord(); if (!words.length) return false; segs.push({ op, words, redirs }); words = []; redirs = []; op = next; return true }
  for (let i = 0; i < cmd.length; i++) {
    const c = cmd[i]
    if (c === "'") {
      const j = cmd.indexOf("'", i + 1); if (j < 0) return fail("unterminated quote")
      w = (w || "") + cmd.slice(i + 1, j); i = j; continue
    }
    if (c === '"') {
      const d = readDouble(cmd, i + 1)
      if (d.end < 0) return fail("unterminated quote")
      if (d.expands) return fail("expansion inside double quotes")
      w = (w || "") + d.s; i = d.end; continue
    }
    if (c === "\\") return fail("backslash outside quotes")
    if (c === "$" && cmd[i + 1] === "?") { w = (w || "") + "$?"; i++; continue }
    if (c === "$" || c === "`") return fail("expansion")
    if ("(){}".includes(c)) return fail("grouping")
    if ("*?[".includes(c)) return fail("unquoted glob")
    if (c === "~" && w === null) return fail("tilde")
    if (c === "#" && w === null) return fail("comment")
    if (c === "!" && w === null) return fail("negation or history")
    if (c === " " || c === "\t") { endWord(); continue }
    if (c === "\n") { if (words.length || w !== null) { if (!endSeg("\n")) return fail("empty part") } continue }
    if (c === ";") { if (cmd[i + 1] === ";") return fail("case operator"); if (!endSeg(";")) return fail("empty part"); continue }
    if (c === "&") {
      if (cmd[i + 1] === "&") { if (!endSeg("&&")) return fail("empty part"); i++; continue }
      return fail("background or &> redirect")
    }
    if (c === "|") {
      if (cmd[i + 1] === "|") { if (!endSeg("||")) return fail("empty part"); i++; continue }
      if (cmd[i + 1] === "&") return fail("|&")
      if (!endSeg("|")) return fail("empty part"); continue
    }
    if (c === ">" || c === "<") {
      if (w !== null && !/^\d$/.test(w)) endWord()
      if (w !== null) w = null
      let k = i + 1; let rop = c
      if (c === ">" && cmd[k] === ">") { rop = ">>"; k++ }
      if (cmd[k] === "&") { const m = cmd.slice(k + 1).match(/^\d(?=[\s;&|]|$)/); if (!m) return fail("redirect"); i = k + 1; continue }
      if (cmd[k] === "<" || cmd[k] === "(" || cmd[k] === ">") return fail("here-doc or process substitution")
      while (cmd[k] === " " || cmd[k] === "\t") k++
      let t = ""
      for (; k < cmd.length && !" \t\n;&|<>()".includes(cmd[k]); k++) {
        if ("$`\\*?[~{}'\"".includes(cmd[k])) return fail("redirect target")
        t += cmd[k]
      }
      if (!t) return fail("redirect")
      redirs.push({ op: rop, target: t }); i = k - 1; continue
    }
    w = (w || "") + c
  }
  endWord()
  if (words.length) segs.push({ op, words, redirs })
  else if (redirs.length) return fail("redirect without a command")
  else if (segs.length && op !== "\n") return fail("trailing operator")
  if (!segs.length) return fail("empty")
  for (const s of segs) {
    if (/^[A-Za-z_][A-Za-z0-9_]*=/.test(s.words[0])) return fail("leading assignment")
    if (["for", "while", "until", "do", "done", "if", "then", "else", "fi", "case", "esac", "function", "select", "time", "coproc"].includes(s.words[0])) return fail("shell keyword")
  }
  return { ok: true, segments: segs }
}

/**
 * Splits any command loosely, for block checks only. Quotes are honoured (quoted text stays one
 * word); a backslash keeps the next character; every operator, grouping or expansion is a break.
 * The body of each $( ) or backtick, even inside double quotes, is split again as a command of its
 * own. Args: cmd. Returns arrays of words.
 */
function looseSegments(cmd) {
  const out = []; let words = []; let w = null
  const endWord = () => { if (w !== null) { words.push(w); w = null } }
  const endSeg = () => { endWord(); if (words.length) out.push(words); words = [] }
  for (let i = 0; i < cmd.length; i++) {
    const c = cmd[i]
    if (c === "'") { const j = cmd.indexOf("'", i + 1); const e = j < 0 ? cmd.length : j; w = (w || "") + cmd.slice(i + 1, e); i = e; continue }
    if (c === '"') { const d = readDouble(cmd, i + 1); const e = d.end < 0 ? cmd.length : d.end; w = (w || "") + d.s; i = e; continue }
    if (c === "\\") { if (i + 1 < cmd.length) w = (w || "") + cmd[++i]; continue }
    if (" \t".includes(c)) { endWord(); continue }
    if (";&|\n()`<>{}$".includes(c)) { endSeg(); continue }
    w = (w || "") + c
  }
  endSeg()
  for (const m of cmd.matchAll(/\$\(([^()]*(?:\([^()]*\)[^()]*)*)\)|`([^`]*)`/g)) out.push(...looseSegments(m[1] || m[2] || ""))
  return out
}

/** True when a short-flag word (like -am) holds the letter, or the word equals one of the long names. */
function hasFlag(words, letter, longs) {
  return words.some((x) => (letter && /^-[A-Za-z]+$/.test(x) && x.slice(1).includes(letter)) || (longs || []).some((l) => x === l || x.startsWith(l + "=")))
}

/** Strips `timeout N`, and `git` global options, returning {cmd, args, gitC} with args after the subcommand for git. */
function head(words) {
  let w = words.slice()
  if (w[0] === "timeout" && /^\d+(\.\d+)?[smhd]?$/.test(w[1] || "")) w = w.slice(2)
  if (w[0] !== "git") return { cmd: w[0], args: w.slice(1), words: w }
  let i = 1; const globals = []; let gitC = null
  while (i < w.length && w[i].startsWith("-")) {
    if (w[i] === "-C") { gitC = w[i + 1]; globals.push(w[i], w[i + 1]); i += 2; continue }
    globals.push(w[i]); i++
  }
  return { cmd: "git", sub: w[i], args: w.slice(i + 1), globals, gitC, words: w }
}

/**
 * Applies the block rules to one segment's words. Args: words, env (see makeEnv). Returns a
 * block result or null. Runs on both the strict parse and the loose split.
 */
function blockWords(words, env) {
  const all = words.join(" ")
  if (GUARD_PATHS.some((g) => all.includes(g))) return block("self: the guard's own files are the owner's; tell the owner")
  const h = head(words)
  if (h.cmd === "gh") {
    const sub = h.args.slice(0, 2).join(" ")
    if (/^(pr (view|diff|list|status|checks)|issue (view|list)|run (view|list))$/.test(sub)) return null
    if (h.args[0] === "api" && !h.args.some((a) => /^(-X|--method|-f|-F|--field|--raw-field|--input)/.test(a))) return null
    return block("gh: posting to GitHub needs the owner's yes; draft it and tell the owner")
  }
  if (h.cmd !== "git") return null
  if (words.includes("--no-verify")) return block("no-verify: hooks must run")
  if (/core\.hookspath/i.test(all)) return block("hooksPath: hooks must run")
  if (h.globals.some((g) => g === "-c" || g.startsWith("-c"))) return block("git -c: config on the command line")
  const a = h.args
  if (env.claimed && ["add", "commit", "merge", "push", "rebase", "switch", "checkout", "reset", "cherry-pick", "revert", "am"].includes(h.sub)) return block("folder: this worktree belongs to another harness")
  switch (h.sub) {
    case "commit":
      if (hasFlag(a, "n", ["--no-verify"])) return block("no-verify: hooks must run")
      if (hasFlag(a, "a", ["--all"])) return block("stage-all: stage named files only")
      if (env.protectedChanged()) return block("tripwire: a protected file changed outside the editor; tell the owner")
      return null
    case "add":
      if (hasFlag(a, "A", ["--all"]) || hasFlag(a, "u", ["--update"])) return block("stage-all: stage named files only")
      for (const x of a.filter((x) => !x.startsWith("-"))) {
        if (x === "." || x === "./" || x.startsWith(":") || /[*?[]/.test(x) || env.isDir(x)) return block("stage-all: stage named files only")
      }
      return null
    case "config":
      return null
    case "reset": return a.includes("--hard") ? block("destructive: reset --hard") : null
    case "clean": return block("destructive: git clean")
    case "stash": return block("destructive: git stash")
    case "checkout":
      if (a.includes(".") ) return block("destructive: checkout .")
      return null
    case "branch":
      if (hasFlag(a, "D") || hasFlag(a, "d", ["--delete"])) return block("branch-delete: deleting a branch")
      return null
    case "push": {
      if (hasFlag(a, "d", ["--delete"])) return block("branch-delete: deleting a branch")
      const pos = a.filter((x) => !x.startsWith("-"))
      if (pos.length && pos[0] !== "origin") return block("push-remote: push only to origin")
      for (const r of pos.slice(1)) {
        if (r.startsWith(":")) return block("branch-delete: deleting a branch")
        const dst = r.replace(/^\+/, "").split(":").pop().replace(/^refs\/heads\//, "")
        if (PROTECTED_BRANCHES.includes(dst) || ((dst === "HEAD" || dst === "@") && PROTECTED_BRANCHES.includes(env.branch()))) return block("push-protected: develop, integration and main are the owner's")
      }
      if (pos.length < 2 && PROTECTED_BRANCHES.includes(env.branch())) return block("push-protected: develop, integration and main are the owner's")
      if (env.protectedChanged()) return block("tripwire: a protected file changed outside the editor; tell the owner")
      return null
    }
  }
  return null
}

/** True when any folder in an absolute path is named for another harness (orcpub-claude-x). */
function claimedPath(p) {
  return p.split(path.sep).some((d) => /(^|-)(claude|copilot)(-|$)/.test(d))
}

/** True when a path (relative to dir) is under /tmp/ and names something below it. */
function inTmp(dir, p) {
  const r = path.resolve(dir, p)
  return r.startsWith("/tmp/") && !p.split("/").includes("..")
}

/** True when a path (relative to dir) stays inside the session folder. */
function inside(dir, p, env) {
  if (!p || p.includes("$") || p.startsWith("~")) return false
  const r = path.resolve(dir, p)
  return r === env.root || r.startsWith(env.root + path.sep)
}

/** True when any word is one of the flags: a long flag alone or with =value, a short flag anywhere in a cluster (-no holds -o). */
const forbids = (words, list) => words.some((x) => list.some((f) => x === f || x.startsWith(f + "=") || (f.length === 2 && /^-[^-]/.test(x) && x.slice(1).includes(f[1]))))
const SED_ADDR = String.raw`(?:\d+|\$|/(?:[^/\\]|\\.)*/)`
const SED_PRINT = new RegExp(String.raw`^\s*${SED_ADDR}?(?:\s*,\s*${SED_ADDR})?\s*p\s*(?:;\s*${SED_ADDR}?(?:\s*,\s*${SED_ADDR})?\s*p\s*)*;?\s*$`)

/** Checks sed: -n, no in-place or script file, and only print scripts. Returns a reason string when it must ask, or null. */
function sedProblem(args) {
  let n = false; const scripts = []; const files = []
  for (let i = 0; i < args.length; i++) {
    const x = args[i]
    if (x === "-e" || x === "--expression") { scripts.push(args[++i]); continue }
    if (x.startsWith("--expression=")) { scripts.push(x.slice(13)); continue }
    if (x === "--quiet" || x === "--silent") { n = true; continue }
    if (x.startsWith("--")) { if (["--regexp-extended", "--null-data", "--separate", "--unbuffered", "--posix"].includes(x)) continue; return "sed option " + x }
    if (/^-[A-Za-z]+$/.test(x)) {
      for (const ch of x.slice(1)) { if (ch === "n") n = true; else if (!"Ersuz".includes(ch)) return "sed option -" + ch }
      continue
    }
    if (!scripts.length && !x.startsWith("-")) { scripts.push(x); continue }
    files.push(x)
  }
  if (!n) return "sed without -n"
  if (!scripts.length || !scripts.every((s) => SED_PRINT.test(s))) return "sed script is not print-only"
  return null
}

/**
 * Decides one strictly parsed simple command (not cd). Args: words, cwd, env, isFilter, home
 * (true when cwd is surely the session folder or under it: writes and runners need it).
 * Returns a run or ask result.
 */
function allowWords(words, cwd, env, isFilter, home) {
  const h = head(words)
  const c = h.cmd; const a = h.args
  if (isFilter && !FILTERS.has(c)) return ask("pipe into " + c)
  switch (c) {
    case "ls": case "pwd": case "cat": case "head": case "tail": case "wc": case "cut": case "tr":
    case "diff": case "stat": case "echo": case "printf": case "true": case "which": case "grep": case "test":
      return run()
    case "uniq": return a.filter((x) => !x.startsWith("-")).length > 1 ? ask("uniq with an output file") : run()
    case "date": return forbids(a, ["-s", "--set"]) ? ask("date --set") : run()
    case "file": return forbids(a, ["-C", "--compile"]) ? ask("file --compile") : run()
    case "sort": return forbids(a, ["-o", "--output", "--compress-program"]) ? ask("sort writes or runs") : run()
    case "rg": return forbids(a, ["--pre", "--pre-glob"]) ? ask("rg --pre runs a program") : run()
    case "find": return a.some((x) => ["-exec", "-execdir", "-ok", "-okdir", "-delete", "-fprint", "-fprint0", "-fprintf", "-fls"].includes(x)) ? ask("find writes or runs") : run()
    case "sed": { const p = sedProblem(a); return p ? ask(p) : run() }
    case "sleep": return a.length === 1 && /^\d+(\.\d+)?[smh]?$/.test(a[0]) ? run() : ask("sleep")
    case "mkdir": return a.every((x) => x === "-p" || inTmp(cwd, x)) && a.some((x) => x !== "-p") ? run() : ask("mkdir outside /tmp")
  }
  if (["python3", "bash", "lein"].includes(c) && !home) return ask("runner outside the session folder")
  switch (c) {
    case "python3":
      if (a[0] === "scripts/clj-grep.py" || a[0] === "docs/kb/tools/kb.py") return env.changed(a[0], cwd) ? ask("runner script changed") : run()
      return ask("not listed: python3")
    case "bash":
      if (a.length === 1 && (a[0] === "docs/kb/tools/topic-index.sh" || a[0] === "scripts/check-docs.sh")) return env.changed(a[0], cwd) ? ask("runner script changed") : run()
      return ask("not listed: bash")
    case "lein":
      if (env.leinRunning(env.repoRoot(path.join(cwd, "_")) || cwd)) return ask("another lein is running in this worktree; wait for it (AGENTS.md: one lein per worktree)")
      if (a[0] === "test" || a[0] === "lint" || a.slice(0, 3).join(" ") === "trampoline with-profile lint") return run()
      return ask("not listed: lein " + (a[0] || ""))
    case "git": return allowGit(h, cwd, env, home)
  }
  return ask("not listed: " + c)
}

/**
 * Decides a git command that passed the blocks. Args: h (from head), cwd, env, home (see
 * allowWords). Reads run in any folder; add and commit need the session folder. Returns a result.
 */
function allowGit(h, cwd, env, home) {
  const a = h.args
  for (let i = 0; i < h.globals.length; i++) {
    const g = h.globals[i]
    if (g === "--no-pager") continue
    if (g === "-C" && h.globals[i + 1]) { i++; continue }
    return ask("git option " + g)
  }
  const dir = h.gitC ? path.resolve(cwd, h.gitC) : cwd
  const homeHere = h.gitC ? inside(cwd, h.gitC, env) && home : home
  if (["add", "commit"].includes(h.sub) && !homeHere) return ask("git " + h.sub + " outside the session folder")
  const risky = env.riskyConfig(dir)
  if (risky.length) return ask("git config runs a program: " + risky.join(", "))
  if (forbids(a, ["--output", "--ext-diff", "--textconv"])) return ask("git option writes or runs a program")
  const pos = a.filter((x) => !x.startsWith("-"))
  switch (h.sub) {
    case "status": case "diff": case "log": case "show": case "ls-files": case "ls-tree": case "rev-parse":
    case "merge-base": case "cat-file": case "blame": case "shortlog": case "describe": case "check-ignore":
      return run()
    case "symbolic-ref": return pos.length <= 1 ? run() : ask("git symbolic-ref with a target writes")
    case "grep": return forbids(a, ["-O", "--open-files-in-pager"]) ? ask("git grep -O opens a pager") : run()
    case "reflog": return !pos.length || pos[0] === "show" ? run() : ask("git reflog " + pos[0])
    case "branch": {
      const ok = ["--show-current", "--list", "-l", "-a", "--all", "-r", "--remotes", "-v", "-vv", "--verbose", "--contains", "--no-contains", "--merged", "--no-merged", "--points-at", "--sort", "--format", "--no-color", "--color"]
      const takesArg = ["--contains", "--no-contains", "--merged", "--no-merged", "--points-at", "--sort", "--format"]
      let listing = false; const free = []
      for (let i = 0; i < a.length; i++) {
        const x = a[i]; const base = x.split("=")[0]
        if (!x.startsWith("-")) { free.push(x); continue }
        if (!ok.includes(base)) return ask("git branch " + x)
        if (base === "--list" || base === "-l") listing = true
        if (takesArg.includes(x)) i++
      }
      return free.length && !listing ? ask("git branch with a name creates a branch") : run()
    }
    case "remote":
      if (!a.length || (a.length === 1 && a[0] === "-v") || a[0] === "get-url") return run()
      return ask("git remote " + a.join(" "))
    case "worktree": return a[0] === "list" ? run() : ask("git worktree " + (a[0] || ""))
    case "ls-remote": return pos[0] === "origin" ? run() : ask("git ls-remote: origin only")
    case "fetch": {
      if (a.some((x) => x === "--prune" || x === "-p") && !(h.gitC ? inside(cwd, h.gitC, env) && home : home)) return ask("git fetch --prune outside the session folder")
      const flags = a.filter((x) => x.startsWith("-"))
      if (flags.some((f) => !["--prune", "-p", "-q", "--quiet"].includes(f))) return ask("git fetch option")
      if (pos.length > 2 || (pos.length && pos[0] !== "origin") || pos.slice(1).some((r) => r.includes(":") || r.startsWith("+"))) return ask("git fetch: origin and one branch only")
      return run()
    }
    case "add": {
      for (const x of a) {
        if (x === "--" || x === "-v" || x === "--verbose") continue
        if (x.startsWith("-")) return ask("git add " + x)
        if (!env.isFile(path.resolve(dir, x))) return ask("git add: not an existing file")
      }
      return pos.length ? run() : ask("git add with nothing named")
    }
    case "commit": {
      for (let i = 0; i < a.length; i++) {
        const x = a[i]
        if (x === "-m" || x === "--message") { i++; continue }
        if (x.startsWith("--message=") || x === "-q" || x === "--quiet" || x === "-s" || x === "--signoff" || x === "-v" || x === "--verbose") continue
        return ask("git commit " + x)
      }
      return run()
    }
    case "push": return ask("push: asks until row 5 exists")
  }
  return ask("not listed: git " + (h.sub || ""))
}

/**
 * Decides one command string. Args: cmd, env. Returns {decision, reason}.
 * Order: blocks on the loose split, setup commands, strict parse, blocks again, then the list.
 */
function decide(cmd, env) {
  const text = cmd.trim()
  for (const words of looseSegments(text)) { const b = blockWords(words, env); if (b) return b }
  if (SETUP.test(text)) return run("setup")
  const p = parse(text)
  if (!p.ok) return ask("parse: " + p.why)
  let cwd = env.root; let sure = true
  for (let i = 0; i < p.segments.length; i++) {
    const s = p.segments[i]
    const b = blockWords(s.words, env); if (b) return b
    const piped = s.op === "|" || (p.segments[i + 1] && p.segments[i + 1].op === "|")
    for (const r of s.redirs) {
      if (r.op === "<") continue
      if (r.target !== "/dev/null" && !inTmp(cwd, r.target)) return ask("redirect to " + r.target)
    }
    if (s.words[0] === "cd") {
      if (s.words.length !== 2 || piped) return ask("cd form")
      if (!(s.op === "start" || s.op === "&&")) sure = false
      cwd = path.resolve(cwd, s.words[1]); continue
    }
    const home = sure && inside(cwd, ".", env)
    const r = allowWords(s.words, cwd, env, s.op === "|", home)
    if (r.decision !== "run") return r
  }
  return run()
}

/** Pulls the file paths out of an editor call's input (any key naming a path). Returns strings. */
function editorPaths(input) {
  const out = []
  const walk = (o) => { if (o && typeof o === "object") for (const [k, v] of Object.entries(o)) { if (typeof v === "string" && /path|file/i.test(k)) out.push(v); else walk(v) } }
  walk(input); return out
}

/**
 * Decides a write. Each path is judged relative to the git checkout that holds it, so a protected
 * file in another worktree is caught too. Args: paths, env. Returns a block result or null.
 */
function decideEdit(paths, env) {
  for (const p of paths) {
    const abs = path.resolve(env.root, p)
    if (GUARD_PATHS.some((g) => abs.includes(g))) return block("self: the guard's own files are the owner's; tell the owner")
    const repo = env.repoRoot(abs)
    if (!repo) continue
    const rel = path.relative(repo, abs).split(path.sep).join("/")
    if (env.claimed || claimedPath(repo)) return block("folder: this worktree belongs to another harness")
    if (rel === ".git" || rel.startsWith(".git/")) return block("git-dir: writes under .git/ can make git run programs")
    if (PROTECTED.some((q) => q.endsWith("/") ? rel.startsWith(q) : rel === q)) return block("protected: a protected file needs the owner's yes; draft a patch and tell the owner")
  }
  return null
}

/** Builds the real environment for a session folder: git and file-system lookups the rules need. */
function makeEnv(root) {
  const git = (args, dir) => { try { return cp.execFileSync("git", args, { cwd: dir || root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"], timeout: 5000 }) } catch (e) { return "" } }
  return {
    root,
    claimed: claimedPath(path.basename(root)),
    branch: () => git(["branch", "--show-current"]).trim(),
    isDir: (p) => { try { return fs.statSync(path.resolve(root, p)).isDirectory() } catch (e) { return false } },
    repoRoot: (abs) => {
      for (let d = path.dirname(abs); ; d = path.dirname(d)) {
        if (fs.existsSync(path.join(d, ".git"))) return d
        if (d === path.dirname(d)) return null
      }
    },
    isFile: (p) => { try { return fs.statSync(p).isFile() } catch (e) { return false } },
    changed: (p, dir) => git(["status", "--porcelain", "--", p], dir).trim() !== "",
    protectedChanged: () => git(["status", "--porcelain", "--", ...PROTECTED]).trim() !== "",
    leinRunning: (dir) => {
      try {
        return fs.readdirSync("/proc").filter((p) => /^\d+$/.test(p) && Number(p) !== process.pid).some((p) => {
          try {
            const cmd = fs.readFileSync(`/proc/${p}/cmdline`, "utf8")
            if (!/leiningen|clojure\.main/.test(cmd)) return false
            const c = fs.readlinkSync(`/proc/${p}/cwd`)
            return c === dir || c.startsWith(dir + path.sep)
          } catch (e) { return false }
        })
      } catch (e) { return false }
    },
    riskyConfig: (dir) => git(["config", "--list", "--name-only"], dir).split("\n").map((k) => k.trim().toLowerCase()).filter((k) => RISKY_GIT_CONFIG.some((r) => r.test(k))),
  }
}

/** True when the folder is a checkout whose origin is codeGlaze/orcpub. */
function inScope(root) {
  try { return /codeglaze\/orcpub(\.git)?$/i.test(cp.execFileSync("git", ["remote", "get-url", "origin"], { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"], timeout: 5000 }).trim()) } catch (e) { return false }
}

/** Writes the canary: epoch, the plugin's own hash, and whether Cline's command auto-approve is on. */
function canary() {
  const dir = path.join(os.homedir(), ".cline", "orcpub-guard")
  let auto = "unknown"
  try {
    const s = JSON.parse(fs.readFileSync(path.join(os.homedir(), ".cline", "data", "globalState.json"), "utf8")).autoApprovalSettings || {}
    const act = s.actions || s
    auto = act.executeSafeCommands ? "on" : "off"
  } catch (e) {}
  const hash = require("crypto").createHash("sha256").update(fs.readFileSync(__filename)).digest("hex")
  fs.mkdirSync(dir, { recursive: true })
  fs.writeFileSync(path.join(dir, "alive"), `${Math.floor(Date.now() / 1000)} ${hash} commands-auto-approve-${auto}\n`)
}

let workspaceRoot = null

/**
 * Finds the session folder: Cline's session file for the hook's conversationId (it records cwd),
 * else the workspace given to setup, else this process's folder. Returns {root, source}.
 */
function sessionRoot(ctx) {
  const id = ctx && ctx.snapshot && ctx.snapshot.conversationId
  if (id && /^[\w.-]+$/.test(id)) {
    try {
      const meta = JSON.parse(fs.readFileSync(path.join(os.homedir(), ".cline", "data", "sessions", id, id + ".json"), "utf8"))
      if (meta.cwd) return { root: meta.cwd, source: "session" }
    } catch (e) {}
  }
  if (workspaceRoot) return { root: workspaceRoot, source: "workspace" }
  return { root: process.cwd(), source: "process" }
}

/** Appends one line saying which tool name and folder source a hook saw (plan row 3, step 1). */
function capture(ctx, where) {
  try {
    const f = path.join(os.homedir(), ".cline", "orcpub-guard", "capture.log")
    const line = JSON.stringify({ t: new Date().toISOString(), where, tool: ctx && ctx.tool && ctx.tool.name, conversationId: ctx && ctx.snapshot && ctx.snapshot.conversationId, root: sessionRoot(ctx), workspaceRoot, cwd: process.cwd(), inputType: typeof (ctx && ctx.input) })
    fs.mkdirSync(path.dirname(f), { recursive: true }); fs.appendFileSync(f, line + "\n")
  } catch (e) {}
}

const COMMAND_TOOLS = new Set(["run_commands", "bash"])
const WRITE_TOOLS = new Set(["editor", "edit", "write", "apply_patch"])

/** Reads a hook context's input: a JSON string is parsed, anything else is returned as it is. */
function inputOf(ctx) {
  const x = ctx && ctx.input
  if (typeof x === "string") { try { return JSON.parse(x) } catch (e) { return x } }
  return x === undefined ? {} : x
}

/** Lists the file paths a patch text names (*** Add|Update|Delete File: p, *** Move to: p). */
function patchPaths(text) {
  return [...String(text).matchAll(/^\*\*\* (?:(?:Add|Update|Delete) File|Move to): *(.+?) *$/gm)].map((m) => m[1])
}

/**
 * Works out what a tool call would run or write. Args: tool (name or ""), input (as Cline passes
 * it). Returns {commands: [strings] | null, paths: [strings]}. Commands come from a string, an
 * array, {commands} or {command}; paths from any path-like key and from patch text.
 */
function extract(tool, input) {
  const asList = (v) => (typeof v === "string" ? [v] : Array.isArray(v) ? v.map(String) : null)
  const obj = input && typeof input === "object" && !Array.isArray(input) ? input : null
  if (COMMAND_TOOLS.has(tool) || (!tool && (typeof input === "string" || Array.isArray(input) || (obj && ("commands" in obj || "command" in obj))))) {
    const cmds = asList(input) || (obj && (asList(obj.commands) || asList(obj.command)))
    return { commands: cmds || [], paths: [] }
  }
  const paths = obj ? editorPaths(obj) : []
  const strings = []; const walk = (o) => { if (typeof o === "string") strings.push(o); else if (o && typeof o === "object") Object.values(o).forEach(walk) }
  walk(input); for (const t of strings) paths.push(...patchPaths(t))
  return { commands: null, paths: WRITE_TOOLS.has(tool) || !tool ? paths : [] }
}

/** The beforeTool hook: block, approve, or leave to Cline's dialog. Never throws. */
function beforeTool(ctx) {
  const tool = (ctx && ((ctx.tool && ctx.tool.name) || (ctx.toolCall && ctx.toolCall.name))) || ""
  const input = inputOf(ctx)
  try {
    capture(ctx, "beforeTool")
    const { root } = sessionRoot(ctx)
    if (!inScope(root)) return undefined
    const env = makeEnv(root)
    const x = extract(tool, input)
    if (x.commands) {
      if (!x.commands.length) return undefined
      let allRun = true
      for (const c of x.commands) {
        const r = decide(String(c), env)
        if (r.decision === "block") return { skip: true, reason: `orcpub guard: ${r.reason}` }
        if (r.decision !== "run") allRun = false
      }
      return allRun ? { policy: { autoApprove: true } } : undefined
    }
    if (x.paths.length) { const r = decideEdit(x.paths, env); if (r) return { skip: true, reason: `orcpub guard: ${r.reason}` } }
    if (WRITE_TOOLS.has(tool) && !x.paths.length) return { skip: true, reason: "orcpub guard: could not read which file this write targets; tell the owner" }
    return undefined
  } catch (e) {
    const text = JSON.stringify(input) || ""
    if (WRITE_TOOLS.has(tool) || (COMMAND_TOOLS.has(tool) && /\b(git|gh)\b/.test(text))) return { skip: true, reason: `orcpub guard error: ${e.message}; tell the owner` }
    return undefined
  }
}

module.exports = {
  name: "orcpub-guard",
  manifest: { capabilities: ["hooks"] },
  /** Optional setup Cline calls with {workspaceInfo}; keeps the workspace folder as a fallback root. */
  setup(api, info) { try { const w = info && info.workspaceInfo; workspaceRoot = (w && (w.rootPath || w.workspacePath || w.cwd)) || null } catch (e) {} },
  hooks: { beforeRun(ctx) { try { canary(); capture(ctx, "beforeRun") } catch (e) {} }, beforeTool },
  _test: { parse, decide, decideEdit, makeEnv, extract },
}

if (require.main === module && process.argv.includes("--self-test")) {
  process.exit(require("./orcpub-guard-selftest.js")(module.exports._test))
}
