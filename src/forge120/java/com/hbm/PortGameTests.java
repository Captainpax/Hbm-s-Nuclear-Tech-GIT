package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.concurrent.ThreadLocalRandom;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortGameTests {
    private PortGameTests() {}

    @GameTest(template = "empty")
    public static void uraniumProgression(GameTestHelper helper) {
        var oreId = PortBlocks.ORE_URANIUM.getId();
        if (ForgeRegistries.BLOCKS.getValue(oreId) != PortBlocks.ORE_URANIUM.get()) {
            throw new IllegalStateException("Uranium ore is not registered");
        }
        helper.setBlock(new BlockPos(1, 1, 1), PortBlocks.ORE_URANIUM.get());
        helper.assertBlockPresent(PortBlocks.ORE_URANIUM.get(), new BlockPos(1, 1, 1));

        var input = new SimpleContainer(new ItemStack(PortItems.ORE_URANIUM.get()));
        var recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, input, helper.getLevel())
                .orElseThrow(() -> new IllegalStateException("Uranium smelting recipe is missing"));
        if (!recipe.getResultItem(helper.getLevel().registryAccess()).is(PortItems.INGOT_URANIUM.get())) {
            throw new IllegalStateException("Uranium smelting result is wrong");
        }
        if (recipe.getExperience() != 6.0F || recipe.getCookingTime() != 200) {
            throw new IllegalStateException("Uranium smelting experience or time differs from the port spec");
        }
        if (!helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(oreId)) {
            throw new IllegalStateException("Uranium ore placed feature is missing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void uraniumOutgasses(GameTestHelper helper) {
        BlockPos orePos = new BlockPos(1, 2, 1);
        helper.setBlock(orePos, PortBlocks.ORE_URANIUM.get());
        var state = helper.getBlockState(orePos);
        for (int i = 0; i < 32; i++) {
            PortBlocks.ORE_URANIUM.get().randomTick(state, helper.getLevel(),
                    helper.absolutePos(orePos), RandomSource.create(i));
        }
        boolean found = false;
        for (Direction direction : Direction.values()) {
            if (helper.getLevel().getBlockState(helper.absolutePos(orePos.relative(direction)))
                    .is(PortBlocks.GAS_RADON.get())) {
                found = true;
            }
        }
        if (!found) throw new IllegalStateException("Uranium ore did not emit radon");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radonMovesDown(GameTestHelper helper) {
        BlockPos gasPos = new BlockPos(1, 2, 1);
        helper.setBlock(gasPos.below(), Blocks.AIR);
        helper.setBlock(gasPos, PortBlocks.GAS_RADON.get());
        long seed = 0;
        while (true) {
            RandomSource candidate = RandomSource.create(seed);
            if (candidate.nextInt(50) != 0 && candidate.nextInt(5) != 0) break;
            seed++;
        }
        PortBlocks.GAS_RADON.get().tick(helper.getBlockState(gasPos), helper.getLevel(),
                helper.absolutePos(gasPos), RandomSource.create(seed));
        helper.assertBlockPresent(PortBlocks.GAS_RADON.get(), gasPos.below());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void radonExposurePersists(GameTestHelper helper) {
        BlockPos gasPos = new BlockPos(1, 2, 1);
        helper.setBlock(gasPos, PortBlocks.GAS_RADON.get());
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, gasPos);
        PortBlocks.GAS_RADON.get().entityInside(helper.getBlockState(gasPos), helper.getLevel(),
                helper.absolutePos(gasPos), cow);
        var exposure = PortRadiation.get(cow)
                .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));
        if (Math.abs(exposure.radiation() - 0.05F) > 0.0001F || exposure.asbestos() != 1) {
            throw new IllegalStateException("Radon did not apply its legacy exposure amounts");
        }
        CompoundTag saved = cow.saveWithoutId(new CompoundTag());
        var restored = EntityType.COW.create(helper.getLevel());
        if (restored == null) throw new IllegalStateException("Could not create a cow for NBT reload");
        restored.load(saved);
        var reloaded = PortRadiation.get(restored)
                .orElseThrow(() -> new IllegalStateException("Reloaded radiation capability missing"));
        if (Math.abs(reloaded.radiation() - 0.05F) > 0.0001F || reloaded.asbestos() != 1) {
            throw new IllegalStateException("Exposure did not survive entity NBT reload");
        }
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, gasPos);
        PortBlocks.GAS_RADON.get().entityInside(helper.getBlockState(gasPos), helper.getLevel(),
                helper.absolutePos(gasPos), zombie);
        var immuneExposure = PortRadiation.get(zombie)
                .orElseThrow(() -> new IllegalStateException("Zombie radiation capability missing"));
        if (immuneExposure.radiation() != 0.0F) {
            throw new IllegalStateException("A legacy radiation-immune mob received a dose");
        }
        immuneExposure.addRadiation(1000.0F);
        PortRadiationEffects.apply(zombie, immuneExposure);
        if (zombie.getHealth() <= 0.0F) {
            throw new IllegalStateException("A legacy radiation-immune mob got radiation sickness");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filteredMaskBlocksRadonAndWearsOut(GameTestHelper helper) {
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(1, 2, 1));
        ItemStack mask = new ItemStack(PortItems.GAS_MASK.get());
        ItemStack filter = new ItemStack(PortItems.GAS_MASK_FILTER.get());
        filter.setDamageValue(filter.getMaxDamage() - 1);
        PortGasMask.installFilter(mask, filter);
        cow.setItemSlot(EquipmentSlot.HEAD, mask);
        var exposure = PortRadiation.get(cow)
                .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));

        PortRadiation.exposeRadon(cow);
        if (exposure.radiation() != 0.0F || exposure.asbestos() != 0
                || PortGasMask.filter(mask).getDamageValue() != filter.getMaxDamage()) {
            throw new IllegalStateException("Filter failed to protect or consume one durability point");
        }
        PortRadiation.exposeRadon(cow);
        if (exposure.radiation() != 0.0F || !PortGasMask.filter(mask).isEmpty()) {
            throw new IllegalStateException("Final filter use should protect and remove spent filter");
        }
        PortRadiation.exposeRadon(cow);
        if (Math.abs(exposure.radiation() - 0.05F) > 0.0001F || exposure.asbestos() != 1) {
            throw new IllegalStateException("Unfiltered radon did not expose the entity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gasMaskFilterInstallsAndSwaps(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack mask = new ItemStack(PortItems.GAS_MASK.get());
        player.setItemSlot(EquipmentSlot.HEAD, mask);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PortItems.GAS_MASK_FILTER.get()));
        PortItems.GAS_MASK_FILTER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                || !PortGasMask.filter(mask).is(PortItems.GAS_MASK_FILTER.get())) {
            throw new IllegalStateException("Right-click did not install the filter in the worn mask");
        }

        ItemStack replacement = new ItemStack(PortItems.GAS_MASK_FILTER.get());
        replacement.setDamageValue(10);
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        PortItems.GAS_MASK_FILTER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(PortItems.GAS_MASK_FILTER.get())
                || player.getItemInHand(InteractionHand.MAIN_HAND).getDamageValue() != 0
                || PortGasMask.filter(mask).getDamageValue() != 10) {
            throw new IllegalStateException("Replacing a filter did not return the old filter");
        }
        ItemStack reloaded = ItemStack.of(mask.save(new CompoundTag()));
        if (PortGasMask.filter(reloaded).getDamageValue() != 10) {
            throw new IllegalStateException("Installed filter did not persist in mask NBT");
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allGasMaskFilterVariantsFollowFineParticleRule(GameTestHelper helper) {
        Item[] masks = {
                PortItems.GAS_MASK.get(), PortItems.GAS_MASK_M65.get(),
                PortItems.GAS_MASK_MONO.get(), PortItems.GAS_MASK_OLDE.get()
        };
        Item[] filters = {
                PortItems.GAS_MASK_FILTER.get(), PortItems.GAS_MASK_FILTER_MONO.get(),
                PortItems.GAS_MASK_FILTER_COMBO.get(), PortItems.GAS_MASK_FILTER_RAG.get(),
                PortItems.GAS_MASK_FILTER_PISS.get()
        };
        for (Item maskItem : masks) {
            for (Item filterItem : filters) {
                var cow = EntityType.COW.create(helper.getLevel());
                if (cow == null) throw new IllegalStateException("Could not create exposure test cow");
                ItemStack mask = new ItemStack(maskItem);
                PortGasMask.installFilter(mask, new ItemStack(filterItem));
                cow.setItemSlot(EquipmentSlot.HEAD, mask);
                PortRadiation.exposeRadon(cow);
                var exposure = PortRadiation.get(cow)
                        .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));
                boolean fineFilter = filterItem == PortItems.GAS_MASK_FILTER.get()
                        || filterItem == PortItems.GAS_MASK_FILTER_COMBO.get();
                if (fineFilter) {
                    if (exposure.radiation() != 0.0F || exposure.asbestos() != 0
                            || PortGasMask.filter(mask).getDamageValue() != 1) {
                        throw new IllegalStateException("Fine-particle filter failed in " + maskItem);
                    }
                } else if (Math.abs(exposure.radiation() - 0.05F) > 0.0001F
                        || exposure.asbestos() != 1 || PortGasMask.filter(mask).getDamageValue() != 0) {
                    throw new IllegalStateException("Non-fine filter incorrectly protected in " + maskItem);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fatalRadiationBypassesArmor(GameTestHelper helper) {
        var damageKey = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(HbmPort.MOD_ID, "radiation"));
        if (!helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(damageKey).is(DamageTypeTags.BYPASSES_ARMOR)) {
            throw new IllegalStateException("Radiation damage does not bypass armor");
        }
        var pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
        var exposure = PortRadiation.get(pig)
                .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));
        exposure.addRadiation(1000.0F);
        PortRadiationEffects.apply(pig, exposure);
        if (pig.getHealth() > 0.0F || exposure.radiation() != 0.0F) {
            throw new IllegalStateException("Lethal radiation did not kill and clear the dose");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void earlyRadiationIllness(GameTestHelper helper) {
        var pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
        var exposure = PortRadiation.get(pig)
                .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));
        exposure.addRadiation(200.0F);
        long seed = 0;
        while (RandomSource.create(seed).nextInt(300) != 0) seed++;
        PortRadiationEffects.apply(pig, exposure, RandomSource.create(seed));
        if (!pig.hasEffect(MobEffects.CONFUSION)) {
            throw new IllegalStateException("200 RAD did not trigger the nausea tier");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void uraniumGeneratesInFreshChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        // The GameTest world survives local reruns. Select distant coordinates so
        // a passing repeat run does not silently inspect previously made chunks.
        int baseChunkX = (origin.getX() >> 4) + ThreadLocalRandom.current().nextInt(256, 8192);
        int baseChunkZ = (origin.getZ() >> 4) + ThreadLocalRandom.current().nextInt(256, 8192);
        int oreCount = 0;
        for (int chunkX = baseChunkX; chunkX < baseChunkX + 2; chunkX++) {
            for (int chunkZ = baseChunkZ; chunkZ < baseChunkZ + 2; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
                for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                    for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                        for (int y = 5; y <= 20; y++) {
                            if (level.getBlockState(new BlockPos(x, y, z)).is(PortBlocks.ORE_URANIUM.get())) {
                                oreCount++;
                            }
                        }
                    }
                }
            }
        }
        if (oreCount == 0) {
            throw new IllegalStateException("No uranium ore generated in four new overworld chunks");
        }
        helper.succeed();
    }
}
