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

/** Fuel press timing, 13-slot inventory and stamp wear from TileEntityMachinePress. */
public final class PortPressBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_SPEED = 400;
    public static final int MAX_PRESS = 200;
    private final ItemStackHandler items = new ItemStackHandler(13) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) return fuelTime(stack) > 0;
            if (slot == 1) return PortPressRecipes.isStamp(stack);
            if (slot == 2) return !PortPressRecipes.isStamp(stack) && fuelTime(stack) == 0;
            return slot >= 4;
        }
        @Override public int getSlotLimit(int slot) { return slot == 1 ? 1 : 64; }
    };
    private LazyOptional<IItemHandler> fullItems = LazyOptional.of(() -> items);
    private LazyOptional<IItemHandler> sideItems = LazyOptional.of(() -> new SidedItems(items));
    private int speed;
    private int burnTime;
    private int press;
    private int delay;
    private boolean retracting;
    private final ContainerData menuData = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> speed;
                case 1 -> burnTime;
                case 2 -> press;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 3; }
    };

    public PortPressBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.PRESS.get(), pos, state);
    }

    public ItemStackHandler items() { return items; }
    public int speed() { return speed; }
    public int burnTime() { return burnTime; }
    public int press() { return press; }
    public ContainerData menuData() { return menuData; }

    static int fuelTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortPressBlockEntity machine) {
        if (level.isClientSide) return;
        machine.tickServer();
    }

    private void tickServer() {
        boolean canProcess = canProcess();
        if ((canProcess || retracting) && burnTime >= 200) speed = Math.min(MAX_SPEED, speed + 1);
        else speed = Math.max(0, speed - 1);

        if (delay > 0) {
            delay--;
        } else {
            int stampSpeed = speed * 25 / MAX_SPEED;
            if (retracting) {
                press -= stampSpeed;
                if (press <= 0) {
                    press = 0;
                    retracting = false;
                    delay = 5;
                }
            } else if (canProcess) {
                press += stampSpeed;
                if (press >= MAX_PRESS) {
                    ItemStack output = PortPressRecipes.output(items.getStackInSlot(2), items.getStackInSlot(1));
                    ItemStack current = items.getStackInSlot(3);
                    if (current.isEmpty()) items.setStackInSlot(3, output.copy());
                    else {
                        ItemStack grown = current.copy();
                        grown.grow(output.getCount());
                        items.setStackInSlot(3, grown);
                    }
                    items.extractItem(2, 1, false);
                    ItemStack stamp = items.getStackInSlot(1).copy();
                    stamp.setDamageValue(stamp.getDamageValue() + 1);
                    items.setStackInSlot(1, stamp.getDamageValue() >= stamp.getMaxDamage() ? ItemStack.EMPTY : stamp);
                    burnTime -= 200;
                    retracting = true;
                    delay = 5;
                }
            } else if (press > 0) {
                retracting = true;
            }
        }

        ItemStack fuel = items.getStackInSlot(0);
        if (!fuel.isEmpty() && burnTime < 200) {
            int value = fuelTime(fuel);
            if (value > 0) {
                burnTime += value;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                if (fuel.getCount() == 1 && !remainder.isEmpty()) items.setStackInSlot(0, remainder);
                else items.extractItem(0, 1, false);
            }
        }
        if (canProcess || retracting || delay > 0 || speed > 0 || burnTime > 0) setChanged();
    }

    public boolean canProcess() {
        if (burnTime < 200) return false;
        ItemStack result = PortPressRecipes.output(items.getStackInSlot(2), items.getStackInSlot(1));
        if (result.isEmpty()) return false;
        ItemStack current = items.getStackInSlot(3);
        return current.isEmpty() || ItemStack.isSameItemSameTags(current, result)
                && current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items", 10)) items.deserializeNBT(tag.getCompound("items"));
        speed = Math.max(0, Math.min(MAX_SPEED, tag.getInt("speed")));
        burnTime = Math.max(0, tag.getInt("burnTime"));
        press = Math.max(0, Math.min(MAX_PRESS, tag.getInt("press")));
        delay = Math.max(0, Math.min(5, tag.getInt("delay")));
        retracting = tag.getBoolean("retracting");
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("speed", speed);
        tag.putInt("burnTime", burnTime);
        tag.putInt("press", press);
        tag.putInt("delay", delay);
        tag.putBoolean("retracting", retracting);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.hbm.press"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortPressMenu(id, inventory, this);
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
        @Override public int getSlots() { return 4; }
        @Override public ItemStack getStackInSlot(int slot) { return backing.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < 3 ? backing.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 3 ? backing.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return backing.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot < 3 && backing.isItemValid(slot, stack);
        }
    }
}
