package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

class MessageService<D extends Data<D>> {
    static final byte PROTOCOL_VERSION = 1;
    private final static Logger log = LoggerFactory.getLogger(MessageService.class);
    private final Context<D> context;
    private final DatagramSocket udpSendSocket;

    public MessageService(Context<D> context) throws SocketException {
        this.context = context;
        udpSendSocket = new DatagramSocket();
    }

    public void handleTPMessage(byte[] data, TPHandler handler) throws IOException, ParseException {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        MessageType type = readType(ois);
        switch (type) {
            case TP1:
                handler.handleTP1(readInt(ois), readInt(ois));
                break;
            case TP2:
                handler.handleTP2();
                break;
            case TP3:
                handler.handleTP3();
                break;
            case TP4:
                handler.handleTP4(parseNodeList(ois));
                break;
            case TP5:
                handler.handleTP5(readInt(ois), ois);
                break;
        }
    }

    private int readInt(ObjectInputStream ois) throws ParseException {
        try {
            return ois.readInt();
        } catch (IOException e) {
            throw new ParseException(e);
        }
    }

    private List<Node> parseNodeList(ObjectInputStream ois) throws ParseException {
        try {
            int size = ois.readInt();
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < size; ++i) {
                nodes.add(parseNode(ois));
            }
            return nodes;
        } catch (IOException e) {
            throw new ParseException(e);
        }
    }

    private Node parseNode(ObjectInputStream ois) throws IOException {
        byte meta = ois.readByte();
        byte[] ipAddress;
        if ((meta & 1) == 0) {
            ipAddress = new byte[4];
        } else {
            ipAddress = new byte[16];
        }
        ois.readFully(ipAddress);
        InetAddress address = InetAddress.getByAddress(ipAddress);
        return new Node(address, ois.readUnsignedShort());
    }

    public void handleTRMessage(DatagramPacket packet, TRHandler trHandler) throws ParseException, IOException {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()));
        MessageType type = readType(ois);
        switch (type) {
            case TR1:
                trHandler.handleTR1(packet.getAddress(), readInt(ois), readInt(ois));
                break;
            case TR2:
                trHandler.handleTR2(packet.getAddress(), readInt(ois));
                break;
        }
    }

    private void writeTypeProtocolByte(MessageType type, DataOutputStream dos) throws IOException {
        dos.writeByte(PROTOCOL_VERSION | (type.getCode() << 4));
    }

    public void sendTR1Message(int tokenId) throws IOException {
        ByteArrayOutputStream bas = new ByteArrayOutputStream(5);
        DataOutputStream dos = new DataOutputStream(bas);
        writeTypeProtocolByte(MessageType.TR1, dos);
        dos.writeInt(tokenId);
        dos.writeShort(context.getTcpPort());
        sendUDPMessage(null, bas.toByteArray());
    }

    public void sendTR2Message(InetAddress destination, int tokenId) throws IOException {
        ByteArrayOutputStream bas = new ByteArrayOutputStream(5);
        DataOutputStream dos = new DataOutputStream(bas);
        writeTypeProtocolByte(MessageType.TR2, dos);
        dos.writeInt(tokenId);
        sendUDPMessage(destination, bas.toByteArray());
    }

    private void sendUDPMessage(InetAddress destination, byte[] bytes) throws IOException {
        if (destination == null) {
            for (InterfaceAddress address : context.getSettings().getNetworkInterface().getInterfaceAddresses()) {
                if (address.getBroadcast() != null) {
                    udpSendSocket.send(createUdpDatagram(address.getBroadcast(), bytes));
                }
            }
        } else {
            udpSendSocket.send(createUdpDatagram(destination, bytes));
        }
    }

    private DatagramPacket createUdpDatagram(InetAddress address, byte[] bytes) {
        return new DatagramPacket(bytes, 0, bytes.length, address, context.getSettings().getUdpPort());
    }

    private MessageType readType(ObjectInputStream ois) throws ParseException {
        try {
            int versionType = ois.readUnsignedByte();
            int version = versionType & 0xF;
            if (PROTOCOL_VERSION != version) {
                throw new ParseException("Version mismatch: " + version + " not supported (current version: " + MessageService.PROTOCOL_VERSION + ")");
            }
            int typeCode = versionType & 0xF0;
            MessageType type = MessageType.forCode(typeCode);
            if (type == null) {
                throw new ParseException("Unknown type code: " + typeCode);
            }
            log.debug("Handling {} message", type);
            return type;
        } catch (IOException e) {
            throw new ParseException(e);
        }
    }

}
