package com.hbm;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;

/** Material recipes run independently of item crafting, at the source tick frequency. */
public final class PortCrucibleRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final int frequency;
    private final Map<PortMaterial, Integer> input, output;
    private final ItemStack icon;

    public PortCrucibleRecipe(ResourceLocation id, int frequency, Map<PortMaterial, Integer> input,
            Map<PortMaterial, Integer> output, ItemStack icon) {
        if (frequency < 1 || input.isEmpty() || output.isEmpty() || icon.isEmpty()) {
            throw new IllegalArgumentException("Crucible recipe needs positive frequency, materials and an icon");
        }
        long in = validate(input), out = validate(output);
        if (in > PortCrucibleBlockEntity.STORE_CAPACITY || out > in) {
            throw new IllegalArgumentException("Crucible recipe exceeds capacity or expands material");
        }
        this.id = id;
        this.frequency = frequency;
        this.input = Collections.unmodifiableMap(new LinkedHashMap<>(input));
        this.output = Collections.unmodifiableMap(new LinkedHashMap<>(output));
        this.icon = icon.copyWithCount(1);
    }
    private static long validate(Map<PortMaterial, Integer> materials) {
        long total = 0;
        for (var entry : materials.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Invalid crucible recipe material");
            }
            total += entry.getValue();
        }
        return total;
    }
    public int frequency() { return frequency; }
    public Map<PortMaterial, Integer> input() { return input; }
    public Map<PortMaterial, Integer> output() { return output; }
    public int inputAmount() { return input.values().stream().mapToInt(Integer::intValue).sum(); }
    public boolean uses(PortMaterial material) { return input.containsKey(material) || output.containsKey(material); }
    public boolean apply(PortMaterialStore store, long time) {
        if (time % frequency != 0) return false;
        for (var entry : input.entrySet()) if (store.amount(entry.getKey()) < entry.getValue()) return false;
        input.forEach(store::remove);
        output.forEach(store::add);
        return true;
    }
    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return icon.copy(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeType<?> getType() { return PortCrucibleRecipes.TYPE.get(); }
    @Override public RecipeSerializer<?> getSerializer() { return PortCrucibleRecipes.SERIALIZER.get(); }

    public static final class Serializer implements RecipeSerializer<PortCrucibleRecipe> {
        @Override public PortCrucibleRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new PortCrucibleRecipe(id, GsonHelper.getAsInt(json, "frequency"),
                    materials(json, "input"), materials(json, "output"),
                    CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "icon"), true));
        }
        private static Map<PortMaterial, Integer> materials(JsonObject json, String key) {
            Map<PortMaterial, Integer> materials = new LinkedHashMap<>();
            for (var element : GsonHelper.getAsJsonArray(json, key)) {
                JsonObject entry = element.getAsJsonObject();
                PortMaterial material = PortMaterial.byKey(GsonHelper.getAsString(entry, "material"));
                if (materials.put(material, GsonHelper.getAsInt(entry, "amount")) != null) {
                    throw new IllegalArgumentException("Duplicate crucible material: " + material);
                }
            }
            return materials;
        }
        @Override public PortCrucibleRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new PortCrucibleRecipe(id, buffer.readVarInt(), readMaterials(buffer), readMaterials(buffer), buffer.readItem());
        }
        private static Map<PortMaterial, Integer> readMaterials(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 1 || count > PortMaterial.values().length) throw new IllegalArgumentException("Invalid material count");
            Map<PortMaterial, Integer> materials = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                if (materials.put(buffer.readEnum(PortMaterial.class), buffer.readVarInt()) != null) {
                    throw new IllegalArgumentException("Duplicate crucible material");
                }
            }
            return materials;
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, PortCrucibleRecipe recipe) {
            buffer.writeVarInt(recipe.frequency);
            writeMaterials(buffer, recipe.input);
            writeMaterials(buffer, recipe.output);
            buffer.writeItem(recipe.icon);
        }
        private static void writeMaterials(FriendlyByteBuf buffer, Map<PortMaterial, Integer> materials) {
            buffer.writeVarInt(materials.size());
            materials.forEach((material, amount) -> { buffer.writeEnum(material); buffer.writeVarInt(amount); });
        }
    }
}
