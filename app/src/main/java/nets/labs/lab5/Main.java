package nets.labs.lab5;

import java.io.IOException;

import static java.lang.Integer.parseInt;

public class Main {
    public static void main(String[] args) {
        try {
            new Socks5Server(1080).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}