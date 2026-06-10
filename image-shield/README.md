# 🛡️ Image Shield

A small, **fully self-contained** single-page app for preparing images before you
publish them online. It lets you:

1. **Select** an image from your local machine (click or drag-and-drop).
2. **Resize** it (scale %, exact dimensions with aspect lock, or fit-longest-edge).
3. **Change format & quality** (JPEG / WebP quality, or lossless PNG).
4. **Apply adversarial "cloaking"** — Glaze-style and Nightshade-style perturbations
   intended to make the image harder for AI models to scrape, mimic, and train on.

Everything happens **locally in your browser using the Canvas API**. Your image is
never uploaded to any server.

## Running it

It's a static app with **zero dependencies and no build step**. Open the folder and:

```bash
# Option A: just open the file
open index.html            # macOS
xdg-open index.html        # Linux

# Option B: serve it (recommended so the File/Canvas APIs behave consistently)
cd image-shield
python3 -m http.server 8000
# then visit http://localhost:8000
```

It is deliberately **siloed** from the rest of the OrcPub project: it shares no code,
no build tooling (Leiningen/ClojureScript), and no dependencies. You can copy the
`image-shield/` folder anywhere and it will still work.

## Files

| File          | Purpose                                                        |
|---------------|----------------------------------------------------------------|
| `index.html`  | Markup and the layout of the controls / preview.               |
| `styles.css`  | Styling (dark UI).                                             |
| `app.js`      | All logic: load, resize, cloak, encode, download.              |

## How the cloaking works (and its honest limits)

> **⚠️ Read this.** **Glaze** and **Nightshade** are real research tools from the
> University of Chicago. Glaze adds perturbations that protect an artist's *style*
> from being mimicked; Nightshade *poisons* images so that models which train on them
> learn incorrect concept associations. **Both real tools rely on trained neural
> networks and meaningful compute and cannot run as a tiny in-browser script.**

What this app implements are **approximations** of the *ideas* behind those tools,
done with classic spatial-domain image processing:

- **Glaze-style (protect style):** decorrelated high-frequency per-pixel hash noise
  that targets the fine texture signal which style-transfer / style-mimicry models
  latch onto.
- **Nightshade-style (poison concept):** a smooth low-frequency sinusoidal lattice
  that biases broad color/feature statistics, nudging an image's global "concept"
  representation.
- **Perceptual masking (optional):** a Sobel-style luma edge map is used to push more
  perturbation into busy/textured regions and less into flat areas, so the effect is
  harder to see while remaining present where it does the most disruption.
- **Reproducibility:** perturbations are driven by a seedable PRNG (`mulberry32`), so
  the same seed + settings produce the same output.

**These approximations are not equivalent to the official tools** and should not be
relied on as a substitute for them. They can raise the cost of casual scraping and
degrade naive feature extraction, but a determined adversary with the right models can
likely reduce their effect. For serious protection, use the official
[Glaze](https://glaze.cs.uchicago.edu/) and
[Nightshade](https://nightshade.cs.uchicago.edu/) releases — ideally **in addition**
to a pass like this, not instead of it.

## Tips

- Apply cloaking **after** you've settled on the final resize, since resampling can
  weaken perturbations.
- Lossy recompression (JPEG/WebP) also weakens perturbations; if protection matters
  most, prefer PNG or a high quality value.
- Higher **Intensity** = stronger protection but more visible artifacts. Find the
  highest value you can tolerate visually.
