# Portrait compositor styles

The drawer, the builder's Portrait tab, the summary thumbnail and the artist credit are styled in
Garden, in `src/clj/orcpub/styles/portrait.clj`. They are compiled into the app's stylesheet
(`orcpub.styles.core/app`, last, so they win ties with the rules before them) by `lein garden once`.
`portrait.cljs` holds no CSS. `orcpub.styles.portrait-test` checks this.

## Values from data are CSS variables

A view sets only custom properties inline. The rules that use them live in Garden:

| Variable | Set on | Used for |
|---|---|---|
| `--pl-z` | each `.portrait-layer` | its place in the stack |
| `--pl-art` | `.portrait-layer-art`, `.portrait-layer-tinted` | the piece's image, as `url(...)` |
| `--pl-tint` | `.portrait-layer-tinted` | the colour multiplied under it |
| `--pl-fill` | anything with `.pl-fill` (swatches, presets, chips, dots) | its background |
| `--pl-logo` | `.pl-empty-hint`, `.pl-thumb-empty` | the debossed site logo, from `branding/logo-path` |
| `--lk-mark-color` | `.lk-mark` | a link mark's own colour (falls back to `--lk-dim`) |
| `--lk-icon` | `.lk-ico` | the service's icon, used as a mask |

`.pl-fill` comes last in the sheet so a swatch's colour wins over its own class's rules.

In Garden, a rule with more than eight properties is split over several maps. A larger map literal
is a hash map and would not keep its declarations in order, and the order matters wherever a
shorthand (`background`, `font`, `border`) is followed by one of its longhands.

## Why some rules are the way they are

**The credit face.** The baked credit is drawn on a canvas, and a canvas can only use faces the
document has loaded. Vollkorn is the face the PDF is set in, so the caption inside the exported
picture matches the sheet around it. The `@font-face` points at a subset first: the whole face is
357 KB to draw one short line, and the subset is Latin-1 plus the separators and quotes a name may
carry (197 glyphs, 26 KB, kerning kept). The full TTF is the fallback: it is on disk anyway for
PDFBox, and it covers a name the subset does not. The browser fetches it only when something draws
with it, which is an export. Regenerate the subset with fonttools and brotli:

```bash
pyftsubset resources/public/fonts/Vollkorn-Italic.ttf --flavor=woff2 \
  --unicodes='U+0020-007E,U+00A0-00FF,U+2013-2014,U+2018-2019,U+201C-201D' \
  --output-file=resources/public/fonts/Vollkorn-Italic-credit.woff2
```

**The light theme.** The drawer is mounted as a sibling of `content-page`, outside `.app`, so it
cannot inherit `.app.light-theme`; its rules hang off `.pl-root.light-theme`, and the drawer puts
the theme class on its own root. The launcher and the thumbnail pencil sit inside the builder, so
theirs hang off `.app.light-theme`. The light theme uses the app's own light colours (`#363636`
text, the `#33658A` of its `.form-button`) because it turns `.orange` near-black, and an amber
drawer would look pasted on. The portrait frame stays dark in both themes: what it holds is
character colours, pale skin and blonde hair among them, which a light frame would swallow.

**The empty slot.** The empty frame and thumbnail use a shallow top-down gradient, like the app's
other panels, and carry the site logo debossed: masked to about 5% white with a one-pixel shadow
under it. Only the empty states carry it, so a portrait never shows a logo through its transparent
parts. The logo comes from `branding/logo-path`, so a fork shows its own. In the big frame the logo
sits high and the "pick a layer" hint low, so neither muddies the other.

**The art column.** The frame fills the column, like every button and row under it, with the
frame's 4:5 ratio setting its height. Its border is inside the width (`box-sizing`), so it lines up
with them.

**The credit.** A small label, then each artist's name on a broken rule with their link marks as
its end caps, or centred below when they cannot balance (`credit-mark-layout`). A linked name has a
dotted underline at rest, drawn as a text decoration so it does not grow the box. When someone
reaches for it (mouse, keyboard or tap) the letters turn prismatic: a spectrum clipped to the
glyphs, drifting slowly, under a two-colour halo. Nothing moves at rest, and anyone who asked for
less motion gets the spectrum standing still. The halo is a `drop-shadow` filter, because with the
letters transparent a `text-shadow` would show through them.

**A planned piece.** A piece the illustrator has planned but not drawn is dashed, with no hover and
no pointer, so it does not look like a swatch you can press.
