package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * States:
 * * leader: token_id > 0
 * * waiter: token_id < 0 && (System.currentTimeMillis() <= {renew_timeout} + lastLivenessEventTime)
 * * orphan: token_id < 0 && (System.currentTimeMillis() > {renew_timeout} + lastLivenessEventTime)
 *
 * @param <D> type of data (application-defined)
 */
class Processor<D extends Data<D>> extends Thread implements State<D> {
    private static final Logger log = LoggerFactory.getLogger(Processor.class);
    private final Context<D> context;
    private final BlockingQueue<Event> eventQueue;

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
        eventQueue = new ArrayBlockingQueue<>(context.getSettings().getQueueCapacity());
        data = context.getSettings().getInitialData();
        tokenId = generateTokenId();
    }

    private void rememberNode(Node node) {
        //@TODO
    }

    @Override
    public void rememberNode(int hostId, InetAddress address, int tcpPort) {
        rememberNode(new Node(hostId, address, tcpPort));
    }

    @Override
    public void reportTR2(InetAddress senderAddress, int tokenId) {
        //@TODO
    }

    @Override
    public void handleSocketConnection(Socket socket) {
        //@TODO
    }

    private int generateTokenId() {
        int randInt = 0;
        while (randInt == 0) {
            randInt = context.getRandom().nextInt();
        }
        return (randInt > 0) ? -randInt : randInt;
    }

    @Override
    public void run() {
        //@TODO init timeouts
        while (true) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                log.debug("Processor was interrupted");
                break;
            }
            Event event;
            try {
                event = eventQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Processor was interrupted");
                break;
            }
            //@TODO determine event type, perform appropriate action
            //if(event instanceof (..)){..}
        }
    }

    private interface Event {
    }

    private static class TRInitiateEvent implements Event {
    }

}
