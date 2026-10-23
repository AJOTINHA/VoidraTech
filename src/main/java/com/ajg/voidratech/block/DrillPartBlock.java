package com.ajg.voidratech.block;

import com.ajg.voidratech.miner.DrillTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A Void Drill structure block (frames, stabilizers, emitters, lens mount, lens, plating): tells nearby controllers when it comes or goes. */
public class DrillPartBlock extends Block {
    public DrillPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(state.getBlock())) {
            DrillTracker.onPartChanged(serverLevel, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        DrillTracker.onPartChanged(level, pos);
    }
}
