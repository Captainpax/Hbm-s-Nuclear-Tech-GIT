package com.hbm;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Soot's source spawn modifiers and lung irritation; other pollutants remain separate work. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortSootEffects {
    static final UUID MAX_HEALTH = UUID.fromString("25462f6c-2cb2-4ca8-9b47-3a011cc61207");
    static final UUID ATTACK_DAMAGE = UUID.fromString("8f442d7c-d03f-49f6-a040-249ae742eed9");
    private static final String PENDING_SPAWN = "hbmSootSpawnPending";

    private PortSootEffects() {}

    @SubscribeEvent
    public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Enemy) {
            // Generation can finalize mobs off-thread. Defer SavedData access
            // until the first server living tick, after vanilla spawn attributes.
            event.getEntity().getPersistentData().putBoolean(PENDING_SPAWN, true);
        }
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        if (entity.getPersistentData().getBoolean(PENDING_SPAWN)) {
            entity.getPersistentData().remove(PENDING_SPAWN);
            decorateMob(entity);
        }
        if (entity instanceof Player player) {
            int period = coughPeriod(player);
            if (period > 0 && level.getGameTime() % period == player.getId() % period) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        PortSounds.COUGH.get(), SoundSource.PLAYERS, 1F, 1F);
            }
        }
    }

    static void decorateMob(LivingEntity entity) {
        if (!(entity instanceof Enemy) || !(entity.level() instanceof ServerLevel level)
                || !PortConfig.ENABLE_POLLUTION.get()
                || PortSootPollution.get(level).at(entity.blockPosition()) <= PortConfig.SOOT_MOB_THRESHOLD.get()) return;
        var health = entity.getAttribute(Attributes.MAX_HEALTH);
        var attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (health != null && health.getModifier(MAX_HEALTH) == null) {
            health.addPermanentModifier(new AttributeModifier(MAX_HEALTH, "Soot Anger Health Increase",
                    1D, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        if (attack != null && attack.getModifier(ATTACK_DAMAGE) == null) {
            attack.addPermanentModifier(new AttributeModifier(ATTACK_DAMAGE, "Soot Anger Damage Increase",
                    1.5D, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        entity.setHealth(entity.getMaxHealth());
    }

    static int coughPeriod(Player player) {
        if (player.isCreative() || !(player.level() instanceof ServerLevel level)) return 0;
        float soot = PortGasMask.hasCoarseParticleProtection(player) ? 0
                : PortSootPollution.get(level).at(player.blockPosition());
        double asbestos = PortRadiation.get(player).map(data ->
                Math.min(data.asbestos(), PortRadiation.MAX_ASBESTOS) / (double) PortRadiation.MAX_ASBESTOS).orElse(0D);
        if (asbestos <= 0.25D && soot <= 30) return 0;
        double total = 1 - (1 - asbestos) * (1 - Math.min(soot / 100D, 1D));
        return Math.max((int) (1000 - 950 * total), 20);
    }
}
