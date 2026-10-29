package com.hbm;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortCrucibleGameTests {
    private static final String STEEL = "hbm:crucible/steel";
    private PortCrucibleGameTests() {}

    @GameTest(template = "empty")
    public static void steelRecipeAndFluxCraftMatchFrozenSource(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = PortCrucibleRecipes.find(level, STEEL);
        require(recipe != null && recipe.frequency() == 20
                && recipe.input().equals(Map.of(PortMaterial.IRON, 16, PortMaterial.CARBON, 24, PortMaterial.FLUX, 8))
                && recipe.output().equals(Map.of(PortMaterial.STEEL, 16)), "Source steel alloy ratios changed");
        var serializer = new PortCrucibleRecipe.Serializer();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var decoded = serializer.fromNetwork(recipe.getId(), buffer);
            require(decoded.input().equals(recipe.input()) && decoded.output().equals(recipe.output())
                    && decoded.frequency() == 20 && decoded.getResultItem(level.registryAccess()).is(PortItems.INGOT_STEEL.get())
                    && buffer.readableBytes() == 0, "Crucible recipe network round trip changed data");
        } finally { buffer.release(); }
        String json = "{\"frequency\":20,\"input\":[{\"material\":\"iron\",\"amount\":16}],"
                + "\"output\":[{\"material\":\"steel\",\"amount\":16}],\"icon\":{\"item\":\"hbm:ingot_steel\"}}";
        require(serializer.fromJson(recipe.getId(), JsonParser.parseString(json).getAsJsonObject()).inputAmount() == 16,
                "Crucible JSON serializer rejected a valid material recipe");
        for (String invalid : List.of(json.replace("\"amount\":16}],\"output\"", "\"amount\":0}],\"output\""),
                json.replace("\"iron\"", "\"unknown\""), json.replace("\"steel\",\"amount\":16", "\"steel\",\"amount\":17"),
                json.replace("\"input\":[", "\"input\":[{\"material\":\"iron\",\"amount\":1},"))) {
            boolean rejected = false;
            try { serializer.fromJson(recipe.getId(), JsonParser.parseString(invalid).getAsJsonObject()); }
            catch (RuntimeException expected) { rejected = true; }
            require(rejected, "Crucible serializer accepted invalid/duplicate/expanding materials");
        }
        var player = FakePlayerFactory.getMinecraft(level);
        var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        grid.setItem(0, new ItemStack(Items.CHARCOAL));
        grid.setItem(8, new ItemStack(Items.RED_SAND));
        var fluxId = ResourceLocation.parse("hbm:powder_flux");
        var flux = (ShapelessRecipe) level.getRecipeManager().byKey(fluxId).orElseThrow();
        require(flux.matches(grid, level) && flux.assemble(grid, level.registryAccess()).is(PortItems.POWDER_FLUX.get())
                && flux.assemble(grid, level.registryAccess()).getCount() == 1
                && level.getServer().getAdvancements().getAdvancement(ResourceLocation.parse("hbm:recipes/misc/powder_flux")) != null,
                "Charcoal plus sand flux route or its recipe unlock is missing");
        require(PortMaterial.fromItem(new ItemStack(Items.COAL)).equals(List.of(new PortMaterialStack(PortMaterial.CARBON, 36)))
                && PortMaterial.fromItem(new ItemStack(Items.CHARCOAL)).equals(List.of(new PortMaterialStack(PortMaterial.CARBON, 24)))
                && PortMaterial.fromItem(new ItemStack(PortItems.STONE_RESOURCE_LIMESTONE.get()))
                        .equals(List.of(new PortMaterialStack(PortMaterial.FLUX, 720))), "Source carbon/limestone conversion changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleStoresHaveIndependentCapacitiesAndRatios(GameTestHelper helper) {
        var machine = crucible(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), Direction.NORTH);
        require(PortCrucibleBlockEntity.STORE_CAPACITY == 10_368, "Source store capacity must be sixteen blocks each");
        require(machine.acceptPour(PortMaterial.COPPER, Integer.MAX_VALUE) == 10_368, "Waste store capacity is wrong");
        require(machine.selectRecipe(STEEL) && !machine.canSmelt(new ItemStack(PortItems.INGOT_COPPER.get())),
                "Selected steel recipe accepted an unrelated pure copper item");
        require(machine.acceptPour(PortMaterial.IRON, Integer.MAX_VALUE) == 3_456
                && machine.acceptPour(PortMaterial.CARBON, Integer.MAX_VALUE) == 5_184
                && machine.acceptPour(PortMaterial.FLUX, Integer.MAX_VALUE) == 1_728
                && machine.totalMolten() == 20_736, "Independent store capacity or input ratios overflowed");
        require(machine.acceptPour(PortMaterial.IRON, 1) == 0 && machine.acceptPour(PortMaterial.STEEL, 1) == 0
                && !machine.canSmelt(new ItemStack(PortItems.INGOT_STEEL.get())), "Full recipe store accepted more material");
        machine.recipeStore().takeScraps();
        machine.recipeStore().add(PortMaterial.IRON, 3_450);
        require(machine.canSmelt(PortScrapsItem.of(PortMaterial.IRON, 6))
                && !machine.canSmelt(PortScrapsItem.of(PortMaterial.IRON, 7))
                && machine.canSmelt(PortScrapsItem.of(PortMaterial.STEEL, 6_918)), "Input ratio or output remelting limit is wrong");
        require(machine.selectRecipe("") && machine.recipeStore().amount(PortMaterial.IRON) == 3_450
                && machine.wasteStore().amount(PortMaterial.COPPER) == 10_368, "Changing selection discarded material");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceInputsMeltAlloyAndCastSteelWithoutMaterialLoss(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var machine = crucible(level, pos, Direction.EAST);
        machine.selectRecipe(STEEL);
        // Source inputs: four iron ingots, eighteen charcoal and two powders -> four steel ingots.
        for (int i = 0; i < 24; i++) {
            ItemStack input = i < 4 ? new ItemStack(Items.IRON_INGOT)
                    : i < 22 ? new ItemStack(Items.CHARCOAL) : new ItemStack(PortItems.POWDER_FLUX.get());
            var saved = machine.saveWithFullMetadata();
            saved.putInt("heat", 100_000);
            saved.putInt("progress", 19_999);
            machine.load(saved);
            machine.items().setStackInSlot(1, input);
            tick(level, machine);
            require(machine.items().getStackInSlot(1).isEmpty(), "Supported alloy input did not melt");
        }
        var recipe = machine.loadedRecipe();
        while (recipe.apply(machine.recipeStore(), 20)) {}
        require(machine.recipeStore().total() == 288 && machine.recipeStore().amount(PortMaterial.STEEL) == 288
                && machine.wasteStore().total() == 0, "Steel alloy operation lost/duplicated metal or additives");
        BlockPos moldPos = pos.east(2).below();
        var mold = mold(level, moldPos);
        for (int i = 0; i < 210; i++) {
            tick(level, machine);
            PortFoundryMoldBlockEntity.tick(level, moldPos, mold.getBlockState(), mold);
        }
        require(mold.items().getStackInSlot(1).is(PortItems.PLATE_CAST_STEEL.get())
                && mold.items().getStackInSlot(0).is(PortItems.MOLD_PLATE_CAST.get())
                && machine.recipeStore().amount(PortMaterial.STEEL) == 72 && mold.amount() == 0,
                "Steel casting did not consume exactly three ingots and preserve the reusable mold");
        require(mold.acceptMaterial(PortMaterial.IRON, 1) == 0, "Finished output did not block another pour");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothSpoutsRespectFacingObstructionsAndMaterialTypes(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 8, 3));
        for (Direction front : Direction.Plane.HORIZONTAL) {
            var machine = crucible(level, pos, front);
            machine.selectRecipe(STEEL);
            machine.recipeStore().add(PortMaterial.IRON, 17);
            machine.recipeStore().add(PortMaterial.STEEL, 216);
            machine.wasteStore().add(PortMaterial.COPPER, 145);
            BlockPos frontPos = pos.relative(front, 2).below(5);
            BlockPos rearPos = pos.relative(front.getOpposite(), 2).below();
            var alloy = mold(level, frontPos);
            var copper = mold(level, rearPos);
            BlockPos obstruction = frontPos.above(2);
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            tick(level, machine);
            require(alloy.amount() == 0 && copper.amount() == 24 && machine.recipeStore().amount(PortMaterial.STEEL) == 216,
                    "Blocked front spout lost alloy or stopped the independent rear spout");
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
            tick(level, machine);
            require(alloy.material() == PortMaterial.STEEL && alloy.amount() == 24 && copper.amount() == 48
                    && machine.recipeStore().amount(PortMaterial.IRON) == 17, "Spout direction, depth, output filtering or 24-quanta rate changed");
            require(alloy.acceptMaterial(PortMaterial.COPPER, 1) == 0 && alloy.acceptMaterial(PortMaterial.CARBON, 1) == 0,
                    "Foundry accepted a different metal or an additive into its current casting");
            machine.selectRecipe("");
            alloy.takeScraps();
            tick(level, machine);
            require(alloy.material() == PortMaterial.IRON && alloy.amount() == 17,
                    "Clearing selection did not release retained recipe inputs through the front spout");
            level.setBlock(frontPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(rearPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        var upper = crucible(level, pos, Direction.EAST);
        var lower = crucible(level, pos.west(2).below(2), Direction.NORTH);
        lower.selectRecipe(STEEL);
        lower.recipeStore().add(PortMaterial.IRON, 3_450);
        upper.wasteStore().add(PortMaterial.IRON, 24);
        tick(level, upper);
        require(lower.recipeStore().amount(PortMaterial.IRON) == 3_456 && upper.wasteStore().amount(PortMaterial.IRON) == 18,
                "Crucible acceptor did not subtract only the partial ratio-limited transfer");
        tick(level, upper);
        require(upper.wasteStore().amount(PortMaterial.IRON) == 18, "Full receiving crucible consumed rejected material");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void safeSpoutDoesNotLoadAnAbsentNeighborChunk(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(160_015, 100, 160_000);
        level.getChunkAt(pos);
        BlockPos target = pos.east(2);
        require(!level.hasChunkAt(target), "Unloaded-spout fixture unexpectedly has a loaded neighboring chunk");
        // Bypass neighbor notifications while constructing this isolated fixture: those
        // notifications themselves can load the adjacent chunk before a spout runs.
        level.setBlock(pos, PortBlocks.MACHINE_CRUCIBLE.get().defaultBlockState()
                .setValue(PortCastingMachineBlock.FACING, Direction.WEST), 2 | 16);
        var machine = (PortCrucibleBlockEntity) level.getBlockEntity(pos);
        require(!level.hasChunkAt(target), "Fixture placement loaded the neighboring chunk before the spout ran");
        // This fixture is outside the template and may remain after a failed prior run.
        machine.load(new CompoundTag());
        machine.wasteStore().add(PortMaterial.COPPER, 216);
        tick(level, machine);
        require(!level.hasChunkAt(target) && machine.wasteStore().amount(PortMaterial.COPPER) == 216,
                "Safe spout loaded target=" + level.hasChunkAt(target) + "; copper=" + machine.wasteStore().amount(PortMaterial.COPPER));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void typedStoresMigrateSaveAndRecoverEveryMaterialExactly(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var machine = crucible(level, pos, Direction.NORTH);
        var legacy = machine.saveWithFullMetadata();
        legacy.remove("wasteMaterials");
        legacy.putInt("moltenCopper", 145);
        machine.load(legacy);
        machine.selectRecipe(STEEL);
        machine.recipeStore().add(PortMaterial.STEEL, 73);
        machine.recipeStore().add(PortMaterial.IRON, 17);
        machine.recipeStore().add(PortMaterial.CARBON, 11);
        machine.recipeStore().add(PortMaterial.FLUX, 7);
        var saved = machine.saveWithFullMetadata();
        saved.putInt("moltenCopper", 999);
        var copy = new PortCrucibleBlockEntity(pos, machine.getBlockState());
        copy.load(saved);
        require(copy.totalMolten() == 253 && copy.recipeStore().contents().equals(machine.recipeStore().contents())
                && copy.wasteStore().amount(PortMaterial.COPPER) == 145 && copy.selectedRecipe().equals(STEEL),
                "Store save/reload reordered materials, lost selection or counted the old copper alias twice");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        for (var material : PortMaterial.values()) {
            int expected = material == PortMaterial.COPPER ? 145 : material == PortMaterial.STEEL ? 73
                    : material == PortMaterial.IRON ? 17 : material == PortMaterial.CARBON ? 11 : 7;
            long actual = drops.stream().filter(e -> e.getItem().getItem() == material.scrapItem())
                    .mapToLong(e -> (long) e.getItem().getCount() * PortScrapsItem.quanta(e.getItem())).sum();
            require(actual == expected, "Crucible teardown lost/duplicated " + material);
        }
        require(machine.takeScraps().isEmpty(), "Teardown left recoverable duplicated material");
        var mold = new PortFoundryMoldBlockEntity(pos, PortBlocks.FOUNDRY_MOLD.get().defaultBlockState());
        CompoundTag oldMold = new CompoundTag();
        oldMold.putInt("copperQuanta", 71);
        oldMold.putInt("cooloff", 93);
        mold.load(oldMold);
        require(mold.amount() == 71 && mold.material() == PortMaterial.COPPER && mold.cooloff() == 93,
                "Old development foundry save did not migrate");
        var reloaded = mold.saveWithFullMetadata();
        reloaded.putInt("copperQuanta", 999);
        mold.load(reloaded);
        require(PortScrapsItem.quanta(mold.takeScraps()) == 71 && mold.takeScraps().isEmpty(),
                "New foundry save duplicated legacy copper or repeated recovery");
        var bounded = new PortMaterialStore(10_368);
        var invalid = new ListTag();
        CompoundTag unknown = new CompoundTag();
        unknown.putString("material", "unknown"); unknown.putInt("amount", 1); invalid.add(unknown);
        CompoundTag overflow = new CompoundTag();
        overflow.putString("material", "iron"); overflow.putInt("amount", Integer.MAX_VALUE); invalid.add(overflow);
        bounded.load(invalid);
        require(bounded.total() == 10_368 && bounded.amount(PortMaterial.IRON) == 10_368, "Corrupt material NBT overflowed capacity");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleMenuSyncAndRenderPacketsPreserveAuthority(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var machine = crucible(level, pos, Direction.NORTH);
        var player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var menu = new PortCrucibleMenu(71, player.getInventory(), machine);
        require(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, 999) && menu.clickMenuButton(player, 1)
                && machine.selectedRecipe().equals(STEEL), "Recipe selector did not validate server button range");
        var saved = machine.saveWithFullMetadata();
        saved.putInt("heat", 100_000); saved.putInt("progress", 19_999); machine.load(saved);
        machine.recipeStore().add(PortMaterial.STEEL, 73);
        machine.recipeStore().add(PortMaterial.IRON, 17);
        machine.wasteStore().add(PortMaterial.COPPER, 145);
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.writeBlockPos(pos);
            var client = new PortCrucibleMenu(71, player.getInventory(), packet);
            for (int i = 0; i < machine.menuData().getCount(); i++) client.setData(i, (short) machine.menuData().get(i));
            require(client.heat() == 100_000 && client.progress() == 19_999 && client.selectedRecipe() != null
                    && client.amount(true, PortMaterial.STEEL) == 73 && client.amount(false, PortMaterial.COPPER) == 145
                    && client.materials(true).equals(List.of(PortMaterial.STEEL, PortMaterial.IRON)), "Menu short transport lost heat/materials/order");
        } finally { packet.release(); }
        var visual = machine.getUpdateTag();
        require(visual.getAllKeys().equals(java.util.Set.of("renderMass")) && visual.getInt("renderMass") == 235,
                "Crucible visual packet leaked inventory or omitted total mass");
        machine.items().setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
        visual.putInt("renderMass", Integer.MAX_VALUE); machine.handleUpdateTag(visual);
        require(machine.renderMass() == 20_736 && machine.recipeStore().amount(PortMaterial.STEEL) == 73
                && machine.items().getStackInSlot(1).is(Items.IRON_INGOT), "Visual packet replaced authoritative material/inventory");
        player.moveTo(pos.getX() + 100, pos.getY(), pos.getZ());
        require(!menu.clickMenuButton(player, 0) && machine.selectedRecipe().equals(STEEL), "Distant player changed recipe selection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedStoresAndSteelCastingSurviveDedicatedRestart(GameTestHelper helper) {
        String phase = System.getProperty("hbm.port.persistence.phase", "");
        if (phase.isEmpty()) { helper.succeed(); return; }
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(783, 90, 576); // Front spout crosses the x=784 chunk boundary.
        BlockPos front = pos.east(2).below(), rear = pos.west(2).below();
        level.getChunkAt(pos); level.getChunkAt(front); level.getChunkAt(rear);
        if (phase.equals("seed")) {
            level.setBlock(front, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(rear, Blocks.AIR.defaultBlockState(), 3);
            var machine = crucible(level, pos, Direction.EAST);
            machine.selectRecipe(STEEL);
            machine.recipeStore().add(PortMaterial.STEEL, 216);
            machine.wasteStore().add(PortMaterial.COPPER, 145);
            machine.wasteStore().add(PortMaterial.IRON, 17);
            machine.items().setStackInSlot(1, new ItemStack(Items.CHARCOAL));
        } else if (phase.equals("verify")) {
            require(level.getBlockEntity(pos) instanceof PortCrucibleBlockEntity, "Mixed-store fixture missing after restart");
            var machine = (PortCrucibleBlockEntity) level.getBlockEntity(pos);
            require(machine.selectedRecipe().equals(STEEL) && machine.recipeStore().amount(PortMaterial.STEEL) == 216
                    && machine.wasteStore().contents().equals(List.of(new PortMaterialStack(PortMaterial.COPPER, 145),
                            new PortMaterialStack(PortMaterial.IRON, 17))) && machine.items().getStackInSlot(1).is(Items.CHARCOAL),
                    "Restart changed recipe selection, buffered input, material order or quantity");
            var alloy = mold(level, front); var copper = mold(level, rear);
            for (int i = 0; i < 210; i++) {
                tick(level, machine);
                PortFoundryMoldBlockEntity.tick(level, front, alloy.getBlockState(), alloy);
            }
            require(alloy.items().getStackInSlot(1).is(PortItems.PLATE_CAST_STEEL.get()) && alloy.amount() == 0
                    && copper.material() == PortMaterial.COPPER && copper.amount() == 145
                    && machine.recipeStore().total() == 0 && machine.wasteStore().amount(PortMaterial.IRON) == 17,
                    "Reloaded dual spouts lost/duplicated typed material across chunk boundary");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(front, Blocks.AIR.defaultBlockState(), 3); level.setBlock(rear, Blocks.AIR.defaultBlockState(), 3);
        } else throw new IllegalStateException("Unknown restart phase " + phase);
        helper.succeed();
    }

    private static PortCrucibleBlockEntity crucible(ServerLevel level, BlockPos pos, Direction front) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, PortBlocks.MACHINE_CRUCIBLE.get().defaultBlockState().setValue(PortCastingMachineBlock.FACING, front), 3);
        return (PortCrucibleBlockEntity) level.getBlockEntity(pos);
    }
    private static PortFoundryMoldBlockEntity mold(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, PortBlocks.FOUNDRY_MOLD.get().defaultBlockState(), 3);
        var mold = (PortFoundryMoldBlockEntity) level.getBlockEntity(pos);
        mold.items().setStackInSlot(0, new ItemStack(PortItems.MOLD_PLATE_CAST.get()));
        return mold;
    }
    private static void tick(ServerLevel level, PortCrucibleBlockEntity machine) {
        PortCrucibleBlockEntity.tick(level, machine.getBlockPos(), machine.getBlockState(), machine);
    }
    private static void require(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException(failure);
    }
}
