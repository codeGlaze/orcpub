# My Account is white text on light grey in the light theme

*Found 2026-10-14 on `claude/artist-profile-pages` at `dbf63877`, driving the page in a browser.
Not fixed: it predates that branch.*

In the light theme every line of My Account's body (username, email, "News and updates", "What
should we call you?", the artist credit form's labels) renders `rgb(255, 255, 255)` on the light
page background: next to unreadable.

**Cause:** the page body is wrapped in a hard-coded `.white` utility class
(`src/cljs/orcpub/dnd/e5/views.cljs:10190`, `[:div.f-s-24.p-10.white ...]`, from 2017), and the
light theme does not override `.white`. Other pages use `.main-text-color`, which the themes set.

**Likely fix:** `.main-text-color` in place of `.white` there, then look for other `.white` text on
page grounds. Check both themes; the dark theme must stay as it is.

Related measurement from the same work: the light theme's unticked checkbox edge was
`rgba(0,0,0,0.35)` on white, **2.44:1**, under the 3:1 a control boundary needs; now
`muted-on-light`, 4.6:1 (`orcpub.styles.checkbox-test`).
