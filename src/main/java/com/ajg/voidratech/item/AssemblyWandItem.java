package com.ajg.voidratech.item;

import com.ajg.voidratech.block.VoidDrillBlock;
import com.ajg.voidratech.miner.DrillStructure;
import com.ajg.voidratech.miner.DrillTier;
import com.ajg.voidratech.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Assembly Wand: right-click a Void Drill controller and hold to build its structure around it, one block every
 * {@link #TICKS_PER_BLOCK} ticks. Blocks come from the player's inventory (free in creative); occupied spots are never
 * broken.
 */
public class AssemblyWandItem extends Item {
    public static final int TICKS_PER_BLOCK = 2;
    /** How far the player may wander from the controller while building. */
    private static final double MAX_DISTANCE_SQR = 24.0 * 24.0;

    /** Controller each player is building, on the server. */
    private static final Map<UUID, BlockPos> TARGETS = new HashMap<>();

    public enum Result {
        PLACED,
        DONE,
        MISSING_BLOCK,
        BLOCKED,
        NO_PERMISSION,
        NOT_A_DRILL
    }

    public AssemblyWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof VoidDrillBlock)) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide()) {
            TARGETS.put(player.getUUID(), context.getClickedPos().immutable());
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return;
        }
        BlockPos target = TARGETS.get(player.getUUID());
        if (target == null || player.distanceToSqr(Vec3.atCenterOf(target)) > MAX_DISTANCE_SQR) {
            player.releaseUsingItem();
            return;
        }
        int elapsed = getUseDuration(stack, entity) - ticksRemaining;
        if (elapsed % TICKS_PER_BLOCK != 0) {
            return;
        }
        Result result = placeNext(serverLevel, player, target);
        if (result != Result.PLACED) {
            player.releaseUsingItem();
        }
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        TARGETS.remove(entity.getUUID());
    }

    /**
     * Places the next missing block of the structure around the controller at {@code controller}, telling the
     * player why when it can't.
     */
    public static Result placeNext(ServerLevel level, Player player, BlockPos controller) {
        if (!(level.getBlockState(controller).getBlock() instanceof VoidDrillBlock drill)) {
            return Result.NOT_A_DRILL;
        }
        DrillTier tier = drill.tier();
        for (DrillStructure.Element element : DrillStructure.layout(tier)) {
            BlockPos pos = controller.offset(element.offset());
            Block wanted = ModContent.structureBlock(tier, element.part());
            BlockState current = level.getBlockState(pos);
            if (ModContent.accepts(tier, element.part(), current)) {
                continue;
            }
            if (!current.canBeReplaced() || element.part() == DrillStructure.Part.EMPTY) {
                // Never breaks anything: a block in the way, or anything left where the structure must be empty, stops it.
                player.sendOverlayMessage(Component.translatable("message.voidratech.wand.blocked",
                        pos.getX(), pos.getY(), pos.getZ(), current.getBlock().getName()));
                return Result.BLOCKED;
            }
            if (!player.mayBuild() || !level.mayInteract(player, pos)) {
                return Result.NO_PERMISSION;
            }
            if (!player.hasInfiniteMaterials() && !takeFromInventory(player.getInventory(), wanted)) {
                player.sendOverlayMessage(Component.translatable("message.voidratech.wand.missing", wanted.getName()));
                return Result.MISSING_BLOCK;
            }
            BlockState state = wanted.defaultBlockState();
            level.setBlock(pos, state, Block.UPDATE_ALL);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
            SoundType sound = state.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            return Result.PLACED;
        }
        player.sendOverlayMessage(Component.translatable("message.voidratech.wand.done"));
        return Result.DONE;
    }


    private static boolean takeFromInventory(Inventory inventory, Block block) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
