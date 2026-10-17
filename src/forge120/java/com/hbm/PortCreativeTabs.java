package com.hbm;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Restores the original parts and blocks tab grouping as content is ported. */
public final class PortCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HbmPort.MOD_ID);

    public static final RegistryObject<CreativeModeTab> PARTS = TABS.register("parts",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hbm.parts"))
                    .icon(() -> new ItemStack(PortItems.INGOT_URANIUM.get()))
                    .displayItems((parameters, output) -> output.accept(PortItems.INGOT_URANIUM.get()))
                    .build());

    public static final RegistryObject<CreativeModeTab> BLOCKS = TABS.register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hbm.blocks"))
                    .icon(() -> new ItemStack(PortItems.ORE_URANIUM.get()))
                    .displayItems((parameters, output) -> output.accept(PortItems.ORE_URANIUM.get()))
                    .build());

    private PortCreativeTabs() {}
}
