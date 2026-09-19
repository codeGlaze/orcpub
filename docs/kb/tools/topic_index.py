#!/usr/bin/env python3
"""Generate docs/kb/topic-index.md — a flat, greppable map from TOPIC to document.

Port of dev/orcpub/topic_index.clj. Same algorithm, same output; see that file for
why the index exists at all.

Why a port. The original is invoked as `lein with-profile +tools run -m
orcpub.topic-index`, and Leiningen is not installed in the containers agents actually
run in — so the one tool meant to keep the KB navigable was the one nobody could run.
The index went stale for that reason. The script needs nothing but the standard
library, in any language: it reads markdown and counts words.

Run it with the dispatcher, which picks whatever runtime is present:

    docs/kb/tools/topic-index.sh            # regenerate
    docs/kb/tools/topic-index.sh --check    # exit 1 if the index is out of date

Or directly:  python3 docs/kb/tools/topic_index.py

Two deliberate differences from the original Clojure:

  * Nested documents work. The original called .getName on a recursive file-seq,
    discarding the directory, then read docs/kb/<basename> — so it threw
    FileNotFoundException on anything under docs/kb/rescued/. Documents are keyed by
    their path relative to docs/kb.

  * Ties break deterministically. The original sorted by score alone, leaving equal
    scores in hash-map order, so two runs could disagree. Ties break on the word.
"""

import math
import re
import sys
from pathlib import Path

KB_DIR = Path("docs/kb")
OUT_FILE = KB_DIR / "topic-index.md"
TOP_TERMS = 18

STOP = {
    "the", "and", "that", "this", "with", "for", "not", "but", "are", "was", "were", "has", "have",
    "had", "which", "from", "into", "when", "what", "where", "how", "why", "it", "its", "a", "an",
    "of", "to", "in", "on", "is", "as", "at", "by", "or", "be", "so", "if", "than", "then", "there",
    "here", "one", "two", "can", "cannot", "does", "doing", "done", "would", "should", "could",
    "must", "will", "now", "still", "only", "also", "any", "all", "every", "each", "same", "other",
    "more", "most", "less", "just", "like", "who", "whom", "they", "them", "their", "you", "your",
    "we", "our", "us", "i", "me", "my", "he", "she", "his", "her", "him", "no", "yes", "doc", "docs",
    "md", "see", "note", "notes", "used", "using", "use", "uses", "new", "old", "first", "second",
    "test", "tests", "code", "line", "lines", "file", "files", "case", "cases", "thing", "things",
    "way", "ways", "well", "much", "many", "some", "such", "over", "under", "after", "before",
    "because", "since", "while",
}

HEADING_RE = re.compile(r"^#{2,3}\s+(.+?)\s*$")
FENCED_RE = re.compile(r"```.*?```", re.DOTALL)
INLINE_CODE_RE = re.compile(r"`[^`]*`")
WORD_SPLIT_RE = re.compile(r"[^a-z0-9-]+")

# ASCII-only lowercasing on purpose. str.lower() is Unicode-aware and the Clojure
# original used str/lower-case, which is LOCALE-aware -- on a Turkish JVM it folds
# "I" to dotless "i" and silently produces a different index. Document text is being
# tokenised, not read aloud. See docs/kb/locale-safety.md.
ASCII_LOWER = str.maketrans("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz")


def docs():
    """Every KB document, keyed by path relative to docs/kb, sorted."""
    return sorted(
        str(p.relative_to(KB_DIR))
        for p in KB_DIR.rglob("*.md")
        if p.is_file() and p.name != "README.md" and p.name != "topic-index.md"
    )


def read(rel):
    return (KB_DIR / rel).read_text(encoding="utf-8")


def headings(text):
    """Section headings (## and ###), stripped of inline code, links and emphasis."""
    out = []
    for line in text.splitlines():
        m = HEADING_RE.match(line)
        if not m:
            continue
        h = m.group(1)
        h = re.sub(r"`([^`]*)`", r"\1", h)
        h = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", h)
        h = re.sub(r"[*_]{1,2}", "", h)
        h = h.strip()
        if h and h not in out:
            out.append(h)
    return out


def words(text):
    """Topic words: code stripped, lowercased, >=3 chars, stopwords removed."""
    text = FENCED_RE.sub(" ", text)
    text = INLINE_CODE_RE.sub(" ", text)
    text = text.translate(ASCII_LOWER)
    return [w for w in WORD_SPLIT_RE.split(text) if len(w) >= 3 and w not in STOP]


def frequencies(items):
    f = {}
    for i in items:
        f[i] = f.get(i, 0) + 1
    return f


def distinctive_terms(freqs, n_docs, rel, k=TOP_TERMS):
    """Crude TF-IDF: the words characterising ONE document against the corpus."""
    tf = freqs[rel]
    df = {}
    for w in tf:
        df[w] = sum(1 for other in freqs.values() if w in other)
    scored = [(w, c * math.log(n_docs / max(1, df[w]))) for w, c in tf.items()]
    scored.sort(key=lambda p: (-p[1], p[0]))
    return sorted(w for w, _ in scored[:k])


def terms_from_name(rel):
    stem = rel[:-3] if rel.endswith(".md") else rel
    return [stem, stem.replace("-", " ")]


def render():
    ds = docs()
    texts = {rel: read(rel) for rel in ds}
    freqs = {rel: frequencies(words(t)) for rel, t in texts.items()}
    n = len(ds)

    parts = [
        "# Topic index — what has already been looked at\n\n",
        "**GENERATED — do not edit.** `docs/kb/tools/topic-index.sh`\n\n",
        "## Grep the corpus first\n\n",
        '```\ngrep -ril "<term>" docs/kb/\n```\n\n',
        "**That is the search.** This file is for orientation — what each document is about, and\n"
        "which one owns a topic — not for recall. Measured against fourteen realistic queries the\n"
        "corpus answered **all fourteen**; this index answered **nine**. It cannot match multi-word\n"
        "phrases (`import conflict`, `spell list`) because it is built from single words, and a\n"
        "topic mentioned once loses its place to one discussed throughout. Use it to find the right\n"
        "document, then read that document; use grep to find out whether anyone has been there.\n\n",
        "Each document is listed with its filename (hyphenated **and** spaced, because queries are\n"
        "typed with spaces), the words that most distinguish it from the rest of the corpus, and\n"
        "every section heading it contains.\n\n",
        "---\n\n",
    ]

    blocks = []
    for rel in ds:
        block = (
            f"## {rel}\n\n"
            f"_{' · '.join(terms_from_name(rel))}_\n\n"
            f"**topics:** {', '.join(distinctive_terms(freqs, n, rel))}\n\n"
            + "\n".join(f"- {h}" for h in headings(texts[rel]))
            + "\n"
        )
        blocks.append(block)

    return "".join(parts) + "\n".join(blocks) + "\n"


def main(argv):
    check = "--check" in argv
    if not KB_DIR.is_dir():
        sys.stderr.write(f"error: {KB_DIR} not found — run from the repository root\n")
        return 2

    content = render()
    ds = docs()
    n_headings = sum(len(headings(read(rel))) for rel in ds)

    if check:
        current = OUT_FILE.read_text(encoding="utf-8") if OUT_FILE.exists() else None
        if current == content:
            print(f"topic-index.md is up to date ({len(ds)} documents, {n_headings} headings)")
            return 0
        sys.stderr.write(
            "topic-index.md is OUT OF DATE — regenerate with docs/kb/tools/topic-index.sh\n"
        )
        return 1

    OUT_FILE.write_text(content, encoding="utf-8")
    print(f"wrote {OUT_FILE} ({len(ds)} documents, {n_headings} headings)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
