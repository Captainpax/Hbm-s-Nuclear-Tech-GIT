package com.hbm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            float chunk = PortChunkRadiation.at(server, player.blockPosition());
            player.sendSystemMessage(Component.literal("===== ☢ ")
                    .append(Component.translatable("geiger.title"))
                    .append(" ☢ =====").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(reading("geiger.chunkRad", chunk, " RAD/s"));
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
