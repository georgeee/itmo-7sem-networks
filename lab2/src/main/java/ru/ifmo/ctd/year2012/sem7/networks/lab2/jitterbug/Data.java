package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.Serializable;

public interface Data<T extends Data<T>> extends Serializable {
    T next();
    T mergeWith(T data);
}
