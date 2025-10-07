package nets.labs.lab3.dto;

import lombok.Data;

@Data
public class SimplePlace {
    private String xid;
    private String name;
    private String kinds;
    private Point point;
    
    @Data
    public static class Point {
        private double lon;
        private double lat;
    }
}
