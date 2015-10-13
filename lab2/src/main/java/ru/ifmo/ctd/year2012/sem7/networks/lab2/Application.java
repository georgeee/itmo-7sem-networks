package ru.ifmo.ctd.year2012.sem7.networks.lab2;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug.Jitterbug;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


@SpringBootApplication
public class Application implements CommandLineRunner {
    private final static Logger log = LoggerFactory.getLogger(Application.class);

    @Autowired
    private AppSettings appSettings;

    @Autowired
    private JitterbugSettings jitterbugSettings;

    public static void main(String[] args) throws Exception {
        SpringApplication application = new SpringApplication(Application.class);
        application.setApplicationContextClass(AnnotationConfigApplicationContext.class);
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws IOException {
        if (jitterbugSettings.getNetworkInterface() == null) {
            printHelp();
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
            return;
        }
        Jitterbug jitterbug = new Jitterbug(jitterbugSettings);
        jitterbug.start();
        try {
            jitterbug.awaitTermination();
        } catch (InterruptedException e) {
            jitterbug.stop();
        }
    }

    private void printHelp() {
        System.out.println("java -jar jitterbug.jar [opts]");
//        System.out.println();
//        System.out.println("Options:");
//        System.out.println("--udp.iface={interface} \t\t\t network interface to use for UDP broadcasting");
//        System.out.println("--udp.port={port} \t\t\t port to use for UDP broadcasting, defaults to 30041");
    }

}
