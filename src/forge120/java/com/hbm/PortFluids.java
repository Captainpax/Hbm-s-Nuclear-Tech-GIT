package com.hbm;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** First non-placeable process gases, available to Forge fluid capabilities. */
public final class PortFluids {
    public static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, HbmPort.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, HbmPort.MOD_ID);
    public static final RegistryObject<FluidType> AIRBLAST_TYPE = TYPES.register("airblast",
            () -> new FluidType(FluidType.Properties.create().density(-100).viscosity(500)) {});
    public static final RegistryObject<FluidType> FLUE_TYPE = TYPES.register("flue",
            () -> new FluidType(FluidType.Properties.create().density(-100).viscosity(500)) {});
    public static final RegistryObject<Fluid> AIRBLAST = FLUIDS.register("airblast",
            () -> new ForgeFlowingFluid.Source(airblastProperties()));
    public static final RegistryObject<Fluid> AIRBLAST_FLOWING = FLUIDS.register("airblast_flowing",
            () -> new ForgeFlowingFluid.Flowing(airblastProperties()));
    public static final RegistryObject<Fluid> FLUE = FLUIDS.register("flue",
            () -> new ForgeFlowingFluid.Source(flueProperties()));
    public static final RegistryObject<Fluid> FLUE_FLOWING = FLUIDS.register("flue_flowing",
            () -> new ForgeFlowingFluid.Flowing(flueProperties()));

    private static ForgeFlowingFluid.Properties airblastProperties() {
        return new ForgeFlowingFluid.Properties(AIRBLAST_TYPE, AIRBLAST, AIRBLAST_FLOWING);
    }
    private static ForgeFlowingFluid.Properties flueProperties() {
        return new ForgeFlowingFluid.Properties(FLUE_TYPE, FLUE, FLUE_FLOWING);
    }
    private PortFluids() {}
}
