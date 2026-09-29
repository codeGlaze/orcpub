# Portrait credit line — design exploration

How the artist credit under the composed portrait should look in the compose
drawer. Worked through with the owner in September 2026, after the credit had
been built, restyled and re-aligned several times without ever being designed.

## Open it

`credit-lockups.html` is self-contained — open it in a browser. Six
compositions of the credit, each captured from the running drawer in the dark
and light themes at actual size.

The same page is published at <https://claude.ai/artifact/AR1KADrvoN99CEbShbpeCx>
(private to the owner). This copy exists because the artifact's source lived
in an ephemeral container.

## Where it landed

The owner's favourite ornament was the **broken rule** — hairlines running
out to either side of the credit, fading at their outer ends. Framing the line
symmetrically exposed that the line itself had no balance: two typefaces, caps
and italic, and four colours before the link marks added two more, with a wide
grey label on one side and two bright marks on the other.

Every lockup therefore makes the same two cuts — one typeface for the words
(Vollkorn italic) and no ornament but the rule and the marks' own colours; the
section sign and the dotted underline go — and then rearranges around the
centre:

| | Composition | Status |
|---|---|---|
| A | Broken rule on the line as shipped | reference |
| B | One typeface, one line: `—— art by Fusspot ⊕ ▣ ——` | **contender** |
| C | Phrase on the rule, marks centred on a second line | |
| D | Marks as the rule's end caps, `ART BY` centred above | **contender** |
| E | D without the label | |
| F | Label, name on the rule, marks below — three centred lines | **contender** |

B, D and F are the contenders. No decision yet.

## Constraints that came out of it

- **The credit should draw the eye.** A quiet grey credit is one designed to
  be skipped. The link marks wear their brand colours at rest.
- **Marks are sized to the text's cap height**, not its line box: 12 px beside
  the 13–14 px italic name. At 15 px they broke the cap line and read as
  floating, however their boxes were centred.
- **The credit sits directly under the portrait**, not in the drawer footer
  among the actions.
- **Fusspot asked for two links only**: her site and her Twitch.
- Everything is hairline weight. Nothing heavy or gaudy.

## Regenerating

Both capture scripts drive the real app with Playwright and inject CSS (and,
for the lockups, markup) into the drawer before each screenshot, so what they
capture is the app rather than a mockup. From a code branch, with the app built
and `lein e2e-server` running on :8890:

```bash
NODE_PATH=node_modules node docs/design/portrait-credit/capture-lockups.js   <out-dir>
NODE_PATH=node_modules node docs/design/portrait-credit/capture-ornaments.js <out-dir>
```

`capture-ornaments.js` produces the earlier round: ten ornaments (hairline,
tapered rule, corner caps, end ticks, diamonds, broken rule, …) on the line as
it then stood.

Both use `/image/social/*.svg` and `.pl-*` class names from the portrait
drawer, so they will need updating if those change.
