package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by kerzok11 on 19.12.2015.
 */
public class AudioRecorder extends Thread implements RecorderCallback {
    private static final Logger log = LoggerFactory.getLogger(AudioRecorder.class);
    private static final int CHUNK_SIZE = 10240;
    boolean finishFlag = false;
    private OutputStream outputStream;
    private TargetDataLine microphone;
    private volatile boolean isStarted;
    private volatile boolean isFinished;


    public AudioRecorder(OutputStream outputStream) {
        this.outputStream = outputStream;
        try {
            AudioFormat format = new AudioFormat(16000.0f, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
        } catch (LineUnavailableException e) {
            log.error("Unable to find microphone", e);
            microphone = null;
        }
    }

    public void run() {
        if (microphone == null) {
            return;
        }
        while (true) {
            if (isStarted) {
                isStarted = false;
                microphone.start();
                while (!isFinished) {
                    try {
                        byte[] data = new byte[CHUNK_SIZE];
                        while (!finishFlag) {
                            int numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
                            outputStream.write(data);
                            log.debug("[Recorder] read {} from microphone", numBytesRead);
                        }
                    } catch (IOException e) {
                        log.error("error while transmit data", e);
                    }
                }
                microphone.stop();
                isFinished = false;
            }
        }
    }

    @Override
    public void startRecording() {
        isStarted = true;
    }

    @Override
    public void stopRecording() {
        isFinished = true;
    }
}
