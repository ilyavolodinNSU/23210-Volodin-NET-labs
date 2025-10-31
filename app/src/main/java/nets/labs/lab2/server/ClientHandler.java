package nets.labs.lab2.server;

import lombok.Data;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

@Data
class ClientHandler implements Runnable {
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

            // Read and validate file name
            int fileNameLength = input.readInt();
            FileValidator.validateFileNameLength(fileNameLength);
            
            byte[] fileNameBytes = new byte[fileNameLength];
            input.readFully(fileNameBytes);
            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
            this.fileName = fileName;

            long fileSize = input.readLong();
            this.startTimestamp = System.currentTimeMillis();
            this.totalBytes.set(0);

            // Start speed monitoring
            speedThread = new Thread(new SpeedMonitor(totalBytes, isCompleted, startTimestamp, fileName));
            speedThread.setDaemon(true);
            speedThread.start();

            // Prepare file for writing
            File uploadsDir = new File(Constants.UPLOADS_DIR);
            uploadsDir.mkdirs();
            File targetFile = new File(uploadsDir, fileName);
            FileValidator.validateFileName(uploadsDir, targetFile, fileName);

            // Write file data
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[Constants.BUFFER_SIZE];
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

            FileValidator.validateFileSize(fileSize, totalBytes.get());

            clientSocket.getOutputStream().write(Constants.RESPONSE_SUCCESS);
            isCompleted = true;

        } catch (Exception e) {
            System.err.println("Error handling client " + clientSocket.getInetAddress() + " for file '" + fileName + "': " + e.getMessage());
            try {
                clientSocket.getOutputStream().write(Constants.RESPONSE_FAILURE);
            } catch (IOException ex) {
                // Ignore secondary exception
            }
            isCompleted = true;
        } finally {
            if (speedThread != null && speedThread.isAlive()) {
                speedThread.interrupt();
                try {
                    speedThread.join(Constants.JOIN_TIMEOUT_MS);
                } catch (InterruptedException ignored) {}
            }

            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}