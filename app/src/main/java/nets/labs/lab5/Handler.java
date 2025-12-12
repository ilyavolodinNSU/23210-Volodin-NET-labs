package nets.labs.lab5;

import java.nio.channels.SelectionKey;

public interface Handler {
    void handle(SelectionKey key) throws Exception;
}
