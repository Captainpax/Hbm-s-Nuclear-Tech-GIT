package com.hbm;

import net.minecraft.world.item.ItemStack;

/** First material mappings from Mats and MaterialShapes, in source quanta per item. */
final class PortCopperMaterial {
    static final int INGOT_QUANTA = 72;

    private PortCopperMaterial() {}

    static int quantaPerItem(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(PortItems.INGOT_COPPER.get()) || stack.is(PortItems.PLATE_COPPER.get())) return INGOT_QUANTA;
        if (stack.is(PortItems.WIRE_FINE_COPPER.get())) return 9;
        if (stack.is(PortItems.PLATE_CAST_COPPER.get())) return INGOT_QUANTA * 3;
        return stack.getItem() instanceof PortScrapsItem scrap && scrap.material() == PortMaterial.COPPER
                ? PortScrapsItem.quanta(stack) : 0;
    }
}
