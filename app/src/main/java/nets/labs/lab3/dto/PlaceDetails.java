package nets.labs.lab3.dto;

import lombok.Data;

@Data
public class PlaceDetails {
    private String xid;
    private String name;
    private String kinds;
    private String wikipedia;
    private String image;
    private Info info;
    
    @Data
    public static class Info {
        private String descr;
    }
}
