package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The asbestos branch of legacy BlockOutgas: random, stepped-on, and break emissions. */
public final class PortAsbestosOre extends Block {
    public PortAsbestosOre(BlockBehaviour.Properties properties) {
        super(properties.randomTicks());
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        release(level, pos.relative(Direction.getRandom(random)));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (level.isClientSide && level.getBlockState(pos.above()).isAir()) {
            for (int i = 0; i < 5; i++) {
                level.addParticle(ParticleTypes.MYCELIUM,
                        pos.getX() + level.random.nextFloat(), pos.getY() + 1.1D,
                        pos.getZ() + level.random.nextFloat(), 0.0D, 0.0D, 0.0D);
            }
        }
        if (!level.isClientSide && level.random.nextInt(10) == 0) {
            release(level, pos.above());
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide) release(level, pos);
    }

    private static void release(Level level, BlockPos destination) {
        if (level.getBlockState(destination).isAir()) {
            level.setBlock(destination, PortBlocks.GAS_ASBESTOS.get().defaultBlockState(), 3);
        }
    }
}
