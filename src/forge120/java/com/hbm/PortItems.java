package com.hbm;

import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
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
    public static final RegistryObject<Item> INGOT_STEEL = ingot("ingot_steel");
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
    public static final RegistryObject<Item> ORE_ASBESTOS = ore("ore_asbestos", PortBlocks.ORE_ASBESTOS);
    public static final RegistryObject<Item> BLOCK_ASBESTOS = ore("block_asbestos", PortBlocks.BLOCK_ASBESTOS);
    public static final RegistryObject<Item> GAS_ASBESTOS = ITEMS.register("gas_asbestos",
            () -> new BlockItem(PortBlocks.GAS_ASBESTOS.get(), new Item.Properties()));
    public static final RegistryObject<Item> INGOT_ASBESTOS = ingot("ingot_asbestos");
    public static final RegistryObject<Item> POWDER_ASBESTOS = ingot("powder_asbestos");
    public static final RegistryObject<Item> CHUNK_ORE_CRYOLITE = ingot("chunk_ore_cryolite");
    public static final RegistryObject<Item> PLATE_POLYMER = ingot("plate_polymer");
    public static final RegistryObject<Item> MACHINE_BATTERY = ITEMS.register("machine_battery",
            () -> new PortBatteryBlockItem((PortBatteryBlock) PortBlocks.MACHINE_BATTERY.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> RED_CABLE_CLASSIC = ITEMS.register("red_cable_classic",
            () -> new BlockItem(PortBlocks.RED_CABLE_CLASSIC.get(), new Item.Properties()));
    public static final RegistryObject<Item> MACHINE_BATTERY_SOCKET = ITEMS.register("machine_battery_socket",
            () -> new BlockItem(PortBlocks.MACHINE_BATTERY_SOCKET.get(), new Item.Properties()));
    public static final RegistryObject<Item> MACHINE_ELECTRIC_FURNACE = ore("machine_electric_furnace", PortBlocks.MACHINE_ELECTRIC_FURNACE);
    public static final RegistryObject<Item> MACHINE_PRESS = ore("machine_press", PortBlocks.MACHINE_PRESS);
    public static final RegistryObject<Item> ANVIL_IRON = ore("anvil_iron", PortBlocks.ANVIL_IRON);
    public static final RegistryObject<Item> ANVIL_STEEL = ore("anvil_steel", PortBlocks.ANVIL_STEEL);
    public static final RegistryObject<Item> STAMP_IRON_FLAT = stamp("stamp_iron_flat");
    public static final RegistryObject<Item> STAMP_IRON_PLATE = stamp("stamp_iron_plate");
    public static final RegistryObject<Item> STAMP_IRON_WIRE = stamp("stamp_iron_wire");
    public static final RegistryObject<Item> WIRE_FINE_TUNGSTEN = ingot("wire_fine_tungsten");
    public static final RegistryObject<Item> WIRE_FINE_COPPER = ingot("wire_fine_copper");
    public static final RegistryObject<Item> WIRE_FINE_ALUMINIUM = ingot("wire_fine_aluminium");
    public static final RegistryObject<Item> COIL_TUNGSTEN = ingot("coil_tungsten");
    public static final RegistryObject<Item> PLATE_COPPER = ingot("plate_copper");
    public static final RegistryObject<Item> PLATE_ALUMINIUM = ingot("plate_aluminium");
    public static final RegistryObject<Item> PLATE_IRON = ingot("plate_iron");
    public static final RegistryObject<Item> PLATE_STEEL = ingot("plate_steel");
    public static final RegistryObject<Item> BOLT_STEEL = ingot("bolt_steel");
    public static final RegistryObject<Item> BATTERY_POTATO = ITEMS.register("battery_potato",
            () -> new PortBatteryItem(new Item.Properties(), 1_000L, 0L, 100L));
    public static final RegistryObject<Item> CUBE_POWER = ITEMS.register("cube_power",
            () -> new PortBatteryItem(new Item.Properties(),
                    1_000_000_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000_000L));
    public static final RegistryObject<Item> BATTERY_PACK_REDSTONE = pack("battery_pack_redstone", 100L, false);
    public static final RegistryObject<Item> BATTERY_PACK_LEAD = pack("battery_pack_lead", 1_000L, false);
    public static final RegistryObject<Item> BATTERY_PACK_LITHIUM = pack("battery_pack_lithium", 10_000L, false);
    public static final RegistryObject<Item> BATTERY_PACK_SODIUM = pack("battery_pack_sodium", 50_000L, false);
    public static final RegistryObject<Item> BATTERY_PACK_SCHRABIDIUM =
            pack("battery_pack_schrabidium", 250_000L, false);
    public static final RegistryObject<Item> BATTERY_PACK_QUANTUM = ITEMS.register("battery_pack_quantum",
            () -> new PortBatteryItem(new Item.Properties(), 72_000_000_000L, 10_000_000L, 1_000_000L, false));
    public static final RegistryObject<Item> BATTERY_PACK_CAPACITOR_COPPER =
            pack("battery_pack_capacitor_copper", 1_000L, true);
    public static final RegistryObject<Item> BATTERY_PACK_CAPACITOR_GOLD =
            pack("battery_pack_capacitor_gold", 10_000L, true);
    public static final RegistryObject<Item> BATTERY_PACK_CAPACITOR_NIOBIUM =
            pack("battery_pack_capacitor_niobium", 100_000L, true);
    public static final RegistryObject<Item> BATTERY_PACK_CAPACITOR_TANTALUM =
            pack("battery_pack_capacitor_tantalum", 500_000L, true);
    public static final RegistryObject<Item> BATTERY_PACK_CAPACITOR_BISMUTH =
            pack("battery_pack_capacitor_bismuth", 2_500_000L, true);
    public static final RegistryObject<Item> BATTERY_PACK_CAPACITOR_SPARK =
            pack("battery_pack_capacitor_spark", 10_000_000L, true);
    public static final RegistryObject<Item> ORE_RARE = ITEMS.register("ore_rare",
            () -> new BlockItem(PortBlocks.ORE_RARE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ORE_LIGNITE = ore("ore_lignite", PortBlocks.ORE_LIGNITE);
    public static final RegistryObject<Item> ORE_CINNEBAR = ore("ore_cinnebar", PortBlocks.ORE_CINNEBAR);
    public static final RegistryObject<Item> ORE_COBALT = ore("ore_cobalt", PortBlocks.ORE_COBALT);
    public static final RegistryObject<Item> CHUNK_ORE_RARE = ingot("chunk_ore_rare");
    public static final RegistryObject<Item> LIGNITE = ITEMS.register("lignite",
            () -> new PortLigniteItem(new Item.Properties()));
    public static final RegistryObject<Item> CINNEBAR = ingot("cinnebar");
    public static final RegistryObject<Item> FRAGMENT_COBALT = ingot("fragment_cobalt");
    public static final RegistryObject<Item> INGOT_COBALT = ingot("ingot_cobalt");
    public static final RegistryObject<Item> CLUSTER_IRON = ore("cluster_iron", PortBlocks.CLUSTER_IRON);
    public static final RegistryObject<Item> CLUSTER_TITANIUM = ore("cluster_titanium", PortBlocks.CLUSTER_TITANIUM);
    public static final RegistryObject<Item> CLUSTER_ALUMINIUM = ore("cluster_aluminium", PortBlocks.CLUSTER_ALUMINIUM);
    public static final RegistryObject<Item> CLUSTER_COPPER = ore("cluster_copper", PortBlocks.CLUSTER_COPPER);
    public static final RegistryObject<Item> CRYSTAL_IRON = ingot("crystal_iron");
    public static final RegistryObject<Item> CRYSTAL_TITANIUM = ingot("crystal_titanium");
    public static final RegistryObject<Item> CRYSTAL_ALUMINIUM = ingot("crystal_aluminium");
    public static final RegistryObject<Item> CRYSTAL_COPPER = ingot("crystal_copper");
    public static final RegistryObject<Item> INGOT_ALUMINIUM = ingot("ingot_aluminium");
    public static final RegistryObject<Item> STONE_GNEISS = ore("stone_gneiss", PortBlocks.STONE_GNEISS);
    public static final RegistryObject<Item> ORE_GNEISS_IRON = ore("ore_gneiss_iron", PortBlocks.ORE_GNEISS_IRON);
    public static final RegistryObject<Item> ORE_GNEISS_GOLD = ore("ore_gneiss_gold", PortBlocks.ORE_GNEISS_GOLD);
    public static final RegistryObject<Item> ORE_GNEISS_URANIUM = ore("ore_gneiss_uranium", PortBlocks.ORE_GNEISS_URANIUM);
    public static final RegistryObject<Item> ORE_GNEISS_URANIUM_SCORCHED =
            ore("ore_gneiss_uranium_scorched", PortBlocks.ORE_GNEISS_URANIUM_SCORCHED);
    public static final RegistryObject<Item> ORE_GNEISS_COPPER = ore("ore_gneiss_copper", PortBlocks.ORE_GNEISS_COPPER);
    public static final RegistryObject<Item> ORE_GNEISS_ASBESTOS = ore("ore_gneiss_asbestos", PortBlocks.ORE_GNEISS_ASBESTOS);
    public static final RegistryObject<Item> ORE_GNEISS_LITHIUM = ore("ore_gneiss_lithium", PortBlocks.ORE_GNEISS_LITHIUM);
    public static final RegistryObject<Item> ORE_GNEISS_SCHRABIDIUM = ore("ore_gneiss_schrabidium", PortBlocks.ORE_GNEISS_SCHRABIDIUM);
    public static final RegistryObject<Item> ORE_GNEISS_RARE = ore("ore_gneiss_rare", PortBlocks.ORE_GNEISS_RARE);
    public static final RegistryObject<Item> ORE_GNEISS_GAS = ore("ore_gneiss_gas", PortBlocks.ORE_GNEISS_GAS);
    public static final RegistryObject<Item> LITHIUM = ingot("lithium");
    public static final RegistryObject<Item> INGOT_SCHRABIDIUM = ingot("ingot_schrabidium");
    public static final RegistryObject<Item> STONE_RESOURCE_LIMESTONE = ore("stone_resource_limestone", PortBlocks.STONE_RESOURCE_LIMESTONE);
    public static final RegistryObject<Item> STONE_DEPTH = ore("stone_depth", PortBlocks.STONE_DEPTH);
    public static final RegistryObject<Item> CLUSTER_DEPTH_IRON = ore("cluster_depth_iron", PortBlocks.CLUSTER_DEPTH_IRON);
    public static final RegistryObject<Item> CLUSTER_DEPTH_TITANIUM = ore("cluster_depth_titanium", PortBlocks.CLUSTER_DEPTH_TITANIUM);
    public static final RegistryObject<Item> CLUSTER_DEPTH_TUNGSTEN = ore("cluster_depth_tungsten", PortBlocks.CLUSTER_DEPTH_TUNGSTEN);
    public static final RegistryObject<Item> ORE_DEPTH_CINNEBAR = ore("ore_depth_cinnebar", PortBlocks.ORE_DEPTH_CINNEBAR);
    public static final RegistryObject<Item> ORE_DEPTH_ZIRCONIUM = ore("ore_depth_zirconium", PortBlocks.ORE_DEPTH_ZIRCONIUM);
    public static final RegistryObject<Item> ORE_DEPTH_BORAX = ore("ore_depth_borax", PortBlocks.ORE_DEPTH_BORAX);
    public static final RegistryObject<Item> CRYSTAL_TUNGSTEN = ingot("crystal_tungsten");
    public static final RegistryObject<Item> NUGGET_ZIRCONIUM = ingot("nugget_zirconium");
    public static final RegistryObject<Item> POWDER_BORAX = ingot("powder_borax");
    public static final RegistryObject<Item> BALL_FIRECLAY = ingot("ball_fireclay");
    public static final RegistryObject<Item> INGOT_FIREBRICK = ingot("ingot_firebrick");

    /** Legacy ItemBatteryPack enum order, now represented by stable named item IDs. */
    public static final List<RegistryObject<Item>> BATTERY_PACKS = List.of(
            BATTERY_PACK_REDSTONE, BATTERY_PACK_LEAD, BATTERY_PACK_LITHIUM,
            BATTERY_PACK_SODIUM, BATTERY_PACK_SCHRABIDIUM, BATTERY_PACK_QUANTUM,
            BATTERY_PACK_CAPACITOR_COPPER, BATTERY_PACK_CAPACITOR_GOLD,
            BATTERY_PACK_CAPACITOR_NIOBIUM, BATTERY_PACK_CAPACITOR_TANTALUM,
            BATTERY_PACK_CAPACITOR_BISMUTH, BATTERY_PACK_CAPACITOR_SPARK);

    private PortItems() {}

    private static RegistryObject<Item> ore(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static RegistryObject<Item> ingot(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static RegistryObject<Item> stamp(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().durability(64)));
    }

    private static RegistryObject<Item> pack(String name, long dischargeRate, boolean capacitor) {
        long capacity = Math.multiplyExact(dischargeRate, capacitor ? 600L : 18_000L);
        long chargeRate = capacitor ? dischargeRate : Math.multiplyExact(dischargeRate, 10L);
        return ITEMS.register(name, () -> new PortBatteryItem(new Item.Properties(),
                capacity, chargeRate, dischargeRate, false));
    }
}
