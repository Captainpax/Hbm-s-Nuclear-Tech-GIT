package com.hbm;

import net.minecraftforge.common.ForgeConfigSpec;

/** First server-synced legacy config slice; remaining options are in the parity inventory. */
public final class PortConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAMINATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHUNK_RADS;
    public static final ForgeConfigSpec.DoubleValue NETHER_AMBIENT_RADS;
    public static final ForgeConfigSpec.BooleanValue DISABLE_ASBESTOS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("radiation");
        ENABLE_CONTAMINATION = builder
                .comment("Toggles radiation contamination; legacy negative effects are still being ported.")
                .define("RADIATION_00_enableContamination", true);
        ENABLE_CHUNK_RADS = builder
                .comment("Enable persistent chunk radiation in the default scalar system.")
                .define("RADIATION_01_enableChunkRads", true);
        NETHER_AMBIENT_RADS = builder
                .comment("Ambient Nether radiation in RAD per second.")
                .defineInRange("AMBIENT_00_nether", 0.1D, 0.0D, 100_000.0D);
        builder.pop();
        builder.push("hazards");
        DISABLE_ASBESTOS = builder
                .comment("Disable asbestos hazards.")
                .define("HAZ_00_disableAsbestos", false);
        builder.pop();
        SPEC = builder.build();
    }

    private PortConfig() {}
}
