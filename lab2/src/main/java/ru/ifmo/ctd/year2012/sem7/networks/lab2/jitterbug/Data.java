package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

public interface Data<T extends Data<T>> extends Comparable<T> {
    byte[] getBytes();

    T next();

    T readFromBytes(byte[] bytes);
}
