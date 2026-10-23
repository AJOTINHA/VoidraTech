"""Generates the static resources for Voidra Tech: block states, models, loot tables, tags, the default
void drops and the empty gametest platform. Textures come from gen_textures.py.

Usage: python tools/gen_resources.py .
"""
import gzip
import json
import struct
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) / "src" / "main" / "resources"
NS = "voidratech"
ASSETS = ROOT / "assets" / NS
DATA = ROOT / "data" / NS

TIERS = ["umbrite", "nebulite", "obscurium", "penumbrite", "eclipsium", "voidrium"]
DRILLS = [f"{t}_void_drill" for t in TIERS]
FRAMES = [f"{t}_frame" for t in TIERS]
STABILIZERS = [f"{t}_stabilizer" for t in TIERS]
EMITTERS = [f"{t}_beam_emitter" for t in TIERS]
LENS_MOUNTS = [f"{t}_lens_mount" for t in TIERS]
# The 16 dye colours, in vanilla order.
LENS_COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
LENSES = ["clear_lens"] + [f"{c}_lens" for c in LENS_COLOURS]
CRYSTALS = [f"{t}_crystal" for t in TIERS]
CRYSTAL_BLOCKS = [f"{t}_crystal_block" for t in TIERS]
MODIFIERS = ["null_modifier", "speed_modifier", "energy_modifier", "focus_modifier"]
ALL_BLOCKS = (DRILLS + FRAMES + STABILIZERS + EMITTERS + LENS_MOUNTS + ["void_plating"] + MODIFIERS + LENSES
              + CRYSTAL_BLOCKS + ["infinite_energy_cell"])


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def block_model(name, parent, **textures):
    write(ASSETS / f"models/block/{name}.json",
          {"parent": f"minecraft:block/{parent}", "textures": {k: f"{NS}:block/{v}" for k, v in textures.items()}})


# --- Models --------------------------------------------------------------------------------------------------
# Void Drill controller: the "coiled reactor". Base and cap with a glowing line, ribbed corner posts and a
# glowing core wrapped in four coil bands. One shared parent model; each tier only swaps the textures.
def face_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


def cuboid(f, t, textures, light=0, side_v=None):
    """textures maps a face (or "side" / "end" / "all") to a texture variable; side_v picks the rows shown on the sides."""
    faces = {}
    for face in ("north", "south", "west", "east", "up", "down"):
        key = textures.get(face) or textures.get("end" if face in ("up", "down") else "side") or textures.get("all")
        uv = face_uv(face, f, t)
        if side_v is not None and face not in ("up", "down"):
            uv[1], uv[3] = side_v, side_v + (t[1] - f[1])
        faces[face] = {"texture": f"#{key}", "uv": uv}
    element = {"from": list(f), "to": list(t), "faces": faces}
    if light:
        element["light_emission"] = light
    return element


def coil_band(y):
    """A hollow square band around the core, from 4 to 12, one unit high."""
    tex = {"side": "coil", "end": "metal"}
    return [cuboid((4, y, 4), (12, y + 1, 5), tex), cuboid((4, y, 11), (12, y + 1, 12), tex),
            cuboid((4, y, 5), (5, y + 1, 11), tex), cuboid((11, y, 5), (12, y + 1, 11), tex)]


drill_elements = [
    cuboid((0, 0, 0), (16, 3, 16), {"side": "strip", "end": "metal"}, side_v=6),
    cuboid((0, 13, 0), (16, 16, 16), {"side": "strip", "up": "eye", "down": "metal"}, side_v=6),
]
for x in (0, 13):
    for z in (0, 13):
        drill_elements.append(cuboid((x, 3, z), (x + 3, 13, z + 3), {"side": "ribbed", "end": "metal"}))
write(ASSETS / "models/block/void_drill.json",
      {"parent": "minecraft:block/block", "textures": {"particle": "#metal"}, "elements": drill_elements})

# The rotor (glowing core and its coils) is its own model: VoidDrillRenderer spins it while the drill works.
rotor_elements = [cuboid((5, 3, 5), (11, 13, 11), {"all": "core"}, light=12)]
# Core from 3 to 13: one unit of core shows above and below, the odd extra unit goes between the middle bands.
for y in (4, 6, 9, 11):
    rotor_elements += coil_band(y)
write(ASSETS / "models/block/void_drill_rotor.json",
      {"parent": "minecraft:block/block", "textures": {"particle": "#metal"}, "elements": rotor_elements})

DRILL_PARTS = ("metal", "eye", "strip", "ribbed", "core", "coil")
for tier in TIERS:
    textures = {part: f"{NS}:block/{tier}_void_drill_{part}" for part in DRILL_PARTS}
    write(ASSETS / f"models/block/{tier}_void_drill.json", {"parent": f"{NS}:block/void_drill", "textures": textures})
    write(ASSETS / f"models/block/{tier}_void_drill_rotor.json", {"parent": f"{NS}:block/void_drill_rotor", "textures": textures})
    block_model(f"{tier}_frame", "cube_all", all=f"{tier}_frame")
    block_model(f"{tier}_stabilizer", "cube_all", all=f"{tier}_stabilizer")
    block_model(f"{tier}_beam_emitter", "cube_column", end=f"{tier}_beam_emitter_end", side=f"{tier}_beam_emitter_side")
    block_model(f"{tier}_lens_mount", "cube_bottom_top", top=f"{tier}_lens_mount_top", side=f"{tier}_lens_mount_side",
                bottom=f"{tier}_lens_mount_bottom")
block_model("infinite_energy_cell", "cube_all", all="infinite_energy_cell")

for block in ALL_BLOCKS:
    write(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    if block in DRILLS:
        write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": f"{NS}:block/{block}"},
            {"type": "minecraft:model", "model": f"{NS}:block/{block}_rotor"},
        ]}})
    elif block in ["void_plating"] + MODIFIERS:
        write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{block}"}})
    else:
        write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}})

for lens in LENSES:
    block_model(lens, "cube_all", all=lens)
block_model("void_plating", "cube_all", all="void_plating")
for modifier in MODIFIERS:
    block_model(modifier, "cube_all", all=modifier)
# Their items use bigger rivets, which survive the icon's small top face.
write(ASSETS / "models/item/void_plating.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/void_plating_item"}})
for modifier in MODIFIERS:
    write(ASSETS / f"models/item/{modifier}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{modifier}_item"}})
# The clear lens is see-through glass, drawn like vanilla glass.
write(ASSETS / "models/block/clear_lens.json", {"parent": "minecraft:block/cube_all", "textures": {
    "all": {"sprite": f"{NS}:block/clear_lens", "force_translucent": True}}})

for block in CRYSTAL_BLOCKS:
    block_model(block, "cube_all", all=block)
# Crystals are flat items; Voidrium's texture is an animation strip (see gen_textures.py).
for crystal in CRYSTALS:
    write(ASSETS / f"models/item/{crystal}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{crystal}"}})
    write(ASSETS / f"items/{crystal}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{crystal}"}})

write(ASSETS / "models/item/assembly_wand.json",
      {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/assembly_wand"}})
# In the hand the wand uses a mirrored crook (see gen_textures.assembly_wand), elsewhere the approved icon.
write(ASSETS / "models/item/assembly_wand_held.json",
      {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/assembly_wand_held"}})
write(ASSETS / "items/assembly_wand.json", {"model": {
    "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [{"when": ["gui", "ground", "fixed"], "model": {"type": "minecraft:model", "model": f"{NS}:item/assembly_wand"}}],
    "fallback": {"type": "minecraft:model", "model": f"{NS}:item/assembly_wand_held"},
}})

# --- Loot tables and tags ------------------------------------------------------------------------------------
for block in ALL_BLOCKS:
    write(DATA / f"loot_table/blocks/{block}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"{NS}:blocks/{block}",
    })

write(ROOT / "data/minecraft/tags/block/mineable/pickaxe.json", {"values": [f"{NS}:{b}" for b in ALL_BLOCKS]})
write(ROOT / "data/minecraft/tags/block/needs_iron_tool.json", {"values": [f"{NS}:{b}" for b in ALL_BLOCKS]})

# --- Default void drops --------------------------------------------------------------------------------------
# (weight, min_tier, lens): weight = relative chance, min_tier = first drill level that can pull it out, lens = the
# dye colour of the lens that focuses it (the ore's own colour); that lens makes it 8x as likely.
OVERWORLD = ["minecraft:overworld"]
NETHER = ["minecraft:the_nether"]
VANILLA_DROPS = {
    # Overworld
    "coal_ore": (100, 1, "black", OVERWORLD), "iron_ore": (80, 1, "white", OVERWORLD),
    "copper_ore": (90, 1, "orange", OVERWORLD), "gold_ore": (40, 1, "yellow", OVERWORLD),
    "redstone_ore": (50, 1, "red", OVERWORLD), "lapis_ore": (40, 1, "blue", OVERWORLD),
    "diamond_ore": (20, 2, "cyan", OVERWORLD), "emerald_ore": (15, 2, "lime", OVERWORLD),
    # Nether
    "nether_quartz_ore": (60, 1, "white", NETHER), "nether_gold_ore": (40, 1, "yellow", NETHER),
    "ancient_debris": (5, 4, "brown", NETHER),
}
# Ores from other mods, through common c:ores tags: skipped while no installed mod fills the tag.
TAG_DROPS = {
    # Metals
    "tin": (40, 1, "gray", OVERWORLD), "nickel": (25, 2, "gray", OVERWORLD), "silver": (25, 2, "light_gray", OVERWORLD),
    "zinc": (30, 1, "pink", OVERWORLD), "lead": (30, 1, "purple", OVERWORLD), "aluminum": (35, 1, "white", OVERWORLD),
    "osmium": (35, 2, "light_blue", OVERWORLD), "platinum": (10, 4, "light_blue", OVERWORLD),
    "uranium": (15, 3, "green", OVERWORLD), "iridium": (5, 5, "white", OVERWORLD), "titanium": (12, 3, "white", OVERWORLD),
    "tungsten": (12, 3, "black", OVERWORLD), "thorium": (10, 3, "black", OVERWORLD), "lithium": (15, 2, "white", OVERWORLD),
    "magnesium": (15, 2, "white", OVERWORLD), "boron": (15, 2, "light_gray", OVERWORLD), "yellorite": (15, 2, "yellow", OVERWORLD),
    "cobalt": (15, 3, "blue", NETHER),
    # Gems and minerals
    "ruby": (15, 2, "red", OVERWORLD), "sapphire": (15, 2, "blue", OVERWORLD), "peridot": (15, 2, "green", OVERWORLD),
    "apatite": (25, 1, "light_blue", OVERWORLD), "certus_quartz": (20, 2, "light_blue", OVERWORLD),
    "black_quartz": (20, 1, "black", OVERWORLD), "fluorite": (25, 1, "pink", OVERWORLD), "cinnabar": (20, 1, "brown", OVERWORLD),
    "sulfur": (25, 1, "yellow", OVERWORLD), "saltpeter": (20, 1, "white", OVERWORLD), "graphite": (25, 1, "black", OVERWORLD),
}
for item, (weight, min_tier, lens, dimensions) in VANILLA_DROPS.items():
    write(DATA / f"{NS}/void_drops/{item}.json",
          {"item": f"minecraft:{item}", "weight": weight, "min_tier": min_tier, "lens": lens, "dimensions": dimensions})
for ore, (weight, min_tier, lens, dimensions) in TAG_DROPS.items():
    write(DATA / f"{NS}/void_drops/{ore}_ore.json",
          {"tag": f"c:ores/{ore}", "weight": weight, "min_tier": min_tier, "lens": lens, "dimensions": dimensions})

# Crystals: each tier's drill also pulls out the crystal of the next tier (and its own, from tier 1 up), so every
# tier unlocks the next. Any dimension. On a scale where coal is 100, the newest crystal is always the rarest, and with
# vanilla ores crystals are 8-14% of the drops. No ore uses the magenta lens: it focuses every crystal at once (the
# crystal lens). (tier, weight, min_tier)
CRYSTAL_LENS = "magenta"
CRYSTAL_DROPS = [("umbrite", 20, 1), ("nebulite", 15, 1), ("obscurium", 12, 2), ("penumbrite", 9, 3), ("eclipsium", 6, 4),
                 ("voidrium", 3, 5)]
for tier, weight, min_tier in CRYSTAL_DROPS:
    write(DATA / f"{NS}/void_drops/{tier}_crystal.json",
          {"item": f"{NS}:{tier}_crystal", "weight": weight, "min_tier": min_tier, "lens": CRYSTAL_LENS})

# --- Recipes ---------------------------------------------------------------------------------------------------
# Umbrite, the first crystal, is crafted; the rest only come out of drills.
write(DATA / "recipe/umbrite_crystal.json", {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"A": "minecraft:amethyst_shard", "Q": "minecraft:quartz", "E": "minecraft:ender_pearl"},
    "pattern": [" A ", "QEQ", " A "],
    "result": {"id": f"{NS}:umbrite_crystal", "count": 2},
})
# Speed modifier: redstone blocks around a null modifier, tier-2 crystal on top and below.
write(DATA / "recipe/speed_modifier.json", {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"R": "minecraft:redstone_block", "N": f"{NS}:nebulite_crystal", "U": f"{NS}:umbrite_crystal",
            "M": f"{NS}:null_modifier"},
    "pattern": ["RNR", "UMU", "RNR"],
    "result": {"id": f"{NS}:speed_modifier"},
})
# Energy modifier: the same, with glowstone in place of the redstone.
write(DATA / "recipe/energy_modifier.json", {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"G": "minecraft:glowstone", "N": f"{NS}:nebulite_crystal", "U": f"{NS}:umbrite_crystal",
            "M": f"{NS}:null_modifier"},
    "pattern": ["GNG", "UMU", "GNG"],
    "result": {"id": f"{NS}:energy_modifier"},
})
# Focus modifier: the same, with lapis blocks.
write(DATA / "recipe/focus_modifier.json", {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"L": "minecraft:lapis_block", "N": f"{NS}:nebulite_crystal", "U": f"{NS}:umbrite_crystal",
            "M": f"{NS}:null_modifier"},
    "pattern": ["LNL", "UMU", "LNL"],
    "result": {"id": f"{NS}:focus_modifier"},
})
# The crystal lens: 4 Nebulite crystal blocks around a clear lens.
write(DATA / f"recipe/{CRYSTAL_LENS}_lens.json", {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"N": f"{NS}:nebulite_crystal_block", "L": f"{NS}:clear_lens"},
    "pattern": ["N N", "NLN", "N N"],
    "result": {"id": f"{NS}:{CRYSTAL_LENS}_lens"},
})
def shaped(name, pattern, key, count=1, category="building"):
    write(DATA / f"recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
        "result": {"id": f"{NS}:{name}", "count": count} if count > 1 else {"id": f"{NS}:{name}"},
    })


# The drill's parts. Tier 1 is made from vanilla items and Umbrite crystals; every later tier upgrades the parts of the
# tier before with its own crystal, so a drill is rebuilt tier by tier. Frames, stabilizers and plating, needed by the dozen, come 8 to a craft for a single crystal; the few emitters
# (4 crystals each), the lens mount (4 crystals and a crystal block) and the drill itself cost more.
U = f"{NS}:umbrite_crystal"
shaped("umbrite_frame", ["IUI", "I I", "IUI"], {"I": "minecraft:iron_ingot", "U": U}, 8)
shaped("umbrite_stabilizer", ["IQI", "QUQ", "IQI"], {"I": "minecraft:iron_ingot", "Q": "minecraft:quartz", "U": U}, 4)
shaped("umbrite_beam_emitter", ["IGI", "URU", "IGI"],
       {"I": "minecraft:iron_ingot", "G": "minecraft:glass", "U": U, "R": "minecraft:redstone_block"})
shaped("umbrite_lens_mount", ["IUI", "U U", "OOO"], {"I": "minecraft:iron_ingot", "U": U, "O": "minecraft:obsidian"})
shaped("umbrite_void_drill", ["UDU", "UEU", "UOU"], {"U": U, "D": "minecraft:diamond_block",
       "E": f"{NS}:umbrite_beam_emitter", "O": "minecraft:obsidian"})
for previous, tier in zip(TIERS, TIERS[1:]):
    C = f"{NS}:{tier}_crystal"
    shaped(f"{tier}_frame", ["FFF", "FCF", "FFF"], {"F": f"{NS}:{previous}_frame", "C": C}, 8)
    shaped(f"{tier}_stabilizer", ["SSS", "SCS", "SSS"], {"S": f"{NS}:{previous}_stabilizer", "C": C}, 8)
    shaped(f"{tier}_beam_emitter", ["CRC", "GEG", "CRC"], {"E": f"{NS}:{previous}_beam_emitter", "C": C,
           "R": "minecraft:redstone_block", "G": "minecraft:glass"})
    shaped(f"{tier}_lens_mount", ["CDC", "OMO", "CBC"], {"M": f"{NS}:{previous}_lens_mount", "C": C,
           "D": "minecraft:diamond", "O": "minecraft:obsidian", "B": f"{NS}:{tier}_crystal_block"})
    shaped(f"{tier}_void_drill", ["CDC", "CVC", "CEC"], {"C": C, "D": "minecraft:diamond_block",
           "V": f"{NS}:{previous}_void_drill", "E": f"{NS}:{tier}_beam_emitter"})
# The floor and its modifiers are the same for every tier.
shaped("void_plating", ["IOI", "OUO", "IOI"], {"I": "minecraft:iron_ingot", "O": "minecraft:obsidian", "U": U}, 8)
shaped("null_modifier", ["IGI", "GPG", "IGI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass",
       "P": f"{NS}:void_plating"}, category="misc")
# Lenses: glass for the clear one, a dye to colour it (the magenta one is the crystal lens, below), and any
# coloured lens back to clear.
shaped("clear_lens", ["G G", "GGG", "G G"], {"G": "minecraft:glass"}, category="misc")
for colour in LENS_COLOURS:
    if colour != CRYSTAL_LENS:
        write(DATA / f"recipe/{colour}_lens.json", {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [f"{NS}:clear_lens", f"minecraft:{colour}_dye"],
            "result": {"id": f"{NS}:{colour}_lens"},
        })
write(DATA / "tags/item/coloured_lenses.json", {"values": [f"{NS}:{c}_lens" for c in LENS_COLOURS]})
write(DATA / "recipe/clear_lens_from_coloured.json", {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": [f"#{NS}:coloured_lenses"],
    "result": {"id": f"{NS}:clear_lens"},
})
# The assembly wand: an Umbrite crystal on an obsidian rod.
write(DATA / "recipe/assembly_wand.json", {
    "type": "minecraft:crafting_shaped", "category": "equipment",
    "key": {"U": U, "O": "minecraft:obsidian"}, "pattern": ["  U", " O ", "O  "],
    "result": {"id": f"{NS}:assembly_wand"},
})
for tier in TIERS:
    write(DATA / f"recipe/{tier}_crystal_block.json", {
        "type": "minecraft:crafting_shaped", "category": "building",
        "key": {"#": f"{NS}:{tier}_crystal"}, "pattern": ["###", "###", "###"],
        "result": {"id": f"{NS}:{tier}_crystal_block"},
    })
    write(DATA / f"recipe/{tier}_crystal_from_block.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [f"{NS}:{tier}_crystal_block"],
        "result": {"id": f"{NS}:{tier}_crystal", "count": 9},
    })


# --- Gametest platform: an empty 9x8x9 structure ---------------------------------------------------------------
def nbt_string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def nbt_int_list(name, values):
    return b"\x09" + nbt_string(name) + b"\x03" + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def empty_structure(size, data_version=5023):
    body = b"\x03" + nbt_string("DataVersion") + struct.pack(">i", data_version)
    body += nbt_int_list("size", size)
    air = b"\x08" + nbt_string("Name") + nbt_string("minecraft:air") + b"\x00"
    body += b"\x09" + nbt_string("palette") + b"\x0a" + struct.pack(">i", 1) + air
    body += b"\x09" + nbt_string("blocks") + b"\x0a" + struct.pack(">i", 0)
    body += b"\x09" + nbt_string("entities") + b"\x0a" + struct.pack(">i", 0)
    return gzip.compress(b"\x0a" + nbt_string("") + body + b"\x00", mtime=0)


structure = DATA / "structure/drill_platform.nbt"
structure.parent.mkdir(parents=True, exist_ok=True)
structure.write_bytes(empty_structure([9, 8, 9]))
