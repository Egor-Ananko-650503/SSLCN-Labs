package by.bsuir.sslcn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import static by.bsuir.sslcn.Constants.*;

public class AsyncServer {

    private boolean bExit = false;

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
                                processWrite(key);
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

        if (con.hasResponse) {
            channel.send(con.response, con.socketAddress);
            con.hasResponse = false;
            System.out.println("In writable block");
        }
    }

    private void processRead(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        Connection con = (Connection) key.attachment();

        con.request.clear();
        con.socketAddress = channel.receive(con.request);
        con.request.flip();

        String request = new String(con.request.array(), 0, con.request.limit());
        System.out.println("Request: " + request);

        String response = String.format("Response to request [%s] from [%s]", request, con.socketAddress.toString());
        con.response = ByteBuffer.wrap(response.getBytes());
        con.hasResponse = true;
    }

    private static class Connection {
        ByteBuffer request;
        ByteBuffer response;
        SocketAddress socketAddress;
        boolean hasResponse;

        public Connection() {
            request = ByteBuffer.allocate(BUFFER_WITH_HEADER_SIZE);
            hasResponse = false;
        }
    }
}
