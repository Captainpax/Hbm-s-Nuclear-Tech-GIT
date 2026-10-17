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

/** One item slot and server-authoritative control buttons for the 2x2 socket. */
public final class PortBatterySocketMenu extends AbstractContainerMenu {
    private final PortBatterySocketBlockEntity socket;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public PortBatterySocketMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, findSocket(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(11));
    }

    public PortBatterySocketMenu(int containerId, Inventory playerInventory, PortBatterySocketBlockEntity socket) {
        this(containerId, playerInventory, socket, socket.menuData());
    }

    private PortBatterySocketMenu(int containerId, Inventory playerInventory, PortBatterySocketBlockEntity socket,
            ContainerData data) {
        super(PortMenus.BATTERY_SOCKET.get(), containerId);
        this.socket = socket;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), socket.getBlockPos());
        this.data = data;
        addSlot(new SlotItemHandler(socket.items(), 0, 35, 35));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 99 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 157));
        }
        addDataSlots(data);
    }

    private static PortBatterySocketBlockEntity findSocket(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PortBatterySocketBlockEntity socket) return socket;
        throw new IllegalStateException("Battery socket menu opened without its core at " + pos);
    }

    private long longData(int first) {
        return (data.get(first) & 0xffffL) | ((data.get(first + 1) & 0xffffL) << 16)
                | ((data.get(first + 2) & 0xffffL) << 32) | ((data.get(first + 3) & 0xffffL) << 48);
    }

    public long power() {
        return longData(0);
    }

    public long capacity() {
        return longData(4);
    }

    public int lowMode() {
        return data.get(8);
    }

    public int highMode() {
        return data.get(9);
    }

    public int priority() {
        return data.get(10);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PortBlocks.MACHINE_BATTERY_SOCKET.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (player.level().isClientSide || !stillValid(player)) return false;
        switch (buttonId) {
            case 0 -> socket.setModes((short) ((socket.lowMode() + 1) % 4), socket.highMode());
            case 1 -> socket.setModes(socket.lowMode(), (short) ((socket.highMode() + 1) % 4));
            case 2 -> socket.setPriority(switch (socket.priority()) {
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
        if (index == 0) {
            if (!moveItemStackTo(original, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!(original.getItem() instanceof PortBatteryItem)
                || !moveItemStackTo(original, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
