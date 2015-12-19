package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sound.sampled.AudioFormat;
import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class Settings {
    private static final Logger log = LoggerFactory.getLogger(Settings.class);

    @Getter
    @Value("${lastSendTimeout:1000}")
    private int lastSendTimeout;

    @Getter
    @Value("${udpPort:30041}")
    private int udpPort;
    @Getter
    private NetworkInterface networkInterface;
    @Getter
    @Value("${iface:}")
    private String interfaceName;

    @Getter
    @Value("${send:false}")
    private boolean startSender;

    @Getter
    @Value("${receive:false}")
    private boolean startReceiver;

    @Getter
    @Value("${preferIPv6:false}")
    private boolean preferIPv6;

    @Value("${group}")
    private String groupAddressName;

    @Getter
    @Value("${packetSize:1024}")
    private int packetSize;

    @Getter
    private InetAddress selfAddress;

    @Getter
    private Set<InetAddress> selfAddresses;

    @Getter
    private InetAddress groupAddress;

    @PostConstruct
    public void init() throws UnknownHostException {
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
        if (getNetworkInterface() != null) {
            selfAddress = computeSelfAddress();
            selfAddresses = computeSelfAddresses();
        }
        groupAddress = InetAddress.getByName(groupAddressName);
    }

    public AudioFormat getAudioFormat(){
        return new AudioFormat(16000.0f, 16, 2, true, false);
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
