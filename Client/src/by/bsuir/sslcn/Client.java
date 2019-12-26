package by.bsuir.sslcn;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Set;

import by.bsuir.sslcn.IO.MessageSender;
import by.bsuir.sslcn.IO.MessageTransmitter;
import by.bsuir.sslcn.Message;

import static by.bsuir.sslcn.Constants.*;

public class Client {

    private static final String WORK_DIR = "D:\\Projects\\IdeaProjects\\Java\\SSLСN\\Lab_2\\Client\\res\\content\\";
    private static final byte CLIENT_WIN_VALUE = 5;
    private DatagramSocket clientSocket;
    private ClientInfo clientInfo = new ClientInfo();

    private Set<Integer> sentMessages = new HashSet<>();
    private Set<Integer> receivedMessages = new HashSet<>();
    private Set<Integer> ackMessages = new HashSet<>();

    public static void main(String[] args) throws Exception {
        Client client = new Client();
        client.runClient(args);
    }

    public void runClient(String[] args) throws Exception {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        clientInfo.ipAddress = InetAddress.getByName("25.107.253.63");
        clientInfo.port = PORT;
        clientSocket = new DatagramSocket(clientInfo.port, InetAddress.getByName("25.107.253.89"));

        byte[] sendData = new byte[BUFFER_SIZE];
        byte[] receiveData = new byte[BUFFER_SIZE];
        boolean running = true;

        while (running) {
            String userInput = inFromUser.readLine();

            Message sendMsg = new Message();
            sendMsg.data = userInput.getBytes();
            sendData = sendMsg.getByteData();

            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientInfo.ipAddress, clientInfo.port);
            clientSocket.send(sendPacket);

            String[] splittedCommand = userInput.split(" ", 2);
            String command = splittedCommand[0];
            String commandArgs = splittedCommand.length > 1 ? splittedCommand[1] : null;

            switch (command.toUpperCase())
            {
                case COMMAND_DOWNLOAD:
                    doDownload(commandArgs);
                    continue;
                case COMMAND_UPLOAD:
                    continue;
                case COMMAND_END:
                    running = false;
                    continue;
            }

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);

            byte[] usefulData = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), usefulData, 0, receivePacket.getLength());

            Message receiveMsg = Message.parseByteData(usefulData);

            String modifiedSentence = new String(receiveMsg.data);
            System.out.println("FROM SERVER: " + modifiedSentence);
        }

        clientSocket.close();
    }

    private void doDownload(String args) throws IOException {
        String fileName = args.split(" ", 2)[0]; // TODO: check null

        RandomAccessFile rafFile = new RandomAccessFile(WORK_DIR + fileName, "rw");
        FileChannel fileChannel = rafFile.getChannel();

        int seq = 1;
        int read = 0;

        clientSocket.setSoTimeout(1);
        while(true) {
            try {
                Message msg = MessageTransmitter.receiveMessage(clientSocket);

                if (msg.fin) break;

                if (!receivedMessages.contains(msg.sequenceNumber)) {
                    rafFile.seek(msg.sequenceNumber - 1);
                    rafFile.write(msg.data);

                    receivedMessages.add(msg.sequenceNumber);
                }
            } catch (SocketTimeoutException ignored) {
            }

            for (int ackNum : receivedMessages) {
                sendAck(ackNum, CLIENT_WIN_VALUE);
            }
            receivedMessages.clear();
        }
        clientSocket.setSoTimeout(0);

        MessageSender.sendFin(clientSocket, clientInfo);

        fileChannel.close();
        rafFile.close();

        receivedMessages.clear();

        System.out.println("File transfer is ended");
    }

    private void sendAck(int ackNum, byte win) throws IOException {
        Message msg = new Message(0, ackNum, true, win, false, new byte[0]);
        MessageSender.sendMessage(clientSocket, clientInfo, msg);
    }
}
