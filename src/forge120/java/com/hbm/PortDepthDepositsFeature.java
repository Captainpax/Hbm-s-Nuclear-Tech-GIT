package com.hbm;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Six source DepthDeposit spheres, clipped to the generating chunk for safe 1.20.1 worldgen. */
public final class PortDepthDepositsFeature extends Feature<NoneFeatureConfiguration> {
    private record Deposit(int chance, double fill, Supplier<Block> block) {}

    private static final Deposit[] DEPOSITS = {
            new Deposit(24, 0.6D, PortBlocks.CLUSTER_DEPTH_IRON),
            new Deposit(32, 0.6D, PortBlocks.CLUSTER_DEPTH_TITANIUM),
            new Deposit(32, 0.6D, PortBlocks.CLUSTER_DEPTH_TUNGSTEN),
            new Deposit(16, 0.8D, PortBlocks.ORE_DEPTH_CINNEBAR),
            new Deposit(16, 0.8D, PortBlocks.ORE_DEPTH_ZIRCONIUM),
            new Deposit(16, 0.8D, PortBlocks.ORE_DEPTH_BORAX)
    };

    public PortDepthDepositsFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!PortConfig.ENABLE_OVERWORLD_ORES.get()) return false;
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int chunkX = context.origin().getX() & ~15;
        int chunkZ = context.origin().getZ() & ~15;
        boolean placed = false;
        for (Deposit deposit : DEPOSITS) {
            if (random.nextInt(deposit.chance()) != 0) continue;
            int centerX = chunkX + random.nextInt(16);
            int centerY = level.getMinBuildHeight() + 1 + random.nextInt(3);
            int centerZ = chunkZ + random.nextInt(16);
            placed |= sphere(level, random, chunkX, chunkZ, centerX, centerY, centerZ,
                    deposit.fill(), deposit.block().get());
        }
        return placed;
    }

    private static boolean sphere(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ,
                                  int centerX, int centerY, int centerZ, double fill, Block ore) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 1;
        boolean placed = false;
        for (int x = Math.max(chunkX, centerX - 5); x <= Math.min(chunkX + 15, centerX + 5); x++) {
            for (int z = Math.max(chunkZ, centerZ - 5); z <= Math.min(chunkZ + 15, centerZ + 5); z++) {
                for (int y = Math.max(minY, centerY - 5); y <= Math.min(maxY, centerY + 5); y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.BEDROCK))) continue;
                    double distance = Math.sqrt((long) (x - centerX) * (x - centerX)
                            + (long) (y - centerY) * (y - centerY)
                            + (long) (z - centerZ) * (z - centerZ));
                    if (distance + random.nextInt(2) < 5.0D * fill) {
                        level.setBlock(pos, ore.defaultBlockState(), 2);
                        placed = true;
                    } else if (distance + random.nextInt(2) <= 5.0D) {
                        level.setBlock(pos, PortBlocks.STONE_DEPTH.get().defaultBlockState(), 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }
}
