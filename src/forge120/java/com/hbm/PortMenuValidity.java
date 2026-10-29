package com.hbm;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source TileEntityMachineBase identity check and 128-square-block interaction range. */
final class PortMenuValidity {
    private PortMenuValidity() {}

    static boolean isUsable(Player player, BlockEntity machine) {
        var level = machine.getLevel();
        return level != null && level == player.level() && !machine.isRemoved()
                && level.hasChunkAt(machine.getBlockPos())
                && level.getBlockEntity(machine.getBlockPos()) == machine
                && player.distanceToSqr(machine.getBlockPos().getX() + 0.5,
                        machine.getBlockPos().getY() + 0.5, machine.getBlockPos().getZ() + 0.5) <= 128;
    }
}
