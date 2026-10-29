package com.hbm;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortMenuGameTests {
    private PortMenuGameTests() {}

    @GameTest(template = "empty")
    public static void machineMenusRejectAReplacementAtTheSamePosition(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("3dbe977e-14c8-4ea1-9c2f-763aa2f66d7d"), "MenuLifetimeTest"));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        for (var block : List.of(PortBlocks.MACHINE_BATTERY.get(), PortBlocks.MACHINE_BATTERY_SOCKET.get(),
                PortBlocks.MACHINE_ELECTRIC_FURNACE.get(), PortBlocks.MACHINE_PRESS.get(),
                PortBlocks.MACHINE_BLAST_FURNACE.get(), PortBlocks.HEATER_FIREBOX.get(), PortBlocks.MACHINE_CRUCIBLE.get())) {
            BlockState state = block.defaultBlockState();
            level.setBlock(pos, state, 3);
            var original = level.getBlockEntity(pos);
            player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            var menu = ((MenuProvider) original).createMenu(91, player.getInventory(), player);
            if (!menu.stillValid(player)) throw new IllegalStateException("Live machine menu rejected its owner: " + block);
            // Another player can break and replace the same block before the viewer's next tick.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, state, 3);
            if (level.getBlockEntity(pos) == original) throw new IllegalStateException("Fixture did not replace the core");
            if (menu.stillValid(player)) {
                throw new IllegalStateException("Old menu still accepts clicks into the removed machine inventory: " + block);
            }
            var replacement = (MenuProvider) level.getBlockEntity(pos);
            var current = replacement.createMenu(92, player.getInventory(), player);
            if (!current.stillValid(player)) throw new IllegalStateException("Replacement machine cannot open a fresh menu");
            player.moveTo(pos.getX() + 11.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (!current.stillValid(player)) throw new IllegalStateException("Machine menu shortened the source 128-square range");
            player.moveTo(pos.getX() + 12.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (current.stillValid(player)) throw new IllegalStateException("Machine menu lost its distance check");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }
}
