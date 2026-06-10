// String-aware EDN top-level map splitter for orcbrew packs.
// Usage: node edn-split.js <file> list
//        node edn-split.js <file> extract "<Source Name>" <outfile>
const fs = require('fs');
const file = process.argv[2];
const mode = process.argv[3];
let s = fs.readFileSync(file, 'utf8');
if (s.charCodeAt(0) === 0xFEFF) s = s.slice(1); // strip BOM

// Find matching close brace for the '{' at index `open`, skipping strings.
function matchBrace(str, open) {
  let depth = 0, inStr = false;
  for (let i = open; i < str.length; i++) {
    const c = str[i];
    if (inStr) {
      if (c === '\\') { i++; continue; }
      if (c === '"') inStr = false;
    } else {
      if (c === '"') inStr = true;
      else if (c === '{') depth++;
      else if (c === '}') { depth--; if (depth === 0) return i; }
    }
  }
  return -1;
}

// Walk top-level: outer map starts at first '{'.
const outerOpen = s.indexOf('{');
// Iterate keys: a top-level source key is a "string" followed by its value (a map).
const sources = [];
let i = outerOpen + 1;
while (i < s.length) {
  // skip whitespace/commas
  while (i < s.length && /[\s,]/.test(s[i])) i++;
  if (s[i] === '}') break;
  if (s[i] !== '"') { i++; continue; }
  // read the source name string
  let j = i + 1, name = '';
  while (j < s.length) { if (s[j] === '\\') { name += s[j + 1]; j += 2; continue; } if (s[j] === '"') break; name += s[j]; j++; }
  // value: next '{'
  let k = j + 1; while (k < s.length && /[\s,]/.test(s[k])) k++;
  if (s[k] !== '{') { i = k; continue; }
  const close = matchBrace(s, k);
  const value = s.slice(k, close + 1);
  sources.push({ name, start: i, valStart: k, valEnd: close, size: value.length, hasClasses: /:orcpub\.dnd\.e5\/classes\s*\{\s*:/.test(value) });
  i = close + 1;
}

if (mode === 'list') {
  sources.sort((a, b) => a.size - b.size);
  for (const so of sources) console.log(`${(so.size/1024).toFixed(0)}KB  classes=${so.hasClasses?'YES':'no '}  ${so.name}`);
} else if (mode === 'extract') {
  const want = process.argv[4], out = process.argv[5];
  const so = sources.find((x) => x.name === want);
  if (!so) { console.error('source not found: ' + want); process.exit(1); }
  const value = s.slice(so.valStart, so.valEnd + 1);
  fs.writeFileSync(out, `{${JSON.stringify(so.name)}\n ${value}}\n`);
  console.log(`wrote ${out} (${(fs.statSync(out).size/1024).toFixed(0)}KB) for source "${so.name}"`);
}
