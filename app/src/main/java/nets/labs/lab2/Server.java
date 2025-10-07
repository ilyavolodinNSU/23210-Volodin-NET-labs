package nets.labs.lab2;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;

@Data
class ClientHandler implements Runnable {
    private static final int timeout = 1000;
    private final Socket clientSocket;
    private volatile boolean isCompleted = false;
    private AtomicLong totalBytes = new AtomicLong(0);
    private long startTimestamp;
    private Thread speedThread;
    private String fileName;

    @Override
    public void run() {
        try {
            DataInputStream input = new DataInputStream(clientSocket.getInputStream());

            int fileNameLength = input.readInt();
            if (fileNameLength > 4096) throw new IllegalArgumentException("Filename length exceeds 4096 bytes");
            byte[] fileNameBytes = new byte[fileNameLength];
            input.readFully(fileNameBytes);
            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
            this.fileName = fileName; 

            long fileSize = input.readLong();

            this.startTimestamp = System.currentTimeMillis();
            this.totalBytes.set(0);

            speedThread = new Thread(() -> {
                long lastSpeedPrintTime = System.currentTimeMillis();
                long lastSpeedPrintBytes = 0;

                while (!isCompleted) {
                    if (isCompleted) break;

                    long currentTime = System.currentTimeMillis();
                    long currentTotal = totalBytes.get();
                    long elapsed = currentTime - lastSpeedPrintTime;
                    long bytesSinceLast = currentTotal - lastSpeedPrintBytes;

                    double instantaneousMBps = (elapsed == 0) ? 0.0 : 
                        (bytesSinceLast / (elapsed / 1000.0)) / 1_048_576.0;
                    double averageMBps = (currentTime - startTimestamp) == 0 ? 0.0 : 
                        (currentTotal / ((currentTime - startTimestamp) / 1000.0)) / 1_048_576.0;

                    System.out.printf("File '%s': instantaneous %.2f MB/s, average %.2f MB/s%n",
                    fileName, instantaneousMBps, averageMBps);

                    lastSpeedPrintTime = currentTime;
                    lastSpeedPrintBytes = currentTotal;

                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            speedThread.setDaemon(true);
            speedThread.start();

            File uploadsDir = new File("uploads");
            uploadsDir.mkdirs();
            File targetFile = new File(uploadsDir, fileName);
            String canonicalPath = targetFile.getCanonicalPath();
            String uploadsCanonicalPath = uploadsDir.getCanonicalPath();
            if (!canonicalPath.startsWith(uploadsCanonicalPath)) {
                throw new SecurityException("Invalid filename");
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                long remaining = fileSize;

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int bytesRead = input.read(buffer, 0, toRead);
                    if (bytesRead == -1) throw new IOException("Unexpected end of stream before receiving all data");
                    fos.write(buffer, 0, bytesRead);
                    totalBytes.addAndGet(bytesRead);
                    remaining -= bytesRead;
                }
            }

            if (totalBytes.get() != fileSize) throw new IOException("File size mismatch: expected " + fileSize + ", got " + totalBytes.get());

            clientSocket.getOutputStream().write(1);
            isCompleted = true;

        } catch (Exception e) {
            System.err.println("Error handling client " + clientSocket.getInetAddress() + " for file '" + fileName + "': " + e.getMessage());
            try {
                clientSocket.getOutputStream().write(0);
            } catch (IOException ex) {
            }
            isCompleted = true;
        } finally {
            if (speedThread != null && speedThread.isAlive()) {
                speedThread.interrupt();
                try {
                    speedThread.join(1000);
                } catch (InterruptedException ignored) {}
            }

            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}

public class Server {
    private ServerSocket serverSocket;
    private Thread serverThread;

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        serverThread = new Thread(() -> {
            try {
                System.out.println("Server started on port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        });
        serverThread.start();
    }

    public void stop() throws IOException {
        serverSocket.close();
        serverThread.interrupt();
    }
}