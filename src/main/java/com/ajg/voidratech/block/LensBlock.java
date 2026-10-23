package com.ajg.voidratech.block;

import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * A lens: goes right above a drill's lens mount, in place of the lowest beam emitter, and focuses the drops on the ores
 * of its colour. The clear lens has no colour and focuses nothing.
 */
public class LensBlock extends DrillPartBlock {
    private final @Nullable DyeColor color;

    public LensBlock(@Nullable DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    /** The focus colour, or null for the clear lens. */
    public @Nullable DyeColor color() {
        return color;
    }
}
