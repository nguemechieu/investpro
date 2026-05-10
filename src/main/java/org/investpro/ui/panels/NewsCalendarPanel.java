package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import lombok.extern.slf4j.Slf4j;
import org.investpro.i18n.LocalizationService;
import org.investpro.models.market.NewsEvent;
import org.investpro.service.NewsDataProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Modern Economic Calendar Panel with professional visualization.
 *
 * Features:
 * - Timeline view showing events chronologically
 * - Event cards with impact indicators
 * - Quick-filter pills for easy filtering
 * - Real-time status metrics
 * - Blackout period management
 * - Search functionality
 * - Responsive design
 *
 * Design: Modern card-based layout with visual hierarchy and color coding
 */
@Slf4j
public class NewsCalendarPanel extends AnchorPane{
    private final NewsDataProvider newsDataProvider;
   // private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm");
    private final DateTimeFormatter timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm");

    private VBox eventsList;
    private TextField searchField;
    private CheckBox blackoutMasterToggle;
    private Label statusLabel;
    private Label upcomingLabel;
    private Label blackoutLabel;

    // Filters
    private final String[] currencies = { "ALL", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF" };
    private final String[] importances = { "ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW" };
    private String selectedCurrency = "ALL";
    private String selectedImportance = "ALL";

    public NewsCalendarPanel(NewsDataProvider newsDataProvider) {
        this.newsDataProvider = newsDataProvider;
        this.newsDataProvider.loadSampleCalendarIfEmpty();

        setupUI();
        setupEventHandlers();
        LocalizationService.applyTranslations(this);
        refreshNews();
    }

    private void setupUI() {

        setPadding(new Insets(0));
        setStyle("-fx-background-color: #0a0e17;");

        // Header Section
        VBox header = createHeader();
        getChildren().add(header);

        // Metrics Cards Section
        HBox metricsBox = createMetricsCards();
        getChildren().add(metricsBox);

        // Filters Bar Section
        HBox filtersBar = createFiltersBar();
        getChildren().add(filtersBar);

        // Events Timeline Section
        VBox timelineSection = createTimelineSection();
        VBox.setVgrow(timelineSection, Priority.ALWAYS);
        getChildren().add(timelineSection);
    }

    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(20, 20, 15, 20));
        header.setStyle("-fx-background-color: #0f1724; -fx-border-color: #1e2d42; -fx-border-width: 0 0 1 0;");

        Label titleLabel = new Label("📅 Economic Calendar");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        Label descLabel = new Label("Track global economic events and market-moving announcements");
        descLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #94a3b8;");

        HBox headerControls = new HBox(10);
        headerControls.setAlignment(Pos.CENTER_LEFT);

        // Search field
        searchField = new TextField();
        searchField.setPromptText("🔍 Search events...");
        searchField.setPrefWidth(200);
        searchField.setPrefHeight(32);
        searchField.setStyle("-fx-control-inner-background: #1a2332; -fx-text-fill: #e2e8f0; " +
                "-fx-prompt-text-fill: #64748b; -fx-border-color: #334155; -fx-border-radius: 4; -fx-padding: 8;");

        // Blackout toggle
        blackoutMasterToggle = new CheckBox("Enable Blackout Mode");
        blackoutMasterToggle.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 11;");
        blackoutMasterToggle.setSelected(true);

        // Refresh button
        Button refreshButton = new Button("↻ Refresh");
        refreshButton.setPrefSize(100, 32);
        refreshButton.setStyle("-fx-font-size: 11; -fx-padding: 8; " +
                "-fx-background-color: #1e40af; -fx-text-fill: #ffffff; -fx-border-radius: 4;");
        refreshButton.setOnAction(e -> refreshNews());

        // Load sample
        Button loadSampleButton = new Button("📊 Load Sample");
        loadSampleButton.setPrefSize(100, 32);
        loadSampleButton.setStyle("-fx-font-size: 11; -fx-padding: 8; " +
                "-fx-background-color: #7c2d12; -fx-text-fill: #ffffff; -fx-border-radius: 4;");
        loadSampleButton.setOnAction(e -> {
            newsDataProvider.clearAll();
            newsDataProvider.loadSampleCalendar();
            refreshNews();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerControls.getChildren().addAll(searchField, spacer, blackoutMasterToggle, refreshButton, loadSampleButton);

        header.getChildren().addAll(titleLabel, descLabel, headerControls);
        return header;
    }

    private HBox createMetricsCards() {
        HBox metricsBox = new HBox(15);
        metricsBox.setPadding(new Insets(15, 20, 15, 20));
        metricsBox.setStyle("-fx-background-color: #0a0e17;");

        // Total Events Card
        VBox totalCard = createMetricCard("📌 Total Events", "0", "#1e40af");

        // Upcoming Soon Card
        VBox upcomingCard = createMetricCard("⚡ Upcoming (Soon)", "0", "#b45309");
        upcomingLabel = (Label) ((VBox) upcomingCard.getChildren().get(1)).getChildren().get(0);

        // Active Blackout Card
        VBox blackoutCard = createMetricCard("🚫 Active Blackouts", "0", "#dc2626");
        blackoutLabel = (Label) ((VBox) blackoutCard.getChildren().get(1)).getChildren().get(0);

        // Status Card
        VBox statusCard = createMetricCard("✓ Status", "Ready", "#16a34a");
        statusLabel = (Label) ((VBox) statusCard.getChildren().get(1)).getChildren().get(0);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        metricsBox.getChildren().addAll(totalCard, upcomingCard, blackoutCard, statusCard, spacer);
        return metricsBox;
    }

    private VBox createMetricCard(String title, String value, String bgColor) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: " + bgColor + "11; -fx-border-color: " + bgColor + "44; " +
                "-fx-border-radius: 4; -fx-background-radius: 4;");
        card.setPrefWidth(140);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 20; -fx-font-weight: bold;");

        card.getChildren().addAll(titleLabel, new VBox(valueLabel));
        return card;
    }

    private HBox createFiltersBar() {
        HBox filterBar = new HBox(10);
        filterBar.setPadding(new Insets(12, 20, 12, 20));
        filterBar.setStyle("-fx-background-color: #0f1724; -fx-border-color: #1e2d42; -fx-border-width: 1 0 1 0;");
        filterBar.setAlignment(Pos.CENTER_LEFT);

        // Currency filter pills
        VBox currencyBox = createFilterPillBox("Currency", currencies);

        // Importance filter pills
        VBox importanceBox = createFilterPillBox("Impact Level", importances);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        filterBar.getChildren().addAll(currencyBox, importanceBox, spacer);
        return filterBar;
    }

    private VBox createFilterPillBox(String label, String[] options) {
        VBox box = new VBox(6);

        Label labelText = new Label(label);
        labelText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10;");

        HBox pillBox = new HBox(6);
        pillBox.setAlignment(Pos.CENTER_LEFT);

        boolean isCurrency = "Currency".equals(label);

        for (String option : options) {
            Button pill = new Button(option);
            pill.setPrefHeight(28);
            pill.setStyle("-fx-font-size: 10; -fx-padding: 4 10 4 10; " +
                    "-fx-background-color: #1e2d42; -fx-text-fill: #cbd5e1; -fx-border-radius: 12; " +
                    "-fx-background-radius: 12; -fx-cursor: hand;");

            pill.setOnAction(e -> {
                if (isCurrency) {
                    selectedCurrency = option;
                } else {
                    selectedImportance = option;
                }
                updatePillStyles(pillBox, option);
                refreshNews();
            });

            pillBox.getChildren().add(pill);
        }

        // Set initial selection
        updatePillStyles(pillBox, "ALL");

        box.getChildren().addAll(labelText, pillBox);
        return box;
    }

    private void updatePillStyles(HBox pillBox, String selected) {
        for (javafx.scene.Node node : pillBox.getChildren()) {
            if (node instanceof Button button) {
                if (button.getText().equals(selected)) {
                    button.setStyle("-fx-font-size: 10; -fx-padding: 4 10 4 10; " +
                            "-fx-background-color: #3b82f6; -fx-text-fill: #ffffff; -fx-border-radius: 12; " +
                            "-fx-background-radius: 12; -fx-font-weight: bold;");
                } else {
                    button.setStyle("-fx-font-size: 10; -fx-padding: 4 10 4 10; " +
                            "-fx-background-color: #1e2d42; -fx-text-fill: #cbd5e1; -fx-border-radius: 12; " +
                            "-fx-background-radius: 12;");
                }
            }
        }
    }

    private VBox createTimelineSection() {
        VBox timelineSection = new VBox();
        timelineSection.setStyle("-fx-background-color: #0a0e17;");

        eventsList = new VBox(8);
        eventsList.setPadding(new Insets(15, 20, 15, 20));
        eventsList.setStyle("-fx-background-color: #0a0e17;");

        // UI Components
        ScrollPane eventsScrollPane = new ScrollPane(eventsList);
        eventsScrollPane.setStyle("-fx-control-inner-background: #0a0e17;");
        eventsScrollPane.setFitToWidth(true);
        eventsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        timelineSection.getChildren().add(eventsScrollPane);
        VBox.setVgrow(eventsScrollPane, Priority.ALWAYS);

        return timelineSection;
    }

    private void setupEventHandlers() {
        blackoutMasterToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            log.info("News blackout master toggle: {}", newVal);
            updateStatus();
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshNews());

        // Add listener to news provider for real-time updates
        newsDataProvider.addNewsEventListener((event, action) -> {
            if ("ADDED".equals(action) || "PROCESSED".equals(action)) {
                Platform.runLater(this::refreshNews);
            }
        });
    }

    private void refreshNews() {
        eventsList.getChildren().clear();

        List<NewsEvent> events = newsDataProvider.getUpcomingNewsEvents();

        // Apply filters
        if (!"ALL".equals(selectedCurrency)) {
            events = events.stream()
                    .filter(e -> e.getCurrency().equals(selectedCurrency))
                    .toList();
        }

        if (!"ALL".equals(selectedImportance)) {
            events = events.stream()
                    .filter(e -> e.getImportance().toString().equals(selectedImportance))
                    .toList();
        }

        String searchText = searchField.getText().trim().toLowerCase();
        if (!searchText.isEmpty()) {
            events = events.stream()
                    .filter(e -> e.getTitle().toLowerCase().contains(searchText) ||
                            e.getCurrency().toLowerCase().contains(searchText))
                    .toList();
        }

        if (events.isEmpty()) {
            Label noEventsLabel = new Label("📭 No events matching your filters");
            noEventsLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12; -fx-padding: 40;");
            eventsList.getChildren().add(noEventsLabel);
        } else {
            LocalDateTime now = LocalDateTime.now();
            String currentDate = "";

            for (NewsEvent event : events) {
                LocalDateTime eventTime = event.getEventTime().atZone(ZoneId.systemDefault()).toLocalDateTime();
                String eventDate = eventTime.toLocalDate().toString();

                // Add date divider if date changed
                if (!eventDate.equals(currentDate)) {
                    currentDate = eventDate;
                    Label dateLabel = new Label("📆 " + eventDate);
                    dateLabel.setStyle(
                            "-fx-text-fill: #94a3b8; -fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 10 0 5 0;");
                    eventsList.getChildren().add(dateLabel);
                }

                VBox eventCard = createEventCard(event);
                eventsList.getChildren().add(eventCard);
            }
        }

        updateStatus();
    }

    private VBox createEventCard(NewsEvent event) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #0f1724; -fx-border-color: #1e2d42; -fx-border-width: 1; " +
                "-fx-border-radius: 4; -fx-background-radius: 4;");

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Impact indicator circle
        Circle impactCircle = createImpactCircle(event.getImportance().toString());

        // Time
        LocalDateTime eventTime = event.getEventTime().atZone(ZoneId.systemDefault()).toLocalDateTime();
        Label timeLabel = new Label(eventTime.format(timeOnlyFormatter));
        timeLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 11; -fx-font-weight: bold;");

        // Currency badge
        Label currencyBadge = new Label(event.getCurrency());
        currencyBadge.setPadding(new Insets(2, 6, 2, 6));
        currencyBadge.setStyle("-fx-background-color: #1e40af; -fx-text-fill: #ffffff; " +
                "-fx-font-size: 9; -fx-font-weight: bold; -fx-border-radius: 3; -fx-background-radius: 3;");

        // Event title
        Label titleLabel = new Label(event.getTitle());
        titleLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 12; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(impactCircle, timeLabel, currencyBadge, titleLabel, spacer);

        HBox detailsRow = new HBox(20);
        detailsRow.setPadding(new Insets(6, 0, 0, 0));

        // Sentiment indicator
        Label sentimentLabel = new Label("Sentiment: " + event.getSentiment().toString());
        String sentimentColor = "BULLISH".equals(event.getSentiment().toString()) ? "#10b981"
                : "BEARISH".equals(event.getSentiment().toString()) ? "#ef4444" : "#6b7280";
        sentimentLabel.setStyle("-fx-text-fill: " + sentimentColor + "; -fx-font-size: 10;");

        // Blackout status
        Label blackoutLabel = new Label(event.isBlackoutEnabled() ? "🚫 Blackout Active" : "✓ Trading Allowed");
        String blackoutColor = event.isBlackoutEnabled() ? "#dc2626" : "#10b981";
        blackoutLabel.setStyle("-fx-text-fill: " + blackoutColor + "; -fx-font-size: 10; -fx-font-weight: bold;");

        detailsRow.getChildren().addAll(sentimentLabel, blackoutLabel);

        card.getChildren().addAll(topRow, detailsRow);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #0f1724; -fx-border-color: #3b82f6; " +
                "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #0f1724; -fx-border-color: #1e2d42; " +
                "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;"));

        return card;
    }

    private Circle createImpactCircle(String importance) {
        Circle circle = new Circle(6);
        switch (importance) {
            case "CRITICAL" -> circle.setFill(Color.web("#ff0000"));
            case "HIGH" -> circle.setFill(Color.web("#ff6666"));
            case "MEDIUM" -> circle.setFill(Color.web("#ffaa00"));
            case "LOW" -> circle.setFill(Color.web("#888888"));
        }
        return circle;
    }

    private void updateStatus() {
        int totalEvents = newsDataProvider.getUpcomingNewsEvents().size();
        int blackoutCount = newsDataProvider.getActiveBlackoutCount();
        int upcomingSoon = newsDataProvider.getImmediateUpcomingEvents().size();

        // Update metric labels
        upcomingLabel.setText(String.valueOf(upcomingSoon));
        blackoutLabel.setText(String.valueOf(blackoutCount));

        if (blackoutMasterToggle.isSelected() && blackoutCount > 0) {
            statusLabel.setText("🚫 Blackout Active");
            statusLabel.setStyle("-fx-text-fill: #ff0000;");
        } else if (upcomingSoon > 0) {
            statusLabel.setText("⚡ Events Soon");
            statusLabel.setStyle("-fx-text-fill: #ffaa00;");
        } else {
            statusLabel.setText("✓ Ready");
            statusLabel.setStyle("-fx-text-fill: #10b981;");
        }
    }
}
