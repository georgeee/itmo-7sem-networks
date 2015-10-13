package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.IOException;

public class Jitterbug<D extends Data<D>> {

    private final Settings<D> settings;
    private volatile Context<D> context;

    public Jitterbug(Settings<D> settings) {
        this.settings = settings;
    }

    public synchronized boolean start() throws IOException {
        if (context == null) {
            context = new Context<>(settings);
            context.start();
            return true;
        }
        return false;
    }

    public synchronized boolean stop() {
        if (context != null) {
            context.stop();
            context.notifyAll();
            return true;
        }
        return false;
    }

    public synchronized void awaitTermination() throws InterruptedException {
        if (context != null) {
            context.wait();
        }
    }
}
