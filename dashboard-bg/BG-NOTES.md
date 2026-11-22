# Dashboard background, selection notes (2026-11-21)

Reference: the large abstract painting over Jonathan's couch
(`~/workspace/user/files/c612a53e-..._2_2pa6.jpg`): warm off-white and
greige field, blocky taupe/camel masses center-right, slate blue-grey
and muted teal low in the canvas, thin sketchy gestural lines, thin
metallic frame, matte and calm. The room around it: warm white walls,
grey couch, camel/tan textiles, warm lamp light.

Three original candidates were generated with the painting photo as a
style reference (all 2048x1152 native, 16:9):

- `candidate-1-faithful.png`: blocky taupe/camel masses center-right,
  slate blue and muted teal accents, sketchy lines, pale quiet left
  third and corners. Closest to the painting's actual vocabulary.
- `candidate-2-airy.png`: luminous warm-white washes, gesture reduced
  to a low drift of taupe and slate. Calmest for text, but reads as
  watercolor mist/seascape rather than the painting's painterly blocks.
- `candidate-3-bands.png`: strong slate horizon band across the whole
  bottom edge and a large central taupe mass; the band fights any low
  text and the composition feels landscape-literal.

## Winner: candidate 1 (`dashboard-bg.jpg`)

It is the only candidate that speaks the painting's own language:
same palette (warm white, greige, taupe, camel, slate blue-grey,
muted teal), same blocky painterly gesture gathered center-right,
same thin sketchy lines. Its quiet zones line up with the dashboard
layout without forcing it: the left column (clock, hero line, ledger,
brief) is pale and empty, the top-right notification corner is pale
with only a faint wash, and the painting's energy sits center-right
where the dashboard leaves open canvas.

Production notes: generated at 2048x1152 (the generator's largest
16:9), upscaled 2x to 3840x2160 with Lanczos and saved as JPEG q90
(730 KB). A soft abstract tolerates the upscale; there are no hard
edges anywhere in the piece to ring or alias. The app additionally
runs its matte treatment over it, which hides any remaining softness.

## Superseded: quiet wall texture direction (2026-11-21)

Jonathan changed the direction after the abstract selection: two
competing art pieces across from each other is not good feng shui.
The painting over the couch remains the room's one real artwork, and
the TV backdrop must be subordinate. The former winner is preserved
as `superseded-abstract-winner-candidate-1.jpg`; all three abstract
candidates remain in this folder.

Two native 3840x2160 wall finishes were built procedurally with
Python PIL and NumPy (`generate_wall_textures.py`). Procedural
generation was chosen because its contrast and spatial character can
be controlled exactly. Both variants use correlated multi-scale
noise and microtexture only: no composition, blocks, focal gesture,
bands, slate masses, or teal.

- `wall-texture-plaster-warm.jpg`: warm greige plaster with broad,
  mineral tonal variation and a very slight lighting falloff. Its
  measured 8-bit luminance is min 216, max 239, mean 228.15, standard
  deviation 4.17; the 1st to 99th percentile range is 219 to 236.
  Mean RGB is 234.8, 227.5, 215.4.
- `wall-texture-linen-pale.jpg`: paler warm greige with a fine woven
  structure. Its measured 8-bit luminance is min 228, max 242, mean
  235.00, standard deviation 2.15; the 1st to 99th percentile range
  is 231 to 240. Mean RGB is 239.1, 234.9, 225.4.

### New winner: plaster warm

`wall-texture-plaster-warm.jpg` is now `dashboard-bg.jpg` and is the
asset copied to `app/src/main/assets/dashboard-bg.jpg`. It won at
both full size and couch-distance downscale. The plaster has enough
warmth and tonal travel to feel like a finished wall and to ground
future dashboard text, while its complete 23-level luminance range
keeps it visually quiet. The linen is cleaner and paler, but at couch
distance it comes closer to a blank panel; its weave is mostly a
near-field detail and carries more moire risk on a TV panel.

Judged beside the couch painting, the plaster does not read as a
second artwork. It has no object, scene, horizon, gesture, or color
event to hold attention. It reads as the room's warm wall surface,
with the painting retaining all of the contrast, movement, and
authorship.
