package nets.labs.lab1;

import lombok.Builder;
import lombok.Getter;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MCast {
    private static final int BUFFER_SIZE = 1024;
    private static final int HEARTBEAT_INTERVAL_MS = 1000;
    private static final int PEER_TIMEOUT_MS = 3000;
    private static final byte[] HEARTBEAT_MSG = new byte[]{1};

    private static final Map<String, Long> peers = new ConcurrentHashMap<>();
    private static final Object printLock = new Object();
    private static volatile boolean running = true;

    private InetAddress mcastAddr = InetAddress.getByName("224.1.1.3");;
    private NetworkInterface netIf = NetworkInterface.getByName("wlan0");
    private int PORT = 5000;
    private MulticastSocket socket;

    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";
    private static final String RESET = "\033[0m";
    private static final String INFO = "ℹ️  ";
    private static final String JOIN = "✅";
    private static final String LEAVE = "❌";
    private static final String ERROR = "💥";
    private static final String EXIT = "🚪";

    @Builder
    public MCast(String mcastAddrStr, int port, String interfaceName) throws IOException {
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("Неверный порт: " + port);
        this.PORT = port;

        try {
            this.mcastAddr = InetAddress.getByName(mcastAddrStr);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Неверный multicast-адрес: " + mcastAddrStr, e);
        }

        this.netIf = NetworkInterface.getByName(interfaceName);
        if (this.netIf == null) throw new IOException("Интерфейс '" + interfaceName + "' не найден.");
        if (!this.netIf.supportsMulticast())
            throw new IOException("Интерфейс '" + interfaceName + "' не поддерживает мультикаст.");
    }

    public void start() {
        try {
            socket = new MulticastSocket(PORT);
            socket.joinGroup(new InetSocketAddress(mcastAddr, 0), netIf);
            socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);

            System.out.println(GREEN + JOIN + " Подключён к multicast группе: "
                    + mcastAddr.getHostAddress() + " через '" + netIf.getName() + "'" + RESET);

            InetSocketAddress group = new InetSocketAddress(mcastAddr, PORT);
            DatagramPacket pkgSend = new DatagramPacket(HEARTBEAT_MSG, HEARTBEAT_MSG.length, group);
            DatagramPacket pkgRecv = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

            Thread receiver = new Thread(() -> {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        socket.receive(pkgRecv);
                        String senderAddr = pkgRecv.getAddress().getHostAddress();

                        boolean isNew = peers.containsKey(senderAddr);
                        peers.put(senderAddr, System.currentTimeMillis());

                        if (!isNew) {
                            synchronized (printLock) {
                                System.out.println(GREEN + JOIN + " Новый узел: " + senderAddr + RESET);
                                printStatus();
                            }
                        }
                    } catch (IOException e) {
                        if (running) {
                            System.err.println(RED + ERROR + " Ошибка при приёме: " + e.getMessage() + RESET);
                        }
                    }
                }
            });
            receiver.setDaemon(true);
            receiver.start();

            Thread inputThread = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                System.out.println(YELLOW + "Введите 'exit' для выхода:" + RESET);
                while (running) {
                    if (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim().toLowerCase();
                        if (line.equals("exit") || line.equals("quit")) {
                            handleExit();
                        } else {
                            System.out.println(YELLOW + "❗ Неизвестная команда. Введите 'exit'." + RESET);
                        }
                    }
                }
                scanner.close();
            });
            inputThread.start();

            while (running) { // heartbeat
                try {
                    socket.send(pkgSend);
                    long now = System.currentTimeMillis();
                    peers.forEach((addr, lastTime) -> {
                        if (now - lastTime > PEER_TIMEOUT_MS) {
                            peers.remove(addr);
                            synchronized (printLock) {
                                System.out.println(RED + LEAVE + " Таймаут узла: " + addr + RESET);
                                printStatus();
                            }
                        }
                    });
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                } catch (IOException e) {
                    if (running) {
                        System.err.println(RED + ERROR + " Ошибка отправки: " + e.getMessage() + RESET);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!socket.isClosed()) socket.close();

        } catch (IOException e) {
            System.err.println(RED + ERROR + " Не удалось подключиться: " + e.getMessage() + RESET);
        }
    }

    private static void handleExit() {
        System.out.println("\n" + YELLOW + EXIT + " Завершение работы..." + RESET);
        running = false;
        System.exit(0);
    }

    private static void printStatus() {
        synchronized (printLock) {
            System.out.println("\n" + BLUE + INFO + " Активные узлы:" + RESET);
            if (peers.isEmpty()) {
                System.out.println("  📭 Никто не подключён.");
            } else {
                peers.keySet().forEach(addr -> System.out.println("  🌐 " + addr));
            }
            System.out.println();
        }
    }
}
