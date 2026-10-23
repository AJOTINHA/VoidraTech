package com.ajg.voidratech.registry;

import com.ajg.voidratech.VoidraTech;
import com.ajg.voidratech.block.DrillPartBlock;
import com.ajg.voidratech.block.InfiniteEnergyCellBlock;
import com.ajg.voidratech.block.LensBlock;
import com.ajg.voidratech.block.ModifierBlock;
import com.ajg.voidratech.block.VoidDrillBlock;
import com.ajg.voidratech.block.entity.InfiniteEnergyCellBlockEntity;
import com.ajg.voidratech.block.entity.VoidDrillBlockEntity;
import com.ajg.voidratech.item.AssemblyWandItem;
import com.ajg.voidratech.miner.DrillStructure;
import com.ajg.voidratech.miner.DrillTier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ModContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VoidraTech.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VoidraTech.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VoidraTech.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VoidraTech.MOD_ID);

    /** Every item in creative-tab order. */
    private static final List<DeferredItem<?>> TAB_ITEMS = new ArrayList<>();

    private static final Map<DrillTier, DeferredBlock<VoidDrillBlock>> DRILLS = new EnumMap<>(DrillTier.class);
    private static final Map<DrillTier, DeferredBlock<DrillPartBlock>> FRAMES = new EnumMap<>(DrillTier.class);
    private static final Map<DrillTier, DeferredBlock<DrillPartBlock>> STABILIZERS = new EnumMap<>(DrillTier.class);
    private static final Map<DrillTier, DeferredBlock<DrillPartBlock>> EMITTERS = new EnumMap<>(DrillTier.class);
    private static final Map<DrillTier, DeferredBlock<DrillPartBlock>> LENS_MOUNTS = new EnumMap<>(DrillTier.class);
    private static final Map<DyeColor, DeferredBlock<LensBlock>> LENSES = new EnumMap<>(DyeColor.class);
    /** Each tier's crystal: tier 1 is crafted, the next tier's comes out of the drill of the tier before it. */
    private static final Map<DrillTier, DeferredItem<Item>> CRYSTALS = new EnumMap<>(DrillTier.class);
    private static final Map<DrillTier, DeferredBlock<Block>> CRYSTAL_BLOCKS = new EnumMap<>(DrillTier.class);
    /** Floor of every drill's bottom layer; one block for all tiers. */
    public static final DeferredBlock<DrillPartBlock> VOID_PLATING = BLOCKS.registerBlock("void_plating", DrillPartBlock::new,
            ModContent::machineProperties);
    /** Fills a modifier slot without changing anything. */
    public static final DeferredBlock<ModifierBlock> NULL_MODIFIER = BLOCKS.registerBlock("null_modifier",
            p -> new ModifierBlock(ModifierBlock.Kind.NULL, p), ModContent::machineProperties);
    /** Makes the drill faster and each drop dearer (see {@link DrillTier}). */
    public static final DeferredBlock<ModifierBlock> SPEED_MODIFIER = BLOCKS.registerBlock("speed_modifier",
            p -> new ModifierBlock(ModifierBlock.Kind.SPEED, p), ModContent::machineProperties);
    /** Makes the drill use less energy (see {@link DrillTier}). */
    public static final DeferredBlock<ModifierBlock> ENERGY_MODIFIER = BLOCKS.registerBlock("energy_modifier",
            p -> new ModifierBlock(ModifierBlock.Kind.ENERGY, p), ModContent::machineProperties);
    /** Sharpens the lens's focus (see {@link com.ajg.voidratech.miner.VoidDrops}). */
    public static final DeferredBlock<ModifierBlock> FOCUS_MODIFIER = BLOCKS.registerBlock("focus_modifier",
            p -> new ModifierBlock(ModifierBlock.Kind.FOCUS, p), ModContent::machineProperties);
    /** Lets the beam through without any focus, like having no lens. */
    public static final DeferredBlock<LensBlock> CLEAR_LENS = BLOCKS.registerBlock("clear_lens", p -> new LensBlock(null, p),
            p -> machineProperties(p).noOcclusion());

    public static final DeferredItem<AssemblyWandItem> ASSEMBLY_WAND = ITEMS.registerItem("assembly_wand", AssemblyWandItem::new, p -> p.stacksTo(1));

    static {
        TAB_ITEMS.add(ASSEMBLY_WAND);
        for (DrillTier tier : DrillTier.values()) {
            DeferredBlock<VoidDrillBlock> drill = BLOCKS.registerBlock(tier.material() + "_void_drill",
                    // The coiled reactor model is open on the sides: neighbours must still draw their faces.
                    p -> new VoidDrillBlock(tier, p), p -> machineProperties(p).noOcclusion());
            DRILLS.put(tier, drill);
            TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(drill));
        }
        for (DrillTier tier : DrillTier.values()) {
            DeferredBlock<DrillPartBlock> frame = BLOCKS.registerBlock(tier.material() + "_frame", DrillPartBlock::new, ModContent::machineProperties);
            FRAMES.put(tier, frame);
            TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(frame));
        }
        for (DrillTier tier : DrillTier.values()) {
            DeferredBlock<DrillPartBlock> stabilizer = BLOCKS.registerBlock(tier.material() + "_stabilizer", DrillPartBlock::new, ModContent::machineProperties);
            STABILIZERS.put(tier, stabilizer);
            TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(stabilizer));
        }
        for (DrillTier tier : DrillTier.values()) {
            DeferredBlock<DrillPartBlock> emitter = BLOCKS.registerBlock(tier.material() + "_beam_emitter", DrillPartBlock::new, ModContent::machineProperties);
            EMITTERS.put(tier, emitter);
            TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(emitter));
        }
        for (DrillTier tier : DrillTier.values()) {
            DeferredBlock<DrillPartBlock> lensMount = BLOCKS.registerBlock(tier.material() + "_lens_mount", DrillPartBlock::new, ModContent::machineProperties);
            LENS_MOUNTS.put(tier, lensMount);
            TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(lensMount));
        }
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(VOID_PLATING));
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(NULL_MODIFIER));
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(SPEED_MODIFIER));
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(ENERGY_MODIFIER));
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(FOCUS_MODIFIER));
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(CLEAR_LENS));
        for (DyeColor color : DyeColor.values()) {
            DeferredBlock<LensBlock> lens = BLOCKS.registerBlock(color.getSerializedName() + "_lens", p -> new LensBlock(color, p), ModContent::machineProperties);
            LENSES.put(color, lens);
            TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(lens));
        }
        for (DrillTier tier : DrillTier.values()) {
            DeferredItem<Item> crystal = ITEMS.registerSimpleItem(tier.material() + "_crystal");
            CRYSTALS.put(tier, crystal);
            TAB_ITEMS.add(crystal);
        }
        for (DrillTier tier : DrillTier.values()) {
            DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(tier.material() + "_crystal_block", p -> crystalBlockProperties(tier, p));
            CRYSTAL_BLOCKS.put(tier, block);
            TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        }
    }


    /** Testing only: endless FE. */
    public static final DeferredBlock<InfiniteEnergyCellBlock> INFINITE_ENERGY_CELL = BLOCKS.registerBlock("infinite_energy_cell",
            InfiniteEnergyCellBlock::new, ModContent::machineProperties);

    static {
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(INFINITE_ENERGY_CELL));
    }

    public static final Supplier<BlockEntityType<VoidDrillBlockEntity>> VOID_DRILL_BE = BLOCK_ENTITIES.register("void_drill",
            () -> new BlockEntityType<>(VoidDrillBlockEntity::new, DRILLS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    public static final Supplier<BlockEntityType<InfiniteEnergyCellBlockEntity>> INFINITE_ENERGY_CELL_BE = BLOCK_ENTITIES.register("infinite_energy_cell",
            () -> new BlockEntityType<>(InfiniteEnergyCellBlockEntity::new, INFINITE_ENERGY_CELL.get()));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.voidratech"))
            // After every vanilla tab, so the first page stays vanilla.
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> DRILLS.get(DrillTier.UMBRITE).get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> TAB_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private ModContent() {
    }

    public static VoidDrillBlock drill(DrillTier tier) {
        return DRILLS.get(tier).get();
    }

    public static DrillPartBlock frame(DrillTier tier) {
        return FRAMES.get(tier).get();
    }

    public static DrillPartBlock lensMount(DrillTier tier) {
        return LENS_MOUNTS.get(tier).get();
    }

    public static DrillPartBlock emitter(DrillTier tier) {
        return EMITTERS.get(tier).get();
    }

    public static DrillPartBlock stabilizer(DrillTier tier) {
        return STABILIZERS.get(tier).get();
    }

    public static LensBlock lens(DyeColor color) {
        return LENSES.get(color).get();
    }

    public static Item crystal(DrillTier tier) {
        return CRYSTALS.get(tier).get();
    }

    public static Block crystalBlock(DrillTier tier) {
        return CRYSTAL_BLOCKS.get(tier).get();
    }

    /**
     * The block the Assembly Wand places for a part of a drill structure of this tier (the clear lens for the lens slot,
     * the null modifier for modifier slots).
     */
    public static Block structureBlock(DrillTier tier, DrillStructure.Part part) {
        return switch (part) {
            case FRAME -> frame(tier);
            case EMITTER -> emitter(tier);
            case LENS_SLOT -> CLEAR_LENS.get();
            case LENS_MOUNT -> lensMount(tier);
            case STABILIZER -> stabilizer(tier);
            case FLOOR -> VOID_PLATING.get();
            case MODIFIER -> NULL_MODIFIER.get();
            case EMPTY -> Blocks.AIR;
        };
    }

    /** Whether {@code state} fits this part of a drill structure of this tier. */
    public static boolean accepts(DrillTier tier, DrillStructure.Part part, BlockState state) {
        return switch (part) {
            case LENS_SLOT -> state.getBlock() instanceof LensBlock;
            case MODIFIER -> state.getBlock() instanceof ModifierBlock;
            case EMPTY -> state.isAir();
            default -> state.is(structureBlock(tier, part));
        };
    }

    private static BlockBehaviour.Properties machineProperties(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.IMMOVEABLE);
    }

    /** Storage blocks of crystal: as tough as a block of diamond, ringing like amethyst. */
    private static BlockBehaviour.Properties crystalBlockProperties(DrillTier tier, BlockBehaviour.Properties properties) {
        MapColor color = switch (tier) {
            case UMBRITE -> MapColor.COLOR_LIGHT_GRAY;
            case NEBULITE -> MapColor.COLOR_MAGENTA;
            case OBSCURIUM -> MapColor.COLOR_BLUE;
            case PENUMBRITE -> MapColor.COLOR_CYAN;
            case ECLIPSIUM -> MapColor.GOLD;
            case VOIDRIUM -> MapColor.COLOR_PURPLE;
        };
        return properties.mapColor(color)
                .strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.AMETHYST);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
