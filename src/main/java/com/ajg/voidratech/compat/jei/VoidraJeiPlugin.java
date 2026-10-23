package com.ajg.voidratech.compat.jei;

import com.ajg.voidratech.VoidraTech;
import com.ajg.voidratech.block.LensBlock;
import com.ajg.voidratech.miner.DrillTier;
import com.ajg.voidratech.miner.VoidDrops;
import com.ajg.voidratech.registry.ModContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JEI: a tab for each drill tier with everything it can pull out of the void. Loaded by JEI only, so the mod still
 * runs without it. The drops come from the server ({@link VoidDrops#client()}) and are read on every lookup, so the
 * tabs follow datapack reloads.
 */
@JeiPlugin
public final class VoidraJeiPlugin implements IModPlugin {
    private static final Map<DrillTier, IRecipeType<VoidDrops.Chance>> TYPES = new EnumMap<>(DrillTier.class);

    static {
        for (DrillTier tier : DrillTier.values()) {
            // JEI sorts tabs by id: numbered so they come in tier order.
            TYPES.put(tier, IRecipeType.create(VoidraTech.MOD_ID, "void_drill_tier_" + tier.level(), VoidDrops.Chance.class));
        }
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        for (DrillTier tier : DrillTier.values()) {
            registration.addRecipeCategories(new DrillDropCategory(tier, TYPES.get(tier), guiHelper));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (DrillTier tier : DrillTier.values()) {
            registration.addCraftingStation(TYPES.get(tier), ModContent.drill(tier));
        }
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        for (DrillTier tier : DrillTier.values()) {
            registration.addSimpleRecipeManagerPlugin(TYPES.get(tier), new DrillDrops(tier));
        }
    }

    /**
     * A drill tier's drops: looked up by what comes out, or by a lens (a coloured one shows the drops it focuses, the
     * clear one shows everything).
     */
    private record DrillDrops(DrillTier tier) implements ISimpleRecipeManagerPlugin<VoidDrops.Chance> {
        @Override
        public boolean isHandledInput(ITypedIngredient<?> input) {
            return !getRecipesForInput(input).isEmpty();
        }

        @Override
        public boolean isHandledOutput(ITypedIngredient<?> output) {
            return !getRecipesForOutput(output).isEmpty();
        }

        @Override
        public List<VoidDrops.Chance> getRecipesForInput(ITypedIngredient<?> input) {
            LensBlock lens = lens(input);
            if (lens == null) {
                return List.of();
            }
            DyeColor color = lens.color();
            return color == null ? getAllRecipes() : getAllRecipes().stream().filter(drop -> drop.lens() == color).toList();
        }

        @Override
        public List<VoidDrops.Chance> getRecipesForOutput(ITypedIngredient<?> output) {
            ItemStack stack = itemStack(output);
            return stack == null ? List.of() : getAllRecipes().stream().filter(drop -> stack.is(drop.item())).toList();
        }

        @Override
        public List<VoidDrops.Chance> getAllRecipes() {
            return VoidDrops.client().chances(tier);
        }

        private static @Nullable ItemStack itemStack(ITypedIngredient<?> ingredient) {
            ITypedIngredient<ItemStack> stack = ingredient.castToItemStackType();
            return stack == null ? null : stack.getIngredient();
        }

        private static @Nullable LensBlock lens(ITypedIngredient<?> ingredient) {
            ItemStack stack = itemStack(ingredient);
            return stack != null && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof LensBlock lens ? lens : null;
        }
    }
}
