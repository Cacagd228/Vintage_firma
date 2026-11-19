"""Generates the (original) textures used by Vintage Firma.

Run with: python tools/gen_textures.py

The voxel texture is deliberately simple: one voxel face is only 1/16 of a block, so the metal is a
nearly flat, low contrast surface with a soft 4x4 pixel variation. Any higher frequency detail aliases
into moire or into visible "grid" seams between neighbouring voxels.
"""

import os
import random
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "vintage_firma", "textures")


def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)
    print("wrote", os.path.relpath(path, ROOT))


def clamp(value):
    return max(0, min(255, int(value)))


def voxel_metal():
    """A 16x16 metal surface, sampled as a 2x2 window per voxel face.

    Every 2x2 block gets its own tone, so a voxel face reads as a tiny patch of cast metal. Low contrast
    on average, with visible blotches (darker and lighter, a few warmer ones) so neighbouring voxels never
    look like a repeating tile.
    """
    rng = random.Random(11)
    # A handful of soft blotches: (cx, cy, radius, strength, warmth)
    blotches = [
        (2, 3, 4.5, -22, -4),
        (11, 2, 3.6, 18, 3),
        (6, 9, 5.0, -16, 2),
        (13, 12, 3.0, 20, 6),
        (2, 13, 3.6, 14, -2),
        (9, 15, 2.6, -18, 0),
    ]

    # One tone per 2x2 block
    tones = {}
    for by in range(8):
        for bx in range(8):
            tones[(bx, by)] = 150 + rng.randint(-9, 9)

    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            base = tones[(x // 2, y // 2)]
            warm = 0
            for cx, cy, radius, strength, warmth in blotches:
                distance = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if distance < radius:
                    falloff = 1.0 - distance / radius
                    base += int(strength * falloff)
                    warm += int(warmth * falloff)
            row.append((clamp(base + warm), clamp(base), clamp(base - warm // 2), 255))
        pixels.append(row)
    return pixels


def work_item():
    rng = random.Random(21)
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            dx = abs(x - 7.5) / 7.0
            dy = abs(y - 8.0) / 4.9
            distance = (dx * dx + dy * dy) ** 0.5
            if distance >= 1.0:
                row.append((0, 0, 0, 0))
                continue

            shade = 128 + rng.randint(-8, 8) - int(30 * distance)
            if distance < 0.9 and (x * 3 - y * 4) < -18:
                shade += 26
            if distance > 0.84:
                shade -= 16
            row.append((clamp(shade), clamp(shade + 2), clamp(shade + 6), 255))
        pixels.append(row)
    return pixels


if __name__ == "__main__":
    write_png(os.path.join(ASSETS, "block", "voxel_metal.png"), voxel_metal())
    write_png(os.path.join(ASSETS, "item", "work_item.png"), work_item())