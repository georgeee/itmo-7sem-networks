package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

class TRListener<D extends Data<D>> extends Thread implements TRHandler {
    private static final Logger log = LoggerFactory.getLogger(TRListener.class);

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
                        log.warn("Error trying to parse message", e);
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
    public void handleTR1(InetAddress senderAddress, int tokenId, int hostId, int senderTcpPort) throws IOException {
        int selfTokenId = context.getState().getTokenId();
        if (selfTokenId > tokenId) {
            context.getMessageService().sendTR2Message(senderAddress, selfTokenId);
        }
        context.getState().rememberNode(hostId, senderAddress, senderTcpPort);
    }

    @Override
    public void handleTR2(InetAddress senderAddress, int tokenId) {
        context.getState().reportTR2(senderAddress, tokenId);
    }


}

