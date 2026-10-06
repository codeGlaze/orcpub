# Branch changelog — `claude/character-portrait-generator-hOutO`

## Why this branch exists

Characters could only have a portrait by pasting an image URL. This adds a paper-doll
compositor: layered line art by an illustrator, stacked in fixed z-order and tinted from the
character's own colours, with a seeded Randomize.

It is **not** AI image generation. Every asset is pre-authored art, and attribution is a
first-class part of the feature rather than a footnote.

Scope notes for review:

- The real art is not in the repository. `resources/public/image/portraits/` holds the
  anonymised silhouettes from the illustrator's tooling at the paths the art will occupy;
  the art pack (prepared in the Silhouette Loom, kept on `agents/develop` under
  `agents/loom/`) is unpacked over them at deploy, followed by the strand-file step
  (`lein run -m orcpub.portrait-pack.strands <art-dir>`). No code changes between the two.
- The art licence (`ARTISTS.md`, a directory `LICENSE`, a carve-out from the root EPL-2.0)
  still needs the commission paperwork and the illustrator's agreement. Not in this branch.
- Unfinished work is behind Developer mode (split dye, the runtime see-through fill, an
  alternative far-iris placement) or listed in `docs/PORTRAIT-ROADMAP.md`.

## Highlights

Characters can now be given a portrait built from layered art rather than a pasted URL:
pick or randomize each piece, colour hair, skin, eyes and clothing per character, and add
an ombré, freckles, glowing eyes or a torch-lit edge. The picture follows the character
everywhere it goes, the summary, the exported sheet and the card a shared link unfurls into,
each carrying the artist's credit with it.

## Added

- **Characters:** a portrait builder. Pick or randomize hair (back, front, bangs, bits), head,
  ears, eyes, nose, mouth and shirt from layered art, from the summary thumbnail, beside the
  Image URL field, or in a Portrait tab in the builder.
- **Characters:** portrait colours per character for hair, skin, eyes, eye whites, lips and
  shirt, with a lighter, darker or different colour for any single piece.
- **Characters:** hair ombré with streaks that follow the drawn strands, a shine you can place,
  dyed or root-coloured bangs, and soft shadows where hair falls over the face.
- **Characters:** eyes with slit or goat pupils, a different colour for each eye, brightness,
  and glowing eyes in five presets or any colour.
- **Characters:** blush, freckles (amount, placement and how strong) and shading under the hair.
- **Characters:** mood light: torch, moonlight or arcane, as a lit edge, a cast or a rim, from
  any side.
- **Printing:** the composed portrait prints on the exported character sheet.
- **Sharing:** a shared character link unfurls with the character's own portrait.
- **Characters:** the artist is credited under the portrait, on the sheet, in the shared card and
  in the composed image itself, with the links the artist chose.
- **Accounts:** artists named in the deployment's portrait-artist settings get an account, with a
  welcome email and a week-long link to set a password; existing accounts are linked, and the
  site admin hears about each.
- **Accounts:** a preferred name, encrypted at rest, used to greet you in site emails; artists can
  change how they are credited, and their links, on My Account.
- **Site:** pages and portrait images ask AI crawlers not to train on or reuse them.
- **Server:** a startup log line when hair art is deployed without its strand files.

## Fixed

- **Sharing:** link previews of shared characters failed on Discord, Mastodon, Slack, Telegram
  and X, because `robots.txt` blocked every crawler that honours it.
- **Sharing:** every shared character link had an empty title and image in its preview.
- **Printing:** exported sheets reported "Adobe InDesign CS6" as their creator and had no title or
  author.

## Changed

- **Characters:** the builder's tab bar wraps on narrow screens.
- **Printing:** artwork the app generates for the sheet has its own size limit, separate from the
  one for uploaded images, so the portrait prints at full resolution.

## Security
