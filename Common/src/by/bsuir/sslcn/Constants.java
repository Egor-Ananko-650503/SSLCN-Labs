package by.bsuir.sslcn;

public class Constants {

    public static final String LOCALHOST = "localhost";
    public static final int PORT = 7575;
    public static final int CLIENT_LOCAL_PORT = 7576;

    public static final int BUFFER_SIZE = 1024;
    public static final int BUFFER_WITH_HEADER_SIZE = 1024 + Message.HEADER_SIZE;

    public static final String COMMAND_ECHO = "ECHO";
    public static final String COMMAND_TIME = "TIME";
    public static final String COMMAND_DOWNLOAD = "DOWNLOAD";
    public static final String COMMAND_UPLOAD = "UPLOAD";
    public static final String COMMAND_END = "END";
}
