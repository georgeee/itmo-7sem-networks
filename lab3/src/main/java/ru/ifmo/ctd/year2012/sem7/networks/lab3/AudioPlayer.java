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
    private InputStream is;
    private volatile byte[] data;
    private volatile int numBytesRead;

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
            speakers.open(format);
            speakers.start();
            data = new byte[204800];

            while (true) {
                numBytesRead = is.read(data, 0, 1024);
                log.debug("[Player] received {} bytes", numBytesRead);
                speakers.write(data, 0, numBytesRead);
            }
        } catch (LineUnavailableException | IOException e) {
            e.printStackTrace();
        }
    }
}
