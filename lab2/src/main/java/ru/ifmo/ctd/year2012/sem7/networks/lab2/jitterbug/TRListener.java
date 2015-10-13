package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

class TRListener<D extends Data<D>> extends Thread implements TRHandler {
    private final static Logger log = LoggerFactory.getLogger(TRListener.class);

    private final Context<D> context;
    private final DatagramSocket socket;

    public TRListener(Context<D> context, DatagramSocket socket) {
        this.context = context;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }

                byte[] buf = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buf, 4096);
                socket.receive(packet);
                log.debug("Received udp packet from {}", packet.getAddress());
                context.getExecutor().submit(() -> {
                    try {
                        context.getMessageService().handleTRMessage(packet, this);
                    } catch (ParseException e) {
                        log.warn("Error trying to parse message");
                        log.debug("Error trying to parse message {}", packet.getData());
                    } catch (IOException e) {
                        log.info("IO error occurred", e);
                    }
                });
            }
        } catch (IOException e) {
            log.info("Caught exception", e);
        }
    }

    @Override
    public void handleTR1(InetAddress senderAddress, int tokenId, int senderTcpPort) throws IOException {
        log.debug("Received TR1 from {} with tokenId={} tcpPort={}", senderAddress, tokenId, senderTcpPort);
        int selfTokenId = context.getState().getTokenId();
        if (selfTokenId > tokenId) {
            context.getMessageService().sendTR2Message(senderAddress, selfTokenId);
        }
        context.getState().rememberNode(senderAddress, senderTcpPort);
    }

    @Override
    public void handleTR2(InetAddress senderAddress, int tokenId) {
        log.debug("Received TR2 from {} with tokenId={}", senderAddress, tokenId);
        context.getState().reportTR2(senderAddress, tokenId);
    }


}

