package nets.labs.lab3.services;

import nets.labs.lab3.AppConfig;
import nets.labs.lab3.dto.Location;

import java.util.concurrent.CompletableFuture;

import static nets.labs.lab3.AppConstants.GEOCODING_QUERY_PARAMS;

public class GeocodingService {
    private final ApiClient apiClient;
    private final String apiKey;
    private final String baseUrl;
    private final String locale;
    
    public GeocodingService(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.apiKey = AppConfig.getGraphhopperApiKey();
        this.baseUrl = AppConfig.getGraphhopperBaseUrl();
        this.locale = AppConfig.getLocale();
    }
    
    public CompletableFuture<Location> searchLocations(String query) {
        String url = baseUrl + String.format(GEOCODING_QUERY_PARAMS, 
                java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8),
                locale, apiKey);
        return apiClient.getAsync(url, Location.class);
    }

    public String determineLocationType(Location.Hit location) {
        if (location.getName() != null) {
            String name = location.getName().toLowerCase();
            
            if (location.getState() != null && !location.getState().isEmpty()) {
                return "Administrative Region";
            }
            if (name.contains("город") || name.contains("city") || name.contains("town")) {
                return "City";
            }
            if (name.contains("улица") || name.contains("street") || name.contains("avenue")) {
                return "Street";
            }
            if (name.contains("район") || name.contains("district") || name.contains("region")) {
                return "District";
            }
            if (name.contains("деревня") || name.contains("village")) {
                return "Village";
            }
        }
        
        if (location.getCountry() != null && location.getState() != null) {
            return "Populated Place";
        }
        
        return "Location";
    }
}