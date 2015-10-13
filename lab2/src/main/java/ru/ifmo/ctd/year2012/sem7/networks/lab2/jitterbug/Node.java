package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.ToString;

import java.net.InetAddress;

@ToString
class Node {
    private final InetAddress address;
    private final int port;

    public Node(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }
}
