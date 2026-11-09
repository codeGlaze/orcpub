#!/usr/bin/env python3
"""Shrink a Claude Code session transcript (.jsonl) to its skeleton.

Keeps: every user message, Claude's own text and (shortened) thinking, each
tool call's key input, and a one-line summary of each tool result.
Drops: images, file contents, test logs and other bulk tool output.
Redacts: tokens, keys, passwords and credentials in URLs (see SECRET_PATTERNS).
Read the output before sharing it anyway: no pattern list catches everything.

Usage:
    python3 trim_transcript.py SESSION.jsonl > skeleton.jsonl
    python3 trim_transcript.py SESSION.jsonl --text > skeleton.txt   # readable
    python3 trim_transcript.py SESSION.jsonl --text --since 2026-11-01 > recent.txt
    add --talk to keep only what the owner and Claude said (no tool calls): far smaller
    add --owner to keep only the owner's own messages
    add --flagged to keep the owner's messages plus only those Claude messages that read like a
        correction, trap, decision or open item (see MARKERS)
Always dropped: subagent side-chains, hook/meta entries, compaction summaries, system reminders.
Stdlib only. Lines it cannot parse are skipped and counted on stderr.
"""
import json
import re
import sys

# Secrets are replaced before anything is written. Bare 40-hex strings are left alone (they are
# usually git commit ids) unless a token-ish word sits just before them.
SECRET_PATTERNS = [
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", re.S),
    re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"),
    re.compile(r"\bsk-(?:ant-)?[A-Za-z0-9_-]{20,}"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"\bxox[abpr]-[A-Za-z0-9-]{10,}"),
    re.compile(r"(?i)(authorization:\s*(?:token|bearer|basic)\s+)[^\s\"']+"),
    re.compile(r"(?i)\b(bearer\s+)[A-Za-z0-9._~+/=-]{16,}"),
    re.compile(r"(?i)(\b[A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_?KEY|PRIVATE_KEY|SIGNATURE)[A-Z0-9_]*\s*[=:]\s*[\"']?)[^\s\"']{6,}"),
    re.compile(r"(?i)((?:token|password|secret|apikey|api_key)[\s\"':=]+)[A-Fa-f0-9]{32,}"),
    re.compile(r"(https?://[^\s:/@]+:)[^\s@/]+(@)"),
]


NOISE = re.compile(r"<(system-reminder|local-command-stdout|local-command-stderr|command-message|command-args|bash-stdout|bash-stderr)>.*?</\1>", re.S)
MARKERS = re.compile(r"(?i)\b(actually|turns out|i was wrong|wrong about|mistake|instead of|not what|trap|gotcha|careful|never |always |don't|do not|open question|still open|unverified|not checked|decid|reverted|regress|broke|root cause)")
PASTE_CHARS = 1500   # a user text longer than this is mostly pasted output: keep its start


def denoise(s):
    return NOISE.sub("", s).strip()


def redact(s):
    if not isinstance(s, str):
        return s
    for pat in SECRET_PATTERNS:
        if pat.groups >= 2:
            s = pat.sub(lambda m: m.group(1) + "[REDACTED]" + m.group(2), s)
        elif pat.groups == 1:
            s = pat.sub(lambda m: m.group(1) + "[REDACTED]", s)
        else:
            s = pat.sub("[REDACTED]", s)
    return s

RESULT_CHARS = 240     # kept from the start of each tool result
THINK_CHARS = 1200     # kept from each thinking block
INPUT_CHARS = 300      # kept from each tool input field
KEY_INPUTS = ("command", "file_path", "path", "pattern", "query", "url",
              "description", "prompt", "old_string", "new_string", "skill")


def clip(s, n):
    s = redact(s if isinstance(s, str) else json.dumps(s, ensure_ascii=False))
    return s if len(s) <= n else s[:n] + f"… [+{len(s) - n} chars]"


def result_text(content):
    """Flatten a tool_result's content; images become a marker."""
    if isinstance(content, str):
        return content
    parts = []
    for c in content or []:
        if isinstance(c, dict):
            if c.get("type") == "text":
                parts.append(c.get("text", ""))
            elif c.get("type") == "image":
                parts.append("[image]")
    return "\n".join(parts)


def blocks_of(msg):
    content = msg.get("content")
    if isinstance(content, str):
        return [{"type": "text", "text": content}]
    return content if isinstance(content, list) else []


def trim(entry):
    msg = entry.get("message")
    if not isinstance(msg, dict):
        return None
    if entry.get("isMeta") or entry.get("isCompactSummary") or entry.get("isSidechain"):
        return None
    role = msg.get("role") or entry.get("type")
    out = []
    for b in blocks_of(msg):
        if not isinstance(b, dict):
            continue
        t = b.get("type")
        if t == "text" and denoise(b.get("text", "")):
            txt = denoise(b["text"])
            if txt.startswith("This session is being continued from a previous conversation"):
                continue
            if role == "user" and len(txt) > PASTE_CHARS:
                txt = txt[:PASTE_CHARS] + f"… [pasted, +{len(txt) - PASTE_CHARS} chars]"
            out.append({"k": "text", "v": redact(txt)})
        elif t == "thinking" and b.get("thinking"):
            out.append({"k": "think", "v": clip(b["thinking"], THINK_CHARS)})
        elif t == "tool_use":
            inp = b.get("input") or {}
            keep = {k: clip(inp[k], INPUT_CHARS) for k in KEY_INPUTS if k in inp}
            out.append({"k": "call", "tool": b.get("name"), "id": b.get("id"), "in": keep})
        elif t == "tool_result":
            full = result_text(b.get("content"))
            out.append({"k": "result", "id": b.get("tool_use_id"),
                        "err": bool(b.get("is_error")), "len": len(full),
                        "v": clip(full, RESULT_CHARS)})
    if not out:
        return None
    return {"ts": entry.get("timestamp"), "role": role,
            "sidechain": bool(entry.get("isSidechain")), "b": out}


def as_text(rec):
    lines = []
    who = {"user": "USER", "assistant": "CLAUDE"}.get(rec["role"], rec["role"])
    for b in rec["b"]:
        if b["k"] == "text":
            lines.append(f"[{who}] {b['v']}")
        elif b["k"] == "think":
            lines.append(f"[THINK] {b['v']}")
        elif b["k"] == "call":
            lines.append(f"  -> {b['tool']} {json.dumps(b['in'], ensure_ascii=False)}")
        elif b["k"] == "result":
            flag = " ERROR" if b["err"] else ""
            lines.append(f"  <-{flag} ({b['len']} chars) {b['v']}")
    return "\n".join(lines)


def main():
    """Read the transcript named on the command line and print its trimmed entries to stdout.

    Args: sys.argv (one .jsonl path, in any position, plus the flags in the module docstring).
    Returns: None; prints the usage text and exits on -h/--help or when no path is given.
    """
    args = sys.argv[1:]
    if "-h" in args or "--help" in args:
        sys.exit(__doc__)
    text_mode = "--text" in args
    owner = "--owner" in args
    flagged = "--flagged" in args
    talk = "--talk" in args or owner or flagged
    since_at = args.index("--since") + 1 if "--since" in args else None
    since = args[since_at] if since_at is not None and since_at < len(args) else ""
    paths = [a for i, a in enumerate(args) if not a.startswith("-") and i != since_at]
    if len(paths) != 1:
        sys.exit(__doc__)
    bad = kept = 0
    with open(paths[0], encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                rec = trim(json.loads(line))
            except (json.JSONDecodeError, AttributeError, TypeError):
                bad += 1
                continue
            if rec and since and (rec.get("ts") or "") < since:
                continue
            if rec and talk:
                rec["b"] = [b for b in rec["b"] if b["k"] == "text"]
                if rec["role"] == "assistant":
                    if owner:
                        rec["b"] = []
                    elif flagged:
                        rec["b"] = [b for b in rec["b"] if MARKERS.search(b["v"])]
                rec = rec if rec["b"] else None
            if rec:
                kept += 1
                print(as_text(rec) if text_mode else json.dumps(rec, ensure_ascii=False))
    print(f"kept {kept} entries, skipped {bad} unparseable lines", file=sys.stderr)


if __name__ == "__main__":
    main()
