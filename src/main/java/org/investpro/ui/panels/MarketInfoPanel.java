package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.market.MarketStats;
import org.investpro.models.trading.TradePair;
import org.investpro.service.MarketInfoDataProvider;
import org.investpro.service.NewsDataProvider;

/**
 * Professional market stats panel displaying comprehensive market information.
 * Shows market cap, volume, performance metrics, benchmarks, and asset
 * description.
 */
@Slf4j
@Getter
@Setter
public class MarketInfoPanel extends ScrollPane {

    private final VBox mainContent = new VBox(16);
    private final Label symbolLabel = new Label();
    private final Label priceLabel = new Label();
    private final MarketInfoDataProvider dataProvider;
    private Exchange exchange;
    private String currentSymbol = "";

    public MarketInfoPanel() {
        this(null, null);
    }

    public MarketInfoPanel(Exchange exchange, NewsDataProvider newsDataProvider) {
        this.exchange = exchange;
        this.dataProvider = new MarketInfoDataProvider(newsDataProvider);
        setFitToWidth(true);
        setStyle("-fx-padding: 0; -fx-border-color: #e5e7eb;");
        getStyleClass().add("market-stats-panel");

        mainContent.setPadding(new Insets(16));
        mainContent.setStyle("-fx-background-color: #ffffff;");
        setContent(mainContent);

        setupUI();
    }

    private void setupUI() {
        // Header section
        VBox headerBox = createHeaderBox();
        mainContent.getChildren().add(headerBox);

        // Will add stats and other sections when data is available
    }

    private VBox createHeaderBox() {
        VBox header = new VBox(8);
        header.setStyle(
                "-fx-background-color: #f3f4f6; -fx-padding: 16; -fx-border-radius: 8; -fx-border-color: #d1d5db; -fx-border-width: 1;");

        symbolLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #111827;");
        priceLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
        priceLabel.setTextFill(Color.web("#000000"));

        header.getChildren().addAll(symbolLabel, priceLabel);
        return header;
    }

    /**
     * Update display with comprehensive market stats
     */
    public void updateStats(MarketStats stats) {
        if (stats == null) {
            clearStats();
            return;
        }

        currentSymbol = stats.getSymbol();
        symbolLabel.setText(stats.getSymbol() + " - " + stats.getName());
        priceLabel.setText(formatPrice(stats.getCurrentPrice()));

        mainContent.getChildren().clear();
        mainContent.getChildren().add(createHeaderBox());

        // Stats section
        mainContent.getChildren().addAll(
                new Separator(),
                createLabel("Stats", true),
                createStatsGrid(stats),
                new Separator(),
                createLabel("Benchmarks", true),
                createBenchmarksGrid(stats),
                new Separator(),
                createLabel("About " + stats.getSymbol(), true),
                createAboutSection(stats));
    }

    private VBox createStatsGrid(MarketStats stats) {
        VBox grid = new VBox(8);
        grid.setStyle(
                "-fx-background-color: #f9fafb; -fx-padding: 12; -fx-border-radius: 6; -fx-border-color: #e5e7eb; -fx-border-width: 1;");

        grid.getChildren()
                .add(createStatRow("Market cap", valueOrNA(stats.getMarketCap(), stats.getFormattedMarketCap()),
                        Color.web("#3b82f6")));

        // Volume (24h)
        String volumeText = valueOrNA(stats.getVolume24h(), stats.getFormattedVolume24h());
        String volumeChange = String.format("%+.2f%%", stats.getVolumeChange24h());
        grid.getChildren().add(createStatRowWithChange("Volume (24h)", volumeText, volumeChange,
                stats.getVolumeChange24h() >= 0 ? Color.web("#10b981") : Color.web("#ef4444")));

        // Circulating supply
        String supply = stats.getCirculating() > 0.0
                ? String.format("%.0f %s", stats.getCirculating(), stats.getCirculatingUnit())
                : "N/A";
        grid.getChildren().add(createStatRow("Circulating supply", supply, Color.web("#8b5cf6")));

        // Fully diluted market cap
        grid.getChildren()
                .add(createStatRow("Fully diluted market cap", valueOrNA(stats.getFullyDilutedMarketCap(),
                        stats.getFormattedFDMC()), Color.web("#3b82f6")));

        // All Time High
        String athText = stats.getAllTimeHigh() > 0.0 ? formatPrice(stats.getAllTimeHigh()) : "N/A";
        String athDate = stats.getAllTimeHighDate() == null || stats.getAllTimeHighDate().isBlank()
                ? ""
                : " (" + stats.getAllTimeHighDate() + ")";
        grid.getChildren().add(createStatRow("All time high" + athDate, athText, Color.web("#10b981")));

        // % down from ATH
        String athPercent = String.format("-%.2f%%", stats.getPercentDownFromATH());
        grid.getChildren().add(createStatRow("% down from all time high", athPercent,
                Color.web(stats.getATHColor())));

        return grid;
    }

    private VBox createBenchmarksGrid(MarketStats stats) {
        VBox grid = new VBox(8);
        grid.setStyle(
                "-fx-background-color: #f9fafb; -fx-padding: 12; -fx-border-radius: 6; -fx-border-color: #e5e7eb; -fx-border-width: 1;");

        // Coinbase Popularity
        grid.getChildren()
                .add(createStatRow("Coinbase Popularity", stats.getCoinbasePopularityRank(), Color.web("#f59e0b")));

        // Performance section
        VBox performanceBox = new VBox(6);
        performanceBox.setPadding(new Insets(8));
        performanceBox.setStyle("-fx-border-color: #e5e7eb; -fx-border-radius: 4;");
        performanceBox.getChildren().add(createLabel("Performance (Past year)", false));
        performanceBox.getChildren()
                .add(createStatRow("Past year", String.format("%+.2f%%", stats.getPerformanceOneYear()),
                        Color.web(stats.getPerformanceColor(stats.getPerformanceOneYear()))));
        performanceBox.getChildren()
                .add(createStatRow("Past month", String.format("%+.2f%%", stats.getPerformanceOneMonth()),
                        Color.web(stats.getPerformanceColor(stats.getPerformanceOneMonth()))));
        performanceBox.getChildren()
                .add(createStatRow("Past week", String.format("%+.2f%%", stats.getPerformanceOneWeek()),
                        Color.web(stats.getPerformanceColor(stats.getPerformanceOneWeek()))));
        performanceBox.getChildren()
                .add(createStatRow("Past day", String.format("%+.2f%%", stats.getPerformanceOneDay()),
                        Color.web(stats.getPerformanceColor(stats.getPerformanceOneDay()))));
        grid.getChildren().add(performanceBox);

        // Comparison section
        VBox comparisonBox = new VBox(6);
        comparisonBox.setPadding(new Insets(8));
        comparisonBox.setStyle("-fx-border-color: #e5e7eb; -fx-border-radius: 4;");

        String benchmarkLabel = getBenchmarkLabel();
        comparisonBox.getChildren().add(createLabel("Comparison vs. " + benchmarkLabel + " & Market", false));
        comparisonBox.getChildren()
                .add(createStatRow("vs. " + benchmarkLabel + " (Past year)",
                        String.format("%+.2f%%", stats.getVsEthOneYear()),
                        Color.web(stats.getPerformanceColor(stats.getVsEthOneYear()))));
        comparisonBox.getChildren()
                .add(createStatRow("vs. Market (Past year)", String.format("%+.2f%%", stats.getVsMarketOneYear()),
                        Color.web(stats.getPerformanceColor(stats.getVsMarketOneYear()))));
        grid.getChildren().add(comparisonBox);

        return grid;
    }

    /**
     * Determine the appropriate benchmark asset based on the current symbol
     */
    private String getBenchmarkLabel() {
        if (currentSymbol == null || currentSymbol.isEmpty()) {
            return "ETH";
        }

        String symbol = currentSymbol.toUpperCase();

        // If current symbol is BTC, compare against ETH
        if (symbol.equals("BTC")) {
            return "ETH";
        }

        // If current symbol is ETH, compare against BTC
        if (symbol.equals("ETH")) {
            return "BTC";
        }

        // For all other assets, compare against ETH as primary benchmark
        return "ETH";
    }

    private VBox createAboutSection(MarketStats stats) {
        VBox about = new VBox(12);
        about.setStyle(
                "-fx-background-color: #f9fafb; -fx-padding: 12; -fx-border-radius: 6; -fx-border-color: #e5e7eb; -fx-border-width: 1;");

        // Description
        if (stats.getDescription() != null && !stats.getDescription().isEmpty()) {
            TextArea descArea = new TextArea(stats.getDescription());
            descArea.setWrapText(true);
            descArea.setEditable(false);
            descArea.setPrefRowCount(4);
            descArea.setStyle("-fx-control-inner-background: #f9fafb; -fx-font-size: 11;");
            about.getChildren().add(descArea);
        }

        // Resources
        HBox resourcesBox = new HBox(12);
        resourcesBox.setPadding(new Insets(8));
        resourcesBox.setStyle("-fx-border-color: #e5e7eb; -fx-border-radius: 4;");

        if (stats.getWebsiteUrl() != null && !stats.getWebsiteUrl().isEmpty()) {
            Hyperlink website = new Hyperlink("Website");
            website.setOnAction(e -> openLink(stats.getWebsiteUrl()));
            resourcesBox.getChildren().add(website);
        }

        if (stats.getWhitePaperUrl() != null && !stats.getWhitePaperUrl().isEmpty()) {
            Hyperlink whitepaper = new Hyperlink("Whitepaper");
            whitepaper.setOnAction(e -> openLink(stats.getWhitePaperUrl()));
            resourcesBox.getChildren().add(whitepaper);
        }

        if (stats.getGithubUrl() != null && !stats.getGithubUrl().isEmpty()) {
            Hyperlink github = new Hyperlink("GitHub");
            github.setOnAction(e -> openLink(stats.getGithubUrl()));
            resourcesBox.getChildren().add(github);
        }

        if (stats.getTwitterUrl() != null && !stats.getTwitterUrl().isEmpty()) {
            Hyperlink twitter = new Hyperlink("Twitter");
            twitter.setOnAction(e -> openLink(stats.getTwitterUrl()));
            resourcesBox.getChildren().add(twitter);
        }

        if (!resourcesBox.getChildren().isEmpty()) {
            VBox resourcesSection = new VBox(6);
            resourcesSection.getChildren().add(createLabel("Resources", false));
            resourcesSection.getChildren().add(resourcesBox);
            about.getChildren().add(resourcesSection);
        }

        // Disclaimer
        Label disclaimer = new Label(
                "Displayed prices exclude trading costs. Actual execution may incur additional spread and fees. " +
                        "Third-party and user-generated content is made available for informational purposes only and should not be treated as investment advice.");
        disclaimer.setWrapText(true);
        disclaimer.setStyle("-fx-font-size: 10; -fx-text-fill: #9ca3af;");
        disclaimer.setPadding(new Insets(8));
        about.getChildren().add(disclaimer);

        return about;
    }

    private HBox createStatRow(String label, String value, Color color) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setStyle("-fx-background-color: #ffffff; -fx-padding: 8; -fx-border-radius: 4;");

        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-size: 12; -fx-text-fill: #374151; -fx-font-weight: 500;");
        labelNode.setMinWidth(150);

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        valueNode.setTextFill(color);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(labelNode, spacer, valueNode);
        return row;
    }

    private HBox createStatRowWithChange(String label, String value, String change, Color changeColor) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setStyle("-fx-background-color: #ffffff; -fx-padding: 8; -fx-border-radius: 4;");

        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-size: 12; -fx-text-fill: #374151; -fx-font-weight: 500;");
        labelNode.setMinWidth(150);

        VBox valueBox = new VBox(2);
        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
        valueNode.setTextFill(Color.web("#000000"));

        Label changeNode = new Label(change);
        changeNode.setStyle("-fx-font-size: 10;");
        changeNode.setTextFill(changeColor);

        valueBox.getChildren().addAll(valueNode, changeNode);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(labelNode, spacer, valueBox);
        return row;
    }

    private Label createLabel(String text, boolean isSection) {
        Label label = new Label(text);
        if (isSection) {
            label.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #111827; -fx-padding: 8 0 4 0;");
        } else {
            label.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #374151; -fx-padding: 6 0 4 0;");
        }
        return label;
    }

    private void openLink(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            log.warn("Failed to open link: {}", url, e);
        }
    }

    private void clearStats() {
        symbolLabel.setText("N/A");
        priceLabel.setText("0.00");
        mainContent.getChildren().clear();
        mainContent.getChildren().add(createHeaderBox());
    }

    /**
     * Load sample data for testing
     */
    public void loadSampleData() {
        updateStats(MarketStats.createDummyBitcoinStats());
    }

    /**
     * Update market info panel for a trading pair
     * Currently displays sample data - will be enhanced with real API data
     */
    public void updateForPair(TradePair pair) {
        if (pair == null) {
            clearStats();
            return;
        }

        symbolLabel.setText(pair.getSymbol());
        priceLabel.setText("Loading...");
        mainContent.getChildren().clear();
        mainContent.getChildren().add(createHeaderBox());

        dataProvider.getMarketInfo(exchange, pair)
                .thenAccept(stats -> Platform.runLater(() -> updateStats(stats)))
                .exceptionally(error -> {
                    log.warn("Failed to load market info for {}", pair, error);
                    Platform.runLater(() -> updateStats(null));
                    return null;
                });
    }

    private String formatPrice(double value) {
        if (value <= 0.0 || !Double.isFinite(value)) {
            return "N/A";
        }
        return value >= 100.0 ? String.format("%.2f", value) : String.format("%.6f", value);
    }

    private String valueOrNA(double value, String formatted) {
        return value > 0.0 && Double.isFinite(value) ? formatted : "N/A";
    }
}
