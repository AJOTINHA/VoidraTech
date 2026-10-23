package com.ajg.voidratech.test;

import com.ajg.voidratech.VoidraTech;
import com.ajg.voidratech.block.entity.VoidDrillBlockEntity;
import com.ajg.voidratech.item.AssemblyWandItem;
import com.ajg.voidratech.miner.DrillStructure;
import com.ajg.voidratech.miner.DrillTier;
import net.minecraft.world.item.DyeColor;
import com.ajg.voidratech.miner.VoidDrops;
import com.ajg.voidratech.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Consumer;

/**
 * Run with {@code ./gradlew runGameTestServer}. Each test builds an Umbrite drill (7x7, 4 layers under the controller) on a 9x8x9 platform
 * with bedrock right under the lens mount, so the beam is always clear unless a test blocks it.
 */
public final class VoidraGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VoidraTech.MOD_ID);
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, "drill_platform");
    private static final DrillTier TIER = DrillTier.UMBRITE;
    /** Controller position: centred, with the 4 layers below it and bedrock under the lens mount. */
    private static final BlockPos CONTROLLER = new BlockPos(4, 5, 4);
    private static final BlockPos CHEST = CONTROLLER.above();

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> function) {
    }

    private static final List<Test> TESTS = List.of(
            new Test("drill_mines_into_chest", 600, VoidraGameTests::drillMinesIntoChest),
            new Test("incomplete_structure_does_not_form", 100, VoidraGameTests::incompleteStructureDoesNotForm),
            new Test("no_chest_no_drilling", 100, VoidraGameTests::noChestNoDrilling),
            new Test("blocked_beam_stops_drill", 200, VoidraGameTests::blockedBeamStopsDrill),
            new Test("breaking_a_frame_unforms", 100, VoidraGameTests::breakingAFrameUnforms),
            new Test("wand_builds_drill_in_creative", 100, VoidraGameTests::wandBuildsDrillInCreative),
            new Test("wand_uses_inventory_blocks", 100, VoidraGameTests::wandUsesInventoryBlocks),
            new Test("wand_never_breaks_blocks", 100, VoidraGameTests::wandNeverBreaksBlocks),
            new Test("energy_cell_powers_drill", 100, VoidraGameTests::energyCellPowersDrill),
            new Test("lens_fits_above_lens_mount", 100, VoidraGameTests::lensFitsAboveLensMount),
            new Test("red_lens_focuses_redstone", 20, VoidraGameTests::redLensFocusesRedstone),
            new Test("wand_keeps_the_lens", 100, VoidraGameTests::wandKeepsTheLens),
            new Test("drops_depend_on_dimension", 20, VoidraGameTests::dropsDependOnDimension),
            new Test("each_tier_drills_the_next_crystal", 20, VoidraGameTests::eachTierDrillsTheNextCrystal),
            new Test("magenta_lens_focuses_crystals", 20, VoidraGameTests::magentaLensFocusesCrystals),
            new Test("jei_chances_add_up", 20, VoidraGameTests::jeiChancesAddUp),
            new Test("clear_lens_has_no_focus", 100, VoidraGameTests::clearLensHasNoFocus),
            new Test("lens_is_required", 100, VoidraGameTests::lensIsRequired),
            new Test("modifier_slots_need_a_modifier", 100, VoidraGameTests::modifierSlotsNeedAModifier),
            new Test("modifier_counts_by_tier", 20, VoidraGameTests::modifierCountsByTier),
            new Test("speed_modifiers_speed_up_drill", 100, VoidraGameTests::speedModifiersSpeedUpDrill),
            new Test("energy_modifiers_save_energy", 100, VoidraGameTests::energyModifiersSaveEnergy),
            new Test("focus_modifiers_sharpen_lens", 100, VoidraGameTests::focusModifiersSharpenLens),
            new Test("every_part_has_a_recipe", 20, VoidraGameTests::everyPartHasARecipe),
            new Test("stabilizers_hang_around_the_column", 20, VoidraGameTests::stabilizersHangAroundTheColumn),
            new Test("stray_block_takes_drill_apart", 120, VoidraGameTests::strayBlockTakesDrillApart));

    static {
        for (Test test : TESTS) {
            FUNCTIONS.register(test.name(), () -> test.function());
        }
    }

    private VoidraGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(VoidraGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, "default"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test test : TESTS) {
            Identifier id = Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, test.name());
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, STRUCTURE, test.maxTicks(), 0, true)));
        }
    }

    /** Builds the whole structure, bedrock under the lens and (optionally) a chest on the controller. */
    private static VoidDrillBlockEntity buildDrill(GameTestHelper helper, boolean chest) {
        for (DrillStructure.Element element : DrillStructure.layout(TIER)) {
            helper.setBlock(CONTROLLER.offset(element.offset()), ModContent.structureBlock(TIER, element.part()));
        }
        helper.setBlock(CONTROLLER.offset(DrillStructure.lensOffset(TIER)).below(), Blocks.BEDROCK);
        helper.setBlock(CONTROLLER, ModContent.drill(TIER));
        if (chest) {
            helper.setBlock(CHEST, Blocks.CHEST);
        }
        VoidDrillBlockEntity drill = helper.getBlockEntity(CONTROLLER, VoidDrillBlockEntity.class);
        drill.getEnergy().set(drill.getEnergy().getCapacityAsInt());
        return drill;
    }

    private static void drillMinesIntoChest(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        int startEnergy = drill.getEnergy().getAmountAsInt();
        helper.succeedWhen(() -> {
            helper.assertTrue(drill.isFormed(), "drill did not form");
            ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
            helper.assertFalse(chest.isEmpty(), "no ore in the chest yet");
            helper.assertTrue(drill.getEnergy().getAmountAsInt() <= startEnergy - TIER.energyPerTick() * TIER.ticksPerDrop(),
                    "a drop should cost a full cycle of energy");
        });
    }

    private static void incompleteStructureDoesNotForm(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        BlockPos corner = CONTROLLER.offset(TIER.radius(), -1, TIER.radius());
        helper.setBlock(corner, Blocks.AIR);
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(drill.isFormed(), "a drill with a missing frame should not form");
            helper.assertTrue(drill.getStatus() == VoidDrillBlockEntity.Status.INCOMPLETE, "status should be INCOMPLETE");
            helper.succeed();
        });
    }

    private static void noChestNoDrilling(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, false);
        int startEnergy = drill.getEnergy().getAmountAsInt();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(drill.isFormed(), "drill did not form");
            helper.assertTrue(drill.getStatus() == VoidDrillBlockEntity.Status.NO_OUTPUT, "status should be NO_OUTPUT, was " + drill.getStatus());
            helper.assertTrue(drill.getEnergy().getAmountAsInt() == startEnergy, "a drill with no chest should not spend energy");
            helper.assertTrue(drill.getProgress() == 0, "a drill with no chest should not make progress");
            helper.succeed();
        });
    }

    private static void blockedBeamStopsDrill(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        BlockPos underLens = CONTROLLER.offset(DrillStructure.lensOffset(TIER)).below();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(drill.isActive(), "drill should be working before the beam is blocked");
            helper.setBlock(underLens, Blocks.STONE);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(drill.getStatus() == VoidDrillBlockEntity.Status.BEAM_BLOCKED, "status should be BEAM_BLOCKED, was " + drill.getStatus());
            helper.assertFalse(drill.isActive(), "a blocked drill should stop");
            helper.succeed();
        });
    }

    private static void breakingAFrameUnforms(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(drill.isFormed(), "drill did not form");
            helper.destroyBlock(CONTROLLER.offset(-TIER.radius(), -1, 0));
        });
        helper.runAfterDelay(30, () -> {
            helper.assertFalse(drill.isFormed(), "breaking a frame should unform the drill within a few ticks");
            helper.succeed();
        });
    }

    /** The mock player doesn't get creative abilities from its game mode, unlike a real one. */
    private static Player creativePlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        return player;
    }

    /** Runs the wand until it stops, like holding right-click; returns why it stopped. */
    private static AssemblyWandItem.Result runWand(GameTestHelper helper, Player player) {
        BlockPos controller = helper.absolutePos(CONTROLLER);
        for (int i = 0; i < 1000; i++) {
            AssemblyWandItem.Result result = AssemblyWandItem.placeNext(helper.getLevel(), player, controller);
            if (result != AssemblyWandItem.Result.PLACED) {
                return result;
            }
        }
        throw helper.assertionException("the wand never stopped");
    }

    private static void wandBuildsDrillInCreative(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModContent.drill(TIER));
        AssemblyWandItem.Result result = runWand(helper, creativePlayer(helper));
        helper.assertTrue(result == AssemblyWandItem.Result.DONE, "the wand should finish the structure, stopped with " + result);
        VoidDrillBlockEntity drill = helper.getBlockEntity(CONTROLLER, VoidDrillBlockEntity.class);
        helper.succeedWhen(() -> helper.assertTrue(drill.isFormed(), "the built drill did not form"));
    }

    private static void wandUsesInventoryBlocks(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModContent.drill(TIER));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // Everything but the lens mount, so the wand runs out right at it.
        player.getInventory().add(new ItemStack(ModContent.frame(TIER), 64));
        player.getInventory().add(new ItemStack(ModContent.stabilizer(TIER), 24));
        player.getInventory().add(new ItemStack(ModContent.emitter(TIER), 2));
        player.getInventory().add(new ItemStack(ModContent.CLEAR_LENS.get()));
        player.getInventory().add(new ItemStack(ModContent.VOID_PLATING.get(), 16));
        player.getInventory().add(new ItemStack(ModContent.NULL_MODIFIER.get(), 2));

        AssemblyWandItem.Result result = runWand(helper, player);
        helper.assertTrue(result == AssemblyWandItem.Result.MISSING_BLOCK, "the wand should stop for the lens mount, stopped with " + result);
        helper.assertTrue(player.getInventory().countItem(ModContent.frame(TIER).asItem()) == 64 - 32,
                "the wand should have used the frames it placed so far");
        helper.assertBlockNotPresent(ModContent.lensMount(TIER), CONTROLLER.offset(DrillStructure.lensOffset(TIER)));

        player.getInventory().add(new ItemStack(ModContent.lensMount(TIER)));
        result = runWand(helper, player);
        helper.assertTrue(result == AssemblyWandItem.Result.DONE, "with a lens mount the wand should finish, stopped with " + result);
        helper.assertTrue(player.getInventory().countItem(ModContent.frame(TIER).asItem()) == 64 - 56, "56 frames should be used in total");
        helper.assertTrue(player.getInventory().countItem(ModContent.stabilizer(TIER).asItem()) == 0, "all 24 stabilizers should be used");
        helper.succeed();
    }

    private static void wandNeverBreaksBlocks(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModContent.drill(TIER));
        BlockPos inTheWay = CONTROLLER.offset(TIER.radius(), -1, TIER.radius());
        helper.setBlock(inTheWay, Blocks.STONE);
        AssemblyWandItem.Result result = runWand(helper, creativePlayer(helper));
        helper.assertTrue(result == AssemblyWandItem.Result.BLOCKED, "the wand should stop at the stone, stopped with " + result);
        helper.assertBlockPresent(Blocks.STONE, inTheWay);
        helper.succeed();
    }

    private static void energyCellPowersDrill(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        drill.getEnergy().set(0);
        helper.setBlock(CONTROLLER.east(), ModContent.INFINITE_ENERGY_CELL.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(drill.isActive(), "the drill should work on the cell's energy");
            helper.assertTrue(drill.getEnergy().getAmountAsInt() > 0, "the cell should keep the drill charged");
        });
    }

    private static void lensFitsAboveLensMount(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        helper.setBlock(CONTROLLER.offset(DrillStructure.lensSlotOffset(TIER)), ModContent.lens(DyeColor.RED));
        helper.succeedWhen(() -> {
            helper.assertTrue(drill.isFormed(), "a lens above the lens mount should still form the drill");
            helper.assertTrue(drill.getLens() == DyeColor.RED, "the drill should read its red lens, got " + drill.getLens());
        });
    }

    private static void redLensFocusesRedstone(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        int rolls = 4000;
        int plain = 0;
        int focused = 0;
        for (int i = 0; i < rolls; i++) {
            if (VoidDrops.roll(TIER, null, Level.OVERWORLD.identifier(), random).is(Items.REDSTONE_ORE)) {
                plain++;
            }
            if (VoidDrops.roll(TIER, DyeColor.RED, Level.OVERWORLD.identifier(), random).is(Items.REDSTONE_ORE)) {
                focused++;
            }
        }
        helper.assertTrue(plain < rolls * 0.2, "without a lens redstone should be uncommon, got " + plain + "/" + rolls);
        helper.assertTrue(focused > rolls * 0.45, "a red lens should make redstone common, got " + focused + "/" + rolls);
        helper.succeed();
    }

    private static void wandKeepsTheLens(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModContent.drill(TIER));
        BlockPos slot = CONTROLLER.offset(DrillStructure.lensSlotOffset(TIER));
        helper.setBlock(slot, ModContent.lens(DyeColor.BLUE));
        AssemblyWandItem.Result result = runWand(helper, creativePlayer(helper));
        helper.assertTrue(result == AssemblyWandItem.Result.DONE, "the wand should build around a lens, stopped with " + result);
        helper.assertBlockPresent(ModContent.lens(DyeColor.BLUE), slot);
        VoidDrillBlockEntity drill = helper.getBlockEntity(CONTROLLER, VoidDrillBlockEntity.class);
        helper.succeedWhen(() -> helper.assertTrue(drill.isFormed() && drill.getLens() == DyeColor.BLUE, "drill should form with its blue lens"));
    }

    private static void dropsDependOnDimension(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        boolean quartzInOverworld = false;
        boolean coalInNether = false;
        boolean quartzInNether = false;
        boolean coalInEnd = false;
        for (int i = 0; i < 2000; i++) {
            quartzInOverworld |= VoidDrops.roll(TIER, null, Level.OVERWORLD.identifier(), random).is(Items.NETHER_QUARTZ_ORE);
            ItemStack nether = VoidDrops.roll(TIER, null, Level.NETHER.identifier(), random);
            coalInNether |= nether.is(Items.COAL_ORE);
            quartzInNether |= nether.is(Items.NETHER_QUARTZ_ORE);
            coalInEnd |= VoidDrops.roll(TIER, null, Level.END.identifier(), random).is(Items.COAL_ORE);
        }
        helper.assertFalse(quartzInOverworld, "nether quartz should only come from drills in the Nether");
        helper.assertFalse(coalInNether, "coal should not come from drills in the Nether");
        helper.assertTrue(quartzInNether, "a drill in the Nether should find nether quartz");
        helper.assertTrue(coalInEnd, "a dimension with no drops of its own should drill like the Overworld");
        helper.succeed();
    }

    private static void eachTierDrillsTheNextCrystal(GameTestHelper helper) {
        RandomSource random = RandomSource.create(11);
        DrillTier[] tiers = DrillTier.values();
        for (int i = 0; i < tiers.length; i++) {
            Item crystal = ModContent.crystal(tiers[i]);
            boolean fromTierBefore = false;
            boolean fromTwoTiersBefore = false;
            // The newest crystal is the rarest (Voidrium is about one drop in 170), so roll plenty.
            for (int roll = 0; roll < 5000; roll++) {
                // Umbrite, the first crystal, also comes out of the Umbrite drill itself.
                fromTierBefore |= VoidDrops.roll(tiers[Math.max(0, i - 1)], null, Level.OVERWORLD.identifier(), random).is(crystal);
                if (i >= 2) {
                    fromTwoTiersBefore |= VoidDrops.roll(tiers[i - 2], null, Level.OVERWORLD.identifier(), random).is(crystal);
                }
            }
            helper.assertTrue(fromTierBefore, crystal + " should come out of the drill one tier below");
            helper.assertFalse(fromTwoTiersBefore, crystal + " should not come out of drills two tiers below");
        }
        helper.succeed();
    }

    private static void magentaLensFocusesCrystals(GameTestHelper helper) {
        RandomSource random = RandomSource.create(5);
        int rolls = 4000;
        int plain = 0;
        int focused = 0;
        for (int i = 0; i < rolls; i++) {
            if (isCrystal(VoidDrops.roll(TIER, null, Level.OVERWORLD.identifier(), random))) {
                plain++;
            }
            if (isCrystal(VoidDrops.roll(TIER, DyeColor.MAGENTA, Level.OVERWORLD.identifier(), random))) {
                focused++;
            }
        }
        // Tier 1 with vanilla ores: about 8% crystals, about 40% with the magenta lens.
        helper.assertTrue(plain < rolls * 0.15, "without a lens crystals should be uncommon, got " + plain + "/" + rolls);
        helper.assertTrue(focused > rolls * 0.3, "the magenta lens should make crystals common, got " + focused + "/" + rolls);
        helper.succeed();
    }

    /** The odds JEI shows: each dimension's chances add up to 1, and the lens raises its own drops. */
    private static void jeiChancesAddUp(GameTestHelper helper) {
        for (DrillTier tier : DrillTier.values()) {
            List<VoidDrops.Chance> chances = VoidDrops.server().chances(tier);
            for (Identifier dimension : List.of(Level.OVERWORLD.identifier(), Level.NETHER.identifier())) {
                double total = chances.stream().filter(drop -> drop.dimension().equals(dimension)).mapToDouble(VoidDrops.Chance::plain).sum();
                helper.assertTrue(Math.abs(total - 1) < 1e-9, tier + " chances in " + dimension + " should add up to 1, got " + total);
            }
            for (VoidDrops.Chance drop : chances) {
                helper.assertTrue(drop.lens() == null ? drop.focused() == drop.plain() : drop.focused() > drop.plain(),
                        drop.item() + " with its lens should be more likely than without");
            }
        }
        VoidDrops.Chance nebulite = VoidDrops.server().chances(TIER).stream()
                .filter(drop -> drop.item() == ModContent.crystal(DrillTier.NEBULITE) && drop.dimension().equals(Level.OVERWORLD.identifier()))
                .findFirst().orElseThrow(() -> helper.assertionException("the Umbrite drill should list Nebulite crystals"));
        helper.assertTrue(nebulite.lens() == DyeColor.MAGENTA, "the crystal lens is the magenta one, got " + nebulite.lens());
        helper.succeed();
    }

    private static boolean isCrystal(ItemStack stack) {
        for (DrillTier tier : DrillTier.values()) {
            if (stack.is(ModContent.crystal(tier))) {
                return true;
            }
        }
        return false;
    }

    private static void clearLensHasNoFocus(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        helper.setBlock(CONTROLLER.offset(DrillStructure.lensSlotOffset(TIER)), ModContent.CLEAR_LENS.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(drill.isFormed(), "a clear lens should still form the drill");
            helper.assertTrue(drill.getLens() == null, "a clear lens should not focus anything, got " + drill.getLens());
        });
    }

    private static void lensIsRequired(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        helper.setBlock(CONTROLLER.offset(DrillStructure.lensSlotOffset(TIER)), ModContent.emitter(TIER));
        helper.runAfterDelay(40, () -> {
            helper.assertFalse(drill.isFormed(), "a beam emitter in place of the lens should not form the drill");
            helper.succeed();
        });
    }

    private static void modifierSlotsNeedAModifier(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        BlockPos slot = DrillStructure.layout(TIER).stream()
                .filter(element -> element.part() == DrillStructure.Part.MODIFIER)
                .findFirst().orElseThrow().offset();
        helper.setBlock(CONTROLLER.offset(slot), ModContent.VOID_PLATING.get());
        helper.runAfterDelay(40, () -> {
            helper.assertFalse(drill.isFormed(), "void plating in a modifier slot should not form the drill");
            helper.setBlock(CONTROLLER.offset(slot), ModContent.NULL_MODIFIER.get());
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(drill.isFormed(), "the null modifier should fill the slot");
            helper.succeed();
        });
    }

    private static void speedModifiersSpeedUpDrill(GameTestHelper helper) {
        helper.assertTrue(DrillTier.speedPercent(20) == 300 && DrillTier.VOIDRIUM.energyPerTick(20, 0, 0) == 720_000,
                "20 speed modifiers should make a tier-6 drill 3x as fast for 9x the energy");
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        DrillStructure.layout(TIER).stream().filter(element -> element.part() == DrillStructure.Part.MODIFIER)
                .forEach(element -> helper.setBlock(CONTROLLER.offset(element.offset()), ModContent.SPEED_MODIFIER.get()));
        int[] before = new int[2];
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(drill.isActive() && drill.getSpeedModifiers() == 2, "the drill should work with its 2 speed modifiers, has "
                    + drill.getSpeedModifiers());
            before[0] = drill.getEnergy().getAmountAsInt();
            before[1] = drill.getProgress();
        });
        helper.runAfterDelay(70, () -> {
            int spent = before[0] - drill.getEnergy().getAmountAsInt();
            helper.assertTrue(spent == 10 * 288, "2 speed modifiers should cost 200 x 1.2 x 1.2 = 288 FE/t, spent " + spent + " in 10 ticks");
            helper.assertTrue(drill.getProgress() - before[1] == 10 * 120, "2 speed modifiers should make 1.2 ticks of work per tick");
            helper.succeed();
        });
    }

    private static void energyModifiersSaveEnergy(GameTestHelper helper) {
        helper.assertTrue(DrillTier.VOIDRIUM.energyPerTick(0, 20, 0) == 26_666 && DrillTier.VOIDRIUM.energyPerTick(10, 10, 0) == 160_000,
                "a tier-6 floor full of energy modifiers should use a third; 10 of each, 2x as fast for 2x the energy");
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        DrillStructure.layout(TIER).stream().filter(element -> element.part() == DrillStructure.Part.MODIFIER)
                .forEach(element -> helper.setBlock(CONTROLLER.offset(element.offset()), ModContent.ENERGY_MODIFIER.get()));
        int[] before = new int[2];
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(drill.isActive() && drill.getEnergyModifiers() == 2, "the drill should work with its 2 energy modifiers, has "
                    + drill.getEnergyModifiers());
            before[0] = drill.getEnergy().getAmountAsInt();
            before[1] = drill.getProgress();
        });
        helper.runAfterDelay(70, () -> {
            int spent = before[0] - drill.getEnergy().getAmountAsInt();
            helper.assertTrue(spent == 10 * 166, "2 energy modifiers should cost 200 / 1.2 = 166 FE/t, spent " + spent + " in 10 ticks");
            helper.assertTrue(drill.getProgress() - before[1] == 10 * 100, "energy modifiers should not change the speed");
            helper.succeed();
        });
    }

    private static void focusModifiersSharpenLens(GameTestHelper helper) {
        helper.assertTrue(VoidDrops.focusMultiplier(0) == 8 && Math.abs(VoidDrops.focusMultiplier(2) - 9.4) < 0.001
                        && Math.abs(VoidDrops.focusMultiplier(20) - 22) < 0.001,
                "the lens should multiply its drops by 8, its boost growing 10% per focus modifier");
        helper.assertTrue(DrillTier.UMBRITE.energyPerTick(0, 0, 2) == 220 && DrillTier.VOIDRIUM.energyPerTick(0, 0, 20) == 160_000,
                "each focus modifier should cost 5% more energy");
        RandomSource random = RandomSource.create(42);
        int rolls = 4000;
        int lensOnly = 0;
        int sharpened = 0;
        int clear = 0;
        for (int i = 0; i < rolls; i++) {
            lensOnly += VoidDrops.roll(TIER, DyeColor.RED, 0, Level.OVERWORLD.identifier(), random).is(Items.REDSTONE_ORE) ? 1 : 0;
            sharpened += VoidDrops.roll(TIER, DyeColor.RED, 20, Level.OVERWORLD.identifier(), random).is(Items.REDSTONE_ORE) ? 1 : 0;
            clear += VoidDrops.roll(TIER, null, 20, Level.OVERWORLD.identifier(), random).is(Items.REDSTONE_ORE) ? 1 : 0;
        }
        helper.assertTrue(sharpened > lensOnly + rolls / 10, "focus modifiers should make the red lens find more redstone, got "
                + sharpened + " vs " + lensOnly + "/" + rolls);
        helper.assertTrue(clear < rolls * 0.2, "focus modifiers without a coloured lens should change nothing, got " + clear + "/" + rolls);
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        helper.setBlock(CONTROLLER.offset(DrillStructure.lensSlotOffset(TIER)), ModContent.lens(DyeColor.RED));
        DrillStructure.layout(TIER).stream().filter(element -> element.part() == DrillStructure.Part.MODIFIER)
                .forEach(element -> helper.setBlock(CONTROLLER.offset(element.offset()), ModContent.FOCUS_MODIFIER.get()));
        int[] before = new int[1];
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(drill.isActive() && drill.getFocusModifiers() == 2 && drill.getLens() == DyeColor.RED,
                    "the drill should work with its red lens and 2 focus modifiers, has " + drill.getFocusModifiers());
            before[0] = drill.getEnergy().getAmountAsInt();
        });
        helper.runAfterDelay(70, () -> {
            int spent = before[0] - drill.getEnergy().getAmountAsInt();
            helper.assertTrue(spent == 10 * 220, "2 focus modifiers should cost 200 x 1.1 = 220 FE/t, spent " + spent + " in 10 ticks");
            helper.succeed();
        });
    }

    /** Every drill part of every tier, the floor, the lenses and the wand can be crafted. */
    private static void everyPartHasARecipe(GameTestHelper helper) {
        List<String> names = new java.util.ArrayList<>(List.of("void_plating", "null_modifier", "speed_modifier", "energy_modifier",
                "focus_modifier", "clear_lens", "clear_lens_from_coloured", "assembly_wand", "umbrite_crystal"));
        for (DrillTier tier : DrillTier.values()) {
            for (String part : List.of("frame", "stabilizer", "beam_emitter", "lens_mount", "void_drill", "crystal_block")) {
                names.add(tier.material() + "_" + part);
            }
        }
        for (DyeColor color : DyeColor.values()) {
            names.add(color.getSerializedName() + "_lens");
        }
        var recipes = helper.getLevel().getServer().getRecipeManager();
        List<String> missing = names.stream().filter(name -> recipes.byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, name))).isEmpty()).toList();
        helper.assertTrue(missing.isEmpty(), "missing recipes: " + missing);
        helper.succeed();
    }

    private static void modifierCountsByTier(GameTestHelper helper) {
        int[] expected = {2, 4, 8, 12, 16, 20};
        for (DrillTier tier : DrillTier.values()) {
            long count = DrillStructure.layout(tier).stream().filter(element -> element.part() == DrillStructure.Part.MODIFIER).count();
            helper.assertTrue(count == expected[tier.ordinal()] && count == tier.modifierSlots(),
                    tier + " should have " + expected[tier.ordinal()] + " modifier slots, has " + count);
        }
        helper.succeed();
    }

    private static void stabilizersHangAroundTheColumn(GameTestHelper helper) {
        int[] layers = {0, 1, 1, 2, 2, 3};
        for (DrillTier tier : DrillTier.values()) {
            // Only the middle layers: the bottom layer has its own stabilizer cross and floor.
            for (int down = 1; -1 - down > -tier.depth(); down++) {
                BlockPos side = new BlockPos(1, -1 - down, 0);
                BlockPos corner = new BlockPos(1, -1 - down, 1);
                boolean present = DrillStructure.layout(tier).stream()
                        .anyMatch(element -> element.part() == DrillStructure.Part.STABILIZER && element.offset().equals(side));
                helper.assertTrue(DrillStructure.layout(tier).stream()
                                .anyMatch(element -> element.offset().equals(corner) && element.part() == DrillStructure.Part.EMPTY),
                        tier + ": the corners beside the column should stay empty");
                helper.assertTrue(present == (down <= layers[tier.ordinal()]),
                        tier + ": stabilizer " + down + " layer(s) under the top should be " + (down <= layers[tier.ordinal()]));
            }
        }
        helper.succeed();
    }

    private static void strayBlockTakesDrillApart(GameTestHelper helper) {
        VoidDrillBlockEntity drill = buildDrill(helper, true);
        // Inside the box, not part of the structure: a middle-layer spot next to a corner pillar.
        BlockPos stray = CONTROLLER.offset(2, -2, 1);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(drill.isFormed(), "drill did not form");
            helper.setBlock(stray, Blocks.STONE);
            // Placing through a player fires the event that wakes the controller; a test helper does not.
            drill.onStructureChanged(helper.absolutePos(stray));
        });
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(drill.isFormed(), "a stray block inside the structure should take it apart");
            helper.setBlock(stray, Blocks.AIR);
            drill.onStructureChanged(helper.absolutePos(stray));
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(drill.isFormed(), "removing the stray block should let the drill form again");
            helper.succeed();
        });
    }
}
