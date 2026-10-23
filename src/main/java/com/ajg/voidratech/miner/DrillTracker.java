package com.ajg.voidratech.miner;

import com.ajg.voidratech.block.entity.VoidDrillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loaded Void Drill controllers per dimension. When a structure block is placed or removed, only the controllers
 * whose structure could contain it are asked to re-check, so formed drills never poll their blocks every tick.
 */
public final class DrillTracker {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> CONTROLLERS = new HashMap<>();

    private DrillTracker() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> CONTROLLERS.clear());
        // Any block placed or broken inside a structure matters, not only drill parts: stray blocks take it apart.
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> onAnyBlockChanged(event.getLevel(), event.getPos()));
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> onAnyBlockChanged(event.getLevel(), event.getPos()));
    }

    private static void onAnyBlockChanged(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            onPartChanged(serverLevel, pos);
        }
    }

    public static void add(ServerLevel level, BlockPos pos) {
        CONTROLLERS.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = CONTROLLERS.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /** A structure block changed at {@code pos}: wake every controller whose structure covers it. */
    public static void onPartChanged(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = CONTROLLERS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        for (BlockPos controller : set) {
            int dy = pos.getY() - controller.getY();
            if (dy > 0 || dy < -DrillTier.MAX_DEPTH
                    || Math.abs(pos.getX() - controller.getX()) > DrillTier.MAX_RADIUS
                    || Math.abs(pos.getZ() - controller.getZ()) > DrillTier.MAX_RADIUS) {
                continue;
            }
            if (level.getBlockEntity(controller) instanceof VoidDrillBlockEntity drill) {
                drill.onStructureChanged(pos);
            }
        }
    }
}
