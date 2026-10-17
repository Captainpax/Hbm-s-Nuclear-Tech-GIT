package com.hbm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The first ported filter-bearing helmet. Specialized legacy rendering is still pending. */
public final class PortGasMask extends ArmorItem {
    private static final String FILTER_KEY = "hfrFilter";

    public PortGasMask(Properties properties) {
        super(ArmorMaterials.IRON, Type.HELMET, properties);
    }

    public static ItemStack filter(ItemStack mask) {
        if (!mask.is(PortItems.GAS_MASK.get())) return ItemStack.EMPTY;
        CompoundTag tag = mask.getTag();
        if (tag == null || !tag.contains(FILTER_KEY, 10)) return ItemStack.EMPTY;
        ItemStack filter = ItemStack.of(tag.getCompound(FILTER_KEY));
        return filter.is(PortItems.GAS_MASK_FILTER.get()) ? filter : ItemStack.EMPTY;
    }

    public static void installFilter(ItemStack mask, ItemStack filter) {
        if (!mask.is(PortItems.GAS_MASK.get()) || !filter.is(PortItems.GAS_MASK_FILTER.get())) return;
        mask.getOrCreateTag().put(FILTER_KEY, filter.save(new CompoundTag()));
    }

    public static void removeFilter(ItemStack mask) {
        if (mask.getTag() != null) mask.getTag().remove(FILTER_KEY);
    }

    /** Matches legacy radon: consume one point while protected, including the last usable point. */
    public static boolean protectFromFineParticles(LivingEntity entity) {
        ItemStack mask = entity.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack filter = filter(mask);
        if (filter.isEmpty()) return false;
        int newDamage = filter.getDamageValue() + 1;
        if (newDamage > filter.getMaxDamage()) {
            removeFilter(mask);
        } else {
            filter.setDamageValue(newDamage);
            installFilter(mask, filter);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack mask = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || filter(mask).isEmpty()) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide) {
            ItemStack removed = filter(mask);
            removeFilter(mask);
            if (!player.getInventory().add(removed)) player.drop(removed, true);
        }
        return InteractionResultHolder.sidedSuccess(mask, level.isClientSide);
    }
}
