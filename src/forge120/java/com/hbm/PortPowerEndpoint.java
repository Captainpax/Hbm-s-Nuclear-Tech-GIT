package com.hbm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The machine-facing side of a cable network, independent of its storage type. */
public interface PortPowerEndpoint {
    BlockEntity blockEntity();

    PortPowerReservoir reservoir();

    short mode(ServerLevel level);

    PortPowerNetwork.Priority priority();

    long providerSpeed();

    long receiverSpeed();

    void onPowerTransferred();
}
