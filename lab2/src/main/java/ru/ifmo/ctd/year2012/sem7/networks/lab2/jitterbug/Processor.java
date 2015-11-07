package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    private static final Logger log = LoggerFactory.getLogger(Processor.class);
    private final Random random;
    private final Context<D> context;
    private final BlockingQueue<Event> eventQueue;

    private final Set<Node> addQueue = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final NodeList nodeList = new NodeList();
    private final MessageService<D> messageService;

    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    private volatile long lastLivenessEventTime;
    private volatile boolean trInProgress;
    private volatile boolean tr2ReceivedGreater;

    private Map<Integer, Penalty> penalties = new HashMap<>();

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
        messageService = context.getMessageService();
        tokenId = generateTokenId();
        random = new Random(System.currentTimeMillis() ^ context.getHostId());
        rememberNode(context.getHostId(), context.getSettings().getSelfAddress(), context.getSelfTcpPort());
    }

    @Override
    public void rememberNode(int hostId, InetAddress address, int tcpPort) {
        Node node = new Node(hostId, address, tcpPort);
        if (addQueue.add(node)) {
            log.info("Added node {} to add queue", node);
        }
    }

    @Override
    public void reportTR2(InetAddress senderAddress, int tokenId) {
        tr2ReceivedGreater |= tokenId > this.tokenId;
    }

    @Override
    public void handleSocketConnection(Socket socket) {
        try {
            eventQueue.put(new TPReceivedEvent(socket));
        } catch (InterruptedException e) {
            log.debug("Ignoring tcp connection: interrupted");
            Thread.currentThread().interrupt();
        }
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
        initTimeouts();
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
            if (event instanceof TPReceivedEvent) {
                lastLivenessEventTime = System.currentTimeMillis();
                log.debug("Socket connection received");
                trInProgress = false;
                InetAddress remoteAddress = null;
                boolean processedTokenPass = false;
                try (Socket socket = ((TPReceivedEvent) event).getSocket()) {
                    socket.setSoTimeout(context.getSettings().getTpTimeout());
                    try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                         DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    ) {
                        remoteAddress = socket.getInetAddress();
                        new TokenReceive(dis, dos).process();
                        processedTokenPass = true;
                    }
                } catch (IOException | ParseException e) {
                    log.info("Exception caught while communicating through socket: address={}", remoteAddress, e);
                }
                if (processedTokenPass) {
                    if (!actAsLeader()) {
                        return;
                    }
                }
            } else if (event instanceof TRInitiateEvent) {
                if (!trInProgress && needTokenRestore()) {
                    trInProgress = true;
                    tr2ReceivedGreater = false;
                    log.info("[TR init] Initiated token restore, tokenId={}", tokenId);
                    messageService.sendTR1MessageRepeatedly(tokenId);
                    scheduledExecutor.schedule(() -> eventQueue.offer(new TRPhase1Event()), context.getSettings().getTrPhaseTimeout(), TimeUnit.MILLISECONDS);
                }
            } else if (event instanceof TRPhase1Event) {
                if (trInProgress) {
                    if (!tr2ReceivedGreater) {
                        int oldTokenId = tokenId;
                        tokenId = generateTokenId();
                        log.info("[TR phase1] Generated new token id: oldTokenId={} newTokenId={}", oldTokenId, tokenId);
                        tr2ReceivedGreater = false;
                        messageService.sendTR1MessageRepeatedly(tokenId);
                        scheduledExecutor.schedule(() -> eventQueue.offer(new TRPhase2Event()), context.getSettings().getTrPhaseTimeout(), TimeUnit.MILLISECONDS);
                    } else {
                        trInProgress = false;
                        log.info("[TR phase1] Received greater tokenId, aborting TR procedure");
                    }
                }
            } else if (event instanceof TRPhase2Event) {
                if (trInProgress) {
                    if (!tr2ReceivedGreater) {
                        tokenId = -tokenId;
                        log.info("[TR phase2] Became a leader, tokenId={}", tokenId);
                        if (!actAsLeader()) {
                            return;
                        }
                    } else {
                        log.info("[TR phase2] Received greater tokenId, aborting TR procedure");
                    }
                    trInProgress = false;
                }
            }
        }
    }

    private boolean actAsLeader() {
        boolean success = false;
        while (!success) {
            try {
                success = actAsLeaderPhase();
                if (!success) {
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        log.info("Leader interrupted, exiting");
                        return false;
                    }
                    lastLivenessEventTime = System.currentTimeMillis();
                    log.info("Token not passed, repeating as leader...");
                }
            } catch (Exception e) {
                log.error("Received error in leader phase", e);
            }
        }
        log.info("Token passed, switching to waiter state");
        lastLivenessEventTime = System.currentTimeMillis();
        tokenId = -tokenId;
        return true;
    }

    private boolean actAsLeaderPhase() {
        int coinThreshold = (int) (context.getSettings().getTokenLooseProbBase() * addQueue.size());
        log.info("Throwing coin (whether to loose token) with probability {}", coinThreshold);
        int coinResult = random.nextInt(coinThreshold);
        if (coinResult == 0) {
            addTRInitEvent();
            log.info("Token was intentionally lost: switching to orphan state");
            return true;
        } else {
            log.info("Processing as leader: tokenId = {}", tokenId);
            long dataComputationDelay = context.getSettings().getDataComputationDelay();
            if (dataComputationDelay > 0) {
                try {
                    Thread.sleep(dataComputationDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            data = data.next();
            log.info("Computed next data: {}", data);
            addQueue.stream().forEach(nodeList::add);
            int selfIndex = nodeList.getByHostId(context.getHostId());
            log.info("Trying to pass token, selfIndex={}, nodeList={}", selfIndex, nodeList);
            boolean tokenPassed = false;
            for (int i = selfIndex + 1; i < nodeList.size() && !tokenPassed; ++i) {
                tokenPassed = tokenPassForCandidate(i);
            }
            for (int i = 0; i < selfIndex && !tokenPassed; ++i) {
                tokenPassed = tokenPassForCandidate(i);
            }
            return tokenPassed;
        }
    }

    private boolean tokenPassForCandidate(int i) {
        Node candidate = nodeList.get(i);
        log.info("Trying candidate #{}, {}", i, candidate);
        if (candidate.getHostId() == context.getHostId()) {
            return false;
        }
        Penalty penalty = penalties.computeIfAbsent(candidate.getHostId(), n -> new Penalty());
        if (penalty.count < (1 << penalty.threshold) - 1) {
            log.info("Candidate omitted due to penalties: {}/{} passed", penalty.count, (1 << penalty.threshold) - 1);
            penalty.count++;
        } else {
            //Allowed for round
            penalty.count = 0;
            if (tokenPassForCandidateImpl(candidate)) {
                penalty.decThreshold();
                log.info("Token successfully passed");
                return true;
            } else {
                penalty.incThreshold();
                log.info("Token not passed");
            }
        }
        return false;
    }

    private boolean tokenPassForCandidateImpl(Node candidate) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(context.getSettings().getTpTimeout());
            socket.connect(new InetSocketAddress(candidate.getAddress(), candidate.getPort()), context.getSettings().getTpTimeout());
            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            ) {
                messageService.sendTP1Message(dos, tokenId, nodeList.getHash());
                messageService.handleTPMessage(dis, new TPHandler() {
                    @Override
                    public void handleTP2() throws IOException, ParseException {
                        messageService.sendTP4Message(dos, nodeList.size(), nodeList.getBytes());
                    }

                    @Override
                    public void handleTP3() throws IOException, ParseException {
                        //Do nothing
                    }
                });
                messageService.sendTP5Message(dos, data);
                return true;
            }
        } catch (IOException | ParseException e) {
            log.debug("Caught exception trying to pass token to candidate {}", candidate, e);
        }
        return false;
    }

    boolean needTokenRestore() {
        int trInitDelay = context.getSettings().getTrInitTimeout();
        return lastLivenessEventTime + trInitDelay < System.currentTimeMillis();
    }

    private void initTimeouts() {
        int trInitDelay = context.getSettings().getTrInitTimeout();
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            if (needTokenRestore()) addTRInitEvent();
        }, 0, trInitDelay, TimeUnit.MILLISECONDS);
    }

    private void addTRInitEvent() {
        eventQueue.offer(new TRInitiateEvent());
    }

    private class TokenReceive {
        int newTokenId;
        List<Node> newNodes;
        D newData;
        final DataInputStream dis;
        final DataOutputStream dos;

        private TokenReceive(DataInputStream dis, DataOutputStream dos) {
            this.dis = dis;
            this.dos = dos;
        }


        void process() throws IOException, ParseException {
            messageService.handleTPMessage(dis, new TPHandler() {
                @Override
                public void handleTP1(int newTokenId1, int nodeListHash) throws IOException, ParseException {
                    newTokenId = newTokenId1;
                    log.info("[TokenPass] Procedure started: newTokenId={} oldTokenId={}", newTokenId, tokenId);
                    if (nodeListHash == nodeList.getHash()) {
                        messageService.sendTP3Message(dos);
                    } else {
                        messageService.sendTP2Message(dos);
                        messageService.handleTPMessage(dis, new TPHandler() {
                            @Override
                            public void handleTP4(List<Node> nodes) throws IOException, ParseException {
                                newNodes = nodes;
                            }
                        });
                    }
                }
            });
            messageService.handleTPMessage(dis, new TPHandler() {
                @Override
                @SuppressWarnings("unchecked")
                public void handleTP5(DataInputStream dis) throws IOException, ParseException {
                    ObjectInputStream ois = new ObjectInputStream(dis);
                    try {
                        newData = (D) ois.readObject();
                    } catch (ClassNotFoundException e) {
                        throw new IOException(e);
                    }
                }
            });
            log.info("[TokenPass] Procedure started: newTokenId={} oldTokenId={}", newTokenId, tokenId);
            if (newTokenId == tokenId) {
                data = newData;
            } else {
                tokenId = newTokenId;
                data = newData.mergeWith(data);
            }
            if (newNodes != null) {
                Set<Node> oldNodes = nodeList.replace(newNodes);
                oldNodes.forEach(addQueue::add);
            }
            if (tokenId < 0) {
                tokenId = -tokenId;
            }
            log.info("[TokenPass] Procedure performed, switched to leader state: tokenId={}", tokenId);
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
