package com.hbm;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Legacy radiation sickness thresholds. Entity transformations are a later slice. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortRadiationEffects {
    private static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(HbmPort.MOD_ID, "radiation"));

    private PortRadiationEffects() {}

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.isAlive() || !PortConfig.ENABLE_CONTAMINATION.get()) return;
        if (entity instanceof Player player && player.isCreative()) return;
        PortRadiation.get(entity).ifPresent(data -> apply(entity, data));
    }

    static void apply(LivingEntity entity, PortRadiation.Exposure data) {
        apply(entity, data, entity.getRandom());
    }

    static void apply(LivingEntity entity, PortRadiation.Exposure data, RandomSource random) {
        if (PortRadiation.isRadiationImmune(entity)) return;
        float rads = data.radiation();
        if (rads < 200.0F) return;
        if (rads >= 1000.0F) {
            data.addRadiation(-2500.0F);
            var damageType = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(RADIATION_DAMAGE);
            entity.hurt(new DamageSource(damageType), 1000.0F);
            return;
        }

        if (rads >= 800.0F) {
            chance(entity, random, 300, MobEffects.CONFUSION, 150, 0);
            chance(entity, random, 300, MobEffects.MOVEMENT_SLOWDOWN, 200, 2);
            chance(entity, random, 300, MobEffects.WEAKNESS, 200, 2);
            chance(entity, random, 500, MobEffects.POISON, 60, 2);
            chance(entity, random, 700, MobEffects.WITHER, 60, 1);
        } else if (rads >= 600.0F) {
            chance(entity, random, 300, MobEffects.CONFUSION, 150, 0);
            chance(entity, random, 300, MobEffects.MOVEMENT_SLOWDOWN, 200, 2);
            chance(entity, random, 300, MobEffects.WEAKNESS, 200, 2);
            chance(entity, random, 500, MobEffects.POISON, 60, 1);
        } else if (rads >= 400.0F) {
            chance(entity, random, 300, MobEffects.CONFUSION, 150, 0);
            chance(entity, random, 500, MobEffects.MOVEMENT_SLOWDOWN, 100, 0);
            chance(entity, random, 300, MobEffects.WEAKNESS, 100, 1);
        } else {
            chance(entity, random, 300, MobEffects.CONFUSION, 100, 0);
            chance(entity, random, 500, MobEffects.WEAKNESS, 100, 0);
        }
    }

    private static void chance(LivingEntity entity, RandomSource random, int denominator,
                               MobEffect effect, int duration, int amplifier) {
        if (random.nextInt(denominator) == 0) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
        }
    }
}
