package by.bsuir.sslcn;

import by.bsuir.sslcn.IO.MessageSender;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;

import static by.bsuir.sslcn.Constants.*;
import static by.bsuir.sslcn.Server.*;

enum ServerOperation {
    NONE,
    DOWNLOAD,
    UPLOAD
}

public class ClientContainer {

    public String args;
    public ByteBuffer incomingPacket;
    public Message incomingMessage;

    public InetSocketAddress address;
    public ServerOperation lastOp = ServerOperation.NONE;
    public String fileName;
    public File file;
    public RandomAccessFile rafFile;
    public long fileOffset;
    public long fileLength;
    public int winMax;
    public int winMin;
    public int winFree;
    public int seq;
    public int read;
    public boolean isFileEnd;
    public boolean isConnectionIssue;
    public boolean isConnectionLost;
    public long timeout;
    public LocalTime lastSuccess;

    public boolean isTransferInitialized = false;
    public boolean isFinSent = false;
    public boolean isEndCommand = false;

    public Set<Integer> sentMessages = new TreeSet<>();
    public Set<Integer> receivedMessages = new TreeSet<>();
    public Set<Integer> ackMessages = new TreeSet<>();

    public List<Message> responseQueue = new LinkedList<>();

    private ClientContainer() {
    }

    ClientContainer(SocketAddress address) {
        this.address = (InetSocketAddress) address;
    }

    public List<Message> parseDataPacket(ByteBuffer packet) throws IOException {
        incomingPacket = ByteBuffer.wrap(packet.array());
        incomingMessage = Message.parseByteData(incomingPacket);
        responseQueue.clear();

        switch (lastOp) {
            case NONE:
                parseCommand(packet);
                break;
            case UPLOAD:
                winFree = (winFree < winMax ? winFree + 1 : winMax);
                doUpload();
                break;
            case DOWNLOAD:
                winFree = (winFree < winMax ? winFree + 1 : winMax);
                doDownload();
                break;
        }

        return responseQueue;
    }

    public void parseCommand(ByteBuffer buffer) throws IOException {
        Message msg = Message.parseByteData(buffer);
        String request = new String(msg.data != null ? msg.data : "".getBytes());
        System.out.println("FROM CLIENT [" + address + "]: " + request);

        String[] splittedCommand = request.split(" ", 2);
        String command = splittedCommand[0];
        args = splittedCommand.length > 1 ? splittedCommand[1] : null;

        switch (command.toUpperCase()) {
            case COMMAND_END:
                isEndCommand = true;
                break;
            case COMMAND_ECHO:
                doEcho();
                break;
            case COMMAND_TIME:
                doTime();
                break;
            case COMMAND_UPLOAD:
                doUpload();
                break;
            case COMMAND_DOWNLOAD:
                doDownload();
                break;
            default:
                addDefaultResponse(WRONG_COMMAND.getBytes());
                break;
        }
    }

    private void doEcho() {
        addDefaultResponse((args != null ? args : WRONG_COMMAND_ARGUMENTS).getBytes());
    }

    private void doTime() {
        String currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());

        addDefaultResponse(currentDate.getBytes());
    }

    public void doUpload() throws IOException {
        if (!isTransferInitialized) {
            isTransferInitialized = true;
            lastOp = ServerOperation.UPLOAD;
        }

        isTransferInitialized = false;
        lastOp = ServerOperation.NONE;
    }

    public void doDownload() throws IOException {
        if (!isTransferInitialized) {
            isTransferInitialized = true;
            isFinSent = false;
            lastOp = ServerOperation.DOWNLOAD;

            fileName = args.split(" ", 2)[0];
            file = getFileFromWorkDir(fileName);
            if (file == null) {
                return;
            }

            rafFile = new RandomAccessFile(file, "r");

            fileLength = rafFile.length();
            System.out.println("File length: " + fileLength);

            winMax = 1;
            winFree = 1;
            winMin = 0;

            seq = 1;
            read = 0;

            isFileEnd = false;
        }

        if (!isFileEnd || !sentMessages.isEmpty()) {
            byte[] buffer = new byte[BUFFER_SIZE];

            resendMissed(buffer);
            collectAcks();

            if (!isFileEnd) {
                read = rafFile.read(buffer);
                if (read == -1) {
                    isFileEnd = true;
                } else {
                    Message msg = MessageSender.constructMessage(seq, read, buffer);
                    msg.target = address;
                    responseQueue.add(msg);

                    sentMessages.add(seq);

                    seq += read;

                    if (winFree > winMin) winFree--;
                }
            }
        } else {
            if (!isFinSent) {
                Message msg = new Message();
                msg.target = address;
                msg.fin = true;

                isFinSent = true;
                responseQueue.add(msg);
            }

            if (incomingMessage.fin) {
                rafFile.close();

                winMax = 1;
                winFree = 1;
                winMin = 0;

                sentMessages.clear();
                ackMessages.clear();

                isTransferInitialized = false;
                isFinSent = false;
                lastOp = ServerOperation.NONE;

                System.out.println("File transfer is ended");
            }
        }
    }


    private void resendMissed(byte[] buffer) throws IOException {
        int read;
        if (!sentMessages.isEmpty()) {
            long currPosition = rafFile.getFilePointer();
            for (int seqForResend : sentMessages) {
                rafFile.seek(seqForResend - 1);
                read = rafFile.read(buffer);

                Message msg = MessageSender.constructMessage(seqForResend, read, buffer);
                msg.target = address;
                responseQueue.add(msg);

                if (winFree > winMin) winFree--;
                else break;
            }
            rafFile.seek(currPosition);
        }
    }

    private void collectAcks() {
        if (incomingMessage.ack) {
            ackMessages.add(incomingMessage.acknowledgmentNumber);
            sentMessages.remove(incomingMessage.acknowledgmentNumber);
            if (winMax != incomingMessage.win) {
                winFree = Math.max(winFree + (incomingMessage.win - winMax), winMin);
                winMax = incomingMessage.win;
            }
        }
    }

    private File getFileFromWorkDir(String fileName) {
        File file = new File(WORK_DIR + fileName);
        return (file.exists() ? file : null);
    }

    private void addDefaultResponse(byte[] bytes) {
        Message msg = new Message();
        msg.data = bytes;
        msg.target = address;

        responseQueue.add(msg);
    }
}
