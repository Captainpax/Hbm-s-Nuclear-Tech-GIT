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
    public static final ForgeConfigSpec.IntValue ORE_SULFUR_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_FLUORITE_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_NITER_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_ALUMINIUM_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_ASBESTOS_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_RARE_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_LIGNITE_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_CINNEBAR_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_COBALT_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_GNEISS_LITHIUM_COUNT;
    public static final ForgeConfigSpec.IntValue ORE_GNEISS_GAS_COUNT;
    public static final ForgeConfigSpec.IntValue CLUSTER_IRON_COUNT;
    public static final ForgeConfigSpec.IntValue CLUSTER_TITANIUM_COUNT;
    public static final ForgeConfigSpec.IntValue CLUSTER_ALUMINIUM_COUNT;
    public static final ForgeConfigSpec.IntValue CLUSTER_COPPER_COUNT;

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
        ORE_SULFUR_COUNT = oreCount(builder, "2.02_sulfurSpawnrate", 5);
        ORE_FLUORITE_COUNT = oreCount(builder, "2.05_fluoriteSpawnrate", 6);
        ORE_NITER_COUNT = oreCount(builder, "2.06_niterSpawnrate", 6);
        ORE_ALUMINIUM_COUNT = oreCount(builder, "2.03_aluminiumSpawnrate", 7);
        ORE_ASBESTOS_COUNT = oreCount(builder, "2.12_asbestosSpawnRate", 2);
        ORE_LIGNITE_COUNT = oreCount(builder, "2.11_ligniteSpawnrate", 2);
        ORE_RARE_COUNT = oreCount(builder, "2.14_rareEarthSpawnRate", 6);
        ORE_CINNEBAR_COUNT = oreCount(builder, "2.18_cinnebarSpawnRate", 1);
        ORE_COBALT_COUNT = oreCount(builder, "2.18_cobaltSpawnRate", 2);
        ORE_GNEISS_LITHIUM_COUNT = oreCount(builder, "2.13_lithiumSpawnRate", 6);
        ORE_GNEISS_GAS_COUNT = oreCount(builder, "2.16_gasShaleSpawnRate", 5);
        CLUSTER_IRON_COUNT = oreCount(builder, "2.C00_ironClusterSpawn", 4);
        CLUSTER_TITANIUM_COUNT = oreCount(builder, "2.C01_titaniumClusterSpawn", 2);
        CLUSTER_ALUMINIUM_COUNT = oreCount(builder, "2.C02_aluminiumClusterSpawn", 3);
        CLUSTER_COPPER_COUNT = oreCount(builder, "2.C03_copperClusterSpawn", 4);
        builder.pop();
        SPEC = builder.build();
    }

    private PortConfig() {}

    private static ForgeConfigSpec.IntValue oreCount(ForgeConfigSpec.Builder builder, String key, int defaultCount) {
        return builder.comment("Ore vein placement attempts per new Overworld chunk; zero disables this ore.")
                .defineInRange(key, defaultCount, 0, Integer.MAX_VALUE);
    }

    static int oreCount(String ore) {
        return switch (ore) {
            case "gneiss_iron" -> 25;
            case "gneiss_gold" -> 10;
            case "gneiss_uranium" -> triple(ORE_URANIUM_COUNT.get());
            case "gneiss_copper" -> triple(ORE_COPPER_COUNT.get());
            case "gneiss_asbestos" -> triple(ORE_ASBESTOS_COUNT.get());
            case "gneiss_lithium" -> ORE_GNEISS_LITHIUM_COUNT.get();
            case "gneiss_rare" -> ORE_RARE_COUNT.get();
            case "gneiss_gas" -> triple(ORE_GNEISS_GAS_COUNT.get());
            default -> oreCountSetting(ore).get();
        };
    }

    static void validateOreCountKey(String ore) {
        switch (ore) {
            case "gneiss_iron", "gneiss_gold", "gneiss_uranium", "gneiss_copper",
                    "gneiss_asbestos", "gneiss_lithium", "gneiss_rare", "gneiss_gas" -> { return; }
            default -> oreCountSetting(ore);
        }
    }

    private static int triple(int count) {
        return (int) Math.min((long) count * 3L, Integer.MAX_VALUE);
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
            case "sulfur" -> ORE_SULFUR_COUNT;
            case "fluorite" -> ORE_FLUORITE_COUNT;
            case "niter" -> ORE_NITER_COUNT;
            case "aluminium" -> ORE_ALUMINIUM_COUNT;
            case "asbestos" -> ORE_ASBESTOS_COUNT;
            case "rare" -> ORE_RARE_COUNT;
            case "lignite" -> ORE_LIGNITE_COUNT;
            case "cinnebar" -> ORE_CINNEBAR_COUNT;
            case "cobalt" -> ORE_COBALT_COUNT;
            case "cluster_iron" -> CLUSTER_IRON_COUNT;
            case "cluster_titanium" -> CLUSTER_TITANIUM_COUNT;
            case "cluster_aluminium" -> CLUSTER_ALUMINIUM_COUNT;
            case "cluster_copper" -> CLUSTER_COPPER_COUNT;
            default -> throw new IllegalArgumentException("Unknown HBM ore placement: " + ore);
        };
    }
}
