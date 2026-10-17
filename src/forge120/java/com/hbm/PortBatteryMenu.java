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

/** The legacy two-slot battery menu, with server-authoritative mode buttons. */
public final class PortBatteryMenu extends AbstractContainerMenu {
    private final PortBatteryBlockEntity battery;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public PortBatteryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, findBattery(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(7));
    }

    public PortBatteryMenu(int containerId, Inventory playerInventory, PortBatteryBlockEntity battery) {
        this(containerId, playerInventory, battery, battery.menuData());
    }

    private PortBatteryMenu(int containerId, Inventory playerInventory, PortBatteryBlockEntity battery,
            ContainerData data) {
        super(PortMenus.BATTERY.get(), containerId);
        this.battery = battery;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), battery.getBlockPos());
        this.data = data;
        addSlot(new SlotItemHandler(battery.items(), 0, 26, 17));
        addSlot(new SlotItemHandler(battery.items(), 1, 26, 53));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    private static PortBatteryBlockEntity findBattery(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PortBatteryBlockEntity battery) return battery;
        throw new IllegalStateException("Battery menu opened without its block entity at " + pos);
    }

    public long power() {
        return (data.get(0) & 0xffffL) | ((data.get(1) & 0xffffL) << 16)
                | ((data.get(2) & 0xffffL) << 32) | ((data.get(3) & 0xffffL) << 48);
    }

    public long capacity() {
        return battery.capacity();
    }

    public int lowMode() {
        return data.get(4);
    }

    public int highMode() {
        return data.get(5);
    }

    public int priority() {
        return data.get(6);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PortBlocks.MACHINE_BATTERY.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (player.level().isClientSide || !stillValid(player)) return false;
        switch (buttonId) {
            case 0 -> battery.setModes((short) ((battery.lowMode() + 1) % 4), battery.highMode());
            case 1 -> battery.setModes(battery.lowMode(), (short) ((battery.highMode() + 1) % 4));
            case 2 -> battery.setPriority(switch (battery.priority()) {
                case LOW -> PortPowerNetwork.Priority.NORMAL;
                case NORMAL -> PortPowerNetwork.Priority.HIGH;
                default -> PortPowerNetwork.Priority.LOW;
            });
            default -> { return false; }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < 2) {
            if (!moveItemStackTo(original, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!(original.getItem() instanceof PortBatteryItem)
                || !moveItemStackTo(original, 0, 2, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
