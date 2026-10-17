package com.hbm;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block entities as their legacy machines are brought over. */
public final class PortBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HbmPort.MOD_ID);

    public static final RegistryObject<BlockEntityType<PortBatteryBlockEntity>> BATTERY =
            TYPES.register("machine_battery", () -> BlockEntityType.Builder.of(
                    PortBatteryBlockEntity::new, PortBlocks.MACHINE_BATTERY.get()).build(null));

    private PortBlockEntities() {}
}
