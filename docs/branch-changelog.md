# Branch changelog — `feature/artist-pages`

## Why this branch exists

A public page for each portrait artist, reached from the credit under every portrait, which the
artist controls from My Account. Carries the review fixes to the portrait work that landed in #64
(examples drawn through the portrait pool, the licensed-art mark on JPEGs, https-only artist links)
and the batched emails an artist's credit edits send.

## Added

- **Site:** a public page for each portrait artist, with their name, how many pieces they drew,
  three small watermarked example portraits and the links they listed, and a page listing
  every artist.
- **Characters:** the portrait credit links to the artist's page.
- **Accounts:** artists choose on My Account whether they have a page, and whether it shows
  their example portraits, their links and how many pieces they drew.

## Changed

- **Site:** tick boxes are rounded and fill with the theme's accent when ticked (amber in the dark
  theme, blue in the light), and the sign-up page's updates box matches the rest.

## Fixed

- **Site:** all seven header tabs and their menus fit on a 320px phone.
- **Spells:** a spell can no longer be prepared once every preparation is used.

## Security

- **Site:** other sites a page links to are told only this site's domain, never the page's address.
