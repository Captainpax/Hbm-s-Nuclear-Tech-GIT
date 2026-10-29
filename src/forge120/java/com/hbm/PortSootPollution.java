package com.hbm;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Source 64x64 pollution cells. Emissions persist; dispersion and world effects remain a later slice. */
public final class PortSootPollution extends SavedData {
    private final Map<Long, Float> soot = new HashMap<>();
    public static PortSootPollution get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PortSootPollution::load,
                PortSootPollution::new, "hbm_soot_pollution");
    }
    public float at(BlockPos pos) {
        return soot.getOrDefault(ChunkPos.asLong(pos.getX() >> 6, pos.getZ() >> 6), 0.0F);
    }
    public void add(BlockPos pos, float amount) {
        if (!PortConfig.ENABLE_POLLUTION.get() || !Float.isFinite(amount) || amount <= 0) return;
        long cell = ChunkPos.asLong(pos.getX() >> 6, pos.getZ() >> 6);
        float value = Math.min(10_000.0F, at(pos) + amount * PortConfig.POLLUTION_MULTIPLIER.get().floatValue());
        soot.put(cell, value);
        setDirty();
    }
    public static PortSootPollution load(CompoundTag tag) {
        PortSootPollution data = new PortSootPollution();
        ListTag entries = tag.getList("soot", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            float amount = entry.getFloat("amount");
            if (Float.isFinite(amount) && amount > 0) data.soot.put(entry.getLong("cell"), Math.min(10_000, amount));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        soot.forEach((cell, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("cell", cell);
            entry.putFloat("amount", amount);
            entries.add(entry);
        });
        tag.put("soot", entries);
        return tag;
    }
}
