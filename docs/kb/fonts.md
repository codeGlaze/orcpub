# Fonts are self-hosted

The app uses **Open Sans**, served from `resources/public/` rather than linked from Google Fonts.

## Why

The CDN link (`https://fonts.googleapis.com/css?family=Open+Sans`) was replaced because it:

- **fails outright in a sandboxed browser** — `ERR_CONNECTION_RESET`, which showed up as a spurious
  failure in the browser e2e suite and had to be filtered out
- **leaks every visitor's IP** to a third party on page load
- is a **render-blocking round trip** to another origin for a file that never changes
- forced three CSP directives to allow external hosts

Open Sans is licensed **SIL OFL 1.1**, which permits redistribution.

## What is checked in

| path | what |
|---|---|
| `resources/public/css/open-sans.css` | the `@font-face` rules, gstatic URLs rewritten to local paths |
| `resources/public/fonts/open-sans-{1..4}.woff2` | latin + latin-ext, normal + italic (variable weight 300–800) |

Referenced from `index.clj` as `/css/open-sans.css`. Total ~118 KB.

Only the **latin and latin-ext** subsets are kept. The Google CSS ships 50 subset blocks (cyrillic,
greek, hebrew, vietnamese…); keeping all of them would mean dozens of files for a UI that ships no
translations. Add subsets here if the app is ever localised.

## Regenerating

```sh
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
curl -A "$UA" -o /tmp/os.css \
  "https://fonts.googleapis.com/css2?family=Open+Sans:ital,wght@0,300;0,400;0,600;0,700;1,400&display=swap"
```

Then keep only the blocks whose subset comment starts with `latin`, download each
`fonts.gstatic.com` URL in order to `resources/public/fonts/open-sans-N.woff2`, and rewrite the
`url(...)` values to `/fonts/open-sans-N.woff2`. A browser User-Agent is required — the API serves
different formats (and refuses) based on it.

## CSP

Because nothing external is fetched any more, `font-src` and `style-src` are `'self'` in both
`config.clj` and `csp.clj`. **Do not re-add the font hosts** when touching CSP — if a font 404s the
cause is a missing local file, not a blocked host.
