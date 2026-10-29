package com.hbm;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;

/** First material mappings from Mats and MaterialShapes, in source quanta per item. */
final class PortCopperMaterial {
    static final int INGOT_QUANTA = 72;

    private PortCopperMaterial() {}

    static int quantaPerItem(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Tags.Items.INGOTS_COPPER) || stack.is(PortItemTags.PLATES_COPPER)) return INGOT_QUANTA;
        if (stack.is(PortItemTags.FINE_WIRES_COPPER)) return 9;
        if (stack.is(PortItemTags.CAST_PLATES_COPPER)) return INGOT_QUANTA * 3;
        if (stack.is(Tags.Items.STORAGE_BLOCKS_COPPER)) return INGOT_QUANTA * 9;
        return stack.getItem() instanceof PortScrapsItem scrap && scrap.material() == PortMaterial.COPPER
                ? PortScrapsItem.quanta(stack) : 0;
    }
}
