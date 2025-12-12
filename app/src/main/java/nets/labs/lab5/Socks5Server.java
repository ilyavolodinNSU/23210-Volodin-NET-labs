package nets.labs.lab5;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

@RequiredArgsConstructor
public class Socks5Server {
    private static final Logger logger = LogManager.getLogger(Socks5Server.class);
    
    private final int port;
    public static final ThreadLocal<ByteBuffer> TRANSFER_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(32768));
    private DNSResolver resolver;
    private CredentialValidator validator;
    private volatile boolean isRun = true;
    private Selector selector;

    public void start() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        serverSocketChannel.configureBlocking(false);
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        resolver = new DNSResolver(selector);
        
        // Инициализируем валидатор (можно заменить на другой)
        validator = new SimpleCredentialValidator();
        
        logger.info("Сервер запущен и слушает на порту {}", port);
        logger.info("Используется аутентификация USERNAME/PASSWORD");
        
        while (isRun) {
            int readyChannels = selector.select();

            if (!isRun) break;
            if (readyChannels == 0) continue;
            
            // обрабатываем события
            for (SelectionKey key : selector.selectedKeys()) {
                try {
                    if (!key.isValid()) {
                        logger.debug("Ключ недействителен, пропускаем");
                        continue;
                    }
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        if (client == null) continue;
                        client.configureBlocking(false);
                        // Передаем валидатор в обработчик рукопожатия
                        client.register(selector, SelectionKey.OP_READ, 
                                    new Socks5HandshakeHandler(client, resolver, validator));
                        logger.info("Новый клиент подключился: {}", client.getRemoteAddress());
                    }
                    if (key.isConnectable()) {
                        logger.debug("Обработка события подключения");
                        ((ConnectionHandler) key.attachment()).finishConnect(key);
                    }
                    if (key.isReadable()) {
                        Object att = key.attachment();
                        if (att instanceof Handler handler) {
                            handler.handle(key);
                            continue;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при обработке события селектора", e);
                    try {
                        key.channel().close();
                    } catch (Exception ignored) {
                        logger.debug("Ошибка при закрытии канала, игнорируем");
                    }
                    key.cancel();
                }
            }
            selector.selectedKeys().clear();
        }
        cleanup();
    }

    public void stop() {
        logger.info("Остановка сервера...");
        isRun = false;
        if (selector != null) {
            selector.wakeup();
        }
    }

    private void cleanup() {
        logger.info("Очистка ресурсов...");
        if (selector != null) {
            try {
                for (SelectionKey key : selector.keys()) {
                    if (key.channel() != null) {
                        key.channel().close();
                    }
                    key.cancel();
                }
                selector.close();
                logger.info("Селектор успешно закрыт");
            } catch (Exception e) {
                logger.error("Ошибка при очистке ресурсов", e);
            }
        }
    }
}