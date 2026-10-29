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

    public static final RegistryObject<BlockEntityType<PortBatterySocketBlockEntity>> BATTERY_SOCKET =
            TYPES.register("machine_battery_socket", () -> BlockEntityType.Builder.of(
                    PortBatterySocketBlockEntity::new, PortBlocks.MACHINE_BATTERY_SOCKET.get()).build(null));

    public static final RegistryObject<BlockEntityType<PortBatterySocketProxyBlockEntity>> BATTERY_SOCKET_PROXY =
            TYPES.register("machine_battery_socket_proxy", () -> BlockEntityType.Builder.of(
                    PortBatterySocketProxyBlockEntity::new, PortBlocks.MACHINE_BATTERY_SOCKET.get()).build(null));
    public static final RegistryObject<BlockEntityType<PortElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
            TYPES.register("machine_electric_furnace", () -> BlockEntityType.Builder.of(
                    PortElectricFurnaceBlockEntity::new, PortBlocks.MACHINE_ELECTRIC_FURNACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<PortPressBlockEntity>> PRESS =
            TYPES.register("machine_press", () -> BlockEntityType.Builder.of(
                    PortPressBlockEntity::new, PortBlocks.MACHINE_PRESS.get()).build(null));
    public static final RegistryObject<BlockEntityType<PortBlastFurnaceBlockEntity>> BLAST_FURNACE =
            TYPES.register("machine_blast_furnace", () -> BlockEntityType.Builder.of(
                    PortBlastFurnaceBlockEntity::new, PortBlocks.MACHINE_BLAST_FURNACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<PortFireboxBlockEntity>> FIREBOX =
            TYPES.register("heater_firebox", () -> BlockEntityType.Builder.of(
                    PortFireboxBlockEntity::new, PortBlocks.HEATER_FIREBOX.get()).build(null));
    public static final RegistryObject<BlockEntityType<PortBlastFurnacePortBlockEntity>> BLAST_FURNACE_PORT =
            TYPES.register("machine_blast_furnace_port", () -> BlockEntityType.Builder.of(
                    PortBlastFurnacePortBlockEntity::new, PortBlocks.MACHINE_BLAST_FURNACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<PortCrucibleBlockEntity>> CRUCIBLE =
            TYPES.register("machine_crucible", () -> BlockEntityType.Builder.of(
                    PortCrucibleBlockEntity::new, PortBlocks.MACHINE_CRUCIBLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<PortFoundryMoldBlockEntity>> FOUNDRY_MOLD =
            TYPES.register("foundry_mold", () -> BlockEntityType.Builder.of(
                    PortFoundryMoldBlockEntity::new, PortBlocks.FOUNDRY_MOLD.get()).build(null));

    private PortBlockEntities() {}
}
