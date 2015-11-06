package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Set;

public interface Settings<D extends Data<D>> {
    NetworkInterface getNetworkInterface();

    int getUdpPort();

    int getExecutorPoolSize();

    int getQueueCapacity();

    D getInitialData();

    int getTrInitTimeout();

    int getTpTimeout();

    Set<InetAddress> getSelfAddresses();

    InetAddress getSelfAddress();

    int getTr1Delay();

    int getTr1Repeat();

    default int getTrPhaseTimeout(){
        return getTr1Delay() * getTr1Repeat();
    }

    long getDataComputationDelay();

    double getTokenLooseProbBase();
}
