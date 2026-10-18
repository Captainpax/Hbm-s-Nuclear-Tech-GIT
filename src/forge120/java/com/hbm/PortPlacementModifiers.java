package com.hbm;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Placement modifier types used by the HBM worldgen datapack. */
public final class PortPlacementModifiers {
    public static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, HbmPort.MOD_ID);

    public static final RegistryObject<PlacementModifierType<PortOreCountPlacement>> ORE_COUNT =
            TYPES.register("ore_count", () -> () -> PortOreCountPlacement.CODEC);
    public static final RegistryObject<PlacementModifierType<PortOverworldPlacement>> OVERWORLD_ONLY =
            TYPES.register("overworld_only", () -> () -> PortOverworldPlacement.CODEC);

    private PortPlacementModifiers() {}
}
