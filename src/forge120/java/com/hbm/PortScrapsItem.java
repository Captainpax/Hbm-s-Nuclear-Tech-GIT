package com.hbm;

import java.util.List;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Named ItemScraps material variants, retaining exact per-item amount NBT. */
public final class PortScrapsItem extends Item {
    private final PortMaterial material;
    public PortScrapsItem(Properties properties) { this(properties, PortMaterial.COPPER); }
    public PortScrapsItem(Properties properties, PortMaterial material) {
        super(properties);
        this.material = material;
    }
    public PortMaterial material() { return material; }

    public static ItemStack copper(int quanta) {
        return of(PortMaterial.COPPER, quanta);
    }
    public static ItemStack of(PortMaterial material, int quanta) {
        if (quanta <= 0) return ItemStack.EMPTY;
        ItemStack scrap = new ItemStack(material.scrapItem());
        scrap.getOrCreateTag().putInt("amount", quanta);
        return scrap;
    }

    /** The source defaults an untagged scrap to one ingot; tagged amounts are per item. */
    public static int quanta(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PortScrapsItem)) return 0;
        if (!stack.hasTag()) return PortCopperMaterial.INGOT_QUANTA;
        var tag = stack.getTag();
        return tag.contains("amount", Tag.TAG_INT) ? Math.max(0, tag.getInt("amount")) : 0;
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        int amount = quanta(stack);
        tooltip.add(Component.translatable("item.hbm.scraps_copper.amount", amount));
    }
}
