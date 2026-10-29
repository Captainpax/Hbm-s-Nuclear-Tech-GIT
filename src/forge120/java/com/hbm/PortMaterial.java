package com.hbm;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** First shared Mats definitions, with their frozen-source IDs, colors and quanta. */
public enum PortMaterial {
    COPPER(2900, 0xFDCA88, 0x601E0D, 0xC18336, true),
    IRON(2600, 0xFFFFFF, 0x353535, 0xFFA259, true),
    STEEL(30, 0xAFAFAF, 0x0F0F0F, 0x4A4A4A, true),
    CARBON(699, 0x363636, 0x030303, 0x404040, false),
    FLUX(40, 0xF1E0BB, 0x6F6256, 0xDECCAD, false);

    public static final int INGOT = 72;
    public static final int BLOCK = INGOT * 9;
    public final int sourceId, lightColor, darkColor, moltenColor;
    public final boolean castable;

    PortMaterial(int sourceId, int lightColor, int darkColor, int moltenColor, boolean castable) {
        this.sourceId = sourceId;
        this.lightColor = lightColor;
        this.darkColor = darkColor;
        this.moltenColor = moltenColor;
        this.castable = castable;
    }
    public String key() { return name().toLowerCase(Locale.ROOT); }
    public Component title() { return Component.translatable("material.hbm." + key()); }
    public static PortMaterial byKey(String key) {
        for (PortMaterial material : values()) if (material.key().equals(key)) return material;
        throw new IllegalArgumentException("Unknown HBM material: " + key);
    }
    public Item scrapItem() {
        return switch (this) {
            case COPPER -> PortItems.SCRAPS_COPPER.get();
            case IRON -> PortItems.SCRAPS_IRON.get();
            case STEEL -> PortItems.SCRAPS_STEEL.get();
            case CARBON -> PortItems.SCRAPS_CARBON.get();
            case FLUX -> PortItems.SCRAPS_FLUX.get();
        };
    }
    public ItemStack castPlate() {
        return switch (this) {
            case COPPER -> new ItemStack(PortItems.PLATE_CAST_COPPER.get());
            case IRON -> new ItemStack(PortItems.PLATE_CAST_IRON.get());
            case STEEL -> new ItemStack(PortItems.PLATE_CAST_STEEL.get());
            default -> ItemStack.EMPTY;
        };
    }

    /** Per item, independent of the stack count; source conversion rounds down. */
    public static List<PortMaterialStack> fromItem(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        if (stack.getItem() instanceof PortScrapsItem scraps) {
            int amount = PortScrapsItem.quanta(stack);
            return amount > 0 ? List.of(new PortMaterialStack(scraps.material(), amount)) : List.of();
        }
        int copper = PortCopperMaterial.quantaPerItem(stack);
        if (copper > 0) return List.of(new PortMaterialStack(COPPER, copper));
        if (stack.is(Items.IRON_INGOT) || stack.is(PortItems.PLATE_IRON.get())) return of(IRON, INGOT);
        if (stack.is(PortItems.PLATE_CAST_IRON.get()) || stack.is(PortItems.STAMP_IRON_FLAT.get())) return of(IRON, INGOT * 3);
        if (stack.is(PortItems.INGOT_STEEL.get()) || stack.is(PortItems.PLATE_STEEL.get())) return of(STEEL, INGOT);
        if (stack.is(PortItems.BOLT_STEEL.get())) return of(STEEL, 9);
        if (stack.is(PortItems.PLATE_CAST_STEEL.get())) return of(STEEL, INGOT * 3);
        if (stack.is(Items.CHARCOAL) || stack.is(PortItems.LIGNITE.get())) return of(CARBON, 24);
        if (stack.is(Items.COAL)) return of(CARBON, 36);
        if (stack.is(Items.DIAMOND)) return of(CARBON, INGOT);
        if (stack.is(PortItems.POWDER_FLUX.get())) return of(FLUX, INGOT);
        if (stack.is(PortItems.STONE_RESOURCE_LIMESTONE.get())) return of(FLUX, INGOT * 10);
        return List.of();
    }
    private static List<PortMaterialStack> of(PortMaterial material, int amount) {
        return List.of(new PortMaterialStack(material, amount));
    }
}
