package com.hbm;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** First tier-1 construction recipes, consumed from the player's inventory like AnvilCraftPacket. */
public final class PortAnvilConstruction {
    public record Requirement(Supplier<Item> item, int count) {}
    public record Recipe(int tier, Supplier<ItemStack> output, List<Requirement> requirements) {}

    private PortAnvilConstruction() {}

    public static List<Recipe> recipes() {
        return List.of(
                new Recipe(1, () -> new ItemStack(PortItems.STAMP_IRON_PLATE.get()),
                        List.of(new Requirement(PortItems.STAMP_IRON_FLAT::get, 1))),
                new Recipe(1, () -> new ItemStack(PortItems.STAMP_IRON_WIRE.get()),
                        List.of(new Requirement(PortItems.STAMP_IRON_FLAT::get, 1))),
                new Recipe(1, () -> new ItemStack(PortItems.MACHINE_BLAST_FURNACE.get()),
                        List.of(new Requirement(() -> Blocks.STONE_BRICKS.asItem(), 4),
                                new Requirement(PortItems.INGOT_FIREBRICK::get, 32),
                                new Requirement(PortItems.PLATE_COPPER::get, 8))));
    }

    public static boolean craft(Player player, int tier, int recipeIndex, boolean bulk) {
        List<Recipe> recipes = recipes();
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return false;
        Recipe recipe = recipes.get(recipeIndex);
        if (recipe.tier() > tier) return false;
        int attempts = bulk ? Math.max(1, 64 / recipe.output().get().getCount()) : 1;
        boolean made = false;
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (!hasRequirements(player, recipe)) break;
            for (Requirement requirement : recipe.requirements()) {
                int remaining = requirement.count();
                for (int slot = 0; slot < player.getInventory().items.size() && remaining > 0; slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.is(requirement.item().get()) && stack.getDamageValue() == 0) {
                        int taken = Math.min(remaining, stack.getCount());
                        player.getInventory().removeItem(slot, taken);
                        remaining -= taken;
                    }
                }
            }
            ItemStack result = recipe.output().get();
            if (!player.getInventory().add(result)) player.drop(result, false);
            made = true;
        }
        player.getInventory().setChanged();
        return made;
    }

    private static boolean hasRequirements(Player player, Recipe recipe) {
        for (Requirement requirement : recipe.requirements()) {
            int found = 0;
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(requirement.item().get()) && stack.getDamageValue() == 0) found += stack.getCount();
            }
            if (found < requirement.count()) return false;
        }
        return true;
    }
}
