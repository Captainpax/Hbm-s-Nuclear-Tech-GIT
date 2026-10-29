package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

/** A source gas port; storage remains solely in the blast-furnace core. */
public final class PortBlastFurnacePortBlockEntity extends BlockEntity {
    public PortBlastFurnacePortBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.BLAST_FURNACE_PORT.get(), pos, state);
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (!isRemoved() && level != null && capability == ForgeCapabilities.FLUID_HANDLER
                && side == PortBlastFurnaceBlock.portDirection(getBlockState())) {
            BlockPos core = PortBlastFurnaceBlock.corePos(worldPosition, getBlockState());
            if (level.hasChunkAt(core)
                    && level.getBlockEntity(core) instanceof PortBlastFurnaceBlockEntity machine) {
                return machine.getCapability(capability, null);
            }
        }
        return super.getCapability(capability, side);
    }
}
