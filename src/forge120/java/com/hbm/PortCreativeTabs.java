package com.hbm;

import java.util.ArrayList;
import java.util.List;

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
                    .displayItems((parameters, output) -> {
                        output.accept(PortItems.INGOT_URANIUM.get());
                        output.accept(PortItems.INGOT_TH232.get());
                        output.accept(PortItems.INGOT_TITANIUM.get());
                        output.accept(PortItems.INGOT_COPPER.get());
                        output.accept(PortItems.INGOT_TUNGSTEN.get());
                        output.accept(PortItems.INGOT_LEAD.get());
                        output.accept(PortItems.INGOT_BERYLLIUM.get());
                        output.accept(PortItems.SULFUR.get());
                        output.accept(PortItems.FLUORITE.get());
                        output.accept(PortItems.NITER.get());
                        output.accept(PortItems.CHUNK_ORE_CRYOLITE.get());
                        output.accept(PortItems.PLATE_POLYMER.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> BLOCKS = TABS.register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hbm.blocks"))
                    .icon(() -> new ItemStack(PortItems.ORE_URANIUM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PortItems.ORE_URANIUM.get());
                        output.accept(PortItems.ORE_THORIUM.get());
                        output.accept(PortItems.ORE_TITANIUM.get());
                        output.accept(PortItems.ORE_COPPER.get());
                        output.accept(PortItems.ORE_TUNGSTEN.get());
                        output.accept(PortItems.ORE_LEAD.get());
                        output.accept(PortItems.ORE_BERYLLIUM.get());
                        output.accept(PortItems.ORE_SULFUR.get());
                        output.accept(PortItems.ORE_FLUORITE.get());
                        output.accept(PortItems.ORE_NITER.get());
                        output.accept(PortItems.ORE_ALUMINIUM.get());
                        output.accept(PortItems.GAS_RADON.get());
                        output.accept(PortItems.RED_CABLE_CLASSIC.get());
                        output.accept(PortItems.MACHINE_BATTERY_SOCKET.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> CONSUMABLES = TABS.register("consumables",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hbm.consumables"))
                    .icon(() -> new ItemStack(PortItems.GAS_MASK_FILTER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PortItems.GAS_MASK_FILTER.get());
                        output.accept(PortItems.GAS_MASK_FILTER_MONO.get());
                        output.accept(PortItems.GAS_MASK_FILTER_COMBO.get());
                        output.accept(PortItems.GAS_MASK_FILTER_RAG.get());
                        output.accept(PortItems.GAS_MASK_FILTER_PISS.get());
                        output.accept(PortItems.GEIGER_COUNTER.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ARMOR = TABS.register("armor",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hbm.armor"))
                    .icon(() -> new ItemStack(PortItems.GAS_MASK.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PortItems.GAS_MASK.get());
                        output.accept(PortItems.GAS_MASK_M65.get());
                        output.accept(PortItems.GAS_MASK_MONO.get());
                        output.accept(PortItems.GAS_MASK_OLDE.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> CONTROL = TABS.register("control",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hbm.control"))
                    .icon(() -> new ItemStack(PortItems.BATTERY_POTATO.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(PortItems.BATTERY_POTATO.get());
                        output.accept(PortItems.CUBE_POWER.get());
                        for (ItemStack stack : packDisplayStacks()) output.accept(stack);
                    })
                    .build());

    private PortCreativeTabs() {}

    static List<ItemStack> packDisplayStacks() {
        List<ItemStack> stacks = new ArrayList<>(PortItems.BATTERY_PACKS.size() * 2);
        for (var variant : PortItems.BATTERY_PACKS) {
            PortBatteryItem battery = (PortBatteryItem) variant.get();
            ItemStack empty = new ItemStack(battery);
            battery.setCharge(empty, 0);
            stacks.add(empty);
            ItemStack full = new ItemStack(battery);
            battery.setCharge(full, battery.capacity());
            stacks.add(full);
        }
        return List.copyOf(stacks);
    }
}
