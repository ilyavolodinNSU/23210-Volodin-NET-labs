package nets.labs.lab3.dto;

import lombok.Data;
import java.util.List;

@Data
public class CompositeResult {
    private Location.Hit selectedLocation;
    private Weather weather;
    private List<PlaceWithDetails> places;
    private String locationType;
    
    @Data
    public static class PlaceWithDetails {
        private SimplePlace place;
        private PlaceDetails details;
        
        public String getPlaceType() {
            if (details != null && details.getKinds() != null) {
                return extractMainCategory(details.getKinds());
            } else if (place != null && place.getKinds() != null) {
                return extractMainCategory(place.getKinds());
            }
            return "Unknown";
        }
        
        private String extractMainCategory(String kinds) {
            if (kinds == null || kinds.isEmpty()) {
                return "Unknown";
            }
            String[] categories = kinds.split(",");
            return categories.length > 0 ? categories[0].trim() : "Unknown";
        }
    }
}