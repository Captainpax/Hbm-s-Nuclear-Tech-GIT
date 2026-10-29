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

/** Seven-high, three-wide source blast-furnace footprint with one server-owned core. */
public final class PortBlastFurnaceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("y", 0, 6);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, 2);
    private static final ThreadLocal<Boolean> TEARING_DOWN = ThreadLocal.withInitial(() -> false);

    public PortBlastFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 1).setValue(Y, 0).setValue(Z, 1));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    public static BlockPos corePos(BlockPos pos, BlockState state) {
        return pos.offset(1 - state.getValue(X), -state.getValue(Y), 1 - state.getValue(Z));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        // The source places the item at the front of a core one block behind it.
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        Level level = context.getLevel();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 6; y++) {
            BlockPos member = core.offset(x, y, z);
            if (member.equals(clicked)) continue;
            if (member.getY() < level.getMinBuildHeight() || member.getY() >= level.getMaxBuildHeight()
                    || !level.hasChunkAt(member) || !level.getWorldBorder().isWithinBounds(member)
                    || !level.getBlockState(member).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing)
                .setValue(X, clicked.getX() - core.getX() + 1)
                .setValue(Z, clicked.getZ() - core.getZ() + 1);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        BlockPos core = corePos(pos, state);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 6; y++) {
            level.setBlock(core.offset(x, y, z), defaultBlockState()
                    .setValue(FACING, state.getValue(FACING))
                    .setValue(X, x + 1).setValue(Y, y).setValue(Z, z + 1), 3);
        }
    }

    private static boolean isCore(BlockState state) {
        return state.getValue(X) == 1 && state.getValue(Y) == 0 && state.getValue(Z) == 1;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new PortBlastFurnaceBlockEntity(pos, state)
                : portDirection(state) != null ? new PortBlastFurnacePortBlockEntity(pos, state) : null;
    }

    public static Direction portDirection(BlockState state) {
        if (!(state.getBlock() instanceof PortBlastFurnaceBlock)) return null;
        int x = state.getValue(X) - 1;
        int y = state.getValue(Y);
        int z = state.getValue(Z) - 1;
        if (y == 0 && z == 0 && Math.abs(x) == 1) return x > 0 ? Direction.EAST : Direction.WEST;
        if (y == 0 && x == 0 && z == 1) return Direction.SOUTH;
        if (y == 6 && x == 0 && z == 0) return Direction.UP;
        Direction facing = state.getValue(FACING);
        return (y == 3 || y == 5) && x == facing.getStepX() && z == facing.getStepZ() ? facing : null;
    }

    public static java.util.List<BlockPos> fluidPorts(BlockPos core, BlockState state) {
        Direction facing = state.getValue(FACING);
        return java.util.List.of(core.east(), core.west(), core.south(),
                core.relative(facing).above(3), core.relative(facing).above(5), core.above(6));
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide || !isCore(state) ? null
                : createTickerHelper(type, PortBlockEntities.BLAST_FURNACE.get(), PortBlastFurnaceBlockEntity::tick);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePos(pos, state);
        if (!level.hasChunkAt(core)) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(core) instanceof PortBlastFurnaceBlockEntity machine) {
            NetworkHooks.openScreen(serverPlayer, machine, core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock()) && !TEARING_DOWN.get()) {
            TEARING_DOWN.set(true);
            try {
                BlockPos core = corePos(pos, state);
                if (level.getBlockState(core).is(this)
                        && level.getBlockEntity(core) instanceof PortBlastFurnaceBlockEntity machine) {
                    for (int slot = 0; slot < machine.items().getSlots(); slot++) {
                        ItemStack stack = machine.items().getStackInSlot(slot);
                        if (!stack.isEmpty()) Containers.dropItemStack(level,
                                core.getX(), core.getY(), core.getZ(), stack.copy());
                    }
                }
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 6; y++) {
                    BlockPos member = core.offset(x, y, z);
                    // Teardown must also remove members in a neighboring chunk
                    // that was unloaded after this structure was placed.
                    if (member.equals(pos)) continue;
                    BlockState memberState = level.getBlockState(member);
                    if (memberState.is(this) && corePos(member, memberState).equals(core)) {
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
