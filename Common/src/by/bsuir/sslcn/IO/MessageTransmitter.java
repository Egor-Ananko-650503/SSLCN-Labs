package by.bsuir.sslcn.IO;

import by.bsuir.sslcn.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

import static by.bsuir.sslcn.Constants.BUFFER_WITH_HEADER_SIZE;

public class MessageTransmitter {

    public static DatagramPacket receivePacket(DatagramSocket socket) throws IOException {
        byte[] receiveData = new byte[BUFFER_WITH_HEADER_SIZE];

        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);

        return receivePacket;
    }

    public static byte[] extractReceivedData(DatagramPacket receivePacket) {
        byte[] usefulData = new byte[receivePacket.getLength()];
        System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), usefulData, 0, receivePacket.getLength());

        return usefulData;
    }

    public static Message receiveMessage(DatagramSocket socket) throws IOException {
        return Message.parseByteData(
                extractReceivedData(
                        receivePacket(socket)));
    }
}
