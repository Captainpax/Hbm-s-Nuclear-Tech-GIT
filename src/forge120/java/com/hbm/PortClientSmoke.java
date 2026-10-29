package com.hbm;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Developer/CI client smoke: stop after model baking and the first menu screen. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class PortClientSmoke {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean finished;

    private PortClientSmoke() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("hbm.port.clientSmoke")
                || !PortBatterySocketRenderer.modelsAudited()
                || !PortMachineObjRenderer.modelsAudited()
                || !PortFoundryMoldRenderer.modelsAudited()) return;
        Minecraft minecraft = Minecraft.getInstance();
        // A fresh game directory opens the accessibility onboarding screen before the title screen.
        if (minecraft.screen == null || minecraft.level != null) return;
        if (minecraft.getItemRenderer().getModel(new ItemStack(PortItems.PLATE_POLYMER.get()),
                null, null, 0) == minecraft.getModelManager().getMissingModel()) {
            throw new IllegalStateException("Insulator inventory model is missing");
        }
        for (var item : new net.minecraft.world.item.Item[] {
                PortItems.ORE_ASBESTOS.get(), PortItems.BLOCK_ASBESTOS.get(),
                PortItems.INGOT_ASBESTOS.get(), PortItems.POWDER_ASBESTOS.get(),
                PortItems.ORE_RARE.get(), PortItems.ORE_LIGNITE.get(),
                PortItems.ORE_CINNEBAR.get(), PortItems.ORE_COBALT.get(),
                PortItems.CHUNK_ORE_RARE.get(), PortItems.LIGNITE.get(),
                PortItems.CINNEBAR.get(), PortItems.FRAGMENT_COBALT.get(),
                PortItems.INGOT_COBALT.get(), PortItems.CLUSTER_IRON.get(),
                PortItems.CLUSTER_TITANIUM.get(), PortItems.CLUSTER_ALUMINIUM.get(),
                PortItems.CLUSTER_COPPER.get(), PortItems.CRYSTAL_IRON.get(),
                PortItems.CRYSTAL_TITANIUM.get(), PortItems.CRYSTAL_ALUMINIUM.get(),
                PortItems.CRYSTAL_COPPER.get(), PortItems.INGOT_ALUMINIUM.get(),
                PortItems.STONE_GNEISS.get(), PortItems.ORE_GNEISS_IRON.get(),
                PortItems.ORE_GNEISS_GOLD.get(), PortItems.ORE_GNEISS_URANIUM.get(),
                PortItems.ORE_GNEISS_URANIUM_SCORCHED.get(), PortItems.ORE_GNEISS_COPPER.get(),
                PortItems.ORE_GNEISS_ASBESTOS.get(), PortItems.ORE_GNEISS_LITHIUM.get(),
                PortItems.ORE_GNEISS_SCHRABIDIUM.get(), PortItems.ORE_GNEISS_RARE.get(),
                PortItems.ORE_GNEISS_GAS.get(), PortItems.LITHIUM.get(),
                PortItems.INGOT_SCHRABIDIUM.get(),
                PortItems.STONE_RESOURCE_LIMESTONE.get(), PortItems.STONE_DEPTH.get(),
                PortItems.CLUSTER_DEPTH_IRON.get(), PortItems.CLUSTER_DEPTH_TITANIUM.get(),
                PortItems.CLUSTER_DEPTH_TUNGSTEN.get(), PortItems.ORE_DEPTH_CINNEBAR.get(),
                PortItems.ORE_DEPTH_ZIRCONIUM.get(), PortItems.ORE_DEPTH_BORAX.get(),
                PortItems.CRYSTAL_TUNGSTEN.get(), PortItems.NUGGET_ZIRCONIUM.get(),
                PortItems.POWDER_BORAX.get(), PortItems.BALL_FIRECLAY.get(),
                PortItems.INGOT_FIREBRICK.get(), PortItems.MACHINE_ELECTRIC_FURNACE.get(),
                PortItems.MACHINE_PRESS.get(), PortItems.STAMP_IRON_FLAT.get(),
                PortItems.STAMP_IRON_PLATE.get(), PortItems.STAMP_IRON_WIRE.get(),
                PortItems.WIRE_FINE_TUNGSTEN.get(), PortItems.WIRE_FINE_COPPER.get(),
                PortItems.WIRE_FINE_ALUMINIUM.get(), PortItems.COIL_TUNGSTEN.get(),
                PortItems.PLATE_COPPER.get(), PortItems.PLATE_ALUMINIUM.get(),
                PortItems.ANVIL_IRON.get(), PortItems.ANVIL_STEEL.get(),
                PortItems.INGOT_STEEL.get(), PortItems.PLATE_IRON.get(),
                PortItems.PLATE_STEEL.get(), PortItems.BOLT_STEEL.get(),
                PortItems.MACHINE_BLAST_FURNACE.get(), PortItems.INGOT_RAW_SLAG.get(),
                PortItems.HEATER_FIREBOX.get(), PortItems.MACHINE_CRUCIBLE.get(),
                PortItems.FOUNDRY_MOLD.get(), PortItems.MOLD_BASE.get(),
                PortItems.MOLD_PLATE_CAST.get(), PortItems.PLATE_CAST_IRON.get(),
                PortItems.PLATE_CAST_COPPER.get(), PortItems.SCRAPS_COPPER.get()
        }) {
            if (minecraft.getItemRenderer().getModel(new ItemStack(item), null, null, 0)
                    == minecraft.getModelManager().getMissingModel()) {
                throw new IllegalStateException("Ore/resource inventory model is missing: " + item);
            }
        }
        finished = true;
        LOGGER.info("HBM client smoke passed: menu loaded, ore/resource and progression inventory models, hollow foundry mold, socket, casting, blast-furnace, press and anvil OBJ layers validated");
        minecraft.stop();
    }
}
