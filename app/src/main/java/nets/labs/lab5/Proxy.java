package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

@RequiredArgsConstructor
public class Proxy implements Handler {
    private static final Logger logger = LogManager.getLogger(Proxy.class);
    
    private final SocketChannel client;
    private final SocketChannel remote;

    @Override
    public void handle(SelectionKey key) throws Exception {
        transfer(key);
    }

    public void transfer(SelectionKey key) throws IOException {
        SocketChannel from = (SocketChannel) key.channel();
        SocketChannel to;
        
        // куда читать а куда писать
        if (from == client) {
            to = remote;
            logger.trace("Передача данных от клиента к серверу");
        } else {
            to = client;
            logger.trace("Передача данных от сервера к клиенту");
        }
        
        ByteBuffer buffer = Socks5Server.TRANSFER_BUFFER.get();
        if (!buffer.hasRemaining()) {
            buffer.clear();
        }
        
        int read = from.read(buffer);
        if (read == -1) {
            logger.info("Соединение закрыто, разрыв туннеля");
            client.close();
            remote.close();
            return;
        }
        
        if (read > 0) {
            logger.debug("Передано {} байт", read);
            buffer.flip();
            int written = to.write(buffer); // пишем в противоположный канал
            logger.trace("Записано {} байт", written);
            
            if (buffer.hasRemaining()) {
                SelectionKey toKey = to.keyFor(key.selector());
                toKey.interestOps(toKey.interestOps() | SelectionKey.OP_WRITE);
                logger.debug("Буфер не пуст, добавлен флаг OP_WRITE");
            } else {
                buffer.clear();
            }
        }
    }
}