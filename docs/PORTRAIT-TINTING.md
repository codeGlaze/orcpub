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

What ships instead: the iris region is **authored once per asset** in the Loom
and stored in the registry as `:asset/iris` / `:asset/pupil` on each eye style
(`portrait_assets.cljc`) -- an ellipse, a pupil radius, a pupil offset, and a lid
curve per eye -- and the renderer maps the drawing's own luminance through the
chosen colour inside it.

All three renderers do this, from one module, `portrait_colorize.cljc`: the maths
and the region geometry live there, and only the pixel loop and the drawing of
the region are per renderer. The share card fills the region with Java2D; the
builder's drawer and the PDF bake fill it on a canvas, and the drawer shows those
layers as `<canvas>` elements with `object-fit: contain`, since CSS can't map
luminance through a colour. The lips use the same function over the whole asset.
An eye style with no placed region is multiplied instead, because colouring the
whole asset would paint the whites and the lashes too. Dark stays dark, mid becomes the colour, the highlight stays a
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

## Hair streaks follow the drawn strands

The ombré's Streaks setting moves where the tip colour starts, strand by
strand. It was first a wave of horizontal position only, so every streak was
a vertical stripe. On long straight hair that passed, but on the short cut,
which is swept and curled all over, it read as paint on glass.

**Reversal:** streaks are no longer a function of x alone. `strand-field` in
`portrait_effects.cljc` reads the strand direction from the linework (the
smoothed structure tensor of the ink; the lines run along the strands). It
points that direction away from the crown and falls back to "outward from the
crown" where nothing is drawn. It then averages noise along that direction
(line integral convolution).

- **Cost:** about 200ms per piece on the JVM at 1200x1500, on a 4px grid.
  Nearly all of the first 400ms was a box blur whose local helper fns boxed
  every value; plain index arithmetic halved it.
- **Worked out once, ahead of time:** the field depends only on the art, not
  on the head or the colours. It is computed in the art's own pixels, with
  the crown taken from the piece itself, and written beside the art by
  `orcpub.portrait-pack.strands` (see PORTRAIT-PACK.md). The share card maps
  its frame pixels back through the art's placement, so all three renderers
  read the same field. Without the file, a renderer computes the field once
  and keeps it.
- **Identical renderers:** the noise hash is 32-bit integer maths
  (`Math.imul` in JS, `unchecked-multiply-int` on the JVM). A test pins its
  values, so every renderer streaks the same way.
- **Bug to avoid:** a direction field has no sign, so each step of the walk
  keeps the heading of the last *field* direction, not the last *motion*.
  Comparing against motion flipped the backward walk at every step, and the
  smear went back and forth on the spot. That produced blotches, not streaks.

## Eye and skin effects, hair shine and split

All in shared cljc (`portrait_face.cljc` for eyes and skin, `portrait_effects.cljc`
for hair), so the share card, the builder and the PDF draw them identically.

- **Light from below (always on):** the lower part of each iris is lifted in
  its own colour, the way light comes through a real iris. No catchlight is
  added: the art already draws one beside the pupil, and a second highlight
  read as salt on the real art, however it was shaped.
- **Second eye colour** goes to the eye on the right of the picture.
- **Snake and goat pupils** are painted over the drawn round pupil, in the
  iris's own middle tone, then the slit or bar on top. They are placed in
  SCREEN space: several eyes were placed in the Loom with the oval turned
  90 degrees, and a slit that followed the oval's rotation came out sideways.
- **Blush and freckles** are multiplied onto skin that shows, the same mask as
  the cast shadows, and placed from the eye placements, so no piece needs a
  cheek or a nose marked. They share the skin overlay with the shadows.
- **Shine:** one clean ring across each crown piece (front hair, bangs),
  measured along that piece's own crown line, crisp edged, in the hair's own
  hue lifted toward white, on the fill only with the paper grain flattened.
  Two attempts were rejected on the real art: a band broken along the strand
  field read as salt drying on cloth, and one ellipse across the whole head
  arched over every piece like a halo. Each piece takes its own ring.
  The ring sits the same depth below every piece's top, set by the Shine at
  slider (default 0.3, chosen on the purple short cut). It used to sit 22% of
  each piece's own length down, which put a long piece's ring by the ear and
  well below the fringe's.
- **Split:** the tips colour on one side of a line down through the crown.
  The line and its wobble are measured on the frame, the same for every
  piece. Each piece's own measure put the line somewhere different on each
  piece, and the hair behind the bangs showed a wedge of the other colour.
  Angle mode had the same fault and is now measured from the crown too. A
  split or an angle ignores the bangs' hold-the-root-colour choice.

**Cost bug:** blush and freckles first took 105s per share card. The skin masks
were untyped locals, so every pixel read in the overlay loop went through
reflection. With type hints the effects cost nothing measurable (3.4s, the
same as without them).

**Art bug, ours:** a scratch copy of the art had every enclosed gap in the hair
filled white, from a test during the scalp work. Bangs 03 has two arcs that
touch at the tip; filling the gap between them drew a jagged sliver over the
face. Mock-ups must be rendered from the artist's pack as delivered.

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
