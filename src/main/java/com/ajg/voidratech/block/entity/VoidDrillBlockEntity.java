package com.ajg.voidratech.block.entity;

import com.ajg.voidratech.block.LensBlock;
import com.ajg.voidratech.block.ModifierBlock;
import com.ajg.voidratech.block.VoidDrillBlock;
import com.ajg.voidratech.miner.DrillEnergy;
import com.ajg.voidratech.miner.DrillStructure;
import com.ajg.voidratech.miner.DrillTier;
import com.ajg.voidratech.miner.DrillTracker;
import net.minecraft.world.item.DyeColor;
import com.ajg.voidratech.miner.VoidDrops;
import com.ajg.voidratech.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Void Drill controller. Kept cheap on the server on purpose:
 * <ul>
 *   <li>the structure (and the spots inside it that must stay empty) is checked a slice per tick, and only when a block in it changes
 *       ({@link DrillTracker}) plus a rare safety pass;</li>
 *   <li>the beam column is re-checked every two seconds, also a slice per tick;</li>
 *   <li>a drill that can't work (no power, no chest, chest full) sleeps and only looks again once a second;</li>
 *   <li>clients only get a packet when something they can see changes (formed, working, beam length), never for
 *       progress or energy.</li>
 * </ul>
 */
public class VoidDrillBlockEntity extends BlockEntity {
    private static final int STRUCTURE_BLOCKS_PER_TICK = 64;
    private static final int BEAM_BLOCKS_PER_TICK = 64;
    private static final int BEAM_RECHECK_TICKS = 40;
    private static final int SAFETY_RECHECK_TICKS = 600;
    private static final int UNLOADED_RETRY_TICKS = 20;
    private static final int IDLE_TICKS = 20;
    private static final int NO_SCAN = Integer.MIN_VALUE;

    private static final String ENERGY_KEY = "energy";
    private static final String PROGRESS_KEY = "progress";
    private static final String PENDING_KEY = "pending";
    private static final String FORMED_KEY = "formed";
    private static final String ACTIVE_KEY = "active";
    private static final String BEAM_KEY = "beam_length";
    private static final String LENS_KEY = "lens";
    private static final String SPEED_KEY = "speed_modifiers";
    private static final String ENERGY_MODIFIERS_KEY = "energy_modifiers";
    private static final String FOCUS_KEY = "focus_modifiers";

    /** Why the drill is not working right now, for the status message. */
    public enum Status {
        WORKING,
        INCOMPLETE,
        BEAM_BLOCKED,
        NO_OUTPUT,
        OUTPUT_FULL,
        NO_ENERGY,
        NO_DROPS
    }

    private final DrillTier tier;
    private final DrillEnergy energy;

    // Structure.
    private boolean formed;
    private int structureScanIndex = -1;
    private long nextSafetyCheck;
    private long nextStructureRetry = -1;
    private DrillStructure.@Nullable Element missing;
    /** Lens in the slot above the lens mount, or null when an emitter is there; read when the structure forms. */
    private @Nullable DyeColor lens;
    /** Speed, energy and focus modifiers in the floor's modifier slots, counted by the last full structure scan. */
    private int speedModifiers;
    private int energyModifiers;
    private int focusModifiers;
    private int scanSpeedModifiers;
    private int scanEnergyModifiers;
    private int scanFocusModifiers;

    // Beam: scanned from right below the lens mount down to bedrock or the void.
    private int beamScanY = NO_SCAN;
    private long nextBeamCheck;
    private boolean beamClear;
    /** Blocks between the lens mount and the end of the beam; -1 when there is no beam. Synced. */
    private int beamLength = -1;
    private int blockedAtY;

    // Work.
    /** Work towards the next drop, in hundredths of a tick: speed modifiers add more than 100 per tick. */
    private int progress;
    private ItemStack pending = ItemStack.EMPTY;
    private boolean active;
    private int sleepTicks;
    private Status status = Status.INCOMPLETE;
    private final List<BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>> outputs = new ArrayList<>();

    // Client-side rotor animation (core and coils): spins up while the drill works and winds down when it stops.
    private static final float COIL_MAX_SPEED = 9.0F;
    private static final float COIL_ACCELERATION = 0.04F;
    private float coilAngle;
    private float previousCoilAngle;
    private float coilSpeed;

    public VoidDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.VOID_DRILL_BE.get(), pos, state);
        this.tier = ((VoidDrillBlock) state.getBlock()).tier();
        this.energy = new DrillEnergy(tier, this::setChanged);
    }

    public DrillTier getTier() {
        return tier;
    }

    public DrillEnergy getEnergy() {
        return energy;
    }

    public boolean isFormed() {
        return formed;
    }

    public boolean isActive() {
        return active;
    }

    public int getBeamLength() {
        return beamLength;
    }

    public Status getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public @Nullable DyeColor getLens() {
        return lens;
    }

    public int getSpeedModifiers() {
        return speedModifiers;
    }

    public int getEnergyModifiers() {
        return energyModifiers;
    }

    public int getFocusModifiers() {
        return focusModifiers;
    }

    /** Energy spent per working tick, with this drill's modifiers. */
    /** A lens multiplier for the status, like "8" or "11.5". */
    private static String multiplier(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public int energyPerTick() {
        return tier.energyPerTick(speedModifiers, energyModifiers, focusModifiers);
    }

    private void setLens(@Nullable DyeColor lens) {
        if (this.lens != lens) {
            this.lens = lens;
            setChanged();
        }
    }

    // --- Lifecycle -------------------------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            DrillTracker.add(serverLevel, worldPosition);
            structureScanIndex = 0;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            DrillTracker.remove(serverLevel, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel) {
            DrillTracker.remove(serverLevel, worldPosition);
        }
    }

    /** A block that may belong to this structure was placed or removed: check the structure again. */
    public void onStructureChanged(BlockPos pos) {
        structureScanIndex = 0;
        if (pos.getX() == worldPosition.getX() && pos.getZ() == worldPosition.getZ() && pos.getY() < worldPosition.getY()) {
            // Also covers blocks placed in the beam column right below the structure.
            nextBeamCheck = 0;
        }
    }

    // --- Ticking ---------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, VoidDrillBlockEntity drill) {
        drill.tickServer((ServerLevel) level);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, VoidDrillBlockEntity drill) {
        drill.previousCoilAngle = drill.coilAngle;
        if (drill.active) {
            drill.coilSpeed += (COIL_MAX_SPEED - drill.coilSpeed) * COIL_ACCELERATION;
        } else if (drill.coilSpeed > 0.0F) {
            // Wind down, slowing near the next quarter turn so the square rotor stops lined up with the frame.
            float rest = 90.0F - drill.coilAngle % 90.0F;
            drill.coilSpeed = Math.max(0.25F, Math.min(drill.coilSpeed * (1.0F - COIL_ACCELERATION), rest * 0.1F));
            if (drill.coilSpeed >= rest) {
                drill.coilAngle += rest;
                drill.coilSpeed = 0.0F;
            }
        }
        drill.coilAngle += drill.coilSpeed;
        if (drill.coilAngle >= 360.0F) {
            drill.coilAngle -= 360.0F;
            drill.previousCoilAngle -= 360.0F;
        }
    }

    /** Rotor rotation in degrees, for rendering. */
    public float getCoilAngle(float partialTicks) {
        return previousCoilAngle + (coilAngle - previousCoilAngle) * partialTicks;
    }

    private void tickServer(ServerLevel level) {
        long time = level.getGameTime();
        if (structureScanIndex < 0 && (time >= nextSafetyCheck || (nextStructureRetry >= 0 && time >= nextStructureRetry))) {
            structureScanIndex = 0;
        }
        if (structureScanIndex >= 0) {
            scanStructure(level, time);
        }
        if (!formed) {
            status = Status.INCOMPLETE;
            setActive(false);
            return;
        }

        if (beamScanY == NO_SCAN && time >= nextBeamCheck) {
            beamScanY = worldPosition.getY() + DrillStructure.lensOffset(tier).getY() - 1;
        }
        if (beamScanY != NO_SCAN) {
            scanBeam(level, time);
        }
        if (!beamClear) {
            status = Status.BEAM_BLOCKED;
            setActive(false);
            return;
        }

        tickWork(level);
    }

    private void scanStructure(ServerLevel level, long time) {
        List<DrillStructure.Element> layout = DrillStructure.layout(tier);
        if (structureScanIndex == 0) {
            scanSpeedModifiers = 0;
            scanEnergyModifiers = 0;
            scanFocusModifiers = 0;
        }
        for (int checked = 0; checked < STRUCTURE_BLOCKS_PER_TICK && structureScanIndex < layout.size(); checked++) {
            DrillStructure.Element element = layout.get(structureScanIndex);
            BlockPos pos = worldPosition.offset(element.offset());
            if (!level.isLoaded(pos)) {
                // Part of the structure is in an unloaded chunk: keep the current state and try again later.
                structureScanIndex = -1;
                nextStructureRetry = time + UNLOADED_RETRY_TICKS;
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!ModContent.accepts(tier, element.part(), state)) {
                structureScanIndex = -1;
                nextStructureRetry = -1;
                nextSafetyCheck = time + SAFETY_RECHECK_TICKS;
                missing = element;
                setFormed(false);
                return;
            }
            if (element.part() == DrillStructure.Part.LENS_SLOT) {
                setLens(state.getBlock() instanceof LensBlock lensBlock ? lensBlock.color() : null);
            }
            if (state.getBlock() instanceof ModifierBlock modifier) {
                switch (modifier.kind()) {
                    case SPEED -> scanSpeedModifiers++;
                    case ENERGY -> scanEnergyModifiers++;
                    case FOCUS -> scanFocusModifiers++;
                    case NULL -> {
                    }
                }
            }
            structureScanIndex++;
        }
        if (structureScanIndex >= layout.size()) {
            structureScanIndex = -1;
            nextStructureRetry = -1;
            nextSafetyCheck = time + SAFETY_RECHECK_TICKS;
            missing = null;
            if (speedModifiers != scanSpeedModifiers || energyModifiers != scanEnergyModifiers || focusModifiers != scanFocusModifiers) {
                speedModifiers = scanSpeedModifiers;
                energyModifiers = scanEnergyModifiers;
                focusModifiers = scanFocusModifiers;
                setChanged();
            }
            setFormed(true);
        }
    }

    private void scanBeam(ServerLevel level, long time) {
        int startY = worldPosition.getY() + DrillStructure.lensOffset(tier).getY() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(worldPosition.getX(), beamScanY, worldPosition.getZ());
        for (int checked = 0; checked < BEAM_BLOCKS_PER_TICK; checked++) {
            int y = beamScanY;
            if (y < level.getMinY()) {
                finishBeam(time, true, startY - level.getMinY() + 1);
                return;
            }
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.BEDROCK)) {
                finishBeam(time, true, startY - y);
                return;
            }
            if (!state.isAir() && !state.getCollisionShape(level, cursor).isEmpty()) {
                blockedAtY = y;
                finishBeam(time, false, -1);
                return;
            }
            beamScanY--;
        }
    }

    private void finishBeam(long time, boolean clear, int length) {
        beamScanY = NO_SCAN;
        nextBeamCheck = time + BEAM_RECHECK_TICKS;
        beamClear = clear;
        if (length != beamLength) {
            beamLength = length;
            sync();
        }
    }

    private void tickWork(ServerLevel level) {
        if (sleepTicks > 0) {
            sleepTicks--;
            return;
        }
        if (!pending.isEmpty() && !flushPending(level)) {
            sleep(Status.OUTPUT_FULL);
            return;
        }
        if (progress == 0) {
            // A new drop only starts with somewhere to put it: no chest, no drilling.
            if (!VoidDrops.hasDrops(tier, level.dimension().identifier())) {
                sleep(Status.NO_DROPS);
                return;
            }
            Status output = checkOutput(level);
            if (output != Status.WORKING) {
                sleep(output);
                return;
            }
        }
        if (!energy.consume(energyPerTick())) {
            sleep(Status.NO_ENERGY);
            return;
        }
        status = Status.WORKING;
        setActive(true);
        progress += DrillTier.speedPercent(speedModifiers);
        if (progress >= tier.ticksPerDrop() * 100) {
            progress = 0;
            pending = VoidDrops.roll(tier, lens, focusModifiers, level.dimension().identifier(), level.getRandom());
            flushPending(level);
        }
        setChanged();
    }

    private void sleep(Status reason) {
        status = reason;
        sleepTicks = IDLE_TICKS;
        setActive(false);
    }

    private Status checkOutput(ServerLevel level) {
        boolean found = false;
        for (ResourceHandler<ItemResource> handler : outputHandlers(level)) {
            found = true;
            if (!ResourceHandlerUtil.isFull(handler)) {
                return Status.WORKING;
            }
        }
        return found ? Status.OUTPUT_FULL : Status.NO_OUTPUT;
    }

    /** Pushes the pending drop into the inventories next to the controller; true once it is all out. */
    private boolean flushPending(ServerLevel level) {
        for (ResourceHandler<ItemResource> handler : outputHandlers(level)) {
            if (pending.isEmpty()) {
                break;
            }
            int inserted = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(pending), pending.getCount(), null);
            pending.shrink(inserted);
        }
        if (pending.isEmpty()) {
            pending = ItemStack.EMPTY;
        }
        setChanged();
        return pending.isEmpty();
    }

    private List<ResourceHandler<ItemResource>> outputHandlers(ServerLevel level) {
        if (outputs.isEmpty()) {
            for (Direction side : Direction.values()) {
                outputs.add(BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, worldPosition.relative(side), side.getOpposite()));
            }
        }
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>(2);
        for (BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache : outputs) {
            ResourceHandler<ItemResource> handler = cache.getCapability();
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    // --- State shown to clients ------------------------------------------------------------------------------

    private void setFormed(boolean formed) {
        if (this.formed == formed) {
            return;
        }
        this.formed = formed;
        if (formed) {
            nextBeamCheck = 0;
        } else {
            beamScanY = NO_SCAN;
            beamClear = false;
            beamLength = -1;
            active = false;
        }
        setChanged();
        sync();
    }

    private void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            sync();
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Status lines shown when a player clicks the controller. */
    public List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        String prefix = "message.voidratech.drill.";
        lines.add(Component.translatable(prefix + "title", getBlockState().getBlock().getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!formed) {
            if (missing != null) {
                BlockPos pos = worldPosition.offset(missing.offset());
                if (missing.part() == DrillStructure.Part.EMPTY && level != null) {
                    lines.add(Component.translatable(prefix + "in_the_way", level.getBlockState(pos).getBlock().getName(),
                            pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.RED));
                    return lines;
                }
                Component part = switch (missing.part()) {
                    case LENS_SLOT -> Component.translatable(prefix + "any_lens");
                    case MODIFIER -> Component.translatable(prefix + "any_modifier");
                    default -> ModContent.structureBlock(tier, missing.part()).getName();
                };
                lines.add(Component.translatable(prefix + "incomplete", part,
                        pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.translatable(prefix + "checking").withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (level != null && level.getBlockState(worldPosition.offset(DrillStructure.lensSlotOffset(tier))).getBlock() instanceof LensBlock lensBlock) {
            // A coloured lens shows how much it multiplies its ores by, focus modifiers included; the clear lens has no focus.
            lines.add((lensBlock.color() == null
                    ? Component.translatable(prefix + "lens_no_focus", lensBlock.getName())
                    : Component.translatable(prefix + "lens", lensBlock.getName(), multiplier(VoidDrops.focusMultiplier(focusModifiers))))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (speedModifiers > 0) {
            lines.add(Component.translatable(prefix + "speed", speedModifiers, DrillTier.speedPercent(speedModifiers) - 100)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (energyModifiers > 0) {
            lines.add(Component.translatable(prefix + "efficiency", energyModifiers, DrillTier.EFFICIENCY_PERCENT_PER_MODIFIER * energyModifiers)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (focusModifiers > 0) {
            // Without a coloured lens there is nothing to sharpen, which the line says.
            int percent = VoidDrops.FOCUS_PERCENT_PER_MODIFIER * focusModifiers;
            lines.add(Component.translatable(prefix + (lens == null ? "focus_no_lens" : "focus"), focusModifiers, percent)
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(prefix + "energy", energy.getAmountAsInt(), energy.getCapacityAsInt(), energyPerTick())
                .withStyle(ChatFormatting.AQUA));
        Component state = switch (status) {
            case WORKING -> Component.translatable(prefix + "working", progress / tier.ticksPerDrop());
            case BEAM_BLOCKED -> Component.translatable(prefix + "beam_blocked", blockedAtY);
            default -> Component.translatable(prefix + status.name().toLowerCase(java.util.Locale.ROOT));
        };
        lines.add(state.copy().withStyle(status == Status.WORKING ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return lines;
    }

    // --- Saving and syncing ----------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child(ENERGY_KEY));
        output.putInt(PROGRESS_KEY, progress);
        output.storeNullable(LENS_KEY, DyeColor.CODEC, lens);
        output.putInt(SPEED_KEY, speedModifiers);
        output.putInt(ENERGY_MODIFIERS_KEY, energyModifiers);
        output.putInt(FOCUS_KEY, focusModifiers);
        if (!pending.isEmpty()) {
            output.store(PENDING_KEY, ItemStack.CODEC, pending);
        }
        output.putBoolean(FORMED_KEY, formed);
        output.putBoolean(ACTIVE_KEY, active);
        output.putInt(BEAM_KEY, beamLength);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child(ENERGY_KEY).ifPresent(energy::deserialize);
        progress = input.getIntOr(PROGRESS_KEY, progress);
        pending = input.read(PENDING_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        lens = input.read(LENS_KEY, DyeColor.CODEC).orElse(null);
        speedModifiers = input.getIntOr(SPEED_KEY, 0);
        energyModifiers = input.getIntOr(ENERGY_MODIFIERS_KEY, 0);
        focusModifiers = input.getIntOr(FOCUS_KEY, 0);
        formed = input.getBooleanOr(FORMED_KEY, false);
        active = input.getBooleanOr(ACTIVE_KEY, false);
        beamLength = input.getIntOr(BEAM_KEY, -1);
        // Trust a saved beam until the first re-check, so a reloaded drill doesn't stall for a scan.
        beamClear = formed && beamLength >= 0;
    }

    /** Clients only need what they draw: no energy, progress or pending items. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FORMED_KEY, formed);
        tag.putBoolean(ACTIVE_KEY, active);
        tag.putInt(BEAM_KEY, beamLength);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
