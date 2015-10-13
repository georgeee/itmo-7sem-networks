package ru.georgeee.itmo.sem7.networks.lab1;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReceivedMap extends ConcurrentHashMap<Long, Pair<Long, Message>> {
}
