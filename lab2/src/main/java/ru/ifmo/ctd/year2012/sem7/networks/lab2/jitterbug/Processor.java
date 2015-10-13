package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class Processor<D extends Data<D>> extends Thread implements State<D> {
    private final static Logger log = LoggerFactory.getLogger(MessageService.class);
    private final static Random RANDOM = new Random(System.currentTimeMillis());
    private final Context<D> context;
    private final BlockingQueue<Event> queue;

    @Getter
    private volatile D data;

    /**
     * Token id
     * Positive value means that we are a leader
     * Negative - that we don't
     */
    @Getter
    private volatile int tokenId;

    Processor(Context<D> context) {
        this.context = context;
        queue = new ArrayBlockingQueue<>(context.getSettings().getQueueCapacity());
        data = context.getSettings().getInitialData();
        tokenId = generateTokenId();
    }

    @Override
    public void rememberNode(InetAddress address, int tcpPort) {

    }

    @Override
    public void reportTR2(InetAddress senderAddress, int tokenId) {

    }

    @Override
    public void handleSocketConnection(Socket socket) {
        queue.add(new TPReceivedEvent(socket));
    }

    private int generateTokenId() {
        int randInt = 0;
        while (randInt == 0) {
            randInt = RANDOM.nextInt();
        }
        return (randInt > 0) ? -randInt : randInt;
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.interrupted();
                    break;
                }
                Event event = queue.take();
                if (event instanceof TPReceivedEvent) {
                    Socket socket = ((TPReceivedEvent) event).getSocket();
                    //...
                }
            }
        } catch (InterruptedException e) {
            log.debug("Processor was interrupted");
        }
    }

    private interface Event {
    }

    private static class TPReceivedEvent implements Event {
        @Getter
        private final Socket socket;

        public TPReceivedEvent(Socket socket) {
            this.socket = socket;
        }
    }
}
