package com.hbm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The source anvil's two-slot smithing and inventory-backed construction paths. */
public final class PortAnvilMenu extends AbstractContainerMenu {
    private final SimpleContainer input = new SimpleContainer(2) {
        @Override public void setChanged() {
            super.setChanged();
            PortAnvilMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer output = new ResultContainer();
    private final ContainerLevelAccess access;
    private final int tier;

    public PortAnvilMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, extra.readBlockPos());
    }

    private PortAnvilMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, inventory.player.level().getBlockState(pos).getBlock() instanceof PortAnvilBlock block
                ? block.tier() : 0);
    }

    public PortAnvilMenu(int id, Inventory inventory, BlockPos pos, int tier) {
        super(PortMenus.ANVIL.get(), id);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.tier = tier;
        addSlot(new Slot(input, 0, 17, 27));
        addSlot(new Slot(input, 1, 53, 27));
        addSlot(new Slot(output, 0, 89, 27) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return !output.getItem(0).isEmpty() && validSmithing(); }
            @Override public void onTake(Player player, ItemStack stack) {
                if (validSmithing()) {
                    input.removeItem(0, 1);
                    input.removeItem(1, 10);
                }
                super.onTake(player, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 198));
        slotsChanged(input);
    }

    public int tier() { return tier; }

    private boolean validSmithing() {
        return tier >= 1 && input.getItem(0).is(PortItems.ANVIL_IRON.get())
                && input.getItem(1).is(PortItems.INGOT_STEEL.get())
                && input.getItem(1).getCount() >= 10;
    }

    @Override public void slotsChanged(Container container) {
        if (validSmithing()) output.setItem(0, new ItemStack(PortItems.ANVIL_STEEL.get()));
        else output.setItem(0, ItemStack.EMPTY);
        broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player) || id < 0 || id >= PortAnvilConstruction.recipes().size() * 2) return false;
        if (player.level().isClientSide) return true;
        boolean made = PortAnvilConstruction.craft(player, tier, id / 2, (id & 1) == 1);
        if (made) broadcastChanges();
        return made;
    }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, tier == 1 ? PortBlocks.ANVIL_IRON.get() : PortBlocks.ANVIL_STEEL.get());
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 2) {
            if (!validSmithing() || !moveItemStackTo(stack, 3, slots.size(), true)) return ItemStack.EMPTY;
            slot.onTake(player, original);
            return original;
        }
        if (index < 2) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = stack.is(PortItems.ANVIL_IRON.get()) ? 0
                    : stack.is(PortItems.INGOT_STEEL.get()) ? 1 : -1;
            if (target == -1 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        clearContainer(player, input);
    }
}
