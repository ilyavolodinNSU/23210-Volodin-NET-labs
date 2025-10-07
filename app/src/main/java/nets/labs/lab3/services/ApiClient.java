package nets.labs.lab3.services;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import nets.labs.lab3.AppConfig;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static nets.labs.lab3.AppConstants.*;

@Slf4j
public class ApiClient {
    private final HttpClient httpClient;
    private final Gson gson;
    private final int timeoutSeconds;
    private final int connectTimeoutSeconds;
    
    public ApiClient() {
        this.timeoutSeconds = AppConfig.getHttpTimeout();
        this.connectTimeoutSeconds = AppConfig.getHttpConnectTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        this.gson = new Gson();
    }
    
    public <T> CompletableFuture<T> getAsync(String url, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header(ACCEPT_HEADER, APPLICATION_JSON)
                .GET()
                .build();
                
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("HTTP error: " + response.statusCode());
                    }
                    return response.body();
                })
                .thenApply(body -> {
                    try {
                        return gson.fromJson(body, responseType);
                    } catch (Exception e) {
                        log.error("Error parsing JSON for URL {}: {}", url, e.getMessage());
                        throw new RuntimeException("JSON parsing error", e);
                    }
                })
                .exceptionally(ex -> {
                    log.error("API call failed for URL {}: {}", url, ex.getMessage());
                    throw new RuntimeException("API call failed", ex);
                });
    }
}