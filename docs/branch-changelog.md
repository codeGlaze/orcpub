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
  `agents/loom/`) is laid in on the private deployment branch only (`scripts/private-art.sh`);
  the image build writes the strand files. No code changes between the two.
- The art licence (`ARTISTS.md`, a directory `LICENSE`, a carve-out from the root EPL-2.0)
  still needs the commission paperwork and the illustrator's agreement. Not in this branch.
- Unfinished work is behind Developer mode (split dye, the runtime see-through fill, an
  alternative far-iris placement) or listed in `docs/PORTRAIT-ROADMAP.md`.

## Added

- **Characters:** a portrait builder: pick or randomize layered art for hair, head, ears, eyes,
  nose, mouth and shirt, from the summary thumbnail, beside the Image URL field, or in a Portrait
  tab in the builder.
- **Characters:** portrait colours per character for hair, skin, eyes, eye whites, lips and shirt,
  with a lighter, darker or different colour for any single piece.
- **Characters:** hair ombré with streaks that follow the drawn strands, a shine you can place,
  dyed bangs, and soft shadows where hair falls over the face.
- **Characters:** eyes with slit or goat pupils, a different colour for each eye, brightness, and
  glowing eyes.
- **Characters:** blush, freckles and shading under the hair.
- **Characters:** mood light: torch, moonlight or arcane, as a lit edge, a cast or a rim.
- **Characters:** the artist is credited under the portrait, on the sheet, in shared link previews
  and in the picture itself, with the links the artist chose.
- **Printing:** the portrait prints on the exported character sheet, at full resolution.
- **Sharing:** a shared character link previews with the character's own portrait, and the
  preview updates when the portrait changes.
- **Accounts:** a preferred name, used to greet you in site emails.
- **Accounts:** artists get an account with a welcome email and can change how they are credited
  on My Account.
- **Site:** pages and portrait images ask AI crawlers not to train on them.
- **Site:** a public page for each portrait artist, with their name, how many pieces they drew,
  three small watermarked example portraits and the links they listed, and a page listing
  every artist.
- **Characters:** the portrait credit links to the artist's page.
- **Site:** an artist's page can be turned off in the deployment's portrait-artist settings.

## Changed

- **Accounts:** site emails greet you by your preferred name, or by your username when you have
  not set one, instead of "Hi there".
- **Characters:** the builder's tab bar wraps on narrow screens.
- **Site:** tick boxes are rounded and fill with the theme's accent when ticked (amber in the dark
  theme, blue in the light), and the sign-up page's updates box matches the rest.

## Fixed

- **Characters:** a late save for one character no longer replaces another character open in the
  builder, along with its unsaved edits.
- **Sharing:** link previews of shared characters failed on Discord, Mastodon, Slack, Telegram and
  X, because `robots.txt` blocked every crawler that honours it.
- **Sharing:** every shared character link had an empty title and image in its preview.
- **Printing:** exported sheets reported "Adobe InDesign CS6" as their creator and had no title or
  author.
