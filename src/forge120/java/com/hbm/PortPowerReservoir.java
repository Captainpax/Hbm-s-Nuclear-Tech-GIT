package com.hbm;

/** A server-owned HE store; capacity may depend on an installed battery item. */
public interface PortPowerReservoir {
    long capacity();

    long power();

    /** Returns the HE actually accepted. */
    long receive(long amount);

    /** Returns the HE actually removed. */
    long extract(long amount);
}
