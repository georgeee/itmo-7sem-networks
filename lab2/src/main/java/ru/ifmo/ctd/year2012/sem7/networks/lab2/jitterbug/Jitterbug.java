package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.IOException;

public class Jitterbug<D extends Data<D>> {

    private final Settings<D> settings;
    private Context<D> context;
    private final Object monitor = new Object();

    public Jitterbug(Settings<D> settings) {
        this.settings = settings;
    }

    public boolean start() throws IOException {
        synchronized (monitor) {
            if (context == null) {
                context = new Context<>(settings);
                context.start();
                return true;
            }
        }
        return false;
    }

    public boolean stop() {
        synchronized (monitor) {
            if (context != null) {
                context.stop();
                monitor.notifyAll();
                return true;
            }
        }
        return false;
    }

    public void awaitTermination() throws InterruptedException {
        synchronized (monitor) {
            if (context != null) {
                monitor.wait();
            }
        }
    }
}
