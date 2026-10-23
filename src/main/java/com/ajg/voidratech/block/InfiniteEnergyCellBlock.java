package com.ajg.voidratech.block;

import com.ajg.voidratech.block.entity.InfiniteEnergyCellBlockEntity;
import com.ajg.voidratech.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Infinite Energy Cell: endless FE for testing, pushed into every side and free to pull. */
public class InfiniteEnergyCellBlock extends BaseEntityBlock {
    public InfiniteEnergyCellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteEnergyCellBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModContent.INFINITE_ENERGY_CELL_BE.get(), InfiniteEnergyCellBlockEntity::serverTick);
    }
}
