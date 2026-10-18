package com.hbm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** The legacy SchistStratum's four-octave, seed-driven layer, confined to its own chunk. */
public final class PortGneissStratumFeature extends Feature<NoneFeatureConfiguration> {
    private static final ConcurrentMap<Long, SimplexNoise[]> NOISE_BY_SEED = new ConcurrentHashMap<>();

    public PortGneissStratumFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int chunkX = context.origin().getX() & ~15;
        int chunkZ = context.origin().getZ() & ~15;
        SimplexNoise[] octaves = NOISE_BY_SEED.computeIfAbsent(level.getSeed(), PortGneissStratumFeature::octaves);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int x = chunkX; x < chunkX + 16; x++) {
            for (int z = chunkZ; z < chunkZ + 16; z++) {
                int range = range(octaves, x, z);
                if (range < 0) continue;
                for (int y = 30 - range; y <= 30 + range; y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.STONE)) {
                        level.setBlock(pos, PortBlocks.STONE_GNEISS.get().defaultBlockState(), 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    private static SimplexNoise[] octaves(long seed) {
        RandomSource random = new LegacyRandomSource(seed);
        SimplexNoise[] octaves = new SimplexNoise[4];
        for (int i = 0; i < octaves.length; i++) octaves[i] = new SimplexNoise(random);
        return octaves;
    }

    static int range(long seed, int x, int z) {
        SimplexNoise[] octaves = NOISE_BY_SEED.computeIfAbsent(seed, PortGneissStratumFeature::octaves);
        return range(octaves, x, z);
    }

    private static int range(SimplexNoise[] octaves, int x, int z) {
        double value = sample(octaves, x * 0.01D, z * 0.01D);
        if (value <= 5.0D) return -1;
        int range = (int) ((value - 5.0D) * 3.0D);
        if (range > 4) range = 8 - range;
        return range;
    }

    static double sample(SimplexNoise[] octaves, double x, double z) {
        double value = 0.0D;
        double frequency = 1.0D;
        for (SimplexNoise octave : octaves) {
            value += octave.getValue(x * frequency, z * frequency) / frequency;
            frequency *= 0.5D;
        }
        return value;
    }
}
