package by.bsuir.sslcn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

import static by.bsuir.sslcn.Constants.*;

public class AsyncServer {

    private static final String WRONG_COMMAND = "Wrong command!";
    private static final String WRONG_COMMAND_ARGUMENTS = "Wrong command arguments!";
    private static final String WORK_DIR = "D:\\Projects\\IdeaProjects\\Java\\SSLСN\\Lab_2\\Server\\res\\content\\";
    private static final byte SERVER_WIN_VALUE = 5;
    public static final int CLIENTS_DEFAULT_SIZE = 10;
    private boolean bExit = false;

    Set<Message> responseQueue = new LinkedHashSet<>();
    List<ClientContainer> clients = new ArrayList<>(CLIENTS_DEFAULT_SIZE);

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
//                                System.out.println("In readable block");
                            } else if (key.isWritable()) {
                                if (!responseQueue.isEmpty()) {
                                    processWrite(key);
//                                    System.out.println("In writable block");
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

        Iterator<Message> iterator = responseQueue.iterator();
        if (iterator.hasNext()) {
            Message msg = iterator.next();
            iterator.remove();
            channel.send(msg.getByteBufferData(), msg.target);
        }
    }

    private void processRead(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Connection con = (Connection) key.attachment();

        con.request.clear();
        con.socketAddress = channel.receive(con.request);
        con.request.flip();

        ClientContainer client = findClientByAddress(con.socketAddress);
        if (client == null) {
            client = new ClientContainer(con.socketAddress);
            clients.add(client);
        }

        List<Message> responseSet = client.parseDataPacket(con.request);

        if (client.isEndCommand) {
            bExit = true;
        }

        responseQueue.addAll(responseSet);
    }

    private ClientContainer findClientByAddress(SocketAddress address) {
        for (ClientContainer clientContainer : clients) {
            if (clientContainer.address.equals(address)) {
                return clientContainer;
            }
        }

        return null;
    }

    public static class Connection {
        ByteBuffer request;
        ByteBuffer response;
        SocketAddress socketAddress;

        public Connection() {
            request = ByteBuffer.allocate(BUFFER_WITH_HEADER_SIZE);
        }
    }
}
