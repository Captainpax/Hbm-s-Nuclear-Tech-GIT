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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The soot channel of source PollutionHandler; cells are 64 blocks wide, not chunks. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortSootPollution extends SavedData {
    public static final float SOOT_PER_SECOND = 1F / 25F;
    private final Map<Long, Float> soot = new HashMap<>();
    public static PortSootPollution get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PortSootPollution::load,
                PortSootPollution::new, "hbm_soot_pollution");
    }
    public float at(BlockPos pos) {
        if (!PortConfig.ENABLE_POLLUTION.get()) return 0;
        return soot.getOrDefault(ChunkPos.asLong(pos.getX() >> 6, pos.getZ() >> 6), 0.0F);
    }
    public void add(BlockPos pos, float amount) {
        if (!PortConfig.ENABLE_POLLUTION.get() || !Float.isFinite(amount) || amount == 0) return;
        set(pos, at(pos) + amount * PortConfig.POLLUTION_MULTIPLIER.get().floatValue());
    }
    public void set(BlockPos pos, float amount) {
        if (!PortConfig.ENABLE_POLLUTION.get() || !Float.isFinite(amount)) return;
        long cell = ChunkPos.asLong(pos.getX() >> 6, pos.getZ() >> 6);
        float value = Math.max(0, Math.min(10_000.0F, amount));
        if (value == soot.getOrDefault(cell, 0F)) return;
        if (value == 0) soot.remove(cell);
        else soot.put(cell, value);
        setDirty();
    }

    /** Simultaneous source update, including unloaded cells, without requesting terrain. */
    void spread() {
        if (!PortConfig.ENABLE_POLLUTION.get() || soot.isEmpty()) return;
        Map<Long, Float> next = new HashMap<>();
        soot.forEach((cell, amount) -> {
            float retained = amount;
            if (amount > 10) {
                float sent = amount * 0.05F;
                int x = ChunkPos.getX(cell), z = ChunkPos.getZ(cell);
                next.merge(ChunkPos.asLong(x + 1, z), sent, Float::sum);
                next.merge(ChunkPos.asLong(x - 1, z), sent, Float::sum);
                next.merge(ChunkPos.asLong(x, z + 1), sent, Float::sum);
                next.merge(ChunkPos.asLong(x, z - 1), sent, Float::sum);
                retained *= 0.8F;
            }
            retained *= 0.99F;
            if (retained > 0) next.merge(cell, retained, Float::sum);
        });
        soot.clear();
        soot.putAll(next);
        setDirty();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !PortConfig.ENABLE_POLLUTION.get()) return;
        if (event.getServer().getTickCount() % 60 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) get(level).spread();
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
