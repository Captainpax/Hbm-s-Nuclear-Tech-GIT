package com.hbm;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The first source PressRecipes mappings; no copper cast plate shortcut. */
public final class PortPressRecipes {
    private PortPressRecipes() {}

    public static boolean isStamp(ItemStack stack) {
        Item item = stack.getItem();
        return item == PortItems.STAMP_IRON_FLAT.get()
                || item == PortItems.STAMP_IRON_PLATE.get()
                || item == PortItems.STAMP_IRON_WIRE.get();
    }

    public static ItemStack output(ItemStack input, ItemStack stamp) {
        if (input.isEmpty() || stamp.isEmpty()) return ItemStack.EMPTY;
        if (stamp.is(PortItems.STAMP_IRON_WIRE.get())) {
            if (input.is(PortItems.INGOT_TUNGSTEN.get())) return new ItemStack(PortItems.WIRE_FINE_TUNGSTEN.get(), 8);
            if (input.is(PortItems.INGOT_COPPER.get())) return new ItemStack(PortItems.WIRE_FINE_COPPER.get(), 8);
            if (input.is(PortItems.INGOT_ALUMINIUM.get())) return new ItemStack(PortItems.WIRE_FINE_ALUMINIUM.get(), 8);
        }
        if (stamp.is(PortItems.STAMP_IRON_PLATE.get())) {
            if (input.is(net.minecraft.world.item.Items.IRON_INGOT)) return new ItemStack(PortItems.PLATE_IRON.get());
            if (input.is(PortItems.INGOT_STEEL.get())) return new ItemStack(PortItems.PLATE_STEEL.get());
            if (input.is(PortItems.INGOT_COPPER.get())) return new ItemStack(PortItems.PLATE_COPPER.get());
            if (input.is(PortItems.INGOT_ALUMINIUM.get())) return new ItemStack(PortItems.PLATE_ALUMINIUM.get());
        }
        return ItemStack.EMPTY;
    }
}
