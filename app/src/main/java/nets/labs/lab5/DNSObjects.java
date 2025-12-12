package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.channels.SelectionKey;

public record DNSObjects(
        ConnectionHandler connect,
        SelectionKey key,
        int port
) {
    private static final Logger logger = LogManager.getLogger(DNSObjects.class);
    
    @Override
    public String toString() {
        return "DNSObjects[port=" + port + "]";
    }
}