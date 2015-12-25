package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.ifmo.ctd.year2012.sem7.networks.lab3.transmission.Sender;

import javax.annotation.PostConstruct;
import javax.sound.sampled.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class AudioRecorder extends Thread {
    private static final Logger log = LoggerFactory.getLogger(AudioRecorder.class);
    private static final int CHUNK_SIZE = 10240;
    private OutputStream outputStream;
    private TargetDataLine microphone;

    @Getter
    private volatile boolean running;

    private final Object monitor = new Object();

    public void setRunning(boolean running) {
        this.running = running;
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    @Autowired
    private Sender sender;

    @Autowired
    private Settings settings;

    @PostConstruct
    public void init() {
        outputStream = sender.getOutputStream();
        try {
            AudioFormat format = settings.getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
        } catch (LineUnavailableException e) {
            log.error("Unable to find microphone", e);
            throw new IllegalStateException("Unable to find microphone", e);
        }
    }

    public void run() {
        byte[] data = new byte[CHUNK_SIZE];
        try {
            while (true) {
                if(running) {
                    microphone.start();
                    while (running) {
                        if (Thread.interrupted()) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        int numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
                        outputStream.write(data, 0, numBytesRead);
                        outputStream.flush();
                        log.debug("[Recorder] read {} from microphone", numBytesRead);
                    }
                    microphone.stop();
                }
                while (!running) {
                    synchronized (monitor) {
                        try {
                            monitor.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("[Recorder] error while transmitting data", e);
        }
    }

}
