package by.bsuir.sslcn;

import java.net.DatagramPacket;
import java.net.InetAddress;

public class ClientInfo {
    public InetAddress ipAddress;
    public int port;

    public void extractInfo(DatagramPacket packet) {
        ipAddress = packet.getAddress();
        port = packet.getPort();
    }

    @Override
    public String toString() {
        return ipAddress + ":" + port;
    }
}
