package com.hbm;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-clicking a filter installs it in the worn gas mask and returns the old filter. */
public final class PortGasMaskFilter extends Item {
    public PortGasMaskFilter(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!mask.is(PortItems.GAS_MASK.get())) return InteractionResultHolder.pass(player.getItemInHand(hand));
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ItemStack old = PortGasMask.filter(mask);
            PortGasMask.installFilter(mask, held.copy());
            if (old.isEmpty()) {
                held.shrink(1);
            } else {
                player.setItemInHand(hand, old);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
