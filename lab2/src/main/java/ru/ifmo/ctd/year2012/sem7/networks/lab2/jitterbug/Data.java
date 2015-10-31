package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

public interface Data<T extends Data<T>> {
    void writeToStream(ObjectOutputStream oos) throws IOException;

    T next();

    T readFromStream(ObjectInputStream ois) throws IOException;

    T mergeWith(T data);
}
