package com.hbm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-owned HE, redstone modes, and direct-neighbor transfer for the legacy battery. */
public final class PortBatteryBlockEntity extends BlockEntity {
    public static final short INPUT = 0;
    public static final short BUFFER = 1;
    public static final short OUTPUT = 2;
    public static final short DISABLED = 3;

    private final PortPowerStorage storage;
    private short redLow = INPUT;
    private short redHigh = OUTPUT;

    public PortBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(PortBlockEntities.BATTERY.get(), pos, state);
        long capacity = state.getBlock() instanceof PortBatteryBlock battery ? battery.capacity() : 1_000_000L;
        storage = new PortPowerStorage(capacity);
    }

    public long power() {
        return storage.power();
    }

    public long capacity() {
        return storage.capacity();
    }

    public void setPower(long amount) {
        storage.setPower(amount);
        changed();
    }

    public void setModes(short low, short high) {
        redLow = validMode(low);
        redHigh = validMode(high);
        changed();
    }

    public short mode(boolean powered) {
        return powered ? redHigh : redLow;
    }

    public int comparatorPower() {
        if (storage.power() == 0) return 0;
        return Math.min(15, (int) ((double) storage.power() / storage.capacity() * 15D) + 1);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PortBatteryBlockEntity battery) {
        short mode = battery.mode(level.hasNeighborSignal(pos));
        if (mode != OUTPUT && mode != BUFFER) return;
        List<PortPowerNetwork.Receiver> receivers = new ArrayList<>();
        List<PortBatteryBlockEntity> destinations = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            if (!(level.getBlockEntity(neighbor) instanceof PortBatteryBlockEntity other)) continue;
            short otherMode = other.mode(level.hasNeighborSignal(neighbor));
            if (otherMode != INPUT && otherMode != BUFFER) continue;
            destinations.add(other);
            receivers.add(new PortPowerNetwork.Receiver(other.storage, Math.max(1, other.capacity() / 200),
                    PortPowerNetwork.Priority.LOW));
        }
        if (receivers.isEmpty()) return;
        long moved = PortPowerNetwork.transfer(
                List.of(new PortPowerNetwork.Provider(battery.storage, Math.max(1, battery.capacity() / 600))),
                receivers);
        if (moved == 0) return;
        battery.changed();
        for (PortBatteryBlockEntity destination : destinations) destination.changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static short validMode(short mode) {
        return mode >= INPUT && mode <= DISABLED ? mode : DISABLED;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.load(tag, "power");
        redLow = validMode(tag.getShort("redLow"));
        redHigh = tag.contains("redHigh") ? validMode(tag.getShort("redHigh")) : OUTPUT;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        storage.save(tag, "power");
        tag.putShort("redLow", redLow);
        tag.putShort("redHigh", redHigh);
    }

    public void savePersistent(CompoundTag itemTag) {
        CompoundTag data = new CompoundTag();
        storage.save(data, "power");
        data.putShort("redLow", redLow);
        data.putShort("redHigh", redHigh);
        itemTag.put("persistent", data);
    }

    public void loadPersistent(CompoundTag itemTag) {
        if (!itemTag.contains("persistent", 10)) return;
        CompoundTag data = itemTag.getCompound("persistent");
        storage.load(data, "power");
        redLow = validMode(data.getShort("redLow"));
        redHigh = validMode(data.getShort("redHigh"));
        changed();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
