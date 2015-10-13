package ru.ifmo.ctd.year2012.sem7.networks.lab2;

import ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug.Data;

public class PiComputation implements Data<PiComputation> {
    @Override
    public byte[] getBytes() {
        return new byte[0];
    }

    @Override
    public PiComputation next() {
        return null;
    }

    @Override
    public PiComputation readFromBytes(byte[] bytes) {
        return null;
    }

    @Override
    public int compareTo(PiComputation o) {
        return 0;
    }
}
