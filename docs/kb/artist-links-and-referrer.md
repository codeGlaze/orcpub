# Artist links: https, the real domain, and what the referrer says

*Verified 2026-10-14 on `claude/artist-profile-pages` at `dbf63877`.*

## Decisions (owner's)

- **`rel="noopener"` only** on every link to an artist's own site: no `noreferrer`, `nofollow` or
  `ugc`. Artists are vetted accounts; the owner wants them to see this site in their own analytics
  and to get the search credit. `artist-profile/external-link`, `artist_profile.cljc:155`.
  *Rejected:* `noopener noreferrer nofollow ugc` (the usual default for user-supplied links), which
  was built first and reversed in `d23f31f0`.
- **`Referrer-Policy: strict-origin-when-cross-origin` site-wide**, so another site only ever sees
  the domain, never the page's address. Pedestal's secure-headers has no Referrer-Policy option,
  so it is its own `:leave` interceptor, `pedestal.clj:81`, set only when a handler set none. The
  **password-reset page keeps `no-referrer`** (`routes.clj:1763`): its URL carries the reset key.

## Rules every link follows

- **https only, from any source.** Links from the deployment's `PORTRAIT_ARTISTS` settings used to
  skip the rule artist edits are saved under; `web-url?` (`artist_profile.cljc:148`) is now that
  rule (`orcpub.artist-links/url-problem`) plus no whitespace, and `external-link` checks it again
  where each link is drawn (it returns nil for anything else).
- **The real domain is shown** beside each link on the artist page, and an international host is
  shown and linked in **punycode** (`ascii-url`, `:91`), so lookalike letters cannot pass for
  another site: `https://exаmple.test/` (Cyrillic а) is `https://xn--exmple-4nf.test/`.
