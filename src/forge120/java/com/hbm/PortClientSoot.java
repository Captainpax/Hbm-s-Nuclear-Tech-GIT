package com.hbm;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class PortClientSoot {
    private static final PortSootFog FOG = new PortSootFog();
    private static Object displayedLevel;
    private static Object displayedPlayer;

    private PortClientSoot() {}

    private static void checkContext() {
        var minecraft = Minecraft.getInstance();
        if (displayedLevel != minecraft.level || displayedPlayer != minecraft.player) {
            FOG.reset();
            displayedLevel = minecraft.level;
            displayedPlayer = minecraft.player;
        }
    }

    public static void receive(PortNetwork.SootSync packet) {
        checkContext();
        if (Minecraft.getInstance().player != null) FOG.receive(packet.soot());
    }

    private static boolean enabled() {
        return Minecraft.getInstance().level != null && PortConfig.ENABLE_POLLUTION.get()
                && PortConfig.ENABLE_SOOT_FOG.get();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        checkContext();
        if (enabled() && !Minecraft.getInstance().isPaused()) FOG.tick();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fogDistance(ViewportEvent.RenderFog event) {
        if (!enabled() || FOG.excess() <= 0) return;
        event.setNearPlaneDistance(0);
        event.setFarPlaneDistance(FOG.farPlane(Minecraft.getInstance().options.renderDistance().get() * 16F));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!enabled()) return;
        float amount = FOG.opacity();
        event.setRed(event.getRed() * (1 - amount) + 0.15F * amount);
        event.setGreen(event.getGreen() * (1 - amount) + 0.15F * amount);
        event.setBlue(event.getBlue() * (1 - amount) + 0.15F * amount);
    }
}
