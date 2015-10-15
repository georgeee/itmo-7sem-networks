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
 * * waiter: token_id < 0 && !waiterInTokenPass && (System.currentTimeMillis() <= {renew_timeout} + lastLivenessEventTime)
 * * orphan: token_id < 0 && !waiterInTokenPass && (System.currentTimeMillis() > {renew_timeout} + lastLivenessEventTime)
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

    private volatile long lastLivenessEventTime;
    private volatile boolean trInProgress;
    private volatile boolean tr2ReceivedGreater;

    private Map<Node, Penalty> penalties = new HashMap<>();

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
        tr2ReceivedGreater |= tokenId > this.tokenId;
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
                    lastLivenessEventTime = System.currentTimeMillis();
                    trInProgress = false;
                    InetAddress remoteAddress = null;
                    boolean processedTokenPass = false;
                    try (Socket socket = ((TPReceivedEvent) event).getSocket()) {
                        remoteAddress = socket.getInetAddress();
                        new TokenPassReceive(socket).process();
                        processedTokenPass = true;
                    } catch (IOException | ParseException e) {
                        log.info("Exception caught while communicating through socket: address={}", remoteAddress);
                    }
                    if (processedTokenPass) {
                        boolean leaderShipPassed = false;
                        while (!leaderShipPassed) {
                            leaderShipPassed = actAsLeader();
                            lastLivenessEventTime = System.currentTimeMillis();
                        }
                        tokenId = -tokenId;
                    }
                } else if (event instanceof TRInitiateEvent) {
                    if (!trInProgress && needTokenRestore()) {
                        trInProgress = true;
                        tr2ReceivedGreater = false;
                        log.info("[TR init] Initiated token restore, tokenId={}", tokenId);
                        messageService.sendTR1MessageRepeatedly(tokenId);
                        scheduledExecutor.schedule(() -> eventQueue.add(new TRPhase1Event()), context.getSettings().getTrPhaseTimeout(), TimeUnit.MILLISECONDS);
                    }
                } else if (event instanceof TRPhase1Event) {
                    if (trInProgress && !tr2ReceivedGreater) {
                        int oldTokenId = tokenId;
                        tokenId = generateTokenId();
                        log.info("[TR phase1] Generated new token id: oldTokenId={} newTokenId={}", oldTokenId, tokenId);
                        tr2ReceivedGreater = false;
                        messageService.sendTR1MessageRepeatedly(tokenId);
                        scheduledExecutor.schedule(() -> eventQueue.add(new TRPhase2Event()), context.getSettings().getTrPhaseTimeout(), TimeUnit.MILLISECONDS);
                    }
                } else if (event instanceof TRPhase2Event) {
                    if (trInProgress && !tr2ReceivedGreater) {
                        tokenId = -tokenId;
                        log.info("[TR phase2] Became a leader, tokenId={}", tokenId);
                        actAsLeader();
                    }
                }
            }
        } catch (InterruptedException e) {
            log.debug("Processor was interrupted");
        }
    }

    private boolean actAsLeader() {
        log.info("Processing as leader");
        data = data.next();
        addQueue.stream().forEach(nodeList::add);
        int selfIndex = locateSelf();
        if (selfIndex < 0) {
            Node selfNode = context.getSelfNode();
            allKnown.add(selfNode);
            addQueue.remove(selfNode);
            nodeList.add(selfNode);
            selfIndex = nodeList.size() - 1;
        }
        boolean tokenPassed = false;
        for (int i = selfIndex + 1; i < nodeList.size() && !tokenPassed; ++i) {
            tokenPassed = tokenPassForCandidate(i);
        }
        for (int i = 0; i < selfIndex && !tokenPassed; ++i) {
            tokenPassed = tokenPassForCandidate(i);
        }
        return tokenPassed;
    }

    private boolean tokenPassForCandidate(int i) {
        Node candidate = nodeList.get(i);
        Penalty penalty = penalties.computeIfAbsent(candidate, n -> new Penalty());
        if (penalty.count < (1 << penalty.threshold) - 1) {
            penalty.count++;
        } else {
            //Allowed for round
            penalty.count = 0;
            if (tokenPassForCandidateImpl(candidate)) {
                penalty.decThreshold();
                return true;
            } else {
                penalty.incThreshold();
            }
        }
        return false;
    }

    private boolean tokenPassForCandidateImpl(Node candidate) {
        try {
            try (Socket socket = new Socket(candidate.getAddress(), candidate.getPort())) {
                socket.setSoTimeout(context.getSettings().getTpTimeout());
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
                messageService.sendTP1Message(oos, tokenId, nodeList.getHash());
                messageService.handleTPMessage(ois, new TPHandler() {
                    @Override
                    public void handleTP2() throws IOException, ParseException {
                        messageService.sendTP4Message(oos, nodeList.size(), nodeList.getBytes());
                    }

                    @Override
                    public void handleTP3() throws IOException, ParseException {
                        //Do nothing
                    }
                });
                messageService.sendTP5MessageHeader(oos);
                data.writeToStream(oos);
                oos.flush();
                return true;
            }
        } catch (IOException | ParseException e) {
            log.info("Caught exception trying to pass token to candidate {}", e);
        }
        return false;
    }

    private int locateSelf() {
        Set<InetAddress> addresses = context.getSettings().getSelfAddresses();
        for (int i = 0; i < nodeList.size(); ++i) {
            Node node = nodeList.get(i);
            if (addresses.contains(node.getAddress()) && node.getPort() == context.getSelfTcpPort()) {
                return i;
            }
        }
        return -1;
    }

    boolean needTokenRestore() {
        int trInitDelay = context.getSettings().getTrInitTimeout();
        return lastLivenessEventTime + trInitDelay < System.currentTimeMillis();
    }

    private void initTimeouts() {
        int trInitDelay = context.getSettings().getTrInitTimeout();
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
            socket.setSoTimeout(context.getSettings().getTpTimeout());
            oos = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            ois = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
        }

        void process() throws IOException, ParseException {
            messageService.handleTPMessage(ois, new TPHandler() {
                @Override
                public void handleTP1(int tokenId, int nodeListHash) throws IOException, ParseException {
                    newTokenId = tokenId;
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
            if (tokenId < 0) {
                tokenId = -tokenId;
            }
        }
    }

    private static class Penalty {
        private static final int MAX_THRESHOLD = 10;
        int threshold, count;

        void decThreshold() {
            --threshold;
            if (threshold < 0) {
                threshold = 0;
            }
        }

        void incThreshold() {
            ++threshold;
            if (threshold > MAX_THRESHOLD) {
                threshold = MAX_THRESHOLD;
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

    private static class TRPhase2Event implements Event {
    }

    private static class TRPhase1Event implements Event {
    }
}
