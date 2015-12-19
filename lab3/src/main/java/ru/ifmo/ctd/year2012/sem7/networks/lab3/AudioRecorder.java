package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.ifmo.ctd.year2012.sem7.networks.lab3.transmission.Sender;

import javax.annotation.PostConstruct;
import javax.sound.sampled.*;
import java.io.IOException;
import java.io.OutputStream;

@Component
public class AudioRecorder implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(AudioRecorder.class);
    private static final int CHUNK_SIZE = 10240;
    private OutputStream outputStream;
    private TargetDataLine microphone;

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
        microphone.start();
        byte[] data = new byte[CHUNK_SIZE];
        try {
            while (!Thread.interrupted()) {
                int numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
                outputStream.write(data, 0, numBytesRead);
                outputStream.flush();
                log.debug("[Recorder] read {} from microphone", numBytesRead);
            }
        } catch (IOException e) {
            log.error("[Recorder] error while transmitting data", e);
        }
        microphone.stop();
    }

}
