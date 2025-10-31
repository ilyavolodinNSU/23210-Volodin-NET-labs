package nets.labs.lab3.services;

import nets.labs.lab3.dto.SimplePlace;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class LocationTypeService {
    private final PlacesService placesService;
    
    public LocationTypeService(PlacesService placesService) {
        this.placesService = placesService;
    }
    
    public CompletableFuture<String> determineLocationType(double lat, double lon) {
        return placesService.getPlaces(lat, lon, 500)
                .thenApply(places -> {
                    if (places != null && !places.isEmpty()) {
                        SimplePlace closestPlace = places.get(0);
                        if (closestPlace.getKinds() != null) {
                            String[] categories = closestPlace.getKinds().split(",");
                            return categories[0].trim();
                        }
                    }
                    return "General Location";
                })
                .exceptionally(ex -> {
                    log.error("Error determining location type: {}", ex.getMessage());
                    return "Unknown";
                });
    }
}