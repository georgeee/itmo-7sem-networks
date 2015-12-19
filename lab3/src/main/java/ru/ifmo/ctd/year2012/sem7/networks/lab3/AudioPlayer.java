package ru.ifmo.ctd.year2012.sem7.networks.lab3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.ifmo.ctd.year2012.sem7.networks.lab3.transmission.Receiver;

import javax.sound.sampled.*;
import java.io.IOException;

@Component
public class AudioPlayer extends Thread {
    private static final Logger log = LoggerFactory.getLogger(AudioPlayer.class);

    @Autowired
    private Receiver receiver;

    @Autowired
    private Settings settings;

    @Override
    public void run() {
        try {
            AudioFormat format = settings.getAudioFormat();
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            speakers.open(format);
            speakers.start();
            byte[] data = new byte[204800];

            while (!Thread.interrupted()) {
                int numBytesRead = receiver.getInputStream().read(data, 0, 1024);
                log.debug("[Player] received {} bytes", numBytesRead);
                speakers.write(data, 0, numBytesRead);
            }
        } catch (LineUnavailableException | IOException e) {
            log.error("[Player] error occurred", e);
        }
    }
}
