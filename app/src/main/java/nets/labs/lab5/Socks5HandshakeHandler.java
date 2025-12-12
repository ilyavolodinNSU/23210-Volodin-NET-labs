package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

@RequiredArgsConstructor
public class Socks5HandshakeHandler implements Handler {
    private final SocketChannel client;
    private final DNSResolver resolver;
    private final CredentialValidator validator;

    private static final Logger logger = LogManager.getLogger(Socks5HandshakeHandler.class);
    
    private final int BUFSIZE = 512;
    private ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
    private final byte VERSION = 0x05;
    private final byte NO_AUTH = 0x00;
    private final byte USERNAME_PASSWORD = 0x02;
    private static final byte NO_ACCEPTABLE_METHODS = (byte) 0xFF;
    
    // Изменен ответ на рукопожатие - теперь только USERNAME_PASSWORD метод
    private final byte[] SUCCESS_RESPONSE = {VERSION, USERNAME_PASSWORD};

    @Override
    public void handle(SelectionKey key) throws Exception {
        doHandshake(key);
    }

    public void doHandshake(SelectionKey key) throws IOException {
        int read = client.read(buffer);
        if (read == -1) {
            logger.debug("Клиент закрыл соединение (EOF)");
            key.cancel();
            client.close();
            return;
        }
        if (read == 0) {
            logger.trace("Прочитано 0 байт, ожидание данных");
            return;
        }
        
        logger.debug("Прочитано {} байт при рукопожатии", read);
        
        buffer.flip();
        if (buffer.remaining() < 3) {
            logger.debug("Недостаточно данных для рукопожатия, ожидание");
            buffer.compact();
            return;
        }
        
        byte version = buffer.get();
        if (version != VERSION) {
            logger.warn("Неверная версия SOCKS: {}", version);
            sendReplyAndClose(key, NO_ACCEPTABLE_METHODS);
            return;
        }
        
        int nmethods = buffer.get() & 0xFF; // Беззнаковое чтение
        if (buffer.remaining() < nmethods) {
            logger.debug("Недостаточно методов аутентификации, ожидание");
            buffer.compact();
            return;
        }

        boolean supportsUsernamePassword = false;
        int methodsRead = 0;
        while (methodsRead < nmethods) {
            byte method = buffer.get();
            if (method == USERNAME_PASSWORD) {
                supportsUsernamePassword = true;
                logger.debug("Клиент поддерживает USERNAME/PASSWORD аутентификацию");
            }
            methodsRead++;
        }

        if (!supportsUsernamePassword) {
            logger.warn("Клиент не поддерживает USERNAME/PASSWORD аутентификацию");
            sendReplyAndClose(key, NO_ACCEPTABLE_METHODS);
            return;
        }

        logger.debug("Запрошена USERNAME/PASSWORD аутентификация");
        
        // Отправляем клиенту, что выбрали USERNAME/PASSWORD метод
        if (client.write(ByteBuffer.wrap(SUCCESS_RESPONSE)) <= 0) {
            logger.warn("Не удалось отправить ответ на рукопожатие");
            closeConnection(key);
            return;
        }

        // Переключаемся на обработчик аутентификации
        key.attach(new Socks5AuthHandler(client, validator, resolver));
        logger.debug("Переход к этапу аутентификации");
        buffer.clear();
    }

    private void sendReplyAndClose(SelectionKey key, byte method) {
        try {
            logger.debug("Отправка отказа на рукопожатие: метод {}", method);
            client.write(ByteBuffer.wrap(new byte[]{VERSION, method}));
        } catch (Exception e) {
            logger.error("Ошибка при отправке отказа на рукопожатие", e);
        }
        closeConnection(key);
    }

    private void closeConnection(SelectionKey key) {
        key.cancel();
        try {
            client.close();
            logger.debug("Соединение с клиентом закрыто");
        } catch (IOException e) {
            logger.error("Ошибка при закрытии соединения", e);
        }
    }
}