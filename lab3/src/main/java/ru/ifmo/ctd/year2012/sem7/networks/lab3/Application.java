package ru.ifmo.ctd.year2012.sem7.networks.lab3;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.ifmo.ctd.year2012.sem7.networks.lab3.transmission.Receiver;
import ru.ifmo.ctd.year2012.sem7.networks.lab3.transmission.Sender;

import javax.xml.bind.SchemaOutputResolver;
import java.io.*;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


@SpringBootApplication
public class Application implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    @Autowired
    private Settings settings;

    @Autowired
    private Receiver receiver;

    @Autowired
    private Sender sender;

    @Autowired
    private AudioPlayer audioPlayer;

    @Autowired
    private AudioRecorder audioRecorder;

    public static void main(String[] args) throws Exception {
        SpringApplication application = new SpringApplication(Application.class);
        application.setApplicationContextClass(AnnotationConfigApplicationContext.class);
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws IOException {
        if (settings.getNetworkInterface() == null) {
            System.out.println("Available interfaces:");
            try {
                Enumeration<NetworkInterface> ifaceEnumeration = NetworkInterface.getNetworkInterfaces();
                while (ifaceEnumeration.hasMoreElements()) {
                    NetworkInterface iface = ifaceEnumeration.nextElement();
                    System.out.println(iface.getDisplayName());
                }
            } catch (SocketException e) {
                log.warn("Exception caught while printing interfaces", e);
            }
        }
        if (settings.isStartReceiver()) {
            receiver.start();
            audioPlayer.start();
        }
        if (settings.isStartSender()) {
            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                    br.lines().forEach(line -> {
                        Thread recorderThread = new Thread(audioRecorder);
                        if ("start".equals(line)) {
                            if (System.currentTimeMillis() - receiver.getLastReceived() > settings.getLastSendTimeout()) {
                                sender.start();
                                recorderThread.start();
                            } else {
                                log.info("Somebody is holding the line, please try again a bit later");
                            }
                        } else {
                            if ("stop".equals(line)) {
                                recorderThread.interrupt();
                                sender.interrupt();
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("Error in sender example", e);
                }
            }).start();
        }
    }
    
}
