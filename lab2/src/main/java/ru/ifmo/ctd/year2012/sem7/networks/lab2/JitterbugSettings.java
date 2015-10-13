package ru.ifmo.ctd.year2012.sem7.networks.lab2;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug.Data;
import ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug.Settings;

import javax.annotation.PostConstruct;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

@Service
public class JitterbugSettings implements Settings {
    private static final Logger log = LoggerFactory.getLogger(JitterbugSettings.class);
    @Getter
    @Value("${jitterbug.udp.port:30041}")
    private int udpPort;
    @Getter
    private NetworkInterface networkInterface;
    @Getter
    @Value("${jitterbug.udp.iface:}")
    private String interfaceName;
    @Getter
    @Value("${jitterbug.queue.capacity:20}")
    private int queueCapacity;
    @Getter
    @Value("${jitterbug.executor.poolSize:5}")
    private int executorPoolSize;

    @Override
    public Data getInitialData() {
        return new PiComputation();
    }

    @PostConstruct
    public void init() {
        try {
            Enumeration<NetworkInterface> ifaceEnumeration = NetworkInterface.getNetworkInterfaces();
            while (ifaceEnumeration.hasMoreElements()) {
                NetworkInterface iface = ifaceEnumeration.nextElement();
                if (interfaceName.equals(iface.getDisplayName())) {
                    networkInterface = iface;
                    break;
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
    }
}
