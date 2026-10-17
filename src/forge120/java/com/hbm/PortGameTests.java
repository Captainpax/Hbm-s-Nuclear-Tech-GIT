package com.hbm;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(HbmPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortGameTests {
    private PortGameTests() {}

    @GameTest(template = "empty")
    public static void batteryPackVariantsMatchLegacyRatesAndNBT(GameTestHelper helper) {
        String[] ids = {
                "battery_pack_redstone", "battery_pack_lead", "battery_pack_lithium",
                "battery_pack_sodium", "battery_pack_schrabidium", "battery_pack_quantum",
                "battery_pack_capacitor_copper", "battery_pack_capacitor_gold",
                "battery_pack_capacitor_niobium", "battery_pack_capacitor_tantalum",
                "battery_pack_capacitor_bismuth", "battery_pack_capacitor_spark"
        };
        long[] capacities = {
                1_800_000L, 18_000_000L, 180_000_000L, 900_000_000L,
                4_500_000_000L, 72_000_000_000L, 600_000L, 6_000_000L,
                60_000_000L, 300_000_000L, 1_500_000_000L, 6_000_000_000L
        };
        long[] chargeRates = {
                1_000L, 10_000L, 100_000L, 500_000L, 2_500_000L, 10_000_000L,
                1_000L, 10_000L, 100_000L, 500_000L, 2_500_000L, 10_000_000L
        };
        long[] dischargeRates = {
                100L, 1_000L, 10_000L, 50_000L, 250_000L, 1_000_000L,
                1_000L, 10_000L, 100_000L, 500_000L, 2_500_000L, 10_000_000L
        };
        List<ItemStack> creativeStacks = PortCreativeTabs.packDisplayStacks();
        if (PortItems.BATTERY_PACKS.size() != ids.length || creativeStacks.size() != ids.length * 2) {
            throw new IllegalStateException("Battery pack metadata variants were omitted");
        }
        for (int i = 0; i < ids.length; i++) {
            Item item = PortItems.BATTERY_PACKS.get(i).get();
            if (!(item instanceof PortBatteryItem battery)
                    || !ForgeRegistries.ITEMS.getKey(item).getPath().equals(ids[i])
                    || battery.capacity() != capacities[i]
                    || battery.chargeRate() != chargeRates[i]
                    || battery.dischargeRate() != dischargeRates[i]) {
                throw new IllegalStateException("Legacy battery pack rate or ID differs at " + ids[i]);
            }
            if (!creativeStacks.get(i * 2).is(item) || battery.charge(creativeStacks.get(i * 2)) != 0
                    || !creativeStacks.get(i * 2 + 1).is(item)
                    || battery.charge(creativeStacks.get(i * 2 + 1)) != capacities[i]) {
                throw new IllegalStateException("Creative tab is missing empty/full pack pair: " + ids[i]);
            }
            ItemStack stack = new ItemStack(item);
            if (battery.charge(stack) != 0 || !battery.isBarVisible(stack)) {
                throw new IllegalStateException("Untagged battery pack did not start empty: " + ids[i]);
            }
            battery.setCharge(stack, Long.MAX_VALUE);
            ItemStack reloaded = ItemStack.of(stack.save(new CompoundTag()));
            if (battery.charge(reloaded) != capacities[i] || battery.isBarVisible(reloaded)) {
                throw new IllegalStateException("Full battery pack NBT or charge clamp failed: " + ids[i]);
            }
            battery.setCharge(reloaded, -1);
            if (battery.charge(reloaded) != 0) {
                throw new IllegalStateException("Negative battery pack charge was not clamped: " + ids[i]);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batteryPacksChargeAndDischargeThroughSocketAtLegacyRates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        PortBatterySocketBlock block = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        var state = block.defaultBlockState().setValue(PortBatterySocketBlock.FACING, Direction.NORTH);
        level.setBlock(pos, state, 3);
        block.setPlacedBy(level, pos, state, null, new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get()));
        PortBatterySocketBlockEntity socket = (PortBatterySocketBlockEntity) level.getBlockEntity(pos);
        for (var registered : PortItems.BATTERY_PACKS) {
            PortBatteryItem battery = (PortBatteryItem) registered.get();
            ItemStack stack = new ItemStack(battery);
            socket.items().setStackInSlot(0, stack);
            PortPowerStorage source = new PortPowerStorage(battery.capacity());
            source.setPower(battery.capacity());
            long charged = PortPowerNetwork.transfer(
                    List.of(new PortPowerNetwork.Provider(source, battery.capacity())),
                    List.of(new PortPowerNetwork.Receiver(socket.reservoir(), socket.receiverSpeed(),
                            PortPowerNetwork.Priority.NORMAL)));
            if (charged != battery.chargeRate() || battery.charge(stack) != charged
                    || source.power() != battery.capacity() - charged) {
                throw new IllegalStateException("Socket charging rate differs for " + registered.getId());
            }
            battery.setCharge(stack, battery.capacity());
            PortPowerStorage target = new PortPowerStorage(battery.capacity());
            long discharged = PortPowerNetwork.transfer(
                    List.of(new PortPowerNetwork.Provider(socket.reservoir(), socket.providerSpeed())),
                    List.of(new PortPowerNetwork.Receiver(target, battery.capacity(),
                            PortPowerNetwork.Priority.NORMAL)));
            if (discharged != battery.dischargeRate() || target.power() != discharged
                    || battery.charge(stack) != battery.capacity() - discharged) {
                throw new IllegalStateException("Socket discharge rate differs for " + registered.getId());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySocketRenderBoundsAndPackUpdateTags(GameTestHelper helper) {
        BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = PortBlocks.MACHINE_BATTERY_SOCKET.get().defaultBlockState()
                    .setValue(PortBatterySocketBlock.FACING, facing);
            PortBatterySocketBlockEntity socket = new PortBatterySocketBlockEntity(core, state);
            BlockPos oppositeCorner = PortBatterySocketBlock.partPos(core, facing, 3);
            AABB bounds = socket.getRenderBoundingBox();
            if (bounds.minX != Math.min(core.getX(), oppositeCorner.getX())
                    || bounds.maxX != Math.max(core.getX(), oppositeCorner.getX()) + 1
                    || bounds.minY != core.getY() || bounds.maxY != core.getY() + 2
                    || bounds.minZ != Math.min(core.getZ(), oppositeCorner.getZ())
                    || bounds.maxZ != Math.max(core.getZ(), oppositeCorner.getZ()) + 1) {
                throw new IllegalStateException("Socket renderer is culled before its entire footprint: " + facing);
            }
            for (var pack : PortItems.BATTERY_PACKS) {
                ItemStack installed = new ItemStack(pack.get());
                ((PortBatteryItem) installed.getItem()).setCharge(installed, 12345L);
                socket.items().setStackInSlot(0, installed);
                PortBatterySocketBlockEntity clientCopy = new PortBatterySocketBlockEntity(core, state);
                clientCopy.load(socket.getUpdateTag());
                ItemStack received = clientCopy.items().getStackInSlot(0);
                if (!received.is(pack.get())
                        || ((PortBatteryItem) received.getItem()).charge(received) != 12345L) {
                    throw new IllegalStateException("Socket update tag omitted installed pack: " + pack.getId());
                }
            }
        }
        helper.succeed();
    }

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
    public static void batterySocketItemPlacementRejectsBlockedFootprint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (!PortBlocks.MACHINE_BATTERY_SOCKET.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)
                || !PortBlocks.RED_CABLE_CLASSIC.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            throw new IllegalStateException("Socket or classic cable has no survival pickaxe mining tag");
        }
        BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos floor = core.below();
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        var player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(core.getX() + 0.5D, core.getY(), core.getZ() + 2.5D);
        ItemStack held = new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        PortBatterySocketBlock socket = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos blocked = PortBatterySocketBlock.partPos(core, facing, 3);
        level.setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
        if (socket.getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(context)) != null
                || held.useOn(context).consumesAction() || !level.getBlockState(core).isAir()
                || held.getCount() != 2) {
            throw new IllegalStateException("Socket item placed despite an obstructed 2x2 footprint");
        }
        level.setBlock(blocked, Blocks.AIR.defaultBlockState(), 3);
        InteractionResult placed = held.useOn(context);
        if (!placed.consumesAction()) {
            throw new IllegalStateException("Socket item could not place on an empty footprint: " + placed);
        }
        for (int part = 0; part < 4; part++) {
            BlockPos pos = PortBatterySocketBlock.partPos(core, facing, part);
            if (!level.getBlockState(pos).is(socket)
                    || level.getBlockState(pos).getValue(PortBatterySocketBlock.PART) != part) {
                throw new IllegalStateException("Socket item placement missed footprint part " + part);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySocketFormsAndBreaksAsOneMachine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        PortBatterySocketBlock block = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        var coreState = block.defaultBlockState().setValue(PortBatterySocketBlock.FACING, Direction.NORTH);
        level.setBlock(core, coreState, 3);
        block.setPlacedBy(level, core, coreState, null, new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get()));
        if (!(level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket)) {
            throw new IllegalStateException("Battery socket core block entity was not created");
        }
        for (int part = 0; part < 4; part++) {
            BlockPos pos = PortBatterySocketBlock.partPos(core, Direction.NORTH, part);
            var state = level.getBlockState(pos);
            if (!state.is(block) || state.getValue(PortBatterySocketBlock.PART) != part
                    || !PortBatterySocketBlock.corePos(pos, state).equals(core)
                    || (part != 0 && !(level.getBlockEntity(pos) instanceof PortBatterySocketProxyBlockEntity))) {
                throw new IllegalStateException("Battery socket 2x2 footprint or core mapping is wrong");
            }
        }
        ItemStack cube = new ItemStack(PortItems.CUBE_POWER.get());
        ((PortBatteryItem) cube.getItem()).setCharge(cube, 123);
        socket.items().setStackInSlot(0, cube);
        if (socket.reservoir().power() != 123 || socket.reservoir().capacity() != 1_000_000_000_000_000_000L
                || socket.comparatorPower() != 0) {
            throw new IllegalStateException("Socket did not read installed battery HE or comparator state");
        }
        BlockPos proxy = PortBatterySocketBlock.partPos(core, Direction.NORTH, 3);
        var drops = Block.getDrops(level.getBlockState(proxy), level, proxy, null,
                FakePlayerFactory.getMinecraft(level), new ItemStack(Items.IRON_PICKAXE));
        if (drops.size() != 1 || !drops.get(0).is(PortItems.MACHINE_BATTERY_SOCKET.get())) {
            throw new IllegalStateException("Breaking a socket proxy would not return one machine item");
        }
        level.setBlock(proxy, Blocks.AIR.defaultBlockState(), 3);
        for (int part = 0; part < 4; part++) {
            if (!level.getBlockState(PortBatterySocketBlock.partPos(core, Direction.NORTH, part)).isAir()) {
                throw new IllegalStateException("Breaking a socket proxy left orphan multiblock parts");
            }
        }
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(core).inflate(2));
        if (dropped.stream().filter(entity -> entity.getItem().is(PortItems.CUBE_POWER.get())).count() != 1) {
            throw new IllegalStateException("Socket item was lost or duplicated on proxy break");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySocketProxiesShareAutomationSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        PortBatterySocketBlock block = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        var state = block.defaultBlockState().setValue(PortBatterySocketBlock.FACING, Direction.NORTH);
        level.setBlock(core, state, 3);
        block.setPlacedBy(level, core, state, null, new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get()));
        PortBatterySocketBlockEntity socket = (PortBatterySocketBlockEntity) level.getBlockEntity(core);
        var first = level.getBlockEntity(PortBatterySocketBlock.partPos(core, Direction.NORTH, 1))
                .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
        var second = level.getBlockEntity(PortBatterySocketBlock.partPos(core, Direction.NORTH, 2))
                .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).resolve().orElseThrow();
        var third = level.getBlockEntity(PortBatterySocketBlock.partPos(core, Direction.NORTH, 3))
                .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).resolve().orElseThrow();
        if (first.getSlots() != 1 || second.getSlots() != 1 || third.getSlots() != 1
                || first.isItemValid(0, new ItemStack(Items.DIRT))
                || first.insertItem(0, new ItemStack(Items.DIRT), false).isEmpty()) {
            throw new IllegalStateException("Socket proxy exposes invalid or duplicated inventory slots");
        }
        ItemStack cube = new ItemStack(PortItems.CUBE_POWER.get());
        PortBatteryItem battery = (PortBatteryItem) cube.getItem();
        battery.setCharge(cube, 0);
        if (!first.insertItem(0, cube, false).isEmpty()
                || !second.getStackInSlot(0).is(PortItems.CUBE_POWER.get())
                || !third.insertItem(0, new ItemStack(PortItems.BATTERY_POTATO.get()), true)
                        .is(PortItems.BATTERY_POTATO.get())) {
            throw new IllegalStateException("Proxy insertion did not reach the one core slot");
        }
        ItemStack installed = socket.items().getStackInSlot(0);
        battery.setCharge(installed, 500);
        if (!second.extractItem(0, 1, true).isEmpty()) {
            throw new IllegalStateException("Proxy automation extracted a partially charged battery");
        }
        battery.setCharge(installed, battery.capacity());
        if (!third.extractItem(0, 1, false).is(PortItems.CUBE_POWER.get())
                || !socket.items().getStackInSlot(0).isEmpty()) {
            throw new IllegalStateException("Proxy extraction did not empty the one core slot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySocketChargesItemThroughCable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos cablePos = sourcePos.east();
        BlockPos core = cablePos.east();
        PortBatterySocketBlock block = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        var coreState = block.defaultBlockState().setValue(PortBatterySocketBlock.FACING, Direction.NORTH);
        level.setBlock(core, coreState, 3);
        block.setPlacedBy(level, core, coreState, null, new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get()));
        level.setBlock(sourcePos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        PortCableBlock cable = (PortCableBlock) PortBlocks.RED_CABLE_CLASSIC.get();
        level.setBlock(cablePos, cable.connectedState(level, cablePos), 3);
        level.setBlock(sourcePos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        PortBatteryBlockEntity source = (PortBatteryBlockEntity) level.getBlockEntity(sourcePos);
        PortBatterySocketBlockEntity socket = (PortBatterySocketBlockEntity) level.getBlockEntity(core);
        ItemStack cube = new ItemStack(PortItems.CUBE_POWER.get());
        ((PortBatteryItem) cube.getItem()).setCharge(cube, 0);
        socket.items().setStackInSlot(0, cube);
        source.setPower(5_000);
        PortBatteryBlockEntity.tick(level, sourcePos, level.getBlockState(sourcePos), source);
        if (source.power() != 3_334 || socket.reservoir().power() != 1_666) {
            throw new IllegalStateException("Socket item did not accept HE from the classic cable");
        }
        PortBatterySocketBlockEntity.tick(level, core, level.getBlockState(core), socket);
        if (source.power() != 3_334 || socket.reservoir().power() != 1_666) {
            throw new IllegalStateException("Socket transferred HE twice when it ticked after the source");
        }
        var sided = socket.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
        if (sided.getSlots() != 1 || !sided.extractItem(0, 1, true).isEmpty()) {
            throw new IllegalStateException("Automation removed a partially charged socket battery");
        }
        ((PortBatteryItem) cube.getItem()).setCharge(cube, ((PortBatteryItem) cube.getItem()).capacity());
        if (!sided.extractItem(0, 1, true).is(PortItems.CUBE_POWER.get())) {
            throw new IllegalStateException("Automation could not remove a full socket battery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySocketDischargesItemToLegacyBattery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(new BlockPos(3, 2, 3));
        PortBatterySocketBlock block = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        var coreState = block.defaultBlockState().setValue(PortBatterySocketBlock.FACING, Direction.NORTH);
        level.setBlock(core, coreState, 3);
        block.setPlacedBy(level, core, coreState, null, new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get()));
        BlockPos port = PortBatterySocketBlock.partPos(core, Direction.NORTH, 2);
        BlockPos targetPos = port.east();
        level.setBlock(targetPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
        PortBatterySocketBlockEntity socket = (PortBatterySocketBlockEntity) level.getBlockEntity(core);
        PortBatteryBlockEntity target = (PortBatteryBlockEntity) level.getBlockEntity(targetPos);
        ItemStack cube = new ItemStack(PortItems.CUBE_POWER.get());
        ((PortBatteryItem) cube.getItem()).setCharge(cube, 4_000);
        socket.items().setStackInSlot(0, cube);
        socket.setModes(PortBatteryBlockEntity.OUTPUT, PortBatteryBlockEntity.OUTPUT);
        PortBatterySocketBlockEntity.tick(level, core, level.getBlockState(core), socket);
        if (socket.reservoir().power() != 0 || target.power() != 4_000) {
            throw new IllegalStateException("Socket proxy did not output HE from the installed item");
        }
        var fake = FakePlayerFactory.getMinecraft(level);
        fake.moveTo(core.getX() + 0.5D, core.getY() + 0.5D, core.getZ() + 0.5D);
        PortBatterySocketMenu menu = new PortBatterySocketMenu(2, fake.getInventory(), socket);
        if (!menu.stillValid(fake) || menu.power() != 0 || menu.capacity() != 1_000_000_000_000_000_000L
                || !menu.clickMenuButton(fake, 0) || !menu.clickMenuButton(fake, 1)
                || !menu.clickMenuButton(fake, 2) || menu.clickMenuButton(fake, 99)
                || socket.lowMode() != PortBatteryBlockEntity.DISABLED
                || socket.highMode() != PortBatteryBlockEntity.DISABLED
                || socket.priority() != PortPowerNetwork.Priority.NORMAL) {
            throw new IllegalStateException("Socket menu controls or 64-bit item HE data failed");
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

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void batterySocketPersistsAcrossChunkBorderAndServerRestart(GameTestHelper helper) {
        String phase = System.getProperty("hbm.port.persistence.phase", "");
        if (phase.isEmpty()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos core = new BlockPos(527, 80, 560);
        BlockPos proxy = core.east();
        BlockPos targetPos = proxy.east();
        level.getChunkAt(core);
        level.getChunkAt(proxy);
        if (core.getX() >> 4 == proxy.getX() >> 4) {
            throw new IllegalStateException("Socket restart fixture does not cross a chunk border");
        }
        PortBatterySocketBlock block = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        if (phase.equals("seed")) {
            var state = block.defaultBlockState().setValue(PortBatterySocketBlock.FACING, Direction.NORTH);
            level.setBlock(core, state, 3);
            block.setPlacedBy(level, core, state, null, new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get()));
            level.setBlock(targetPos, PortBlocks.MACHINE_BATTERY.get().defaultBlockState(), 3);
            PortBatterySocketBlockEntity socket = (PortBatterySocketBlockEntity) level.getBlockEntity(core);
            ItemStack cube = new ItemStack(PortItems.CUBE_POWER.get());
            ((PortBatteryItem) cube.getItem()).setCharge(cube, 3_456);
            socket.items().setStackInSlot(0, cube);
            socket.setModes(PortBatteryBlockEntity.DISABLED, PortBatteryBlockEntity.DISABLED);
            socket.setPriority(PortPowerNetwork.Priority.HIGH);
        } else if (phase.equals("verify")) {
            if (!(level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket)
                    || !(level.getBlockEntity(targetPos) instanceof PortBatteryBlockEntity target)
                    || !level.getBlockState(proxy).is(block)
                    || level.getBlockState(proxy).getValue(PortBatterySocketBlock.PART) != 2
                    || socket.reservoir().power() != 3_456
                    || socket.reservoir().capacity() != 1_000_000_000_000_000_000L
                    || socket.lowMode() != PortBatteryBlockEntity.DISABLED
                    || socket.priority() != PortPowerNetwork.Priority.HIGH
                    || target.power() != 0) {
                throw new IllegalStateException("Cross-chunk socket footprint item HE or controls failed restart");
            }
            var proxyItems = level.getBlockEntity(proxy)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).resolve().orElseThrow();
            if (proxyItems.getSlots() != 1
                    || !proxyItems.getStackInSlot(0).is(PortItems.CUBE_POWER.get())) {
                throw new IllegalStateException("Cross-chunk socket proxy lost core item automation on reload");
            }
            socket.setModes(PortBatteryBlockEntity.OUTPUT, PortBatteryBlockEntity.OUTPUT);
            PortBatterySocketBlockEntity.tick(level, core, level.getBlockState(core), socket);
            if (socket.reservoir().power() != 0 || target.power() != 3_456) {
                throw new IllegalStateException("Reloaded socket failed to output item HE across its proxy");
            }
            level.setBlock(proxy, Blocks.AIR.defaultBlockState(), 3);
            if (proxyItems.getSlots() != 0) {
                throw new IllegalStateException("Removed socket proxy retained access to its former core");
            }
            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
            for (int part = 0; part < 4; part++) {
                if (!level.getBlockState(PortBatterySocketBlock.partPos(core, Direction.NORTH, part)).isAir()) {
                    throw new IllegalStateException("Cross-chunk socket breakup left an orphan part");
                }
            }
        } else {
            throw new IllegalStateException("Unknown socket persistence phase: " + phase);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void quantumBatteryPackPersistsInSocketAcrossServerRestart(GameTestHelper helper) {
        String phase = System.getProperty("hbm.port.persistence.phase", "");
        if (phase.isEmpty()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos core = new BlockPos(560, 80, 560);
        level.getChunkAt(core);
        PortBatterySocketBlock block = (PortBatterySocketBlock) PortBlocks.MACHINE_BATTERY_SOCKET.get();
        if (phase.equals("seed")) {
            var state = block.defaultBlockState().setValue(PortBatterySocketBlock.FACING, Direction.NORTH);
            level.setBlock(core, state, 3);
            block.setPlacedBy(level, core, state, null, new ItemStack(PortItems.MACHINE_BATTERY_SOCKET.get()));
            PortBatterySocketBlockEntity socket = (PortBatterySocketBlockEntity) level.getBlockEntity(core);
            ItemStack pack = new ItemStack(PortItems.BATTERY_PACK_QUANTUM.get());
            ((PortBatteryItem) pack.getItem()).setCharge(pack, 7_654_321L);
            socket.items().setStackInSlot(0, pack);
            socket.setModes(PortBatteryBlockEntity.DISABLED, PortBatteryBlockEntity.DISABLED);
        } else if (phase.equals("verify")) {
            if (!(level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket)
                    || !socket.items().getStackInSlot(0).is(PortItems.BATTERY_PACK_QUANTUM.get())
                    || socket.reservoir().power() != 7_654_321L
                    || socket.reservoir().capacity() != 72_000_000_000L
                    || socket.lowMode() != PortBatteryBlockEntity.DISABLED) {
                throw new IllegalStateException("Named quantum pack lost charge or socket state on restart");
            }
            level.setBlock(core, Blocks.AIR.defaultBlockState(), 3);
        } else {
            throw new IllegalStateException("Unknown battery pack persistence phase: " + phase);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insulatorCraftingMatchesLegacy(GameTestHelper helper) {
        String[] recipes = {
                "plate_polymer_from_wool", "plate_polymer_from_brick", "plate_polymer_from_nether_brick"
        };
        Item[][] inputs = {
                {Items.STRING, Items.WHITE_WOOL, Items.STRING},
                {Items.BRICK, Items.BRICK},
                {Items.NETHER_BRICK, Items.NETHER_BRICK}
        };
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var menu = player.inventoryMenu;
        for (int i = 0; i < recipes.length; i++) {
            var id = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, recipes[i]);
            var recipe = level.getRecipeManager().byKey(id)
                    .orElseThrow(() -> new IllegalStateException("Missing insulator recipe: " + id));
            if (!(recipe instanceof ShapedRecipe shaped)) {
                throw new IllegalStateException("Insulator recipe is not shaped: " + id);
            }
            var unlock = level.getServer().getAdvancements().getAdvancement(
                    ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "recipes/misc/" + recipes[i]));
            if (unlock == null || !Set.of(unlock.getRewards().getRecipes()).contains(id)) {
                throw new IllegalStateException("Insulator recipe-book unlock is missing: " + id);
            }
            var grid = new TransientCraftingContainer(menu, 3, 3);
            for (int slot = 0; slot < inputs[i].length; slot++) {
                grid.setItem(slot, new ItemStack(inputs[i][slot]));
            }
            ItemStack result = shaped.assemble(grid, level.registryAccess());
            if (!shaped.matches(grid, level) || !result.is(PortItems.PLATE_POLYMER.get())
                    || result.getCount() != 4) {
                throw new IllegalStateException("Insulator recipe inputs or yield differ from legacy: " + id);
            }
            grid.setItem(1, new ItemStack(Items.DIRT));
            if (shaped.matches(grid, level)) {
                throw new IllegalStateException("Insulator recipe accepts an invalid material: " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void asbestosBlockAndPowderRecipesMatchLegacy(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var manager = level.getRecipeManager();
        var blockId = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "block_asbestos");
        var blockRecipe = manager.byKey(blockId)
                .orElseThrow(() -> new IllegalStateException("Missing nine-sheet asbestos block recipe"));
        var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        for (int slot = 0; slot < 9; slot++) grid.setItem(slot, new ItemStack(PortItems.INGOT_ASBESTOS.get()));
        if (!(blockRecipe instanceof ShapedRecipe shapedBlock) || !shapedBlock.matches(grid, level)
                || !shapedBlock.assemble(grid, level.registryAccess()).is(PortItems.BLOCK_ASBESTOS.get())) {
            throw new IllegalStateException("Nine asbestos sheets did not craft a compacted block");
        }
        grid.setItem(8, new ItemStack(Items.DIRT));
        if (shapedBlock.matches(grid, level)) {
            throw new IllegalStateException("Asbestos block recipe accepted a non-sheet input");
        }

        var sheetId = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "ingot_asbestos_from_block");
        var sheetRecipe = manager.byKey(sheetId)
                .orElseThrow(() -> new IllegalStateException("Missing asbestos block unpacking recipe"));
        var unpackGrid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        unpackGrid.setItem(0, new ItemStack(PortItems.BLOCK_ASBESTOS.get()));
        if (!(sheetRecipe instanceof ShapedRecipe shapedSheet) || !shapedSheet.matches(unpackGrid, level)
                || !shapedSheet.assemble(unpackGrid, level.registryAccess()).is(PortItems.INGOT_ASBESTOS.get())
                || shapedSheet.assemble(unpackGrid, level.registryAccess()).getCount() != 9) {
            throw new IllegalStateException("One asbestos block did not unpack to nine sheets");
        }
        for (var id : new ResourceLocation[] {blockId, sheetId}) {
            var unlock = level.getServer().getAdvancements().getAdvancement(
                    ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "recipes/misc/" + id.getPath()));
            if (unlock == null || !Set.of(unlock.getRewards().getRecipes()).contains(id)) {
                throw new IllegalStateException("Asbestos recipe-book unlock missing for " + id);
            }
        }
        var smelting = manager.getRecipeFor(RecipeType.SMELTING,
                new SimpleContainer(new ItemStack(PortItems.POWDER_ASBESTOS.get())), level)
                .orElseThrow(() -> new IllegalStateException("Asbestos powder smelting missing"));
        if (!smelting.getResultItem(level.registryAccess()).is(PortItems.INGOT_ASBESTOS.get())
                || smelting.getExperience() != 1.0F || smelting.getCookingTime() != 200) {
            throw new IllegalStateException("Asbestos powder smelting differs from legacy");
        }
        var smeltId = ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "ingot_asbestos_from_powder");
        var smeltUnlock = level.getServer().getAdvancements().getAdvancement(
                ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "recipes/misc/ingot_asbestos_from_powder"));
        if (smeltUnlock == null || !Set.of(smeltUnlock.getRewards().getRecipes()).contains(smeltId)) {
            throw new IllegalStateException("Asbestos powder smelting recipe-book unlock missing");
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
    public static void rareLigniteCinnabarCobaltDropsAndUses(GameTestHelper helper) {
        Block[] ores = {PortBlocks.ORE_RARE.get(), PortBlocks.ORE_LIGNITE.get(),
                PortBlocks.ORE_CINNEBAR.get(), PortBlocks.ORE_COBALT.get()};
        Item[] oreItems = {PortItems.ORE_RARE.get(), PortItems.ORE_LIGNITE.get(),
                PortItems.ORE_CINNEBAR.get(), PortItems.ORE_COBALT.get()};
        Item[] products = {PortItems.CHUNK_ORE_RARE.get(), PortItems.LIGNITE.get(),
                PortItems.CINNEBAR.get(), PortItems.FRAGMENT_COBALT.get()};
        int[] minimum = {1, 1, 1, 4};
        int[] maximum = {1, 1, 1, 9};
        BlockPos orePos = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemStack silkPick = new ItemStack(Items.IRON_PICKAXE);
        silkPick.enchant(Enchantments.SILK_TOUCH, 1);
        for (int i = 0; i < ores.length; i++) {
            var id = ForgeRegistries.BLOCKS.getKey(ores[i]);
            if (id == null || ForgeRegistries.ITEMS.getValue(id) != oreItems[i]
                    || !helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(id)) {
                throw new IllegalStateException("Ore item or placed feature missing for " + id);
            }
            helper.getLevel().setBlock(orePos, ores[i].defaultBlockState(), 3);
            var drops = Block.getDrops(ores[i].defaultBlockState(), helper.getLevel(), orePos,
                    null, FakePlayerFactory.getMinecraft(helper.getLevel()), new ItemStack(Items.IRON_PICKAXE));
            if (drops.size() != 1 || !drops.get(0).is(products[i])
                    || drops.get(0).getCount() < minimum[i] || drops.get(0).getCount() > maximum[i]) {
                throw new IllegalStateException("Legacy ore yield differs for " + id);
            }
            var silkDrops = Block.getDrops(ores[i].defaultBlockState(), helper.getLevel(), orePos,
                    null, FakePlayerFactory.getMinecraft(helper.getLevel()), silkPick);
            if (silkDrops.size() != 1 || !silkDrops.get(0).is(oreItems[i]) || silkDrops.get(0).getCount() != 1) {
                throw new IllegalStateException("Silk Touch ore yield differs for " + id);
            }
        }
        if (PortItems.LIGNITE.get().getBurnTime(new ItemStack(PortItems.LIGNITE.get()), RecipeType.SMELTING) != 1200) {
            throw new IllegalStateException("Lignite fuel time differs from legacy");
        }
        if (PortItems.ORE_RARE.get().getRarity(new ItemStack(PortItems.ORE_RARE.get())) != Rarity.UNCOMMON) {
            throw new IllegalStateException("Rare earth ore item rarity differs from legacy");
        }
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SimpleContainer(new ItemStack(PortItems.ORE_COBALT.get())), helper.getLevel())
                .orElseThrow(() -> new IllegalStateException("Cobalt smelting recipe missing"));
        if (!recipe.getResultItem(helper.getLevel().registryAccess()).is(PortItems.INGOT_COBALT.get())
                || recipe.getExperience() != 2.0F || recipe.getCookingTime() != 200) {
            throw new IllegalStateException("Cobalt smelting differs from legacy");
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
    public static void asbestosOreDropsAndOutgasses(GameTestHelper helper) {
        BlockPos orePos = new BlockPos(1, 2, 1);
        helper.setBlock(orePos, PortBlocks.ORE_ASBESTOS.get());
        var level = helper.getLevel();
        var state = helper.getBlockState(orePos);
        var id = PortBlocks.ORE_ASBESTOS.getId();
        if (ForgeRegistries.ITEMS.getValue(id) != PortItems.ORE_ASBESTOS.get()
                || !level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(id)) {
            throw new IllegalStateException("Asbestos ore item or placed feature is missing");
        }
        BlockPos absolute = helper.absolutePos(orePos);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        var player = FakePlayerFactory.getMinecraft(level);
        var drops = Block.getDrops(state, level, absolute, null, player, pick);
        if (drops.size() != 1 || !drops.get(0).is(PortItems.INGOT_ASBESTOS.get())
                || drops.get(0).getCount() != 1) {
            throw new IllegalStateException("Asbestos ore did not drop one source-equivalent sheet");
        }
        ItemStack silkPick = new ItemStack(Items.IRON_PICKAXE);
        silkPick.enchant(Enchantments.SILK_TOUCH, 1);
        var silkDrops = Block.getDrops(state, level, absolute, null, player, silkPick);
        if (silkDrops.size() != 1 || !silkDrops.get(0).is(PortItems.ORE_ASBESTOS.get())) {
            throw new IllegalStateException("Silk Touch did not preserve asbestos ore");
        }
        for (int i = 0; i < 32; i++) {
            PortBlocks.ORE_ASBESTOS.get().randomTick(state, level, absolute, RandomSource.create(i));
        }
        boolean found = false;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(absolute.relative(direction)).is(PortBlocks.GAS_ASBESTOS.get())) {
                found = true;
            }
        }
        if (!found) throw new IllegalStateException("Asbestos ore did not emit airborne particles");
        level.setBlock(absolute, Blocks.AIR.defaultBlockState(), 3);
        PortBlocks.ORE_ASBESTOS.get().playerDestroy(level, player, absolute, state, null, pick);
        if (!level.getBlockState(absolute).is(PortBlocks.GAS_ASBESTOS.get())) {
            throw new IllegalStateException("Breaking asbestos ore did not release airborne particles");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void asbestosGasExposureAndFilter(GameTestHelper helper) {
        BlockPos gasPos = new BlockPos(1, 2, 1);
        helper.setBlock(gasPos, PortBlocks.GAS_ASBESTOS.get());
        var level = helper.getLevel();
        var gas = PortBlocks.GAS_ASBESTOS.get();
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, gasPos);
        gas.entityInside(helper.getBlockState(gasPos), level, helper.absolutePos(gasPos), cow);
        var exposure = PortRadiation.get(cow).orElseThrow(() ->
                new IllegalStateException("Cow missing HBM exposure capability"));
        if (exposure.asbestos() != 1 || exposure.radiation() != 0.0F) {
            throw new IllegalStateException("Airborne asbestos did not apply asbestos-only exposure");
        }
        ItemStack mask = new ItemStack(PortItems.GAS_MASK_OLDE.get());
        PortGasMask.installFilter(mask, new ItemStack(PortItems.GAS_MASK_FILTER_RAG.get()));
        cow.setItemSlot(EquipmentSlot.HEAD, mask);
        gas.entityInside(helper.getBlockState(gasPos), level, helper.absolutePos(gasPos), cow);
        if (exposure.asbestos() != 2 || PortGasMask.filter(mask).getDamageValue() != 0) {
            throw new IllegalStateException("Coarse-only rag filter incorrectly blocked fine asbestos");
        }
        PortGasMask.installFilter(mask, new ItemStack(PortItems.GAS_MASK_FILTER.get()));
        gas.entityInside(helper.getBlockState(gasPos), level, helper.absolutePos(gasPos), cow);
        if (exposure.asbestos() != 2 || PortGasMask.filter(mask).getDamageValue() != 1) {
            throw new IllegalStateException("Fine filter failed to block or wear against asbestos gas");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void asbestosGasDriftsAndDecays(GameTestHelper helper) {
        BlockPos gasPos = new BlockPos(1, 2, 1);
        helper.setBlock(gasPos.below(), Blocks.AIR);
        helper.setBlock(gasPos, PortBlocks.GAS_ASBESTOS.get());
        long moveSeed = 0;
        while (true) {
            RandomSource candidate = RandomSource.create(moveSeed);
            if (candidate.nextInt(50) != 0 && candidate.nextInt(5) == 0) break;
            moveSeed++;
        }
        var gas = PortBlocks.GAS_ASBESTOS.get();
        var level = helper.getLevel();
        gas.tick(helper.getBlockState(gasPos), level, helper.absolutePos(gasPos),
                RandomSource.create(moveSeed));
        helper.assertBlockPresent(gas, gasPos.below());
        long decaySeed = 0;
        while (RandomSource.create(decaySeed).nextInt(50) != 0) decaySeed++;
        BlockPos destination = gasPos.below();
        gas.tick(helper.getBlockState(destination), level, helper.absolutePos(destination),
                RandomSource.create(decaySeed));
        helper.assertBlockPresent(Blocks.AIR, destination);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compactedAsbestosDropsAndOutgasses(GameTestHelper helper) {
        BlockPos blockPos = new BlockPos(1, 2, 1);
        helper.setBlock(blockPos, PortBlocks.BLOCK_ASBESTOS.get());
        var level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(blockPos);
        var state = helper.getBlockState(blockPos);
        var blockId = PortBlocks.BLOCK_ASBESTOS.getId();
        if (ForgeRegistries.ITEMS.getValue(blockId) != PortItems.BLOCK_ASBESTOS.get()) {
            throw new IllegalStateException("Compacted asbestos block item is missing");
        }
        var drops = Block.getDrops(state, level, absolute, null,
                FakePlayerFactory.getMinecraft(level), ItemStack.EMPTY);
        if (drops.size() != 1 || !drops.get(0).is(PortItems.BLOCK_ASBESTOS.get())) {
            throw new IllegalStateException("Compacted asbestos did not drop itself");
        }
        for (int i = 0; i < 32; i++) {
            PortBlocks.BLOCK_ASBESTOS.get().randomTick(state, level, absolute, RandomSource.create(i));
        }
        boolean foundGas = false;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(absolute.relative(direction)).is(PortBlocks.GAS_ASBESTOS.get())) {
                foundGas = true;
            }
        }
        if (!foundGas) throw new IllegalStateException("Compacted asbestos did not outgas");
        level.setBlock(absolute, Blocks.AIR.defaultBlockState(), 3);
        PortBlocks.BLOCK_ASBESTOS.get().playerDestroy(level, FakePlayerFactory.getMinecraft(level),
                absolute, state, null, ItemStack.EMPTY);
        if (!level.getBlockState(absolute).is(PortBlocks.GAS_ASBESTOS.get())) {
            throw new IllegalStateException("Breaking compacted asbestos did not leave airborne particles");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void carriedAsbestosDoseAndLethalThreshold(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "hbm_asbestos"));
        player.getInventory().items.set(0, new ItemStack(PortItems.INGOT_ASBESTOS.get(), 64));
        player.getInventory().items.set(1, new ItemStack(PortItems.POWDER_ASBESTOS.get()));
        player.getInventory().items.set(2, new ItemStack(PortItems.ORE_ASBESTOS.get()));
        player.getInventory().items.set(3, new ItemStack(PortItems.BLOCK_ASBESTOS.get()));
        var exposure = PortRadiation.get(player)
                .orElseThrow(() -> new IllegalStateException("Test player missing exposure capability"));
        if (PortAsbestosInventoryHazard.dose(player.getInventory().items.get(0)) != 1
                || PortAsbestosInventoryHazard.dose(player.getInventory().items.get(1)) != 3
                || PortAsbestosInventoryHazard.dose(player.getInventory().items.get(2)) != 1
                || PortAsbestosInventoryHazard.dose(player.getInventory().items.get(3)) != 10) {
            throw new IllegalStateException("Asbestos material hazard multipliers differ from source");
        }
        PortAsbestosInventoryHazard.tickInventory(player);
        if (exposure.asbestos() != 15) {
            throw new IllegalStateException("Inventory asbestos did not apply one dose per occupied slot");
        }
        ItemStack mask = new ItemStack(PortItems.GAS_MASK_OLDE.get());
        PortGasMask.installFilter(mask, new ItemStack(PortItems.GAS_MASK_FILTER.get()));
        player.setItemSlot(EquipmentSlot.HEAD, mask);
        PortAsbestosInventoryHazard.tickInventory(player);
        if (exposure.asbestos() != 15 || PortGasMask.filter(mask).getDamageValue() != 15) {
            throw new IllegalStateException("Fine filter failed to block inventory asbestos or wear by dose");
        }
        PortGasMask.installFilter(mask, new ItemStack(PortItems.GAS_MASK_FILTER_RAG.get()));
        PortAsbestosInventoryHazard.tickInventory(player);
        if (exposure.asbestos() != 30 || PortGasMask.filter(mask).getDamageValue() != 0) {
            throw new IllegalStateException("Coarse rag filter incorrectly blocked inventory asbestos");
        }
        for (int i = 0; i < 4; i++) player.getInventory().items.set(i, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

        var asbestosKey = ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(HbmPort.MOD_ID, "asbestos"));
        var damageType = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(asbestosKey);
        if (!damageType.is(DamageTypeTags.BYPASSES_ARMOR)
                || !damageType.is(DamageTypeTags.BYPASSES_EFFECTS)
                || !damageType.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || !damageType.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            throw new IllegalStateException("Lethal asbestos damage did not preserve absolute armor bypass");
        }
        var cow = EntityType.COW.create(level);
        if (cow == null) throw new IllegalStateException("Could not create asbestos threshold test cow");
        var cowExposure = PortRadiation.get(cow)
                .orElseThrow(() -> new IllegalStateException("Test cow missing exposure capability"));
        cowExposure.addAsbestos(PortRadiation.MAX_ASBESTOS - 1);
        PortRadiation.exposeAsbestos(cow);
        if (cow.isAlive() || cowExposure.asbestos() != 0) {
            throw new IllegalStateException("Lethal asbestos threshold did not reset exposure and kill");
        }
        var radonCow = EntityType.COW.create(level);
        if (radonCow == null) throw new IllegalStateException("Could not create radon threshold test cow");
        var radonExposure = PortRadiation.get(radonCow)
                .orElseThrow(() -> new IllegalStateException("Radon test cow missing exposure capability"));
        radonExposure.addAsbestos(PortRadiation.MAX_ASBESTOS - 1);
        PortRadiation.exposeRadon(radonCow);
        if (radonCow.isAlive() || radonExposure.asbestos() != 0) {
            throw new IllegalStateException("Radon asbestos dose bypassed the lethal threshold");
        }
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
                PortBlocks.ORE_ALUMINIUM.get(), PortBlocks.ORE_RARE.get(), PortBlocks.ORE_LIGNITE.get(),
                PortBlocks.ORE_CINNEBAR.get(), PortBlocks.ORE_COBALT.get()
        };
        int[] found = new int[ores.length];
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int sample = 0; sample < 8; sample++) {
            int baseChunkX = (origin.getX() >> 4) + ThreadLocalRandom.current().nextInt(256, 8192);
            int baseChunkZ = (origin.getZ() >> 4) + ThreadLocalRandom.current().nextInt(256, 8192);
            for (int chunkX = baseChunkX; chunkX < baseChunkX + 2; chunkX++) {
                for (int chunkZ = baseChunkZ; chunkZ < baseChunkZ + 2; chunkZ++) {
                    level.getChunk(chunkX, chunkZ);
                    for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                        for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                            for (int y = 4; y <= 59; y++) {
                                var state = level.getBlockState(new BlockPos(x, y, z));
                                for (int i = 0; i < ores.length; i++) {
                                    if (state.is(ores[i])) found[i]++;
                                }
                            }
                        }
                    }
                }
            }
            boolean allPresent = true;
            for (int count : found) if (count == 0) allPresent = false;
            if (allPresent) break;
        }
        for (int i = 0; i < ores.length; i++) {
            if (found[i] == 0) {
                throw new IllegalStateException("No " + ForgeRegistries.BLOCKS.getKey(ores[i])
                        + " across up to thirty-two fresh overworld chunks");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void asbestosOreGeneratesInFreshChunks(GameTestHelper helper) {
        if (!System.getProperty("hbm.port.ore.config.phase", "").isEmpty()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int count = 0;
        for (int sample = 0; sample < 4 && count == 0; sample++) {
            int chunkX = (origin.getX() >> 4) + ThreadLocalRandom.current().nextInt(40_000, 44_000);
            int chunkZ = (origin.getZ() >> 4) + ThreadLocalRandom.current().nextInt(40_000, 44_000);
            count += countOresInFreshChunks(level, chunkX, chunkZ,
                    new Block[] {PortBlocks.ORE_ASBESTOS.get()});
        }
        if (count == 0) {
            throw new IllegalStateException("No asbestos ore generated in up to sixteen fresh Overworld chunks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void oreSpawnSettingsControlFreshChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String phase = System.getProperty("hbm.port.ore.config.phase", "");
        for (String ore : new String[] {
                "uranium", "thorium", "titanium", "copper", "tungsten", "lead", "beryllium",
                "sulfur", "fluorite", "niter", "aluminium", "asbestos",
                "rare", "lignite", "cinnebar", "cobalt"
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
                PortBlocks.ORE_ALUMINIUM.get(), PortBlocks.ORE_ASBESTOS.get(),
                PortBlocks.ORE_RARE.get(), PortBlocks.ORE_LIGNITE.get(),
                PortBlocks.ORE_CINNEBAR.get(), PortBlocks.ORE_COBALT.get()
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
        } else if (phase.equals("copper-high")) {
            int chunkX = ThreadLocalRandom.current().nextInt(32_000, 36_000);
            int chunkZ = ThreadLocalRandom.current().nextInt(32_000, 36_000);
            if (!PortConfig.ENABLE_OVERWORLD_ORES.get() || PortConfig.ORE_COPPER_COUNT.get() != 48
                    || countOresInFreshChunks(level, chunkX, chunkZ,
                            new Block[] {PortBlocks.ORE_COPPER.get()}) == 0) {
                throw new IllegalStateException("Positive copper spawn count failed in fresh chunks");
            }
        } else if (phase.equals("asbestos-off")) {
            int chunkX = ThreadLocalRandom.current().nextInt(44_000, 48_000);
            int chunkZ = ThreadLocalRandom.current().nextInt(44_000, 48_000);
            if (!PortConfig.ENABLE_OVERWORLD_ORES.get() || PortConfig.ORE_ASBESTOS_COUNT.get() != 0
                    || countOresInFreshChunks(level, chunkX, chunkZ,
                            new Block[] {PortBlocks.ORE_ASBESTOS.get()}) != 0
                    || countOresInFreshChunks(level, chunkX, chunkZ,
                            new Block[] {PortBlocks.ORE_URANIUM.get()}) == 0) {
                throw new IllegalStateException("Asbestos-only disable setting failed in fresh chunks");
            }
        } else if (phase.equals("cobalt-off")) {
            int chunkX = ThreadLocalRandom.current().nextInt(50_000, 54_000);
            int chunkZ = ThreadLocalRandom.current().nextInt(50_000, 54_000);
            if (!PortConfig.ENABLE_OVERWORLD_ORES.get() || PortConfig.ORE_COBALT_COUNT.get() != 0
                    || countOresInFreshChunks(level, chunkX, chunkZ,
                            new Block[] {PortBlocks.ORE_COBALT.get()}) != 0
                    || countOresInFreshChunks(level, chunkX, chunkZ,
                            new Block[] {PortBlocks.ORE_URANIUM.get()}) == 0) {
                throw new IllegalStateException("Cobalt-only disable setting failed in fresh chunks");
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
                        for (int y = 4; y <= 59; y++) {
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
