package com.hbm;

import net.minecraft.nbt.CompoundTag;

/** Long-valued HE storage shared by the ported power network and future machines. */
public final class PortPowerStorage {
    private final long capacity;
    private long power;

    public PortPowerStorage(long capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Negative HE capacity");
        this.capacity = capacity;
    }

    public long capacity() {
        return capacity;
    }

    public long power() {
        return power;
    }

    public void setPower(long value) {
        power = Math.max(0L, Math.min(value, capacity));
    }

    /** Returns the HE actually accepted. */
    public long receive(long amount) {
        if (amount <= 0) return 0;
        long accepted = Math.min(amount, capacity - power);
        power += accepted;
        return accepted;
    }

    /** Returns the HE actually removed. */
    public long extract(long amount) {
        if (amount <= 0) return 0;
        long extracted = Math.min(amount, power);
        power -= extracted;
        return extracted;
    }

    public void save(CompoundTag tag, String key) {
        tag.putLong(key, power);
    }

    public void load(CompoundTag tag, String key) {
        setPower(tag.getLong(key));
    }
}
