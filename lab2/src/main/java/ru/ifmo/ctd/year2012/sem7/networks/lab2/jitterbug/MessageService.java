package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class MessageService<D extends Data<D>> {
    static final byte PROTOCOL_VERSION = 1;
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private final Context<D> context;
    private final DatagramSocket udpSendSocket;
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    public MessageService(Context<D> context) throws SocketException {
        this.context = context;
        udpSendSocket = new DatagramSocket();
    }

    public void handleTPMessage(DataInputStream dis, TPHandler handler) throws IOException, ParseException {
        MessageType type = readType(dis);
        switch (type) {
            case TP1: {
                int tokenId, nodeListHash;
                try {
                    tokenId = dis.readInt();
                    nodeListHash = dis.readInt();
                } catch (IOException e) {
                    throw new ParseException(e);
                }
                log.info("Received TP1 with tokenId={} nodeListHash={}", tokenId, nodeListHash);
                handler.handleTP1(tokenId, nodeListHash);
            }
            break;
            case TP2:
                log.info("Received TP2");
                handler.handleTP2();
                break;
            case TP3:
                log.info("Received TP3");
                handler.handleTP3();
                break;
            case TP4:
                log.info("Received TP4");
                handler.handleTP4(parseNodeList(dis));
                break;
            case TP5:
                log.info("Received TP5");
                handler.handleTP5(dis);
                break;
        }
    }

    private List<Node> parseNodeList(DataInputStream dis) throws ParseException {
        try {
            int size = dis.readInt();
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < size; ++i) {
                nodes.add(parseNode(dis));
            }
            return nodes;
        } catch (IOException e) {
            throw new ParseException(e);
        }
    }

    private Node parseNode(DataInputStream dis) throws IOException {
        int hostId = dis.readInt();
        boolean isIPv6 = false;
        if(hostId < 0){
            hostId = -hostId;
            isIPv6 = true;
        }
        byte[] ipAddress;
        if (isIPv6) {
            ipAddress = new byte[16];
        } else {
            ipAddress = new byte[4];
        }
        dis.readFully(ipAddress);
        InetAddress address = InetAddress.getByAddress(ipAddress);
        return new Node(hostId, address, dis.readUnsignedShort());
    }

    public void handleTRMessage(DatagramPacket packet, TRHandler trHandler) throws ParseException, IOException {
        ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        MessageType type = readType(buffer);
        switch (type) {
            case TR1: {
                int tokenId, tcpPort, hostId;
                try {
                    tokenId = buffer.getInt();
                    hostId = buffer.getInt();
                    tcpPort = Short.toUnsignedInt(buffer.getShort());
                } catch (BufferUnderflowException e) {
                    throw new ParseException(e);
                }
                log.debug("Received TR1 from {} with tokenId={} tcpPort={}", packet.getAddress(), tokenId, tcpPort);
                trHandler.handleTR1(packet.getAddress(), tokenId, hostId, tcpPort);
            }
            break;
            case TR2: {
                int tokenId;
                try {
                    tokenId = buffer.getInt();
                } catch (BufferUnderflowException e) {
                    throw new ParseException(e);
                }
                log.debug("Received TR2 from {} with tokenId={}", packet.getAddress(), tokenId);
                trHandler.handleTR2(packet.getAddress(), tokenId);
            }
            break;
        }
    }

    private byte getTypeProtocolByte(MessageType type) throws IOException {
        byte version = (byte) (PROTOCOL_VERSION | (type.getCode() << 4));
        return version;
    }

    public void sendTR1MessageRepeatedly(int tokenId) {
        sendTR1Message(tokenId, context.getSettings().getTr1Repeat());
    }

    private void sendTR1Message(int tokenId, int repeatsRemained) {
        log.debug("Sending TR1 message tokenId={} repeatsRemained={}", tokenId, repeatsRemained);
        int delay = context.getSettings().getTr1Delay();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(32);
            buffer.put(getTypeProtocolByte(MessageType.TR1));
            buffer.putInt(tokenId);
            buffer.putInt(context.getHostId());
            buffer.putShort((short) context.getSelfTcpPort());
            sendUDPMessage(null, buffer);
        } catch (IOException e) {
            log.warn("Error sending TR1 message: tokenId={} repeatsRemained={}", tokenId, repeatsRemained);
        }
        if (repeatsRemained > 0) {
            scheduledExecutor.schedule(() -> sendTR1Message(tokenId, repeatsRemained - 1), delay, TimeUnit.MILLISECONDS);
        }
    }

    public void sendTR2Message(InetAddress destination, int tokenId) throws IOException {
        log.debug("Sending TR2 message tokenId={} dest={}", tokenId, destination);
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put(getTypeProtocolByte(MessageType.TR2));
        buffer.putInt(tokenId);
        sendUDPMessage(destination, buffer);
    }

    private void sendUDPMessage(InetAddress destination, ByteBuffer buffer) throws IOException {
        sendUDPMessage(destination, buffer.array(), buffer.arrayOffset(), buffer.position());
    }

    private void sendUDPMessage(InetAddress destination, byte[] bytes, int offset, int length) throws IOException {
        if (destination == null) {
            for (InterfaceAddress address : context.getSettings().getNetworkInterface().getInterfaceAddresses()) {
                if (address.getBroadcast() != null) {
                    udpSendSocket.send(createUdpDatagram(address.getBroadcast(), bytes, offset, length));
                }
            }
        } else {
            udpSendSocket.send(createUdpDatagram(destination, bytes, offset, length));
        }
    }

    private DatagramPacket createUdpDatagram(InetAddress address, byte[] bytes, int offset, int length) {
        return new DatagramPacket(bytes, offset, length, address, context.getSettings().getUdpPort());
    }

    private MessageType readType(DataInputStream dis) throws ParseException {
        try {
            return readType(dis.readByte());
        } catch (IOException e) {
            throw new ParseException(e);
        }
    }

    private MessageType readType(ByteBuffer buffer) throws ParseException {
        try {
            return readType(buffer.get());
        } catch (BufferUnderflowException e) {
            throw new ParseException(e);
        }
    }

    private MessageType readType(byte versionType_) throws ParseException {
        int versionType = Byte.toUnsignedInt(versionType_);
        int version = versionType & 0xF;
        if (PROTOCOL_VERSION != version) {
            throw new ParseException("Version mismatch: " + version + " not supported (current version: " + MessageService.PROTOCOL_VERSION + ")");
        }
        int typeCode = (versionType & 0xF0) >> 4;
        MessageType type = MessageType.forCode(typeCode);
        if (type == null) {
            throw new ParseException("Unknown type code: " + typeCode);
        }
        log.debug("Handling {} message", type);
        return type;
    }


    public void sendTP1Message(DataOutputStream dos, int tokenId, int nodeListHash) throws IOException {
        log.debug("Sending TP1 message tokenId={} nodeListHash={}", tokenId, nodeListHash);
        dos.write(getTypeProtocolByte(MessageType.TP1));
        dos.writeInt(tokenId);
        dos.writeInt(nodeListHash);
        dos.flush();
    }

    public void sendTP2Message(DataOutputStream dos) throws IOException {
        log.debug("Sending TP2");
        dos.write(getTypeProtocolByte(MessageType.TP2));
        dos.flush();
    }

    public void sendTP3Message(DataOutputStream dos) throws IOException {
        log.debug("Sending TP3");
        dos.write(getTypeProtocolByte(MessageType.TP3));
        dos.flush();
    }

    public void sendTP4Message(DataOutputStream dos, int nodeListSize, byte[] nodeList) throws IOException {
        log.debug("Sending TP4");
        dos.write(getTypeProtocolByte(MessageType.TP4));
        dos.writeInt(nodeListSize);
        dos.write(nodeList);
        dos.flush();
    }

    public void sendTP5Message(DataOutputStream dos, D data) throws IOException {
        log.debug("Sending TP5");
        dos.write(getTypeProtocolByte(MessageType.TP5));
        ObjectOutputStream oos = new ObjectOutputStream(dos);
        oos.writeObject(data);
        oos.flush();
        dos.flush();
    }
}
