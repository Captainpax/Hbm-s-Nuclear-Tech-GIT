package com.hbm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Chunk and Nether exposure applied on the server, before illness thresholds are evaluated. */
public final class PortEnvironmentRadiation {
    private PortEnvironmentRadiation() {}

    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        PortRadiation.get(entity).ifPresent(data -> {
            if (entity.tickCount % 20 == 0) data.rollEnvironmentalRadiation();
            if (PortRadiation.isRadiationImmune(entity)) return;

            float rate = PortChunkRadiation.at(level, entity.blockPosition());
            if (level.dimension() == Level.NETHER) {
                rate = Math.max(rate, PortConfig.NETHER_AMBIENT_RADS.get().floatValue());
            }
            if (rate <= 0.0F) return;
            float dose = rate / 20.0F;
            data.addEnvironmentalRadiation(dose);
            if (PortConfig.ENABLE_CONTAMINATION.get()
                    && !(entity instanceof Player player && (player.isCreative() || player.tickCount < 200))) {
                data.addRadiation(dose * PortRadiationProtection.doseMultiplier(entity));
            }
        });
    }
}
