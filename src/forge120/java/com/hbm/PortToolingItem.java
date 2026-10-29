package com.hbm;

import api.hbm.block.IToolable;
import api.hbm.block.IToolable.ToolType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Source tooling dispatch; failed actions do not consume durability. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortToolingItem extends PortCraftingToolItem {
    private final ToolType type;

    public PortToolingItem(ToolType type, int durability) {
        super(durability);
        this.type = type;
    }

    @SubscribeEvent
    public static void registerTools(FMLCommonSetupEvent event) {
        // Forge item-stack delegates exist only after item registration has finished.
        event.enqueueWork(() -> {
            for (var entry : PortItems.ITEMS.getEntries()) {
                if (entry.get() instanceof PortToolingItem tool) tool.type.register(new ItemStack(tool));
            }
        });
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock()
                instanceof IToolable toolable) || !toolable.onScrew(context, type)) return InteractionResult.PASS;
        ItemStack held = context.getItemInHand();
        if (!context.getLevel().isClientSide && !player.getAbilities().instabuild && held.isDamageableItem()) {
            // Source ItemStack breaks ABOVE maxDamage. Keep its Unbreaking roll,
            // but do not use the modern hurtAndBreak equality threshold.
            held.hurt(1, player.getRandom(), player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
            if (held.getDamageValue() > held.getMaxDamage()) {
                player.broadcastBreakEvent(context.getHand());
                player.awardStat(Stats.ITEM_BROKEN.get(held.getItem()));
                held.shrink(1);
                held.setDamageValue(0);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
