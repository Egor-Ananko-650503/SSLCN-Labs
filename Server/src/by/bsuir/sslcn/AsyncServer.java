package by.bsuir.sslcn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static by.bsuir.sslcn.Constants.*;

public class AsyncServer {

    public static final int RESPONSE_QUEUE_SIZE = 100;
    private static final String WRONG_COMMAND = "Wrong command!";
    private static final String WRONG_COMMAND_ARGUMENTS = "Wrong command arguments!";
    private static final String WORK_DIR = "D:\\Projects\\IdeaProjects\\Java\\SSLСN\\Lab_2\\Server\\res\\content\\";
    private static final byte SERVER_WIN_VALUE = 5;
    private boolean bExit = false;
    private List<Message> responseQueue = new ArrayList<Message>(RESPONSE_QUEUE_SIZE);

    public static void main(String[] args) {
        AsyncServer asyncServer = new AsyncServer();
        asyncServer.process();
    }

    private void process() {
        try {
            Selector selector = Selector.open();

            DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET);
            InetSocketAddress isa = new InetSocketAddress(LOCALHOST, PORT);
            channel.socket().bind(isa);
            channel.configureBlocking(false);

            int ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
            SelectionKey selectionKey = channel.register(selector, ops, new Connection());

            while (!bExit) {
                try {
                    selector.select();
                    Iterator<SelectionKey> selectionKeyIterator = selector.selectedKeys().iterator();

                    while (selectionKeyIterator.hasNext()) {
                        try {
                            SelectionKey key = selectionKeyIterator.next();
                            selectionKeyIterator.remove();

                            if (!key.isValid()) {
                                continue;
                            }

                            if (key.isReadable()) {
                                processRead(key);
                                System.out.println("In readable block");
                            } else if (key.isWritable()) {
                                if (!responseQueue.isEmpty()) {
                                    processWrite(key);
                                    System.out.println("In writable block");
                                }
                            }

                        } catch (IOException e) {
                            System.err.println("glitch, continuing... " + (e.getMessage() != null ? e.getMessage() : ""));
                        }
                    }

                } catch (IOException e) {
                    System.err.println("glitch, continuing... " + (e.getMessage() != null ? e.getMessage() : ""));
                }
            }

        } catch (IOException e) {
            System.err.println("network error: " + (e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void processWrite(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Connection con = (Connection) key.attachment();

        con.response = responseQueue.remove(0).getByteBufferData();
        channel.send(con.response, con.socketAddress);
    }

    private void processRead(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Connection con = (Connection) key.attachment();

        con.request.clear();
        con.socketAddress = channel.receive(con.request);
        con.request.flip();

        Message msg = Message.parseByteData(con.request);
        String request = new String(msg.data != null ? msg.data : "".getBytes());
        System.out.println("FROM CLIENT [" + con.socketAddress + "]: " + request);

        if (request.equalsIgnoreCase(COMMAND_END)) {
            bExit = true;
        } else {
            parseRequest(request, channel, con);
        }
    }

    private void parseRequest(String request, /*REMOVE*/ DatagramChannel channel, Connection con) {
        String[] splittedCommand = request.split(" ", 2);
        String command = splittedCommand[0];
        String args = splittedCommand.length > 1 ? splittedCommand[1] : null;

        switch (command.toUpperCase()) {
            case COMMAND_ECHO:
                doEcho(args, channel, con);
                break;
            case COMMAND_TIME:
                doTime(channel, con);
                break;
            case COMMAND_UPLOAD:
//                doUpload(args);
                break;
            case COMMAND_DOWNLOAD:
//                doDownload(args);
                break;
            default:
                addDefaultResponse(con, WRONG_COMMAND.getBytes());
                break;
        }
    }

    private void doTime(DatagramChannel channel, Connection con) {
        String currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());

        addDefaultResponse(con, currentDate.getBytes());
    }

    private void doEcho(String args, DatagramChannel channel, Connection con) {
        addDefaultResponse(con, (args != null ? args : WRONG_COMMAND_ARGUMENTS).getBytes());
    }

    private void addDefaultResponse(Connection con, byte[] bytes) {
        Message msg = new Message();
        msg.data = bytes;

        responseQueue.add(msg);
    }

    private static class Connection {
        ByteBuffer request;
        ByteBuffer response;
        SocketAddress socketAddress;

        public Connection() {
            request = ByteBuffer.allocate(BUFFER_WITH_HEADER_SIZE);
        }
    }
}
