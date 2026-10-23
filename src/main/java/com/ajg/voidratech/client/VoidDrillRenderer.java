package com.ajg.voidratech.client;

import com.ajg.voidratech.block.entity.VoidDrillBlockEntity;
import com.ajg.voidratech.miner.DrillStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The drill's moving parts: the rotor (glowing core and its coils) spinning, and the beam, a beacon-style beam in the tier's
 * colour from the bottom of the lens mount down to the top of the bedrock (or into the void), drawn while the
 * structure is formed and the path is clear.
 */
public class VoidDrillRenderer implements BlockEntityRenderer<VoidDrillBlockEntity, VoidDrillRenderer.State> {
    private static final int[] NO_TINTS = new int[0];

    public VoidDrillRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static class State extends BlockEntityRenderState {
        @Nullable BlockStateModelPart rotor;
        float coilAngle;
        boolean formed;
        /** The lens, relative to the controller; the beam also shows through it. */
        int lensY;
        boolean visible;
        /** Bottom of the beam, relative to the controller. */
        int start;
        int length;
        int color;
        float animationTime;
        float radiusScale = 1.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VoidDrillBlockEntity drill, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(drill, state, partialTicks, cameraPosition, breakProgress);
        state.rotor = Minecraft.getInstance().getModelManager().getStandaloneModel(VoidraTechClient.rotorModel(drill.getTier()));
        state.coilAngle = drill.getCoilAngle(partialTicks);
        state.length = drill.getBeamLength();
        state.formed = drill.isFormed();
        // Below the drill: shown as soon as the structure is formed with a clear path, working or not.
        state.visible = state.formed && state.length > 0;
        if (!state.formed) {
            return;
        }
        state.lensY = DrillStructure.lensSlotOffset(drill.getTier()).getY();
        state.start = DrillStructure.lensOffset(drill.getTier()).getY() - state.length;
        state.color = 0xFF000000 | drill.getTier().color();
        state.animationTime = drill.getLevel() == null ? 0.0F : Math.floorMod(drill.getLevel().getGameTime(), 40) + partialTicks;
        // Like the beacon: the beam gets thicker far away so it stays visible.
        float distance = (float) cameraPosition.subtract(Vec3.atCenterOf(drill.getBlockPos())).horizontalDistance();
        LocalPlayer player = Minecraft.getInstance().player;
        state.radiusScale = player != null && player.isScoping() ? 1.0F : Math.max(1.0F, distance / 96.0F);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.rotor != null) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.rotateDegrees(Axis.YP, state.coilAngle);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            collector.submitMultiLayerBlockModel(poseStack, List.of(state.rotor), false, NO_TINTS, state.lightCoords,
                    OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.formed) {
            // Through the lens, seen through the clear lens's glass.
            BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0F, state.animationTime,
                    state.lensY, 1, state.color, 0.2F, 0.25F);
        }
        if (state.visible) {
            BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0F, state.animationTime,
                    state.start, state.length, state.color, 0.2F * state.radiusScale, 0.25F * state.radiusScale);
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    /** Covers the whole beam, so it is culled like any other block entity instead of being drawn everywhere. */
    @Override
    public AABB getRenderBoundingBox(VoidDrillBlockEntity drill) {
        BlockPos pos = drill.getBlockPos();
        int length = Math.max(0, drill.getBeamLength());
        int lensY = pos.getY() + DrillStructure.lensOffset(drill.getTier()).getY();
        return new AABB(pos.getX(), lensY - length, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
}
