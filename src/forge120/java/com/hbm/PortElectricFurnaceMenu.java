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

/** Original furnace slot positions and server-authoritative progress/power data. */
public final class PortElectricFurnaceMenu extends AbstractContainerMenu {
    private final PortElectricFurnaceBlockEntity furnace;
    private final ContainerData data;

    public PortElectricFurnaceMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, find(inventory, extra.readBlockPos()), new SimpleContainerData(4));
    }

    public PortElectricFurnaceMenu(int id, Inventory inventory, PortElectricFurnaceBlockEntity furnace) {
        this(id, inventory, furnace, furnace.menuData());
    }

    private PortElectricFurnaceMenu(int id, Inventory inventory, PortElectricFurnaceBlockEntity furnace, ContainerData data) {
        super(PortMenus.ELECTRIC_FURNACE.get(), id);
        this.furnace = furnace;
        this.data = data;
        addSlot(new SlotItemHandler(furnace.items(), 0, 152, 54));
        addSlot(new SlotItemHandler(furnace.items(), 1, 20, 35));
        addSlot(new SlotItemHandler(furnace.items(), 2, 80, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new SlotItemHandler(furnace.items(), 3, 111, 34) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 162));
        addDataSlots(data);
    }

    private static PortElectricFurnaceBlockEntity find(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PortElectricFurnaceBlockEntity furnace) return furnace;
        throw new IllegalStateException("Electric furnace menu opened without block entity at " + pos);
    }

    public int power() { return (data.get(0) & 0xffff) | ((data.get(1) & 0xffff) << 16); }
    public int progress() { return data.get(2); }
    public int cooldown() { return data.get(3); }

    @Override public boolean stillValid(Player player) {
        return PortMenuValidity.isUsable(player, furnace);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < 4) {
            if (!moveItemStackTo(original, 4, slots.size(), true)) return ItemStack.EMPTY;
        } else if (original.getItem() instanceof PortBatteryItem) {
            if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, 1, 2, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
