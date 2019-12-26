package by.bsuir.sslcn;

import by.bsuir.sslcn.IO.MessageSender;
import by.bsuir.sslcn.IO.MessageTransmitter;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static by.bsuir.sslcn.Constants.*;

public class Server {

    private static final String WRONG_COMMAND = "Wrong command!";
    private static final String WRONG_COMMAND_ARGUMENTS = "Wrong command arguments!";
    private static final String WORK_DIR = "D:\\Projects\\IdeaProjects\\Java\\SSLСN\\Lab_2\\Server\\res\\content\\";
    private static final byte SERVER_WIN_VALUE = 5;

    private DatagramSocket serverSocket;
    private ClientInfo clientInfo = new ClientInfo();

    private Set<Integer> sentMessages = new TreeSet<>();
    private Set<Integer> receivedMessages = new TreeSet<>();
    private Set<Integer> ackMessages = new TreeSet<>();

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.runServer(args);
    }

    public void runServer(String[] args) throws IOException {
        boolean running = true;

        serverSocket = new DatagramSocket(PORT, InetAddress.getByName("192.168.43.171"));

        byte[] receiveData = new byte[BUFFER_SIZE];
        byte[] sendData = new byte[BUFFER_SIZE];

        while (running) {
            DatagramPacket receivePacket = MessageTransmitter.receivePacket(serverSocket);
            receiveData = MessageTransmitter.extractReceivedData(receivePacket);
            Message receiveMsg = Message.parseByteData(receiveData);

            clientInfo.extractInfo(receivePacket);

            String command = new String(receiveMsg.data != null ? receiveMsg.data : "".getBytes());
            System.out.println("RECEIVED [" + clientInfo + "]: " + command);

            if (command.equalsIgnoreCase("end")) {
                running = false;
                continue;
            } else {
                parseCommand(command);
            }
        }
    }

    private void parseCommand(String sourceCommand) throws IOException {
        String[] splittedCommand = sourceCommand.split(" ", 2);
        String command = splittedCommand[0];
        String args = splittedCommand.length > 1 ? splittedCommand[1] : null;

        switch (command.toUpperCase()) {
            case COMMAND_ECHO:
                doEcho(args);
                break;
            case COMMAND_TIME:
                doTime();
                break;
            case COMMAND_UPLOAD:
                break;
            case COMMAND_DOWNLOAD:
                doDownload(args);
                break;
            default:
                sendString(WRONG_COMMAND);
                break;
        }
    }

    private void doEcho(String message) throws IOException {
        sendString(message != null ? message : WRONG_COMMAND_ARGUMENTS);
    }

    private void doTime() throws IOException {
        sendString((new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")).format(new Date()));
    }

    private void doDownload(String args) throws IOException {
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

        int winMax = 1;
        int winFree = 1;
        int winMin = 0;

        int seq = 1;
        int read = 0;

        boolean isFileEnd = false;

        serverSocket.setSoTimeout(1);
        while (!isFileEnd || !sentMessages.isEmpty()) {
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

            try {
                while (true) {
                    Message msgAck = MessageTransmitter.receiveMessage(serverSocket);
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

            try {
                while (true) {
                    Message msgAck = MessageTransmitter.receiveMessage(serverSocket);
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
        serverSocket.setSoTimeout(0);

        MessageSender.sendFin(serverSocket, clientInfo);

        while (!MessageTransmitter.receiveMessage(serverSocket).fin) ;

        fileChannel.close();
        rafFile.close();

        sentMessages.clear();
        ackMessages.clear();

        System.out.println("File transfer is ended");
    }

    private void resendMissed(RandomAccessFile rafFile, byte[] buffer) throws IOException {
        if (sentMessages.isEmpty()) return;
        // TODO: winFree
        int read;
        long currPosition = rafFile.getFilePointer();

        for (int seqForResend : sentMessages) {
            rafFile.seek(seqForResend - 1);
            read = rafFile.read(buffer);
            Message msg = MessageSender.constructMessage(seqForResend, read, buffer);
            sendMessage(msg);
        }
        rafFile.seek(currPosition);
    }

    private File getFileFromWorkDir(String fileName) {
        File file = new File(WORK_DIR + fileName);
        return (file.exists() ? file : null);
    }

    private void sendMessage(Message msg) throws IOException {
        MessageSender.sendMessage(serverSocket, clientInfo, msg);
    }

    private void sendString(String string) throws IOException {
        MessageSender.sendString(serverSocket, clientInfo, string);
    }
}
