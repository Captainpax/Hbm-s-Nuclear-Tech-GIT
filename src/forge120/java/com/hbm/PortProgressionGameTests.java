package com.hbm;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Source ore-dictionary behavior, including the copper naturally present in modern worlds. */
@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortProgressionGameTests {
    private static final boolean ALIASES = Boolean.getBoolean("hbm.port.taggedInputs");
    private PortProgressionGameTests() {}

    @GameTest(template = "empty")
    public static void vanillaCopperPressingUsesSourceYieldFuelAndStampWear(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, PortBlocks.MACHINE_PRESS.get().defaultBlockState(), 3);
        var press = (PortPressBlockEntity) level.getBlockEntity(pos);
        press.items().setStackInSlot(0, new ItemStack(Items.COAL));
        press.items().setStackInSlot(1, new ItemStack(PortItems.STAMP_IRON_WIRE.get()));
        for (Item input : List.of(Items.COPPER_INGOT, PortItems.INGOT_COPPER.get())) {
            press.items().setStackInSlot(2, new ItemStack(input));
            for (int tick = 0; tick < 300; tick++) PortPressBlockEntity.tick(level, pos, press.getBlockState(), press);
            require(press.items().getStackInSlot(2).isEmpty(), "Equivalent copper ingot did not process");
        }
        require(press.items().getStackInSlot(3).is(PortItems.WIRE_FINE_COPPER.get())
                && press.items().getStackInSlot(3).getCount() == 16
                && press.items().getStackInSlot(1).getDamageValue() == 2 && press.burnTime() == 1_200,
                "Two equivalent copper ingots must make sixteen wires for 400 stored fuel and two stamp uses");
        press.items().setStackInSlot(1, new ItemStack(PortItems.STAMP_IRON_PLATE.get()));
        press.items().setStackInSlot(2, new ItemStack(Items.COPPER_INGOT));
        for (int tick = 0; tick < 300; tick++) PortPressBlockEntity.tick(level, pos, press.getBlockState(), press);
        require(press.items().getStackInSlot(2).getCount() == 1 && press.burnTime() == 1_200
                && press.items().getStackInSlot(1).getDamageValue() == 0, "Blocked output consumed a tagged input");
        press.items().setStackInSlot(3, ItemStack.EMPTY);
        for (int tick = 0; tick < 300; tick++) PortPressBlockEntity.tick(level, pos, press.getBlockState(), press);
        require(press.items().getStackInSlot(3).is(PortItems.PLATE_COPPER.get())
                && press.items().getStackInSlot(3).getCount() == 1 && press.burnTime() == 1_000
                && press.items().getStackInSlot(1).getDamageValue() == 1, "Copper plate source accounting changed");
        if (ALIASES) {
            require(PortPressRecipes.output(new ItemStack(Items.ECHO_SHARD), new ItemStack(PortItems.STAMP_IRON_WIRE.get()))
                            .is(PortItems.WIRE_FINE_TUNGSTEN.get())
                    && PortPressRecipes.output(new ItemStack(Items.PRISMARINE_CRYSTALS), new ItemStack(PortItems.STAMP_IRON_WIRE.get()))
                            .is(PortItems.WIRE_FINE_ALUMINIUM.get())
                    && PortPressRecipes.output(new ItemStack(Items.PRISMARINE_SHARD), new ItemStack(PortItems.STAMP_IRON_PLATE.get()))
                            .is(PortItems.PLATE_STEEL.get()), "Press cached concrete items instead of live ingredient tags");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilConstructionConsumesMixedCopperWithoutPartialLoss(GameTestHelper helper) {
        var player = player(helper.getLevel(), "MixedConstruct");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.FURNACE));
        player.getInventory().setItem(1, new ItemStack(PortItems.PLATE_STEEL.get(), 16));
        player.getInventory().setItem(2, new ItemStack(PortItems.INGOT_COPPER.get(), 7));
        player.getInventory().setItem(3, new ItemStack(Items.COPPER_INGOT, 8));
        player.getInventory().setItem(4, new ItemStack(PortItems.INGOT_FIREBRICK.get(), 20));
        require(!PortAnvilConstruction.craft(player, 1, 3, false) && count(player, Items.COPPER_INGOT) == 8,
                "Low-tier construction consumed ingredients");
        require(PortAnvilConstruction.craft(player, 2, 3, false)
                && count(player, PortItems.HEATER_FIREBOX.get()) == 1
                && count(player, Items.COPPER_INGOT) == 7 && count(player, PortItems.INGOT_COPPER.get()) == 0,
                "Firebox did not consume exactly eight copper ingots across two item identities");
        require(!PortAnvilConstruction.craft(player, 2, 4, false)
                && count(player, Items.COPPER_INGOT) == 7 && count(player, PortItems.PLATE_STEEL.get()) == 8
                && count(player, PortItems.INGOT_FIREBRICK.get()) == 20, "Incomplete construction removed partial ingredients");
        player.getInventory().setItem(2, new ItemStack(PortItems.INGOT_COPPER.get()));
        if (ALIASES) player.getInventory().setItem(1, new ItemStack(Items.SCUTE, 8));
        require(PortAnvilConstruction.craft(player, 2, 4, false)
                && count(player, PortItems.MACHINE_CRUCIBLE.get()) == 1
                && count(player, Items.COPPER_INGOT) == 0 && count(player, PortItems.INGOT_COPPER.get()) == 0
                && count(player, PortItems.PLATE_STEEL.get()) == 0 && count(player, Items.SCUTE) == 0
                && count(player, PortItems.INGOT_FIREBRICK.get()) == 0, "Mixed crucible construction lost or duplicated material");
        player.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equivalentCopperFormsCastExactlyOnePlate(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var block = (PortCastingMachineBlock) PortBlocks.MACHINE_CRUCIBLE.get();
        var state = block.defaultBlockState().setValue(PortCastingMachineBlock.FACING, Direction.EAST);
        level.setBlock(pos, state, 3);
        block.setPlacedBy(level, pos, state, null, new ItemStack(block));
        var machine = (PortCrucibleBlockEntity) level.getBlockEntity(pos);
        for (int i = 0; i < 10; i++) {
            var saved = machine.saveWithFullMetadata();
            saved.putInt("heat", 100_000); saved.putInt("progress", 19_999);
            machine.load(saved);
            Item input = i == 0 ? Items.COPPER_INGOT : i == 1 ? PortItems.INGOT_COPPER.get() : PortItems.WIRE_FINE_COPPER.get();
            machine.items().setStackInSlot(1, new ItemStack(input));
            PortCrucibleBlockEntity.tick(level, pos, state, machine);
            require(machine.items().getStackInSlot(1).isEmpty(), "A source-equivalent copper form did not melt");
        }
        require(machine.totalMolten() == 216, "One vanilla ingot, one HBM ingot and eight wires must total 216 quanta");
        // With no alloy selected, source copper leaves the rear waste spout.
        BlockPos moldPos = pos.west(2).below();
        level.setBlock(moldPos, PortBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        var mold = (PortFoundryMoldBlockEntity) level.getBlockEntity(moldPos);
        mold.items().setStackInSlot(0, new ItemStack(PortItems.MOLD_PLATE_CAST.get()));
        for (int tick = 0; tick < 215; tick++) {
            PortCrucibleBlockEntity.tick(level, pos, state, machine);
            PortFoundryMoldBlockEntity.tick(level, moldPos, mold.getBlockState(), mold);
        }
        require(machine.totalMolten() == 0 && mold.amount() == 0
                && mold.items().getStackInSlot(1).is(PortItems.PLATE_CAST_COPPER.get())
                && mold.items().getStackInSlot(1).getCount() == 1
                && mold.items().getStackInSlot(0).is(PortItems.MOLD_PLATE_CAST.get()), "Mixed copper casting lost material or the reusable mold");
        require(PortMaterial.fromItem(new ItemStack(Items.COPPER_BLOCK)).equals(List.of(new PortMaterialStack(PortMaterial.COPPER, 648)))
                && PortMaterial.fromItem(new ItemStack(Items.IRON_BLOCK)).equals(List.of(new PortMaterialStack(PortMaterial.IRON, 648)))
                && PortMaterial.fromItem(new ItemStack(Items.RAW_COPPER)).isEmpty(), "Source block quantities or unsupported raw-ore classification changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void progressionRecipesKeepSourceTaggedShapes(GameTestHelper helper) {
        var level = helper.getLevel();
        Map<String, Map<String, Integer>> sourceTags = Map.of(
                "bolt_steel", Map.of("forge:ingots/steel", 2),
                "plate_cast_iron", Map.of("hbm:bolts/steel", 6, "forge:plates/iron", 3),
                "coil_tungsten", Map.of("hbm:fine_wires/tungsten", 8, "forge:ingots/iron", 1),
                "coil_tungsten_steel", Map.of("hbm:fine_wires/tungsten", 8, "forge:ingots/steel", 1),
                "battery_potato", Map.of("hbm:fine_wires/aluminum", 1, "hbm:fine_wires/copper", 1),
                "machine_electric_furnace", Map.of("forge:ingots/beryllium", 3, "hbm:cast_plates/copper", 2));
        for (var entry : sourceTags.entrySet()) {
            Map<String, Integer> actual = new HashMap<>();
            for (var ingredient : recipe(level, entry.getKey()).getIngredients()) {
                var json = ingredient.toJson();
                if (json.isJsonObject() && json.getAsJsonObject().has("tag")) {
                    actual.merge(json.getAsJsonObject().get("tag").getAsString(), 1, Integer::sum);
                }
            }
            require(actual.equals(entry.getValue()), "Source ore-dictionary ingredient shape/count changed: " + entry.getKey());
        }
        if (ALIASES) {
            var grid = new TransientCraftingContainer(player(level, "TagRecipes").inventoryMenu, 3, 3);
            grid.setItem(0, new ItemStack(Items.POTATO));
            grid.setItem(1, new ItemStack(Items.NETHER_STAR));
            grid.setItem(2, new ItemStack(Items.AMETHYST_SHARD));
            var potato = recipe(level, "battery_potato");
            require(potato.matches(grid, level) && ((PortBatteryItem) potato.assemble(grid, level.registryAccess()).getItem())
                    .charge(potato.assemble(grid, level.registryAccess())) == 1_000, "Alternate tagged wires did not make a charged potato battery");
            for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(i < 3 ? Items.HEART_OF_THE_SEA
                    : i == 3 || i == 5 ? Items.NAUTILUS_SHELL : i == 4 ? Items.FURNACE : PortItems.COIL_TUNGSTEN.get()));
            require(recipe(level, "machine_electric_furnace").matches(grid, level), "Furnace refused alternate tagged beryllium or cast copper");
            require(PortMaterial.fromItem(new ItemStack(Items.NAUTILUS_SHELL)).equals(List.of(new PortMaterialStack(PortMaterial.COPPER, 216)))
                    && PortMaterial.fromItem(new ItemStack(Items.AMETHYST_SHARD)).equals(List.of(new PortMaterialStack(PortMaterial.COPPER, 9)))
                    && PortMaterial.fromItem(new ItemStack(Items.SCUTE)).equals(List.of(new PortMaterialStack(PortMaterial.STEEL, 72))),
                    "Tag aliases did not retain their source shape quantities");
            var smith = player(level, "TagSmithing");
            smith.getInventory().clearContent();
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            level.setBlock(pos, PortBlocks.ANVIL_IRON.get().defaultBlockState(), 3);
            smith.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            var menu = new PortAnvilMenu(90, smith.getInventory(), pos, 1);
            menu.getSlot(0).set(new ItemStack(PortItems.ANVIL_IRON.get()));
            smith.getInventory().setItem(9, new ItemStack(Items.PRISMARINE_SHARD, 10));
            require(!menu.quickMoveStack(smith, 3).isEmpty() && menu.getSlot(1).getItem().getCount() == 10
                    && menu.quickMoveStack(smith, 2).is(PortItems.ANVIL_STEEL.get())
                    && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                    && count(smith, PortItems.ANVIL_STEEL.get()) == 1, "Tagged steel upgrade shift-click lost or duplicated inputs");
            smith.getInventory().clearContent();
            smith.getInventory().setItem(9, new ItemStack(Items.GHAST_TEAR));
            require(menu.quickMoveStack(smith, 3).is(Items.GHAST_TEAR), "Tagged cast-iron template was not routed to smithing input");
            menu.getSlot(1).set(new ItemStack(PortItems.MOLD_BASE.get(), 2));
            require(menu.quickMoveStack(smith, 2).is(PortItems.MOLD_PLATE_CAST.get())
                    && menu.getSlot(0).getItem().is(Items.GHAST_TEAR)
                    && menu.getSlot(1).getItem().getCount() == 1 && count(smith, PortItems.MOLD_PLATE_CAST.get()) == 1,
                    "Tagged mold template must be reusable while consuming exactly one mold base");
            menu.removed(smith);
            require(count(smith, Items.GHAST_TEAR) == 1 && count(smith, PortItems.MOLD_BASE.get()) == 1,
                    "Closing tagged smithing lost its template or remaining mold base");
            smith.getInventory().clearContent();
        }
        helper.succeed();
    }

    private static CraftingRecipe recipe(ServerLevel level, String path) {
        return (CraftingRecipe) level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("hbm", path)).orElseThrow();
    }
    private static Player player(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
    }
    private static int count(Player player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException(failure);
    }
}
