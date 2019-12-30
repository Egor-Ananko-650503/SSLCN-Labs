package by.bsuir.sslcn;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class Message {

    public static final int HEADER_SIZE = 11;
    private static final byte DEFAULT_WIN_VALUE = 1;
    private static final boolean DEFAULT_FIN_VALUE = false;

    public int sequenceNumber;
    public int acknowledgmentNumber;
    public boolean ack;
    public byte win;
    public boolean fin;
    public byte[] data;

    public InetSocketAddress target;

    public Message() {
        this(0, 0, false, DEFAULT_WIN_VALUE, DEFAULT_FIN_VALUE, new byte[0]);
    }

    public Message(int seqNum, int ackNum, boolean ack, byte[] data) {
        this(seqNum, ackNum, ack, DEFAULT_WIN_VALUE, DEFAULT_FIN_VALUE, data);
    }

    public Message(int seqNum, int ackNum, boolean ack, byte win, boolean fin, byte[] data) {
        this.sequenceNumber = seqNum;
        this.acknowledgmentNumber = ackNum;
        this.ack = ack;
        this.win = win;
        this.fin = fin;
        this.data = data;
    }

    public static Message parseByteData(byte[] array) {
        return parseByteData(ByteBuffer.wrap(array));
    }

    public static Message parseByteData(ByteBuffer byteBuffer) {
        Message msg = new Message();

        msg.sequenceNumber = byteBuffer.getInt();
        msg.acknowledgmentNumber = byteBuffer.getInt();
        msg.ack = byteBuffer.get() == 1;
        msg.win = byteBuffer.get();
        msg.fin = byteBuffer.get() == 1;

        if (byteBuffer.limit() > HEADER_SIZE) {
            int dataLength = byteBuffer.limit() - HEADER_SIZE;
            msg.data = new byte[dataLength];
            System.arraycopy(byteBuffer.array(), HEADER_SIZE, msg.data, 0, dataLength);
        }

        return msg;
    }

    public byte[] getByteData() {
        return ByteBuffer.allocate(HEADER_SIZE + data.length)
                .putInt(sequenceNumber)
                .putInt(acknowledgmentNumber)
                .put((byte) (ack ? 1 : 0))
                .put(win)
                .put((byte) (fin ? 1 : 0))
                .put(data)
                .array();
    }

    public ByteBuffer getByteBufferData() {
        return ByteBuffer.wrap(getByteData());
    }

    @Override
    public String toString() {
        return Integer.toString(sequenceNumber) + ' '
                + acknowledgmentNumber + ' '
                + ack + ' '
                + win + ' '
                + fin + ' '
                + Arrays.toString(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message message = (Message) o;
        return sequenceNumber == message.sequenceNumber &&
                acknowledgmentNumber == message.acknowledgmentNumber &&
                ack == message.ack &&
                win == message.win &&
                fin == message.fin &&
                Arrays.equals(data, message.data) &&
                Objects.equals(target, message.target);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(sequenceNumber, acknowledgmentNumber, ack, win, fin, target);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}
