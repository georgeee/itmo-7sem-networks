package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.IOException;
import java.net.InetAddress;

interface TRHandler {
    void handleTR1(InetAddress address, int tokenId, int tcpPort) throws IOException, ParseException;

    void handleTR2(InetAddress address, int tokenId) throws IOException, ParseException;
}
