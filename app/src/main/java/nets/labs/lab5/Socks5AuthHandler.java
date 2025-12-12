package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class Socks5AuthHandler implements Handler {
    private final SocketChannel client;
    private final CredentialValidator validator;
    private final DNSResolver resolver;

    private static final Logger logger = LogManager.getLogger(Socks5AuthHandler.class);
    
    private final int BUFSIZE = 512;
    private ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
    private final byte VERSION = 0x01; // Версия подпротокола аутентификации
    private final byte SUCCESS = 0x00;
    private final byte FAILURE = 0x01;

    @Override
    public void handle(SelectionKey key) throws Exception {
        doAuthentication(key);
    }

    public void doAuthentication(SelectionKey key) throws IOException {
        int read = client.read(buffer);
        if (read == -1) {
            logger.debug("Клиент закрыл соединение во время аутентификации");
            key.cancel();
            client.close();
            return;
        }
        if (read == 0) {
            logger.trace("Прочитано 0 байт при аутентификации, ожидание");
            return;
        }
        
        logger.debug("Прочитано {} байт аутентификационных данных", read);
        
        buffer.flip();
        if (buffer.remaining() < 3) {
            logger.debug("Недостаточно данных для аутентификации, ожидание");
            buffer.compact();
            return;
        }
        
        byte version = buffer.get();
        if (version != VERSION) {
            logger.warn("Неверная версия подпротокола аутентификации: {}", version);
            sendAuthFailure(key);
            return;
        }
        
        byte usernameLength = buffer.get();
        if (usernameLength <= 0 || buffer.remaining() < usernameLength + 1) {
            logger.debug("Недостаточно данных для имени пользователя, ожидание");
            buffer.compact();
            return;
        }
        
        byte[] usernameBytes = new byte[usernameLength];
        buffer.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.UTF_8);
        
        byte passwordLength = buffer.get();
        if (buffer.remaining() < passwordLength) {
            logger.debug("Недостаточно данных для пароля, ожидание");
            // Нужно восстановить позицию буфера
            buffer.position(buffer.position() - usernameLength - 2);
            buffer.compact();
            return;
        }
        
        byte[] passwordBytes = new byte[passwordLength];
        buffer.get(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.UTF_8);
        
        logger.info("Аутентификация пользователя: '{}'", username);
        
        boolean authenticated = validator.validate(username, password);
        
        if (authenticated) {
            logger.info("Пользователь '{}' успешно аутентифицирован", username);
            sendAuthSuccess(key);
            // Переходим к этапу соединения
            key.attach(new ConnectionHandler(client, key.selector(), resolver));
            logger.debug("Переход к этапу соединения");
        } else {
            logger.warn("Неудачная аутентификация для пользователя '{}'", username);
            sendAuthFailure(key);
        }
        
        buffer.clear();
    }

    private void sendAuthSuccess(SelectionKey key) throws IOException {
        byte[] response = {VERSION, SUCCESS};
        client.write(ByteBuffer.wrap(response));
        logger.debug("Отправлен успешный ответ аутентификации");
    }

    private void sendAuthFailure(SelectionKey key) throws IOException {
        try {
            byte[] response = {VERSION, FAILURE};
            client.write(ByteBuffer.wrap(response));
            logger.debug("Отправлен отказ аутентификации");
        } catch (Exception e) {
            logger.error("Ошибка при отправке отказа аутентификации", e);
        }
        closeConnection(key);
    }

    private void closeConnection(SelectionKey key) {
        key.cancel();
        try {
            client.close();
            logger.debug("Соединение с клиентом закрыто после неудачной аутентификации");
        } catch (IOException e) {
            logger.error("Ошибка при закрытии соединения", e);
        }
    }
}