package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The uranium branch of the 1.7.10 BlockOutgas behavior. */
public final class PortUraniumOre extends Block {
    public PortUraniumOre(BlockBehaviour.Properties properties) {
        super(properties.randomTicks());
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos destination = pos.relative(net.minecraft.core.Direction.getRandom(random));
        if (level.getBlockState(destination).isAir()) {
            level.setBlock(destination, PortBlocks.GAS_RADON.get().defaultBlockState(), 3);
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, PortBlocks.GAS_RADON.get().defaultBlockState(), 3);
        }
    }
}
