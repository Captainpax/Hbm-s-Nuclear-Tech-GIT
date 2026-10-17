package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Server-owned HE, two item slots, modes, and classic-cable network transfer. */
public final class PortBatteryBlockEntity extends BlockEntity implements MenuProvider, PortPowerEndpoint {
    public static final short INPUT = 0;
    public static final short BUFFER = 1;
    public static final short OUTPUT = 2;
    public static final short DISABLED = 3;

    private final PortPowerStorage storage;
    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> items);
    private LazyOptional<IItemHandler> topCapability = LazyOptional.of(() -> new SidedItems(items, 0));
    private LazyOptional<IItemHandler> bottomCapability = LazyOptional.of(() -> new SidedItems(items, 0, 1));
    private LazyOptional<IItemHandler> sideCapability = LazyOptional.of(() -> new SidedItems(items, 1));
    private short redLow = INPUT;
    private short redHigh = OUTPUT;
    private PortPowerNetwork.Priority priority = PortPowerNetwork.Priority.LOW;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index < 4) return (int) ((storage.power() >>> (index * 16)) & 0xffff);
            return switch (index) {
                case 4 -> redLow;
                case 5 -> redHigh;
                case 6 -> priority.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // The server-owned block entity is never changed through client data slots.
        }

        @Override
        public int getCount() {
            return 7;
        }
    };

    public PortBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.BATTERY.get(), pos, state);
        long capacity = state.getBlock() instanceof PortBatteryBlock battery ? battery.capacity() : 1_000_000L;
        storage = new PortPowerStorage(capacity);
    }

    public long power() {
        return storage.power();
    }

    public long capacity() {
        return storage.capacity();
    }

    PortPowerStorage storage() {
        return storage;
    }

    @Override
    public BlockEntity blockEntity() {
        return this;
    }

    @Override
    public PortPowerReservoir reservoir() {
        return storage;
    }

    @Override
    public short mode(ServerLevel level) {
        return mode(level.hasNeighborSignal(worldPosition));
    }

    @Override
    public long providerSpeed() {
        return capacity() / 600;
    }

    @Override
    public long receiverSpeed() {
        return capacity() / 200;
    }

    @Override
    public void onPowerTransferred() {
        changed();
    }

    public ItemStackHandler items() {
        return items;
    }

    public ContainerData menuData() {
        return menuData;
    }

    public void setPower(long amount) {
        storage.setPower(amount);
        changed();
    }

    public void setModes(short low, short high) {
        redLow = validMode(low);
        redHigh = validMode(high);
        changed();
    }

    public short lowMode() {
        return redLow;
    }

    public short highMode() {
        return redHigh;
    }

    public PortPowerNetwork.Priority priority() {
        return priority;
    }

    public void setPriority(PortPowerNetwork.Priority value) {
        if (value != PortPowerNetwork.Priority.LOW && value != PortPowerNetwork.Priority.NORMAL
                && value != PortPowerNetwork.Priority.HIGH) {
            throw new IllegalArgumentException("Battery priority must be low, normal, or high");
        }
        priority = value;
        changed();
    }

    public short mode(boolean powered) {
        return powered ? redHigh : redLow;
    }

    public int comparatorPower() {
        if (storage.power() == 0) return 0;
        return Math.min(15, (int) ((double) storage.power() / storage.capacity() * 15D) + 1);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortBatteryBlockEntity battery) {
        battery.chargeItemFromStorage();
        if (level instanceof ServerLevel serverLevel) PortPowerGrid.tick(serverLevel, battery);
        battery.chargeStorageFromItem();
    }

    private void chargeItemFromStorage() {
        ItemStack stack = items.getStackInSlot(1);
        if (!(stack.getItem() instanceof PortBatteryItem battery)) return;
        long amount = Math.min(storage.power(), battery.chargeRate());
        amount = Math.min(amount, battery.capacity() - battery.charge(stack));
        if (amount <= 0) return;
        battery.setCharge(stack, battery.charge(stack) + storage.extract(amount));
        changed();
    }

    private void chargeStorageFromItem() {
        ItemStack stack = items.getStackInSlot(0);
        if (!(stack.getItem() instanceof PortBatteryItem battery)) return;
        long amount = Math.min(storage.capacity() - storage.power(), battery.dischargeRate());
        amount = Math.min(amount, battery.charge(stack));
        if (amount <= 0) return;
        battery.setCharge(stack, battery.charge(stack) - amount);
        storage.receive(amount);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static short validMode(short mode) {
        return mode >= INPUT && mode <= DISABLED ? mode : DISABLED;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.load(tag, "power");
        redLow = validMode(tag.getShort("redLow"));
        redHigh = tag.contains("redHigh") ? validMode(tag.getShort("redHigh")) : OUTPUT;
        priority = validPriority(tag.getInt("priority"));
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        storage.save(tag, "power");
        tag.putShort("redLow", redLow);
        tag.putShort("redHigh", redHigh);
        tag.putInt("priority", priority.ordinal());
        tag.put("items", items.serializeNBT());
    }

    public void savePersistent(CompoundTag itemTag) {
        CompoundTag data = new CompoundTag();
        storage.save(data, "power");
        data.putShort("redLow", redLow);
        data.putShort("redHigh", redHigh);
        data.putInt("priority", priority.ordinal());
        itemTag.put("persistent", data);
    }

    public void loadPersistent(CompoundTag itemTag) {
        if (!itemTag.contains("persistent", 10)) return;
        CompoundTag data = itemTag.getCompound("persistent");
        storage.load(data, "power");
        redLow = validMode(data.getShort("redLow"));
        redHigh = validMode(data.getShort("redHigh"));
        priority = validPriority(data.getInt("priority"));
        changed();
    }

    private static PortPowerNetwork.Priority validPriority(int ordinal) {
        return switch (ordinal) {
            case 2 -> PortPowerNetwork.Priority.NORMAL;
            case 3 -> PortPowerNetwork.Priority.HIGH;
            default -> PortPowerNetwork.Priority.LOW;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hbm.battery");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PortBatteryMenu(containerId, playerInventory, this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) {
            return (side == Direction.UP ? topCapability
                    : side == Direction.DOWN ? bottomCapability
                    : side == null ? itemCapability : sideCapability).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        topCapability.invalidate();
        bottomCapability.invalidate();
        sideCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> items);
        topCapability = LazyOptional.of(() -> new SidedItems(items, 0));
        bottomCapability = LazyOptional.of(() -> new SidedItems(items, 0, 1));
        sideCapability = LazyOptional.of(() -> new SidedItems(items, 1));
    }

    private record SidedItems(ItemStackHandler backing, int... slots) implements IItemHandler {
        @Override
        public int getSlots() {
            return slots.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return backing.getStackInSlot(slots[slot]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return backing.insertItem(slots[slot], stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int actualSlot = slots[slot];
            ItemStack stack = backing.getStackInSlot(actualSlot);
            if (!(stack.getItem() instanceof PortBatteryItem battery)) return ItemStack.EMPTY;
            long charge = battery.charge(stack);
            if ((actualSlot == 0 && charge != 0) || (actualSlot == 1 && charge != battery.capacity())) {
                return ItemStack.EMPTY;
            }
            return backing.extractItem(actualSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return backing.getSlotLimit(slots[slot]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return backing.isItemValid(slots[slot], stack);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
