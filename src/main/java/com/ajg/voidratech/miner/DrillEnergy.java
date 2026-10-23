package com.ajg.voidratech.miner;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** A drill's FE buffer: cables can insert but not extract; the drill spends it directly. */
public class DrillEnergy extends SimpleEnergyHandler {
    private final Runnable onChanged;

    public DrillEnergy(DrillTier tier, Runnable onChanged) {
        super(tier.energyCapacity(), tier.maxEnergyInput(), 0);
        this.onChanged = onChanged;
    }

    /** Spends {@code amount} if it is all there; returns whether it was spent. */
    public boolean consume(int amount) {
        if (energy < amount) {
            return false;
        }
        set(energy - amount);
        return true;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }
}
