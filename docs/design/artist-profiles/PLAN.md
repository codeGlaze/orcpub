# Artist profile pages — plan

Branch: `claude/artist-profile-pages`, cut from `claude/character-portrait-generator-hOutO`.

A public page per portrait artist at `/artists/<slug>`: their name, the pieces they
drew, a few portraits composed from those pieces, and the links they listed. It is the
site's showcase of them. It is not their home page, so the credit's name link still
goes to wherever they chose (`:artist/link`), and the profile is reached by a second,
quieter link.

## Routes

| Path | Server | Client |
|---|---|---|
| `/artists` | index HTML, generic share tags | lists every registered artist |
| `/artists/<slug>` | index HTML with that artist's share tags (title, description, `og:image`); **404** for an unknown slug | the profile |
| `/artists/<slug>/portrait.png` | the first example portrait rendered by `portrait-render/render-png`, for `og:image` | — |

Declared once in `route-map.cljc` (bidi), served by `routes.clj`, and dispatched in
`core.cljs` like every other page. This follows the character page: an SPA view, with
the share tags set on the server because a crawler never runs the JavaScript.

**Why the slug is not `:artist/id`.** Fusspot's id is `:house-pack`, and saved portraits
store it, so it cannot be renamed. The registry entry gains `:artist/slug "fusspot"`.
It is deliberately **not** overridable by `set-artist-overrides!`: a URL that moved
whenever a deployment restated a name would break every link already shared. An artist
with no slug falls back to `(name id)`.

## Data

Nothing new is stored. There is no database, no account and no user record: the page
reads only the registry (`portrait_assets.cljc`) plus the deployment overrides that
the credit already honours. The pure functions go in a new cljc namespace,
`orcpub.dnd.e5.artist-profile`, so the JVM (share tags, og image) and the browser
(the page) agree and both can be tested:

- `slug`, `artist-by-slug`, `profile-path`: resolve a URL to an artist and back.
- `pieces-by-layer`, `piece-count`: what they drew, grouped in picker order. The
  hidden `:scalp` is left out: `ARTISTS.md` says it is a generated patch, not
  anyone's drawing.
- `listed-links`: `:artist/links` (or `:artist/link` alone as a "Site" link when no
  labelled set exists), **http(s) only**. A `mailto:` or any other scheme is dropped,
  so an email address cannot reach the page even if one is put in the registry.
  Nothing is looked up or added: these are the links the artist listed.
- `example-portraits`: a few deterministic portraits. Each layer the artist drew uses
  their pieces, rotated so that across the examples every piece appears where
  possible; colours come from the existing presets. Layers they did not draw are
  filled from other artists so the picture is whole, and `credit-order` then names
  those others under that example. Seeded from the slug, so the page, the og image
  and every reload show the same portraits.
- `profile-link`: where the credit's secondary link goes: one artist gives their
  profile, several give `/artists`.

## What renders where

- **Server:** the share tags and the og PNG. Nothing about the page body.
- **Client:** everything else. The examples use `portrait/composite`, the
  same CSS-mask compositor as the drawer and the character page, so they cannot
  look different from a real portrait.

## Look

The drawer's vocabulary, nothing new: Vollkorn italic for names, Open Sans for UI,
the small uppercase `.lk-cap` label, the broken amber rule, the link marks in their own
colours. One centred column like a Carrd page, inside the normal app shell (header,
footer). The portrait frames stay dark in both themes, as in the drawer, because they
hold character colours. Light theme: `#363636` text with slate-blue `#33658A` accents.
Dark theme: `#ebeef4` on the app's navy ground with amber `#f0a100` accents. Scoped to the
page's own root class and the app's theme class, the way the drawer scopes `.pl-root`.

## The secondary link from the credit

**Proposal:** in the drawer's credit, a small uppercase line at the foot of the lockup,
mirroring the "ART BY" label at the top: **"About the artist ›"**, or **"About the
artists ›"** when there are several. It opens in a new tab, as the other credit links
do, so an unsaved portrait draft is not left behind. The name, its dotted underline
and its glow, and the link marks are all unchanged.

Considered and not chosen:
- *A profile mark among the link marks.* Those marks are the artist's own links, and
  one of ours among them would blur that. It would also change the mark count, which
  sets whether the marks can flank the name.
- *Linking the "ART BY" label.* Nobody would know it was a link.
- *The character page's 100px credit strip.* It is where a viewer, rather than the
  person composing, actually sees the credit, so it is the better place in principle.
  It is two clamped lines of 9px text today, so adding a line changes that surface.
  Left for the owner to decide (below).

## Tests

- cljc unit tests for every new pure function (JVM `lein test`; they are cljc so the
  cljs runner can take them too), using stand-in artists built with `with-redefs`, as
  the credit-order tests do. No placeholder artist is added to the registry.
- JVM route test: the page's share tags, the 404, and the og PNG.
- A browser probe, `test/browser/artist_profile_e2e.js`: the page renders in both themes,
  the drawer credit carries the secondary link and the name link is unchanged, the
  page never shows an email address or `mailto:`, and screenshots of both themes.

## Out of scope

Artist accounts, editing a profile, anything tied to a site user, and any link the
artist did not list.
