package by.bsuir.sslcn.IO;

import by.bsuir.sslcn.ClientInfo;
import by.bsuir.sslcn.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class MessageSender {

    public static void sendString(DatagramSocket socket, ClientInfo clientInfo, String string) throws IOException {
        Message sendMsg = new Message();
        sendMsg.data = string.getBytes();

        sendMessage(socket, clientInfo, sendMsg);
    }

    public static void sendMessage(DatagramSocket socket, ClientInfo clientInfo, Message msg) throws IOException {
        byte[] sendData = msg.getByteData();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientInfo.ipAddress, clientInfo.port);
        socket.send(sendPacket);
    }

    public static Message constructMessage(int seq, int size, byte[] buffer) {
        byte[] usefulData = new byte[size];
        System.arraycopy(buffer, 0, usefulData, 0, size);

        return new Message(seq, seq + size, false, usefulData);
    }

    public static void sendFin(DatagramSocket serverSocket, ClientInfo clientInfo) throws IOException {
        Message msg = new Message();
        msg.fin = true;
        sendMessage(serverSocket, clientInfo, msg);
    }
}
