package com.ajg.voidratech.compat.jei;

import com.ajg.voidratech.miner.DrillTier;
import com.ajg.voidratech.miner.VoidDrops;
import com.ajg.voidratech.registry.ModContent;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * One JEI tab per drill tier. Each recipe is one thing the drill can pull out (left): how often it comes up with the
 * clear lens, which focuses nothing, and with the lens that focuses it, and about how long the drill takes to find one.
 */
final class DrillDropCategory extends AbstractRecipeCategory<VoidDrops.Chance> {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 56;
    private static final int LENS_X = 26;
    private static final int TEXT_X = 48;
    private static final int TEXT_COLOR = 0xFF404040;

    private final DrillTier tier;

    DrillDropCategory(DrillTier tier, IRecipeType<VoidDrops.Chance> recipeType, IGuiHelper guiHelper) {
        super(recipeType, ModContent.drill(tier).getName(), guiHelper.createDrawableItemLike(ModContent.drill(tier)), WIDTH, HEIGHT);
        this.tier = tier;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, VoidDrops.Chance drop, IFocusGroup focuses) {
        builder.addOutputSlot(1, 11).setStandardSlotBackground().add(drop.item());
        builder.addInputSlot(LENS_X, 1).setStandardSlotBackground().add(ModContent.CLEAR_LENS.get());
        if (drop.lens() != null) {
            builder.addInputSlot(LENS_X, 21).setStandardSlotBackground().add(ModContent.lens(drop.lens()));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, VoidDrops.Chance drop, IFocusGroup focuses) {
        Component timeTooltip = Component.translatable("jei.voidratech.time_tooltip");
        builder.addText(chance(drop.plain()), WIDTH - TEXT_X, 10).setPosition(TEXT_X, 6).setColor(TEXT_COLOR)
                .setTooltip(timeTooltip);
        if (drop.lens() != null) {
            builder.addText(chance(drop.focused()), WIDTH - TEXT_X, 10).setPosition(TEXT_X, 26).setColor(TEXT_COLOR)
                    .setTooltip(timeTooltip);
        } else {
            builder.addText(Component.translatable("jei.voidratech.no_focus"), WIDTH - LENS_X, 10).setPosition(LENS_X, 26)
                    .setColor(TEXT_COLOR);
        }
        builder.addText(Component.translatable("jei.voidratech.dimension", dimensionName(drop.dimension())), WIDTH, 10)
                .setPosition(1, 45).setColor(TEXT_COLOR);
    }

    /** "3.4% (~9.7 min)": chance per drop and the average time for one to come up, with the drill running non-stop. */
    private Component chance(double chance) {
        String percent = String.format(Locale.ROOT, chance < 0.01 ? "%.2f%%" : "%.1f%%", chance * 100);
        double seconds = tier.ticksPerDrop() / 20.0 / chance;
        String time = seconds < 90 ? String.format(Locale.ROOT, "%.0f s", seconds)
                : seconds < 90 * 60 ? String.format(Locale.ROOT, "%.1f min", seconds / 60)
                : String.format(Locale.ROOT, "%.1f h", seconds / 3600);
        return Component.translatable("jei.voidratech.chance", percent, time);
    }

    private static Component dimensionName(Identifier dimension) {
        return Component.translatableWithFallback("dimension." + dimension.getNamespace() + "." + dimension.getPath(), dimension.toString());
    }
}
