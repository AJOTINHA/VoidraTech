package com.ajg.voidratech.miner;

/**
 * The six Void Drill levels. Each one has its own frame material and a bigger structure: {@code radius} is the
 * half-width of the square footprint and {@code depth} the number of layers below the controller.
 * <p>
 * Speed modifiers in the floor's modifier slots each make the drill {@link #SPEED_PERCENT_PER_MODIFIER}% faster and
 * each drop as much more expensive, so the energy per tick grows with the square: with every slot on speed a tier-1
 * drill is 1.2x as fast for 1.44x the FE/t, a tier-6 one 3x as fast for 9x the FE/t. Energy modifiers each make the
 * drill {@link #EFFICIENCY_PERCENT_PER_MODIFIER}% more efficient (energy divided by 1 + 0.1 per modifier, so a
 * tier-6 floor full of them uses a third), at no cost in speed. Focus modifiers (see
 * {@link VoidDrops#focusMultiplier}) each cost {@link #FOCUS_ENERGY_PERCENT_PER_MODIFIER}% more energy, so a tier-6
 * floor full of them uses twice as much. The slots are what they all compete for.
 */
public enum DrillTier {
    UMBRITE(1, "umbrite", 3, 4, 400, 200, 2, 0x8498B8),
    NEBULITE(2, "nebulite", 3, 5, 200, 800, 4, 0xD678D6),
    OBSCURIUM(3, "obscurium", 4, 6, 100, 2_500, 8, 0x406EEC),
    PENUMBRITE(4, "penumbrite", 5, 7, 50, 8_000, 12, 0x40DED6),
    ECLIPSIUM(5, "eclipsium", 6, 8, 30, 25_000, 16, 0xF4BA3A),
    VOIDRIUM(6, "voidrium", 7, 9, 20, 80_000, 20, 0xB048FF);

    /** Largest structure of any tier, used to find controllers around a changed block. */
    public static final int MAX_RADIUS = 7;
    public static final int MAX_DEPTH = 9;
    /** Extra speed, and extra energy per drop, of each speed modifier. */
    public static final int SPEED_PERCENT_PER_MODIFIER = 10;
    /** Extra efficiency of each energy modifier. */
    public static final int EFFICIENCY_PERCENT_PER_MODIFIER = 10;
    /** Extra energy of each focus modifier. */
    public static final int FOCUS_ENERGY_PERCENT_PER_MODIFIER = 5;

    private final int level;
    private final String material;
    private final int radius;
    private final int depth;
    private final int ticksPerDrop;
    private final int energyPerTick;
    private final int modifierSlots;
    private final int color;

    /** {@code color} is the glow of the tier's textures (tools/gen_textures.py), also used for the beam. */
    DrillTier(int level, String material, int radius, int depth, int ticksPerDrop, int energyPerTick, int modifierSlots, int color) {
        this.level = level;
        this.material = material;
        this.radius = radius;
        this.depth = depth;
        this.ticksPerDrop = ticksPerDrop;
        this.energyPerTick = energyPerTick;
        this.modifierSlots = modifierSlots;
        this.color = color;
    }

    public int level() {
        return level;
    }

    /** Material name used in block ids, e.g. {@code umbrite_frame}. */
    public String material() {
        return material;
    }

    public int radius() {
        return radius;
    }

    public int depth() {
        return depth;
    }

    public int ticksPerDrop() {
        return ticksPerDrop;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    /** Glow colour as 0xRRGGBB. */
    public int color() {
        return color;
    }

    /** Modifier slots in the floor of this tier's structure. */
    public int modifierSlots() {
        return modifierSlots;
    }

    /** Work done per tick, in percent of a tick: 100 with no speed modifiers. */
    public static int speedPercent(int speedModifiers) {
        return 100 + SPEED_PERCENT_PER_MODIFIER * speedModifiers;
    }

    /**
     * Energy per tick with these modifiers: speed makes it faster and every drop dearer, energy makes it cheaper, focus
     * dearer.
     */
    public int energyPerTick(int speedModifiers, int energyModifiers, int focusModifiers) {
        long percent = speedPercent(speedModifiers);
        long energy = energyPerTick * percent * percent / (100 + EFFICIENCY_PERCENT_PER_MODIFIER * energyModifiers) / 100;
        return (int) (energy * (100 + FOCUS_ENERGY_PERCENT_PER_MODIFIER * focusModifiers) / 100);
    }

    /** Buffer for four full drops even with every slot on speed, so a drill keeps working through short gaps in power. */
    public int energyCapacity() {
        return (int) ((long) energyPerTick * ticksPerDrop * 4 * speedPercent(modifierSlots) / 100);
    }

    /** Fastest input: four ticks' worth per tick, with every slot on speed. */
    public int maxEnergyInput() {
        return energyPerTick(modifierSlots, 0, 0) * 4;
    }
}
