package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Source base-rate furnace, with upgrade slot reserved for a later progression slice. */
public final class PortElectricFurnaceBlockEntity extends BlockEntity implements MenuProvider, PortPowerEndpoint {
    public static final long CAPACITY = 100_000L;
    public static final int CONSUMPTION = 50;
    public static final int DURATION = 100;
    private final PortPowerStorage storage = new PortPowerStorage(CAPACITY);
    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) return stack.getItem() instanceof PortBatteryItem;
            if (slot != 1 || level == null) return false;
            return level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level).isPresent();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 || slot == 3 ? 1 : 64;
        }
    };
    private LazyOptional<IItemHandler> fullCapability = LazyOptional.of(() -> items);
    private LazyOptional<IItemHandler> sidedCapability = LazyOptional.of(() -> new SidedItems(items));
    private int progress;
    private int cooldown;
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) (storage.power() & 0xffff);
                case 1 -> (int) ((storage.power() >>> 16) & 0xffff);
                case 2 -> progress;
                case 3 -> cooldown;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 4; }
    };

    public PortElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.ELECTRIC_FURNACE.get(), pos, state);
    }

    public ItemStackHandler items() { return items; }
    public long power() { return storage.power(); }
    public int progress() { return progress; }
    public int cooldown() { return cooldown; }
    public void setPower(long value) { storage.setPower(value); setChanged(); }
    public ContainerData menuData() { return menuData; }

    @Override public BlockEntity blockEntity() { return this; }
    @Override public PortPowerReservoir reservoir() { return storage; }
    @Override public short mode(ServerLevel level) { return PortBatteryBlockEntity.INPUT; }
    @Override public PortPowerNetwork.Priority priority() { return PortPowerNetwork.Priority.NORMAL; }
    @Override public long providerSpeed() { return 0L; }
    @Override public long receiverSpeed() { return CAPACITY; }
    @Override public void onPowerTransferred() { setChanged(); }

    public static void tick(Level level, BlockPos pos, BlockState state, PortElectricFurnaceBlockEntity furnace) {
        if (!(level instanceof ServerLevel server)) return;
        furnace.chargeFromItem();
        PortPowerGrid.tick(server, furnace);
        furnace.process(server);
    }

    private void chargeFromItem() {
        ItemStack stack = items.getStackInSlot(0);
        if (!(stack.getItem() instanceof PortBatteryItem battery)) return;
        long amount = Math.min(Math.min(storage.capacity() - storage.power(), battery.dischargeRate()), battery.charge(stack));
        if (amount <= 0) return;
        battery.setCharge(stack, battery.charge(stack) - amount);
        storage.receive(amount);
        setChanged();
    }

    private void process(ServerLevel level) {
        if (cooldown > 0) cooldown--;
        SmeltingRecipe recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(items.getStackInSlot(1)), level)
                .orElse(null);
        ItemStack result = recipe == null ? ItemStack.EMPTY : recipe.getResultItem(level.registryAccess());
        boolean canProcess = !result.isEmpty() && cooldown == 0 && canOutput(result);
        if (storage.power() < CONSUMPTION) {
            cooldown = 20;
            canProcess = false;
        }
        if (canProcess) {
            storage.extract(CONSUMPTION);
            progress++;
            if (progress >= DURATION) {
                ItemStack output = items.getStackInSlot(2);
                if (output.isEmpty()) items.setStackInSlot(2, result.copy());
                else {
                    output.grow(result.getCount());
                    items.setStackInSlot(2, output);
                }
                ItemStack input = items.getStackInSlot(1).copy();
                input.shrink(1);
                items.setStackInSlot(1, input);
                progress = 0;
            }
        } else {
            progress = 0;
        }
        boolean lit = progress > 0;
        BlockState state = getBlockState();
        if (state.hasProperty(PortElectricFurnaceBlock.LIT) && state.getValue(PortElectricFurnaceBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(PortElectricFurnaceBlock.LIT, lit), 3);
        }
        if (lit || cooldown > 0) setChanged();
    }

    private boolean canOutput(ItemStack result) {
        ItemStack current = items.getStackInSlot(2);
        return current.isEmpty() || (ItemStack.isSameItemSameTags(current, result)
                && current.getCount() + result.getCount() <= Math.min(current.getMaxStackSize(), items.getSlotLimit(2)));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.load(tag, "power");
        progress = Math.max(0, Math.min(DURATION - 1, tag.getInt("progress")));
        cooldown = Math.max(0, Math.min(20, tag.getInt("cooldown")));
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        storage.save(tag, "power");
        tag.putInt("progress", progress);
        tag.putInt("cooldown", cooldown);
        tag.put("items", items.serializeNBT());
    }

    @Override public Component getDisplayName() { return Component.translatable("container.hbm.electric_furnace"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortElectricFurnaceMenu(id, inventory, this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) {
            return (side == null ? fullCapability : sidedCapability).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override public void invalidateCaps() {
        super.invalidateCaps();
        fullCapability.invalidate();
        sidedCapability.invalidate();
    }

    @Override public void reviveCaps() {
        super.reviveCaps();
        fullCapability = LazyOptional.of(() -> items);
        sidedCapability = LazyOptional.of(() -> new SidedItems(items));
    }

    private record SidedItems(ItemStackHandler backing) implements IItemHandler {
        @Override public int getSlots() { return 3; }
        @Override public ItemStack getStackInSlot(int slot) { return backing.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < 2 ? backing.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 2 || slot == 0 && backing.getStackInSlot(0).getItem() instanceof PortBatteryItem battery
                    && battery.charge(backing.getStackInSlot(0)) == 0) {
                return backing.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return backing.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 2 && backing.isItemValid(slot, stack); }
    }
}
