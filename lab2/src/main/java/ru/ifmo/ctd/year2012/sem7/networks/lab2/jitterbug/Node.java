package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;
import lombok.ToString;

import java.net.InetAddress;

@ToString
class Node {
    @Getter
    private final int hostId;
    @Getter
    private final InetAddress address;
    @Getter
    private final int port;

    public Node(int hostId, InetAddress address, int port) {
        this.hostId = hostId;
        this.address = address;
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return hostId == node.hostId;
    }

    @Override
    public int hashCode() {
        return hostId;
    }
}
