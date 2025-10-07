package nets.labs.lab3;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class AppConfig {
    private static final Properties properties = new Properties();
    
    static {
        loadProperties();
    }
    
    private static void loadProperties() {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                log.warn("Config file not found, using default values");
                return;
            }
            properties.load(input);
            log.info("Configuration loaded successfully");
        } catch (IOException e) {
            log.error("Error loading configuration: {}", e.getMessage());
        }
    }
    
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }
    
    public static String getGraphhopperApiKey() {
        return getProperty("graphhopper.api.key", "");
    }
    
    public static String getGraphhopperBaseUrl() {
        return getProperty("graphhopper.base.url", "https://graphhopper.com/api/1/geocode");
    }
    
    public static String getOpentripmapApiKey() {
        return getProperty("opentripmap.api.key", "");
    }
    
    public static String getOpentripmapBaseUrl() {
        return getProperty("opentripmap.base.url", "https://api.opentripmap.com/0.1");
    }
    
    public static int getOpentripmapDefaultRadius() {
        return getIntProperty("opentripmap.radius.default", 1000);
    }
    
    public static int getOpentripmapDefaultLimit() {
        return getIntProperty("opentripmap.limit.default", 10);
    }
    
    public static String getOpenweatherApiKey() {
        return getProperty("openweather.api.key", "");
    }
    
    public static String getOpenweatherBaseUrl() {
        return getProperty("openweather.base.url", "https://api.openweathermap.org/data/2.5/weather");
    }
    
    public static String getLocale() {
        return getProperty("app.locale", "ru");
    }
    
    public static String getUnits() {
        return getProperty("app.units", "metric");
    }
    
    public static int getHttpTimeout() {
        return getIntProperty("app.http.timeout.seconds", 15);
    }
    
    public static int getHttpConnectTimeout() {
        return getIntProperty("app.http.connect.timeout.seconds", 10);
    }
}