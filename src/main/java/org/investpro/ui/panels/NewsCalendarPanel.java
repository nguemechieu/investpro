package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.market.NewsEvent;
import org.investpro.service.NewsDataProvider;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * News Calendar Panel displaying economic events and news blackout periods.
 * Allows users to view upcoming events, configure blackout settings, and manage
 * news trading rules.
 */
@Slf4j
public class NewsCalendarPanel extends VBox {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NewsCalendarPanel.class);

    private final NewsDataProvider newsDataProvider;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm");

    // Controls
    private final TableView<NewsEvent> newsTable = new TableView<>();
    private final CheckBox blackoutMasterToggle = new CheckBox("Enable News Blackout");
    private final Label statusLabel = new Label("No active blackouts");
    private final Button loadSampleCalendarButton = new Button("Load Sample Calendar");
    private final Button refreshButton = new Button("Refresh");

    // Filters
    private final ComboBox<String> currencyFilter = new ComboBox<>();
    private final ComboBox<String> importanceFilter = new ComboBox<>();

    public NewsCalendarPanel(NewsDataProvider newsDataProvider) {
        this.newsDataProvider = newsDataProvider;
        setupUI();
        setupTable();
        setupEventHandlers();
        refreshNews();
    }

    private void setupUI() {
        setSpacing(12);
        setPadding(new Insets(15));
        setStyle("-fx-border-color: #1a1a1a; -fx-background-color: #0a0a0a;");

        // Title
        Label titleLabel = new Label("ECONOMIC CALENDAR");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #00ff00;");
        getChildren().add(titleLabel);

        getChildren().add(new Separator());

        // Controls Bar
        getChildren().add(createControlsBar());

        getChildren().add(new Separator());

        // Filters
        getChildren().add(createFiltersBar());

        getChildren().add(new Separator());

        // Status
        statusLabel.setStyle("-fx-text-fill: #ffaa00; -fx-font-weight: bold;");
        getChildren().add(statusLabel);

        getChildren().add(new Separator());

        // Table
        newsTable.setPrefHeight(300);
        VBox.setVgrow(newsTable, Priority.ALWAYS);
        getChildren().add(new ScrollPane(newsTable));
    }

    private HBox createControlsBar() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        blackoutMasterToggle.setStyle("-fx-text-fill: #cccccc;");
        blackoutMasterToggle.setSelected(true);

        loadSampleCalendarButton.setStyle("-fx-font-size: 11; -fx-padding: 8;");
        refreshButton.setStyle("-fx-font-size: 11; -fx-padding: 8;");

        box.getChildren().addAll(
                blackoutMasterToggle,
                new Separator(),
                loadSampleCalendarButton,
                refreshButton);

        return box;
    }

    private HBox createFiltersBar() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-padding: 8; -fx-border-color: #333; -fx-border-radius: 3;");

        // Currency Filter
        Label currLabel = new Label("Currency:");
        currLabel.setStyle("-fx-text-fill: #888;");
        currencyFilter.getItems().addAll("ALL", "USD", "EUR", "GBP", "JPY");
        currencyFilter.setValue("ALL");
        currencyFilter.setPrefWidth(100);

        // Importance Filter
        Label impLabel = new Label("Importance:");
        impLabel.setStyle("-fx-text-fill: #888;");
        importanceFilter.getItems().addAll("ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW");
        importanceFilter.setValue("ALL");
        importanceFilter.setPrefWidth(120);

        box.getChildren().addAll(
                currLabel, currencyFilter,
                impLabel, importanceFilter);

        return box;
    }

    private void setupTable() {
        newsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        newsTable.setStyle("-fx-font-size: 10; -fx-control-inner-background: #1a1a1a; -fx-text-fill: #cccccc;");

        // Event Time Column
        TableColumn<NewsEvent, String> timeCol = new TableColumn<>("Event Time");
        timeCol.setPrefWidth(150);
        timeCol.setCellValueFactory(cellData -> javafx.beans.binding.Bindings.createStringBinding(
                () -> cellData.getValue().getEventTime().atZone(java.time.ZoneId.systemDefault())
                        .format(dateFormatter)));

        // Title Column
        TableColumn<NewsEvent, String> titleCol = new TableColumn<>("Event");
        titleCol.setPrefWidth(250);
        titleCol.setCellValueFactory(cellData -> javafx.beans.binding.Bindings.createStringBinding(
                () -> cellData.getValue().getTitle()));

        // Currency Column
        TableColumn<NewsEvent, String> currencyCol = new TableColumn<>("Currency");
        currencyCol.setPrefWidth(80);
        currencyCol.setCellValueFactory(cellData -> javafx.beans.binding.Bindings.createStringBinding(
                () -> cellData.getValue().getCurrency()));

        // Importance Column
        TableColumn<NewsEvent, String> importanceCol = new TableColumn<>("Impact");
        importanceCol.setPrefWidth(80);
        importanceCol.setCellValueFactory(cellData -> javafx.beans.binding.Bindings.createStringBinding(
                () -> cellData.getValue().getImportance().toString()));
        importanceCol.setCellFactory(tc -> new TableCell<NewsEvent, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "CRITICAL" -> setStyle("-fx-text-fill: #ff0000; -fx-font-weight: bold;");
                        case "HIGH" -> setStyle("-fx-text-fill: #ff6666;");
                        case "MEDIUM" -> setStyle("-fx-text-fill: #ffaa00;");
                        case "LOW" -> setStyle("-fx-text-fill: #888;");
                    }
                }
            }
        });

        // Sentiment Column
        TableColumn<NewsEvent, String> sentimentCol = new TableColumn<>("Sentiment");
        sentimentCol.setPrefWidth(100);
        sentimentCol.setCellValueFactory(cellData -> javafx.beans.binding.Bindings.createStringBinding(
                () -> cellData.getValue().getSentiment().toString()));
        sentimentCol.setCellFactory(tc -> new TableCell<NewsEvent, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "BULLISH" -> setStyle("-fx-text-fill: #00ff00;");
                        case "BEARISH" -> setStyle("-fx-text-fill: #ff0000;");
                        case "NEUTRAL" -> setStyle("-fx-text-fill: #888;");
                    }
                }
            }
        });

        // Blackout Status Column
        TableColumn<NewsEvent, String> blackoutCol = new TableColumn<>("Blackout");
        blackoutCol.setPrefWidth(80);
        blackoutCol.setCellValueFactory(cellData -> javafx.beans.binding.Bindings.createStringBinding(
                () -> cellData.getValue().isBlackoutEnabled() ? "ACTIVE" : "OFF"));
        blackoutCol.setCellFactory(tc -> new TableCell<NewsEvent, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("ACTIVE".equals(item)) {
                        setStyle("-fx-text-fill: #ff6666;");
                    }
                }
            }
        });

        newsTable.getColumns().addAll(
                timeCol, titleCol, currencyCol, importanceCol, sentimentCol, blackoutCol);
    }

    private void setupEventHandlers() {
        blackoutMasterToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            // This would toggle in the NewsDataProvider
            log.info("News blackout master toggle: {}", newVal);
            updateStatus();
        });

        loadSampleCalendarButton.setOnAction(event -> {
            newsDataProvider.clearAll();
            newsDataProvider.loadSampleCalendar();
            refreshNews();
            showAlert("Success", "Sample calendar loaded with upcoming economic events");
        });

        refreshButton.setOnAction(event -> refreshNews());

        currencyFilter.setOnAction(event -> refreshNews());
        importanceFilter.setOnAction(event -> refreshNews());

        // Add listener to news provider for real-time updates
        newsDataProvider.addNewsEventListener((event, action) -> {
            if ("ADDED".equals(action) || "PROCESSED".equals(action)) {
                refreshNews();
                updateStatus();
            }
        });
    }

    private void refreshNews() {
        newsTable.getItems().clear();

        // Get filtered events
        List<NewsEvent> events = newsDataProvider.getUpcomingNewsEvents();

        String currency = currencyFilter.getValue();
        if (!"ALL".equals(currency)) {
            events = events.stream()
                    .filter(e -> e.getCurrency().equals(currency))
                    .toList();
        }

        String importance = importanceFilter.getValue();
        if (!"ALL".equals(importance)) {
            events = events.stream()
                    .filter(e -> e.getImportance().toString().equals(importance))
                    .toList();
        }

        newsTable.getItems().addAll(events);
        updateStatus();
    }

    private void updateStatus() {
        int blackoutCount = newsDataProvider.getActiveBlackoutCount();
        boolean blackoutActive = newsDataProvider.isNewsBlackoutActive();

        if (blackoutActive) {
            statusLabel.setText(String.format("⚠️ NEWS BLACKOUT ACTIVE - %d event(s) blocking trades", blackoutCount));
            statusLabel.setStyle("-fx-text-fill: #ff0000; -fx-font-weight: bold;");
        } else if (newsDataProvider.getImmediateUpcomingEvents().size() > 0) {
            statusLabel.setText(String.format("⏰ %d upcoming event(s) in next 60 minutes",
                    newsDataProvider.getImmediateUpcomingEvents().size()));
            statusLabel.setStyle("-fx-text-fill: #ffaa00; -fx-font-weight: bold;");
        } else {
            statusLabel.setText("✓ No active blackouts - Trading enabled");
            statusLabel.setStyle("-fx-text-fill: #00ff00; -fx-font-weight: bold;");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
