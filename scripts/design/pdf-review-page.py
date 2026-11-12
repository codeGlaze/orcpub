#!/usr/bin/env python3
"""Review page for PDF sheet mockups: reads <dir>/shots.json (from pdf-mockups.clj) and writes
<dir>/review.html, one Artifact-ready file with every shot inlined. Each shot shows a close-up
(its crop, in 150 dpi page pixels) with a button to see the whole page, so a phone can read it.

Usage: pdf-review-page.py <dir> "<title>" "<lead>" ["<html for the questions box>"]
shots.json: [{"id","tag","title","note","file","crop":[x,y,w,h]}, ...]; crop optional.
How and why: docs/kb/mockup-review-pages.md, "PDF sheets"."""
import base64, html, json, os, sys

PAGE_W, PAGE_H = 1275, 1650  # a Letter page rendered at 150 dpi

d, title, lead = sys.argv[1], sys.argv[2], sys.argv[3]
ask = sys.argv[4] if len(sys.argv) > 4 else ""
shots = json.load(open(os.path.join(d, "shots.json"), encoding="utf-8"))

def uri(f):
    return "data:image/png;base64," + base64.b64encode(open(os.path.join(d, "shots", f), "rb").read()).decode()

parts = []
for s in shots:
    x, y, w, h = s.get("crop") or [0, 0, PAGE_W, PAGE_H]
    box = "aspect-ratio:%d/%d" % (w, h)
    img = "width:%.3f%%;left:%.3f%%;top:%.3f%%" % (PAGE_W / w * 100, -x / w * 100, -y / h * 100)
    parts.append('''<section class="shot" id="%(id)s">
  <header><p class="eyebrow">%(tag)s</p><h2>%(title)s</h2></header>
  <div class="crop" style="%(box)s"><img src="%(src)s" alt="%(title)s" style="%(img)s"></div>
  <p class="note">%(note)s</p>
  <button class="toggle" type="button" id="t-%(id)s">Show the whole page</button>
</section>''' % {"id": s["id"], "tag": html.escape(s.get("tag", "")), "title": html.escape(s["title"]),
                 "note": html.escape(s.get("note", "")), "box": box, "img": img, "src": uri(s["file"])})

page = '''<title>%(title)s</title>
<meta name="claude:viewport-fit-sides" content="cover">
<style>
/* layout: one column of sheet close-ups, phone first; a button swaps to the whole page */
:root { --bg:#f3f1ec; --panel:#ffffff; --fg:#1d1f24; --muted:#5c616c; --line:#d9d5cc; --accent:#9a3b12;
  --display:"Trebuchet MS","Segoe UI",sans-serif; --body:"Segoe UI",system-ui,sans-serif; }
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { --bg:#171a20; --panel:#20242c; --fg:#ebe7df; --muted:#a4a9b3; --line:#343a45; --accent:#e48a5a; color-scheme:dark } }
:root[data-theme="dark"] { --bg:#171a20; --panel:#20242c; --fg:#ebe7df; --muted:#a4a9b3; --line:#343a45; --accent:#e48a5a; color-scheme:dark }
body { background:var(--bg); color:var(--fg); font-family:var(--body); font-size:16px; line-height:1.45;
  padding-left:max(16px, env(safe-area-inset-left, 0px)); padding-right:max(16px, env(safe-area-inset-right, 0px)); padding-block:20px 40px }
main { max-width:720px; margin:0 auto; display:grid; gap:28px }
h1 { font-family:var(--display); font-size:1.6rem; margin:0; text-wrap:balance }
.lead { color:var(--muted); margin:6px 0 0 }
.ask { background:var(--panel); border:1px solid var(--line); border-radius:8px; padding:12px 14px; display:grid; gap:6px }
.ask p { margin:0 } .ask strong { color:var(--accent) }
.shot { display:grid; gap:10px } .shot header { display:grid; gap:2px }
.eyebrow { font-family:var(--display); letter-spacing:.06em; text-transform:uppercase; color:var(--accent); margin:0; font-size:.8rem }
h2 { font-size:1.2rem; margin:0; text-wrap:balance }
.note { color:var(--muted); margin:0 }
.crop { position:relative; width:100%%; overflow:hidden; border:1px solid var(--line); border-radius:6px; background:#fff }
.crop img { position:absolute; max-width:none }
.shot.full .crop { aspect-ratio:%(pw)d/%(ph)d !important }
.shot.full .crop img { width:100%% !important; left:0 !important; top:0 !important }
.toggle { justify-self:start; font:inherit; font-size:.9rem; color:var(--fg); background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:6px 12px; cursor:pointer }
.toggle:focus-visible { outline:2px solid var(--accent); outline-offset:2px }
</style>
<main>
<header><h1>%(title)s</h1><p class="lead">%(lead)s</p></header>
%(ask)s
%(cards)s
</main>
<script>
document.querySelectorAll('.toggle').forEach(function (b) {
  b.addEventListener('click', function () {
    var s = b.closest('.shot'); var on = s.classList.toggle('full');
    b.textContent = on ? 'Show the close-up' : 'Show the whole page';
  });
});
</script>
''' % {"title": html.escape(title), "lead": html.escape(lead), "pw": PAGE_W, "ph": PAGE_H,
       "ask": ('<div class="ask">%s</div>' % ask) if ask else "", "cards": "\n".join(parts)}

out = os.path.join(d, "review.html")
open(out, "w", encoding="utf-8").write(page)
print("%s: %d KB (Artifact limit 16 MB)" % (out, len(page) // 1024))
