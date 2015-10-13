package ru.georgeee.itmo.sem7.networks.lab1;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class Message {
    private final static Logger log = LoggerFactory.getLogger(Message.class);

    @Getter
    private final long macAddress;
    @Getter
    private final String hostName;
    @Getter
    private final Date createdDate;

    public Message(byte[] macAddress, String hostName) {
        this(macAddress == null ? 0 : readLongBE(reverse(macAddress), 0, 6), hostName, new Date());
    }

    public Message(long macAddress, String hostName) {
        this(macAddress, hostName, new Date());
    }

    public Message(long macAddress, String hostName, Date createdDate) {
        this.macAddress = macAddress;
        this.hostName = hostName;
        this.createdDate = createdDate;
    }

    protected static byte[] reverse(byte[] macAddress) {
        for (int i = 0; i < macAddress.length / 2; ++i) {
            byte c = macAddress[i];
            macAddress[i] = macAddress[macAddress.length - i - 1];
            macAddress[macAddress.length - i - 1] = c;
        }
        return macAddress;
    }

    public static Message readFromBytes(byte[] bytes) {
        long macAddress = readLongBE(bytes, 0, 6);
        int hostNameLength = Byte.toUnsignedInt(bytes[6]);
        String hostName = new String(bytes, 7, hostNameLength);
        Date createdDate = new Date(readLongBE(bytes, hostNameLength + 7, 8) * 1000);
        return new Message(macAddress, hostName, createdDate);
    }

    protected static long readLongBE(byte[] bytes, int offset, int length) {
        long result = 0;
        for (int i = 0; i < length; ++i) {
            result |= Byte.toUnsignedLong(bytes[(length - i - 1) + offset]) << (i * 8);
        }
        return result;
    }

    public byte[] getBytes() {
        byte[] hostNameBytes = hostName.getBytes();
        if (hostNameBytes.length > 256) {
            log.warn("HostName {} to large: need to be of length not more than 256 bytes, {} instead", hostName, hostNameBytes.length);
        }
        int hostNameLength = Math.min(hostNameBytes.length, 256);
        long timestamp = createdDate.getTime() / 1000;
        byte[] result = new byte[hostNameLength + 7 + Long.BYTES];
        copyBytesBE(macAddress, result, 0, 6);
        result[6] = (byte) hostNameLength;
        System.arraycopy(hostNameBytes, 0, result, 7, hostNameLength);
        copyBytesBE(timestamp, result, hostNameLength + 7, 8);
        return result;
    }

    protected void copyBytesBE(long src, byte[] result, int offset, int length) {
        for (int i = 0; i < length; ++i) {
            long mask = 0xffL << ((length - i - 1) * 8);
            result[i + offset] = (byte) ((src & mask) >> ((length - i - 1) * 8));
        }
    }

    @Override
    public String toString() {
        return "Message{" +
                "macAddress=0x" + Long.toHexString(macAddress) +
                ", hostName='" + hostName + '\'' +
                ", createdDate=" + createdDate +
                '}';
    }
}
