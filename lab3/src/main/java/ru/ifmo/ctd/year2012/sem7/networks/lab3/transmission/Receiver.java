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
import java.net.InetAddress;
import java.net.MulticastSocket;

@Component
public class Receiver extends Thread {
    private static final Logger log = LoggerFactory.getLogger(Receiver.class);

    @Autowired
    private Settings settings;

    @Getter
    private PipedInputStream inputStream;

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
        try (MulticastSocket socket = new MulticastSocket(settings.getUdpPort())) {
            log.info("[Receiver] opened socket at port {}", socket.getLocalPort());
            InetAddress group = settings.getGroupAddress();
            socket.joinGroup(group);
            log.info("[Receiver] joined group {}", group);
            byte[] buf = new byte[settings.getPacketSize() * 2];
            while (true) {
                if (Thread.interrupted()) {
                    break;
                }
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                log.debug("[Receiver] received a packet of size {} from {}:{}", packet.getLength(), packet.getAddress(), packet.getPort());
                outputStream.write(packet.getData(), packet.getOffset(), packet.getLength());
            }
            socket.leaveGroup(group);
        } catch (IOException e) {
            log.error("[Receiver] I/O Error occurred", e);
        }
    }

}
