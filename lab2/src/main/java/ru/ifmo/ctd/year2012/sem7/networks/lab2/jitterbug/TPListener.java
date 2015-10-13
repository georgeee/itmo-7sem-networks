package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class TPListener<D extends Data<D>> extends Thread {
    private static final Logger log = LoggerFactory.getLogger(TPListener.class);
    private final Context<D> context;
    private final ServerSocket serverSocket;

    TPListener(Context<D> context, ServerSocket serverSocket) {
        this.context = context;
        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.interrupted();
                break;
            }
            try {
                Socket socket = serverSocket.accept();
                context.getState().handleSocketConnection(socket);
            } catch (IOException e) {
                log.warn("Exception caught while listening to socket", e);
            }
        }
    }
}
