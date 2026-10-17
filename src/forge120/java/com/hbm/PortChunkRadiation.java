package com.hbm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Default legacy-style scalar radiation per chunk, persisted separately for each dimension. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortChunkRadiation extends SavedData {
    private static final String DATA_NAME = "hbm_chunk_radiation";
    private static final float MAX_RADIATION = 100_000.0F;
    private static final Map<ServerLevel, Set<Long>> LOADED = new ConcurrentHashMap<>();
    private final Map<Long, Float> radiation = new HashMap<>();

    public PortChunkRadiation() {}

    public static PortChunkRadiation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PortChunkRadiation::load,
                PortChunkRadiation::new, DATA_NAME);
    }

    public static float at(ServerLevel level, BlockPos pos) {
        if (!PortConfig.ENABLE_CHUNK_RADS.get()
                || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return 0.0F;
        return get(level).at(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    public static void set(ServerLevel level, BlockPos pos, float amount) {
        if (!PortConfig.ENABLE_CHUNK_RADS.get()
                || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        get(level).set(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), amount);
    }

    private void set(long chunk, float amount) {
        float clamped = Float.isFinite(amount) ? Math.max(0.0F, Math.min(MAX_RADIATION, amount)) : 0.0F;
        if (clamped == radiation.getOrDefault(chunk, 0.0F)) return;
        if (clamped == 0.0F) radiation.remove(chunk);
        else radiation.put(chunk, clamped);
        setDirty();
    }

    float at(long chunk) {
        return radiation.getOrDefault(chunk, 0.0F);
    }

    public static PortChunkRadiation load(CompoundTag tag) {
        PortChunkRadiation data = new PortChunkRadiation();
        ListTag entries = tag.getList("radiation", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            float value = entry.getFloat("rad");
            if (Float.isFinite(value) && value > 0.0F) {
                data.radiation.put(entry.getLong("chunk"), Math.min(MAX_RADIATION, value));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        radiation.forEach((chunk, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("chunk", chunk);
            entry.putFloat("rad", value);
            entries.add(entry);
        });
        tag.put("radiation", entries);
        return tag;
    }

    /** Spread to loaded neighbors only; never request a chunk during simulation. */
    void spread(Set<Long> loaded) {
        Map<Long, Float> contributions = new HashMap<>();
        for (long source : loaded) {
            float amount = radiation.getOrDefault(source, 0.0F);
            if (amount <= 0.0F) continue;
            ChunkPos position = new ChunkPos(source);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long target = ChunkPos.asLong(position.x + dx, position.z + dz);
                    if (!loaded.contains(target)) continue;
                    int type = Math.abs(dx) + Math.abs(dz);
                    float portion = type == 0 ? 0.6F : type == 1 ? 0.075F : 0.025F;
                    contributions.merge(target, amount * portion, Float::sum);
                }
            }
        }
        for (long chunk : loaded) {
            set(chunk, contributions.getOrDefault(chunk, 0.0F) * 0.99F - 0.05F);
        }
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED.computeIfAbsent(level, ignored -> ConcurrentHashMap.newKeySet())
                    .add(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Set<Long> loaded = LOADED.get(level);
            if (loaded != null) loaded.remove(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LOADED.remove(level);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !PortConfig.ENABLE_CHUNK_RADS.get()) return;
        for (Map.Entry<ServerLevel, Set<Long>> entry : LOADED.entrySet()) {
            ServerLevel level = entry.getKey();
            if (level.getGameTime() % 20 == 0 && !entry.getValue().isEmpty()) {
                get(level).spread(Set.copyOf(entry.getValue()));
            }
        }
    }
}
