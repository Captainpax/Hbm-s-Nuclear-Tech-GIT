package com.hbm;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Entry point for the 1.20.1 port. Legacy implementation lives in src/main. */
@Mod(HbmPort.MOD_ID)
public final class HbmPort {
    public static final String MOD_ID = "hbm";

    public HbmPort() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        PortBlocks.BLOCKS.register(modBus);
        PortItems.ITEMS.register(modBus);
        PortCreativeTabs.TABS.register(modBus);
        modBus.addListener(PortRadiation::registerCapabilities);
        PortNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PortConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PortClientConfig.SPEC);
    }
}
