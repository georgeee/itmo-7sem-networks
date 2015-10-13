package ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

abstract class TPHandler {

    public void handleTP1(int tokenId, int nodeListHash) throws IOException, ParseException {
        throw new ParseException("TP1 message not expected: tokenId=" + tokenId + " nodeListHash=" + nodeListHash);
    }

    public void handleTP2() throws IOException, ParseException {
        throw new ParseException("TP2 message not expected");
    }

    public void handleTP3() throws IOException, ParseException {
        throw new ParseException("TP3 message not expected");
    }

    public void handleTP4(List<Node> nodes) throws IOException, ParseException {
        throw new ParseException("TP4 message not expected");
    }

    public void handleTP5(int tokenId, ObjectInputStream dataStream) throws IOException, ParseException {
        throw new ParseException("TP5 message not expected: tokenId=" + tokenId);
    }
}
