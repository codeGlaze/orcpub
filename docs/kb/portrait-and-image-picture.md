# A character's picture: portrait or image

A character keeps both a drawn portrait and an image link. `::character/picture-shown` ("portrait" or
"image") says which shows.

## The one rule

`char5e/shown-picture` and `shown-values` are the one rule every reader uses: the getters, the PDF,
the share card, `og:image` and the portrait store. No choice means the portrait wins. `shown-values`
keeps the image when the portrait shows, as the fallback for a portrait the site cannot draw (the
share-card test in `portrait_credit_test`).

## Decided 2026-11-10 (owner)

Plain switching between the two. A one-or-other deletion and a "set aside + Restore" flow were
rejected.

## The editor

A "Build a portrait | Use an image from the web" switch. The link is edited as `:portrait/link-draft`
and applied only by `:portrait/use-link`. Wording and layout rulings: [ux-guide.md](ux-guide.md),
Decisions log.

Related: [pdf-generated-vs-uploaded-images.md](pdf-generated-vs-uploaded-images.md),
[character-image-routes.md](character-image-routes.md).
