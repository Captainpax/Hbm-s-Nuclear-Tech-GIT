package com.hbm;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Biome tags alone do not forbid a biome from being reused in another dimension. */
public final class PortOverworldPlacement extends PlacementFilter {
    public static final PortOverworldPlacement INSTANCE = new PortOverworldPlacement();
    public static final Codec<PortOverworldPlacement> CODEC = Codec.unit(INSTANCE);

    private PortOverworldPlacement() {}

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return context.getLevel().getLevel().dimension().equals(Level.OVERWORLD);
    }

    @Override
    public PlacementModifierType<?> type() {
        return PortPlacementModifiers.OVERWORLD_ONLY.get();
    }
}
