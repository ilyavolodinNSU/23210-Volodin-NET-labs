package nets.labs.lab2.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

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