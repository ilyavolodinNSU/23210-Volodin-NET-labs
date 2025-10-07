package nets.labs.lab3.services;

import nets.labs.lab3.AppConfig;
import nets.labs.lab3.dto.Weather;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static nets.labs.lab3.AppConstants.WEATHER_PARAMS;

public class WeatherService {
    private final ApiClient apiClient;
    private final String apiKey;
    private final String baseUrl;
    private final String units;
    
    public WeatherService(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.apiKey = AppConfig.getOpenweatherApiKey();
        this.baseUrl = AppConfig.getOpenweatherBaseUrl();
        this.units = AppConfig.getUnits();
    }
    
    public CompletableFuture<Weather> getWeather(double lat, double lon) {
        String url = baseUrl + String.format(Locale.US, WEATHER_PARAMS, lat, lon, apiKey, units);
        return apiClient.getAsync(url, Weather.class);
    }
}