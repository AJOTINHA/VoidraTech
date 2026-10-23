package com.ajg.voidratech.block;

import com.ajg.voidratech.block.entity.VoidDrillBlockEntity;
import com.ajg.voidratech.item.AssemblyWandItem;
import com.ajg.voidratech.miner.DrillTier;
import com.ajg.voidratech.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Void Drill controller: the top centre of the structure. Click it with an empty hand to see its status. */
public class VoidDrillBlock extends BaseEntityBlock {
    private final DrillTier tier;

    public VoidDrillBlock(DrillTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public DrillTier tier() {
        return tier;
    }

    /** Lets the Assembly Wand reach its own use instead of opening the status message. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof AssemblyWandItem ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VoidDrillBlockEntity drill) {
            drill.describe().forEach(player::sendSystemMessage);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidDrillBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModContent.VOID_DRILL_BE.get(), VoidDrillBlockEntity::clientTick)
                : createTickerHelper(type, ModContent.VOID_DRILL_BE.get(), VoidDrillBlockEntity::serverTick);
    }
}
