package com.hbm;

/** Source renderer smoothing and fog equations, independent of client class loading. */
final class PortSootFog {
    private float target;
    private float current;

    void receive(float soot) { target = Float.isFinite(soot) ? Math.max(0, Math.min(10_000, soot)) : 0; }
    void reset() { current = target = 0; }
    void tick() {
        if (Math.abs(current - target) < 0.05F) current = target;
        else current += current < target ? 0.05F : -0.05F;
    }
    float current() { return current; }
    float excess() { return Math.max(0, current - PortConfig.SOOT_FOG_THRESHOLD.get().floatValue()); }
    float opacity() { return Math.min(1, excess() / PortConfig.SOOT_FOG_DIVISOR.get().floatValue()); }
    float farPlane(float renderDistance) {
        return renderDistance / (1 + excess() * 5F / PortConfig.SOOT_FOG_DIVISOR.get().floatValue());
    }
}
