package ru.georgeee.itmo.sem7.networks.lab1;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;


@Component
public class Receiver implements Runnable{
    private final static Logger log = LoggerFactory.getLogger(Receiver.class);

    @Autowired
    private Settings settings;

    @Autowired
    private ReceivedMap receivedMap;

    @Override
    public void run() {
        try {
            DatagramSocket datagramSocket = new DatagramSocket(settings.getPort());
            while (true) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }

                byte[] buf = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buf, 4096);
                datagramSocket.receive(packet);
                Message msg = Message.readFromBytes(packet.getData());
                log.debug("Received message: {}", msg);

                receivedMap.put(msg.getMacAddress(), new ImmutablePair<>(System.currentTimeMillis(), msg));
            }
        } catch (IOException e) {
            log.info("Caught exception", e);
        }
    }

}

