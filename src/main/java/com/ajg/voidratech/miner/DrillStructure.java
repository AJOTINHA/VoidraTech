package com.ajg.voidratech.miner;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Block layout of each Void Drill tier, as offsets from the controller. The controller sits on top, above the
 * structure (its sides stay free for a chest and power), and the drill hangs below it:
 * <ul>
 *   <li>top layer: a beam emitter under the controller, a square ring of frames at {@code radius} and stabilizers
 *       on the diagonals and on the axes;</li>
 *   <li>middle layers: a column of beam emitters and a frame pillar at each corner; from tier 2 the four
 *       stabilizers beside the column (a cross, no corners) go down with it, one layer (tiers 2-3), two (tiers 4-5) or three (tier 6); the lowest spot of the column,
 *       right above the lens mount, holds the lens (any colour, or the clear lens for no focus);</li>
 *   <li>bottom layer: the lens mount, the frame ring again, stabilizers on the axes, modifier slots near the
 *       centre (2, 4, 8, 12, 16, 20 by tier) and void plating filling the rest of the floor.</li>
 * </ul>
 * Every other spot inside the structure's box must stay empty: any block there takes the drill apart. The beam leaves
 * the lens mount straight down.
 */
public final class DrillStructure {
    public enum Part {
        FRAME,
        EMITTER,
        /** Right above the lens mount: a lens, required; its colour focuses the drops, the clear lens doesn't. */
        LENS_SLOT,
        LENS_MOUNT,
        STABILIZER,
        /** The floor of the bottom layer: void plating, the same for every tier. */
        FLOOR,
        /** A modifier slot in the floor: any modifier, the null modifier when it should do nothing. */
        MODIFIER,
        /** Inside the structure's box but not part of it: must be air. Listed last, after every real part. */
        EMPTY
    }

    public record Element(BlockPos offset, Part part) {
    }

    private static final Map<DrillTier, List<Element>> LAYOUTS = new EnumMap<>(DrillTier.class);

    static {
        for (DrillTier tier : DrillTier.values()) {
            LAYOUTS.put(tier, List.copyOf(build(tier.radius(), tier.depth(), collarDepth(tier), modifierSlots(tier))));
        }
    }

    private DrillStructure() {
    }

    /** Every block of the structure except the controller itself, top layer first, then the spots that must stay empty. */
    public static List<Element> layout(DrillTier tier) {
        return LAYOUTS.get(tier);
    }

    /** Offset of the lens, right above the lens mount. */
    public static BlockPos lensSlotOffset(DrillTier tier) {
        return lensOffset(tier).above();
    }

    /** Offset of the lens mount; the beam starts right below it. */
    public static BlockPos lensOffset(DrillTier tier) {
        return new BlockPos(0, -tier.depth(), 0);
    }

    /**
     * Floor spots (x, z) that hold modifiers: 2, 4, 8, 12, 16 and 20 by tier, close to the centre and symmetric
     * around the stabilizer cross.
     */
    private static List<int[]> modifierSlots(DrillTier tier) {
        List<int[]> slots = new ArrayList<>();
        switch (tier) {
            case UMBRITE -> {
                // Opposite corners of the centre, on a diagonal.
                slots.add(new int[]{-1, -1});
                slots.add(new int[]{1, 1});
            }
            case NEBULITE -> mirrored(slots, 1, 1);
            case OBSCURIUM -> mirrored(slots, 1, 2);
            case PENUMBRITE -> {
                mirrored(slots, 1, 1);
                mirrored(slots, 1, 2);
            }
            case ECLIPSIUM -> {
                mirrored(slots, 1, 1);
                mirrored(slots, 2, 2);
                mirrored(slots, 1, 2);
            }
            case VOIDRIUM -> {
                mirrored(slots, 1, 1);
                mirrored(slots, 2, 2);
                mirrored(slots, 1, 2);
                mirrored(slots, 3, 3);
            }
        }
        return slots;
    }

    /** Layers under the top one where the four stabilizers beside the column continue: 0, 1, 1, 2, 2, 3 by tier. */
    private static int collarDepth(DrillTier tier) {
        return switch (tier) {
            case UMBRITE -> 0;
            case NEBULITE, OBSCURIUM -> 1;
            case PENUMBRITE, ECLIPSIUM -> 2;
            case VOIDRIUM -> 3;
        };
    }

    /** (a, b) in all four quadrants, and (b, a) too when they differ. */
    private static void mirrored(List<int[]> slots, int a, int b) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                slots.add(new int[]{sx * a, sz * b});
                if (a != b) {
                    slots.add(new int[]{sx * b, sz * a});
                }
            }
        }
    }

    private static List<Element> build(int radius, int depth, int collarDepth, List<int[]> modifiers) {
        List<Element> elements = new ArrayList<>();
        int top = -1;
        int bottom = -depth;

        ring(elements, top, radius);
        for (int k = 1; k < radius; k++) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    elements.add(new Element(new BlockPos(sx * k, top, sz * k), Part.STABILIZER));
                }
            }
            elements.add(new Element(new BlockPos(k, top, 0), Part.STABILIZER));
            elements.add(new Element(new BlockPos(-k, top, 0), Part.STABILIZER));
            elements.add(new Element(new BlockPos(0, top, k), Part.STABILIZER));
            elements.add(new Element(new BlockPos(0, top, -k), Part.STABILIZER));
        }
        elements.add(new Element(new BlockPos(0, top, 0), Part.EMITTER));

        for (int y = top - 1; y > bottom; y--) {
            elements.add(new Element(new BlockPos(0, y, 0), y == bottom + 1 ? Part.LENS_SLOT : Part.EMITTER));
            if (y >= top - collarDepth) {
                elements.add(new Element(new BlockPos(1, y, 0), Part.STABILIZER));
                elements.add(new Element(new BlockPos(-1, y, 0), Part.STABILIZER));
                elements.add(new Element(new BlockPos(0, y, 1), Part.STABILIZER));
                elements.add(new Element(new BlockPos(0, y, -1), Part.STABILIZER));
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    elements.add(new Element(new BlockPos(sx * radius, y, sz * radius), Part.FRAME));
                }
            }
        }

        elements.add(new Element(new BlockPos(0, bottom, 0), Part.LENS_MOUNT));
        ring(elements, bottom, radius);
        for (int k = 1; k < radius; k++) {
            elements.add(new Element(new BlockPos(k, bottom, 0), Part.STABILIZER));
            elements.add(new Element(new BlockPos(-k, bottom, 0), Part.STABILIZER));
            elements.add(new Element(new BlockPos(0, bottom, k), Part.STABILIZER));
            elements.add(new Element(new BlockPos(0, bottom, -k), Part.STABILIZER));
        }
        for (int x = -radius + 1; x < radius; x++) {
            for (int z = -radius + 1; z < radius; z++) {
                if (x != 0 && z != 0) {
                    int fx = x;
                    int fz = z;
                    boolean modifier = modifiers.stream().anyMatch(slot -> slot[0] == fx && slot[1] == fz);
                    elements.add(new Element(new BlockPos(x, bottom, z), modifier ? Part.MODIFIER : Part.FLOOR));
                }
            }
        }
        addEmptySpots(elements, radius, top, bottom);
        return elements;
    }

    /** Every other spot of the box from the top layer to the bottom one. */
    private static void addEmptySpots(List<Element> elements, int radius, int top, int bottom) {
        java.util.Set<BlockPos> used = new java.util.HashSet<>();
        for (Element element : elements) {
            used.add(element.offset());
        }
        for (int y = top; y >= bottom; y--) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!used.contains(pos)) {
                        elements.add(new Element(pos, Part.EMPTY));
                    }
                }
            }
        }
    }

    private static void ring(List<Element> elements, int y, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == radius) {
                    elements.add(new Element(new BlockPos(x, y, z), Part.FRAME));
                }
            }
        }
    }
}
