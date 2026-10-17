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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Set;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortGameTests {
    private PortGameTests() {}

    @GameTest(template = "empty")
    public static void powerNetworkConservesAndPrioritizes(GameTestHelper helper) {
        PortPowerStorage sourceA = new PortPowerStorage(100);
        PortPowerStorage sourceB = new PortPowerStorage(50);
        PortPowerStorage highA = new PortPowerStorage(100);
        PortPowerStorage highB = new PortPowerStorage(100);
        PortPowerStorage low = new PortPowerStorage(50);
        sourceA.setPower(100);
        sourceB.setPower(50);
        long before = sourceA.power() + sourceB.power() + highA.power() + highB.power() + low.power();
        long moved = PortPowerNetwork.transfer(
                List.of(new PortPowerNetwork.Provider(sourceA, 100),
                        new PortPowerNetwork.Provider(sourceB, 50)),
                List.of(new PortPowerNetwork.Receiver(highA, 100, PortPowerNetwork.Priority.HIGH),
                        new PortPowerNetwork.Receiver(highB, 100, PortPowerNetwork.Priority.HIGH),
                        new PortPowerNetwork.Receiver(low, 50, PortPowerNetwork.Priority.LOW)));
        long after = sourceA.power() + sourceB.power() + highA.power() + highB.power() + low.power();
        if (moved != 150 || before != after || highA.power() != 75 || highB.power() != 75
                || low.power() != 0 || sourceA.power() != 0 || sourceB.power() != 0) {
            throw new IllegalStateException("HE priority distribution or conservation failed");
        }
        PortPowerStorage equalA = new PortPowerStorage(100);
        PortPowerStorage equalB = new PortPowerStorage(100);
        PortPowerStorage equalSink = new PortPowerStorage(100);
        equalA.setPower(100);
        equalB.setPower(100);
        long equalMoved = PortPowerNetwork.transfer(
                List.of(new PortPowerNetwork.Provider(equalA, 100),
                        new PortPowerNetwork.Provider(equalB, 100)),
                List.of(new PortPowerNetwork.Receiver(equalSink, 100, PortPowerNetwork.Priority.NORMAL)));
        if (equalMoved != 100 || equalA.power() != 50 || equalB.power() != 50
                || equalSink.power() != 100) {
            throw new IllegalStateException("HE providers did not share load proportionally");
        }
        PortPowerStorage buffer = new PortPowerStorage(100);
        buffer.setPower(50);
        if (PortPowerNetwork.transfer(List.of(new PortPowerNetwork.Provider(buffer, 50)),
                List.of(new PortPowerNetwork.Receiver(buffer, 50, PortPowerNetwork.Priority.NORMAL))) != 0
                || buffer.power() != 50) {
            throw new IllegalStateException("HE buffer supplied itself");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void powerStorageRatesAndPersistence(GameTestHelper helper) {
        PortPowerStorage source = new PortPowerStorage(1_000_000_000_000L);
        PortPowerStorage high = new PortPowerStorage(100);
        PortPowerStorage low = new PortPowerStorage(100);
        source.setPower(source.capacity());
        long moved = PortPowerNetwork.transfer(
                List.of(new PortPowerNetwork.Provider(source, 40)),
                List.of(new PortPowerNetwork.Receiver(high, 25, PortPowerNetwork.Priority.HIGH),
                        new PortPowerNetwork.Receiver(low, 100, PortPowerNetwork.Priority.LOW)));
        if (moved != 40 || source.power() != source.capacity() - 40
                || high.power() != 25 || low.power() != 15) {
            throw new IllegalStateException("HE rate limits or priority order failed");
        }
        CompoundTag tag = new CompoundTag();
        source.save(tag, "power");
        PortPowerStorage reloaded = new PortPowerStorage(source.capacity());
        reloaded.load(tag, "power");
        if (reloaded.power() != source.power()) throw new IllegalStateException("Long HE NBT round-trip failed");
        tag.putLong("power", Long.MAX_VALUE);
        reloaded.load(tag, "power");
        if (reloaded.power() != reloaded.capacity()) throw new IllegalStateException("HE load exceeded capacity");
        tag.putLong("power", -1);
        reloaded.load(tag, "power");
        if (reloaded.power() != 0) throw new IllegalStateException("Negative HE persisted");
        PortPowerStorage hugeSource = new PortPowerStorage(Long.MAX_VALUE);
        PortPowerStorage hugeSink = new PortPowerStorage(Long.MAX_VALUE);
        hugeSource.setPower(Long.MAX_VALUE);
        if (PortPowerNetwork.transfer(List.of(new PortPowerNetwork.Provider(hugeSource, Long.MAX_VALUE)),
                List.of(new PortPowerNetwork.Receiver(hugeSink, Long.MAX_VALUE,
                        PortPowerNetwork.Priority.NORMAL))) != Long.MAX_VALUE
                || hugeSource.power() != 0 || hugeSink.power() != Long.MAX_VALUE) {
            throw new IllegalStateException("Long HE transfer overflowed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batteryTransfersAndPreservesChargeOnBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos targetPos = sourcePos.east();
        level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(targetPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(sourcePos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        if (!(level.getBlockEntity(sourcePos) instanceof PortBatteryBlockEntity source)
                || !(level.getBlockEntity(targetPos) instanceof PortBatteryBlockEntity target)) {
            throw new IllegalStateException("Battery block entities were not created");
        }
        source.setPower(5000);
        if (source.mode(level.hasNeighborSignal(sourcePos)) != PortBatteryBlockEntity.OUTPUT
                || target.mode(level.hasNeighborSignal(targetPos)) != PortBatteryBlockEntity.INPUT) {
            throw new IllegalStateException("Battery redstone modes differ from legacy defaults");
        }
        PortBatteryBlockEntity.tick(level, sourcePos, level.getBlockState(sourcePos), source);
        if (source.power() != 3334 || target.power() != 1666
                || source.comparatorPower() != 1 || target.comparatorPower() != 1) {
            throw new IllegalStateException("Battery direct transfer speed, conservation, or comparator failed");
        }
        source.setModes(PortBatteryBlockEntity.BUFFER, PortBatteryBlockEntity.OUTPUT);
        source.setPriority(PortPowerNetwork.Priority.HIGH);
        var drops = Block.getDrops(level.getBlockState(sourcePos), level, sourcePos, source,
                FakePlayerFactory.getMinecraft(level), new ItemStack(Items.IRON_PICKAXE));
        if (drops.size() != 1 || !drops.get(0).is(PortItems.MACHINE_BATTERY.get())
                || drops.get(0).getTag() == null
                || drops.get(0).getTag().getCompound("persistent").getLong("power") != 3334) {
            throw new IllegalStateException("Battery drop lost its stored HE");
        }
        level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        PortBlocks.MACHINE_BATTERY.get().setPlacedBy(level, sourcePos, level.getBlockState(sourcePos),
                null, drops.get(0));
        if (!(level.getBlockEntity(sourcePos) instanceof PortBatteryBlockEntity restored)
                || restored.power() != 3334 || restored.lowMode() != PortBatteryBlockEntity.BUFFER
                || restored.priority() != PortPowerNetwork.Priority.HIGH) {
            throw new IllegalStateException("Placed battery did not restore item HE or controls");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void classicCablePrioritizesAndDisconnects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos firstCable = sourcePos.east();
        BlockPos secondCable = firstCable.east();
        BlockPos highPos = secondCable.east();
        BlockPos lowPos = secondCable.south();
        level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(highPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(lowPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        PortCableBlock cable = (PortCableBlock) PortBlocks.RED_CABLE_CLASSIC.get();
        level.setBlock(firstCable, cable.connectedState(level, firstCable), 3);
        level.setBlock(secondCable, cable.connectedState(level, secondCable), 3);
        if (ForgeRegistries.BLOCKS.getValue(PortBlocks.RED_CABLE_CLASSIC.getId()) != cable
                || ForgeRegistries.ITEMS.getValue(PortItems.RED_CABLE_CLASSIC.getId())
                        != PortItems.RED_CABLE_CLASSIC.get()) {
            throw new IllegalStateException("Classic cable block or item registry entry is missing");
        }
        var cableDrops = Block.getDrops(level.getBlockState(firstCable), level, firstCable, null,
                FakePlayerFactory.getMinecraft(level), new ItemStack(Items.IRON_PICKAXE));
        if (cableDrops.size() != 1 || !cableDrops.get(0).is(PortItems.RED_CABLE_CLASSIC.get())) {
            throw new IllegalStateException("Classic cable does not drop its item");
        }
        level.setBlock(sourcePos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        PortBatteryBlockEntity source = (PortBatteryBlockEntity) level.getBlockEntity(sourcePos);
        PortBatteryBlockEntity high = (PortBatteryBlockEntity) level.getBlockEntity(highPos);
        PortBatteryBlockEntity low = (PortBatteryBlockEntity) level.getBlockEntity(lowPos);
        source.setPower(5_000);
        high.setPriority(PortPowerNetwork.Priority.HIGH);
        if (!level.getBlockState(firstCable).getValue(PortCableBlock.WEST)
                || !level.getBlockState(firstCable).getValue(PortCableBlock.EAST)
                || !level.getBlockState(secondCable).getValue(PortCableBlock.SOUTH)
                || level.getBlockState(secondCable).getValue(PortCableBlock.UP)) {
            throw new IllegalStateException("Classic cable failed to update its six-way connection state");
        }
        PortBatteryBlockEntity.tick(level, sourcePos, level.getBlockState(sourcePos), source);
        if (source.power() != 3_334 || high.power() != 1_666 || low.power() != 0) {
            throw new IllegalStateException("Cable network priority, rate, or HE conservation failed");
        }
        PortBatteryBlockEntity.tick(level, highPos, level.getBlockState(highPos), high);
        if (source.power() != 3_334 || high.power() != 1_666) {
            throw new IllegalStateException("Cable network transferred twice in one server tick");
        }
        level.setBlock(secondCable, Blocks.AIR.defaultBlockState(), 3);
        long before = high.power() + low.power();
        helper.runAfterDelay(2, () -> {
            if (high.power() + low.power() != before
                    || level.getBlockState(firstCable).getValue(PortCableBlock.EAST)) {
                throw new IllegalStateException("Disconnected HE cable still transferred or retained its arm");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void classicCableInputDoesNotBridge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos firstCable = sourcePos.east();
        BlockPos middlePos = firstCable.east();
        BlockPos secondCable = middlePos.east();
        BlockPos targetPos = secondCable.east();
        level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(middlePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(targetPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        PortCableBlock cable = (PortCableBlock) PortBlocks.RED_CABLE_CLASSIC.get();
        level.setBlock(firstCable, cable.connectedState(level, firstCable), 3);
        level.setBlock(secondCable, cable.connectedState(level, secondCable), 3);
        level.setBlock(sourcePos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        PortBatteryBlockEntity source = (PortBatteryBlockEntity) level.getBlockEntity(sourcePos);
        PortBatteryBlockEntity middle = (PortBatteryBlockEntity) level.getBlockEntity(middlePos);
        PortBatteryBlockEntity target = (PortBatteryBlockEntity) level.getBlockEntity(targetPos);
        target.setPriority(PortPowerNetwork.Priority.HIGH);
        source.setPower(5_000);
        PortBatteryBlockEntity.tick(level, sourcePos, level.getBlockState(sourcePos), source);
        if (source.power() != 3_334 || middle.power() != 1_666 || target.power() != 0) {
            throw new IllegalStateException("Input-only battery illegally bridged two cable networks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void classicCableBufferBridgesTwoSegments(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos firstCable = sourcePos.east();
        BlockPos middlePos = firstCable.east();
        BlockPos secondCable = middlePos.east();
        BlockPos targetPos = secondCable.east();
        level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(middlePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(targetPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        PortCableBlock cable = (PortCableBlock) PortBlocks.RED_CABLE_CLASSIC.get();
        level.setBlock(firstCable, cable.connectedState(level, firstCable), 3);
        level.setBlock(secondCable, cable.connectedState(level, secondCable), 3);
        level.setBlock(sourcePos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        PortBatteryBlockEntity source = (PortBatteryBlockEntity) level.getBlockEntity(sourcePos);
        PortBatteryBlockEntity middle = (PortBatteryBlockEntity) level.getBlockEntity(middlePos);
        PortBatteryBlockEntity target = (PortBatteryBlockEntity) level.getBlockEntity(targetPos);
        middle.setModes(PortBatteryBlockEntity.BUFFER, PortBatteryBlockEntity.BUFFER);
        target.setPriority(PortPowerNetwork.Priority.HIGH);
        source.setPower(5_000);
        PortBatteryBlockEntity.tick(level, sourcePos, level.getBlockState(sourcePos), source);
        if (source.power() != 3_334 || middle.power() != 0 || target.power() != 1_666) {
            throw new IllegalStateException("Buffer-mode battery failed to bridge separate HE cables");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void classicCableLimitsOutputAcrossBranches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos northCable = sourcePos.north();
        BlockPos southCable = sourcePos.south();
        BlockPos northSinkPos = northCable.north();
        BlockPos southSinkPos = southCable.south();
        level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(northSinkPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        level.setBlock(southSinkPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        PortCableBlock cable = (PortCableBlock) PortBlocks.RED_CABLE_CLASSIC.get();
        level.setBlock(northCable, cable.connectedState(level, northCable), 3);
        level.setBlock(southCable, cable.connectedState(level, southCable), 3);
        level.setBlock(sourcePos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        PortBatteryBlockEntity source = (PortBatteryBlockEntity) level.getBlockEntity(sourcePos);
        PortBatteryBlockEntity northSink = (PortBatteryBlockEntity) level.getBlockEntity(northSinkPos);
        PortBatteryBlockEntity southSink = (PortBatteryBlockEntity) level.getBlockEntity(southSinkPos);
        source.setPower(5_000);
        PortBatteryBlockEntity.tick(level, sourcePos, level.getBlockState(sourcePos), source);
        if (source.power() != 3_334 || northSink.power() + southSink.power() != 1_666) {
            throw new IllegalStateException("Separate cable branches exceeded aggregate battery output rate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batteryItemSlotsAndMenuControls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof PortBatteryBlockEntity battery)) {
            throw new IllegalStateException("Battery item-slot fixture missing");
        }
        PortBatteryItem potato = (PortBatteryItem) PortItems.BATTERY_POTATO.get();
        ItemStack potatoStack = new ItemStack(potato);
        if (potato.charge(potatoStack) != 1000 || potato.chargeRate() != 0 || potato.dischargeRate() != 100) {
            throw new IllegalStateException("Potato battery legacy values differ");
        }
        battery.items().setStackInSlot(0, potatoStack);
        PortBatteryBlockEntity.tick(level, pos, level.getBlockState(pos), battery);
        if (battery.power() != 100 || potato.charge(potatoStack) != 900) {
            throw new IllegalStateException("Input item failed to charge the storage at 100 HE/t");
        }
        battery.items().setStackInSlot(0, ItemStack.EMPTY);
        PortBatteryItem cube = (PortBatteryItem) PortItems.CUBE_POWER.get();
        if (cube.capacity() != 1_000_000_000_000_000_000L
                || cube.chargeRate() != 1_000_000_000_000_000L
                || cube.dischargeRate() != 1_000_000_000_000_000L) {
            throw new IllegalStateException("Power cube legacy HE values differ");
        }
        ItemStack cubeStack = new ItemStack(cube);
        cube.setCharge(cubeStack, 0);
        battery.items().setStackInSlot(1, cubeStack);
        battery.setPower(500_000);
        PortBatteryBlockEntity.tick(level, pos, level.getBlockState(pos), battery);
        if (battery.power() != 0 || cube.charge(cubeStack) != 500_000) {
            throw new IllegalStateException("Output item failed to receive HE without loss");
        }
        var top = battery.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
        var bottom = battery.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).resolve().orElseThrow();
        var side = battery.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).resolve().orElseThrow();
        if (top.getSlots() != 1 || bottom.getSlots() != 2 || side.getSlots() != 1
                || !side.extractItem(0, 1, false).isEmpty()) {
            throw new IllegalStateException("Battery sided slots or partial-charge extraction differ from legacy");
        }
        cube.setCharge(cubeStack, cube.capacity());
        if (!side.extractItem(0, 1, true).is(PortItems.CUBE_POWER.get())) {
            throw new IllegalStateException("Fully charged output item was not extractable from the side");
        }
        battery.items().setStackInSlot(0, potatoStack);
        if (!bottom.extractItem(0, 1, false).isEmpty()) {
            throw new IllegalStateException("Partially charged input item was prematurely extractable");
        }
        potato.setCharge(potatoStack, 0);
        if (!bottom.extractItem(0, 1, true).is(PortItems.BATTERY_POTATO.get())) {
            throw new IllegalStateException("Empty input item was not extractable from below");
        }
        var fake = FakePlayerFactory.getMinecraft(level);
        fake.moveTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        battery.setPower(777_777);
        PortBatteryMenu menu = new PortBatteryMenu(1, fake.getInventory(), battery);
        if (menu.power() != 777_777 || !menu.stillValid(fake)
                || !menu.clickMenuButton(fake, 0) || !menu.clickMenuButton(fake, 1)
                || !menu.clickMenuButton(fake, 2) || menu.clickMenuButton(fake, 99)
                || battery.lowMode() != PortBatteryBlockEntity.BUFFER
                || battery.highMode() != PortBatteryBlockEntity.DISABLED
                || battery.priority() != PortPowerNetwork.Priority.NORMAL) {
            throw new IllegalStateException("Battery menu controls failed server validation or mode cycling");
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        if (dropped.stream().noneMatch(entity -> entity.getItem().is(PortItems.BATTERY_POTATO.get()))
                || dropped.stream().noneMatch(entity -> entity.getItem().is(PortItems.CUBE_POWER.get()))) {
            throw new IllegalStateException("Breaking the battery lost its two inventory items");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void batteryChargeSurvivesServerRestart(GameTestHelper helper) {
        String phase = System.getProperty("hbm.port.persistence.phase", "");
        if (phase.isEmpty()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos fixture = new BlockPos(512, 80, 512);
        level.getChunkAt(fixture);
        if (phase.equals("seed")) {
            level.setBlock(fixture, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
            if (!(level.getBlockEntity(fixture) instanceof PortBatteryBlockEntity battery)) {
                throw new IllegalStateException("Battery restart fixture was not created");
            }
            battery.setPower(123_456);
            battery.setModes(PortBatteryBlockEntity.BUFFER, PortBatteryBlockEntity.DISABLED);
            battery.setPriority(PortPowerNetwork.Priority.HIGH);
            PortBatteryItem potato = (PortBatteryItem) PortItems.BATTERY_POTATO.get();
            ItemStack stack = new ItemStack(potato);
            potato.setCharge(stack, 333);
            battery.items().setStackInSlot(1, stack);
        } else if (phase.equals("verify")) {
            if (!(level.getBlockEntity(fixture) instanceof PortBatteryBlockEntity battery)
                    || battery.power() != 123_456
                    || battery.lowMode() != PortBatteryBlockEntity.BUFFER
                    || battery.highMode() != PortBatteryBlockEntity.DISABLED
                    || battery.priority() != PortPowerNetwork.Priority.HIGH
                    || !(battery.items().getStackInSlot(1).getItem() instanceof PortBatteryItem item)
                    || item.charge(battery.items().getStackInSlot(1)) != 333) {
                throw new IllegalStateException("Battery HE, controls, or inventory did not survive restart");
            }
            level.setBlock(fixture, Blocks.AIR.defaultBlockState(), 3);
        } else {
            throw new IllegalStateException("Unknown battery persistence phase: " + phase);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void classicCableTransfersAfterServerRestartAcrossChunkBorder(GameTestHelper helper) {
        String phase = System.getProperty("hbm.port.persistence.phase", "");
        if (phase.isEmpty()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = new BlockPos(527, 80, 512);
        BlockPos cablePos = sourcePos.east();
        BlockPos targetPos = cablePos.east();
        level.getChunkAt(sourcePos);
        level.getChunkAt(cablePos);
        if (sourcePos.getX() >> 4 == cablePos.getX() >> 4) {
            throw new IllegalStateException("Cable restart fixture does not cross a chunk border");
        }
        if (phase.equals("seed")) {
            level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
            level.setBlock(targetPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
            PortCableBlock cable = (PortCableBlock) PortBlocks.RED_CABLE_CLASSIC.get();
            level.setBlock(cablePos, cable.connectedState(level, cablePos), 3);
            PortBatteryBlockEntity source = (PortBatteryBlockEntity) level.getBlockEntity(sourcePos);
            source.setModes(PortBatteryBlockEntity.DISABLED, PortBatteryBlockEntity.DISABLED);
            source.setPower(4_000);
        } else if (phase.equals("verify")) {
            if (!(level.getBlockEntity(sourcePos) instanceof PortBatteryBlockEntity source)
                    || !(level.getBlockEntity(targetPos) instanceof PortBatteryBlockEntity target)
                    || !level.getBlockState(cablePos).is(PortBlocks.RED_CABLE_CLASSIC.get())
                    || !level.getBlockState(cablePos).getValue(PortCableBlock.WEST)
                    || !level.getBlockState(cablePos).getValue(PortCableBlock.EAST)
                    || source.power() != 4_000 || target.power() != 0) {
                throw new IllegalStateException("Cross-chunk HE cable or disabled battery failed restart");
            }
            source.setModes(PortBatteryBlockEntity.OUTPUT, PortBatteryBlockEntity.OUTPUT);
            PortBatteryBlockEntity.tick(level, sourcePos, level.getBlockState(sourcePos), source);
            if (source.power() != 2_334 || target.power() != 1_666) {
                throw new IllegalStateException("Reloaded cross-chunk cable did not transfer at legacy HE rate");
            }
            level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(cablePos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
        } else {
            throw new IllegalStateException("Unknown cable persistence phase: " + phase);
        }
        helper.succeed();
    }

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
                PortBlocks.ORE_TUNGSTEN.get(), PortBlocks.ORE_LEAD.get(), PortBlocks.ORE_BERYLLIUM.get(),
                PortBlocks.ORE_ALUMINIUM.get()
        };
        Item[] oreItems = {
                PortItems.ORE_THORIUM.get(), PortItems.ORE_TITANIUM.get(), PortItems.ORE_COPPER.get(),
                PortItems.ORE_TUNGSTEN.get(), PortItems.ORE_LEAD.get(), PortItems.ORE_BERYLLIUM.get(),
                PortItems.ORE_ALUMINIUM.get()
        };
        Item[] products = {
                PortItems.INGOT_TH232.get(), PortItems.INGOT_TITANIUM.get(), PortItems.INGOT_COPPER.get(),
                PortItems.INGOT_TUNGSTEN.get(), PortItems.INGOT_LEAD.get(), PortItems.INGOT_BERYLLIUM.get(),
                PortItems.CHUNK_ORE_CRYOLITE.get()
        };
        float[] experience = {3.0F, 3.0F, 2.5F, 6.0F, 3.0F, 2.0F, 2.5F};
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
            if (!recipe.getResultItem(helper.getLevel().registryAccess()).is(products[i])
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
    public static void mineralOreDrops(GameTestHelper helper) {
        Block[] ores = {PortBlocks.ORE_SULFUR.get(), PortBlocks.ORE_FLUORITE.get(), PortBlocks.ORE_NITER.get()};
        Item[] oreItems = {PortItems.ORE_SULFUR.get(), PortItems.ORE_FLUORITE.get(), PortItems.ORE_NITER.get()};
        Item[] minerals = {PortItems.SULFUR.get(), PortItems.FLUORITE.get(), PortItems.NITER.get()};
        BlockPos orePos = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemStack silkPick = new ItemStack(Items.IRON_PICKAXE);
        silkPick.enchant(Enchantments.SILK_TOUCH, 1);
        for (int i = 0; i < ores.length; i++) {
            var id = ForgeRegistries.BLOCKS.getKey(ores[i]);
            if (id == null || ForgeRegistries.ITEMS.getValue(id) != oreItems[i]
                    || !helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(id)) {
                throw new IllegalStateException("Mineral ore item or placed feature missing for " + id);
            }
            helper.getLevel().setBlock(orePos, ores[i].defaultBlockState(), 3);
            var drops = Block.getDrops(ores[i].defaultBlockState(), helper.getLevel(), orePos,
                    null, FakePlayerFactory.getMinecraft(helper.getLevel()), new ItemStack(Items.IRON_PICKAXE));
            if (drops.size() != 1 || !drops.get(0).is(minerals[i])
                    || drops.get(0).getCount() < 2 || drops.get(0).getCount() > 4) {
                throw new IllegalStateException("Mineral yield differs from legacy for " + id);
            }
            var silkDrops = Block.getDrops(ores[i].defaultBlockState(), helper.getLevel(), orePos,
                    null, FakePlayerFactory.getMinecraft(helper.getLevel()), silkPick);
            if (silkDrops.size() != 1 || !silkDrops.get(0).is(oreItems[i]) || silkDrops.get(0).getCount() != 1) {
                throw new IllegalStateException("Silk-touch ore drop differs from legacy for " + id);
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
        if (!System.getProperty("hbm.port.ore.config.phase", "").isEmpty()) {
            helper.succeed();
            return;
        }
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
        if (!System.getProperty("hbm.port.ore.config.phase", "").isEmpty()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        Block[] ores = {
                PortBlocks.ORE_THORIUM.get(), PortBlocks.ORE_TITANIUM.get(), PortBlocks.ORE_COPPER.get(),
                PortBlocks.ORE_TUNGSTEN.get(), PortBlocks.ORE_LEAD.get(), PortBlocks.ORE_BERYLLIUM.get(),
                PortBlocks.ORE_SULFUR.get(), PortBlocks.ORE_FLUORITE.get(), PortBlocks.ORE_NITER.get(),
                PortBlocks.ORE_ALUMINIUM.get()
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

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void oreSpawnSettingsControlFreshChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String phase = System.getProperty("hbm.port.ore.config.phase", "");
        for (String ore : new String[] {
                "uranium", "thorium", "titanium", "copper", "tungsten", "lead", "beryllium",
                "sulfur", "fluorite", "niter", "aluminium"
        }) {
            int expected = PortConfig.ENABLE_OVERWORLD_ORES.get() ? PortConfig.oreCount(ore) : 0;
            if (new PortOreCountPlacement(ore).count(RandomSource.create(1), BlockPos.ZERO) != expected) {
                throw new IllegalStateException(ore + " placement does not read the active server config");
            }
        }
        Block[] all = {
                PortBlocks.ORE_URANIUM.get(), PortBlocks.ORE_THORIUM.get(),
                PortBlocks.ORE_TITANIUM.get(), PortBlocks.ORE_COPPER.get(),
                PortBlocks.ORE_TUNGSTEN.get(), PortBlocks.ORE_LEAD.get(),
                PortBlocks.ORE_BERYLLIUM.get(), PortBlocks.ORE_SULFUR.get(),
                PortBlocks.ORE_FLUORITE.get(), PortBlocks.ORE_NITER.get(),
                PortBlocks.ORE_ALUMINIUM.get()
        };
        if (phase.equals("master-off")) {
            int chunkX = ThreadLocalRandom.current().nextInt(20_000, 24_000);
            int chunkZ = ThreadLocalRandom.current().nextInt(20_000, 24_000);
            if (PortConfig.ENABLE_OVERWORLD_ORES.get()
                    || countOresInFreshChunks(level, chunkX, chunkZ, all) != 0) {
                throw new IllegalStateException("HBM ores generated in fresh chunks with master switch off");
            }
        } else if (phase.equals("copper-off")) {
            int chunkX = ThreadLocalRandom.current().nextInt(26_000, 30_000);
            int chunkZ = ThreadLocalRandom.current().nextInt(26_000, 30_000);
            if (!PortConfig.ENABLE_OVERWORLD_ORES.get() || PortConfig.ORE_COPPER_COUNT.get() != 0
                    || countOresInFreshChunks(level, chunkX, chunkZ,
                            new Block[] {PortBlocks.ORE_COPPER.get()}) != 0
                    || countOresInFreshChunks(level, chunkX, chunkZ,
                            new Block[] {PortBlocks.ORE_URANIUM.get()}) == 0) {
                throw new IllegalStateException("Copper-only disable setting failed in fresh chunks");
            }
        } else if (!phase.isEmpty()) {
            throw new IllegalStateException("Unknown ore config test phase: " + phase);
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
