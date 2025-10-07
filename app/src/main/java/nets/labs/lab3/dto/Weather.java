package nets.labs.lab3.dto;

import lombok.Data;
import java.util.List;

@Data
public class Weather {
    private Coord coord;
    private List<WeatherInfo> weather;
    private Main main;
    private Wind wind;
    private String name;
    
    @Data
    public static class Coord {
        private double lon;
        private double lat;
    }
    
    @Data
    public static class WeatherInfo {
        private String main;
        private String description;
    }
    
    @Data
    public static class Main {
        private double temp;
        private double feels_like;
        private double temp_min;
        private double temp_max;
        private int humidity;
        private int pressure;
    }
    
    @Data
    public static class Wind {
        private double speed;
    }
}