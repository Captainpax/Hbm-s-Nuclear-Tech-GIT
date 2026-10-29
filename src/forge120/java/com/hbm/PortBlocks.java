package com.hbm;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** First block registration slice, keyed to the original ModBlocks names. */
public final class PortBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HbmPort.MOD_ID);

    public static final RegistryObject<Block> ORE_URANIUM = BLOCKS.register("ore_uranium",
            () -> new PortUraniumOre(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(5.0F, 10.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> ORE_THORIUM = ore("ore_thorium", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_TITANIUM = ore("ore_titanium", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_COPPER = ore("ore_copper", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_TUNGSTEN = ore("ore_tungsten", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_LEAD = ore("ore_lead", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_BERYLLIUM = ore("ore_beryllium", 5.0F, 15.0F);
    public static final RegistryObject<Block> GAS_RADON = BLOCKS.register("gas_radon",
            () -> new PortRadonGas(BlockBehaviour.Properties.of()
                    .strength(0.0F)
                    .noLootTable()));

    public static final RegistryObject<Block> ORE_SULFUR = ore("ore_sulfur", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_FLUORITE = ore("ore_fluorite", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_NITER = ore("ore_niter", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_ALUMINIUM = ore("ore_aluminium", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_ASBESTOS = BLOCKS.register("ore_asbestos",
            () -> new PortAsbestosOutgasBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(5.0F, 15.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> BLOCK_ASBESTOS = BLOCKS.register("block_asbestos",
            () -> new PortAsbestosOutgasBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(5.0F, 15.0F)
                    .sound(SoundType.WOOL)));
    public static final RegistryObject<Block> GAS_ASBESTOS = BLOCKS.register("gas_asbestos",
            () -> new PortAsbestosGas(BlockBehaviour.Properties.of()
                    .strength(0.0F)
                    .noLootTable()));
    public static final RegistryObject<Block> MACHINE_BATTERY = BLOCKS.register("machine_battery",
            () -> new PortBatteryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops(), 1_000_000L));
    public static final RegistryObject<Block> RED_CABLE_CLASSIC = BLOCKS.register("red_cable_classic",
            () -> new PortCableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> MACHINE_BATTERY_SOCKET = BLOCKS.register("machine_battery_socket",
            () -> new PortBatterySocketBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> MACHINE_ELECTRIC_FURNACE = BLOCKS.register("machine_electric_furnace",
            () -> new PortElectricFurnaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(5.0F, 10.0F)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> MACHINE_PRESS = BLOCKS.register("machine_press",
            () -> new PortPressBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(5.0F, 10.0F)
                    .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ANVIL_IRON = anvil("anvil_iron", 1);
    public static final RegistryObject<Block> ANVIL_STEEL = anvil("anvil_steel", 2);
    public static final RegistryObject<Block> ORE_RARE = ore("ore_rare", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_LIGNITE = ore("ore_lignite", 5.0F, 15.0F);
    public static final RegistryObject<Block> ORE_CINNEBAR = ore("ore_cinnebar", 5.0F, 10.0F);
    public static final RegistryObject<Block> ORE_COBALT = ore("ore_cobalt", 5.0F, 10.0F);
    public static final RegistryObject<Block> CLUSTER_IRON = ore("cluster_iron", 5.0F, 15.0F);
    public static final RegistryObject<Block> CLUSTER_TITANIUM = ore("cluster_titanium", 5.0F, 15.0F);
    public static final RegistryObject<Block> CLUSTER_ALUMINIUM = ore("cluster_aluminium", 5.0F, 15.0F);
    public static final RegistryObject<Block> CLUSTER_COPPER = ore("cluster_copper", 5.0F, 15.0F);
    public static final RegistryObject<Block> STONE_GNEISS = ore("stone_gneiss", 1.5F, 10.0F);
    public static final RegistryObject<Block> ORE_GNEISS_IRON = ore("ore_gneiss_iron", 1.5F, 10.0F);
    public static final RegistryObject<Block> ORE_GNEISS_GOLD = ore("ore_gneiss_gold", 1.5F, 10.0F);
    public static final RegistryObject<Block> ORE_GNEISS_URANIUM = BLOCKS.register("ore_gneiss_uranium",
            () -> new PortUraniumOre(oreProperties(1.5F, 10.0F)));
    public static final RegistryObject<Block> ORE_GNEISS_URANIUM_SCORCHED = BLOCKS.register("ore_gneiss_uranium_scorched",
            () -> new PortUraniumOre(oreProperties(1.5F, 10.0F)));
    public static final RegistryObject<Block> ORE_GNEISS_COPPER = ore("ore_gneiss_copper", 1.5F, 10.0F);
    public static final RegistryObject<Block> ORE_GNEISS_ASBESTOS = BLOCKS.register("ore_gneiss_asbestos",
            () -> new PortAsbestosOutgasBlock(oreProperties(1.5F, 10.0F)));
    public static final RegistryObject<Block> ORE_GNEISS_LITHIUM = ore("ore_gneiss_lithium", 1.5F, 10.0F);
    public static final RegistryObject<Block> ORE_GNEISS_SCHRABIDIUM = ore("ore_gneiss_schrabidium", 1.5F, 10.0F);
    public static final RegistryObject<Block> ORE_GNEISS_RARE = ore("ore_gneiss_rare", 1.5F, 10.0F);
    public static final RegistryObject<Block> ORE_GNEISS_GAS = ore("ore_gneiss_gas", 1.5F, 10.0F);
    public static final RegistryObject<Block> STONE_RESOURCE_LIMESTONE = ore("stone_resource_limestone", 5.0F, 10.0F);
    public static final RegistryObject<Block> STONE_DEPTH = depth("stone_depth");
    public static final RegistryObject<Block> CLUSTER_DEPTH_IRON = depth("cluster_depth_iron");
    public static final RegistryObject<Block> CLUSTER_DEPTH_TITANIUM = depth("cluster_depth_titanium");
    public static final RegistryObject<Block> CLUSTER_DEPTH_TUNGSTEN = depth("cluster_depth_tungsten");
    public static final RegistryObject<Block> ORE_DEPTH_CINNEBAR = depth("ore_depth_cinnebar");
    public static final RegistryObject<Block> ORE_DEPTH_ZIRCONIUM = depth("ore_depth_zirconium");
    public static final RegistryObject<Block> ORE_DEPTH_BORAX = depth("ore_depth_borax");
    public static final RegistryObject<Block> MACHINE_BLAST_FURNACE = BLOCKS.register("machine_blast_furnace",
            () -> new PortBlastFurnaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(5.0F, 10.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()));

    private PortBlocks() {}

    private static RegistryObject<Block> ore(String name, float hardness, float resistance) {
        return BLOCKS.register(name, () -> new Block(oreProperties(hardness, resistance)));
    }

    private static RegistryObject<Block> depth(String name) {
        // Legacy BlockDepth is unbreakable until an IDepthRockTool is held.
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(-1.0F, 10.0F).sound(SoundType.STONE)));
    }

    private static RegistryObject<Block> anvil(String name, int tier) {
        return BLOCKS.register(name, () -> new PortAnvilBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(5.0F, 100.0F)
                .sound(SoundType.ANVIL).noOcclusion().requiresCorrectToolForDrops(), tier));
    }

    private static BlockBehaviour.Properties oreProperties(float hardness, float resistance) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(hardness, resistance)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }
}
