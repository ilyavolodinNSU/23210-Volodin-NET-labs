package nets.labs.lab3;

import nets.labs.lab3.AppConfig;
import nets.labs.lab3.dto.*;
import nets.labs.lab3.services.*;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class LocationSearchAppGUI extends JFrame {
    private final GeocodingService geocodingService;
    private final WeatherService weatherService;
    private final PlacesService placesService;
    private final LocationTypeService locationTypeService;
    private final int defaultRadius;

    private JTextField searchField;
    private JButton searchButton;
    private JList<Location.Hit> locationsList;
    private DefaultListModel<Location.Hit> listModel;
    private JTextArea locationInfoArea;
    private JScrollPane locationsScrollPane;
    private JScrollPane infoScrollPane;

    public LocationSearchAppGUI() {
        ApiClient apiClient = new ApiClient();
        this.geocodingService = new GeocodingService(apiClient);
        this.weatherService = new WeatherService(apiClient);
        this.placesService = new PlacesService(apiClient);
        this.locationTypeService = new LocationTypeService(placesService);
        this.defaultRadius = AppConfig.getOpentripmapDefaultRadius();

        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("Location Search Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);


        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.add(createSearchPanel(), BorderLayout.NORTH);
        contentPanel.add(createMainContentPanel(), BorderLayout.CENTER);

        setContentPane(contentPanel);
    }

    private JPanel createSearchPanel() {
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(new TitledBorder("Search Location"));

        searchField = new JTextField();
        searchButton = new JButton("Search");

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        searchButton.addActionListener(new SearchButtonListener());
        searchField.addActionListener(new SearchButtonListener());

        return searchPanel;
    }

    private JSplitPane createMainContentPanel() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(createLocationsPanel());
        splitPane.setRightComponent(createLocationInfoPanel());
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.5);

        return splitPane;
    }

    private JPanel createLocationsPanel() {
        JPanel locationsPanel = new JPanel(new BorderLayout());
        locationsPanel.setBorder(new TitledBorder("Found Locations"));

        listModel = new DefaultListModel<>();
        locationsList = new JList<>(listModel);
        locationsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        locationsList.setCellRenderer(new LocationListRenderer());

        locationsScrollPane = new JScrollPane(locationsList);
        locationsPanel.add(locationsScrollPane, BorderLayout.CENTER);

        locationsList.addMouseListener(new LocationListListener());

        return locationsPanel;
    }

    private JPanel createLocationInfoPanel() {
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new TitledBorder("Location Information"));

        locationInfoArea = new JTextArea();
        locationInfoArea.setEditable(false);
        locationInfoArea.setLineWrap(true);
        locationInfoArea.setWrapStyleWord(true);
        locationInfoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        infoScrollPane = new JScrollPane(locationInfoArea);
        infoPanel.add(infoScrollPane, BorderLayout.CENTER);

        locationInfoArea.setText("Select a location from the list to see details...");

        return infoPanel;
    }

    private class SearchButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String query = searchField.getText().trim();
            if (query.isEmpty()) {
                JOptionPane.showMessageDialog(LocationSearchAppGUI.this,
                    "Please enter a location name",
                    "Search Error",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            searchButton.setEnabled(false);
            searchButton.setText("Searching...");
            listModel.clear();
            locationInfoArea.setText("Searching...");

            geocodingService.searchLocations(query)
                .thenApply(location -> {
                    List<Location.Hit> hits = location.getHits();
                    if (hits.isEmpty()) {
                        throw new RuntimeException("No locations found");
                    }
                    return hits;
                })
                .thenAccept(hits -> SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    for (Location.Hit hit : hits) {
                        listModel.addElement(hit);
                    }
                    searchButton.setEnabled(true);
                    searchButton.setText("Search");
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(LocationSearchAppGUI.this,
                            "Search failed: " + ex.getMessage(),
                            "Search Error",
                            JOptionPane.ERROR_MESSAGE);
                        searchButton.setEnabled(true);
                        searchButton.setText("Search");
                        locationInfoArea.setText("Search failed. Please try again.");
                    });
                    return null;
                });
        }
    }

    private class LocationListListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 1) {
                Location.Hit selectedLocation = locationsList.getSelectedValue();
                if (selectedLocation != null) {
                    updateLocationInfo(selectedLocation);
                }
            }
        }
    }

    private void updateLocationInfo(Location.Hit selectedLocation) {
        locationInfoArea.setText("Loading location information...");

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
            errorWeather.setName("Weather data unavailable");
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

        locationTypeFuture.thenCompose(locationType -> 
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
        ).thenAccept(result -> SwingUtilities.invokeLater(() -> displayLocationInfo(result)))
         .exceptionally(ex -> {
             SwingUtilities.invokeLater(() -> {
                 locationInfoArea.setText("Error loading location information: " + ex.getMessage());
             });
             return null;
         });
    }

    private void displayLocationInfo(CompositeResult result) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== LOCATION INFO ===\n");
        sb.append(String.format("Name: %s\n", result.getSelectedLocation().getName()));
        sb.append(String.format("Country: %s\n", result.getSelectedLocation().getCountry()));
        if (result.getSelectedLocation().getState() != null && !result.getSelectedLocation().getState().isEmpty()) {
            sb.append(String.format("State: %s\n", result.getSelectedLocation().getState()));
        }
        sb.append(String.format("Type: %s\n", result.getLocationType()));
        sb.append(String.format("Coordinates: %.6f, %.6f\n\n",
            result.getSelectedLocation().getPoint().getLat(),
            result.getSelectedLocation().getPoint().getLng()));

        displayWeatherInfo(sb, result.getWeather());
        displayPlacesInfo(sb, result.getPlaces());

        locationInfoArea.setText(sb.toString());
    }

    private void displayWeatherInfo(StringBuilder sb, Weather weather) {
        sb.append("=== WEATHER ===\n");
        if (weather != null && weather.getName() != null && 
            !weather.getName().equals("Weather data unavailable")) {
            sb.append(String.format("Temperature: %.1f°C (feels like %.1f°C)\n",
                weather.getMain().getTemp(),
                weather.getMain().getFeels_like()));
            
            if (weather.getWeather() != null && !weather.getWeather().isEmpty()) {
                sb.append(String.format("Conditions: %s\n", 
                    weather.getWeather().get(0).getDescription()));
            }
            
            sb.append(String.format("Humidity: %d%%\n", weather.getMain().getHumidity()));
            
            if (weather.getMain().getPressure() > 0) {
                sb.append(String.format("Pressure: %d hPa\n", weather.getMain().getPressure()));
            }
            
            if (weather.getWind() != null) {
                sb.append(String.format("Wind speed: %.1f m/s\n", weather.getWind().getSpeed()));
            }
        } else {
            sb.append("Weather information unavailable\n");
        }
        sb.append("\n");
    }

    private void displayPlacesInfo(StringBuilder sb, List<CompositeResult.PlaceWithDetails> places) {
        sb.append("=== INTERESTING PLACES ===\n");
        if (places == null || places.isEmpty()) {
            sb.append("No interesting places found\n");
        } else {
            for (int i = 0; i < places.size(); i++) {
                CompositeResult.PlaceWithDetails placeWithDetails = places.get(i);
                sb.append(String.format("%d. %s\n", i + 1, placeWithDetails.getPlace().getName()));
                sb.append(String.format("   Type: %s\n", placeWithDetails.getPlaceType()));
                sb.append(String.format("   Categories: %s\n", placeWithDetails.getPlace().getKinds()));
                
                if (placeWithDetails.getDetails() != null && 
                    placeWithDetails.getDetails().getInfo() != null && 
                    placeWithDetails.getDetails().getInfo().getDescr() != null) {
                    String description = placeWithDetails.getDetails().getInfo().getDescr();
                    if (description.length() > 150) {
                        description = description.substring(0, 150) + "...";
                    }
                    sb.append(String.format("   Description: %s\n", description));
                }
                sb.append("\n");
            }
        }
    }

    private static class LocationListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                     boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Location.Hit) {
                Location.Hit hit = (Location.Hit) value;
                String text = String.format("<html><b>%s</b><br/>%s, %s<br/><small>%.6f, %.6f</small></html>",
                    hit.getName(),
                    hit.getState() != null ? hit.getState() : "",
                    hit.getCountry(),
                    hit.getPoint().getLat(),
                    hit.getPoint().getLng());
                setText(text);
            }
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String systemLookAndFeel = UIManager.getSystemLookAndFeelClassName();
                UIManager.setLookAndFeel(systemLookAndFeel);
            } catch (Exception e) {
                log.error("Error setting look and feel: {}", e.getMessage());
            }
            
            new LocationSearchAppGUI().setVisible(true);
        });
    }
}

// btw i hate f***ng swing 