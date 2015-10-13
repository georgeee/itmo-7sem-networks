package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.net.InetAddress;
import java.net.Socket;

interface State<D extends Data<D>> {
    D getData();

    int getTokenId();

    void rememberNode(InetAddress address, int tcpPort);

    void reportTR2(InetAddress senderAddress, int tokenId);

    void handleSocketConnection(Socket socket);
}
