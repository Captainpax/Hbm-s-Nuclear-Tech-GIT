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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Resolves loaded HE conductor components once per level tick; cables never store or create HE. */
@Mod.EventBusSubscriber(modid = HbmPort.MOD_ID)
public final class PortPowerGrid {
    private static final WeakHashMap<ServerLevel, TickState> STATES = new WeakHashMap<>();

    private PortPowerGrid() {}

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    static void tick(ServerLevel level, PortPowerEndpoint endpoint) {
        TickState state = STATES.computeIfAbsent(level, ignored -> new TickState());
        if (state.gameTime != level.getGameTime()) state.reset(level.getGameTime());
        BlockPos pos = endpoint.blockEntity().getBlockPos();

        if (endpoint instanceof PortBatterySocketBlockEntity && !state.processedConductors.contains(pos)) {
            PowerComponent component = findComponent(level, pos);
            state.processedConductors.addAll(component.conductors());
            transfer(level, state, component.endpoints());
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            if (direction.getAxis().isVertical()
                    && (endpoint instanceof PortBatterySocketBlockEntity
                            || level.getBlockState(neighbor).getBlock() instanceof PortBatterySocketBlock)) continue;
            if (isConductor(level, neighbor) && !state.processedConductors.contains(neighbor)) {
                PowerComponent component = findComponent(level, neighbor);
                state.processedConductors.addAll(component.conductors());
                transfer(level, state, component.endpoints());
            }
        }

        // Legacy machines also connect directly without a cable. An unordered
        // edge is processed once even when both block entities tick this frame.
        if (endpoint instanceof PortBatteryBlockEntity battery) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!level.hasChunkAt(neighbor)
                        || !(level.getBlockEntity(neighbor) instanceof PortBatteryBlockEntity other)) continue;
                DirectEdge edge = DirectEdge.of(pos, neighbor);
                if (state.processedDirectEdges.add(edge)) transfer(level, state, List.of(battery, other));
            }
        }
    }

    private static boolean isConductor(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PortCableBlock) return true;
        if (state.getBlock() instanceof PortBatterySocketBlock) {
            BlockPos core = PortBatterySocketBlock.corePos(pos, state);
            return level.hasChunkAt(core)
                    && level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity;
        }
        return state.getBlock() instanceof PortBatteryBlock
                && level.getBlockEntity(pos) instanceof PortBatteryBlockEntity battery
                && battery.mode(level) == PortBatteryBlockEntity.BUFFER;
    }

    private static PowerComponent findComponent(ServerLevel level, BlockPos start) {
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> conductors = new HashSet<>();
        LinkedHashSet<PortPowerEndpoint> endpoints = new LinkedHashSet<>();
        frontier.add(start);
        conductors.add(start);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.removeFirst();
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof PortBatterySocketBlock) {
                BlockPos core = PortBatterySocketBlock.corePos(pos, state);
                if (level.hasChunkAt(core)
                        && level.getBlockEntity(core) instanceof PortBatterySocketBlockEntity socket) {
                    endpoints.add(socket);
                }
            } else if (state.getBlock() instanceof PortBatteryBlock
                    && level.getBlockEntity(pos) instanceof PortBatteryBlockEntity battery) {
                endpoints.add(battery);
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                // Do not ask for a block entity or state in an unloaded chunk:
                // crossing the border must neither force-load it nor transfer HE.
                if (!level.hasChunkAt(neighbor)) continue;
                if (direction.getAxis().isVertical()
                        && (state.getBlock() instanceof PortBatterySocketBlock
                                || level.getBlockState(neighbor).getBlock() instanceof PortBatterySocketBlock)) continue;
                if (isConductor(level, neighbor)) {
                    if (conductors.add(neighbor)) frontier.add(neighbor);
                } else if (level.getBlockState(neighbor).getBlock() instanceof PortBatteryBlock
                        && level.getBlockEntity(neighbor) instanceof PortBatteryBlockEntity battery) {
                    endpoints.add(battery);
                }
            }
        }
        return new PowerComponent(conductors, List.copyOf(endpoints));
    }

    private static void transfer(ServerLevel level, TickState state, List<PortPowerEndpoint> endpoints) {
        List<PortPowerNetwork.Provider> providers = new ArrayList<>();
        List<PortPowerNetwork.Receiver> receivers = new ArrayList<>();
        IdentityHashMap<PortPowerReservoir, PortPowerEndpoint> owners = new IdentityHashMap<>();
        for (PortPowerEndpoint endpoint : endpoints) {
            if (endpoint.blockEntity().isRemoved() || endpoint.blockEntity().getLevel() != level) continue;
            short mode = endpoint.mode(level);
            PortPowerReservoir storage = endpoint.reservoir();
            owners.put(storage, endpoint);
            long output = Math.max(0, endpoint.providerSpeed() - state.usedOutput.getOrDefault(endpoint, 0L));
            long input = Math.max(0, endpoint.receiverSpeed() - state.usedInput.getOrDefault(endpoint, 0L));
            if (output > 0 && (mode == PortBatteryBlockEntity.OUTPUT || mode == PortBatteryBlockEntity.BUFFER)) {
                providers.add(new PortPowerNetwork.Provider(storage, output));
            }
            if (input > 0 && (mode == PortBatteryBlockEntity.INPUT || mode == PortBatteryBlockEntity.BUFFER)) {
                receivers.add(new PortPowerNetwork.Receiver(storage, input, endpoint.priority()));
            }
        }
        if (providers.isEmpty() || receivers.isEmpty()) return;
        Set<PortPowerEndpoint> changed = new HashSet<>();
        PortPowerNetwork.transfer(providers, receivers, (source, destination, amount) -> {
            PortPowerEndpoint output = owners.get(source);
            PortPowerEndpoint input = owners.get(destination);
            state.usedOutput.merge(output, amount, Long::sum);
            state.usedInput.merge(input, amount, Long::sum);
            changed.add(output);
            changed.add(input);
        });
        for (PortPowerEndpoint endpoint : changed) endpoint.onPowerTransferred();
    }

    private record PowerComponent(Set<BlockPos> conductors, List<PortPowerEndpoint> endpoints) {}

    private record DirectEdge(long first, long second) {
        private static DirectEdge of(BlockPos a, BlockPos b) {
            long x = a.asLong();
            long y = b.asLong();
            return Long.compareUnsigned(x, y) <= 0 ? new DirectEdge(x, y) : new DirectEdge(y, x);
        }
    }

    private static final class TickState {
        private long gameTime = Long.MIN_VALUE;
        private final Set<BlockPos> processedConductors = new HashSet<>();
        private final Set<DirectEdge> processedDirectEdges = new HashSet<>();
        private final IdentityHashMap<PortPowerEndpoint, Long> usedOutput = new IdentityHashMap<>();
        private final IdentityHashMap<PortPowerEndpoint, Long> usedInput = new IdentityHashMap<>();

        private void reset(long time) {
            gameTime = time;
            processedConductors.clear();
            processedDirectEdges.clear();
            usedOutput.clear();
            usedInput.clear();
        }
    }
}
