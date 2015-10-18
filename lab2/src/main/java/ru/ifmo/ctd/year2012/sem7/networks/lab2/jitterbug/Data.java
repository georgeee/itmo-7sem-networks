package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface Data<T extends Data<T>> {
    void writeToStream(DataOutputStream oos) throws IOException;

    T next();

    T readFromStream(DataInputStream ois) throws IOException;

    T mergeWith(T data);
}
