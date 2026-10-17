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

    private PortMenus() {}
}
