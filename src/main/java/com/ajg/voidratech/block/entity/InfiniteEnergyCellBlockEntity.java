package com.ajg.voidratech.block.entity;

import com.ajg.voidratech.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Testing block: every tick, fills each neighbour that takes FE with as much as it will accept. */
public class InfiniteEnergyCellBlockEntity extends BlockEntity {
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<EnergyHandler, @Nullable Direction>[] neighbours = new BlockCapabilityCache[Direction.values().length];

    public InfiniteEnergyCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.INFINITE_ENERGY_CELL_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InfiniteEnergyCellBlockEntity cell) {
        for (Direction side : Direction.values()) {
            BlockCapabilityCache<EnergyHandler, @Nullable Direction> cache = cell.neighbours[side.ordinal()];
            if (cache == null) {
                cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, (ServerLevel) level, pos.relative(side), side.getOpposite());
                cell.neighbours[side.ordinal()] = cache;
            }
            EnergyHandler target = cache.getCapability();
            if (target != null) {
                try (Transaction tx = Transaction.openRoot()) {
                    target.insert(Integer.MAX_VALUE, tx);
                    tx.commit();
                }
            }
        }
    }
}
