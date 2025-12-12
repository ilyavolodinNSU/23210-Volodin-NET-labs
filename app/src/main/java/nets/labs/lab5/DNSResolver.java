package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import org.xbill.DNS.ARecord;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DNSResolver implements Handler {
    private static final Logger logger = LogManager.getLogger(DNSResolver.class);
    
    private DatagramChannel channel;
    private static final List<InetSocketAddress> DNS_SERVERS = List.of(
            new InetSocketAddress("1.1.1.1", 53),
            new InetSocketAddress("8.8.8.8", 53),
            new InetSocketAddress("9.9.9.9", 53)
    );
    private static final InetSocketAddress DNSServer = DNS_SERVERS.get(0);
    private final Map<Integer, DNSObjects> DNSServerAnswer = new HashMap<>();
    private final int BUFSIZE = 512;
    private final Selector selector;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);

    public DNSResolver(Selector selector) throws IOException {
        this.selector = selector;
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(DNSServer);
        channel.register(selector, SelectionKey.OP_READ, this);
        logger.info("DNS резолвер инициализирован, используется сервер: {}", DNSServer);
    }

    @Override
    public void handle(SelectionKey key) throws Exception {
        read();
    }

    public boolean resolve(String domain, int port, ConnectionHandler connect, SelectionKey clientKey) {
        if (domain == null || domain.isEmpty() || domain.length() > 253) {
            logger.error("Неверная длина домена или null: '{}'", domain);
            return false;
        }
        
        try {
            Name name;
            if (domain.endsWith(".")) name = Name.fromString(domain);
            else name = Name.fromString(domain + ".");
            
            Record record = Record.newRecord(name, Type.A, DClass.IN);
            Message query = Message.newQuery(record);
            int id = query.getHeader().getID();
            DNSObjects data = new DNSObjects(connect, clientKey, port);
            
            logger.info("Отправка DNS запроса для домена: {} (ID: {})", domain, id);
            channel.write(ByteBuffer.wrap(query.toWire()));
            
            DNSServerAnswer.put(id, data);
            logger.debug("Добавлен DNS запрос в очередь ожидания, ID: {}", id);
            return true;
            
        } catch (TextParseException e) {
            logger.error("Неверное имя домена: '{}'", domain, e);
            return false;
        } catch (IOException e) {
            logger.error("Не удалось отправить DNS запрос для '{}'", domain, e);
            return false;
        } catch (Exception e) {
            logger.error("Неожиданная DNS ошибка для '{}'", domain, e);
            return false;
        }
    }

    public void read() {
        buffer.clear();
        try {
            long bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                logger.warn("DNS канал закрыт удаленным хостом (EOF). Переподключение...");
                cleanupAndReconnect();
                return;
            }
            if (bytesRead == 0) {
                return;
            }
            
            buffer.flip();
            Message response = new Message(buffer);
            int id = response.getHeader().getID();
            
            logger.debug("Получен DNS ответ, ID: {}, размер: {} байт", id, bytesRead);
            
            DNSObjects data = DNSServerAnswer.remove(id);
            if (data == null) {
                logger.warn("DNS ответ с неизвестным ID: {}", id);
                return;
            }
            
            InetAddress resolvedIp = null;
            for (Record rec : response.getSection(Section.ANSWER)) {
                if (rec.getType() == Type.A) {
                    resolvedIp = ((ARecord) rec).getAddress();
                    logger.info("DNS резолвинг успешен: {} -> {}", 
                        rec.getName(), resolvedIp.getHostAddress());
                    break;
                }
            }
            
            if (resolvedIp != null) {
                logger.debug("Продолжение подключения с IP адресом: {}", resolvedIp.getHostAddress());
                data.connect().continueWithIp(resolvedIp, data.port(), data.key());
            } else {
                logger.error("DNS резолвинг не удался (нет A записи) для домена, запрошенного клиентом");
                try {
                    data.connect().sendError();
                } catch (Exception e) {
                    logger.error("Не удалось отправить ошибку клиенту после DNS сбоя", e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Ошибка чтения DNS", e);
        }
    }

    private void cleanupAndReconnect() {
        logger.warn("Очистка и переподключение DNS резолвера");
        
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
                logger.debug("DNS канал закрыт");
            } catch (Exception e) {
                logger.error("Ошибка при закрытии DNS канала", e);
            }
        }
        
        for (DNSObjects data : DNSServerAnswer.values()) {
            try {
                data.connect().sendError();
                logger.debug("Отправлена ошибка клиенту из-за сбоя DNS");
            } catch (Exception e) {
                logger.error("Ошибка при отправке ошибки клиенту", e);
            }
        }
        
        DNSServerAnswer.clear();
        logger.debug("Очередь DNS запросов очищена");
        
        try {
            channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.connect(DNSServer);
            channel.register(selector, SelectionKey.OP_READ, this);
            logger.info("DNS резолвер переподключен к {}", DNSServer);
        } catch (Exception e) {
            logger.error("Не удалось переподключить DNS канал", e);
        }
    }
}