package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
/**
 * Created by kerzok
 */
public class AudioPlayer extends Thread {
    private static final Logger log = LoggerFactory.getLogger(AudioPlayer.class);
    private final InputStream is;

    public AudioPlayer(InputStream is) {
        this.is = is;
    }

    @Override
    public void run() {
        try {
            boolean bigEndian = false;
            AudioFormat format = new AudioFormat(16000.0f, 16, 2, true, bigEndian);
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            System.out.println();
            speakers.open(format);
            speakers.start();
            byte[] data = new byte[204800];

            while (!Thread.interrupted()) {
                int numBytesRead = is.read(data, 0, 1024);
                log.debug("[Player] received {} bytes", numBytesRead);
                speakers.write(data, 0, numBytesRead);
            }
        } catch (LineUnavailableException | IOException e) {
            log.error("[Player] error occurred", e);
        }
    }
}
