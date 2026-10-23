package com.ajg.voidratech.block;

/**
 * A drill modifier: sits in one of the modifier slots of a drill's floor. The null modifier fills a slot without
 * changing anything; the others change how the drill works.
 */
public class ModifierBlock extends DrillPartBlock {
    public enum Kind {
        /** Fills a slot, does nothing. */
        NULL,
        /** Faster drilling, dearer drops: see {@link com.ajg.voidratech.miner.DrillTier#SPEED_PERCENT_PER_MODIFIER}. */
        SPEED,
        /** Less energy for the same work: see {@link com.ajg.voidratech.miner.DrillTier#EFFICIENCY_PERCENT_PER_MODIFIER}. */
        ENERGY,
        /** Sharpens the lens: see {@link com.ajg.voidratech.miner.VoidDrops#FOCUS_PERCENT_PER_MODIFIER}. */
        FOCUS
    }

    private final Kind kind;

    public ModifierBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
