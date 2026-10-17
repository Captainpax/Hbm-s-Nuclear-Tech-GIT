package com.hbm;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;

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
    public static void ordinaryOreSmeltingAndRegistration(GameTestHelper helper) {
        Block[] ores = {
                PortBlocks.ORE_THORIUM.get(), PortBlocks.ORE_TITANIUM.get(), PortBlocks.ORE_COPPER.get(),
                PortBlocks.ORE_TUNGSTEN.get(), PortBlocks.ORE_LEAD.get(), PortBlocks.ORE_BERYLLIUM.get()
        };
        Item[] oreItems = {
                PortItems.ORE_THORIUM.get(), PortItems.ORE_TITANIUM.get(), PortItems.ORE_COPPER.get(),
                PortItems.ORE_TUNGSTEN.get(), PortItems.ORE_LEAD.get(), PortItems.ORE_BERYLLIUM.get()
        };
        Item[] ingots = {
                PortItems.INGOT_TH232.get(), PortItems.INGOT_TITANIUM.get(), PortItems.INGOT_COPPER.get(),
                PortItems.INGOT_TUNGSTEN.get(), PortItems.INGOT_LEAD.get(), PortItems.INGOT_BERYLLIUM.get()
        };
        float[] experience = {3.0F, 3.0F, 2.5F, 6.0F, 3.0F, 2.0F};
        BlockPos orePos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int i = 0; i < ores.length; i++) {
            var id = ForgeRegistries.BLOCKS.getKey(ores[i]);
            if (id == null || ForgeRegistries.ITEMS.getValue(id) != oreItems[i]
                    || !helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(id)) {
                throw new IllegalStateException("Ore item or placed feature missing for " + id);
            }
            var input = new SimpleContainer(new ItemStack(oreItems[i]));
            var recipe = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, input, helper.getLevel())
                    .orElseThrow(() -> new IllegalStateException("Ore smelting recipe missing for " + id));
            if (!recipe.getResultItem(helper.getLevel().registryAccess()).is(ingots[i])
                    || recipe.getExperience() != experience[i] || recipe.getCookingTime() != 200) {
                throw new IllegalStateException("Ore smelting differs from legacy for " + id);
            }
            helper.getLevel().setBlock(orePos, ores[i].defaultBlockState(), 3);
            var drops = Block.getDrops(ores[i].defaultBlockState(), helper.getLevel(), orePos,
                    null, FakePlayerFactory.getMinecraft(helper.getLevel()), new ItemStack(Items.IRON_PICKAXE));
            if (drops.size() != 1 || !drops.get(0).is(oreItems[i]) || drops.get(0).getCount() != 1) {
                throw new IllegalStateException("Ore self-drop differs from legacy for " + id);
            }
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
        exposure.rollEnvironmentalRadiation();
        if (Math.abs(exposure.environmentRate() - 0.05F) > 0.0001F) {
            throw new IllegalStateException("Radon did not contribute to environmental Geiger reading");
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
    public static void exposurePacketRoundTripAndValidation(GameTestHelper helper) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new PortNetwork.ExposureSync(234.5F, 73).encode(buffer);
            var packet = PortNetwork.ExposureSync.decode(buffer);
            var cow = EntityType.COW.create(helper.getLevel());
            if (cow == null) throw new IllegalStateException("Could not create packet test entity");
            var data = PortRadiation.get(cow)
                    .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));
            packet.applyTo(data);
            if (data.radiation() != 234.5F || data.asbestos() != 73) {
                throw new IllegalStateException("Exposure packet round-trip lost data");
            }
        } finally {
            buffer.release();
        }
        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        try {
            invalid.writeFloat(Float.NaN);
            invalid.writeVarInt(Integer.MAX_VALUE);
            var packet = PortNetwork.ExposureSync.decode(invalid);
            if (packet.radiation() != 0.0F || packet.asbestos() != 60 * 60 * 20) {
                throw new IllegalStateException("Exposure packet accepted invalid values");
            }
        } finally {
            invalid.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chunkRadiationPersistsAndDiffuses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long center = ChunkPos.asLong(chunkX, chunkZ);
        long east = ChunkPos.asLong(chunkX + 1, chunkZ);
        PortChunkRadiation.set(level, pos, 100.0F);
        if (PortChunkRadiation.at(level, pos) != 100.0F) {
            throw new IllegalStateException("Chunk radiation was not set in a loaded chunk");
        }
        PortChunkRadiation copy = PortChunkRadiation.load(
                PortChunkRadiation.get(level).save(new CompoundTag()));
        if (copy.at(center) != 100.0F) {
            throw new IllegalStateException("Chunk radiation did not survive NBT round-trip");
        }
        copy.spread(Set.of(center, east));
        if (Math.abs(copy.at(center) - 59.35F) > 0.001F
                || Math.abs(copy.at(east) - 7.375F) > 0.001F) {
            throw new IllegalStateException("Chunk radiation diffusion differs from scalar weights");
        }
        int distantX = chunkX + 8000;
        int distantZ = chunkZ + 8000;
        if (level.getChunkSource().hasChunk(distantX, distantZ)) {
            throw new IllegalStateException("Expected test chunk to be unloaded");
        }
        PortChunkRadiation.set(level, new BlockPos(distantX << 4, pos.getY(), distantZ << 4), 100.0F);
        if (level.getChunkSource().hasChunk(distantX, distantZ)) {
            throw new IllegalStateException("Setting radiation generated an unloaded chunk");
        }
        PortChunkRadiation.set(level, pos, 0.0F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chunkRadiationSurvivesServerRestart(GameTestHelper helper) {
        String phase = System.getProperty("hbm.port.persistence.phase", "");
        if (phase.isEmpty()) {
            helper.succeed();
            return;
        }
        System.out.println("[HBM GameTest] chunk radiation persistence phase: " + phase);
        // A distant, never-loaded chunk prevents diffusion from changing the
        // fixture between the seed and verify processes.
        long fixture = ChunkPos.asLong(9000, 9000);
        PortChunkRadiation saved = PortChunkRadiation.get(helper.getLevel());
        if (phase.equals("seed")) {
            saved.set(fixture, 123.5F);
        } else if (phase.equals("verify")) {
            if (saved.at(fixture) != 123.5F) {
                throw new IllegalStateException("Chunk radiation did not survive a server restart");
            }
            saved.set(fixture, 0.0F);
        } else {
            throw new IllegalStateException("Unknown persistence test phase: " + phase);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chunkRadiationExposesLivingEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        PortChunkRadiation.set(level, pos, 20.0F);
        var cow = EntityType.COW.create(level);
        if (cow == null) throw new IllegalStateException("Could not create exposure test cow");
        cow.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        PortEnvironmentRadiation.tick(cow);
        var data = PortRadiation.get(cow)
                .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));
        data.rollEnvironmentalRadiation();
        PortChunkRadiation.set(level, pos, 0.0F);
        if (Math.abs(data.radiation() - 1.0F) > 0.0001F
                || Math.abs(data.environmentRate() - 1.0F) > 0.0001F) {
            throw new IllegalStateException("20 RAD/s did not give one RAD and report one RAD of tick exposure");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void geigerCounterReadsServerMeasurements(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        PortChunkRadiation.set(level, pos, 10.0F);
        ItemStack counter = new ItemStack(PortItems.GEIGER_COUNTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, counter);
        var data = PortRadiation.get(player)
                .orElseThrow(() -> new IllegalStateException("Player exposure capability missing"));
        data.addRadiation(5.0F);
        data.addEnvironmentalRadiation(2.0F);
        data.rollEnvironmentalRadiation();
        try {
            PortItems.GEIGER_COUNTER.get().use(level, player, InteractionHand.MAIN_HAND);
            if (PortChunkRadiation.at(level, pos) != 10.0F
                    || data.environmentRate() != 2.0F || data.radiation() != 5.0F) {
                throw new IllegalStateException("Geiger readout altered server measurements");
            }
        } finally {
            PortChunkRadiation.set(level, pos, 0.0F);
            data.addRadiation(-2500.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void geigerSoundsMatchLegacyThresholds(GameTestHelper helper) {
        if (ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("hbm", "item.tech_boop"))
                != PortSounds.TECH_BOOP.get()) {
            throw new IllegalStateException("Geiger right-click sound is not registered");
        }
        for (int i = 1; i <= 6; i++) {
            var id = new ResourceLocation("hbm", "item.geiger" + i);
            if (ForgeRegistries.SOUND_EVENTS.getValue(id) != PortSounds.geiger(i)) {
                throw new IllegalStateException("Geiger click sound " + i + " is not registered");
            }
        }
        int[] rates = {5, 10, 15, 20, 25, 30};
        for (int i = 0; i < rates.length; i++) {
            if (PortGeigerCounter.clickSoundFor(rates[i], RandomSource.create(37)) != i + 1) {
                throw new IllegalStateException("Geiger click threshold differs at " + rates[i] + " RAD/s");
            }
        }
        int background = PortGeigerCounter.clickSoundFor(0.0F, RandomSource.create(37));
        if (background != 0 && background != 1) {
            throw new IllegalStateException("Quiet Geiger emitted an invalid sound");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherAmbientRadiationDosesEntities(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) throw new IllegalStateException("Nether test dimension is unavailable");
        var cow = EntityType.COW.create(nether);
        if (cow == null) throw new IllegalStateException("Could not create Nether exposure test cow");
        PortEnvironmentRadiation.tick(cow);
        var data = PortRadiation.get(cow)
                .orElseThrow(() -> new IllegalStateException("Radiation capability missing"));
        float expected = PortConfig.NETHER_AMBIENT_RADS.get().floatValue() / 20.0F;
        if (Math.abs(data.radiation() - expected) > 0.0001F) {
            throw new IllegalStateException("Nether ambient radiation did not dose an entity");
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
                        for (int y = 5; y <= 24; y++) {
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

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ordinaryOresGenerateInFreshChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block[] ores = {
                PortBlocks.ORE_THORIUM.get(), PortBlocks.ORE_TITANIUM.get(), PortBlocks.ORE_COPPER.get(),
                PortBlocks.ORE_TUNGSTEN.get(), PortBlocks.ORE_LEAD.get(), PortBlocks.ORE_BERYLLIUM.get()
        };
        int[] found = new int[ores.length];
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int baseChunkX = (origin.getX() >> 4) + ThreadLocalRandom.current().nextInt(256, 8192);
        int baseChunkZ = (origin.getZ() >> 4) + ThreadLocalRandom.current().nextInt(256, 8192);
        for (int chunkX = baseChunkX; chunkX < baseChunkX + 2; chunkX++) {
            for (int chunkZ = baseChunkZ; chunkZ < baseChunkZ + 2; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
                for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                    for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                        for (int y = 5; y <= 49; y++) {
                            var state = level.getBlockState(new BlockPos(x, y, z));
                            for (int i = 0; i < ores.length; i++) {
                                if (state.is(ores[i])) found[i]++;
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < ores.length; i++) {
            if (found[i] == 0) {
                throw new IllegalStateException("No " + ForgeRegistries.BLOCKS.getKey(ores[i])
                        + " in four fresh overworld chunks");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void oreSpawnSettingsControlFreshChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int baseChunkX = ThreadLocalRandom.current().nextInt(12000, 16000);
        int baseChunkZ = ThreadLocalRandom.current().nextInt(12000, 16000);
        int originalCopper = PortConfig.ORE_COPPER_COUNT.get();
        boolean originalMaster = PortConfig.ENABLE_OVERWORLD_ORES.get();
        try {
            for (String ore : new String[] {
                    "uranium", "thorium", "titanium", "copper", "tungsten", "lead", "beryllium"
            }) {
                var setting = PortConfig.oreCountSetting(ore);
                int originalCount = setting.get();
                try {
                    setting.set(0);
                    if (new PortOreCountPlacement(ore).count(RandomSource.create(1), BlockPos.ZERO) != 0) {
                        throw new IllegalStateException(ore + " zero spawn count did not disable placement attempts");
                    }
                    setting.set(3);
                    if (new PortOreCountPlacement(ore).count(RandomSource.create(1), BlockPos.ZERO) != 3) {
                        throw new IllegalStateException(ore + " placement did not read the changed spawn count");
                    }
                } finally {
                    setting.set(originalCount);
                }
            }
            PortConfig.ORE_COPPER_COUNT.set(0);
            if (new PortOreCountPlacement("copper").count(RandomSource.create(1), BlockPos.ZERO) != 0) {
                throw new IllegalStateException("Zero copper spawn setting did not disable placement attempts");
            }
            if (countOresInFreshChunks(level, baseChunkX, baseChunkZ,
                    new Block[] {PortBlocks.ORE_COPPER.get()}) != 0) {
                throw new IllegalStateException("Copper generated with its spawn count set to zero");
            }

            PortConfig.ORE_COPPER_COUNT.set(3);
            if (new PortOreCountPlacement("copper").count(RandomSource.create(1), BlockPos.ZERO) != 3) {
                throw new IllegalStateException("Copper placement did not read the changed spawn count");
            }
            PortConfig.ENABLE_OVERWORLD_ORES.set(false);
            if (new PortOreCountPlacement("uranium").count(RandomSource.create(1), BlockPos.ZERO) != 0) {
                throw new IllegalStateException("Overworld ore master switch did not disable placement attempts");
            }
            Block[] all = {
                    PortBlocks.ORE_URANIUM.get(), PortBlocks.ORE_THORIUM.get(),
                    PortBlocks.ORE_TITANIUM.get(), PortBlocks.ORE_COPPER.get(),
                    PortBlocks.ORE_TUNGSTEN.get(), PortBlocks.ORE_LEAD.get(),
                    PortBlocks.ORE_BERYLLIUM.get()
            };
            if (countOresInFreshChunks(level, baseChunkX + 64, baseChunkZ + 64, all) != 0) {
                throw new IllegalStateException("HBM ore generated with the Overworld master switch disabled");
            }
        } finally {
            PortConfig.ORE_COPPER_COUNT.set(originalCopper);
            PortConfig.ENABLE_OVERWORLD_ORES.set(originalMaster);
        }
        helper.succeed();
    }

    private static int countOresInFreshChunks(ServerLevel level, int baseChunkX, int baseChunkZ, Block[] ores) {
        int found = 0;
        for (int chunkX = baseChunkX; chunkX < baseChunkX + 2; chunkX++) {
            for (int chunkZ = baseChunkZ; chunkZ < baseChunkZ + 2; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
                for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                    for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                        for (int y = 5; y <= 49; y++) {
                            var state = level.getBlockState(new BlockPos(x, y, z));
                            for (Block ore : ores) if (state.is(ore)) found++;
                        }
                    }
                }
            }
        }
        return found;
    }
}
