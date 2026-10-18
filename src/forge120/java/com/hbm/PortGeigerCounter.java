package com.hbm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Server-side readout for the available legacy Geiger measurements. */
public final class PortGeigerCounter extends Item {
    public PortGeigerCounter(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || level.getGameTime() % 5 != 0) {
            return;
        }
        PortRadiation.get(living).ifPresent(data -> {
            int number = clickSoundFor(data.environmentRate(), living.getRandom());
            if (number > 0) {
                level.playSound(null, entity.blockPosition(), PortSounds.geiger(number),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        });
    }

    /** Legacy weighted sound choice, including its occasional background click. */
    static int clickSoundFor(float rate, RandomSource random) {
        if (!Float.isFinite(rate)) rate = 0.0F;
        if (rate <= 1.0E-5F) return random.nextInt(50) == 0 ? 1 : 0;
        int[] choices = new int[7];
        int count = 0;
        if (rate < 1.0F) choices[count++] = 0;
        if (rate < 5.0F) choices[count++] = 0;
        if (rate < 10.0F) choices[count++] = 1;
        if (rate > 5.0F && rate < 15.0F) choices[count++] = 2;
        if (rate > 10.0F && rate < 20.0F) choices[count++] = 3;
        if (rate > 15.0F && rate < 25.0F) choices[count++] = 4;
        if (rate > 20.0F && rate < 30.0F) choices[count++] = 5;
        if (rate > 25.0F) choices[count++] = 6;
        return choices[random.nextInt(count)];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            server.playSound(null, player.blockPosition(), PortSounds.TECH_BOOP.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            float chunk = PortChunkRadiation.at(server, player.blockPosition());
            player.sendSystemMessage(Component.literal("===== ☢ ")
                    .append(Component.translatable("geiger.title"))
                    .append(" ☢ =====").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(reading("geiger.chunkRad", chunk, " RAD/s"));
            player.sendSystemMessage(Component.translatable("geiger.resistance",
                    String.format(java.util.Locale.ROOT, "%.2f", PortRadiationProtection.resistance(player)),
                    String.format(java.util.Locale.ROOT, "%.2f",
                            (1.0D - PortRadiationProtection.doseMultiplier(player)) * 100.0D))
                    .withStyle(ChatFormatting.AQUA));
            PortRadiation.get(player).ifPresent(data -> {
                player.sendSystemMessage(reading("geiger.envRad", data.environmentRate(), " RAD/s"));
                float personal = PortConfig.ENABLE_CONTAMINATION.get() ? data.radiation() : 0.0F;
                player.sendSystemMessage(reading("geiger.playerRad", personal, " RAD"));
            });
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    private static Component reading(String key, float amount, String unit) {
        float rounded = (float) (Math.floor(amount * 10.0D) / 10.0D);
        ChatFormatting color = rounded == 0.0F ? ChatFormatting.GREEN
                : rounded < 1.0F ? ChatFormatting.YELLOW
                : rounded < 10.0F ? ChatFormatting.GOLD
                : rounded < 100.0F ? ChatFormatting.RED
                : rounded < 1000.0F ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY;
        return Component.translatable(key).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" " + rounded + unit).withStyle(color));
    }
}
