package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sound.sampled.AudioFormat;
import java.net.InetAddress;
import java.net.UnknownHostException;

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
    private InetAddress groupAddress;

    @PostConstruct
    public void init() throws UnknownHostException {
        groupAddress = InetAddress.getByName(groupAddressName);
    }

    public AudioFormat getAudioFormat(){
        return new AudioFormat(16000.0f, 16, 2, true, false);
    }

}
