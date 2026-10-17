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

/** Server-authoritative player exposure sync; clients cannot submit radiation changes. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HbmPort.MOD_ID, "play"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    private PortNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, ExposureSync.class, ExposureSync::encode,
                ExposureSync::decode, ExposureSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 10 == 0) sync(player);
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
