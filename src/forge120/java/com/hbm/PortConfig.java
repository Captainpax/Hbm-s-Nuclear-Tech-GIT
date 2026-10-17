package com.hbm;

import net.minecraftforge.common.ForgeConfigSpec;

/** First server-synced legacy config slice; remaining options are in the parity inventory. */
public final class PortConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAMINATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHUNK_RADS;
    public static final ForgeConfigSpec.DoubleValue NETHER_AMBIENT_RADS;
    public static final ForgeConfigSpec.BooleanValue DISABLE_ASBESTOS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_OVERWORLD_ORES;
    public static final ForgeConfigSpec.IntValue ORE_URANIUM_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_THORIUM_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_TITANIUM_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_COPPER_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_TUNGSTEN_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_LEAD_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_BERYLLIUM_COUNT;

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
        builder.push("oregen");
        ENABLE_OVERWORLD_ORES = builder
                .comment("Generate HBM ores in new Overworld chunks; existing chunks are unchanged.")
                .define("2.D00_overworldOres", true);
        ORE_URANIUM_COUNT = oreCount(builder, "2.00_uraniumSpawnrate", 7);
        ORE_TITANIUM_COUNT = oreCount(builder, "2.01_titaniumSpawnrate", 8);
        ORE_COPPER_COUNT = oreCount(builder, "2.04_copperSpawnrate", 12);
        ORE_TUNGSTEN_COUNT = oreCount(builder, "2.07_tungstenSpawnrate", 10);
        ORE_LEAD_COUNT = oreCount(builder, "2.08_leadSpawnrate", 6);
        ORE_BERYLLIUM_COUNT = oreCount(builder, "2.09_berylliumSpawnrate", 6);
        ORE_THORIUM_COUNT = oreCount(builder, "2.10_thoriumSpawnrate", 7);
        builder.pop();
        SPEC = builder.build();
    }

    private PortConfig() {}

    private static ForgeConfigSpec.IntValue oreCount(ForgeConfigSpec.Builder builder, String key, int defaultCount) {
        return builder.comment("Ore vein placement attempts per new Overworld chunk; zero disables this ore.")
                .defineInRange(key, defaultCount, 0, Integer.MAX_VALUE);
    }

    static int oreCount(String ore) {
        return oreCountSetting(ore).get();
    }

    static ForgeConfigSpec.IntValue oreCountSetting(String ore) {
        return switch (ore) {
            case "uranium" -> ORE_URANIUM_COUNT;
            case "thorium" -> ORE_THORIUM_COUNT;
            case "titanium" -> ORE_TITANIUM_COUNT;
            case "copper" -> ORE_COPPER_COUNT;
            case "tungsten" -> ORE_TUNGSTEN_COUNT;
            case "lead" -> ORE_LEAD_COUNT;
            case "beryllium" -> ORE_BERYLLIUM_COUNT;
            default -> throw new IllegalArgumentException("Unknown HBM ore placement: " + ore);
        };
    }
}
