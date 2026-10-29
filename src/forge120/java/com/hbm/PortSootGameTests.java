package com.hbm;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortSootGameTests {
    private PortSootGameTests() {}

    @GameTest(template = "empty")
    public static void sootUsesSourceCellsAndSimultaneousDispersion(GameTestHelper helper) {
        var data = new PortSootPollution();
        BlockPos home = new BlockPos(0, 70, 0);
        data.set(home, 100);
        near(data.at(new BlockPos(63, -20, 63)), 100, "Soot cell is not 64x64 and height independent");
        near(data.at(new BlockPos(-1, 70, 0)), 0, "Negative coordinates did not floor into the preceding cell");
        data.spread();
        near(data.at(home), 79.2F, "Source center retention differs");
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            near(data.at(home.relative(direction, 64)), 5, "Source sends five percent to four neighbors");
        }
        near(data.at(home.offset(64, 0, 64)), 0, "Soot incorrectly spreads diagonally in one cycle");
        var two = new PortSootPollution();
        two.set(home, 100);
        two.set(home.east(64), 200);
        two.spread();
        near(two.at(home), 89.2F, "Neighbor contribution was processed twice in one cycle");
        near(two.at(home.east(64)), 163.4F, "Adjacent cells were updated in place");
        var threshold = new PortSootPollution();
        threshold.set(home, 10);
        threshold.spread();
        near(threshold.at(home), 9.9F, "Soot at exactly ten must only decay");
        near(threshold.at(home.east(64)), 0, "Source strict spreading threshold changed");
        var loaded = PortSootPollution.load(two.save(new CompoundTag()));
        near(loaded.at(home), 89.2F, "Dispersion state did not survive NBT");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sootConfigAndBoundsPreserveStoredPollution(GameTestHelper helper) {
        boolean enabled = PortConfig.ENABLE_POLLUTION.get();
        double multiplier = PortConfig.POLLUTION_MULTIPLIER.get();
        try {
            PortConfig.ENABLE_POLLUTION.set(true);
            PortConfig.POLLUTION_MULTIPLIER.set(2D);
            var data = new PortSootPollution();
            BlockPos pos = BlockPos.ZERO;
            data.add(pos, 8);
            data.add(pos, -3);
            near(data.at(pos), 10, "Source multiplier or pollution removal differs");
            data.add(pos, Float.NaN);
            near(data.at(pos), 10, "Invalid emission poisoned cell state");
            PortConfig.ENABLE_POLLUTION.set(false);
            data.add(pos, 100);
            data.set(pos, 200);
            data.spread();
            near(data.at(pos), 0, "Disabled pollution must be hidden from gameplay");
            var restored = PortSootPollution.load(data.save(new CompoundTag()));
            PortConfig.ENABLE_POLLUTION.set(true);
            near(restored.at(pos), 10, "Disabling pollution erased or changed stored cells");
            restored.add(pos, 20_000);
            near(restored.at(pos), 10_000, "Source pollution cap lost");
            restored.add(pos, -20_000);
            near(restored.at(pos), 0, "Pollution removal went negative");
        } finally {
            PortConfig.POLLUTION_MULTIPLIER.set(multiplier);
            PortConfig.ENABLE_POLLUTION.set(enabled);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void serverSootCycleDoesNotLoadTerrain(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = PortSootPollution.get(level);
        BlockPos pos = new BlockPos(6_000_000, 100, -6_000_000);
        if (level.hasChunkAt(pos)) throw new IllegalStateException("Soot fixture terrain unexpectedly loaded");
        data.set(pos, 100);
        helper.succeedWhen(() -> {
            helper.assertTrue(data.at(pos) != 100, "Waiting for source 60-tick update");
            near(data.at(pos), 79.2F, "Real server cycle did not apply source dispersion");
            near(data.at(pos.east(64)), 5, "Real server cycle did not update an unloaded neighbor");
            if (level.hasChunkAt(pos) || level.hasChunkAt(pos.east(64))) {
                throw new IllegalStateException("Pollution simulation generated terrain");
            }
            clearCross(data, pos);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void progressionMachinesEmitOnlyWhileWorking(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            helper.assertTrue(level.getGameTime() % 20 == 0, "Waiting for emission second");
            BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
            var soot = PortSootPollution.get(level);
            float before = soot.at(pos);
            var furnace = new PortElectricFurnaceBlockEntity(pos, PortBlocks.MACHINE_ELECTRIC_FURNACE.get().defaultBlockState());
            furnace.setLevel(level);
            furnace.items().setStackInSlot(1, new ItemStack(Items.IRON_ORE));
            furnace.setPower(5000);
            PortElectricFurnaceBlockEntity.tick(level, pos, furnace.getBlockState(), furnace);
            near(soot.at(pos) - before, 0.04F, "Electric furnace source emissions missing");
            furnace.items().setStackInSlot(2, new ItemStack(Items.GOLD_INGOT));
            before = soot.at(pos);
            PortElectricFurnaceBlockEntity.tick(level, pos, furnace.getBlockState(), furnace);
            near(soot.at(pos), before, "Blocked electric furnace emitted soot");
            furnace.items().setStackInSlot(2, ItemStack.EMPTY);
            furnace.setPower(0);
            PortElectricFurnaceBlockEntity.tick(level, pos, furnace.getBlockState(), furnace);
            near(soot.at(pos), before, "Unpowered electric furnace emitted soot");
            var ignition = new PortFireboxBlockEntity(pos, PortBlocks.HEATER_FIREBOX.get().defaultBlockState());
            ignition.setLevel(level);
            ignition.items().setStackInSlot(0, new ItemStack(Items.COAL));
            PortFireboxBlockEntity.tick(level, pos, ignition.getBlockState(), ignition);
            if (ignition.burnTime() != 2000 || ignition.heatEnergy() != 200 || ignition.smoke() != 0
                    || !ignition.items().getStackInSlot(0).isEmpty()) {
                throw new IllegalStateException("Firebox ignition consumed burn time or generated smoke one tick early");
            }
            PortFireboxBlockEntity.tick(level, pos, ignition.getBlockState(), ignition);
            if (ignition.burnTime() != 1999 || ignition.heatEnergy() != 400 || ignition.smoke() != 12) {
                throw new IllegalStateException("Firebox did not begin source fuel use and buffered smoke on its next tick");
            }
            near(soot.at(pos), before, "Firebox emitted smoke that should remain in its buffer");
            var firebox = new PortFireboxBlockEntity(pos, PortBlocks.HEATER_FIREBOX.get().defaultBlockState());
            firebox.setLevel(level);
            var nbt = new CompoundTag();
            nbt.putInt("burnTime", 100); nbt.putInt("maxBurnTime", 100);
            nbt.putInt("burnHeat", 200); nbt.putInt("smoke", 48);
            firebox.load(nbt);
            PortFireboxBlockEntity.tick(level, pos, firebox.getBlockState(), firebox);
            if (firebox.smoke() != 50 || firebox.burnTime() != 99 || firebox.heatEnergy() != 200) {
                throw new IllegalStateException("Firebox lost its source smoke buffer or fuel/heat accounting");
            }
            near(soot.at(pos) - before, 0.10F, "Firebox emitted buffered smoke or lost overflow");
            nbt = firebox.saveWithFullMetadata();
            nbt.putInt("heatEnergy", 100_000);
            firebox.load(nbt);
            before = soot.at(pos);
            PortFireboxBlockEntity.tick(level, pos, firebox.getBlockState(), firebox);
            if (firebox.burnTime() != 99 || firebox.smoke() != 50) {
                throw new IllegalStateException("Full-temperature firebox consumed fuel or produced smoke");
            }
            near(soot.at(pos), before, "Full-temperature firebox emitted soot");
            var copy = new PortFireboxBlockEntity(pos, firebox.getBlockState());
            copy.load(firebox.saveWithFullMetadata());
            if (copy.smoke() != 50) throw new IllegalStateException("Firebox smoke buffer lost on reload");
        });
    }

    @GameTest(template = "empty")
    public static void fireboxExportsSmokeFromAllTwelveSourceOutlets(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        var firebox = new PortFireboxBlockEntity(core, PortBlocks.HEATER_FIREBOX.get().defaultBlockState());
        firebox.setLevel(level);
        for (Direction direction : Direction.Plane.HORIZONTAL) for (int offset = -1; offset <= 1; offset++) {
            BlockPos target = core.relative(direction, 2).relative(direction.getClockWise(), offset);
            level.setBlock(target, Blocks.CHEST.defaultBlockState(), 2);
            var sink = new SmokeSink(target, Blocks.CHEST.defaultBlockState(), direction.getOpposite());
            level.setBlockEntity(sink);
            var tag = new CompoundTag(); tag.putInt("smoke", 50);
            firebox.load(tag);
            PortFireboxBlockEntity.tick(level, core, firebox.getBlockState(), firebox);
            if (firebox.smoke() != 25 || sink.tank.getFluidAmount() != 25
                    || sink.tank.getFluid().getFluid() != PortFluids.SMOKE.get()) {
                throw new IllegalStateException("Source smoke outlet did not conserve a partial transfer: " + direction + "/" + offset);
            }
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireboxSmokeDoesNotLoadAnAbsentOutletChunk(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos core = new BlockPos(2_000_014, 280, -2_000_000);
        BlockPos absent = core.east(2);
        level.getChunkAt(core);
        if (level.hasChunkAt(absent)) throw new IllegalStateException("Smoke outlet fixture already loaded");
        var state = PortBlocks.HEATER_FIREBOX.get().defaultBlockState();
        level.setBlock(core, state, 2);
        var firebox = (PortFireboxBlockEntity) level.getBlockEntity(core);
        var tag = new CompoundTag(); tag.putInt("smoke", 50);
        firebox.load(tag);
        PortFireboxBlockEntity.tick(level, core, state, firebox);
        if (level.hasChunkAt(absent) || firebox.smoke() != 50) {
            throw new IllegalStateException("Smoke export loaded an absent chunk or lost its buffered gas");
        }
        level.setBlock(core, Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sootSpawnBuffUsesPersistentSourceModifiers(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(1_500_000, 280, 1_500_000);
        level.getChunkAt(pos);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        var data = PortSootPollution.get(level);
        var zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(pos, 0, 0);
        data.set(pos, 15);
        PortSootEffects.decorateMob(zombie);
        near(zombie.getMaxHealth(), 20, "Source strict mob threshold changed");
        data.set(pos, 100);
        ForgeEventFactory.onFinalizeSpawn(zombie, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
        if (!zombie.getPersistentData().getBoolean("hbmSootSpawnPending")) {
            throw new IllegalStateException("The Forge spawn event did not reach the soot subscriber");
        }
        zombie.getAttribute(Attributes.MAX_HEALTH).removeModifiers();
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).removeModifiers();
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3);
        zombie.setNoAi(true); zombie.setInvulnerable(true); zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.tickCount > 0, "Waiting for the forced spawn fixture to tick");
            near(zombie.getMaxHealth(), 40, "Source soot health modifier missing after spawn");
            near((float) zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), 7.5F, "Source soot damage modifier missing");
            near(zombie.getHealth(), 40, "Soot-buffed mob did not start at full health");
            PortSootEffects.decorateMob(zombie);
            near(zombie.getMaxHealth(), 40, "Soot health modifier stacked");
            var restored = EntityType.ZOMBIE.create(level);
            var saved = new CompoundTag(); zombie.save(saved); restored.load(saved);
            near(restored.getMaxHealth(), 40, "Soot modifiers did not survive entity NBT");
            var cow = EntityType.COW.create(level); cow.moveTo(pos, 0, 0);
            PortSootEffects.decorateMob(cow);
            near(cow.getMaxHealth(), 10, "Pollution buffed a passive animal");
            zombie.discard(); clearCross(data, pos);
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
        });
    }

    @GameTest(template = "empty")
    public static void sootCoughUsesSourceFrequencyAndCoarseProtection(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("8760d70c-613e-46db-895b-a6a786cb9823"), "SootCoughTest"));
        BlockPos pos = new BlockPos(3_000_000, 100, 3_000_000);
        player.moveTo(pos, 0, 0);
        var data = PortSootPollution.get(level);
        data.set(pos, 30);
        if (PortSootEffects.coughPeriod(player) != 0) throw new IllegalStateException("Soot cough threshold is not strict");
        data.set(pos, 100);
        if (PortSootEffects.coughPeriod(player) != 50) throw new IllegalStateException("Source maximum soot cough frequency changed");
        for (var filter : java.util.List.of(PortItems.GAS_MASK_FILTER.get(), PortItems.GAS_MASK_FILTER_COMBO.get(),
                PortItems.GAS_MASK_FILTER_MONO.get(), PortItems.GAS_MASK_FILTER_RAG.get(), PortItems.GAS_MASK_FILTER_PISS.get())) {
            var mask = new ItemStack(PortItems.GAS_MASK.get());
            PortGasMask.installFilter(mask, new ItemStack(filter));
            player.setItemSlot(EquipmentSlot.HEAD, mask);
            if (PortSootEffects.coughPeriod(player) != 0 || PortGasMask.filter(mask).getDamageValue() != 0) {
                throw new IllegalStateException("Source coarse filter failed or wore merely from soot: " + filter);
            }
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        data.set(pos, 50);
        if (PortSootEffects.coughPeriod(player) != 525) throw new IllegalStateException("Source proportional soot cough frequency changed");
        clearCross(data, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sootPacketAndFogMatchSourceBoundsAndSmoothing(GameTestHelper helper) {
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -10, 100_001}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeFloat(invalid);
                near(PortNetwork.SootSync.decode(buffer).soot(), invalid == 100_001 ? 10_000 : 0,
                        "Soot packet accepted an invalid value");
            } finally { buffer.release(); }
        }
        var fog = new PortSootFog();
        fog.receive(155);
        fog.tick(); near(fog.current(), 0.05F, "Source smog must interpolate by 0.05 per tick");
        for (int i = 1; i < 3101; i++) fog.tick();
        near(fog.current(), 155, "Smog smoothing did not settle on the synchronized cell");
        near(fog.opacity(), 1, "Source smog color must reach 0.15 grey after 120 excess soot");
        near(fog.farPlane(96), 16, "Source smog distance formula changed");
        fog.receive(155.02F); fog.tick(); near(fog.current(), 155.02F, "Source interpolation overshot a small update");
        fog.reset(); near(fog.current(), 0, "World changes retained stale fog");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dispersedSootSurvivesDedicatedRestart(GameTestHelper helper) {
        String phase = System.getProperty("hbm.port.persistence.phase", "");
        var data = PortSootPollution.get(helper.getLevel());
        BlockPos pos = new BlockPos(7_000_000, 100, 7_000_000);
        if (phase.equals("seed")) {
            clearCross(data, pos); data.set(pos, 123.5F); data.spread();
        } else if (phase.equals("verify")) {
            if (data.at(pos) <= 0 || data.at(pos) > 123.5F || data.at(pos.east(64)) <= 0) {
                throw new IllegalStateException("Source soot and its unloaded neighbor did not survive the separate server launch");
            }
            clearCross(data, pos);
        } else if (!phase.isEmpty()) throw new IllegalStateException("Unknown soot persistence phase " + phase);
        helper.succeed();
    }

    private static void clearCross(PortSootPollution data, BlockPos pos) {
        data.set(pos, 0);
        for (Direction direction : Direction.Plane.HORIZONTAL) data.set(pos.relative(direction, 64), 0);
    }

    private static void near(float actual, float expected, String message) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.001F) {
            throw new IllegalStateException(message + ": " + actual + " != " + expected);
        }
    }

    private static final class SmokeSink extends BlockEntity {
        final FluidTank tank = new FluidTank(25);
        private final LazyOptional<net.minecraftforge.fluids.capability.IFluidHandler> capability = LazyOptional.of(() -> tank);
        private final Direction side;
        SmokeSink(BlockPos pos, BlockState state, Direction side) { super(BlockEntityType.CHEST, pos, state); this.side = side; }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction direction) {
            return requested == ForgeCapabilities.FLUID_HANDLER && direction == side ? capability.cast() : super.getCapability(requested, direction);
        }
        @Override public void invalidateCaps() { super.invalidateCaps(); capability.invalidate(); }
    }
}
