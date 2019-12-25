package by.bsuir.sslcn;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
        Message msg = new Message();
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);

        msg.sequenceNumber = byteBuffer.getInt();
        msg.acknowledgmentNumber = byteBuffer.getInt();
        msg.ack = byteBuffer.get() == 1;
        msg.win = byteBuffer.get();
        msg.fin = byteBuffer.get() == 1;

        if (array.length > HEADER_SIZE) {
            msg.data = new byte[array.length - HEADER_SIZE];
            System.arraycopy(array, HEADER_SIZE, msg.data, 0, array.length - HEADER_SIZE);
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
                .put(data).array();
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
}
