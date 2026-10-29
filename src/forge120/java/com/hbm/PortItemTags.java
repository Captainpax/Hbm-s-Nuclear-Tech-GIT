package com.hbm;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Ore-dictionary equivalents used by the currently ported progression recipes. */
public final class PortItemTags {
    public static final TagKey<Item> INGOTS_ALUMINIUM = forge("ingots/aluminum");
    public static final TagKey<Item> INGOTS_BERYLLIUM = forge("ingots/beryllium");
    public static final TagKey<Item> INGOTS_STEEL = forge("ingots/steel");
    public static final TagKey<Item> INGOTS_TUNGSTEN = forge("ingots/tungsten");
    public static final TagKey<Item> PLATES_ALUMINIUM = forge("plates/aluminum");
    public static final TagKey<Item> PLATES_COPPER = forge("plates/copper");
    public static final TagKey<Item> PLATES_IRON = forge("plates/iron");
    public static final TagKey<Item> PLATES_STEEL = forge("plates/steel");
    public static final TagKey<Item> BLOCKS_STEEL = forge("storage_blocks/steel");
    // These source-specific shapes have distinct quantities from ordinary plates/wires.
    public static final TagKey<Item> CAST_PLATES_COPPER = hbm("cast_plates/copper");
    public static final TagKey<Item> CAST_PLATES_IRON = hbm("cast_plates/iron");
    public static final TagKey<Item> CAST_PLATES_STEEL = hbm("cast_plates/steel");
    public static final TagKey<Item> FINE_WIRES_ALUMINIUM = hbm("fine_wires/aluminum");
    public static final TagKey<Item> FINE_WIRES_COPPER = hbm("fine_wires/copper");
    public static final TagKey<Item> FINE_WIRES_TUNGSTEN = hbm("fine_wires/tungsten");
    public static final TagKey<Item> BOLTS_STEEL = hbm("bolts/steel");

    private PortItemTags() {}

    private static TagKey<Item> forge(String path) { return tag("forge", path); }
    private static TagKey<Item> hbm(String path) { return tag(HbmPort.MOD_ID, path); }
    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
