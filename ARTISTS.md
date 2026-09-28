# Artists

The portrait art in this repository was drawn by hand. It is not generated.

## Fusspot

- Site: <https://fusspot.rip/>
- Twitch: <https://www.twitch.tv/fusspot> — where most of the work happens, live
- Bluesky: <https://bsky.app/profile/fusspot.rip>
- Ko-fi: <https://ko-fi.com/fusspot>

Every portrait layer in `resources/public/image/portraits/` is Fusspot's work:
the heads, hair, ears, eyes, noses, mouths and shirts that the portrait maker
composes.

The files currently committed are **silhouettes** — alpha shapes derived from
her drawings, at the source proportions, without line detail. They are derived
works of her art and carry the same credit. The full line art replaces them at
the same paths.

`scalp/l2b_scalp_01.png` is the one exception: a generated blob that fills a
gap between two hair pieces. Its shape is derived from hers, so it is listed
here for completeness rather than claimed as anyone's drawing.

## How the credit is rendered

`orcpub.dnd.e5.portrait-assets/credit-line` produces one line — `Art: Fusspot`
— and the character sheet, the share card and the portrait summary all use it,
so the three always say the same thing. Attribution resolves **per asset**, so
a portrait built from two artists' pieces credits both.

An artist who has not said how they want to be credited is skipped rather than
given a byline nobody chose.

`:artist/link` is the single link, for surfaces that can only hold one -- the
character-page credit is a 100px strip. It points at the artist's own homepage,
because that is the list they maintain, so it cannot go stale the way a copied
handle can.

`:artist/links` is the full labelled set and appears only in the builder, which
has room. It is chosen with the artist rather than harvested from their site:
these are the accounts they want on this work, not every account they have.

Neither is part of `credit-line`. That string is burned into share-card PNGs
and PDF sheets, where a URL cannot be followed, and it would ride along on
every copy of somebody else's character.

## For forks

A deployment can restate a credit without editing shared code, via
`PORTRAIT_ARTISTS`:

```
PORTRAIT_ARTISTS='{"house-pack": {"name": "…", "link": "https://…"}}'
```

Only `name`, `link`, `links` and `license` are honoured. Which artist drew which asset
is structural, lives in the registry, and is not overridable — a configuration
cannot quietly re-attribute someone's work to a different pack.

**This does not grant a licence.** If you fork this repository, the art is
still Fusspot's. Ask her before shipping it.
