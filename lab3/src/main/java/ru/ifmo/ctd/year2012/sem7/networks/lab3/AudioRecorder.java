package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by kerzok11 on 19.12.2015.
 */
public class AudioRecorder implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(AudioRecorder.class);
    private static final int CHUNK_SIZE = 10240;
    private final OutputStream outputStream;
    private final TargetDataLine microphone;


    public AudioRecorder(OutputStream outputStream) {
        this.outputStream = outputStream;
        try {
            AudioFormat format = new AudioFormat(16000.0f, 16, 2, true, false);
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
        while (!Thread.interrupted()) {
            try {
                int numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
                outputStream.write(data, 0, numBytesRead);
                outputStream.flush();
                log.debug("[Recorder] read {} from microphone", numBytesRead);
            } catch (IOException e) {
                log.error("[Recorder] error while transmitting data", e);
            }
        }
        microphone.stop();
    }

}
