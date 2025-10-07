package nets.labs.lab3;

import lombok.extern.slf4j.Slf4j;
import nets.labs.lab3.AppConfig;
import nets.labs.lab3.dto.*;
import nets.labs.lab3.services.*;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static nets.labs.lab3.AppConstants.*;

@Slf4j
public class LocationSearchApp {
    private final GeocodingService geocodingService;
    private final WeatherService weatherService;
    private final PlacesService placesService;
    private final LocationTypeService locationTypeService;
    private final Scanner scanner;
    private final int defaultRadius;
    
    public LocationSearchApp() {
        ApiClient apiClient = new ApiClient();
        this.geocodingService = new GeocodingService(apiClient);
        this.weatherService = new WeatherService(apiClient);
        this.placesService = new PlacesService(apiClient);
        this.locationTypeService = new LocationTypeService(placesService);
        this.scanner = new Scanner(System.in);
        this.defaultRadius = AppConfig.getOpentripmapDefaultRadius();
    }
    
    public void start() {
        System.out.println("=== Location Search Application ===");
        
        System.out.print("Enter location name: ");
        String query = scanner.nextLine();
        
        searchLocations(query)
            .thenCompose(selectedLocation -> {
                CompletableFuture<String> locationTypeFuture = locationTypeService.determineLocationType(
                    selectedLocation.getPoint().getLat(), 
                    selectedLocation.getPoint().getLng()
                );
                
                CompletableFuture<Weather> weatherFuture = weatherService.getWeather(
                    selectedLocation.getPoint().getLat(), 
                    selectedLocation.getPoint().getLng()
                ).exceptionally(ex -> {
                    log.error("Weather service failed: {}", ex.getMessage());
                    Weather errorWeather = new Weather();
                    errorWeather.setName(WEATHER_UNAVAILABLE);
                    return errorWeather;
                });
                
                CompletableFuture<List<CompositeResult.PlaceWithDetails>> placesFuture = 
                    placesService.getPlacesWithDetails(
                        selectedLocation.getPoint().getLat(), 
                        selectedLocation.getPoint().getLng(), 
                        defaultRadius
                    ).exceptionally(ex -> {
                        log.error("Places service failed: {}", ex.getMessage());
                        return List.of();
                    });
                
                return locationTypeFuture.thenCompose(locationType -> 
                    weatherFuture.thenCompose(weather -> 
                        placesFuture.thenApply(places -> {
                            CompositeResult result = new CompositeResult();
                            result.setSelectedLocation(selectedLocation);
                            result.setWeather(weather);
                            result.setPlaces(places);
                            result.setLocationType(locationType);
                            return result;
                        })
                    )
                );
            })
            .thenAccept(this::displayResults)
            .exceptionally(ex -> {
                System.err.println("Application error: " + ex.getMessage());
                return null;
            })
            .join();
    }
    
    private CompletableFuture<Location.Hit> searchLocations(String query) {
        return geocodingService.searchLocations(query)
                .thenApply(location -> {
                    List<Location.Hit> hits = location.getHits();
                    if (hits.isEmpty()) {
                        throw new RuntimeException("No locations found");
                    }
                    
                    System.out.println("\nFound locations:");
                    for (int i = 0; i < hits.size(); i++) {
                        Location.Hit hit = hits.get(i);
                        System.out.printf("%d. %s, %s, %s (%.6f, %.6f)%n", 
                            i + 1, hit.getName(), hit.getState(), hit.getCountry(),
                            hit.getPoint().getLat(), hit.getPoint().getLng());
                    }
                    
                    System.out.print("Select location (1-" + hits.size() + "): ");
                    int choice = scanner.nextInt();
                    scanner.nextLine();
                    
                    if (choice < 1 || choice > hits.size()) {
                        throw new RuntimeException("Invalid selection");
                    }
                    
                    return hits.get(choice - 1);
                });
    }
    
    private void displayResults(CompositeResult result) {
        System.out.println("\n=== RESULTS ===");
        System.out.printf("Selected location: %s, %s%n", 
            result.getSelectedLocation().getName(), 
            result.getSelectedLocation().getCountry());
        System.out.printf("Type: %s%n", result.getLocationType());
        System.out.printf("Coordinates: %.6f, %.6f%n",
            result.getSelectedLocation().getPoint().getLat(),
            result.getSelectedLocation().getPoint().getLng());
        
        displayWeather(result.getWeather());
        displayPlaces(result.getPlaces());
    }
    
    private void displayWeather(Weather weather) {
        System.out.println("\n=== WEATHER ===");
        if (weather != null && weather.getName() != null && 
            !weather.getName().equals("Weather data unavailable")) {
            System.out.printf("Temperature: %.1f°C (feels like %.1f°C)%n",
                weather.getMain().getTemp(),
                weather.getMain().getFeels_like());
            
            if (weather.getWeather() != null && !weather.getWeather().isEmpty()) {
                System.out.printf("Conditions: %s%n", 
                    weather.getWeather().get(0).getDescription());
            }
            
            System.out.printf("Humidity: %d%%%n", weather.getMain().getHumidity());
            
            if (weather.getMain().getPressure() > 0) {
                System.out.printf("Pressure: %d hPa%n", weather.getMain().getPressure());
            }
            
            if (weather.getWind() != null) {
                System.out.printf("Wind speed: %.1f m/s%n", weather.getWind().getSpeed());
            }
        } else {
            System.out.println("Weather information unavailable");
        }
    }
    
    private void displayPlaces(List<CompositeResult.PlaceWithDetails> places) {
        System.out.println("\n=== INTERESTING PLACES ===");
        if (places == null || places.isEmpty()) {
            System.out.println("No interesting places found");
        } else {
            for (int i = 0; i < places.size(); i++) {
                CompositeResult.PlaceWithDetails placeWithDetails = places.get(i);
                System.out.printf("%d. %s%n", i + 1, placeWithDetails.getPlace().getName());
                System.out.printf("   Type: %s%n", placeWithDetails.getPlaceType());
                System.out.printf("   Categories: %s%n", placeWithDetails.getPlace().getKinds());
                
                if (placeWithDetails.getDetails() != null && 
                    placeWithDetails.getDetails().getInfo() != null && 
                    placeWithDetails.getDetails().getInfo().getDescr() != null) {
                    String description = placeWithDetails.getDetails().getInfo().getDescr();
                    if (description.length() > 150) {
                        description = description.substring(0, 150) + "...";
                    }
                    System.out.printf("   Description: %s%n", description);
                }
                System.out.println();
            }
        }
    }
    
    public static void main(String[] args) {
        try {
            new LocationSearchApp().start();
        } catch (Exception e) {
            log.error("Fatal error: {}", e.getMessage());
            System.exit(1);
        }
    }
}