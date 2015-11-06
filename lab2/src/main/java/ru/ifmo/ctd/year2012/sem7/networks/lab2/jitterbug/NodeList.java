package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;

class NodeList implements Iterable<Node> {
    private static final int BASE = 577;
    private final List<Node> nodeList;
    private Map<Integer, Integer> nodeMap;
    private int hash;
    private final ByteArrayOutputStream baos;

    public int size() {
        return nodeList.size();
    }

    public Node get(int index) {
        return nodeList.get(index);
    }

    public NodeList() {
        nodeList = new ArrayList<>();
        nodeMap = new HashMap<>();
        baos = new ByteArrayOutputStream();
    }

    public boolean add(Node node) {
        if (nodeMap.containsKey(node.getHostId())) {
            return false;
        }
        InetAddress address = node.getAddress();
        byte[] addressBytes = address.getAddress();
        ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.putInt((addressBytes.length == 16 ? -1 : 1) * node.getHostId());
        buffer.put(addressBytes);
        buffer.putShort((short) node.getPort());
        hash = updateHash(hash, buffer.array(), buffer.arrayOffset(), buffer.position() - buffer.arrayOffset());
        baos.write(buffer.array(), buffer.arrayOffset(), buffer.position() - buffer.arrayOffset());
        nodeList.add(node);
        nodeMap.put(node.getHostId(), nodeList.size() - 1);
        return true;
    }

    public Integer getByHostId(int hostId) {
        return nodeMap.get(hostId);
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
        nodeMap.clear();
    }

    public Set<Node> replace(List<Node> newNodes) {
        Set<Node> oldNodes = new HashSet<>(nodeList);
        oldNodes.removeAll(newNodes);
        clear();
        newNodes.stream().forEach(this::add);
        return oldNodes;
    }

    @Override
    public String toString() {
        return "NodeList{" +
                nodeList +
                '}';
    }
}
