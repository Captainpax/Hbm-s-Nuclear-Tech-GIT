package com.hbm;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Source non-repairable crafting tool with a worn, reusable recipe remainder. */
public class PortCraftingToolItem extends Item {
    public PortCraftingToolItem(int durability) {
        super(durability > 0 ? new Properties().durability(durability) : new Properties().stacksTo(1));
    }

    @Override public boolean isRepairable(ItemStack stack) { return false; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }

    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack remainder = stack.copyWithCount(1);
        if (remainder.isDamageableItem()) {
            // Source SlotCrafting rejects the remainder only ABOVE maxDamage.
            if (remainder.getDamageValue() >= remainder.getMaxDamage()) return ItemStack.EMPTY;
            remainder.setDamageValue(remainder.getDamageValue() + 1);
        }
        return remainder;
    }
}
