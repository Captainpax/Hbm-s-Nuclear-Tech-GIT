package com.hbm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only Geiger display, using the original counter overlay texture. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortClientRadiation {
    private static final ResourceLocation OVERLAY = new ResourceLocation(HbmPort.MOD_ID,
            "textures/misc/overlay_misc.png");
    private static long lastSurvey;
    private static float previous;
    private static float latest;
    private static Player displayedPlayer;
    private static boolean receivedInitialSync;

    private PortClientRadiation() {}

    public static void receive(PortNetwork.ExposureSync packet) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player != displayedPlayer) {
            displayedPlayer = player;
            receivedInitialSync = false;
        }
        if (!receivedInitialSync) {
            receivedInitialSync = true;
            previous = packet.radiation();
            latest = packet.radiation();
            lastSurvey = System.currentTimeMillis();
        }
        PortRadiation.get(player).ifPresent(packet::applyTo);
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "geiger", (gui, graphics, partialTick,
                width, height) -> render(graphics, width, height));
    }

    private static void render(GuiGraphics graphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui
                || !player.getInventory().contains(new ItemStack(PortItems.GEIGER_COUNTER.get()))) return;
        if (player != displayedPlayer) {
            displayedPlayer = player;
            lastSurvey = 0L;
            previous = 0.0F;
            latest = 0.0F;
            receivedInitialSync = false;
        }
        PortRadiation.get(player).ifPresent(data -> {
            float dose = PortConfig.ENABLE_CONTAMINATION.get() ? data.radiation() : 0.0F;
            long now = System.currentTimeMillis();
            if (now >= lastSurvey + 1000L) {
                lastSurvey = now;
                previous = latest;
                latest = dose;
            }
            float rate = Math.max(0.0F, latest - previous);
            int x = 16 + PortClientConfig.GEIGER_OFFSET_HORIZONTAL.get();
            int y = height - 20 - PortClientConfig.GEIGER_OFFSET_VERTICAL.get();
            int bar = Math.min(74, (int) (dose / 1000.0F * 74));
            graphics.blit(OVERLAY, x, y, 0, 0, 94, 18, 256, 256);
            if (bar > 0) graphics.blit(OVERLAY, x + 1, y + 1, 1, 19, bar, 16, 256, 256);
            if (rate >= 25.0F) graphics.blit(OVERLAY, x + 76, y - 18, 36, 36, 18, 18, 256, 256);
            else if (rate >= 10.0F) graphics.blit(OVERLAY, x + 76, y - 18, 18, 36, 18, 18, 256, 256);
            else if (rate >= 2.5F) graphics.blit(OVERLAY, x + 76, y - 18, 0, 36, 18, 18, 256, 256);
            if (rate > 0.0F) {
                String label = rate > 1000.0F ? ">1000 RAD/s"
                        : rate >= 1.0F ? Math.round(rate) + " RAD/s" : "<1 RAD/s";
                graphics.drawString(minecraft.font, label, x, y - 8, 0xFF0000, false);
            }
        });
    }
}
