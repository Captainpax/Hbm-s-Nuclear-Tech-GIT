package com.hbm;

import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Capacity is per store; insertion order determines the source pouring order. */
public final class PortMaterialStore {
    private final int capacity;
    private final Runnable changed;
    private final LinkedHashMap<PortMaterial, Integer> contents = new LinkedHashMap<>();
    public PortMaterialStore(int capacity) { this(capacity, () -> {}); }
    public PortMaterialStore(int capacity, Runnable changed) { this.capacity = capacity; this.changed = changed; }
    public int capacity() { return capacity; }
    public int amount(PortMaterial material) { return contents.getOrDefault(material, 0); }
    public int total() { return contents.values().stream().mapToInt(Integer::intValue).sum(); }
    public List<PortMaterialStack> contents() {
        return contents.entrySet().stream().map(e -> new PortMaterialStack(e.getKey(), e.getValue())).toList();
    }
    public int add(PortMaterial material, int offered) {
        int accepted = Math.min(Math.max(0, offered), capacity - total());
        if (accepted > 0) { contents.merge(material, accepted, Integer::sum); changed.run(); }
        return accepted;
    }
    public int remove(PortMaterial material, int requested) {
        int removed = Math.min(Math.max(0, requested), amount(material));
        int left = amount(material) - removed;
        if (left == 0) contents.remove(material);
        else contents.put(material, left);
        if (removed > 0) changed.run();
        return removed;
    }
    public List<ItemStack> takeScraps() {
        List<ItemStack> scraps = contents().stream().map(s -> PortScrapsItem.of(s.material(), s.amount())).toList();
        contents.clear();
        if (!scraps.isEmpty()) changed.run();
        return scraps;
    }
    public ListTag save() {
        ListTag tag = new ListTag();
        contents.forEach((material, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("material", material.key());
            entry.putInt("amount", amount);
            tag.add(entry);
        });
        return tag;
    }
    public void load(ListTag tag) {
        contents.clear();
        for (Tag entryTag : tag) {
            if (!(entryTag instanceof CompoundTag entry) || !entry.contains("amount", Tag.TAG_INT)) continue;
            PortMaterial material;
            try { material = PortMaterial.byKey(entry.getString("material")); }
            catch (IllegalArgumentException invalid) { continue; }
            add(material, entry.getInt("amount"));
        }
    }
}
