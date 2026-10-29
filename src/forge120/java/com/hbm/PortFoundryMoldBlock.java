package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ToolActions;

/** Source small mold: click to install a mold or collect the cooled casting. */
public final class PortFoundryMoldBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public PortFoundryMoldBlock(Properties properties) { super(properties); }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortFoundryMoldBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, PortBlockEntities.FOUNDRY_MOLD.get(), PortFoundryMoldBlockEntity::tick);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PortFoundryMoldBlockEntity mold)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack output = mold.items().getStackInSlot(1);
        if (!output.isEmpty()) {
            ItemStack taken = mold.items().extractItem(1, output.getCount(), false);
            if (!player.getInventory().add(taken)) player.drop(taken, false);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(PortItems.MOLD_PLATE_CAST.get()) && mold.items().getStackInSlot(0).isEmpty()) {
            mold.items().setStackInSlot(0, held.copyWithCount(1));
            if (!player.getAbilities().instabuild) held.shrink(1);
            return InteractionResult.CONSUME;
        }
        if (held.canPerformAction(ToolActions.SHOVEL_DIG)) {
            ItemStack recovered = mold.takeCopperScraps();
            if (!recovered.isEmpty() && !player.getInventory().add(recovered)) player.drop(recovered, false);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && held.isEmpty() && mold.copperQuanta() == 0
                && !mold.items().getStackInSlot(0).isEmpty()) {
            ItemStack removed = mold.items().extractItem(0, 1, false);
            if (!player.getInventory().add(removed)) player.drop(removed, false);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof PortFoundryMoldBlockEntity mold) {
            for (int slot = 0; slot < 2; slot++) {
                ItemStack stack = mold.items().getStackInSlot(slot);
                if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        mold.items().extractItem(slot, stack.getCount(), false));
            }
            ItemStack recovered = mold.takeCopperScraps();
            if (!recovered.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), recovered);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
