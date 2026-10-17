package com.hbm;

import net.minecraftforge.common.ForgeConfigSpec;

/** Legacy client HUD offsets; other client preferences remain in the parity checklist. */
public final class PortClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<Integer> GEIGER_OFFSET_HORIZONTAL;
    public static final ForgeConfigSpec.ConfigValue<Integer> GEIGER_OFFSET_VERTICAL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        GEIGER_OFFSET_HORIZONTAL = builder
                .comment("Horizontal Geiger HUD offset in pixels.")
                .define("GEIGER_OFFSET_HORIZONTAL", 0);
        GEIGER_OFFSET_VERTICAL = builder
                .comment("Vertical Geiger HUD offset in pixels.")
                .define("GEIGER_OFFSET_VERTICAL", 0);
        SPEC = builder.build();
    }

    private PortClientConfig() {}
}
