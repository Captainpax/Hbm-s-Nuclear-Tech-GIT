package com.hbm;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class PortCrucibleRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, HbmPort.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, HbmPort.MOD_ID);
    public static final RegistryObject<RecipeType<PortCrucibleRecipe>> TYPE = TYPES.register("crucible", () -> new RecipeType<>() {
        @Override public String toString() { return "hbm:crucible"; }
    });
    public static final RegistryObject<RecipeSerializer<PortCrucibleRecipe>> SERIALIZER = SERIALIZERS.register("crucible", PortCrucibleRecipe.Serializer::new);
    private PortCrucibleRecipes() {}

    public static List<PortCrucibleRecipe> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(TYPE.get()).stream()
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString())).toList();
    }
    public static PortCrucibleRecipe find(Level level, String selection) {
        ResourceLocation id = ResourceLocation.tryParse(selection);
        return id == null ? null : level.getRecipeManager().byKey(id)
                .filter(recipe -> recipe instanceof PortCrucibleRecipe).map(recipe -> (PortCrucibleRecipe) recipe).orElse(null);
    }
}
