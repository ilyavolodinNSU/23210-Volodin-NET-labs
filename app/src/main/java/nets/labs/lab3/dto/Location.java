package nets.labs.lab3.dto;

import lombok.Data;
import java.util.List;

@Data
public class Location {
    private List<Hit> hits;
    
    @Data
    public static class Hit {
        private Point point;
        private String name;
        private String country;
        private String countrycode;
        private String state;
    }
    
    @Data
    public static class Point {
        private double lat;
        private double lng;
    }
}
