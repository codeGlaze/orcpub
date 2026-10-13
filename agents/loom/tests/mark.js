// Every PNG the Loom exports with the art in it carries the orcpub-art mark; silhouettes do not;
// and marking leaves the pixels exactly as they were.
const { chromium } = require('playwright'); const fs = require('fs'); const { execFileSync } = require('child_process');
const EXE = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));
let fails = 0; const check = (ok, m) => { console.log((ok ? 'PASS ' : 'FAIL ') + m); if (!ok) fails++; };
const open = async (p, files) => { await p.goto('file://' + process.env.LOOM); await p.setInputFiles('#file', files);
  await p.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 }); };
// {name: mark text or null} for each PNG in a zip
const marks = (zip) => JSON.parse(execFileSync('python3', ['-I', '-c', `
import zipfile, json, sys, struct
out = {}
for n in zipfile.ZipFile(sys.argv[1]).namelist():
    if not n.endswith('.png'): continue
    b = zipfile.ZipFile(sys.argv[1]).read(n); i = 8; m = None
    while i + 8 <= len(b):
        ln = struct.unpack('>I', b[i:i+4])[0]; t = b[i+4:i+8]
        if t == b'iTXt' and b[i+8:i+8+len('orcpub-art')+1] == b'orcpub-art\\0':
            m = b[i+8:i+8+ln].split(b'\\0', 5)[-1].decode('utf-8')
        if t in (b'IDAT', b'IEND'): break
        i += 12 + ln
    out[n] = m
print(json.dumps(out))`, zip]).toString());
const save = async (p, out) => { const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#save')]); await dl.saveAs(out); return out; };
(async () => { const b = await chromium.launch({ executablePath: EXE });
  const errs = [];
  const p = await (await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } })).newPage();
  p.on('pageerror', e => errs.push(String(e)));
  await open(p, process.env.PACKZIP);
  await p.fill('#ver', 'v9.9'); await p.fill('#artist-id', 'house-pack');
  const art = marks(await save(p, process.env.OUT + '/marked-pack.zip'));
  const names = Object.keys(art);
  check(names.length > 20, `the pack has its pieces (${names.length})`);
  check(names.every(n => art[n] === 'licensed; pack v9.9; artist house-pack'),
        'every piece carries the mark, with the pack version and artist: ' + JSON.stringify(art[names[0]]));
  // pixels: the same canvas encoded with and without the mark decodes identically
  const same = await p.evaluate(async () => {
    const c = document.createElement('canvas'); c.width = 64; c.height = 48; const g = c.getContext('2d');
    for (let i = 0; i < 300; i++) { g.fillStyle = `rgba(${i % 255},${(i * 7) % 255},${(i * 13) % 255},${(i % 10) / 10})`; g.fillRect(i % 64, (i * 3) % 48, 5, 4); }
    const plain = await bytesOf(c), marked = withArtMark(plain, 'licensed; test');
    const px = async (bytes) => { const bm = await createImageBitmap(new Blob([bytes], { type: 'image/png' }));
      const d = document.createElement('canvas'); d.width = bm.width; d.height = bm.height;
      const dg = d.getContext('2d'); dg.drawImage(bm, 0, 0); return Array.from(dg.getImageData(0, 0, bm.width, bm.height).data); };
    const a = await px(plain), m = await px(marked);
    return { grew: marked.length - plain.length, hasMark: hasArtMark(marked), plainMark: hasArtMark(plain),
             equal: a.length === m.length && a.every((v, i) => v === m[i]),
             tail: Array.from(plain.subarray(33)).every((v, i) => v === marked[marked.length - plain.length + 33 + i]) };
  });
  check(same.hasMark && !same.plainMark && same.grew > 0, 'the mark is added once, as its own chunk');
  check(same.tail, 'the image data after it is byte for byte what the browser encoded');
  check(same.equal, 'and the decoded pixels are identical');
  // prototype mode: silhouettes are not marked
  const p2 = await (await b.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } })).newPage();
  p2.on('pageerror', e => errs.push(String(e)));
  await p2.goto('file://' + process.env.LOOM); await p2.selectOption('#mode', 'prototype');
  await p2.setInputFiles('#file', process.env.PACKZIP);
  await p2.waitForFunction(() => !document.getElementById('save').disabled, null, { timeout: 120000 });
  const sil = marks(await save(p2, process.env.OUT + '/silhouette-pack.zip'));
  check(Object.values(sil).every(m => m === null), 'a prototype (silhouette) export carries no mark');
  check(errs.length === 0, 'no page errors ' + errs.join(' | '));
  await b.close(); process.exit(fails ? 1 : 0); })();
