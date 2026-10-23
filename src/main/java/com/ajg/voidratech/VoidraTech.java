package com.ajg.voidratech;

import com.ajg.voidratech.miner.DrillTracker;
import com.ajg.voidratech.miner.VoidDrops;
import com.ajg.voidratech.registry.ModContent;
import com.ajg.voidratech.test.VoidraGameTests;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.InfiniteEnergyHandler;
import org.slf4j.Logger;

@Mod(VoidraTech.MOD_ID)
public class VoidraTech {
    public static final String MOD_ID = "voidratech";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VoidraTech(IEventBus modBus, ModContainer container) {
        ModContent.register(modBus);
        VoidraGameTests.register(modBus);
        DrillTracker.register();
        VoidDrops.register(modBus);
        modBus.addListener(VoidraTech::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModContent.VOID_DRILL_BE.get(), (drill, side) -> drill.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModContent.INFINITE_ENERGY_CELL_BE.get(), (cell, side) -> InfiniteEnergyHandler.INSTANCE);
    }
}
