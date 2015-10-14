package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public interface Data<T extends Data<T>> extends Comparable<T> {
    void writeToStream(ObjectOutputStream oos) throws IOException;

    T next();

    T readFromStream(ObjectInputStream ois) throws IOException;
}
