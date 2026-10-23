"""Draws Voidra Tech's 16x16 block textures: dark gunmetal plates with glowing trim in each tier's colour.

Usage: python tools/gen_textures.py .
"""
import colorsys
import math
import random
import sys
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1]) / "src" / "main" / "resources" / "assets" / "voidratech" / "textures" / "block"
S = 16

# Glow colour of each tier, and the plate tint (Voidrium is darker, almost black).
TIERS = {
    "umbrite": ((132, 152, 184), (44, 47, 55)),
    "nebulite": ((214, 120, 214), (46, 42, 54)),
    "obscurium": ((64, 110, 236), (36, 40, 56)),
    "penumbrite": ((64, 222, 214), (38, 48, 52)),
    "eclipsium": ((244, 186, 58), (52, 46, 40)),
    "voidrium": ((176, 72, 255), (24, 20, 30)),
}
VOID = (150, 90, 230)
GREY = (92, 96, 104)


def shade(colour, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in colour)


def plate(base, seed):
    """Brushed metal plate with a dark rim and a light bevel on the top and left edges."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (S, S))
    for y in range(S):
        for x in range(S):
            img.putpixel((x, y), shade(base, 1.0 + rng.uniform(-0.06, 0.06)) + (255,))
    for i in range(S):
        img.putpixel((i, 0), shade(base, 1.35) + (255,))
        img.putpixel((0, i), shade(base, 1.35) + (255,))
        img.putpixel((i, S - 1), shade(base, 0.55) + (255,))
        img.putpixel((S - 1, i), shade(base, 0.55) + (255,))
    return img


def put(img, x, y, colour):
    img.putpixel((x, y), tuple(colour) + (255,))


def glow_line(img, points, colour):
    """A glowing line: bright core pixels."""
    for x, y in points:
        put(img, x, y, colour)


def frame(base, glow, seed):
    """Fine metal mesh over a faint glow in the tier colour, like a reactor shield."""
    img = Image.new("RGBA", (S, S))
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5) / 10.6
            # Vivid glow in the middle, fading into the dark plate towards the edges.
            behind = mix(shade(glow, 1.2), shade(base, 0.4), min(1.0, d * 1.15))
            put(img, x, y, shade(base, 1.05) if (x % 3 == 0 or y % 3 == 0) else behind)
    for i in range(S):
        put(img, i, 0, shade(base, 1.45))
        put(img, 0, i, shade(base, 1.45))
        put(img, i, S - 1, shade(base, 0.5))
        put(img, S - 1, i, shade(base, 0.5))
    return img


def mix(a, b, t):
    return tuple(int(x + (y - x) * t) for x, y in zip(a, b))


# --- Void Drill controller ("coiled reactor" model, see gen_resources.py) ------------------------------------
def brushed(base, seed):
    """Plate texture without the rim, for parts whose edges come from the model."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (S, S))
    for y in range(S):
        for x in range(S):
            put(img, x, y, shade(base, 1.0 + rng.uniform(-0.06, 0.06)))
    return img


def drill_eye(base, glow, seed):
    """Top: the void eye, a dark disc ringed in the tier colour."""
    img = plate(base, seed)
    for y in range(S):
        for x in range(S):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.2:
                put(img, x, y, (8, 4, 14))
            elif d < 3.3:
                put(img, x, y, shade(VOID, 0.55))
            elif d < 4.4:
                put(img, x, y, glow)
            elif d < 5.2:
                put(img, x, y, shade(base, 0.5))
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, cx, cy, shade(base, 1.6))
    return img


def drill_strip(base, glow, seed):
    """Sides of the base and the cap: metal with a dashed glowing line on rows 7-8 (the model shows rows 6-9)."""
    img = brushed(base, seed)
    for x in range(S):
        put(img, x, 0, shade(base, 1.4))
        put(img, x, S - 1, shade(base, 0.5))
        for y in (7, 8):
            put(img, x, y, glow if x % 4 else shade(glow, 0.5))
        put(img, x, 6, shade(base, 0.5))
        put(img, x, 9, shade(base, 0.5))
    return img


def drill_ribbed(base, seed):
    """Corner posts: horizontal ribs."""
    img = brushed(base, seed)
    for y in range(S):
        for x in range(S):
            if y % 3 == 0:
                put(img, x, y, shade(base, 1.4))
            elif y % 3 == 2:
                put(img, x, y, shade(base, 0.6))
    return img


def drill_core(glow, seed):
    """The glowing core: brightest in the middle, darker towards the edges."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (S, S))
    for y in range(S):
        for x in range(S):
            d = max(abs(x - 7.5), abs(y - 7.5)) / 8
            put(img, x, y, shade(glow, 1.35 - 0.7 * d + rng.uniform(-0.08, 0.08)))
    for i in range(S):
        for x, y in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            put(img, x, y, shade(glow, 0.4))
    return img


def drill_coil(base, glow, seed):
    """The coil bands around the core: light top edge, dark bottom edge, faint glowing windings."""
    img = brushed(base, seed)
    for x in range(S):
        put(img, x, 0, shade(base, 1.6))
        put(img, x, S - 1, shade(base, 0.5))
        if x % 4 == 1:
            for y in range(1, S - 1):
                put(img, x, y, shade(glow, 0.45))
    return img


def emitter_side(base, glow, seed):
    """Beam emitter side: the frame's mesh with a bright core line down the middle. No top/bottom rim, so a column
    of emitters reads as one long piece."""
    img = Image.new("RGBA", (S, S))
    core = mix(glow, (255, 255, 255), 0.6)
    for y in range(S):
        for x in range(S):
            t = min(1.0, abs(x - 7.5) / 7.5 * 1.1)
            behind = mix(shade(glow, 1.2), shade(base, 0.4), t)
            bar = x % 3 == 0 or (y % 3 == 0 and x not in (7, 8))
            put(img, x, y, shade(base, 1.05) if bar else behind)
        put(img, 7, y, core)
        put(img, 8, y, shade(glow, 1.1))
        put(img, 0, y, shade(base, 1.4))
        put(img, S - 1, y, shade(base, 0.5))
    return img


def emitter_end(base, glow, seed):
    """Beam emitter ends: the bore, a bright core ringed in the tier colour."""
    img = brushed(base, seed)
    core = mix(glow, (255, 255, 255), 0.65)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.6:
                put(img, x, y, core)
            elif d < 2.9:
                put(img, x, y, glow)
            elif d < 3.7:
                put(img, x, y, shade(base, 0.45))
            elif 5.0 < d < 5.8:
                put(img, x, y, shade(base, 1.45))
            elif 5.8 <= d < 6.5:
                put(img, x, y, shade(base, 0.6))
    for i in range(S):
        put(img, i, 0, shade(base, 1.35))
        put(img, 0, i, shade(base, 1.35))
        put(img, i, S - 1, shade(base, 0.55))
        put(img, S - 1, i, shade(base, 0.55))
    return img


# --- Lens mount: bolted mechanical housing with a crystal lens underneath ------------------------------------------
def lens_side(base, glow, seed):
    """Bolted panel with eight rivets and a short glowing slot."""
    img = plate(base, seed)
    for y in range(3, 13):
        for x in range(3, 13):
            put(img, x, y, shade(base, 0.75 if x in (3, 12) or y in (3, 12) else 0.95))
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 2), (7, 13), (2, 7), (13, 7)):
        put(img, cx, cy, shade(GREY, 1.4))
        put(img, cx + 1, cy + 1, shade(base, 0.5))
    for x in range(5, 11):
        put(img, x, 7, shade(glow, 0.8))
        put(img, x, 8, shade(glow, 0.5))
    return img


def lens_top(base, glow, seed):
    """Top: corner rivets and the bore the beam comes in through from the emitters."""
    img = plate(base, seed)
    core = mix(glow, (255, 255, 255), 0.65)
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, cx, cy, shade(GREY, 1.4))
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.0:
                put(img, x, y, core)
            elif d < 3.3:
                put(img, x, y, glow)
            elif d < 4.1:
                put(img, x, y, shade(base, 0.45))
    return img


def lens_bottom(base, glow, seed):
    """Bottom, where the beam leaves: a crystal lens in a grey ring, bright in the middle with a white glint."""
    img = plate(base, seed)
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, cx, cy, shade(GREY, 1.4))
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.0:
                glint = (x, y) in ((5, 5), (6, 5), (5, 6))
                put(img, x, y, (255, 255, 255) if glint else mix(mix(glow, (255, 255, 255), 0.4), shade(glow, 0.55), d / 5))
            elif d < 6.0:
                put(img, x, y, shade(GREY, 1.35 if x + y < 15 else 0.75))
            elif d < 6.7:
                put(img, x, y, shade(base, 0.45))
    return img


def stabilizer(base, glow, seed):
    """Energy cross: four raised corner plates around a sunken channel in the tier colour, a glowing diamond where it
    crosses. The channel reaches every edge, so rows of stabilizers join up."""
    core = mix(glow, (255, 255, 255), 0.65)
    img = brushed(base, seed)
    for qx in (0, 10):
        for qy in (0, 10):
            for x in range(qx, qx + 6):
                for y in range(qy, qy + 6):
                    light = x == qx or y == qy
                    dark = x == qx + 5 or y == qy + 5
                    put(img, x, y, shade(base, 1.45 if light else 0.6 if dark else 1.1))
            # Mirrored, so the rivets sit at 2 and 13 on both axes.
            put(img, 2 if qx == 0 else 13, 2 if qy == 0 else 13, shade(GREY, 1.4))
    for i in range(S):
        for c in range(6, 10):
            put(img, i, c, shade(base, 0.4))
            put(img, c, i, shade(base, 0.4))
        for c in (7, 8):
            put(img, i, c, shade(glow, 0.9 if c == 7 else 0.65))
            put(img, c, i, shade(glow, 0.9 if c == 7 else 0.65))
    for x in range(S):
        for y in range(S):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 2.5:
                put(img, x, y, core)
            elif d < 4:
                put(img, x, y, glow)
            elif d < 5:
                put(img, x, y, shade(base, 0.45))
    return img


# Lens colours: the vanilla dye colours.
LENS_COLOURS = {
    "white": (249, 255, 254), "orange": (249, 128, 29), "magenta": (199, 78, 189), "light_blue": (58, 179, 218),
    "yellow": (254, 216, 61), "lime": (128, 199, 31), "pink": (243, 139, 170), "gray": (71, 79, 82),
    "light_gray": (157, 157, 151), "cyan": (22, 156, 156), "purple": (137, 50, 184), "blue": (60, 68, 170),
    "brown": (131, 84, 50), "green": (94, 124, 22), "red": (176, 46, 38), "black": (29, 29, 33),
}


def lens(colour, seed):
    """A coloured crystal lens in a dark metal frame with grey corner rivets; the crystal is lighter at the top left
    and has a white glint, so it reads as glass."""
    base = (40, 43, 50)
    img = plate(base, seed)
    for y in range(2, 14):
        for x in range(2, 14):
            if x in (2, 13) or y in (2, 13):
                put(img, x, y, shade(base, 0.45))
            else:
                t = ((x - 3) + (y - 3)) / 20
                put(img, x, y, mix(mix(colour, (255, 255, 255), 0.35), shade(colour, 0.6), t))
    for x, y in ((4, 4), (5, 4), (4, 5), (10, 11), (11, 10)):
        put(img, x, y, (255, 255, 255) if (x, y) != (10, 11) and (x, y) != (11, 10) else mix(colour, (255, 255, 255), 0.6))
    for cx, cy in ((0, 0), (14, 0), (0, 14), (14, 14)):
        for dx in range(2):
            for dy in range(2):
                put(img, cx + dx, cy + dy, shade(GREY, 1.4 if (dx, dy) == (0, 0) else 0.9))
    return img


def clear_lens(seed):
    """The clear lens: the coloured lenses' pixels exactly (frame, rivets, glints), but the glass is colourless and
    see-through, faintly lighter at the top left like the others."""
    img = lens((255, 255, 255), seed)
    for y in range(3, 13):
        for x in range(3, 13):
            if (x, y) in ((4, 4), (5, 4), (4, 5), (10, 11), (11, 10)):
                img.putpixel((x, y), (255, 255, 255, 220 if x < 8 else 150))
            else:
                t = ((x - 3) + (y - 3)) / 20
                img.putpixel((x, y), (235, 242, 246, int(70 - 40 * t)))
    return img


def void_plating(seed, item=False):
    """Floor of the drill: black, no tier colour. Four bevelled plates, each with one small grey rivet in its middle; the
    seams line up with the neighbouring blocks, so a floor reads as one plated surface."""
    base = (26, 26, 31)
    img = plate(base, seed)
    for qx in (0, 8):
        for qy in (0, 8):
            for x in range(qx, qx + 8):
                for y in range(qy, qy + 8):
                    light = x == qx or y == qy
                    dark = x == qx + 7 or y == qy + 7
                    put(img, x, y, shade(base, 1.6 if light else 0.55 if dark else 1.0))
            # One-pixel rivet like the stabilizer's, mirrored so the four sit at 3 and 12. The item icon shrinks the
            # top face so much that one pixel vanishes: its texture gets 2x2 rivets instead.
            rx, ry = (3 if qx == 0 else 12), (3 if qy == 0 else 12)
            if item:
                rx, ry = (2 if qx == 0 else 12), (2 if qy == 0 else 12)
                for dx in range(2):
                    for dy in range(2):
                        put(img, rx + dx, ry + dy, shade(GREY, 1.4 if (dx, dy) == (0, 0) else 1.1))
            else:
                put(img, rx, ry, shade(GREY, 1.4))
    return img


def null_modifier(seed, item=False):
    """Null modifier: the floor's four plates and rivets, with a large empty socket recessed in the middle, an open
    slot waiting for a real modifier."""
    base = (26, 26, 31)
    img = void_plating(seed, item)
    for y in range(4, 12):
        for x in range(4, 12):
            put(img, x, y, shade(base, 0.4 if x == 4 or y == 4 else 1.4 if x == 11 or y == 11 else 0.6))
    return img


# 6x6 symbols for the modifiers' sockets: X = glow, O = bright core.
FAST_FORWARD = ["O..O..", "XX.XX.", "XXXXXX", "XXXXXX", "XX.XX.", "X..X.."]
LIGHTNING = ["...OX.", "..OX..", ".OXXX.", "..XX..", ".XX...", "XX...."]
LENS_FOCUS = ["XX..XX", "X.XX.X", ".XOOX.", ".XOOX.", "X.XX.X", "XX..XX"]


def modifier(seed, glow, symbol, item=False):
    """A working modifier: the null modifier with its socket lit from inside and a glowing symbol in it."""
    img = null_modifier(seed, item)
    core = mix(glow, (255, 255, 255), 0.55)
    for y in range(5, 11):
        for x in range(5, 11):
            ch = symbol[y - 5][x - 5]
            put(img, x, y, core if ch == "O" else glow if ch == "X" else shade(glow, 0.28))
    return img


def infinite_energy_cell(seed):
    """Testing block: dark plate, magenta-to-cyan rim and a lightning bolt."""
    img = plate((30, 26, 38), seed)
    for i in range(1, S - 1):
        mix = i / (S - 2)
        c = tuple(int(a + (b - a) * mix) for a, b in zip((235, 70, 220), (70, 225, 240)))
        put(img, i, 1, c)
        put(img, 1, i, c)
        put(img, i, S - 2, c)
        put(img, S - 2, i, c)
    bolt = [(9, 3), (8, 4), (8, 5), (7, 6), (7, 7), (6, 8), (7, 8), (8, 8), (9, 8), (8, 9), (8, 10), (7, 11), (7, 12), (6, 13)]
    for x, y in bolt:
        put(img, x, y, (255, 245, 170))
        if x + 1 < S - 2:
            put(img, x + 1, y, (240, 190, 60))
    return img


def assembly_wand(held=False):
    """Staff with a wrapped grip, a steel rod and a crook curling round an Umbrite orb (approved option "3B").
    Light from the top left; every pixel touching the sprite gets a dark outline of its colour.

    held=True mirrors the crook across the rod, for the model in the hand: there the texture's lower-right side faces
    up, so the approved icon's crook would hang upside down."""
    steel = [(20, 20, 30), (44, 46, 60), (72, 76, 94), (108, 114, 136), (152, 160, 184), (204, 212, 230)]
    leather = [(28, 16, 26), (52, 30, 46), (80, 48, 70), (110, 70, 98)]
    orb = ramp(TIERS["umbrite"][0])[1:]
    px = {}
    # Grip: alternating bands of leather wrap, along the diagonal like vanilla's stick.
    for x in range(1, 5):
        a, b = (leather[3], leather[1]) if x % 2 else (leather[2], leather[0])
        px[(x, 15 - x)] = a
        px[(x, 16 - x)] = b
    # Rod, with a light steel band where it meets the grip.
    for x in range(5, 9):
        px[(x, 15 - x)] = steel[4]
        px[(x, 16 - x)] = steel[2]
    px[(5, 10)] = steel[5]
    px[(5, 11)] = steel[3]
    stamps = (
        (9, 1, ["..abbb.", ".ab..cd", "ab....d", "a.....d", "a......", "b......"],
         {"a": steel[5], "b": steel[4], "c": steel[3], "d": steel[2]}),
        (11, 3, [".hm.", "hwtm", "mtts", ".ss."],
         {"w": orb[6], "h": orb[5], "t": orb[4], "m": orb[3], "s": orb[1]}),
    )
    for i, (x0, y0, rows, tones) in enumerate(stamps):
        if held and i == 1:
            # The orb keeps its own shading; only its place is mirrored.
            x0, y0 = S - 1 - (y0 + len(rows) - 1), S - 1 - (x0 + len(rows[0]) - 1)
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    x, y = x0 + dx, y0 + dy
                    if held and i == 0:
                        x, y = S - 1 - y, S - 1 - x
                    px[(x, y)] = tones[ch]
    outline = {}
    for (x, y), c in px.items():
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in px and 0 <= n[0] < S and 0 <= n[1] < S:
                outline.setdefault(n, (max(8, int(c[0] * 0.22) + 6), max(6, int(c[1] * 0.18) + 4),
                                       max(12, int(c[2] * 0.26) + 10)))
    px.update(outline)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for pos, c in px.items():
        img.putpixel(pos, tuple(c) + (255,))
    return img


# --- Tier crystals: one gem per tier, each a different cut, and its storage block --------------------------------
# Tones 0..7 index a ramp built from the tier's glow colour: 0 = outline .. 7 = highlight.
def ramp(rgb, dark=False):
    """8 tones. Hue drifts towards violet in the shadows and towards warm white in the lights; saturation peaks in
    the middle. dark=True is for Voidrium: near-black body, glowing lights."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    tones = []
    values = [0.16, 0.26, 0.40, 0.56, 0.74, 0.88, 0.97, 1.0] if not dark else [0.07, 0.11, 0.17, 0.24, 0.33, 0.55, 0.85, 1.0]
    sats = [0.85, 0.85, 0.82, 0.78, 0.72, 0.55, 0.32, 0.10] if not dark else [0.8, 0.8, 0.75, 0.7, 0.65, 0.75, 0.55, 0.15]
    for i, (val, sat) in enumerate(zip(values, sats)):
        t = (i - 4) / 4  # -1 shadows .. +0.75 lights
        target = 0.75 if t < 0 else 0.14  # violet / warm yellow
        d = ((target - h + 0.5) % 1.0) - 0.5
        hh = (h + d * abs(t) * 0.12) % 1.0
        r, g, b = colorsys.hsv_to_rgb(hh, min(1.0, s * sat / 0.72 if s > 0.25 else sat * 0.45), val)
        tones.append((int(r * 255), int(g * 255), int(b * 255)))
    return tones


def tone_rows(rows):
    """Tone map from rows of digits ("." = transparent)."""
    return {(x, y): int(ch) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch != "."}


def outlined(inside):
    """Adds the two-tone outline around a set of pixels: lighter (1) on the top-left, darker (0) on the bottom-right."""
    t = {}
    for (x, y) in inside:
        for qx, qy, side in ((x - 1, y, 1), (x, y - 1, 1), (x + 1, y, 0), (x, y + 1, 0)):
            if (qx, qy) not in inside and 0 <= qx < S and 0 <= qy < S:
                t[(qx, qy)] = min(t.get((qx, qy), 1), side)
    t.update(inside)
    return t


def umbrite_gem():
    """Hexagonal crystal standing upright, pointed at both ends: light left facet, mid centre, dark right."""
    return tone_rows([
        "................",
        ".......11.......",
        "......1760......",
        ".....176540.....",
        "....16765430....",
        "....17765420....",
        "....17654320....",
        "....17654320....",
        "....16654320....",
        "....16544320....",
        "....15543220....",
        "....15443220....",
        "....05433210....",
        ".....043210.....",
        "......0320......",
        ".......00.......",
    ])


def nebulite_gem():
    """Oval cabochon: a glossy dome with a four-rayed star shining in it, a glint at the top left and reflected light
    along the bottom-right rim."""
    inside = {}
    cx, cy, rx, ry = 8.0, 8.0, 5.9, 6.4
    for y in range(S):
        for x in range(S):
            nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            r2 = nx * nx + ny * ny
            if r2 > 1:
                continue
            light = -nx * 0.5 - ny * 0.6 + math.sqrt(1 - r2) * 0.62
            t = 2 + int(max(0, min(3.99, (light + 0.1) * 3.6)))
            if r2 > 0.72 and nx + ny > 0.4:
                t = max(t, 4)
            inside[(x, y)] = t
    # Drop lone pixels sticking out of the oval.
    for p in [p for p in inside if sum((p[0] + dx, p[1] + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) < 2]:
        del inside[p]
    sx, sy = 7, 7
    for i in range(-3, 4):
        for px, py in ((sx + i, sy), (sx, sy + i)):
            if (px, py) in inside:
                inside[(px, py)] = 7 if abs(i) <= 1 else 6
    for d in (1, -1):
        inside[(sx + d, sy + d)] = 6
        inside[(sx + d, sy - d)] = 6
    for p in ((4, 4), (5, 3), (4, 5)):
        if p in inside:
            inside[p] = 7
    return outlined(inside)


def obscurium_gem():
    """Step cut: an upright octagon with two rings of facets around a flat table."""
    inside = {}
    x0, x1, y0, y1, cut = 3, 12, 1, 14, 3
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            # Depth into the octagon, 0 on the outermost ring.
            d = min(x - x0, x1 - x, y - y0, y1 - y,
                    min(x - x0 + y - y0, x1 - x + y - y0, x - x0 + y1 - y, x1 - x + y1 - y) - cut)
            if d < 0:
                continue
            if d <= 2:
                sides = {"top": y - y0, "bottom": y1 - y, "left": x - x0, "right": x1 - x}
                side = min(sides, key=sides.get)
                # The inner step faces the other way, so it catches light on the opposite sides.
                t = ({"top": 6, "left": 5, "right": 3, "bottom": 2}, {"top": 5, "left": 4, "right": 3, "bottom": 3},
                     {"top": 3, "left": 3, "right": 5, "bottom": 5})[d][side]
            else:
                t = 5 if x + y < 15 else 4
            inside[(x, y)] = t
    for p in ((6, 5), (7, 5), (6, 6), (4, 3), (5, 2)):
        inside[p] = 7
    return outlined(inside)


def penumbrite_gem():
    """Pear cut pointing up: facets radiating from the centre, the left half lit and the right half in deep shadow
    (the penumbra), split by a bright ridge."""
    inside = {}
    cx, cy, r = 7.5, 9.6, 4.9
    tip_y = 0.8
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            in_circle = math.hypot(px - cx, py - cy) <= r
            in_top = tip_y <= py <= cy and abs(px - cx) <= r * (py - tip_y) / (cy - tip_y) * 1.02
            if not (in_circle or in_top):
                continue
            band = int((math.atan2(px - cx, -(py - cy)) + math.pi) / (math.pi / 4))
            left = px < cx
            t = (6 if band % 2 else 5) if left else (2 if band % 2 else 3)
            if math.hypot(px - cx, py - cy) < 2.2:
                t = 5 if left else 3
            inside[(x, y)] = t
    for y in range(2, 14):
        if (7, y) in inside:
            inside[(7, y)] = 7 if y < 6 else 6
    for p in ((5, 7), (5, 8), (6, 6)):
        inside[p] = 7
    return outlined(inside)


def eclipsium_gem():
    """Brilliant cut seen from the side: bright golden crown, girdle line, and the pavilion below in shadow."""
    return tone_rows([
        "................",
        "................",
        "....11111111....",
        "...1776677650...",
        "..176656676540..",
        ".17665566655430.",
        ".16655555544330.",
        ".17777766666550.",
        "..133443322110..",
        "...1344332210...",
        "....13433210....",
        ".....143320.....",
        "......1320......",
        ".......00.......",
        "................",
        "................",
    ])


VOIDRIUM_FRAMES = 16


def voidrium_gem(frame_index=0):
    """A black hole: round black gem with a lit rim, wrapped in a tilted ring of violet light that passes in front at
    the bottom and behind at the top. Two bright spots travel round the ring over the animation (they swap places
    after half a turn, so half a turn loops)."""
    phase = math.pi * frame_index / VOIDRIUM_FRAMES
    c = 7.5
    inside = {(x, y): 2 if x + y > 14 else 3 for y in range(S) for x in range(S) if math.hypot(x - c, y - c) <= 4.6}
    for (x, y) in list(inside):
        if ((x - 1, y) not in inside or (x, y - 1) not in inside) and x + y < 14:
            inside[(x, y)] = 4
    t = outlined(inside)
    tilt = math.radians(-22)
    for x in range(S):
        for y in range(S):
            dx, dy = x - c, y - c
            rx = dx * math.cos(tilt) - dy * math.sin(tilt)
            ry = dx * math.sin(tilt) + dy * math.cos(tilt)
            e = (rx / 7.6) ** 2 + (ry / 3.1) ** 2
            if not 0.5 < e < 1.0 or (ry < 0 and math.hypot(dx, dy) <= 4.6):
                continue
            # Distance along the ring's long axis from the bright spots; |rx| on the first frame.
            k = abs(math.cos(math.atan2(ry / 3.1, rx / 7.6) - phase)) * math.sqrt(e) * 7.6
            t[(x, y)] = 7 if k < 2 else 6 if k < 5 else 5
    t[(6, 5)] = 5
    return t


GEMS = {"umbrite": umbrite_gem, "nebulite": nebulite_gem, "obscurium": obscurium_gem, "penumbrite": penumbrite_gem,
        "eclipsium": eclipsium_gem}


def paint(tones_map, tones):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for (x, y), t in tones_map.items():
        img.putpixel((x, y), tones[t] + (255,))
    return img


def crystal(tier):
    """The tier's crystal item texture; Voidrium's is an animation strip (frames stacked downwards)."""
    tones = ramp(TIERS[tier][0], dark=tier == "voidrium")
    if tier != "voidrium":
        return paint(GEMS[tier](), tones)
    strip = Image.new("RGBA", (S, S * VOIDRIUM_FRAMES), (0, 0, 0, 0))
    for i in range(VOIDRIUM_FRAMES):
        strip.paste(paint(voidrium_gem(i), tones), (0, i * S))
    return strip


def crystal_block(tier, seed):
    """Storage block in the style of the vanilla diamond block: a bright, glassy face with long diagonal reflections
    (bottom-left to top-right), a light inner frame on the top and left, a mid rim on the top and left and a dark rim
    on the bottom and right. Voidrium is dark glass with violet reflections."""
    tones = ramp(TIERS[tier][0], dark=tier == "voidrium")
    rng = random.Random(seed)
    jitter = [rng.choice((-1, 0, 0, 1)) for _ in range(S)]
    bands = [7, 6, 5, 5, 4, 5, 5, 6, 7, 6, 5, 4, 4, 5, 6]
    img = Image.new("RGBA", (S, S))
    for y in range(S):
        for x in range(S):
            if y == S - 1 or x == S - 1:
                t = 3
            elif y == 0 or x == 0:
                t = 5
            elif y == 1 or x == 1:
                t = 7 if (x + y) % 5 else 6
            elif y == S - 2 or x == S - 2:
                t = 4
            else:
                t = bands[(x + y + jitter[y] + jitter[x] // 2) % len(bands)]
                if rng.random() < 0.08:
                    t = max(4, t - 1)
            if tier == "voidrium":
                t = {7: 6, 6: 5, 5: 3, 4: 2, 3: 1}[t]
            put(img, x, y, tones[t])
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    item_dir = OUT.parent / "item"
    item_dir.mkdir(parents=True, exist_ok=True)
    assembly_wand().save(item_dir / "assembly_wand.png")
    assembly_wand(held=True).save(item_dir / "assembly_wand_held.png")
    for index, (tier, (glow, base)) in enumerate(TIERS.items()):
        frame(base, glow, index).save(OUT / f"{tier}_frame.png")
        stabilizer(base, glow, index + 60).save(OUT / f"{tier}_stabilizer.png")
        emitter_side(base, glow, index + 70).save(OUT / f"{tier}_beam_emitter_side.png")
        emitter_end(base, glow, index + 80).save(OUT / f"{tier}_beam_emitter_end.png")
        lens_side(base, glow, index + 90).save(OUT / f"{tier}_lens_mount_side.png")
        lens_top(base, glow, index + 100).save(OUT / f"{tier}_lens_mount_top.png")
        lens_bottom(base, glow, index + 110).save(OUT / f"{tier}_lens_mount_bottom.png")
        plate(base, index + 10).save(OUT / f"{tier}_void_drill_metal.png")
        drill_eye(base, glow, index + 10).save(OUT / f"{tier}_void_drill_eye.png")
        drill_strip(base, glow, index + 20).save(OUT / f"{tier}_void_drill_strip.png")
        drill_ribbed(base, index + 30).save(OUT / f"{tier}_void_drill_ribbed.png")
        drill_core(glow, index + 40).save(OUT / f"{tier}_void_drill_core.png")
        drill_coil(base, glow, index + 50).save(OUT / f"{tier}_void_drill_coil.png")
    neutral = (40, 43, 50)
    infinite_energy_cell(106).save(OUT / "infinite_energy_cell.png")
    clear_lens(199).save(OUT / "clear_lens.png")
    void_plating(198).save(OUT / "void_plating.png")
    void_plating(198, item=True).save(OUT / "void_plating_item.png")
    null_modifier(197).save(OUT / "null_modifier.png")
    null_modifier(197, item=True).save(OUT / "null_modifier_item.png")
    # Speed: redstone-red fast-forward. Energy: amber lightning bolt. Focus: cyan lens focusing to a point.
    for name, glow, symbol in (("speed_modifier", (232, 52, 40), FAST_FORWARD), ("energy_modifier", (255, 176, 40), LIGHTNING),
                               ("focus_modifier", (60, 196, 255), LENS_FOCUS)):
        modifier(197, glow, symbol).save(OUT / f"{name}.png")
        modifier(197, glow, symbol, item=True).save(OUT / f"{name}_item.png")
    for index, (name, colour) in enumerate(LENS_COLOURS.items()):
        lens(colour, 200 + index).save(OUT / f"{name}_lens.png")
    for index, tier in enumerate(TIERS):
        crystal(tier).save(item_dir / f"{tier}_crystal.png")
        crystal_block(tier, 400 + index).save(OUT / f"{tier}_crystal_block.png")
    # One full turn of the ring every 1.6 s.
    (item_dir / "voidrium_crystal.png.mcmeta").write_text('{\n  "animation": {\n    "frametime": 2\n  }\n}\n', encoding="utf-8")


main()
