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
import java.util.List;

public final class PortCrucibleMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final PortCrucibleBlockEntity machine;

    public PortCrucibleMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, find(inventory, extra.readBlockPos()), new SimpleContainerData(PortCrucibleBlockEntity.DATA_COUNT));
    }
    public PortCrucibleMenu(int id, Inventory inventory, PortCrucibleBlockEntity machine) {
        this(id, inventory, machine, machine.menuData());
    }
    private PortCrucibleMenu(int id, Inventory inventory, PortCrucibleBlockEntity machine, ContainerData data) {
        super(PortMenus.CRUCIBLE.get(), id);
        access = ContainerLevelAccess.create(inventory.player.level(), machine.getBlockPos());
        this.data = data;
        this.machine = machine;
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
    public int selectedIndex() { return data.get(3); }
    public List<PortCrucibleRecipe> recipes() { return PortCrucibleRecipes.all(machine.getLevel()); }
    public PortCrucibleRecipe selectedRecipe() {
        var recipes = recipes();
        int index = selectedIndex() - 1;
        return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
    }
    public int amount(boolean recipe, PortMaterial material) {
        return Math.max(0, data.get(4 + (recipe ? 0 : PortMaterial.values().length) + material.ordinal()));
    }
    public List<PortMaterial> materials(boolean recipe) {
        var materials = new java.util.ArrayList<PortMaterial>();
        int base = 4 + PortMaterial.values().length * (recipe ? 2 : 3);
        for (int index = 0; index < PortMaterial.values().length; index++) {
            int value = data.get(base + index);
            if (value > 0 && value <= PortMaterial.values().length) materials.add(PortMaterial.values()[value - 1]);
        }
        return materials;
    }
    @Override public boolean clickMenuButton(Player player, int index) {
        var recipes = recipes();
        if (!stillValid(player) || index < 0 || index > recipes.size()) return false;
        if (player.level().isClientSide) return true;
        boolean selected = machine.selectRecipe(index == 0 ? "" : recipes.get(index - 1).getId().toString());
        if (selected) broadcastChanges();
        return selected;
    }
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
