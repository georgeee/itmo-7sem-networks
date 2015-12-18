package ru.ifmo.ctd.year2012.sem7.networks.lab3.transmission;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.ifmo.ctd.year2012.sem7.networks.lab3.Settings;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.DatagramPacket;
import java.net.MulticastSocket;

@Component
public class Sender extends Thread {
    private static final Logger log = LoggerFactory.getLogger(Sender.class);

    @Autowired
    private Settings settings;

    private PipedInputStream inputStream;

    @Getter
    private PipedOutputStream outputStream;

    @PostConstruct
    private void init() {
        try {
            inputStream = new PipedInputStream();
            outputStream = new PipedOutputStream(inputStream);
        } catch (IOException e) {
            log.error("Error initializing piped streams", e);
        }
    }

    public void run() {
        try (MulticastSocket socket = new MulticastSocket()) {
            log.info("[Sender] opened socket at port {}", socket.getLocalPort());
            byte[] buf = new byte[settings.getPacketSize() * 2];
            while (true) {
                if (Thread.interrupted()) {
                    log.info("[Sender] interrupted, exiting...");
                    break;
                }
                int read = inputStream.read(buf, 0, settings.getPacketSize());
                if (read == -1) {
                    log.info("[Sender] EOF received, exiting...");
                    break;
                }
                DatagramPacket packet = new DatagramPacket(buf, read, settings.getGroupAddress(), settings.getUdpPort());
                socket.send(packet);
                log.debug("[Sender] Sending packet of size {} (group {}, port {}) ...", read, settings.getGroupAddress(), settings.getUdpPort());
            }
        } catch (IOException e) {
            log.error("[Sender] I/O Error occurred", e);
        }
    }

}
