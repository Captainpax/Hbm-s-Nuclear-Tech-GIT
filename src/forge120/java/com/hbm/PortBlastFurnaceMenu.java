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

/** Source five-slot blast furnace GUI with server-owned state. */
public final class PortBlastFurnaceMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public PortBlastFurnaceMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, find(inventory, extra.readBlockPos()), new SimpleContainerData(5));
    }

    public PortBlastFurnaceMenu(int id, Inventory inventory, PortBlastFurnaceBlockEntity machine) {
        this(id, inventory, machine, machine.menuData());
    }

    private PortBlastFurnaceMenu(int id, Inventory inventory, PortBlastFurnaceBlockEntity machine, ContainerData data) {
        super(PortMenus.BLAST_FURNACE.get(), id);
        access = ContainerLevelAccess.create(inventory.player.level(), machine.getBlockPos());
        this.data = data;
        addSlot(new SlotItemHandler(machine.items(), 0, 80, 81));
        addSlot(new SlotItemHandler(machine.items(), 1, 80, 27));
        addSlot(new SlotItemHandler(machine.items(), 2, 80, 45));
        addSlot(new SlotItemHandler(machine.items(), 3, 134, 72) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new SlotItemHandler(machine.items(), 4, 134, 90) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 198));
        addDataSlots(data);
    }

    private static PortBlastFurnaceBlockEntity find(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PortBlastFurnaceBlockEntity machine) return machine;
        throw new IllegalStateException("Blast furnace menu opened without core at " + pos);
    }

    public int fuel() { return (data.get(0) & 0xffff) | ((data.get(1) & 0xffff) << 16); }
    public int progress() { return data.get(2); }
    public int flueGas() { return data.get(3); }
    public boolean progressing() { return data.get(4) != 0; }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, PortBlocks.MACHINE_BLAST_FURNACE.get());
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 5) {
            if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = PortBlastFurnaceBlockEntity.fuelTime(stack) > 0 ? 0
                    : stack.is(net.minecraft.world.item.Items.SAND) || stack.is(net.minecraft.world.item.Items.RED_SAND)
                    ? 2 : 1;
            if (!moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
