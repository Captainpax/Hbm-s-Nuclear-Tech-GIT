package com.hbm;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortClientMenus {
    private PortClientMenus() {}

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(PortMenus.BATTERY.get(), PortBatteryScreen::new);
            MenuScreens.register(PortMenus.BATTERY_SOCKET.get(), PortBatterySocketScreen::new);
            MenuScreens.register(PortMenus.ELECTRIC_FURNACE.get(), PortElectricFurnaceScreen::new);
            MenuScreens.register(PortMenus.PRESS.get(), PortPressScreen::new);
            MenuScreens.register(PortMenus.BLAST_FURNACE.get(), PortBlastFurnaceScreen::new);
            MenuScreens.register(PortMenus.ANVIL.get(), PortAnvilScreen::new);
        });
    }
}
