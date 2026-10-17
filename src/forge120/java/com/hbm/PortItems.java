package com.hbm;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** First item registration slice, keyed to the original ModItems names. */
public final class PortItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HbmPort.MOD_ID);

    public static final RegistryObject<Item> ORE_URANIUM = ITEMS.register("ore_uranium",
            () -> new BlockItem(PortBlocks.ORE_URANIUM.get(), new Item.Properties()));
    public static final RegistryObject<Item> ORE_THORIUM = ore("ore_thorium", PortBlocks.ORE_THORIUM);
    public static final RegistryObject<Item> ORE_TITANIUM = ore("ore_titanium", PortBlocks.ORE_TITANIUM);
    public static final RegistryObject<Item> ORE_COPPER = ore("ore_copper", PortBlocks.ORE_COPPER);
    public static final RegistryObject<Item> ORE_TUNGSTEN = ore("ore_tungsten", PortBlocks.ORE_TUNGSTEN);
    public static final RegistryObject<Item> ORE_LEAD = ore("ore_lead", PortBlocks.ORE_LEAD);
    public static final RegistryObject<Item> ORE_BERYLLIUM = ore("ore_beryllium", PortBlocks.ORE_BERYLLIUM);
    public static final RegistryObject<Item> GAS_RADON = ITEMS.register("gas_radon",
            () -> new BlockItem(PortBlocks.GAS_RADON.get(), new Item.Properties()));
    public static final RegistryObject<Item> INGOT_URANIUM = ITEMS.register("ingot_uranium",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INGOT_TH232 = ingot("ingot_th232");
    public static final RegistryObject<Item> INGOT_TITANIUM = ingot("ingot_titanium");
    public static final RegistryObject<Item> INGOT_COPPER = ingot("ingot_copper");
    public static final RegistryObject<Item> INGOT_TUNGSTEN = ingot("ingot_tungsten");
    public static final RegistryObject<Item> INGOT_LEAD = ingot("ingot_lead");
    public static final RegistryObject<Item> INGOT_BERYLLIUM = ingot("ingot_beryllium");
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
            () -> new PortGeigerCounter(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ORE_SULFUR = ore("ore_sulfur", PortBlocks.ORE_SULFUR);
    public static final RegistryObject<Item> ORE_FLUORITE = ore("ore_fluorite", PortBlocks.ORE_FLUORITE);
    public static final RegistryObject<Item> ORE_NITER = ore("ore_niter", PortBlocks.ORE_NITER);
    public static final RegistryObject<Item> SULFUR = ingot("sulfur");
    public static final RegistryObject<Item> FLUORITE = ingot("fluorite");
    public static final RegistryObject<Item> NITER = ingot("niter");
    public static final RegistryObject<Item> ORE_ALUMINIUM = ore("ore_aluminium", PortBlocks.ORE_ALUMINIUM);
    public static final RegistryObject<Item> CHUNK_ORE_CRYOLITE = ingot("chunk_ore_cryolite");
    public static final RegistryObject<Item> MACHINE_BATTERY = ore("machine_battery", PortBlocks.MACHINE_BATTERY);

    private PortItems() {}

    private static RegistryObject<Item> ore(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static RegistryObject<Item> ingot(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
