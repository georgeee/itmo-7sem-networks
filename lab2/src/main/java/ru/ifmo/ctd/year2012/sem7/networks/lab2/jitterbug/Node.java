package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;
import lombok.ToString;

import java.net.InetAddress;
import java.util.Objects;

@ToString
class Node {
    @Getter
    private final InetAddress address;
    @Getter
    private final int port;

    public Node(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return Objects.equals(port, node.port) &&
                Objects.equals(address, node.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, port);
    }
}
