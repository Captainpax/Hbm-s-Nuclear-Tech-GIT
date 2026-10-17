package com.hbm;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Persistent living-entity exposure data, matching the legacy RAD cap and NBT keys. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortRadiation {
    public static final Capability<Exposure> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = new ResourceLocation(HbmPort.MOD_ID, "living_exposure");

    private PortRadiation() {}

    public interface Exposure {
        float radiation();
        int asbestos();
        void addRadiation(float amount);
        void addAsbestos(int amount);
        CompoundTag save();
        void load(CompoundTag tag);
    }

    private static final class Data implements Exposure {
        private float radiation;
        private int asbestos;

        @Override public float radiation() { return radiation; }
        @Override public int asbestos() { return asbestos; }

        @Override
        public void addRadiation(float amount) {
            if (Float.isFinite(amount)) radiation = Math.max(0.0F, Math.min(2500.0F, radiation + amount));
        }

        @Override
        public void addAsbestos(int amount) {
            asbestos = (int) Math.max(0L, Math.min(60L * 60 * 20, (long) asbestos + amount));
        }

        @Override
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("hfr_radiation", radiation);
            tag.putInt("hfr_asbestos", asbestos);
            return tag;
        }

        @Override
        public void load(CompoundTag tag) {
            radiation = 0;
            asbestos = 0;
            addRadiation(tag.getFloat("hfr_radiation"));
            addAsbestos(tag.getInt("hfr_asbestos"));
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final Data data = new Data();
        private final LazyOptional<Exposure> optional = LazyOptional.of(() -> data);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return CAPABILITY.orEmpty(capability, optional);
        }

        @Override public CompoundTag serializeNBT() { return data.save(); }
        @Override public void deserializeNBT(CompoundTag tag) { data.load(tag); }
        private void invalidate() { optional.invalidate(); }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(Exposure.class);
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            Provider provider = new Provider();
            event.addCapability(KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        // Legacy radiation resets on death. Returning from the End must retain it.
        if (event.isWasDeath()) return;
        Player original = event.getOriginal();
        original.reviveCaps();
        original.getCapability(CAPABILITY).ifPresent(oldData ->
                event.getEntity().getCapability(CAPABILITY).ifPresent(newData -> newData.load(oldData.save())));
        original.invalidateCaps();
    }

    public static LazyOptional<Exposure> get(LivingEntity entity) {
        return entity.getCapability(CAPABILITY);
    }

    public static void exposeRadon(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if (PortGasMask.protectFromFineParticles(entity)) return;
        get(entity).ifPresent(data -> {
            if (PortConfig.ENABLE_CONTAMINATION.get() && !isRadiationImmune(entity)
                    && !(entity instanceof Player player && (player.isCreative() || player.tickCount < 200))) {
                data.addRadiation(0.05F);
            }
            // The original gas also adds one asbestos unit without fine-particle protection.
            if (!PortConfig.DISABLE_ASBESTOS.get()) data.addAsbestos(1);
        });
    }

    static boolean isRadiationImmune(LivingEntity entity) {
        return entity instanceof Zombie || entity instanceof AbstractSkeleton
                || entity instanceof MushroomCow || entity instanceof Ocelot;
    }
}
