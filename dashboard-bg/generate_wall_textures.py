#!/usr/bin/env python3
"""Generate subordinate wall-finish backgrounds for Muse Ambient.

The fields are deliberately non-compositional: correlated multi-scale noise,
microtexture, and only a few code values of broad tonal travel.  There are no
shapes, bands, color masses, or directional gestures.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

W, H = 3840, 2160
OUT = Path(__file__).resolve().parent


def resized_random(rng, grid_w, grid_h):
    small = rng.random((grid_h, grid_w), dtype=np.float32)
    image = Image.fromarray(np.uint8(np.clip(small, 0, 1) * 255), mode="L")
    return np.asarray(image.resize((W, H), Image.Resampling.BICUBIC), dtype=np.float32) / 255.0


def fractal_field(seed, weights):
    rng = np.random.default_rng(seed)
    field = np.zeros((H, W), dtype=np.float32)
    total = 0.0
    for (grid_w, grid_h), weight in weights:
        field += resized_random(rng, grid_w, grid_h) * weight
        total += weight
    field /= total
    low, high = np.percentile(field, (0.5, 99.5))
    return np.clip((field - low) / (high - low), 0, 1)


def colorize(field, low_rgb, high_rgb, vertical_levels=0.0):
    low = np.array(low_rgb, dtype=np.float32)
    high = np.array(high_rgb, dtype=np.float32)
    image = low[None, None, :] + field[:, :, None] * (high - low)[None, None, :]
    if vertical_levels:
        # A barely perceptible lighting falloff, not a horizon or band.
        ramp = np.linspace(vertical_levels / 2, -vertical_levels / 2, H, dtype=np.float32)
        image += ramp[:, None, None]
    return image


def finish(image, seed, grain_sigma, blur_radius):
    rng = np.random.default_rng(seed)
    grain = rng.normal(0.0, grain_sigma, (H, W)).astype(np.float32)
    image += grain[:, :, None]
    result = Image.fromarray(np.uint8(np.clip(image, 0, 255)), mode="RGB")
    if blur_radius:
        result = result.filter(ImageFilter.GaussianBlur(blur_radius))
    return result


def plaster_warm():
    broad = fractal_field(
        211021,
        [((5, 3), 0.62), ((11, 6), 0.24), ((23, 13), 0.10), ((47, 27), 0.04)],
    )
    # Keep the broad field centered and tame its tails: this is a wall, not a
    # landscape.  Total broad travel is roughly 15 code values before grain.
    field = 0.5 + (broad - 0.5) * 0.72
    rgb = colorize(field, (226, 216, 201), (244, 239, 230), vertical_levels=2.0)
    return finish(rgb, seed=711, grain_sigma=0.72, blur_radius=0.38)


def linen_pale():
    broad = fractal_field(
        211107,
        [((6, 3), 0.58), ((13, 7), 0.27), ((29, 16), 0.11), ((59, 33), 0.04)],
    )
    field = 0.5 + (broad - 0.5) * 0.58
    rgb = colorize(field, (233, 227, 216), (248, 244, 237), vertical_levels=1.2)

    # Fine woven structure: two nearly equal thread directions plus a little
    # irregularity.  Amplitude stays around one code value after softening.
    yy, xx = np.mgrid[0:H, 0:W]
    weave = (
        np.sin(2 * np.pi * xx / 4.0) * 0.42
        + np.sin(2 * np.pi * yy / 4.0) * 0.42
    ).astype(np.float32)
    rng = np.random.default_rng(812)
    slub = resized_random(rng, 960, 540)
    weave += (slub - 0.5) * 0.9
    rgb += weave[:, :, None]
    return finish(rgb, seed=812, grain_sigma=0.48, blur_radius=0.24)


def main():
    variants = {
        "wall-texture-plaster-warm.jpg": plaster_warm(),
        "wall-texture-linen-pale.jpg": linen_pale(),
    }
    for name, image in variants.items():
        image.save(OUT / name, quality=96, subsampling=0, optimize=True)
        print(name, image.size)


if __name__ == "__main__":
    main()
