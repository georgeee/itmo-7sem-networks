package ru.ifmo.ctd.year2012.sem7.networks.lab2;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug.Settings;

import javax.annotation.PostConstruct;
import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class JitterbugSettings implements Settings<PiComputation> {
    private static final Logger log = LoggerFactory.getLogger(JitterbugSettings.class);
    @Getter
    @Value("${jitterbug.udpPort:30041}")
    private int udpPort;
    @Getter
    private NetworkInterface networkInterface;
    @Getter
    @Value("${jitterbug.iface:}")
    private String interfaceName;
    @Getter
    @Value("${jitterbug.queueCapacity:20}")
    private int queueCapacity;
    @Getter
    @Value("${jitterbug.executorPoolSize:5}")
    private int executorPoolSize;

    @Value("${jitterbug.preferIPv6:false}")
    private boolean preferIPv6;

    @Getter
    @Value("${jitterbug.dataComputationDelay:0}")
    private long dataComputationDelay;

    @Getter
    @Value("${jitterbug.trInitTimeout:3000}")
    private int trInitTimeout;
    @Getter
    @Value("${jitterbug.tpTimeout:1000}")
    private int tpTimeout;
    @Getter
    @Value("${jitterbug.tr1Delay:200}")
    private int tr1Delay;
    @Getter
    @Value("${jitterbug.tr1Repeat:5}")
    private int tr1Repeat;

    @Getter
    private InetAddress selfAddress;

    @Getter
    private Set<InetAddress> selfAddresses;

    @Override
    public PiComputation getInitialData() {
        return new PiComputation();
    }

    @PostConstruct
    public void init() {
        try {
            Enumeration<NetworkInterface> ifaceEnumeration = NetworkInterface.getNetworkInterfaces();
            while (ifaceEnumeration.hasMoreElements()) {
                NetworkInterface iface = ifaceEnumeration.nextElement();
                if (networkInterface == null && interfaceName.equals(iface.getDisplayName())) {
                    networkInterface = iface;
                }
            }
        } catch (SocketException e) {
            log.warn("Exception caught while initializing interface", e);
        }
        if (networkInterface != null) {
            List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
            if (interfaceAddresses.isEmpty()) {
                throw new IllegalStateException("Empty interface address list");
            }
        }
        selfAddress = computeSelfAddress();
        selfAddresses = computeSelfAddresses();
    }

    private Set<InetAddress> computeSelfAddresses() {
        Set<InetAddress> addresses = new HashSet<>();
        for (InterfaceAddress ifaceAddr : getNetworkInterface().getInterfaceAddresses()) {
            addresses.add(ifaceAddr.getAddress());
        }
        return addresses;
    }

    private InetAddress computeSelfAddress() {
        InetAddress result = null;
        for (InterfaceAddress ifaceAddr : getNetworkInterface().getInterfaceAddresses()) {
            InetAddress address = ifaceAddr.getAddress();
            if (result == null) {
                result = address;
            } else {
                if (preferIPv6) {
                    if (result instanceof Inet4Address && address instanceof Inet6Address) {
                        result = address;
                    }
                } else {
                    if (result instanceof Inet6Address && address instanceof Inet4Address) {
                        result = address;
                    }
                }
            }
        }
        return result;
    }
}
