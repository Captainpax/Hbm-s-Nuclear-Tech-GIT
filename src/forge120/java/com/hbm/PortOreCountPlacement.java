package com.hbm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

/** Reads the per-world server config when a fresh chunk places an HBM ore feature. */
public final class PortOreCountPlacement extends RepeatingPlacement {
    public static final Codec<PortOreCountPlacement> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.STRING.fieldOf("ore").forGetter(placement -> placement.ore))
                    .apply(instance, PortOreCountPlacement::new));

    private final String ore;

    public PortOreCountPlacement(String ore) {
        // Reject typos when datapack features decode, rather than silently skipping an ore.
        PortConfig.oreCountSetting(ore);
        this.ore = ore;
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        return PortConfig.ENABLE_OVERWORLD_ORES.get() ? PortConfig.oreCount(ore) : 0;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PortPlacementModifiers.ORE_COUNT.get();
    }
}
