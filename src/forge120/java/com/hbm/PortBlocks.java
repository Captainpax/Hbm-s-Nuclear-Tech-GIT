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

    private PortBlocks() {}

    private static RegistryObject<Block> ore(String name, float hardness, float resistance) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(hardness, resistance)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()));
    }
}
