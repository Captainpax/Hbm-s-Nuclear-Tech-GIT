package com.hbm;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortOreHarvestGameTests {
    private PortOreHarvestGameTests() {}

    @GameTest(template = "empty")
    public static void sourceOresDropWithEveryPickaxeButNotHandsOrAxes(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("7ef127d6-3aee-4518-95a8-243b288c3561"), "OreHarvestTest"));
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 2, pos.getY(), pos.getZ());
        Block[] ores = {PortBlocks.ORE_URANIUM.get(), PortBlocks.ORE_THORIUM.get(),
                PortBlocks.ORE_TITANIUM.get(), PortBlocks.ORE_COPPER.get(), PortBlocks.ORE_TUNGSTEN.get(),
                PortBlocks.ORE_LEAD.get(), PortBlocks.ORE_BERYLLIUM.get(), PortBlocks.CLUSTER_IRON.get(),
                PortBlocks.CLUSTER_TITANIUM.get(), PortBlocks.CLUSTER_ALUMINIUM.get(), PortBlocks.CLUSTER_COPPER.get()};
        Item[] products = {PortItems.ORE_URANIUM.get(), PortItems.ORE_THORIUM.get(),
                PortItems.ORE_TITANIUM.get(), PortItems.ORE_COPPER.get(), PortItems.ORE_TUNGSTEN.get(),
                PortItems.ORE_LEAD.get(), PortItems.ORE_BERYLLIUM.get(), PortItems.CRYSTAL_IRON.get(),
                PortItems.CRYSTAL_TITANIUM.get(), PortItems.CRYSTAL_ALUMINIUM.get(), PortItems.CRYSTAL_COPPER.get()};
        Item[] tools = {Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE, Items.STONE_PICKAXE,
                Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.WOODEN_AXE, Items.AIR};
        // Legacy generic rock blocks have no explicit harvest level. Forge falls
        // back to ItemPickaxe's material check, which accepts even wood and gold.
        // Exercise actual Survival destruction: inspecting loot tables alone does
        // not cover the tool gate in ServerPlayerGameMode.
        for (int ore = 0; ore < ores.length; ore++) {
            for (int tool = 0; tool < tools.length; tool++) {
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tools[tool]));
                level.setBlock(pos, ores[ore].defaultBlockState(), 3);
                boolean expected = tool < 6;
                if (player.hasCorrectToolForDrops(level.getBlockState(pos)) != expected
                        || !player.gameMode.destroyBlock(pos)) {
                    throw new IllegalStateException("Source harvest gate failed for " + ores[ore] + " using " + tools[tool]);
                }
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos));
                if (expected ? drops.size() != 1 || !drops.get(0).getItem().is(products[ore])
                        || drops.get(0).getItem().getCount() != 1 : !drops.isEmpty()) {
                    throw new IllegalStateException("Source Survival ore drop failed for " + ores[ore] + " using " + tools[tool]);
                }
                if (expected && player.getMainHandItem().getDamageValue() != 1) {
                    throw new IllegalStateException("Ore harvest did not consume normal pickaxe durability");
                }
                drops.forEach(ItemEntity::discard);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        player.getInventory().clearContent();
        helper.succeed();
    }
}
