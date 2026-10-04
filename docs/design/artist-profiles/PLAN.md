# Artist profile pages — plan

Branch: `claude/artist-profile-pages`, cut from `claude/character-portrait-generator-hOutO`.

A public page per portrait artist at `/artists/<slug>`: their name, how many pieces they
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
- `pieces-by-layer`, `piece-count`: what they drew, grouped in picker order, for the
  piece count in the lede and the share description. The hidden `:scalp` is left out:
  `ARTISTS.md` says it is a generated patch, not anyone's drawing. The page does not
  list the pieces (below).
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
  same compositor as the drawer and the character page, so they cannot
  look different from a real portrait.

## Fusspot's links

The registry carries exactly the links she asked for, her site and her Twitch, and no
others: not every handle she has, the ones she wants here. Twitch is where she does most of
the work, so someone who liked the art can go and watch it being made.

- **`:artist/link` is her homepage**, the one link for surfaces that hold one, such as the
  character page's 100px credit strip. It is the list she maintains, so it cannot go stale
  the way a copied handle can.
- **A link with no `:link/icon` shows its label as text**, so a service nobody has drawn a
  mark for degrades to a word, not an empty box. The bluesky and kofi marks stay in
  `/image/social` for the next artist who wants them.
- **`:link/color` is worn at rest, not only on hover.** A credit in the same quiet grey as
  everything around it is designed to be skipped; the eye finds a purple Twitch mark and
  slides past a grey globe. Site takes the app's amber because her homepage has no mark of
  its own and it is the primary link.

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

---

## Added after the plan: the artist can turn their page off

The owner asked for a way for an artist to turn their page off from their account. Artist
accounts are being built elsewhere, so this branch adds only the switch the page obeys:
`:artist/profile?`, on unless it is set to `false`. It can be set through
`set-artist-overrides!` next to the other credit fields, so the account setting only has
to write it. When it is off:

- `/artists/<slug>` and its `portrait.png` return 404, the same as for a name nobody
  has, and the 404's share tags don't name the artist;
- the artist drops out of `/artists`;
- the credit loses its "About the artist" line;
- the credit's name still links to the artist's own `:artist/link`.

## What was built

| Piece | Where |
|---|---|
| Pure functions: slug, lookup, pieces, listed links, examples, profile link, the off switch | `src/cljc/orcpub/dnd/e5/artist_profile.cljc` |
| `:artist/slug "fusspot"`; `:artist/profile?` accepted as an override | `portrait_assets.cljc` |
| Routes `/artists`, `/artists/:slug`, `/artists/:slug/portrait.png` | `route_map.cljc`, `routes.clj` |
| The pages (profile, list, not-found) | `src/cljs/orcpub/dnd/e5/views/artist_page.cljs`, registered in `web/cljs/orcpub/core.cljs` |
| "About the artist ›" at the foot of the drawer credit | `portrait.cljs` (`credit-lockup`, `.lk-about`) |

## What was verified

- `lein test`: the new `artist-profile-test` (19 tests) and `artist-page-test` (5 tests)
  pass, as do the existing `portrait-test`, `portrait-og-test` and `routes-test`.
  Other artists in the tests are obvious placeholders set up with `with-redefs`. No
  registry entry was added.
- `lein fig:build` compiles clean. `lein garden once` runs.
- `test/browser/artist_profile_e2e.js` passes all 51 checks against `lein e2e-server`. It
  is now in `run-browser-probes.js` and the baseline. It covers:
  - the share tags, the og PNG and the 404;
  - both themes: the page repaints and the portrait frames stay dark;
  - Vollkorn italic for the name;
  - exactly the two listed links, opening in new tabs;
  - no email address or `mailto:` anywhere on the page;
  - no sideways scroll at 390px;
  - the list page;
  - in both themes, the drawer credit's name still going to `https://fusspot.rip/` and its
    new profile link sitting at the same dim colour as "ART BY".

  The probe once failed on that last check because the mouse was resting on the link;
  the probe now moves the mouse away first.
- Screenshots, taken with the placeholder silhouettes only: `shots/profile-dark.png`,
  `profile-light.png`, `profile-phone.png`, `index-dark.png`, `drawer-credit-{dark,light}.png`
  and their `-hover` versions.
- Not run: the full `lein test` suite and the other browser probes.

## For the owner to decide

1. **The credit on the character page.** The profile link is only in the drawer credit.
   The 100px credit under the portrait on the character page is where viewers see a
   credit, but adding a line there changes that strip.
2. **Piece tiles.** Withdrawn: the page no longer lists pieces (below).
3. **Example colours** are picked from the picker's presets by a seed. An artist may want
   to choose their own showcase portraits instead, which could be a registry field or an
   account feature later.
4. **When a page is turned off**, the address simply 404s. A short "this artist has made
   their page private" message would be kinder, but it would also confirm that someone
   is registered there.
5. **The slug cannot be overridden** by a deployment, so a fork that renames Fusspot keeps
   `/artists/fusspot`. That's deliberate, so shared links keep working, but a fork might
   want its own.
6. **Signing:** commits are GPG-signed as codeGlaze with a key generated in this
   container. GitHub will show them as Unverified until the public key is added to the
   account.

## Decided: the page does not list the pieces

The owner: the page is easy to scrape and many AI crawlers ignore robots.txt, so it should
not offer every asset; examples, if shown at all, should be small, low quality and
watermarked. The Pieces grid, a tile per asset at full size, is removed. The lede keeps
the count.

**Still open:** the three examples are composed in the browser from the full-size layer
files, and they rotate through every piece, so the page still downloads all of them. Making
the examples small and watermarked means rendering each one on the server as a single
flattened image (`portrait-render` already composes one for the share card) instead of
composing it from the layers. That goes with the card layout choice (cards.html).

