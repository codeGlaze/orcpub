#!/usr/bin/env node
// Generate docs/kb/topic-index.md. Node port of topic_index.py — same algorithm,
// byte-identical output. See topic_index.py for why the index exists and why there
// is more than one implementation.
//
//   node docs/kb/tools/topic_index.js [--check]
//
// Prefer the dispatcher, which picks whatever runtime is present:
//   docs/kb/tools/topic-index.sh [--check]

'use strict';
const fs = require('fs');
const path = require('path');

const KB_DIR = 'docs/kb';
const OUT_FILE = path.join(KB_DIR, 'topic-index.md');
const TOP_TERMS = 18;

const STOP = new Set(`the and that this with for not but are was were has have had which from into
when what where how why it its a an of to in on is as at by or be so if than then there here one
two can cannot does doing done would should could must will now still only also any all every each
same other more most less just like who whom they them their you your we our us i me my he she his
her him no yes doc docs md see note notes used using use uses new old first second test tests code
line lines file files case cases thing things way ways well much many some such over under after
before because since while`.split(/\s+/));

// ASCII-only lowercasing on purpose — see the note in topic_index.py.
const asciiLower = (s) => s.replace(/[A-Z]/g, (c) => String.fromCharCode(c.charCodeAt(0) + 32));

function walk(dir) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(p));
    else if (e.isFile() && e.name.endsWith('.md') &&
             e.name !== 'README.md' && e.name !== 'topic-index.md') out.push(p);
  }
  return out;
}

const docs = () =>
  walk(KB_DIR).map((p) => path.relative(KB_DIR, p).split(path.sep).join('/')).sort();

const read = (rel) => fs.readFileSync(path.join(KB_DIR, rel), 'utf8');

function headings(text) {
  const out = [];
  for (const line of text.split('\n')) {
    const m = /^#{2,3}\s+(.+?)\s*$/.exec(line);
    if (!m) continue;
    const h = m[1]
      .replace(/`([^`]*)`/g, '$1')
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      .replace(/[*_]{1,2}/g, '')
      .trim();
    if (h && !out.includes(h)) out.push(h);
  }
  return out;
}

function words(text) {
  return asciiLower(text.replace(/```[\s\S]*?```/g, ' ').replace(/`[^`]*`/g, ' '))
    .split(/[^a-z0-9-]+/)
    .filter((w) => w.length >= 3 && !STOP.has(w));
}

function frequencies(items) {
  const f = new Map();
  for (const i of items) f.set(i, (f.get(i) || 0) + 1);
  return f;
}

function distinctiveTerms(freqs, nDocs, rel, k = TOP_TERMS) {
  const tf = freqs.get(rel);
  const scored = [];
  for (const [w, c] of tf) {
    let df = 0;
    for (const other of freqs.values()) if (other.has(w)) df++;
    scored.push([w, c * Math.log(nDocs / Math.max(1, df))]);
  }
  scored.sort((a, b) => (b[1] - a[1]) || (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
  return scored.slice(0, k).map((p) => p[0]).sort();
}

const termsFromName = (rel) => {
  const stem = rel.endsWith('.md') ? rel.slice(0, -3) : rel;
  return [stem, stem.replace(/-/g, ' ')];
};

function render() {
  const ds = docs();
  const texts = new Map(ds.map((rel) => [rel, read(rel)]));
  const freqs = new Map(ds.map((rel) => [rel, frequencies(words(texts.get(rel)))]));
  const n = ds.length;

  const head =
    '# Topic index — what has already been looked at\n\n' +
    '**GENERATED — do not edit.** `docs/kb/tools/topic-index.sh`\n\n' +
    '## Grep the corpus first\n\n' +
    '```\ngrep -ril "<term>" docs/kb/\n```\n\n' +
    '**That is the search.** This file is for orientation — what each document is about, and\n' +
    'which one owns a topic — not for recall. Measured against fourteen realistic queries the\n' +
    'corpus answered **all fourteen**; this index answered **nine**. It cannot match multi-word\n' +
    'phrases (`import conflict`, `spell list`) because it is built from single words, and a\n' +
    'topic mentioned once loses its place to one discussed throughout. Use it to find the right\n' +
    'document, then read that document; use grep to find out whether anyone has been there.\n\n' +
    'Each document is listed with its filename (hyphenated **and** spaced, because queries are\n' +
    'typed with spaces), the words that most distinguish it from the rest of the corpus, and\n' +
    'every section heading it contains.\n\n' +
    '---\n\n';

  const blocks = ds.map((rel) =>
    `## ${rel}\n\n` +
    `_${termsFromName(rel).join(' · ')}_\n\n` +
    `**topics:** ${distinctiveTerms(freqs, n, rel).join(', ')}\n\n` +
    headings(texts.get(rel)).map((h) => `- ${h}`).join('\n') + '\n');

  return head + blocks.join('\n') + '\n';
}

function main(argv) {
  if (!fs.existsSync(KB_DIR)) {
    process.stderr.write(`error: ${KB_DIR} not found — run from the repository root\n`);
    return 2;
  }
  const content = render();
  const ds = docs();
  const nHeadings = ds.reduce((a, rel) => a + headings(read(rel)).length, 0);

  if (argv.includes('--check')) {
    const current = fs.existsSync(OUT_FILE) ? fs.readFileSync(OUT_FILE, 'utf8') : null;
    if (current === content) {
      console.log(`topic-index.md is up to date (${ds.length} documents, ${nHeadings} headings)`);
      return 0;
    }
    process.stderr.write(
      'topic-index.md is OUT OF DATE — regenerate with docs/kb/tools/topic-index.sh\n');
    return 1;
  }

  fs.writeFileSync(OUT_FILE, content);
  console.log(`wrote ${OUT_FILE} (${ds.length} documents, ${nHeadings} headings)`);
  return 0;
}

process.exit(main(process.argv.slice(2)));
