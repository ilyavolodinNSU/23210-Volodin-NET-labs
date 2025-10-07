package nets.labs.lab1;

public class MCastApp {
    public static void main(String[] args) {
        try {
            MCast mcast = MCast.builder()
                    .mcastAddrStr(args[0])
                    .port(Integer.parseInt(args[1]))
                    .interfaceName(args[2])
                    .build();
            mcast.start();
        } catch (Exception e) {
            System.err.println("Ошибка запуска: " + e.getMessage());
        }
    }
}
