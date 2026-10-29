package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Source press slot layout, with server-owned progress and fuel state. */
public final class PortPressMenu extends AbstractContainerMenu {
    private final PortPressBlockEntity press;
    private final ContainerData data;

    public PortPressMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, find(inventory, extra.readBlockPos()), new SimpleContainerData(3));
    }

    public PortPressMenu(int id, Inventory inventory, PortPressBlockEntity press) {
        this(id, inventory, press, press.menuData());
    }

    private PortPressMenu(int id, Inventory inventory, PortPressBlockEntity press, ContainerData data) {
        super(PortMenus.PRESS.get(), id);
        this.press = press;
        this.data = data;
        addSlot(new SlotItemHandler(press.items(), 0, 26, 53));
        addSlot(new SlotItemHandler(press.items(), 1, 80, 17));
        addSlot(new SlotItemHandler(press.items(), 2, 80, 53));
        addSlot(new SlotItemHandler(press.items(), 3, 140, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int col = 0; col < 9; col++) addSlot(new SlotItemHandler(press.items(), 4 + col, 8 + col * 18, 84));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 120 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 178));
        addDataSlots(data);
    }

    private static PortPressBlockEntity find(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PortPressBlockEntity press) return press;
        throw new IllegalStateException("Press menu opened without core at " + pos);
    }

    public int speed() { return data.get(0); }
    public int burnTime() { return data.get(1); }
    public int press() { return data.get(2); }

    @Override public boolean stillValid(Player player) {
        return PortMenuValidity.isUsable(player, press);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 13) {
            if (!moveItemStackTo(stack, 13, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = PortPressRecipes.isStamp(stack) ? 1
                    : PortPressBlockEntity.fuelTime(stack) > 0 ? 0 : 2;
            if (!moveItemStackTo(stack, target, target + 1, false)
                    && !moveItemStackTo(stack, 4, 13, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
