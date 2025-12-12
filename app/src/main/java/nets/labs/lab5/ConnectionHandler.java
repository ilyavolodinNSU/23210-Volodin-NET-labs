package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SelectionKey;

/*
    парсим connect
    резолвим адрес
    асихр соед
    создаем прокси
*/

@RequiredArgsConstructor
public class ConnectionHandler implements Handler {
    private static final Logger logger = LogManager.getLogger(ConnectionHandler.class);
    
    private final SocketChannel client;
    private final Selector selector;
    private final DNSResolver resolver;

    private final int MAXSIZE = 512;
    private  ByteBuffer buffer = ByteBuffer.allocate(MAXSIZE);
    private SocketChannel remote = null;
    private final int MASK = 0xFF;
    private final byte CON = 0x01;
    private final byte IPV4 = 0x01;
    private final byte IPV6 = 0x04;
    private final byte DNS = 0x03;
    private final byte VER = 0x05;
    private final byte REP_GENERAL_FAILURE = 0x01;
    private final byte REP = 0x00;
    private final byte RSV = 0x00;
    private final byte BINDADDR = 0x00;
    private final byte BINDPORT = 0x00;
    private final int IPV4SIZE = 4;
    private final int IPV6SIZE = 16;

    @Override
    public void handle(SelectionKey key) throws Exception {
        connect(key);
    }

    public void connect(SelectionKey key) throws IOException {
        int read = client.read(buffer);
        if (read == -1) {
            logger.info("Клиент {} закрыл соединение (EOF)", client.getRemoteAddress());
            client.close();
            return;
        }
        if (read == 0) {
            logger.trace("Прочитано 0 байт от клиента");
            return;
        }
        
        logger.debug("Прочитано {} байт запроса CONNECT", read);
        
        if (buffer.position() > MAXSIZE) {
            logger.error("Клиент {} превысил максимальный размер запроса ({} > {})", 
                client.getRemoteAddress(), buffer.position(), MAXSIZE);
            sendError();
            return;
        }
        
        buffer.flip();
        if (buffer.remaining() < 10) {
            logger.debug("Недостаточно данных для запроса CONNECT, ожидание");
            buffer.compact();
            return;
        }
        
        byte version = buffer.get();
        if (version != VER) {
            logger.error("Неверная версия SOCKS: {}", version);
            client.close();
            return;
        }
        
        byte command = buffer.get();
        if (command != CON) {
            logger.error("Неверная команда: {}", command);
            client.close();
            return;
        }
        
        buffer.get(); // пропускаем rsv
        
        byte address = buffer.get();
        String destAddress = "";
        int destPort = 0;
        
        switch (address) {
            case IPV4:
                byte[] ipv4 = new byte[IPV4SIZE];
                buffer.get(ipv4);
                destAddress = InetAddress.getByAddress(ipv4).getHostAddress();
                logger.info("IPv4 адрес назначения: {}", destAddress);
                break;
            case IPV6:
                byte[] ipv6 = new byte[IPV6SIZE];
                buffer.get(ipv6);
                destAddress = InetAddress.getByAddress(ipv6).getHostAddress();
                logger.info("IPv6 адрес назначения: {}", destAddress);
                break;
            case DNS:
                if (buffer.remaining() < 1) {
                    buffer.position(buffer.position() - 4);
                    buffer.compact();
                    return;
                }
                byte len = buffer.get();
                if (len == 0 || len > 255) {
                    logger.error("Неверная длина домена: {}", len);
                    sendError();
                    return;
                }
                byte[] bytes = new byte[len];
                buffer.get(bytes);
                String domain = new String(bytes, StandardCharsets.US_ASCII).toLowerCase();
                int domainPort = (buffer.get() & MASK) << 8 | (buffer.get() & MASK); // порт 2 байта
                logger.info("DNS запрос для домена: {}:{}", domain, domainPort);
                if (!resolver.resolve(domain, domainPort, this, key)) {
                    sendError();
                    logger.error("Не удалось отправить DNS запрос для: {}", domain);
                    buffer.clear();
                    return;
                }
                buffer.clear();
                return;
            default:
                logger.error("Неподдерживаемый тип адреса: {}", address);
                client.close();
                return;
        }
        
        byte firstByte = buffer.get();
        byte secondByte = buffer.get();
        destPort = ((firstByte & MASK) << 8) | (secondByte & MASK);
        if (destPort == 0) {
            logger.error("Неверный порт: 0 (клиент: {})", client.getRemoteAddress());
            sendError();
            return;
        }
        
        logger.info("Подключение к назначению: {}:{}", destAddress, destPort);
        
        remote = SocketChannel.open();
        remote.configureBlocking(false);
        remote.connect(new InetSocketAddress(destAddress, destPort));
        
        boolean connectedImmediately = !remote.isConnectionPending();
        if (connectedImmediately) {
            logger.debug("Немедленное подключение к {}", destAddress);
            finishConnect(remote.register(selector, 0, this));
        } else {
            logger.debug("Ожидание подключения к {}", destAddress);
            remote.register(selector, SelectionKey.OP_CONNECT, this);
        }
        
        key.interestOps(SelectionKey.OP_READ);
        buffer.clear();
    }

    public void sendError() throws IOException {
        logger.debug("Отправка ошибки клиенту");
        byte[] errorResponse = {VER, REP_GENERAL_FAILURE, RSV, IPV4, BINDADDR, BINDADDR, BINDADDR, BINDADDR, BINDPORT, BINDPORT};
        try {
            client.write(ByteBuffer.wrap(errorResponse));
        } catch (IOException e) {
            logger.error("Ошибка при отправке ошибки клиенту", e);
        }
        client.close();
    }

    public void finishConnect(SelectionKey remoteKey) throws IOException {
        try {
            if (!remote.finishConnect()) {
                logger.error("finishConnect() вернул false (не должно происходить)");
                sendError();
                return;
            }
        } catch (Exception e) {
            logger.error("Не удалось подключиться к {}:{} - {}", 
                remote.socket().getInetAddress(), remote.socket().getPort(), e.getMessage());
            sendError();
            return;
        }

        byte[] response = {VER, REP, RSV, IPV4, BINDADDR, BINDADDR, BINDADDR, BINDADDR, BINDPORT, BINDPORT};
        client.write(ByteBuffer.wrap(response));
        
        logger.info("Успешное подключение к удаленному серверу");

        // меняем хендлер на прокси
        Proxy proxy = new Proxy(client, remote);
        client.register(selector, SelectionKey.OP_READ, proxy);
        remote.register(selector, SelectionKey.OP_READ, proxy);

        logger.info("Туннель установлен между клиентом и {}:{}", 
            remote.socket().getInetAddress(), remote.socket().getPort());
    }

    // работает с айпи от днс резолвера
    public void continueWithIp(InetAddress ip, int port, SelectionKey clientKey) throws IOException {
        logger.info("Продолжение подключения с IP адресом: {}:{}", ip.getHostAddress(), port);
        this.remote = SocketChannel.open();
        this.remote.configureBlocking(false);
        this.remote.connect(new InetSocketAddress(ip, port));
        this.remote.register(selector, SelectionKey.OP_CONNECT, this);
    }
}