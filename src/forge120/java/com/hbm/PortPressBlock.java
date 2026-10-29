package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** Source press occupies its core plus two dummy blocks above it. */
public final class PortPressBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    private static final ThreadLocal<Boolean> TEARING_DOWN = ThreadLocal.withInitial(() -> false);

    public PortPressBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos base = context.getClickedPos();
        for (int part = 1; part <= 2; part++) {
            BlockPos upper = base.above(part);
            if (upper.getY() >= context.getLevel().getMaxBuildHeight()
                    || !context.getLevel().hasChunkAt(upper)
                    || !context.getLevel().getWorldBorder().isWithinBounds(upper)
                    || !context.getLevel().getBlockState(upper).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == 0) {
            for (int part = 1; part <= 2; part++) level.setBlock(pos.above(part), state.setValue(PART, part), 3);
        }
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new PortPressBlockEntity(pos, state) : null;
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return state.getValue(PART) != 0 ? null
                : createTickerHelper(type, PortBlockEntities.PRESS.get(), PortPressBlockEntity::tick);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        BlockPos core = pos.below(state.getValue(PART));
        if (!level.hasChunkAt(core)) return InteractionResult.PASS;
        if (!level.isClientSide && !player.isShiftKeyDown()
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof PortPressBlockEntity press) {
            NetworkHooks.openScreen(serverPlayer, press, core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock()) && !TEARING_DOWN.get()) {
            TEARING_DOWN.set(true);
            try {
                BlockPos core = pos.below(state.getValue(PART));
                BlockState coreState = level.getBlockState(core);
                if (coreState.is(this) && coreState.getValue(PART) == 0
                        && level.getBlockEntity(core) instanceof PortPressBlockEntity press) {
                    for (int slot = 0; slot < press.items().getSlots(); slot++) {
                        ItemStack stack = press.items().getStackInSlot(slot);
                        if (!stack.isEmpty()) Containers.dropItemStack(level, core.getX(), core.getY(), core.getZ(), stack.copy());
                    }
                }
                for (int part = 0; part <= 2; part++) {
                    BlockPos member = core.above(part);
                    if (member.equals(pos)) continue;
                    BlockState memberState = level.getBlockState(member);
                    if (memberState.is(this) && memberState.getValue(PART) == part
                            && memberState.getValue(FACING) == state.getValue(FACING)) {
                        level.setBlock(member, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            } finally {
                TEARING_DOWN.set(false);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
