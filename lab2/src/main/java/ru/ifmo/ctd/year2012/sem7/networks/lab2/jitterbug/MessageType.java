package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import lombok.Getter;

enum MessageType {
    TR1(0), TR2(1),
    TP1(2), TP2(3),
    TP3(4), TP4(5),
    TP5(6);

    private static final MessageType[] BY_CODE;

    static {
        BY_CODE = new MessageType[values().length];
        for (MessageType type : values()) {
            BY_CODE[type.code] = type;
        }
    }

    @Getter
    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public static MessageType forCode(int code) {
        return (code >= 0 && code < BY_CODE.length) ? BY_CODE[code] : null;
    }
}
