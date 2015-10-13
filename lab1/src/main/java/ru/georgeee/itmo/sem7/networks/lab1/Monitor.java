package ru.georgeee.itmo.sem7.networks.lab1;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


@Component
public class Monitor implements Runnable{
    private final static Logger log = LoggerFactory.getLogger(Monitor.class);

    @Autowired
    private Settings settings;

    @Autowired
    private ReceivedMap receivedMap;

    @Override
    public void run() {
        Map<Long, Message> alive = new HashMap<>();
        while (true) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                break;
            }
            long currentTime = System.currentTimeMillis();

            Iterator<Pair<Long, Message>> iterator = receivedMap.values().iterator();
            while(iterator.hasNext()){
                Pair<Long, Message> pair = iterator.next();
                long timestamp = pair.getLeft();
                Message msg = pair.getRight();
                if (currentTime - timestamp > settings.getMissedThreshold() * settings.getInterval() * 1000) {
                    alive.remove(msg.getMacAddress());
                    iterator.remove();
                } else {
                    alive.put(msg.getMacAddress(), msg);
                }
            }

            StringBuilder sb = new StringBuilder("Alive: ");
            for (Message msg : alive.values()) {
                sb.append("{")
                        .append(msg.getHostName())
                        .append(" 0x").append(Long.toHexString(msg.getMacAddress()))
                        .append("}, ");
            }
            log.info(sb.toString());

            try {
                Thread.sleep(settings.getInterval() * 1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

}

