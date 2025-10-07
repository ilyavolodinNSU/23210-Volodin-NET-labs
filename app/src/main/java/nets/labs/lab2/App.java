package nets.labs.lab2;

public class App {
    public static void main(String[] args) {
        try {
            Server server = new Server();
            server.start(8080);
            Client.sendFile("app/src/main/resources/500mb_file.bin", "localhost", 8080);
            server.stop();
        } catch (Exception e) {
            System.err.println("Ошибка запуска: " + e.getMessage());
        }
    }
}
