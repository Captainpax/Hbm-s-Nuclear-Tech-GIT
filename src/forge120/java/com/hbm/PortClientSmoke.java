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
                || !PortBatterySocketRenderer.modelsAudited()) return;
        Minecraft minecraft = Minecraft.getInstance();
        // A fresh game directory opens the accessibility onboarding screen before the title screen.
        if (minecraft.screen == null || minecraft.level != null) return;
        if (minecraft.getItemRenderer().getModel(new ItemStack(PortItems.PLATE_POLYMER.get()),
                null, null, 0) == minecraft.getModelManager().getMissingModel()) {
            throw new IllegalStateException("Insulator inventory model is missing");
        }
        for (var item : new net.minecraft.world.item.Item[] {
                PortItems.ORE_ASBESTOS.get(), PortItems.BLOCK_ASBESTOS.get(),
                PortItems.INGOT_ASBESTOS.get(), PortItems.POWDER_ASBESTOS.get()
        }) {
            if (minecraft.getItemRenderer().getModel(new ItemStack(item), null, null, 0)
                    == minecraft.getModelManager().getMissingModel()) {
                throw new IllegalStateException("Asbestos inventory model is missing: " + item);
            }
        }
        finished = true;
        LOGGER.info("HBM client smoke passed: menu loaded, Insulator and asbestos inventory models, socket OBJ layers validated");
        minecraft.stop();
    }
}
