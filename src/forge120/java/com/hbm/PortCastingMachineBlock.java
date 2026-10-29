package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.common.ToolActions;

/** Source 3x3 firebox and 3x3x2 crucible footprints, each with one core. */
public final class PortCastingMachineBlock extends BaseEntityBlock {
    public enum Kind { FIREBOX, CRUCIBLE }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("y", 0, 1);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, 2);
    private static final ThreadLocal<Boolean> TEARING_DOWN = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape[] CRUCIBLE_SHAPES = makeCrucibleShapes();
    private final Kind kind;

    public PortCastingMachineBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 1).setValue(Y, 0).setValue(Z, 1));
    }

    private int height() { return kind == Kind.CRUCIBLE ? 2 : 1; }

    private static VoxelShape[] makeCrucibleShapes() {
        // MachineCrucible's five source boxes, translated from the model origin
        // to the core block and clipped to each member's own unit cube.
        VoxelShape basin = Shapes.or(
                Shapes.box(-1, 0, -1, 2, 0.5, 2),
                Shapes.box(-0.75, 0.5, -0.75, 1.75, 1.5, -0.5),
                Shapes.box(-0.75, 0.5, -0.75, -0.5, 1.5, 1.75),
                Shapes.box(-0.75, 0.5, 1.5, 1.75, 1.5, 1.75),
                Shapes.box(1.5, 0.5, -0.75, 1.75, 1.5, 1.75));
        VoxelShape[] shapes = new VoxelShape[18];
        for (int y = 0; y < 2; y++) for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) {
            shapes[y * 9 + z * 3 + x] = Shapes.join(basin.move(1 - x, -y, 1 - z),
                    Shapes.block(), BooleanOp.AND).optimize();
        }
        return shapes;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return kind == Kind.CRUCIBLE ? CRUCIBLE_SHAPES[state.getValue(Y) * 9
                + state.getValue(Z) * 3 + state.getValue(X)] : Shapes.block();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    public static BlockPos corePos(BlockPos pos, BlockState state) {
        return pos.offset(1 - state.getValue(X), -state.getValue(Y), 1 - state.getValue(Z));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        Level level = context.getLevel();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < height(); y++) {
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
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < height(); y++) {
            level.setBlock(core.offset(x, y, z), defaultBlockState()
                    .setValue(FACING, state.getValue(FACING))
                    .setValue(X, x + 1).setValue(Y, y).setValue(Z, z + 1), 3);
        }
    }

    private static boolean isCore(BlockState state) {
        return state.getValue(X) == 1 && state.getValue(Y) == 0 && state.getValue(Z) == 1;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isCore(state)) return null;
        return kind == Kind.FIREBOX ? new PortFireboxBlockEntity(pos, state)
                : new PortCrucibleBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide || !isCore(state)) return null;
        return kind == Kind.FIREBOX
                ? createTickerHelper(type, PortBlockEntities.FIREBOX.get(), PortFireboxBlockEntity::tick)
                : createTickerHelper(type, PortBlockEntities.CRUCIBLE.get(), PortCrucibleBlockEntity::tick);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = corePos(pos, state);
        if (!level.hasChunkAt(core)) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(core) instanceof MenuProvider provider) {
            if (provider instanceof PortCrucibleBlockEntity crucible
                    && player.getItemInHand(hand).canPerformAction(ToolActions.SHOVEL_DIG)) {
                ItemStack recovered = crucible.takeCopperScraps();
                if (!recovered.isEmpty() && !player.getInventory().add(recovered)) player.drop(recovered, false);
            } else if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, provider, core);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock()) && !TEARING_DOWN.get()) {
            TEARING_DOWN.set(true);
            try {
                BlockPos core = corePos(pos, state);
                BlockEntity entity = level.getBlockEntity(core);
                ItemStackHandler items = entity instanceof PortFireboxBlockEntity firebox ? firebox.items()
                        : entity instanceof PortCrucibleBlockEntity crucible ? crucible.items() : null;
                if (items != null) for (int slot = 0; slot < items.getSlots(); slot++) {
                    ItemStack stack = items.getStackInSlot(slot);
                    if (!stack.isEmpty()) Containers.dropItemStack(level, core.getX(), core.getY(), core.getZ(),
                            items.extractItem(slot, stack.getCount(), false));
                }
                if (entity instanceof PortCrucibleBlockEntity crucible) {
                    ItemStack recovered = crucible.takeCopperScraps();
                    if (!recovered.isEmpty()) Containers.dropItemStack(level,
                            core.getX(), core.getY(), core.getZ(), recovered);
                }
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < height(); y++) {
                    BlockPos member = core.offset(x, y, z);
                    // A machine can straddle a chunk boundary. Load the small
                    // footprint here so breaking it cannot leave orphan members
                    // in a neighboring chunk that happens to be unloaded.
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
