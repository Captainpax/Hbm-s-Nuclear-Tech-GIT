package com.hbm;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A named 1.20.1 form of the legacy long-valued HE battery item. */
public final class PortBatteryItem extends Item {
    private final long capacity;
    private final long chargeRate;
    private final long dischargeRate;

    public PortBatteryItem(Properties properties, long capacity, long chargeRate, long dischargeRate) {
        super(properties.stacksTo(1));
        if (capacity <= 0 || chargeRate < 0 || dischargeRate < 0) {
            throw new IllegalArgumentException("Invalid HE battery parameters");
        }
        this.capacity = capacity;
        this.chargeRate = chargeRate;
        this.dischargeRate = dischargeRate;
    }

    public long capacity() {
        return capacity;
    }

    public long chargeRate() {
        return chargeRate;
    }

    public long dischargeRate() {
        return dischargeRate;
    }

    /** Legacy ItemBattery defaults to full charge before a charge tag is written. */
    public long charge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? capacity : Math.max(0L, Math.min(capacity, tag.getLong("charge")));
    }

    public void setCharge(ItemStack stack, long charge) {
        stack.getOrCreateTag().putLong("charge", Math.max(0L, Math.min(capacity, charge)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hbm.battery_charge", charge(stack), capacity));
        tooltip.add(Component.translatable("tooltip.hbm.battery_charge_rate", chargeRate));
        tooltip.add(Component.translatable("tooltip.hbm.battery_discharge_rate", dischargeRate));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return charge(stack) < capacity;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * charge(stack) / capacity);
    }
}
