package ru.georgeee.itmo.sem7.networks.lab1;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;


@SpringBootApplication
public class Application implements CommandLineRunner {
    private final static Logger log = LoggerFactory.getLogger(Application.class);

    @Autowired
    private Receiver receiver;

    @Autowired
    private Sender sender;

    @Autowired
    private Monitor monitor;

    @Autowired
    private Settings settings;

    @Override
    public void run(String... args) {
        if (settings.getNetworkInterface() == null) {
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
        new Thread(sender).start();
        if (settings.getSendingStrategy().isLaunchReceiver()) {
            new Thread(receiver).start();
        }
        if (settings.getSendingStrategy().isLaunchMonitor()) {
            new Thread(monitor).start();
        }
    }

    private void printHelp() {
        System.out.println("java -jar lab1.jar --iface={interface} [opts]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("--iface={interface} \t\t\t network interface to use");
        System.out.println("--port={port} \t\t\t port to use, defaults to 30041");
//        System.out.println("--addr={addr} \t\t\t broadcast address to use, defaults to 255.255.255.255");
        System.out.println("--interval={interval} \t\t\t interval to use, in seconds, defaults to 5");
        System.out.println("--missedThreshold={threshold} \t\t\t threshold to use, number of packets to be missed to assume node as dead, defaults to 3");
        System.out.println("--strategy={sending strategy} \t\t\t one of " + Arrays.toString(Sender.getStrategyNames()));
    }

    public static void main(String[] args) throws Exception {
        SpringApplication application = new SpringApplication(Application.class);
        application.setApplicationContextClass(AnnotationConfigApplicationContext.class);
        SpringApplication.run(Application.class, args);
    }

}
