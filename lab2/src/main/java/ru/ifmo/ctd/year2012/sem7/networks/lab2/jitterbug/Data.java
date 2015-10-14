package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public interface Data<T extends Data<T>> extends Comparable<T> {
    void writeToStream(ObjectOutputStream oos);

    T next();

    T readFromStream(ObjectInputStream ois);
}
