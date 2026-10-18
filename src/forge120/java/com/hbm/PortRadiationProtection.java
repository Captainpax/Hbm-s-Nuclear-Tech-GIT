package com.hbm;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The first HazmatRegistry slice: source coefficients and 10^-resistance dose attenuation. */
public final class PortRadiationProtection {
    private PortRadiationProtection() {}

    public static double resistance(Player player) {
        return resistance(player.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD)
                + resistance(player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)
                + resistance(player.getItemBySlot(EquipmentSlot.LEGS), EquipmentSlot.LEGS)
                + resistance(player.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET);
    }

    static double resistance(ItemStack stack, EquipmentSlot slot) {
        Item item = stack.getItem();
        if (slot == EquipmentSlot.HEAD) {
            if (item == PortItems.GAS_MASK.get()) return 0.07D;
            if (item == PortItems.GAS_MASK_M65.get()) return 0.095D;
        }
        double part = switch (slot) {
            case HEAD -> 0.2D;
            case CHEST -> 0.4D;
            case LEGS -> 0.3D;
            case FEET -> 0.1D;
            default -> 0.0D;
        };
        if (item == ironItem(slot) || item == goldItem(slot)) return 0.0225D * part;
        return 0.0D;
    }

    public static float doseMultiplier(LivingEntity entity) {
        return entity instanceof Player player ? (float) Math.pow(10.0D, -resistance(player)) : 1.0F;
    }

    private static Item ironItem(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Items.IRON_HELMET;
            case CHEST -> Items.IRON_CHESTPLATE;
            case LEGS -> Items.IRON_LEGGINGS;
            case FEET -> Items.IRON_BOOTS;
            default -> Items.AIR;
        };
    }

    private static Item goldItem(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Items.GOLDEN_HELMET;
            case CHEST -> Items.GOLDEN_CHESTPLATE;
            case LEGS -> Items.GOLDEN_LEGGINGS;
            case FEET -> Items.GOLDEN_BOOTS;
            default -> Items.AIR;
        };
    }
}
