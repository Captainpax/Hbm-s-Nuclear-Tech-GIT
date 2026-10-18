package com.hbm;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class PortWorldgenFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, HbmPort.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> GNEISS_STRATUM =
            FEATURES.register("gneiss_stratum", PortGneissStratumFeature::new);

    private PortWorldgenFeatures() {}
}
