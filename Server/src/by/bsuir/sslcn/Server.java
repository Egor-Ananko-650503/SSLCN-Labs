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
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import static by.bsuir.sslcn.Constants.*;

public class Server {

    public static final String WRONG_COMMAND = "Wrong command!";
    public static final String WRONG_COMMAND_ARGUMENTS = "Wrong command arguments!";
    public static final String WORK_DIR = "D:\\Projects\\IdeaProjects\\Java\\SSLСN\\Lab_2\\Server\\res\\content\\";
    public static final byte SERVER_WIN_VALUE = 5;

    private DatagramSocket serverSocket;
    private ClientInfo clientInfo = new ClientInfo();

    private Set<Integer> sentMessages = new TreeSet<>();
    private Set<Integer> receivedMessages = new TreeSet<>();
    private Set<Integer> ackMessages = new TreeSet<>();

    private int winMax = 1;
    private int winFree = 1;
    private int winMin = 0;

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.runServer(args);
    }

    public void runServer(String[] args) throws IOException {
        boolean running = true;

        serverSocket = new DatagramSocket(PORT, InetAddress.getByName(LOCALHOST));

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
                doUpload(args);
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

    private void doUpload(String args) throws IOException {
        String fileName = args.split(" ", 2)[0]; // TODO: check null

        RandomAccessFile rafFile = new RandomAccessFile(WORK_DIR + fileName, "rw");
        FileChannel fileChannel = rafFile.getChannel();

        int seq = 1;
        int read = 0;

        boolean isConnectionLost = false;
        boolean isConnectionIssue = false;
        long timeout = LocalTime.of(0, 1).getLong(ChronoField.MINUTE_OF_HOUR);
        LocalTime lastSuccess = null;

        serverSocket.setSoTimeout(1);
        while (!isConnectionLost) {
            try {
                Message msg = MessageTransmitter.receiveMessage(serverSocket);

                if (msg.fin) break;

                if (!receivedMessages.contains(msg.sequenceNumber)) {
                    rafFile.seek(msg.sequenceNumber - 1);
                    rafFile.write(msg.data);

                    receivedMessages.add(msg.sequenceNumber);
                }
            } catch (SocketTimeoutException ignored) {
            }

            if (!receivedMessages.isEmpty())
            {
                for (int ackNum : receivedMessages) {
                    sendAck(ackNum, SERVER_WIN_VALUE);
                }
                receivedMessages.clear();
                isConnectionIssue = false;
            } else {
                if (!isConnectionIssue) {
                    isConnectionIssue = true;
                    lastSuccess = LocalTime.now();
                } else if (LocalTime.now().getLong(ChronoField.MINUTE_OF_DAY) - lastSuccess.getLong(ChronoField.MINUTE_OF_DAY) > timeout) {
                    isConnectionLost = true;
                }
            }
        }
        serverSocket.setSoTimeout(0);

        MessageSender.sendFin(serverSocket, clientInfo);

        fileChannel.close();
        rafFile.close();

        receivedMessages.clear();

        if (isConnectionLost) {
            System.out.println("Connection lost");
            new File(WORK_DIR + fileName).delete();
        } else {
            System.out.println("File transfer is ended");
        }
    }

    private void sendAck(int ackNum, byte win) throws IOException {
        Message msg = new Message(0, ackNum, true, win, false, new byte[0]);
        MessageSender.sendMessage(serverSocket, clientInfo, msg);
    }

    private void doDownload(String args) throws IOException {
        String fileName = args.split(" ", 2)[0]; // TODO: check null

        File file = getFileFromWorkDir(fileName);
        if (file == null) {
            return;
        }

        RandomAccessFile rafFile = new RandomAccessFile(file, "r");
        byte[] buffer = new byte[BUFFER_SIZE];

        long length = rafFile.length();
        System.out.println("File length: " + length);

        winMax = 1;
        winFree = 1;
        winMin = 0;

        int seq = 1;
        int read = 0;

        boolean isFileEnd = false;

        serverSocket.setSoTimeout(1);
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
        serverSocket.setSoTimeout(0);

        MessageSender.sendFin(serverSocket, clientInfo);

        while (!MessageTransmitter.receiveMessage(serverSocket).fin) ;

        rafFile.close();

        winMax = 1;
        winFree = 1;
        winMin = 0;

        sentMessages.clear();
        ackMessages.clear();

        System.out.println("File transfer is ended");
    }

    private void collectAcks() throws IOException {
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
        MessageSender.sendMessage(serverSocket, clientInfo, msg);
    }

    private void sendString(String string) throws IOException {
        MessageSender.sendString(serverSocket, clientInfo, string);
    }
}
