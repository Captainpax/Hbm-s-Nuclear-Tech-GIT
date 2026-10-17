package com.hbm;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Displays the HE retained in a mined legacy battery. */
public final class PortBatteryBlockItem extends BlockItem {
    public PortBatteryBlockItem(PortBatteryBlock block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        long capacity = ((PortBatteryBlock) getBlock()).capacity();
        CompoundTag root = stack.getTag();
        long power = root != null && root.contains("persistent", 10)
                ? root.getCompound("persistent").getLong("power") : 0;
        tooltip.add(Component.translatable("tooltip.hbm.battery_charge",
                Math.max(0L, Math.min(capacity, power)), capacity));
        tooltip.add(Component.translatable("tooltip.hbm.battery_charge_rate", capacity / 200));
        tooltip.add(Component.translatable("tooltip.hbm.battery_discharge_rate", capacity / 600));
    }
}
