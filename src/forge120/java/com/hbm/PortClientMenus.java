package com.hbm;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortClientMenus {
    private PortClientMenus() {}

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFDCA88, PortItems.PLATE_CAST_COPPER.get());
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(PortMenus.BATTERY.get(), PortBatteryScreen::new);
            MenuScreens.register(PortMenus.BATTERY_SOCKET.get(), PortBatterySocketScreen::new);
            MenuScreens.register(PortMenus.ELECTRIC_FURNACE.get(), PortElectricFurnaceScreen::new);
            MenuScreens.register(PortMenus.PRESS.get(), PortPressScreen::new);
            MenuScreens.register(PortMenus.BLAST_FURNACE.get(), PortBlastFurnaceScreen::new);
            MenuScreens.register(PortMenus.FIREBOX.get(), PortFireboxScreen::new);
            MenuScreens.register(PortMenus.CRUCIBLE.get(), PortCrucibleScreen::new);
            MenuScreens.register(PortMenus.ANVIL.get(), PortAnvilScreen::new);
        });
    }
}
