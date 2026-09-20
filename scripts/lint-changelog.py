#!/usr/bin/env python3
"""Check the newest CHANGELOG.md release against the house style.

The rules are the mechanical half of docs/branch-changelog.template.md. They are
the ones a machine can judge; whether a bullet is worth writing at all is still a
person's call.

Every "## [Release]" section is checked. The older releases already comply, so
there is nothing to grandfather -- and a rule with no exemptions is one nobody has
to remember the shape of.

Run it locally the same way CI does:

    scripts/lint-changelog.py                  # check CHANGELOG.md
    scripts/lint-changelog.py --stats          # ... and print the length distribution
    scripts/lint-changelog.py path/to/file.md  # check some other file

Exit status is 0 when clean and 1 when anything is found, which is the whole
contract with CI: a step fails because the command it ran returned non-zero.
"""

import re
import sys
import statistics

DEFAULT_CHANGELOG = "CHANGELOG.md"

# A bullet may be detailed; it may not be a paragraph. The cap is deliberately
# generous: the median bullet in the Summer Patch is 34 words, so 80 is more than
# twice a typical entry and only catches genuine prose.
MAX_BULLET_WORDS = 80

# Prose is allowed in exactly one place, and only that much of it.
MAX_HIGHLIGHT_SENTENCES = 3

# Words that say a change was good without saying what it does.
JARGON = re.compile(
    r"\b(seamless\w*|robust\w*|comprehensive\w*|powerful\w*|streamlin\w*"
    r"|leverag\w*|delve|utilize|holistic|cutting-edge|game-chang\w*)\b",
    re.I,
)

BLOCK_LABELS = ("**Added**", "**Fixed**", "**Changed**", "**Highlights**")


def releases(lines):
    """(start, end, heading) for every release section, newest first."""
    heads = [i for i, l in enumerate(lines) if l.startswith("## [")]
    return [
        (s, heads[n + 1] if n + 1 < len(heads) else len(lines), lines[s].strip())
        for n, s in enumerate(heads)
    ]


def bullets(lines, start, end):
    """Each bullet as (line-number, joined-text).

    Folds in both kinds of continuation: a wrapped line, and a further indented
    paragraph after a blank line -- markdown renders that second paragraph as part
    of the list item, so it counts toward the bullet's length rather than reading
    as prose that escaped a bullet.
    """
    out, i = [], start
    while i < end:
        if lines[i].startswith("- "):
            text, j = lines[i], i + 1
            while j < end:
                if lines[j].startswith(("  ", "\t")) and lines[j].strip():
                    text += " " + lines[j].strip()
                    j += 1
                elif not lines[j].strip():
                    # A blank line only ends the bullet if what follows is not
                    # another indented paragraph belonging to it.
                    k = j
                    while k < end and not lines[k].strip():
                        k += 1
                    if k < end and lines[k].startswith(("  ", "\t")) and lines[k].strip():
                        j = k
                        continue
                    break
                else:
                    break
            out.append((i + 1, text[2:].strip()))
            i = j
        else:
            i += 1
    return out


def highlight_blocks(lines, start, end):
    """Each Highlights block as (line-number, section-name, joined-prose)."""
    out, section, i = [], "(release)", start
    while i < end:
        stripped = lines[i].strip()
        if stripped.startswith("###"):
            section = stripped.lstrip("#").strip()
        if stripped in ("**Highlights**", "### Highlights"):
            j, body = i + 1, []
            while j < end and not (
                lines[j].strip().startswith(BLOCK_LABELS)
                or lines[j].startswith(("###", "## ", "- "))
            ):
                if lines[j].strip():
                    body.append(lines[j].strip())
                j += 1
            out.append((i + 1, section, " ".join(body)))
            i = j
            continue
        i += 1
    return out


def prose_blocks(lines, start, end):
    """Runs of 2+ standalone prose lines: paragraphs outside a bullet or Highlights.

    A line is skipped when it is a bullet, an indented continuation of one, a
    heading, a bold block label, an HTML comment, a table row, or inside a fenced
    code block or a Highlights section -- so only genuine paragraphs are left.
    """
    out, current = [], []
    in_bullet = in_code = in_highlights = False
    for i in range(start, end):
        raw = lines[i]
        s = raw.strip()
        if s.startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            continue
        if s.startswith("###") or s in BLOCK_LABELS:
            in_highlights = "highlight" in s.lower()
            in_bullet = False
            continue
        if not s:
            if current:
                out.append(current)
                current = []
            nxt = next((lines[k] for k in range(i + 1, end) if lines[k].strip()), "")
            if not (in_bullet and nxt.startswith(("  ", "\t"))):
                in_bullet = False
            continue
        if s.startswith("- "):
            in_bullet = True
            continue
        if in_bullet and raw.startswith(("  ", "\t")):
            continue  # continuation paragraph -- counted by bullets(), not prose
        if s.startswith(("#", "<!--", "|", "**")):
            in_bullet = False
            continue
        if in_highlights:
            continue
        current.append((i + 1, s))
    if current:
        out.append(current)
    return [b for b in out if len(b) >= 2]


def sentences(text):
    return len(re.findall(r"[.!?](?:\s|$)", text))


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    CHANGELOG = args[0] if args else DEFAULT_CHANGELOG
    try:
        lines = open(CHANGELOG, encoding="utf-8").read().split("\n")
    except FileNotFoundError:
        print(f"::error file={CHANGELOG}::{CHANGELOG} not found")
        return 1

    sections = releases(lines)
    if not sections:
        print(f"OK - {CHANGELOG} has no release section to check.")
        return 0
    found = []
    all_bullets = []

    def err(line, msg):
        found.append((line, msg))
        print(f"::error file={CHANGELOG},line={line}::{msg}")

    for start, end, release in sections:
        bl = bullets(lines, start, end)
        all_bullets += bl

        for line, text in bl:
            words = len(text.split())
            if words > MAX_BULLET_WORDS:
                err(line, f"Bullet is {words} words (limit {MAX_BULLET_WORDS}). Say what "
                          f"changed and why it matters; the reasoning behind it belongs in "
                          f"the commit message or a KB doc, not here.")
            if text.count(";") >= 2:
                err(line, "Bullet joins several changes with semicolons. One change per "
                          "bullet - split it.")
            hit = JARGON.search(text)
            if hit:
                err(line, f"Jargon: {hit.group(0)!r}. Say what the change does instead.")

        for line, section, body in highlight_blocks(lines, start, end):
            n = sentences(body)
            if n > MAX_HIGHLIGHT_SENTENCES:
                err(line, f"Highlights for {section!r} is {n} sentences (limit "
                          f"{MAX_HIGHLIGHT_SENTENCES}). Prose is allowed here and nowhere "
                          f"else, and only this much of it.")

        for block in prose_blocks(lines, start, end):
            line = block[0][0]
            words = sum(len(t.split()) for _, t in block)
            err(line, f"Prose paragraph ({len(block)} lines, {words} words) outside a "
                      f"bullet. Only a Highlights block may carry prose - make this a "
                      f"bullet or move it to a KB doc.")

    bl = all_bullets
    if "--stats" in sys.argv and bl:
        w = sorted(len(t.split()) for _, t in bl)
        print(f"\n  {len(sections)} release(s): {len(w)} bullets, median {statistics.median(w):.0f}"
              f" words, p90 {w[int(len(w) * .9)]}, max {max(w)}")

    if found:
        print(f"\n{len(found)} changelog style problem(s). "
              f"House style: docs/branch-changelog.template.md")
        return 1
    print(f"OK - all {len(sections)} release(s) follow the changelog house style.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
