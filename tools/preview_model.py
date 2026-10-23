"""Renders Minecraft block models (JSON "elements" cuboids) to PNG previews, so a model can be checked without
starting the game. Only what Voidra Tech models use is supported: axis-aligned cuboids, per-face "uv", no element
or face rotation.

Usage: python tools/preview_model.py <model.json> <textures dir> <out.png>
Or import render_views(model, textures_dir) from another script.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}
NORMALS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def corners(face, f, t):
    """Face corners in texture order: top-left, top-right, bottom-right, bottom-left."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
    }[face]


def default_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


def _norm(v):
    length = math.sqrt(sum(c * c for c in v))
    return tuple(c / length for c in v)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def resolve(ref, textures):
    while ref.startswith("#"):
        ref = textures[ref[1:]]
    return ref


def render(model, textures_dir, view=(1, 1, 1), scale=None, size=320):
    """Orthographic render with a depth buffer, so parts inside other parts are hidden correctly."""
    scale = scale or size / 30
    d = _norm(view)
    right = _norm(_cross((0, 1, 0), d))
    up = _cross(d, right)
    cache = {}

    def texture(ref):
        name = resolve(ref, model["textures"]).split(":")[-1].split("/")[-1]
        if name not in cache:
            cache[name] = np.asarray(Image.open(Path(textures_dir) / f"{name}.png").convert("RGBA"), dtype=np.float32)
        return cache[name]

    def project(p):
        c = (p[0] - 8, p[1] - 8, p[2] - 8)
        return np.array([size / 2 + _dot(c, right) * scale, size / 2 - _dot(c, up) * scale, _dot(c, d)])

    colour = np.zeros((size, size, 4), dtype=np.float32)
    depth = np.full((size, size), -np.inf, dtype=np.float32)
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32) + 0.5

    for element in model["elements"]:
        f, t = element["from"], element["to"]
        glow = element.get("light_emission", 0) > 0
        for face, spec in element["faces"].items():
            if _dot(NORMALS[face], d) <= 0:
                continue
            tex = texture(spec["texture"])
            th, tw = tex.shape[:2]
            u1, v1, u2, v2 = spec.get("uv", default_uv(face, f, t))
            tl, tr, br, bl = (project(q) for q in corners(face, f, t))
            ea, eb = tr - tl, bl - tl
            det = ea[0] * eb[1] - ea[1] * eb[0]
            if abs(det) < 1e-9:
                continue
            x0, x1 = int(max(0, min(tl[0], tr[0], br[0], bl[0]))), int(min(size, max(tl[0], tr[0], br[0], bl[0]) + 1))
            y0, y1 = int(max(0, min(tl[1], tr[1], br[1], bl[1]))), int(min(size, max(tl[1], tr[1], br[1], bl[1]) + 1))
            if x0 >= x1 or y0 >= y1:
                continue
            px = xs[y0:y1, x0:x1] - tl[0]
            py = ys[y0:y1, x0:x1] - tl[1]
            a = (px * eb[1] - py * eb[0]) / det
            b = (py * ea[0] - px * ea[1]) / det
            inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
            z = tl[2] + a * ea[2] + b * eb[2] + 0.001
            tu = np.clip(((u1 + a * (u2 - u1)) * tw / 16).astype(int), 0, tw - 1)
            tv = np.clip(((v1 + b * (v2 - v1)) * tw / 16).astype(int), 0, th - 1)
            texel = tex[tv, tu]
            win = inside & (texel[..., 3] > 0) & (z > depth[y0:y1, x0:x1])
            shade = 1.0 if glow else SHADE[face]
            region = colour[y0:y1, x0:x1]
            region[win, :3] = texel[win, :3] * shade
            region[win, 3] = 255
            depth[y0:y1, x0:x1][win] = z[win]
    return Image.fromarray(np.clip(colour, 0, 255).astype(np.uint8), "RGBA")


def render_views(model, textures_dir, size=320):
    """Front-right and front-left views side by side."""
    a = render(model, textures_dir, (1, 0.9, 1.2), size=size)
    b = render(model, textures_dir, (-1, 0.9, 1.2), size=size)
    out = Image.new("RGBA", (size * 2, size), (0, 0, 0, 0))
    out.alpha_composite(a, (0, 0))
    out.alpha_composite(b, (size, 0))
    return out


if __name__ == "__main__":
    render_views(json.loads(Path(sys.argv[1]).read_text()), sys.argv[2]).save(sys.argv[3])
