package ru.georgeee.itmo.sem7.networks.lab1;

import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

@Component
public class Settings {
    private static final Logger log = LoggerFactory.getLogger(Settings.class);

    private final Random random = new Random(System.currentTimeMillis());

    @Getter
    @Value("${port:30041}")
    private int port;

//    @Getter
//    @Value("${addr:255.255.255.255}")
//    private InetAddress broadcastAddress;

    /**
     * Send message interval, in seconds
     */
    @Getter
    @Value("${interval:5}")
    private int interval;

    @Getter
    @Value("${missedThreshold:5}")
    private int missedThreshold;

    @Getter
    private NetworkInterface networkInterface;
    @Getter
    private String hostName = "noname-" + random.nextInt(100);

    @Getter
    @Value("${iface:}")
    private String interfaceName;

    @Value("${strategy:NORMAL}")
    private String sendingStrategyString;

    @Getter
    private Sender.SendingStrategy sendingStrategy;

    @PostConstruct
    public void init() {
        try {
            sendingStrategy = Sender.SendingStrategy.valueOf(sendingStrategyString.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendingStrategy = Sender.SendingStrategy.NORMAL;
        }
        log.info("Using sending strategy: {}", sendingStrategy);
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
        try {
            hostName = IOUtils.readLines(Runtime.getRuntime().exec("hostname").getInputStream()).get(0);
        } catch (IOException e) {
            log.warn("Exception caught while retrieving hostname", e);
        }
    }


}
