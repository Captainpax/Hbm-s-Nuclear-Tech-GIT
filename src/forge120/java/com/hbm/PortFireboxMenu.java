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

public final class PortFireboxMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final PortFireboxBlockEntity machine;

    public PortFireboxMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, find(inventory, extra.readBlockPos()), new SimpleContainerData(4));
    }
    public PortFireboxMenu(int id, Inventory inventory, PortFireboxBlockEntity machine) {
        this(id, inventory, machine, machine.menuData());
    }
    private PortFireboxMenu(int id, Inventory inventory, PortFireboxBlockEntity machine, ContainerData data) {
        super(PortMenus.FIREBOX.get(), id);
        this.data = data;
        this.machine = machine;
        addSlot(new SlotItemHandler(machine.items(), 0, 44, 27));
        addSlot(new SlotItemHandler(machine.items(), 1, 62, 27));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 86 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 144));
        addDataSlots(data);
    }
    private static PortFireboxBlockEntity find(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PortFireboxBlockEntity machine) return machine;
        throw new IllegalStateException("Firebox menu opened without core at " + pos);
    }
    public int burnTime() { return data.get(0); }
    public int maxBurnTime() { return data.get(1); }
    public int heat() { return (data.get(2) & 0xffff) | ((data.get(3) & 0xffff) << 16); }
    @Override public boolean stillValid(Player player) {
        return PortMenuValidity.isUsable(player, machine);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 2, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
