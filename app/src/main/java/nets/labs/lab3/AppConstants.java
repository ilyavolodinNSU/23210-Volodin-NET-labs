package nets.labs.lab3;

public class AppConstants {
    private AppConstants() {}
    
    public static final String ACCEPT_HEADER = "Accept";
    public static final String APPLICATION_JSON = "application/json";
    
    public static final String GEOCODING_QUERY_PARAMS = "?q=%s&locale=%s&key=%s";
    public static final String PLACES_RADIUS_PARAMS = "/%s/places/radius?radius=%d&lon=%f&lat=%f&format=json&limit=%d&apikey=%s";
    public static final String PLACES_DETAILS_PARAMS = "/%s/places/xid/%s?apikey=%s";
    public static final String WEATHER_PARAMS = "?lat=%f&lon=%f&appid=%s&units=%s";
    
    public static final String WEATHER_UNAVAILABLE = "Weather data unavailable";
    public static final String PLACES_UNAVAILABLE = "No interesting places found";
    
    public static final int MAX_DESCRIPTION_LENGTH = 150;
    public static final String DESCRIPTION_ELLIPSIS = "...";
}