# Tinting portrait line art

How the character's colours get onto the illustrator's drawing, and why each
step is the way it is. Everything here was settled by rendering the real art and
looking, not by argument; the tests named at the end are what keep it settled.

Nothing in here is AI image generation. Every asset is drawn by hand. The
compositor's whole job is to recolour authored line art without damaging it.

## Multiply, not mask

The original compositor tinted by masking: the browser laid a CSS
`mask-image` over a background colour, the server used `AlphaComposite/SrcIn`
over a `fillRect`. Both read the asset's **alpha** and discard its **RGB
entirely**.

That is invisible while the assets are silhouettes, because a silhouette is
nothing but alpha. It stops being invisible the moment line art arrives: a
drawing with black outlines over a white fill comes out as one flat colour --
the same output as its own outline. The pipeline could not tell a detailed
drawing from its silhouette.

The replacement is multiply: `out.rgb = tint.rgb * art.rgb`, `out.a = art.a`.
White fill takes the tint exactly, black lines stay black, a grey darkens the
tint by however grey it is. Java2D has no multiply -- `AlphaComposite` is
Porter-Duff only -- so on the server it is a pixel pass.

**The catch:** a black silhouette multiplies to black, not to the tint. Any
silhouette that has to survive this pipeline must be drawn **white**, not black.

## The mouth is not tinted

Teeth are not lips. An early pass ran the mouth through the skin slot and every
smiling character got pink teeth. The mouth layer carries its own colours and is
drawn untinted; assets that need a colour carry a per-asset default rather than
borrowing a character slot.

## Eyes: colorize inside an authored region

Eyes cannot be multiplied with the rest. Multiplying the whole eye layer tints
the sclera and the lashes along with the iris.

Two things were tried and rejected:

- **Additive blending.** The artwork shows through, so shading and the
  highlight wash toward white. The result looks *transparent* -- this is what
  "the iris colours are see-through" was describing, and it is not a property of
  colouring the iris, only of adding to it.
- **Runtime iris detection.** Fragile on stylised art, and it has to run on
  every render for a fact that never changes.

What ships instead: the iris region is **authored once per asset** and stored in
the registry -- an ellipse, a pupil radius, a pupil offset, and a lid curve per
eye -- and the renderer maps the drawing's own luminance through the chosen
colour inside it. Dark stays dark, mid becomes the colour, the highlight stays a
highlight. It is opaque where the drawing is opaque.

The region is rasterised **antialiased** (`Graphics2D.fill` with
`KEY_ANTIALIASING`), not tested with `Area.contains`. `contains` is a yes-or-no
test, so the iris and pupil edges came out stair-stepped against artwork that is
smooth everywhere else -- invisible at portrait size and very visible on a
printed sheet. The antialiased coverage becomes a per-pixel blend weight, so the
region's edge fades into the drawing instead of stepping.

## Floor and gamma: two viewing distances, two knobs

The luminance ramp has a **floor** -- how much of the eye colour the darkest
iris ink keeps. On art whose irises are drawn almost black, a low floor loses
the colour entirely and the eye reads grey.

Raising the floor was doing two jobs at once, and they pull apart:

- a glance across the room integrates the iris into one average colour, so
  distance legibility is its **mean**;
- a close look sees the shading inside it, so how modelled it looks is the
  **spread** between its darkest and lightest ink.

Raising the floor lifts the mean *by squeezing the ramp*, which costs the
spread. Measured over the placed iris region of the sample pack:

| setting | mean | spread |
|---|---|---|
| floor 0.55, gamma 1.0 | 0.249 | 0.065 |
| floor 0.75, gamma 1.0 | 0.311 | 0.035 |
| **floor 0.55, gamma 0.5** | **0.309** | **0.114** |

The first two rows are the trade: the mean goes up a quarter and the spread
halves. This is why floor 0.55 looked better up close and floor 0.75 better at a
distance -- **both observations were correct**, and no single floor could serve
both.

The fix is a second knob. A gamma below 1 applied to the drawing's luminance
*before* the ramp reads it lifts the midtones -- where the body of the iris sits
-- while leaving the darkest ink alone. The third row is as bright on average as
floor 0.75 while keeping nearly twice the tonal range of floor 0.55.

**Settled: floor 0.55, gamma 0.5.** Raising the floor further past this buys
very little mean and costs spread quickly; dropping the gamma below ~0.4 starts
to look harsh. Both numbers are tuned to art with near-black irises and should
be re-measured if a second illustrator's work comes in with lighter ones.

## Colour slot presets

The eye slot is the one that needed the most saturation: at portrait size an
iris is a few dozen pixels and a muted hue does not read as a colour at all.
Per-slot boosts over the first presets were eyes x1.45 saturation / x1.22 value,
shirt x1.22 / x1.08, hair x1.18 / x1.06, skin x1.06 / x1.02 -- skin barely
moves, because skin is the slot where a boost reads as sunburn.

## The tests

- `test/clj/orcpub/portrait_tint_behaviour_test.clj` -- proves masking flattens
  line art and that multiply is the fix, including the black-silhouette catch.
  Runs with no artwork.
- `test/clj/orcpub/portrait_real_art_compare_test.clj` -- the rendering harness.
  Needs `ORCPUB_PACK=<dir>`; writes to `ORCPUB_PACK_OUT`; skips loudly without
  them. `gamma-separates-legibility-from-modelling` asserts the numbers above,
  **including the trade itself** -- if the floor ever stops flattening the
  spread, the reasoning behind these settings is void and the test says so.
  `reconcile-near-and-far` renders candidates at full size and at a sixth scale,
  because a glance is a different test from a look.

The real art is **not** in this repository and must not be committed. The
harness is written to skip, not fail, when it is absent.
