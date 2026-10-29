package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Source five-slot blast furnace: 800 fuel per 800-duration steel operation at half speed. */
public final class PortBlastFurnaceBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FUEL_RATE = 800;
    public static final int MAX_FUEL = 38_400;
    private final ItemStackHandler items = new ItemStackHandler(5) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && fuelTime(stack) > 0 || slot == 1 || slot == 2;
        }
    };
    private LazyOptional<IItemHandler> fullItems = LazyOptional.of(() -> items);
    private LazyOptional<IItemHandler> sideItems = LazyOptional.of(() -> new SidedItems(items));
    private int fuel;
    private int progress;
    private int flueGas;
    private boolean progressing;
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> fuel & 0xffff;
                case 1 -> fuel >>> 16;
                case 2 -> progress;
                case 3 -> flueGas;
                case 4 -> progressing ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 5; }
    };

    public PortBlastFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.BLAST_FURNACE.get(), pos, state);
    }

    public ItemStackHandler items() { return items; }
    public int fuel() { return fuel; }
    public int progress() { return progress; }
    public int flueGas() { return flueGas; }
    public boolean progressing() { return progressing; }
    public ContainerData menuData() { return menuData; }

    public static int fuelTime(ItemStack stack) {
        if (stack.isEmpty() || !stack.getCraftingRemainingItem().isEmpty()) return 0;
        // The source burn module removes wood heat. Coal, charcoal and lignite
        // are the Survival fuels available in this progression slice.
        if (!stack.is(Items.COAL) && !stack.is(Items.CHARCOAL)
                && !stack.is(PortItems.LIGNITE.get())) return 0;
        return Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortBlastFurnaceBlockEntity machine) {
        if (!level.isClientSide) machine.tickServer();
    }

    private void tickServer() {
        ItemStack fuelStack = items.getStackInSlot(0);
        int burn = fuelTime(fuelStack);
        if (burn > 0 && fuel <= MAX_FUEL - burn) {
            fuel += burn;
            items.extractItem(0, 1, false);
        }
        boolean canRun = fuel >= FUEL_RATE
                && PortBlastFurnaceRecipes.steelFromIngots(items.getStackInSlot(1), items.getStackInSlot(2))
                && canOutput(3, new ItemStack(PortItems.INGOT_STEEL.get(), 2))
                && canOutput(4, new ItemStack(PortItems.INGOT_RAW_SLAG.get()));
        progressing = canRun;
        if (canRun) {
            // Source speed is 0.5 without the optional airblast fluid.
            progress++;
            if (progress >= PortBlastFurnaceRecipes.STEEL_DURATION * 2) {
                int ironSlot = PortBlastFurnaceRecipes.ironSlot(items.getStackInSlot(1), items.getStackInSlot(2));
                int sandSlot = ironSlot == 1 ? 2 : 1;
                items.extractItem(ironSlot, 2, false);
                items.extractItem(sandSlot, 1, false);
                putOutput(3, new ItemStack(PortItems.INGOT_STEEL.get(), 2));
                putOutput(4, new ItemStack(PortItems.INGOT_RAW_SLAG.get()));
                fuel -= FUEL_RATE;
                flueGas = Math.min(1_000, flueGas + 100);
                progress = 0;
            }
        } else {
            progress = 0;
        }
        if (canRun || burn > 0) setChanged();
    }

    private boolean canOutput(int slot, ItemStack output) {
        ItemStack current = items.getStackInSlot(slot);
        return current.isEmpty() || ItemStack.isSameItemSameTags(current, output)
                && current.getCount() + output.getCount() <= current.getMaxStackSize();
    }

    private void putOutput(int slot, ItemStack output) {
        ItemStack current = items.getStackInSlot(slot);
        if (current.isEmpty()) items.setStackInSlot(slot, output);
        else {
            ItemStack grown = current.copy();
            grown.grow(output.getCount());
            items.setStackInSlot(slot, grown);
        }
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
        fuel = Math.max(0, Math.min(MAX_FUEL, tag.getInt("fuel")));
        progress = Math.max(0, Math.min(PortBlastFurnaceRecipes.STEEL_DURATION * 2 - 1, tag.getInt("progress")));
        flueGas = Math.max(0, Math.min(1_000, tag.getInt("flueGas")));
        progressing = tag.getBoolean("progressing");
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("fuel", fuel);
        tag.putInt("progress", progress);
        tag.putInt("flueGas", flueGas);
        tag.putBoolean("progressing", progressing);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.hbm.blast_furnace"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortBlastFurnaceMenu(id, inventory, this);
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return (side == null ? fullItems : sideItems).cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        fullItems.invalidate();
        sideItems.invalidate();
    }
    @Override public void reviveCaps() {
        super.reviveCaps();
        fullItems = LazyOptional.of(() -> items);
        sideItems = LazyOptional.of(() -> new SidedItems(items));
    }

    private record SidedItems(ItemStackHandler backing) implements IItemHandler {
        @Override public int getSlots() { return 5; }
        @Override public ItemStack getStackInSlot(int slot) { return backing.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < 3 ? backing.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= 3 ? backing.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return backing.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot < 3 && backing.isItemValid(slot, stack);
        }
    }
}
