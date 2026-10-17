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

    private PortItems() {}
}
