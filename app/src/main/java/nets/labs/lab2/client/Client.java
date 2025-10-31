package nets.labs.lab2.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Client {
    public static void sendFile(String filePath, String host, int port) throws IOException {
        File file = new File(filePath);

        System.out.println("Attempting to send file: \"" + filePath + "\"");
        System.out.println("Absolute path: " + file.getAbsolutePath());
        System.out.println("Exists: " + file.exists());
        System.out.println("Is file: " + file.isFile());
        System.out.println("Can read: " + file.canRead());

        if (!file.exists()) throw new FileNotFoundException("File does not exist: " + filePath);
        if (!file.isFile()) throw new FileNotFoundException("Path is not a regular file: " + filePath);
        if (!file.canRead()) throw new FileNotFoundException("File is not readable: " + filePath);

        try (Socket socket = new Socket(host, port);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            FileInputStream fis = new FileInputStream(file)) {

            String fileName = file.getName();
            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            
            output.writeInt(fileNameBytes.length);
            output.write(fileNameBytes);
            output.writeLong(file.length());

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();

            int response = socket.getInputStream().read();
            if (response == 1) System.out.println("File '" + fileName + "' transferred successfully");
            else if (response == 0) System.out.println("File '" + fileName + "' transfer failed (server reported error)");
            else if (response == -1) System.out.println("File '" + fileName + "' transfer failed: Server closed connection unexpectedly");
            else System.out.println("File '" + fileName + "' transfer failed: Invalid response from server: " + response);
        } catch (SocketException e) {
            if ("Connection reset".equals(e.getMessage())) {
                System.out.println("File transfer failed: Connection was reset by server");
            } else {
                throw e;
            }
        }
    }
}