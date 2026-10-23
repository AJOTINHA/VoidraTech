package com.ajg.voidratech.client;

import com.ajg.voidratech.VoidraTech;
import com.ajg.voidratech.miner.DrillTier;
import com.ajg.voidratech.registry.ModContent;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import java.util.EnumMap;
import java.util.Map;

@Mod(value = VoidraTech.MOD_ID, dist = Dist.CLIENT)
public class VoidraTechClient {
    /** The spinning rotor (core and coils) of each drill tier, drawn by {@link VoidDrillRenderer}. */
    private static final Map<DrillTier, StandaloneModelKey<BlockStateModelPart>> ROTOR_MODELS = new EnumMap<>(DrillTier.class);

    static {
        for (DrillTier tier : DrillTier.values()) {
            String name = tier.material() + "_void_drill_rotor";
            ROTOR_MODELS.put(tier, new StandaloneModelKey<>(() -> VoidraTech.MOD_ID + ":" + name));
        }
    }

    public VoidraTechClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(VoidraTechClient::registerRenderers);
        modBus.addListener(VoidraTechClient::registerStandaloneModels);
    }

    public static StandaloneModelKey<BlockStateModelPart> rotorModel(DrillTier tier) {
        return ROTOR_MODELS.get(tier);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.VOID_DRILL_BE.get(), VoidDrillRenderer::new);
    }

    private static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        ROTOR_MODELS.forEach((tier, key) -> event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(
                Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, "block/" + tier.material() + "_void_drill_rotor"))));
    }
}
