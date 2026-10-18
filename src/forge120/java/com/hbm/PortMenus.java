package com.hbm;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PortMenus {
    public static final DeferredRegister<MenuType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, HbmPort.MOD_ID);

    public static final RegistryObject<MenuType<PortBatteryMenu>> BATTERY =
            TYPES.register("machine_battery", () -> IForgeMenuType.create(PortBatteryMenu::new));

    public static final RegistryObject<MenuType<PortBatterySocketMenu>> BATTERY_SOCKET =
            TYPES.register("machine_battery_socket", () -> IForgeMenuType.create(PortBatterySocketMenu::new));
    public static final RegistryObject<MenuType<PortElectricFurnaceMenu>> ELECTRIC_FURNACE =
            TYPES.register("machine_electric_furnace", () -> IForgeMenuType.create(PortElectricFurnaceMenu::new));
    public static final RegistryObject<MenuType<PortPressMenu>> PRESS =
            TYPES.register("machine_press", () -> IForgeMenuType.create(PortPressMenu::new));

    private PortMenus() {}
}
