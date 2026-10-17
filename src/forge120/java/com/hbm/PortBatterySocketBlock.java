package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
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

/** Four occupied floor blocks with one battery-item core, matching the source socket footprint. */
public final class PortBatterySocketBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    private static final ThreadLocal<Boolean> TEARING_DOWN = ThreadLocal.withInitial(() -> false);

    public PortBatterySocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    static BlockPos partPos(BlockPos core, Direction facing, int part) {
        Direction back = facing.getOpposite();
        Direction right = facing.getClockWise();
        return core.relative(back, part & 1).relative(right, part >> 1);
    }

    static BlockPos corePos(BlockPos partPos, BlockState state) {
        Direction facing = state.getValue(FACING);
        int part = state.getValue(PART);
        return partPos.relative(facing, part & 1).relative(facing.getCounterClockWise(), part >> 1);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos core = context.getClickedPos();
        for (int part = 1; part < 4; part++) {
            BlockPos pos = partPos(core, facing, part);
            if (!context.getLevel().hasChunkAt(pos)
                    || !context.getLevel().getWorldBorder().isWithinBounds(pos)
                    || !context.getLevel().getBlockState(pos).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != 0) return;
        for (int part = 1; part < 4; part++) {
            BlockPos extra = partPos(pos, state.getValue(FACING), part);
            level.setBlock(extra, state.setValue(PART, part), 3);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new PortBatterySocketBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(PART) != 0 ? null
                : createTickerHelper(type, PortBlockEntities.BATTERY_SOCKET.get(), PortBatterySocketBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock()) && !TEARING_DOWN.get()) {
            TEARING_DOWN.set(true);
            try {
                BlockPos core = corePos(pos, state);
                // A player can break a proxy while its adjacent core chunk is
                // unloaded. Load the four-block footprint to avoid a ghost core.
                level.getChunkAt(core);
                if (level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket) {
                    ItemStack stack = socket.items().getStackInSlot(0);
                    if (!stack.isEmpty()) Containers.dropItemStack(level, core.getX(), core.getY(), core.getZ(),
                            stack.copy());
                }
                for (int part = 0; part < 4; part++) {
                    BlockPos member = partPos(core, state.getValue(FACING), part);
                    if (member.equals(pos)) continue;
                    level.getChunkAt(member);
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

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockPos core = corePos(pos, state);
        return level.hasChunkAt(core) && level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket
                ? socket.comparatorPower() : 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        BlockPos core = corePos(pos, state);
        if (!level.hasChunkAt(core)) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket
                && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.translatable("message.hbm.battery_readout",
                        socket.reservoir().power(), socket.reservoir().capacity()), true);
            } else {
                NetworkHooks.openScreen(serverPlayer, socket, core);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
