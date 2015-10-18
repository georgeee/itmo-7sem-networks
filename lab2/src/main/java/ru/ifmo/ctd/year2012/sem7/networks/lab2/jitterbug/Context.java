package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Context<D extends Data<D>> {
    @Getter
    private final Settings<D> settings;
    @Getter
    private final MessageService<D> messageService;
    @Getter
    private final ExecutorService executor;
    private final TRListener<D> trListener;
    private final TPListener<D> tpListener;
    private final Processor<D> processor;
    @Getter
    private final int selfTcpPort;
    @Getter
    private final int hostId;
    @Getter
    private final Node selfNode;
    private final Random random;

    public Context(Settings<D> settings) throws IOException {
        random = new Random(Long.hashCode(System.currentTimeMillis()) ^ Arrays.hashCode(settings.getNetworkInterface().getHardwareAddress()));
        this.settings = settings;
        messageService = new MessageService<>(this);
        executor = Executors.newFixedThreadPool(settings.getExecutorPoolSize());
        DatagramSocket datagramSocket = new DatagramSocket(settings.getUdpPort());
        ServerSocket serverSocket = new ServerSocket(0);
        selfTcpPort = serverSocket.getLocalPort();
        trListener = new TRListener<>(this, datagramSocket);
        tpListener = new TPListener<>(this, serverSocket);
        hostId = generateHostId();
        selfNode = new Node(hostId, settings.getSelfAddress(), selfTcpPort);
        processor = new Processor<>(this);
    }

    private int generateHostId() {
        //host_id - 4-byte integer with highest (sign) bit not used (viewing host_id as a signed integer, it should be >= 0)
        int hostId = random.nextInt();
        return hostId < 0 ? -hostId : hostId;
    }

    State<D> getState() {
        return processor;
    }

    public void start() {
        trListener.start();
        processor.start();
        tpListener.start();
    }

    public void stop() {
        executor.shutdown();
        tpListener.interrupt();
        processor.interrupt();
        //It's important that trListener stopped, cause we need to release udp port
        trListener.interrupt();
        while (true) {
            try {
                trListener.join();
                return;
            } catch (InterruptedException e) {
                trListener.interrupt();
            }
        }
    }

    public Random getRandom() {
        return random;
    }
}
