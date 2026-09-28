# Artists

The portrait art in this repository was drawn by hand. It is not generated.

## Fusspot — <https://fusspot.rip/>

Support her work: <https://ko-fi.com/fusspot>

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

`:artist/link` points at the artist's own homepage rather than at individual
social accounts. She lists half a dozen there and keeps them current; copying
them here would make this repository responsible for their staleness, and a
dead handle would remain in every character sheet already exported.

`:artist/support` is separate and appears only in the builder, next to the
credit. It is deliberately not part of `credit-line`: that string is burned
into share-card PNGs and PDF sheets, where a URL cannot be followed and a
donation ask would ride along on every copy of someone else's character.

## For forks

A deployment can restate a credit without editing shared code, via
`PORTRAIT_ARTISTS`:

```
PORTRAIT_ARTISTS='{"house-pack": {"name": "…", "link": "https://…"}}'
```

Only `name`, `link`, `support` and `license` are honoured. Which artist drew which asset
is structural, lives in the registry, and is not overridable — a configuration
cannot quietly re-attribute someone's work to a different pack.

**This does not grant a licence.** If you fork this repository, the art is
still Fusspot's. Ask her before shipping it.
