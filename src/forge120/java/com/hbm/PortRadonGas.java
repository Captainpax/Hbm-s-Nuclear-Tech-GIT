package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Invisible, non-solid radon with movement, decay, and server-side exposure. */
public final class PortRadonGas extends Block {
    public PortRadonGas(BlockBehaviour.Properties properties) {
        super(properties.noCollission().noOcclusion().replaceable());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity living) PortRadiation.exposeRadon(living);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 10);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(50) == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }
        Direction primary = random.nextInt(5) == 0 ? Direction.UP : Direction.DOWN;
        if (move(level, pos, primary)) return;
        Direction horizontal = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (!move(level, pos, horizontal)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    private boolean move(ServerLevel level, BlockPos pos, Direction direction) {
        BlockPos target = pos.relative(direction);
        if (!level.getBlockState(target).isAir()) return false;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(target, defaultBlockState(), 3);
        level.scheduleTick(target, this, 2);
        return true;
    }
}
