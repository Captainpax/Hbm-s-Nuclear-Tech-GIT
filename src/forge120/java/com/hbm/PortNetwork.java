package com.hbm;

import java.util.function.Supplier;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-owned exposure and local soot synchronization; no client pollution or radiation writes. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HbmPort.MOD_ID, "play"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    private PortNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, ExposureSync.class, ExposureSync::encode,
                ExposureSync::decode, ExposureSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, SootSync.class, SootSync::encode,
                SootSync::decode, SootSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record SootSync(float soot) {
        public SootSync {
            soot = Float.isFinite(soot) ? Math.max(0, Math.min(10_000, soot)) : 0;
        }
        public void encode(FriendlyByteBuf buffer) { buffer.writeFloat(soot); }
        public static SootSync decode(FriendlyByteBuf buffer) { return new SootSync(buffer.readFloat()); }
        public static void handle(SootSync packet, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> PortClientSoot.receive(packet)));
            }
            context.setPacketHandled(true);
        }
    }

    public record ExposureSync(float radiation, int asbestos) {
        public static ExposureSync of(PortRadiation.Exposure data) {
            return new ExposureSync(data.radiation(), data.asbestos());
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeFloat(radiation);
            buffer.writeVarInt(asbestos);
        }

        public static ExposureSync decode(FriendlyByteBuf buffer) {
            float radiation = buffer.readFloat();
            int asbestos = buffer.readVarInt();
            if (!Float.isFinite(radiation)) radiation = 0.0F;
            return new ExposureSync(Math.max(0.0F, Math.min(2500.0F, radiation)),
                    Math.max(0, Math.min(60 * 60 * 20, asbestos)));
        }

        public void applyTo(PortRadiation.Exposure data) {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("hfr_radiation", radiation);
            tag.putInt("hfr_asbestos", asbestos);
            data.load(tag);
        }

        public static void handle(ExposureSync packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> PortClientRadiation.receive(packet)));
            }
            context.setPacketHandled(true);
        }
    }

    private static void sync(ServerPlayer player) {
        PortRadiation.get(player).ifPresent(data ->
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ExposureSync.of(data)));
        syncSoot(player);
    }

    private static void syncSoot(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SootSync(
                PortSootPollution.get(player.serverLevel()).at(player.blockPosition())));
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (player.tickCount % 10 == 0) sync(player);
            else syncSoot(player);
        }
    }

    @SubscribeEvent
    public static void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void playerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
