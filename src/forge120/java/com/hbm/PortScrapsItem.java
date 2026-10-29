package com.hbm;

import java.util.List;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Named copper variant of ItemScraps, retaining its exact per-item amount NBT. */
public final class PortScrapsItem extends Item {
    public PortScrapsItem(Properties properties) { super(properties); }

    public static ItemStack copper(int quanta) {
        if (quanta <= 0) return ItemStack.EMPTY;
        ItemStack scrap = new ItemStack(PortItems.SCRAPS_COPPER.get());
        scrap.getOrCreateTag().putInt("amount", quanta);
        return scrap;
    }

    /** The source defaults an untagged scrap to one ingot; tagged amounts are per item. */
    public static int quanta(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(PortItems.SCRAPS_COPPER.get())) return 0;
        if (!stack.hasTag()) return PortCopperMaterial.INGOT_QUANTA;
        var tag = stack.getTag();
        return tag.contains("amount", Tag.TAG_INT) ? Math.max(0, tag.getInt("amount")) : 0;
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        int amount = quanta(stack);
        tooltip.add(Component.translatable("item.hbm.scraps_copper.amount", amount));
    }
}
