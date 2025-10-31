package nets.labs.lab2.server;

public final class Constants {
    public static final int SPEED_MONITOR_INTERVAL_MS = 1000;
    public static final int MAX_FILENAME_LENGTH = 4096;
    public static final int BUFFER_SIZE = 8192;
    public static final String UPLOADS_DIR = "uploads";
    public static final int JOIN_TIMEOUT_MS = 1000;
    public static final double BYTES_TO_MEGABYTES_DIVISOR = 1024 * 1024;
    public static final byte RESPONSE_SUCCESS = 1;
    public static final byte RESPONSE_FAILURE = 0;
    
    private Constants() {}
}