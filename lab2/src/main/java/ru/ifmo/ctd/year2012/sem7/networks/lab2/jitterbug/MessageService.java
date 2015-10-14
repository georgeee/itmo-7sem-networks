package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

class MessageService<D extends Data<D>> {
    static final byte PROTOCOL_VERSION = 1;
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private final Context<D> context;
    private final DatagramSocket udpSendSocket;

    public MessageService(Context<D> context) throws SocketException {
        this.context = context;
        udpSendSocket = new DatagramSocket();
    }

    public void handleTPMessage(ObjectInputStream ois, TPHandler handler) throws IOException, ParseException {
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
                handler.handleTP5(ois);
                break;
        }
    }

    private int readUnsignedShort(ObjectInputStream ois) throws ParseException {
        try {
            return ois.readUnsignedShort();
        } catch (IOException e) {
            throw new ParseException(e);
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
                trHandler.handleTR1(packet.getAddress(), readInt(ois), readUnsignedShort(ois));
                break;
            case TR2:
                trHandler.handleTR2(packet.getAddress(), readInt(ois));
                break;
        }
    }

    private byte getTypeProtocolByte(MessageType type) throws IOException {
        return (byte) (PROTOCOL_VERSION | (type.getCode() << 4));
    }

    public void sendTR1Message(int tokenId) throws IOException {
        ByteArrayOutputStream bas = new ByteArrayOutputStream(7);
        DataOutputStream dos = new DataOutputStream(bas);
        dos.write(getTypeProtocolByte(MessageType.TR1));
        dos.writeInt(tokenId);
        dos.writeShort(context.getTcpPort());
        dos.flush();
        sendUDPMessage(null, bas.toByteArray());
    }

    public void sendTR2Message(InetAddress destination, int tokenId) throws IOException {
        ByteArrayOutputStream bas = new ByteArrayOutputStream(5);
        DataOutputStream dos = new DataOutputStream(bas);
        dos.write(getTypeProtocolByte(MessageType.TR2));
        dos.writeInt(tokenId);
        dos.flush();
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


    public void sendTP1Message(ObjectOutputStream oos, int tokenId, int nodeListHash) throws IOException {
        oos.write(getTypeProtocolByte(MessageType.TP1));
        oos.writeInt(tokenId);
        oos.writeInt(nodeListHash);
        oos.flush();
    }

    public void sendTP2Message(ObjectOutputStream oos) throws IOException {
        oos.write(getTypeProtocolByte(MessageType.TP2));
        oos.flush();
    }

    public void sendTP3Message(ObjectOutputStream oos) throws IOException {
        oos.write(getTypeProtocolByte(MessageType.TP3));
        oos.flush();
    }

    public void sendTP4Message(ObjectOutputStream oos, int nodeListSize, byte[] nodeList) throws IOException {
        oos.write(getTypeProtocolByte(MessageType.TP4));
        oos.writeInt(nodeListSize);
        oos.write(nodeList);
        oos.flush();
    }

    public void sendTP5MessageHeader(ObjectOutputStream oos) throws IOException {
        oos.write(getTypeProtocolByte(MessageType.TP5));
    }
}
