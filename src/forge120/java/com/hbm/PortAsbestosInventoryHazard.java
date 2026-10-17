package com.hbm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Source ore-dictionary asbestos hazard strengths, applied once per carried slot per server tick. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortAsbestosInventoryHazard {
    private PortAsbestosInventoryHazard() {}

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        tickInventory(event.getEntity());
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (dose(stack) == 0) return;
        if (stack.is(PortItems.INGOT_ASBESTOS.get())) {
            event.getToolTip().add(Component.translatable("item.hbm.ingot_asbestos.desc")
                    .withStyle(ChatFormatting.ITALIC));
        } else if (stack.is(PortItems.POWDER_ASBESTOS.get())) {
            event.getToolTip().add(Component.translatable("item.hbm.powder_asbestos.desc")
                    .withStyle(ChatFormatting.ITALIC));
        }
        event.getToolTip().add(Component.literal("[")
                .append(Component.translatable("trait.asbestos")).append("]")
                .withStyle(ChatFormatting.WHITE));
    }

    static void tickInventory(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.isAlive() || PortConfig.DISABLE_ASBESTOS.get()) return;
        if (entity instanceof Player player) {
            for (ItemStack stack : player.getInventory().items) apply(entity, stack);
            for (ItemStack stack : player.getInventory().armor) apply(entity, stack);
            for (ItemStack stack : player.getInventory().offhand) apply(entity, stack);
        } else {
            for (ItemStack stack : entity.getHandSlots()) apply(entity, stack);
            for (ItemStack stack : entity.getArmorSlots()) apply(entity, stack);
        }
    }

    private static void apply(LivingEntity entity, ItemStack stack) {
        int amount = dose(stack);
        if (amount > 0 && entity.isAlive()) PortRadiation.exposeAsbestos(entity, amount);
    }

    static int dose(ItemStack stack) {
        if (stack.is(PortItems.INGOT_ASBESTOS.get()) || stack.is(PortItems.ORE_ASBESTOS.get())) return 1;
        if (stack.is(PortItems.POWDER_ASBESTOS.get())) return 3;
        if (stack.is(PortItems.BLOCK_ASBESTOS.get())) return 10;
        return 0;
    }
}
