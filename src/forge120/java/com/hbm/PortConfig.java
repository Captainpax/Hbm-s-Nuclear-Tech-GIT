package com.hbm;

import net.minecraftforge.common.ForgeConfigSpec;

/** First server-synced legacy config slice; remaining options are in the parity inventory. */
public final class PortConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAMINATION;
    public static final ForgeConfigSpec.BooleanValue DISABLE_ASBESTOS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("radiation");
        ENABLE_CONTAMINATION = builder
                .comment("Toggles radiation contamination; legacy negative effects are still being ported.")
                .define("RADIATION_00_enableContamination", true);
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
