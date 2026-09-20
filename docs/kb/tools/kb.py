#!/usr/bin/env python3
"""kb — answer "has this been looked at, and where?" without reading the corpus.

    kb find <query>     rank documents matching a word or phrase
    kb terms <doc>      the words that most distinguish one document
    kb vocab <prefix>   corpus vocabulary, to find the word you didn't know
    kb lint             check the KB for the things that actually rot
    kb lint --self-test prove each check can still fail

Why this exists. The KB is the group memory, and consulting it used to mean
reading docs/kb/topic-index.md (104KB, ~26k tokens) or docs/kb/README.md (40KB,
~10k tokens) into context. `kb find` answers the same question in a few hundred
bytes, ranked, and supports phrases -- which the generated index cannot, because
it is built from single words and says so in its own header.

Descriptions are read from each document's own H1 at query time. Nothing is
generated, so nothing goes stale, and there is no second copy to disagree with
the document.

`terms` and `vocab` exist for the case grep cannot serve: you know the concept
but not the word this corpus uses for it. `vocab` lists the vocabulary that
exists; `terms` shows what a document is about once you have found it. Both
reuse the tokeniser and TF-IDF in topic_index.py rather than reimplementing
them, so there is one definition of "topic word".

Honest limit: this is substring matching. It will not find "locale" from
"i18n". Neither will the generated index -- TF-IDF ranks only words that are
literally present -- so nothing is lost, but do not mistake either for
synonym search. `vocab` is the workaround: look up the corpus's own word first.
"""

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import topic_index as ti  # tokeniser, stoplist and TF-IDF live there  # noqa: E402

KB = ti.KB_DIR
MAX_HITS = 8
MAX_HEADINGS = 2
DESC_CHARS = 72
MAX_VOCAB = 20


def describe(text):
    """The document's own one-liner: its H1, else its opening sentence."""
    title = None
    for line in text.splitlines():
        if title is None:
            if line.startswith("# "):
                title = ti.re.sub(r"[*_`]", "", line[2:]).strip()
            continue
        s = line.strip()
        if not s or s.startswith(("#", ">", "|", "-", "*", "```")):
            continue
        return title or ti.re.sub(r"[*_`]", "", s).strip()
    return title


def corpus():
    """[(rel, text)] for every KB document."""
    return [(rel, ti.read(rel)) for rel in ti.docs()]


def cmd_find(query):
    q = query.lower()
    hits = []
    for rel, text in corpus():
        body = text.lower().count(q)
        named = q in rel.lower().replace("-", " ") or q in rel.lower()
        heads = [h for h in ti.headings(text) if q in h.lower()]
        if not (body or named or heads):
            continue
        hits.append(((100 if named else 0) + 10 * len(heads) + body, rel, body, heads, text))

    if not hits:
        print(f'no KB document contains "{query}"')
        near = [w for w in vocabulary() if q in w][:8]
        if near:
            print(f"  corpus vocabulary containing it: {', '.join(near)}")
        else:
            print(f"  try:  kb vocab {q[:4]}     (the corpus may use a different word)")
        return 1

    hits.sort(key=lambda h: (-h[0], h[1]))
    for _, rel, body, heads, text in hits[:MAX_HITS]:
        desc = describe(text) or ""
        if len(desc) > DESC_CHARS:
            desc = desc[:DESC_CHARS - 1] + "…"
        print(rel)
        if desc:
            print(f"    {desc}")
        if heads:
            more = f" (+{len(heads) - MAX_HEADINGS})" if len(heads) > MAX_HEADINGS else ""
            print("    " + " · ".join(f"§{h}" for h in heads[:MAX_HEADINGS]) + more)
        print(f"    {body} mention{'' if body == 1 else 's'}")
    if len(hits) > MAX_HITS:
        print(f'... {len(hits) - MAX_HITS} more (grep -ril "{query}" docs/kb/)')
    return 0


def cmd_terms(name):
    """What one document is about, by TF-IDF against the corpus."""
    ds = ti.docs()
    matches = [d for d in ds if d == name or Path(d).name == name or name in d]
    if not matches:
        print(f'no KB document matches "{name}"')
        return 1
    freqs = {rel: ti.frequencies(ti.words(ti.read(rel))) for rel in ds}
    for rel in matches[:3]:
        print(rel)
        print("    " + ", ".join(ti.distinctive_terms(freqs, len(ds), rel)))
    return 0


def vocabulary():
    """Every topic word in the corpus, most widely used first."""
    df = {}
    for rel in ti.docs():
        for w in set(ti.words(ti.read(rel))):
            df[w] = df.get(w, 0) + 1
    return [w for w, _ in sorted(df.items(), key=lambda p: (-p[1], p[0]))]


def cmd_vocab(prefix):
    p = prefix.lower()
    words = [w for w in vocabulary() if p in w]
    if not words:
        print(f'no corpus vocabulary contains "{prefix}"')
        return 1
    print(", ".join(words[:MAX_VOCAB]))
    if len(words) > MAX_VOCAB:
        print(f"... {len(words) - MAX_VOCAB} more")
    return 0


def reachable():
    """Docs reachable from README.md by following .md links, transitively.

    Transitive on purpose. docs/kb/rescued/ is linked from rescued/README.md,
    which README.md links -- reachable, just not directly. The coverage test
    checks direct linkage by basename and has been red on those three files
    since they were added, which is what a false failure does to a guard.
    """
    seen, queue = set(), ["README.md"]
    while queue:
        rel = queue.pop()
        if rel in seen:
            continue
        seen.add(rel)
        f = KB / rel
        if not f.is_file():
            continue
        base = Path(rel).parent
        for target in re.findall(r"\]\(([^)#]+\.md)[^)]*\)", f.read_text(encoding="utf-8")):
            if target.startswith(("http://", "https://", "/")):
                continue
            try:
                nxt = str((KB / base / target).resolve().relative_to(KB.resolve()))
            except ValueError:
                continue
            if nxt not in seen:
                queue.append(nxt)
    return seen


def cmd_lint():
    """The things that actually rot. Quiet when clean, specific when not."""
    problems = []

    ds = ti.docs()
    for rel in ds:
        text = ti.read(rel)
        h1 = next((l[2:].strip() for l in text.splitlines() if l.startswith("# ")), None)
        if not h1:
            problems.append(f"{rel}: no H1 — kb find has no description to show")

    reach = reachable()
    for rel in ds:
        if rel not in reach:
            problems.append(f"{rel}: not reachable from README.md — nobody will find it")

    if ti.OUT_FILE.exists() and ti.OUT_FILE.read_text(encoding="utf-8") != ti.render():
        problems.append("topic-index.md is out of date — docs/kb/tools/topic-index.sh")

    if problems:
        for p in problems:
            print(p)
        print(f"\n{len(problems)} problem{'' if len(problems) == 1 else 's'}")
        return 1
    print(f"kb lint: clean ({len(ds)} documents)")
    return 0


def cmd_self_test():
    """Prove every lint check can FAIL. A check that only ever passes is worth nothing.

    This exists because of a real mistake. The first version of the reachability
    check resolved link targets against the working directory instead of docs/kb,
    so relative_to threw on every link and it reported all 135 documents as
    unreachable. That was loud. The opposite mistake -- a check that silently
    never fires -- is far harder to notice, because a green run looks exactly
    like a healthy tree.

    So each check runs against a fixture built to break it, and must complain. A
    clean fixture must pass. If any check stops discriminating, this fails.
    """
    import contextlib
    import io as _io
    import shutil
    import tempfile

    def build(tmp):
        """A minimal, healthy KB: a README linking one document."""
        kb = Path(tmp)
        (kb / "README.md").write_text("# Index\n\n- [good.md](good.md)\n", encoding="utf-8")
        (kb / "good.md").write_text("# A real title\n\nbody text here\n", encoding="utf-8")
        return kb

    @contextlib.contextmanager
    def pointed_at(kb):
        """Point both modules' KB globals at a fixture, then put them back."""
        global KB
        saved = (KB, ti.KB_DIR, ti.OUT_FILE)
        KB, ti.KB_DIR, ti.OUT_FILE = kb, kb, kb / "topic-index.md"
        try:
            yield
        finally:
            KB, ti.KB_DIR, ti.OUT_FILE = saved

    def add_bad(kb):
        (kb / "bad.md").write_text("no heading at all\n", encoding="utf-8")
        (kb / "README.md").write_text(
            "# Index\n\n- [good.md](good.md)\n- [bad.md](bad.md)\n", encoding="utf-8")

    def add_orphan(kb):
        (kb / "orphan.md").write_text("# Orphan\n\nbody\n", encoding="utf-8")

    def stale_index(kb):
        (kb / "topic-index.md").write_text("stale\n", encoding="utf-8")

    cases = [
        ("clean fixture passes",        None,        0, None),
        ("doc with no H1",              add_bad,     1, "no H1"),
        ("doc unreachable from README", add_orphan,  1, "not reachable"),
        ("stale topic-index",           stale_index, 1, "out of date"),
    ]

    failures = []
    for name, mutate, want_rc, want_msg in cases:
        tmp = tempfile.mkdtemp()
        try:
            kb = build(tmp)
            with pointed_at(kb):
                (kb / "topic-index.md").write_text(ti.render(), encoding="utf-8")
                if mutate:
                    mutate(kb)
                    if mutate is not stale_index:
                        # a new doc also makes the index stale; regenerate so the
                        # case under test is the only thing that can fire
                        (kb / "topic-index.md").write_text(ti.render(), encoding="utf-8")
                buf = _io.StringIO()
                with contextlib.redirect_stdout(buf):
                    rc = cmd_lint()
                out = buf.getvalue()
            if rc != want_rc:
                failures.append("%s: expected exit %d, got %d\n%s" % (name, want_rc, rc, out))
            elif want_msg and want_msg not in out:
                failures.append("%s: expected %r in output, got:\n%s" % (name, want_msg, out))
            else:
                print("  ok   %s" % name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    if failures:
        print()
        for f in failures:
            print("  FAIL %s" % f)
        print("\n%d check(s) no longer discriminate" % len(failures))
        return 1
    print("\nkb lint --self-test: all %d checks discriminate" % len(cases))
    return 0


USAGE = "usage: kb find <query> | kb terms <doc> | kb vocab <prefix> | kb lint [--self-test]"


def main(argv):
    if not KB.is_dir():
        sys.stderr.write("error: run from the repository root\n")
        return 2
    if argv and argv[0] == "lint":
        return cmd_self_test() if "--self-test" in argv else cmd_lint()
    if len(argv) < 2:
        sys.stderr.write(USAGE + "\n")
        return 2
    cmd, rest = argv[0], " ".join(argv[1:])
    if cmd == "find":
        return cmd_find(rest)
    if cmd == "terms":
        return cmd_terms(rest)
    if cmd == "vocab":
        return cmd_vocab(rest)
    sys.stderr.write(USAGE + "\n")
    return 2


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except BrokenPipeError:
        # `kb find x | head` closes the pipe early. Without this Python prints a
        # traceback on the way out, which makes a working command look broken.
        # Redirect stdout to devnull so the interpreter's own flush at exit does
        # not raise a second time, then exit quietly.
        import os
        os.dup2(os.open(os.devnull, os.O_WRONLY), sys.stdout.fileno())
        sys.exit(0)
