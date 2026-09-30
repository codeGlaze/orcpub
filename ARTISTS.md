# Artists

The portrait art in this repository was drawn by hand. It is not generated.

## Fusspot

- Site: <https://fusspot.rip/>
- Twitch: <https://www.twitch.tv/fusspot> — where most of the work happens, live

These are the two she asked to be listed. She has others; they are on her site,
which is the point of linking it.

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

## Artist accounts

An artist can have an account that controls how they're credited: their
name, where it links, and which link icons sit beside it. The account can't
change which art is theirs. That stays in the registry.

There's no admin page and no invite endpoint. Accounts come from the same
`PORTRAIT_ARTISTS` entry as the credit, read when the server starts:

```
PORTRAIT_ARTISTS='{"house-pack": {
  "name": "Fusspot",
  "account": "hello@fusspot.rip",
  "username": "fusspot",
  "preferred_name": "Fuss",
  "welcome_subject": "We did it, Fuss! Your art is officially on OrcPub",
  "welcome_note": "We did it! Your art is officially on OrcPub! …"
}}'
```

On each start:

| The address has…                  | What happens                                                            |
|-----------------------------------|-------------------------------------------------------------------------|
| no account                        | one is made and linked, and a welcome email with a set-password link that lasts a week is sent |
| a confirmed account               | it's linked, and an upgrade email is sent                               |
| an unconfirmed account            | nothing yet; it links when its owner confirms the address              |
| an account that's already linked  | nothing, so restarts never resend                                       |

Only `account` is needed; the rest just make the welcome warmer. Changing
`account` moves the link to the new address and tells the old account.
`"account": null` removes the link. Deleting the entry does nothing, so a
bad edit can't quietly strip an artist's access. Several containers
starting together make one account and send one email.

What the site shows is layered, and later layers win field by field:

1. the registry in `portrait_assets.cljc` (the public default)
2. the artist's own edits, from My Account → Artist credit
3. `name`, `link` and `links` in `PORTRAIT_ARTISTS`

So you can always correct a credit from config without touching the
account. Fields the config sets show as fixed on the artist's settings
page. Every edit is emailed to the artist and to the admin.

### Settings these need

| Setting                         | For                                                                 |
|---------------------------------|---------------------------------------------------------------------|
| `APP_URL`                       | links in the startup emails; without it they're skipped (logged) and the artist can use "Forgot password" |
| `EMAIL_SERVER_URL` etc.         | sending anything at all                                             |
| `EMAIL_ADMIN_TO`                | the admin's copies; falls back to `EMAIL_ERRORS_TO`                  |
| `APP_EMAIL_SIGNOFF`             | who the warmer emails are signed from (default "The <sender name>") |
| `PROFILE_ENCRYPTION_KEYS`       | preferred names (see below); a Docker secret `profile_encryption_keys` takes precedence |

## Preferred names

Every account can set "What should we call you?" on My Account. Site emails
open with it ("Hi Kaylee,") and say "Hi there," without it.

It's **encrypted, not hashed**: a hash can't be read back, and the site has
to write the name into the email. The database holds only ciphertext, and
the key lives outside it, so a copied database or backup doesn't reveal the
names. A compromised app server still could, because it holds the key.

```
# one key: id:base64 of 32 random bytes
PROFILE_ENCRYPTION_KEYS="k1:$(openssl rand -base64 32)"
```

Prefer the Docker secret `profile_encryption_keys` in swarm. To rotate, put a
new key first and keep the old ones after it (`k2:…,k1:…`); the first
encrypts and all of them decrypt. **Losing every key loses the names**, and
only the names: they fall back to "Hi there,". With no key set the field says
it isn't available yet, and nothing is ever stored readable.
