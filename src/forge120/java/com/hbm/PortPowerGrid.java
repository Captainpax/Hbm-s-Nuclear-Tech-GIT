package com.hbm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Resolves loaded classic-cable components once per level tick; cables never store or create HE. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortPowerGrid {
    private static final WeakHashMap<ServerLevel, TickState> STATES = new WeakHashMap<>();

    private PortPowerGrid() {}

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    static void tick(ServerLevel level, PortBatteryBlockEntity battery) {
        TickState state = STATES.computeIfAbsent(level, ignored -> new TickState());
        if (state.gameTime != level.getGameTime()) state.reset(level.getGameTime());
        BlockPos pos = battery.getBlockPos();

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            if (level.getBlockState(neighbor).getBlock() instanceof PortCableBlock
                    && !state.processedCables.contains(neighbor)) {
                CableComponent component = findComponent(level, neighbor);
                state.processedCables.addAll(component.cables());
                transfer(level, state, component.batteries());
            }
        }

        // Legacy machines also connect directly without a cable. An unordered
        // edge is processed once even when both block entities tick this frame.
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)
                    || !(level.getBlockEntity(neighbor) instanceof PortBatteryBlockEntity other)) continue;
            DirectEdge edge = DirectEdge.of(pos, neighbor);
            if (state.processedDirectEdges.add(edge)) transfer(level, state, List.of(battery, other));
        }
    }

    private static CableComponent findComponent(ServerLevel level, BlockPos start) {
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> conductors = new HashSet<>();
        Set<BlockPos> cables = new HashSet<>();
        LinkedHashSet<PortBatteryBlockEntity> batteries = new LinkedHashSet<>();
        frontier.add(start);
        conductors.add(start);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.removeFirst();
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockState(pos).getBlock() instanceof PortCableBlock) cables.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                // Do not ask for a block entity or state in an unloaded chunk:
                // crossing the border must neither force-load it nor transfer HE.
                if (!level.hasChunkAt(neighbor)) continue;
                if (level.getBlockState(neighbor).getBlock() instanceof PortCableBlock) {
                    if (conductors.add(neighbor)) frontier.add(neighbor);
                } else if (level.getBlockEntity(neighbor) instanceof PortBatteryBlockEntity battery) {
                    batteries.add(battery);
                    if (battery.mode(level.hasNeighborSignal(neighbor)) == PortBatteryBlockEntity.BUFFER
                            && conductors.add(neighbor)) frontier.add(neighbor);
                }
            }
        }
        return new CableComponent(cables, List.copyOf(batteries));
    }

    private static void transfer(ServerLevel level, TickState state, List<PortBatteryBlockEntity> batteries) {
        List<PortPowerNetwork.Provider> providers = new ArrayList<>();
        List<PortPowerNetwork.Receiver> receivers = new ArrayList<>();
        IdentityHashMap<PortPowerStorage, PortBatteryBlockEntity> owners = new IdentityHashMap<>();
        for (PortBatteryBlockEntity battery : batteries) {
            if (battery.isRemoved() || battery.getLevel() != level) continue;
            BlockPos pos = battery.getBlockPos();
            short mode = battery.mode(level.hasNeighborSignal(pos));
            PortPowerStorage storage = battery.storage();
            owners.put(storage, battery);
            long output = Math.max(0, battery.capacity() / 600 - state.usedOutput.getOrDefault(battery, 0L));
            long input = Math.max(0, battery.capacity() / 200 - state.usedInput.getOrDefault(battery, 0L));
            if (output > 0 && (mode == PortBatteryBlockEntity.OUTPUT || mode == PortBatteryBlockEntity.BUFFER)) {
                providers.add(new PortPowerNetwork.Provider(storage, output));
            }
            if (input > 0 && (mode == PortBatteryBlockEntity.INPUT || mode == PortBatteryBlockEntity.BUFFER)) {
                receivers.add(new PortPowerNetwork.Receiver(storage, input, battery.priority()));
            }
        }
        if (providers.isEmpty() || receivers.isEmpty()) return;
        Set<PortBatteryBlockEntity> changed = new HashSet<>();
        PortPowerNetwork.transfer(providers, receivers, (source, destination, amount) -> {
            PortBatteryBlockEntity output = owners.get(source);
            PortBatteryBlockEntity input = owners.get(destination);
            state.usedOutput.merge(output, amount, Long::sum);
            state.usedInput.merge(input, amount, Long::sum);
            changed.add(output);
            changed.add(input);
        });
        for (PortBatteryBlockEntity battery : changed) battery.onPowerTransferred();
    }

    private record CableComponent(Set<BlockPos> cables, List<PortBatteryBlockEntity> batteries) {}

    private record DirectEdge(long first, long second) {
        private static DirectEdge of(BlockPos a, BlockPos b) {
            long x = a.asLong();
            long y = b.asLong();
            return Long.compareUnsigned(x, y) <= 0 ? new DirectEdge(x, y) : new DirectEdge(y, x);
        }
    }

    private static final class TickState {
        private long gameTime = Long.MIN_VALUE;
        private final Set<BlockPos> processedCables = new HashSet<>();
        private final Set<DirectEdge> processedDirectEdges = new HashSet<>();
        private final IdentityHashMap<PortBatteryBlockEntity, Long> usedOutput = new IdentityHashMap<>();
        private final IdentityHashMap<PortBatteryBlockEntity, Long> usedInput = new IdentityHashMap<>();

        private void reset(long time) {
            gameTime = time;
            processedCables.clear();
            processedDirectEdges.clear();
            usedOutput.clear();
            usedInput.clear();
        }
    }
}
