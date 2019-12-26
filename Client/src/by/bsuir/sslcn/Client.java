package by.bsuir.sslcn;

import by.bsuir.sslcn.IO.MessageSender;
import by.bsuir.sslcn.IO.MessageTransmitter;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.TreeSet;

import static by.bsuir.sslcn.Constants.*;

public class Client {

    private static final String WORK_DIR = "D:\\STUDY\\7 term\\SPOLKS\\Lab_2\\Client\\res\\content\\";
    private static final byte CLIENT_WIN_VALUE = 10;
    private DatagramSocket clientSocket;
    private ClientInfo clientInfo = new ClientInfo();

    private Set<Integer> sentMessages = new TreeSet<>();
    private Set<Integer> receivedMessages = new TreeSet<>();
    private Set<Integer> ackMessages = new TreeSet<>();

    private int winMax = 1;
    private int winFree = 1;
    private int winMin = 0;

    public static void main(String[] args) throws Exception {
        Client client = new Client();
        client.runClient(args);
    }

    public void runClient(String[] args) throws Exception {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        clientInfo.ipAddress = InetAddress.getByName("192.168.43.171");
        clientInfo.port = PORT;
        clientSocket = new DatagramSocket(clientInfo.port, InetAddress.getByName("192.168.43.34"));

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

            switch (command.toUpperCase()) {
                case COMMAND_DOWNLOAD:
                    doDownload(commandArgs);
                    continue;
                case COMMAND_UPLOAD:
                    doUpload(commandArgs);
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
        while (true) {
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

    private void doUpload(String args) throws IOException {
        String fileName = args.split(" ", 2)[0]; // TODO: check null

        File file = getFileFromWorkDir(fileName);
        if (file == null) {
            return;
        }

        RandomAccessFile rafFile = new RandomAccessFile(file, "r");
        FileChannel fileChannel = rafFile.getChannel();
        byte[] buffer = new byte[BUFFER_SIZE];

        long length = rafFile.length();
        System.out.println("File length: " + length);

        winMax = 1;
        winFree = 1;
        winMin = 0;

        int seq = 1;
        int read = 0;

        boolean isFileEnd = false;

        clientSocket.setSoTimeout(1);
        while (!isFileEnd || !sentMessages.isEmpty()) {
            resendMissed(rafFile, buffer);

            collectAcks();

            while (!isFileEnd) {
                read = rafFile.read(buffer);
                if (read == -1) {
                    isFileEnd = true;
                    break;
                }

                Message msg = MessageSender.constructMessage(seq, read, buffer);
                sendMessage(msg);

                sentMessages.add(seq);

                seq += read;

                if (winFree > winMin) winFree--;
                else break;
            }

            collectAcks();
        }
        clientSocket.setSoTimeout(0);

        MessageSender.sendFin(clientSocket, clientInfo);

        while (!MessageTransmitter.receiveMessage(clientSocket).fin) ;

        fileChannel.close();
        rafFile.close();

        winMax = 1;
        winFree = 1;
        winMin = 0;

        sentMessages.clear();
        ackMessages.clear();

        System.out.println("File transfer is ended");
    }

    private void sendAck(int ackNum, byte win) throws IOException {
        Message msg = new Message(0, ackNum, true, win, false, new byte[0]);
        MessageSender.sendMessage(clientSocket, clientInfo, msg);
    }

    private void collectAcks() throws IOException {
        try {
            while (true) {
                Message msgAck = MessageTransmitter.receiveMessage(clientSocket);
                winFree = (winFree < winMax ? winFree + 1 : winMax);

                if (msgAck.ack) {
                    ackMessages.add(msgAck.acknowledgmentNumber);
                    sentMessages.remove(msgAck.acknowledgmentNumber);
                    if (winMax != msgAck.win) {
                        winFree = Math.max(winFree + (msgAck.win - winMax), winMin);
                        winMax = msgAck.win;
                    }
                }
            }
        } catch (SocketTimeoutException ignored) {
        }
    }

    private void resendMissed(RandomAccessFile rafFile, byte[] buffer) throws IOException {
        int read;
        if (!sentMessages.isEmpty()) {
            long currPosition = rafFile.getFilePointer();
            for (int seqForResend : sentMessages) {
                rafFile.seek(seqForResend - 1);
                read = rafFile.read(buffer);

                Message msg = MessageSender.constructMessage(seqForResend, read, buffer);
                sendMessage(msg);

                if (winFree > winMin) winFree--;
                else break;
            }
            rafFile.seek(currPosition);
        }
    }

    private File getFileFromWorkDir(String fileName) {
        File file = new File(WORK_DIR + fileName);
        return (file.exists() ? file : null);
    }

    private void sendMessage(Message msg) throws IOException {
        MessageSender.sendMessage(clientSocket, clientInfo, msg);
    }

    private void sendString(String string) throws IOException {
        MessageSender.sendString(clientSocket, clientInfo, string);
    }
}
