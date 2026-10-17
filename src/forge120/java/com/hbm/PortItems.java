package com.hbm;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** First item registration slice, keyed to the original ModItems names. */
public final class PortItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HbmPort.MOD_ID);

    public static final RegistryObject<Item> ORE_URANIUM = ITEMS.register("ore_uranium",
            () -> new BlockItem(PortBlocks.ORE_URANIUM.get(), new Item.Properties()));
    public static final RegistryObject<Item> GAS_RADON = ITEMS.register("gas_radon",
            () -> new BlockItem(PortBlocks.GAS_RADON.get(), new Item.Properties()));
    public static final RegistryObject<Item> INGOT_URANIUM = ITEMS.register("ingot_uranium",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GAS_MASK = ITEMS.register("gas_mask",
            () -> new PortGasMask(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GAS_MASK_M65 = ITEMS.register("gas_mask_m65",
            () -> new PortGasMask(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GAS_MASK_MONO = ITEMS.register("gas_mask_mono",
            () -> new PortGasMask(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GAS_MASK_OLDE = ITEMS.register("gas_mask_olde",
            () -> new PortGasMask(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GAS_MASK_FILTER = ITEMS.register("gas_mask_filter",
            () -> new PortGasMaskFilter(new Item.Properties().durability(20000)));
    public static final RegistryObject<Item> GAS_MASK_FILTER_MONO = ITEMS.register("gas_mask_filter_mono",
            () -> new PortGasMaskFilter(new Item.Properties().durability(20000)));
    public static final RegistryObject<Item> GAS_MASK_FILTER_COMBO = ITEMS.register("gas_mask_filter_combo",
            () -> new PortGasMaskFilter(new Item.Properties().durability(20000)));
    public static final RegistryObject<Item> GAS_MASK_FILTER_RAG = ITEMS.register("gas_mask_filter_rag",
            () -> new PortGasMaskFilter(new Item.Properties().durability(20000)));
    public static final RegistryObject<Item> GAS_MASK_FILTER_PISS = ITEMS.register("gas_mask_filter_piss",
            () -> new PortGasMaskFilter(new Item.Properties().durability(20000)));
    public static final RegistryObject<Item> GEIGER_COUNTER = ITEMS.register("geiger_counter",
            () -> new Item(new Item.Properties().stacksTo(1)));

    private PortItems() {}
}
