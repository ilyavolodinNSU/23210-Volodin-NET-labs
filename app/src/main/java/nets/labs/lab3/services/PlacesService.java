package nets.labs.lab3.services;

import nets.labs.lab3.AppConfig;
import nets.labs.lab3.dto.CompositeResult;
import nets.labs.lab3.dto.PlaceDetails;
import nets.labs.lab3.dto.SimplePlace;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static nets.labs.lab3.AppConstants.*;

public class PlacesService {
    private final ApiClient apiClient;
    private final String apiKey;
    private final String baseUrl;
    private final String locale;
    private final int defaultRadius;
    private final int defaultLimit;
    
    public PlacesService(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.apiKey = AppConfig.getOpentripmapApiKey();
        this.baseUrl = AppConfig.getOpentripmapBaseUrl();
        this.locale = AppConfig.getLocale();
        this.defaultRadius = AppConfig.getOpentripmapDefaultRadius();
        this.defaultLimit = AppConfig.getOpentripmapDefaultLimit();
    }
    
    public CompletableFuture<List<SimplePlace>> getPlaces(double lat, double lon, int radius) {
        String url = baseUrl + String.format(Locale.US, PLACES_RADIUS_PARAMS, 
                locale, radius, lon, lat, defaultLimit, apiKey);
        return apiClient.getAsync(url, SimplePlace[].class)
                .thenApply(array -> List.of(array));
    }
    
    public CompletableFuture<PlaceDetails> getPlaceDetails(String xid) {
        String url = baseUrl + String.format(PLACES_DETAILS_PARAMS, locale, xid, apiKey);
        return apiClient.getAsync(url, PlaceDetails.class);
    }
    
    public CompletableFuture<List<CompositeResult.PlaceWithDetails>> getPlacesWithDetails(double lat, double lon, int radius) {
        return getPlaces(lat, lon, radius)
                .thenCompose(places -> {
                    if (places == null || places.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                    
                    List<CompletableFuture<CompositeResult.PlaceWithDetails>> futures = 
                        places.stream()
                            .map(place -> {
                                try {
                                    Thread.sleep(200); 
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return getPlaceDetails(place.getXid())
                                        .thenApply(details -> createPlaceWithDetails(place, details))
                                        .exceptionally(ex -> createPlaceWithErrorDetails(place, ex));
                            })
                            .collect(Collectors.toList());
                    
                    CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                    );
                    
                    return allFutures.thenApply(v -> 
                        futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList())
                    );
                });
    }
    
    private CompositeResult.PlaceWithDetails createPlaceWithDetails(SimplePlace place, PlaceDetails details) {
        CompositeResult.PlaceWithDetails placeWithDetails = new CompositeResult.PlaceWithDetails();
        placeWithDetails.setPlace(place);
        placeWithDetails.setDetails(details);
        return placeWithDetails;
    }
    
    private CompositeResult.PlaceWithDetails createPlaceWithErrorDetails(SimplePlace place, Throwable ex) {
        CompositeResult.PlaceWithDetails placeWithDetails = new CompositeResult.PlaceWithDetails();
        placeWithDetails.setPlace(place);
        PlaceDetails errorDetails = new PlaceDetails();
        errorDetails.setName(place.getName());
        errorDetails.setKinds(place.getKinds());
        placeWithDetails.setDetails(errorDetails);
        return placeWithDetails;
    }
    
    public int getDefaultRadius() {
        return defaultRadius;
    }
}