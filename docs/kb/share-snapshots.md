# Short share links: encrypted homebrew snapshots

Built on `integration-local` on 2026-09-13: `20179535`, stored as bytes with the caps below in `19d25c5c`, with `a92b47b7` (the share button packs the
character it belongs to) and `ac2e45bf` (pasted media is stripped). Custom items do not travel in links
at all; a character read brings them (`7f375829`).

## What a link carries

- **The character's owner, logged in, in a browser with Web Crypto:**
  `/pages/dnd/5e/characters/<id>#s=<snapshot id>.<key>`, about 130 characters.
- **Everyone else**, or a browser without Web Crypto, or content over the cap, or a failed upload: the
  bundle embedded in the link after `#c=`, as before, with the old length tiers and the file fallback.
- **Links made earlier** still open, whether they embed homebrew, items or both.

Web Crypto exists only on https and on localhost, so a plain-http address such as a WSL VM reached by
IP always gets the embedded link.

## How a snapshot is made and opened

1. `share-bundle/extract-bundle` picks the homebrew this character uses from the owner's local library,
   and empties any `data:` URI in its text.
2. `share-url/build-snapshot` compresses the EDN, derives the key as SHA-256 of
   `"orcpub share v1 <character id>\n"` followed by the EDN, derives the IV as the first 12 bytes of
   SHA-256(key + `"iv"`), and encrypts with AES-GCM. The snapshot is one version byte (2) followed by
   the ciphertext, and it is uploaded, stored and served as bytes; base64 would add a third. The
   snapshot id is the first 22 characters of the snapshot's SHA-256 in base64url.
3. The share button GETs `/dnd/5e/characters/:id/shares/:share`; on a 404 it PUTs the bytes as
   `application/octet-stream` with the login token. `routes.share/put-share` recomputes the id and refuses a blob
   that does not hash to it.
4. Opening the link, the `:route` handler sees `#s=` and dispatches `::e5/load-shared-snapshot`, which
   fetches the blob, decrypts it with the key (`share-url/decode-snapshot`), and hands the result to
   `::e5/apply-shared-content`, the same path an embedded link takes. A missing snapshot or a wrong key
   shows "could not be loaded".

The same content and character always give the same key, blob and id, so uploading again stores
nothing, and snapshot ids repeat across test runs.

## What the server can and cannot know

- It cannot read a snapshot. The key is after the `#`, which browsers never send.
- The key is derived from the content. Someone who already holds the exact content and knows the
  character id could confirm a snapshot matches it; nobody can learn unknown content.
- It knows who stored a snapshot, for which character, and how big it is.
- Anyone may fetch a snapshot's ciphertext by id, as anyone may read the character.

## Limits, and why these numbers

Measured on 2026-09-13. "Stored" is the compressed, encrypted snapshot as the server keeps it: the
compressed size plus 17 bytes.

`MegaPak - WotC Books.orcbrew`, 12 sources, 2,316 KB as stored and 437 KB compressed:

| character | text | stored |
|---|---|---|
| Divine Soul Sorcerer 20: class, largest subclass, largest race, subrace, background, 5 largest feats | 34 KB | 12 KB |
| Artificer 20, same picks | 62 KB | 16 KB |
| Cleric, paladin, ranger, bard 20: largest subclass, 40 largest homebrew spells, same picks | 64 to 76 KB | 17 to 20 KB |
| Wizard 20, same | 112 KB | 25 KB |
| Wizard holding every subclass (11) and every spell (119) in the pack | 175 KB | 40 KB |

`all-content.orcbrew` gave the same picture (artificer 16 KB, wizard with 40 spells 24 KB).

So, halved from the first guess on the user's call: **128 KB stored** per snapshot
(`routes.share/max-blob-bytes`, mirrored by `share-url/max-snapshot-blob-bytes`), **512 KB of text**
(`max-snapshot-edn-bytes`, which also bounds what a viewer's browser decompresses), **the newest 5** per
character, **2.5 MB** per account. A snapshot over the cap, or an account over its quota, gets the
embedded link instead. Deleting a character deletes its snapshots.

Both packs are mostly published books; their only homebrew classes are Artificer and Divine Soul
Sorcerer. Measure again with a pack of heavy homebrew classes (Blood Hunter, classes with big tables)
before treating the caps as settled. The script is simple: load the pack (strip the byte-order mark
first), build a character map of selected keys, run `extract-bundle`, and gzip `bundle->edn`.

## Open

- **No way to revoke a link.** Pressing Copy link again with unchanged homebrew gives the same link, since
  key and id come from the content. Changing the homebrew makes a new snapshot; older links keep
  working until theirs falls out of the newest 5 or the character is deleted.
- **A report-and-remove process** for stored snapshots. Being unable to read them and able to delete
  them is a better position than hosting homebrew openly, but they are still stored and served. Take
  the details to a lawyer.
- Per-character visibility (TODO, "Share permissions and groups") would put the character read, the
  items it brings and its snapshots behind one check.

## Traps hit building it

- `[:character]` is the builder's working copy. On the character page and in the character list it is
  some other character, so the share button must read `::char5e/character` for its id.
- `.orcbrew` files can start with a byte-order mark, which the EDN reader takes as a symbol.
- `:show-message` closes itself after 5 seconds; a browser check must watch for it from page load.

## Checks

`scripts/e2e/run.sh share-link-carries-homebrew.js` makes kaylee's link from a seeded character that
uses a homebrew language, then opens it logged out, as zoe, and with a wrong key.
`test/clj/orcpub/routes/share_test.clj` covers ownership, id and shape checks, caps, eviction, quota and
deletion; `test/cljs/orcpub/dnd/e5/share_url_test.cljs` covers the crypto in the browser.
