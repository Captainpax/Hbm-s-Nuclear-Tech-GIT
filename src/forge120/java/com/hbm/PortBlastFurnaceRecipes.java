package com.hbm;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;

/** First active BlastFurnaceRecipesNT steel route. */
public final class PortBlastFurnaceRecipes {
    public static final int STEEL_DURATION = 800;
    private PortBlastFurnaceRecipes() {}

    public static boolean steelFromIngots(ItemStack first, ItemStack second) {
        return isIron(first) && isSand(second) || isSand(first) && isIron(second);
    }

    public static int ironSlot(ItemStack first, ItemStack second) {
        return isIron(first) && isSand(second) ? 1 : isSand(first) && isIron(second) ? 2 : -1;
    }

    private static boolean isIron(ItemStack stack) {
        return stack.is(Tags.Items.INGOTS_IRON) && stack.getCount() >= 2;
    }

    private static boolean isSand(ItemStack stack) {
        return stack.is(Tags.Items.SAND);
    }
}
