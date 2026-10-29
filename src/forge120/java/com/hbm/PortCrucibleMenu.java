package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class PortCrucibleMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public PortCrucibleMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, find(inventory, extra.readBlockPos()), new SimpleContainerData(4));
    }
    public PortCrucibleMenu(int id, Inventory inventory, PortCrucibleBlockEntity machine) {
        this(id, inventory, machine, machine.menuData());
    }
    private PortCrucibleMenu(int id, Inventory inventory, PortCrucibleBlockEntity machine, ContainerData data) {
        super(PortMenus.CRUCIBLE.get(), id);
        access = ContainerLevelAccess.create(inventory.player.level(), machine.getBlockPos());
        this.data = data;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            addSlot(new SlotItemHandler(machine.items(), 1 + col + row * 3, 107 + col * 18, 18 + row * 18));
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 132 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 190));
        addDataSlots(data);
    }
    private static PortCrucibleBlockEntity find(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PortCrucibleBlockEntity machine) return machine;
        throw new IllegalStateException("Crucible menu opened without core at " + pos);
    }
    public int heat() { return (data.get(0) & 0xffff) | ((data.get(1) & 0xffff) << 16); }
    public int progress() { return data.get(2); }
    public int moltenCopper() { return data.get(3); }
    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, PortBlocks.MACHINE_CRUCIBLE.get());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 9) {
            if (!moveItemStackTo(stack, 9, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
