package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The legacy classic HE cable: a narrow six-way conductor with no stored HE. */
public final class PortCableBlock extends Block {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    private static final VoxelShape CORE = box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5);

    public PortCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connectedState(context.getLevel(), context.getClickedPos());
    }

    BlockState connectedState(LevelAccessor level, BlockPos pos) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            state = state.setValue(property(direction), level.hasChunkAt(neighbor)
                    && connects(level.getBlockState(neighbor), direction));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(property(direction), level.hasChunkAt(neighborPos)
                && connects(neighborState, direction));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, box(5.5, 5.5, 0, 10.5, 10.5, 5.5));
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, box(5.5, 5.5, 10.5, 10.5, 10.5, 16));
        if (state.getValue(EAST)) shape = Shapes.or(shape, box(10.5, 5.5, 5.5, 16, 10.5, 10.5));
        if (state.getValue(WEST)) shape = Shapes.or(shape, box(0, 5.5, 5.5, 5.5, 10.5, 10.5));
        if (state.getValue(UP)) shape = Shapes.or(shape, box(5.5, 10.5, 5.5, 10.5, 16, 10.5));
        if (state.getValue(DOWN)) shape = Shapes.or(shape, box(5.5, 0, 5.5, 10.5, 5.5, 10.5));
        return shape;
    }

    static boolean connects(BlockState state, Direction direction) {
        return state.getBlock() instanceof PortCableBlock || state.getBlock() instanceof PortBatteryBlock
                || (direction.getAxis().isHorizontal() && state.getBlock() instanceof PortBatterySocketBlock);
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }
}
