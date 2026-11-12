// Curates each recoloured sigil in argv[2]: drops shapes under 30% of the icon, gives every line
// 0.3 pt at a 10 pt print size, pads the viewBox, and writes curated PNGs at 512 px
// (argv[3]/a/*.png) plus a comparison sheet (argv[3]/sheet.png). Why: pdf-generation-architecture.md.
const { chromium } = require('playwright');
const fs = require('fs'); const path = require('path');
const LEVELS = { a: 0.30 };          // keep shapes at least this fraction of the icon's size
const PRINT_PT = 10, LINE_PT = 0.3;           // icon size and line weight as printed
(async () => {
  const [src, out] = process.argv.slice(2);
  const files = fs.readdirSync(src).filter(f => f.endsWith('.svg')).sort();
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 400 } });
  const curated = {};
  for (const f of files) {
    const svg = fs.readFileSync(path.join(src, f), 'utf8').replace(/<\?xml[^>]*>|<!DOCTYPE[^>]*>/g, '');
    curated[f] = {};
    for (const [lv, frac] of Object.entries(LEVELS)) {
      await page.setContent(`<div id="h" style="width:512px;height:512px">${svg}</div>`);
      const res = await page.evaluate(([frac, linePx]) => {
        const root = document.querySelector('#h svg');
        root.setAttribute('width', '512'); root.setAttribute('height', '512');
        const box = root.getBoundingClientRect();
        const size = Math.max(box.width, box.height);
        let kept = 0, dropped = 0;
        for (const el of [...root.querySelectorAll('path,circle,ellipse,rect,line,polyline,polygon')]) {
          if (el.closest('defs')) continue;
          const b = el.getBoundingClientRect();
          const stroked = getComputedStyle(el).stroke !== 'none';
          if (Math.max(b.width, b.height) < frac * size) { el.remove(); dropped++; continue; }
          kept++;
          if (stroked) { const m = el.getCTM(); const k = Math.sqrt(Math.abs(m.a*m.d - m.b*m.c)) || 1; el.style.strokeWidth = (linePx / k) + 'px'; }
        }
        const vb = root.viewBox.baseVal, pad = Math.max(vb.width, vb.height) * 0.04;
        root.setAttribute('viewBox', `${vb.x - pad} ${vb.y - pad} ${vb.width + 2 * pad} ${vb.height + 2 * pad}`);
        return { kept, dropped, svg: root.outerHTML };
      }, [frac, 512 / PRINT_PT * LINE_PT]);
      curated[f][lv] = res;
    }
  }
  // curated PNGs at 512 px
  for (const lv of Object.keys(LEVELS)) {
    fs.mkdirSync(path.join(out, lv), { recursive: true });
    for (const f of files) {
      await page.setContent(`<div id="c" style="width:512px;height:512px;background:transparent">${curated[f][lv].svg}</div>`);
      await page.locator('#c').screenshot({ path: path.join(out, lv, f.replace('.svg', '.png').toLowerCase().replace(/_/g, '-')), omitBackground: true });
    }
  }
  // review sheet: per sigil, original / a / b at 160 px, then the same at true print size (300 dpi)
  const printPx = Math.round(PRINT_PT / 72 * 300);
  const orig = f => fs.readFileSync(path.join(src, f), 'utf8').replace(/<\?xml[^>]*>|<!DOCTYPE[^>]*>/g, '');
  const cell = (svg, px) => `<div style="width:${px}px;height:${px}px">${svg.replace(/<svg /, `<svg width="${px}" height="${px}" `)}</div>`;
  const rows = files.map(f => `<tr><td style="font:12px sans-serif;padding-right:12px">${f.replace('DMV_Sigil_','').replace('.svg','')}<br><span style="color:#888">kept ${curated[f].a.kept} shapes</span></td>
    <td>${cell(orig(f),160)}</td><td>${cell(curated[f].a.svg,160)}</td>
    <td style="padding-left:24px">${cell(orig(f),printPx)}</td><td>${cell(curated[f].a.svg,printPx)}</td></tr>`).join('');
  await page.setViewportSize({ width: 1100, height: 200 });
  await page.setContent(`<body style="margin:16px;background:white;font:13px sans-serif">
    <table cellspacing="10"><tr><th></th><th>original</th><th>curated A (shapes &ge; 30%)</th>
    <th colspan="2" style="padding-left:24px">same, true print size (${PRINT_PT} pt at 300 dpi)</th></tr>${rows}</table></body>`);
  await page.screenshot({ path: path.join(out, 'sheet.png'), fullPage: true });
  console.log(files.map(f => `${f}: A kept ${curated[f].a.kept} dropped ${curated[f].a.dropped}`).join('\n'));
  await browser.close();
})();
