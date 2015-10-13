package ru.georgeee.itmo.sem7.networks.lab1;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InterfaceAddress;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

@Component
public class Sender implements Runnable {
    private final static Logger log = LoggerFactory.getLogger(Sender.class);
    @Autowired
    private Settings settings;
    @Autowired
    private ReceivedMap receivedMap;
    private volatile int sendCounter;

    public static String[] getStrategyNames() {
        return Arrays.asList(SendingStrategy.values()).stream().map(SendingStrategy::name).toArray(String[]::new);
    }

    @PostConstruct
    private void init(){
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            private int last;

            @Override
            public void run() {
                log.info("Sent {} messages", sendCounter - last);
                last = sendCounter;
            }
        }, 0, settings.getInterval(), TimeUnit.SECONDS);
    }

    public void run() {
        try {
            DatagramSocket datagramSocket = new DatagramSocket();
            while (true) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }

                settings.getSendingStrategy().function.apply(this, datagramSocket);
                sendCounter++;
            }
        } catch (SendException e) {
            if (e.getCause() instanceof IOException) {
                log.info("Caught exception", e.getCause());
            } else {
                throw e;
            }
        } catch (IOException e) {
            log.info("Caught exception", e);
        }
    }

    private Void normalSend(DatagramSocket datagramSocket) {
        try {
            Message msg = new Message(settings.getNetworkInterface().getHardwareAddress(), settings.getHostName());
            sendMessage(msg, datagramSocket);

            try {
                Thread.sleep(settings.getInterval() * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            throw new SendException(e);
        }
        return null;
    }

    private Void attack1Send(DatagramSocket datagramSocket) {
        try {
            sendMessage(new BreakingMessage(), datagramSocket);
            sleep();
        } catch (IOException e) {
            throw new SendException(e);
        }
        return null;
    }

    private Void attack2Send(DatagramSocket datagramSocket) {
        try {
            for (Pair<Long, Message> pair : receivedMap.values()) {
                long macAddress = pair.getRight().getMacAddress();
                String hostName = pair.getRight().getHostName();
                sendMessage(new Message(macAddress, hostName), datagramSocket);
            }
            sendSelfMessage(datagramSocket);
            sleep();
        } catch (IOException e) {
            throw new SendException(e);
        }
        return null;
    }

    private Void attack3Send(DatagramSocket datagramSocket) {
        try {
            sendSelfMessage(datagramSocket, true);
        } catch (IOException e) {
            throw new SendException(e);
        }
        return null;
    }

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2);

    private Void attack4Send(DatagramSocket datagramSocket) {
        try {
            sendMessage(new Message(sendCounter, settings.getHostName()), datagramSocket, true);
        } catch (IOException e) {
            throw new SendException(e);
        }
        return null;
    }

    private void sendSelfMessage(DatagramSocket datagramSocket) throws IOException {
        sendSelfMessage(datagramSocket, false);
    }

    private void sendSelfMessage(DatagramSocket datagramSocket, boolean disableLogging) throws IOException {
        Message msg = new Message(settings.getNetworkInterface().getHardwareAddress(), settings.getHostName());
        sendMessage(msg, datagramSocket, disableLogging);
    }

    private void sleep() {
        try {
            Thread.sleep(settings.getInterval() * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendMessage(Message msg, DatagramSocket datagramSocket) throws IOException {
        sendMessage(msg, datagramSocket, false);
    }

    private void sendMessage(Message msg, DatagramSocket datagramSocket, boolean disableLogging) throws IOException {
        if (!disableLogging) {
            log.debug("Sending message: {}", msg);
        }
        byte[] msgBytes = msg.getBytes();
        for (InterfaceAddress address : settings.getNetworkInterface().getInterfaceAddresses()) {
            if (address.getBroadcast() != null) {
                datagramSocket.send(new DatagramPacket(msgBytes, 0, msgBytes.length, address.getBroadcast(), settings.getPort()));
            }
        }
    }

    public enum SendingStrategy {
        NORMAL(Sender::normalSend, true, true),
        ATTACK3(Sender::attack3Send),
        ATTACK4(Sender::attack4Send),
        ATTACK1(Sender::attack1Send),
        ATTACK2(Sender::attack2Send, true, false);

        private final BiFunction<Sender, DatagramSocket, ?> function;
        @Getter
        private final boolean launchReceiver;
        @Getter
        private final boolean launchMonitor;

        SendingStrategy(BiFunction<Sender, DatagramSocket, ?> function, boolean launchReceiver, boolean launchMonitor) {
            this.function = function;
            this.launchReceiver = launchReceiver;
            this.launchMonitor = launchMonitor;
        }

        SendingStrategy(BiFunction<Sender, DatagramSocket, ?> function) {
            this(function, false, false);
        }

    }

    private static class SendException extends RuntimeException {
        public SendException(Throwable cause) {
            super("Exception caught in sender", cause);
        }
    }

}
