package ru.georgeee.itmo.sem7.networks.lab1;

public class BreakingMessage extends Message {
    public BreakingMessage() {
        super(0, "");
    }

    @Override
    public byte[] getBytes() {
        int hostNameLength = 256;
        byte[] result = new byte[7 + Long.BYTES];
        copyBytesBE(getMacAddress(), result, 0, 6);
        result[6] = (byte) hostNameLength;
        return result;
    }
}
