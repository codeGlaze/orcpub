# Branch changelog — `fix/message-banner-spacing`

## Why this branch exists

The import banner read "Import successful Imported 464 items To be safe, export
all content now to create a clean backup." — three sentences with no punctuation
between them, which looks like a typo. It isn't: the message is written with
blank lines between its parts, and HTML collapses those to single spaces. The
banner also sat flush against whatever was above it, which on a phone means
jammed under the page title, with the close icon pressed against the text.

## Fixed

- **Message banners keep their line breaks**, so a multi-part message reads as
  separate lines instead of sentences run together with no punctuation.
- **The banner has room to breathe** — padding above it as well as below, a gap
  between the text and the close icon, and the icon aligned to the first line
  rather than halfway down a three-line message.
