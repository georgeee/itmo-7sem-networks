package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.net.NetworkInterface;

public interface Settings<D extends Data<D>> {
    NetworkInterface getNetworkInterface();

    int getUdpPort();

    int getExecutorPoolSize();

    int getQueueCapacity();

    D getInitialData();
}
