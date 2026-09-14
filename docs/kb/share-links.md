# Short share links: a character's homebrew kept on the server

Built on `integration-local`, 2026-09-13 and 14. The first short links were encrypted snapshots
(`20179535`, stored as bytes in `19d25c5c`, made revocable in `191b612e`); this design, `eab46b9f`, replaced them
the next day (see the encrypted-design section), and `e43b41ca` made it store nothing until Share link. Related: `a92b47b7` (the share button packs the character it belongs
to), `ac2e45bf` (pasted media is stripped), `7f375829` (custom items load with the character, so links
never carry them).

## What a link carries

- **The character's owner, logged in, once they have pressed Share link:**
  `/pages/dnd/5e/characters/<id>#s=<token>`, about 85 characters. The link stays the same until the
  owner presses New link, and it always shows the homebrew the server last received. Before the first
  Share link nothing about the character is stored; the button reads Share link instead of Copy link.
- **Anyone else,** or a browser without CompressionStream, homebrew over the caps, or a failed request:
  the bundle embedded in the link after `#c=`, as before, with the old length tiers and file fallback.
- **Links made earlier** still open.

## How it works

1. The share button (character page header, character list row, builder header) picks the homebrew the
   character uses from the viewer's local library (`share-bundle/extract-bundle`, which empties pasted
   `data:` URIs). It reads the character by id: `[:character]` is the builder's working copy and on the
   character page or list is some other character.
2. For the owner it GETs `/dnd/5e/characters/:id/share-token`. A character never shared gets a 404, and
   the button reads Share link; asking never makes a token. Pressing Share link PUTs to the same route,
   which makes a random 22-character token shown only to the owner, and then continues below. For a
   shared character the GET returns the token. Either response carries the server's caps in
   `X-Share-Max-Upload-Bytes` and `X-Share-Max-Text-Bytes`.
3. `share-url/encode-share` runs the bundle through the whitelist the server requires
   (`share-bundle/whitelist-shared`), writes it out with every map and set sorted by how its members
   print, so the same homebrew always gives the same bytes, and compresses it within the caps.
4. It PUTs those bytes to `/dnd/5e/characters/:id/shares/:token`. `routes.share/put-share` requires the
   current token. An upload identical to the stored one is recognised by its SHA-256 and costs nothing,
   which is the usual case, since every page view by the owner uploads. Any other upload is checked
   once: unpacked under the text cap, read as plain EDN, and required to be exactly homebrew, meaning
   the whitelist keeps all of it and changes nothing. It is then stored as sent, never repacked.
   Anything else is refused, so the store holds nothing but homebrew.
5. Opening the link, the `:route` handler sees `#s=<token>` and dispatches `::e5/load-shared-homebrew`,
   which GETs the homebrew, runs `share-url/decode-share` (unpack within the caps its response reports,
   safe read, the same whitelist)
   and hands it to `::e5/apply-shared-content`, the path an embedded link takes. An old or wrong token
   gets a 404 and the page says the homebrew could not be loaded.
6. **New link** (owner only, beside Copy link, after a confirmation) POSTs to the token route: the token
   is replaced and the stored homebrew deleted, so every earlier link loads nothing. The page then
   uploads again under the new token.

## When the stored copy changes

Only for a character that has been shared, and only when its owner opens a page showing that
character's share buttons: the character page, its row in the character list, or the builder, which
includes right after a save. Every such page view sends one upload, which the server drops when nothing
changed. Editing the library without reopening a character leaves the last copy in place; refreshing
shared characters after a library edit is future work (TODO, "Refresh shared homebrew after a library
edit").

## Pruning

A share nobody uses for `ORCPUB_SHARE_PRUNE_DAYS` days while the server runs (default 180; 0 keeps shares
forever) is deleted. Only the share record goes: the token and the server's copy of the homebrew. The
character, a party's entry for it, and the homebrew in the owner's library are never touched, and the
owner can share again at any time.

Use is a request that could see the share: the link opened with its current token, the owner's page
refreshing the copy, or a party page loading it. It is recorded in `:orcpub.share/used` at most once a
day, so opening a link repeatedly writes nothing; to make up for that, a share is deleted only after one
day more. A request with a wrong or missing token, or from anyone but the owner, records nothing, so
nobody can keep a share alive by requesting its URL.

Only `routes.share/prune!` deletes, run by `orcpub.share-pruner` a minute after start and then hourly.
Asking for a share never deletes it, so a share just past its window still works until the next run, and
opening it in that time keeps it.

Time the server is off does not count. Each run first calls `record-beat!`, which stores the time on the
entity `:orcpub.share/clock`. When the previous beat is more than 70 minutes back, the server was off or
its clock jumped ahead, and that stretch is stored as an `:orcpub.share-outage`; `prune!` subtracts
outages from each share's unused time. A self-hosted server switched off for six months deletes nothing
when it comes back, and a clock set back only makes shares look newer. A restart shorter than about an
hour is not recorded, so each one can count up to about an hour against shares.

Afterwards the old link says the homebrew could not be loaded, a party lists no token for it, and the
owner's button reads Share link again, which makes a new link. Nothing tells the owner that an old link
expired. 180 days leaves room for a campaign that pauses for a season.

## Parties

A logged-in viewer of someone else's character page sees Add to Party, which is how a DM usually adds a
friend's character from a link. A party stores character ids, not links, so without more the party page
loaded the character with no homebrew. Now:

- The `:route` handler remembers the token a character page was opened with (`db :share-link`), and Add to
  Party sends it: `{:character-id id :share-token token}` to `routes.party/add-character`, or
  `::party5e/shared-tokens` when it makes a new party (`create-party`).
- The server keeps a token with the party entry only when it is that character's current token
  (`routes.share/token-current?`), and lists only tokens that still work.
- Opening that character's row on the party page dispatches `::party5e/load-shared-homebrew`, which loads
  the homebrew into the shared overlay beside other rows' (no banner) and counts as use of the share.
- New link or pruning leaves the party listing no token, so the row loads nothing. Removing the character
  from the party drops its token.
- Only characters can join a party. Adding checked nothing before, so any entity id could be added.

Limits: rows share one overlay, so two characters whose homebrew uses the same key show the later row's;
and, as with a link, the overlay stays until the next character page.

## What the server knows

It stores and serves the homebrew readable, as it already does the character and its custom items. The
token sits after the `#`, so it is not in the page request, but the app fetches
`/shares/<token>`, so it does reach the server's API request logs. Only the owner can see or replace a
token; anyone with the link can load the homebrew.

## Limits, and why these numbers

Measured 2026-09-13. Compressed is what is uploaded and stored.

`MegaPak - WotC Books.orcbrew`, 12 sources, 2,316 KB as stored and 437 KB compressed:

| character | text | compressed |
|---|---|---|
| Divine Soul Sorcerer 20: class, largest subclass, largest race, subrace, background, 5 largest feats | 34 KB | 12 KB |
| Artificer 20, same picks | 62 KB | 16 KB |
| Cleric, paladin, ranger, bard 20: largest subclass, 40 largest homebrew spells, same picks | 64 to 76 KB | 17 to 20 KB |
| Wizard 20, same | 112 KB | 25 KB |
| Wizard holding every subclass (11) and every spell (119) in the pack | 175 KB | 40 KB |

`all-content.orcbrew` gave the same picture. The caps are config settings, listed in the startup banner
under capacity: `ORCPUB_SHARE_MAX_UPLOAD_KB` (default 64), `ORCPUB_SHARE_MAX_TEXT_KB` (256, also what a
viewer's browser will unpack) and `ORCPUB_SHARE_MAX_ACCOUNT_KB` (1024, about 40 wizard-sized shares). The
browser learns them from the response headers, so changing one needs no rebuild. There is one copy per
character. Over a cap, the owner gets the embedded link. Deleting a character deletes its copy and token.
The defaults came down in steps on the user's calls as the numbers came in: 1 MB, 256 KB, 128 KB, then
these.

Both packs are mostly published books; their only homebrew classes are Artificer and Divine Soul
Sorcerer. Measure again with heavy homebrew classes (Blood Hunter, classes with big tables) before
treating the caps as settled: load the pack (strip the byte-order mark first), build a character map of
selected keys, run `extract-bundle`, gzip `bundle->edn`.

## The encrypted design, kept in history

`191b612e` holds it. The browser encrypted the bundle with AES-GCM under a key derived from a
per-character salt, the character id and the content; the key travelled only in the link, so the server
could not read what it stored. Its link changed whenever the character's homebrew changed, since the key
was the content's fingerprint.

It was replaced because the usual experience for sharing a character is one link that keeps working and
shows the current version (D&D Beyond, Google Docs, Obsidian Portal, World Anvil), and the server already
stores characters and custom items readable. A stable link, content updated in place, and a server that
cannot read it cannot all be had at once without the owner carrying a key between devices. Bringing it
back means restoring `routes/share.clj`, `share_url.cljs`, the share parts of `integrations.cljs` and
`events.cljs`, their tests and the suite from that commit; its schema attributes differ.

## Open

- **A report-and-remove process** for shared homebrew (TODO, "Share permissions and groups").
- **Per-character visibility** would put the character, the items it brings and its shared homebrew
  behind one check.
- **No way for the owner to see or revoke a party's copy of a token** other than New link, which
  revokes every link and party entry at once.

## Traps hit building it

- `[:character]` is the builder's working copy; the share button must read `::char5e/character` by id.
- `.orcbrew` files can start with a byte-order mark, which the EDN reader takes as a symbol.
- `:show-message` closes itself after 5 seconds; a browser check must watch for it from page load.

## Checks

`scripts/e2e/run.sh share-link-carries-homebrew.js`: kaylee's seeded character using a homebrew language
stores nothing until she presses Share link; her link; the character list gives the same link; logged out and as zoe it loads; a wrong
token loads nothing and says so; after kaylee changes the description the link stays the same and shows
the new text; New link makes a different link, the old one stops loading and the new one works.
`test/clj/orcpub/routes/share_test.clj` covers ownership, tokens, upload checks (not compressed, not data,
code, nothing homebrew-shaped, both caps, pasted media and bad keys refused), an unchanged upload doing no
work, the caps headers, the quota, New link and deletion.
`test/cljs/orcpub/dnd/e5/share_url_test.cljs` covers the round trip, field order and both caps.
`scripts/e2e/run.sh party-keeps-shared-homebrew.js`: zoe opens kaylee's link, adds the character to a
party, and the party page loads its homebrew until kaylee presses New link.
`test/clj/orcpub/routes/party_test.clj` covers characters only, tokens kept only while current, New link
and removal; `share_test.clj` also covers pruning, use keeping a share alive, daily recording, a request
without the token recording nothing, the sweep deleting share records and nothing else, time the server
was off not counting, and 0 days.
