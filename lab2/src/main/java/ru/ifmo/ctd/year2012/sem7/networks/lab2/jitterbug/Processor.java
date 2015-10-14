package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

/**
 * States:
 * * leader: token_id > 0
 * * waiter': token_id < 0 && waiterInTokenPass
 * when waiter participates in token pass (so it's not true waiter already, but neither is he a leader)
 * synthetic state to ignore timeouts
 * * waiter: token_id < 0 && !waiterInTokenPass && (System.currentTimeMillis() + {renew_timeout} > lastTP1Received)
 * * orphan: token_id < 0 && !waiterInTokenPass && (System.currentTimeMillis() + {renew_timeout} <= lastTP1Received)
 *
 * @param <D> type of data (application-defined)
 */
class Processor<D extends Data<D>> extends Thread implements State<D> {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final Random RANDOM = new Random(System.currentTimeMillis());
    private final Context<D> context;
    private final BlockingQueue<Event> eventQueue;

    private final Set<Node> allKnown = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Queue<Node> addQueue = new ConcurrentLinkedQueue<>();
    private final NodeList nodeList = new NodeList();
    private final MessageService<D> messageService;

    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean waiterInTokenPass;
    private volatile long lastTP1Received;

    @Getter
    private volatile D data;

    /**
     * Token id
     * Positive value means that we are a leader
     * Negative - that we don't
     */
    @Getter
    private volatile int tokenId = generateTokenId();

    Processor(Context<D> context) {
        this.context = context;
        eventQueue = new ArrayBlockingQueue<>(context.getSettings().getQueueCapacity());
        data = context.getSettings().getInitialData();
        messageService = context.getMessageService();
    }

    private void rememberNode(Node node) {
        boolean isNotKnown = allKnown.add(node);
        if (isNotKnown) {
            addQueue.add(node);
        }
    }

    @Override
    public void rememberNode(InetAddress address, int tcpPort) {
        rememberNode(new Node(address, tcpPort));
    }

    @Override
    public void reportTR2(InetAddress senderAddress, int tokenId) {

    }

    @Override
    public void handleSocketConnection(Socket socket) {
        eventQueue.add(new TPReceivedEvent(socket));
    }

    private static int generateTokenId() {
        int randInt = 0;
        while (randInt == 0) {
            randInt = RANDOM.nextInt();
        }
        return (randInt > 0) ? -randInt : randInt;
    }

    @Override
    public void run() {
        initTimeouts();
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.interrupted();
                    break;
                }
                Event event = eventQueue.take();
                if (event instanceof TPReceivedEvent) {
                    InetAddress remoteAddress = null;
                    boolean processedTokenPass = false;
                    try(Socket socket = ((TPReceivedEvent) event).getSocket()){
                        remoteAddress = socket.getInetAddress();
                        new TokenPassReceive(socket).process();
                        processedTokenPass = true;
                        socket.close();
                    } catch (IOException | ParseException e) {
                        log.info("Exception caught while communicating through socket: address={}", remoteAddress);
                    }
                    if(processedTokenPass){
                        //act as a leader
                    }
                }
            }
        } catch (InterruptedException e) {
            log.debug("Processor was interrupted");
        }
    }

    boolean needTokenRestore() {
        int trInitDelay = context.getSettings().getTRInitTimeout();
        return lastTP1Received + trInitDelay > System.currentTimeMillis();
    }

    private void initTimeouts() {
        int trInitDelay = context.getSettings().getTRInitTimeout();
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            if (needTokenRestore()) {
                eventQueue.add(new TRInitiateEvent());
            }
        }, trInitDelay, trInitDelay, TimeUnit.MILLISECONDS);
    }

    private class TokenPassReceive {
        final Socket socket;
        int newTokenId;
        List<Node> newNodes;
        D newData;
        final ObjectOutputStream oos;
        final ObjectInputStream ois;

        private TokenPassReceive(Socket socket) throws IOException, ParseException {
            this.socket = socket;
            socket.setSoTimeout(context.getSettings().getTPTimeout());
            oos = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            ois = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
        }

        void process() throws IOException, ParseException {
            try {
                messageService.handleTPMessage(ois, new TPHandler() {
                    @Override
                    public void handleTP1(int tokenId, int nodeListHash) throws IOException, ParseException {
                        newTokenId = tokenId;
                        lastTP1Received = System.currentTimeMillis();
                        waiterInTokenPass = true;
                        if (nodeListHash == nodeList.getHash()) {
                            messageService.sendTP3Message(oos);
                        } else {
                            messageService.sendTP2Message(oos);
                            messageService.handleTPMessage(ois, new TPHandler() {
                                @Override
                                public void handleTP4(List<Node> nodes) throws IOException, ParseException {
                                    newNodes = nodes;
                                }
                            });
                        }
                    }
                });
                messageService.handleTPMessage(ois, new TPHandler() {
                    @Override
                    public void handleTP5(ObjectInputStream dataStream) throws IOException, ParseException {
                        newData = data.readFromStream(dataStream);
                    }
                });
                if (data.compareTo(newData) < 0) {
                    data = newData;
                    tokenId = newTokenId;
                }
                if (newNodes != null) {
                    Set<Node> oldNodes = nodeList.replace(newNodes);
                    oldNodes.forEach(Processor.this::rememberNode);
                }
                if(tokenId < 0){
                    tokenId = -tokenId;
                }
            } finally {
                waiterInTokenPass = false;
            }
        }
    }

    private interface Event {
    }

    private static class TRInitiateEvent implements Event {
    }

    private static class TPReceivedEvent implements Event {
        @Getter
        private final Socket socket;

        public TPReceivedEvent(Socket socket) {
            this.socket = socket;
        }
    }
}
