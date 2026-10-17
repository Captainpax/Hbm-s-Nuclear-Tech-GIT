package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** One item-backed HE reservoir shared by all four socket ports. */
public final class PortBatterySocketBlockEntity extends BlockEntity implements MenuProvider, PortPowerEndpoint {
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof PortBatteryItem;
        }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> new SocketItems(items));
    private final PortPowerReservoir reservoir = new PortPowerReservoir() {
        @Override
        public long capacity() {
            ItemStack stack = items.getStackInSlot(0);
            return stack.getItem() instanceof PortBatteryItem battery ? battery.capacity() : 0;
        }

        @Override
        public long power() {
            ItemStack stack = items.getStackInSlot(0);
            return stack.getItem() instanceof PortBatteryItem battery ? battery.charge(stack) : 0;
        }

        @Override
        public long receive(long amount) {
            ItemStack stack = items.getStackInSlot(0);
            if (amount <= 0 || !(stack.getItem() instanceof PortBatteryItem battery)) return 0;
            long accepted = Math.min(amount, battery.capacity() - battery.charge(stack));
            if (accepted > 0) {
                battery.setCharge(stack, battery.charge(stack) + accepted);
                setChanged();
            }
            return accepted;
        }

        @Override
        public long extract(long amount) {
            ItemStack stack = items.getStackInSlot(0);
            if (amount <= 0 || !(stack.getItem() instanceof PortBatteryItem battery)) return 0;
            long extracted = Math.min(amount, battery.charge(stack));
            if (extracted > 0) {
                battery.setCharge(stack, battery.charge(stack) - extracted);
                setChanged();
            }
            return extracted;
        }
    };
    private short redLow = PortBatteryBlockEntity.INPUT;
    private short redHigh = PortBatteryBlockEntity.OUTPUT;
    private PortPowerNetwork.Priority priority = PortPowerNetwork.Priority.LOW;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index < 4) return (int) ((reservoir.power() >>> (index * 16)) & 0xffff);
            if (index < 8) return (int) ((reservoir.capacity() >>> ((index - 4) * 16)) & 0xffff);
            return switch (index) {
                case 8 -> redLow;
                case 9 -> redHigh;
                case 10 -> priority.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // A client's data slots cannot mutate the server-owned socket.
        }

        @Override
        public int getCount() {
            return 11;
        }
    };

    public PortBatterySocketBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.BATTERY_SOCKET.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortBatterySocketBlockEntity socket) {
        if (level instanceof ServerLevel serverLevel && state.getValue(PortBatterySocketBlock.PART) == 0) {
            PortPowerGrid.tick(serverLevel, socket);
        }
    }

    public ItemStackHandler items() {
        return items;
    }

    public ContainerData menuData() {
        return menuData;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockState state = getBlockState();
        BlockPos corner = PortBatterySocketBlock.partPos(worldPosition,
                state.getValue(PortBatterySocketBlock.FACING), 3);
        return new AABB(Math.min(worldPosition.getX(), corner.getX()), worldPosition.getY(),
                Math.min(worldPosition.getZ(), corner.getZ()),
                Math.max(worldPosition.getX(), corner.getX()) + 1, worldPosition.getY() + 2,
                Math.max(worldPosition.getZ(), corner.getZ()) + 1);
    }

    public short lowMode() {
        return redLow;
    }

    public short highMode() {
        return redHigh;
    }

    public void setModes(short low, short high) {
        redLow = validMode(low);
        redHigh = validMode(high);
        changed();
    }

    public void setPriority(PortPowerNetwork.Priority value) {
        if (value != PortPowerNetwork.Priority.LOW && value != PortPowerNetwork.Priority.NORMAL
                && value != PortPowerNetwork.Priority.HIGH) {
            throw new IllegalArgumentException("Socket priority must be low, normal, or high");
        }
        priority = value;
        changed();
    }

    public int comparatorPower() {
        long capacity = reservoir.capacity();
        return capacity == 0 ? 0 : Math.min(15, (int) Math.round((double) reservoir.power() / capacity * 15));
    }

    @Override
    public BlockEntity blockEntity() {
        return this;
    }

    @Override
    public PortPowerReservoir reservoir() {
        return reservoir;
    }

    @Override
    public short mode(ServerLevel level) {
        BlockState state = getBlockState();
        for (int part = 0; part < 4; part++) {
            BlockPos pos = PortBatterySocketBlock.partPos(worldPosition, state.getValue(PortBatterySocketBlock.FACING), part);
            if (level.hasChunkAt(pos) && level.hasNeighborSignal(pos)) return redHigh;
        }
        return redLow;
    }

    @Override
    public PortPowerNetwork.Priority priority() {
        return priority;
    }

    @Override
    public long providerSpeed() {
        ItemStack stack = items.getStackInSlot(0);
        return stack.getItem() instanceof PortBatteryItem battery ? battery.dischargeRate() : 0;
    }

    @Override
    public long receiverSpeed() {
        ItemStack stack = items.getStackInSlot(0);
        return stack.getItem() instanceof PortBatteryItem battery ? battery.chargeRate() : 0;
    }

    @Override
    public void onPowerTransferred() {
        changed();
    }

    private void changed() {
        setChanged();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        for (int part = 0; part < 4; part++) {
            BlockPos pos = PortBatterySocketBlock.partPos(worldPosition, state.getValue(PortBatterySocketBlock.FACING), part);
            if (level.hasChunkAt(pos)) level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        level.sendBlockUpdated(worldPosition, state, state, 3);
    }

    private static short validMode(short mode) {
        return mode >= PortBatteryBlockEntity.INPUT && mode <= PortBatteryBlockEntity.DISABLED
                ? mode : PortBatteryBlockEntity.DISABLED;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
        redLow = validMode(tag.getShort("redLow"));
        redHigh = tag.contains("redHigh") ? validMode(tag.getShort("redHigh")) : PortBatteryBlockEntity.OUTPUT;
        priority = switch (tag.getInt("priority")) {
            case 2 -> PortPowerNetwork.Priority.NORMAL;
            case 3 -> PortPowerNetwork.Priority.HIGH;
            default -> PortPowerNetwork.Priority.LOW;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putShort("redLow", redLow);
        tag.putShort("redHigh", redHigh);
        tag.putInt("priority", priority.ordinal());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hbm.battery_socket");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PortBatterySocketMenu(containerId, playerInventory, this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, net.minecraft.core.Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> new SocketItems(items));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private record SocketItems(ItemStackHandler backing) implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return backing.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return backing.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stack = backing.getStackInSlot(slot);
            if (!(stack.getItem() instanceof PortBatteryItem battery)) return ItemStack.EMPTY;
            long charge = battery.charge(stack);
            if (charge != 0 && charge != battery.capacity()) return ItemStack.EMPTY;
            return backing.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return backing.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return backing.isItemValid(slot, stack);
        }
    }
}
