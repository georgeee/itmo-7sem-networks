package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;

class NodeList implements Iterable<Node> {
    private static final int BASE = 577;
    private final List<Node> nodeList;
    private int hash;
    private final ByteArrayOutputStream baos;

    public int size() {
        return nodeList.size();
    }

    public boolean isEmpty() {
        return nodeList.isEmpty();
    }

    public Node get(int index) {
        return nodeList.get(index);
    }

    public NodeList() {
        nodeList = new ArrayList<>();
        baos = new ByteArrayOutputStream();
    }

    public void add(Node node) {
        InetAddress address = node.getAddress();
        byte[] addressBytes = address.getAddress();
        byte meta = 0;
        if (addressBytes.length == 16) {
            meta |= 1;
        }
        ByteBuffer buffer = ByteBuffer.allocate(addressBytes.length + 3);
        buffer.put(meta);
        buffer.put(addressBytes);
        buffer.putShort((short) node.getPort());
        hash = updateHash(hash, buffer.array(), buffer.arrayOffset(), addressBytes.length + 3);
        baos.write(buffer.array(), buffer.arrayOffset(), addressBytes.length + 3);
        nodeList.add(node);
    }

    @Override
    public Iterator<Node> iterator() {
        return nodeList.iterator();
    }

    private static int updateHash(int hash, byte[] bytes, int offset, int len) {
        for (int i = offset; i < offset + len; ++i) {
            hash = hash * BASE + bytes[i];
        }
        return hash;
    }


    public byte[] getBytes() {
        return baos.toByteArray();
    }

    public int getHash() {
        return hash;
    }

    private void clear() {
        hash = 0;
        baos.reset();
        nodeList.clear();
    }

    public Set<Node> replace(List<Node> newNodes) {
        Set<Node> oldNodes = new HashSet<>(nodeList);
        oldNodes.removeAll(newNodes);
        clear();
        newNodes.stream().forEach(this::add);
        return oldNodes;
    }

}
