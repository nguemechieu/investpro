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
import org.investpro.i18n.LocalizationService;
import org.investpro.market.MarketMetrics;
import org.investpro.market.MarketStats;
import org.investpro.models.trading.TradePair;
import org.investpro.service.MarketInfoDataProvider;
import org.investpro.service.NewsDataProvider;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

import java.util.concurrent.CompletableFuture;

/**
 * Multi-asset market information panel for InvestPro / TradeAdviser.
 * <p>
 * This panel is intentionally asset-class aware, not crypto-only. It can display
 * crypto, stocks, ETFs, forex, indices, commodities, and derivative-style market
 * information as long as MarketInfoDataProvider can return MarketStats and/or
 * MarketMetrics for the selected TradePair.
 * <p>
 * Provider strategy should live in MarketInfoDataProvider, not here. Suggested
 * provider routing:
 * - Crypto: CoinGecko / exchange ticker / broker ticker
 * - Stocks, ETFs, indices: Yahoo-style quote provider, Alpha Vantage, broker ticker
 * - Forex: broker pricing, Alpha Vantage FX, exchangerate provider
 * - Commodities / futures / CFDs: broker pricing, Yahoo-style symbol, Alpha Vantage
 */
@Slf4j
@Getter
@Setter
public class MarketInfoPanel extends ScrollPane {

    private static final Color BG = Color.web("#0f172a");
    private static final Color CARD = Color.web("#111827");
    private static final Color CARD_2 = Color.web("#172033");
    private static final Color BORDER = Color.web("#263244");
    private static final Color TEXT = Color.web("#e5e7eb");
    private static final Color MUTED = Color.web("#94a3b8");
    private static final Color BLUE = Color.web("#3b82f6");
    private static final Color GREEN = Color.web("#10b981");
    private static final Color RED = Color.web("#ef4444");
    private static final Color AMBER = Color.web("#f59e0b");
    private static final Color PURPLE = Color.web("#8b5cf6");
    private static final Color CYAN = Color.web("#06b6d4");

    private final VBox mainContent = new VBox(14);
    private final VBox headerBox = new VBox(8);
    private final Label assetClassBadge = new Label("MARKET");
    private final Label providerBadge = new Label("Provider: -");
    private final Label symbolLabel = new Label("Select symbol");
    private final Label nameLabel = new Label("Market information will appear here.");
    private final Label priceLabel = new Label("N/A");
    private final Label statusLabel = new Label("Ready");

    private final MarketInfoDataProvider dataProvider;
    private Exchange exchange;
    private TradePair currentPair;
    private String currentSymbol = "";
    private AssetClass currentAssetClass = AssetClass.UNKNOWN;
    private MarketStats currentStats;
    private MarketMetrics currentMetrics;

    public MarketInfoPanel() {
        this(null, null);
    }

    public MarketInfoPanel(Exchange exchange, NewsDataProvider newsDataProvider) {
        this.exchange = exchange;
        this.dataProvider = new MarketInfoDataProvider(newsDataProvider);

        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setStyle("-fx-background: #0f172a; -fx-background-color: #0f172a; -fx-border-color: #263244;");
        getStyleClass().add("market-stats-panel");

        mainContent.setPadding(new Insets(14));
        mainContent.setStyle("-fx-background-color: #0f172a;");
        setContent(mainContent);

        setupUI();
        LocalizationService.applyTranslations(this);
    }

    private void setupUI() {
        configureHeaderBox();
        mainContent.getChildren().setAll(headerBox, createEmptyState());
    }

    private void configureHeaderBox() {
        headerBox.setPadding(new Insets(14));
        headerBox.setStyle(cardStyle("#111827", "#263244"));

        HBox topLine = new HBox(8, assetClassBadge, providerBadge);
        topLine.setAlignment(Pos.CENTER_LEFT);

        styleBadge(assetClassBadge, BLUE);
        styleBadge(providerBadge, MUTED);

        symbolLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");
        nameLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        nameLabel.setWrapText(true);

        priceLabel.setStyle("-fx-font-size: 30; -fx-font-weight: bold; -fx-text-fill: #e5e7eb;");
        statusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        statusLabel.setWrapText(true);

        headerBox.getChildren().setAll(topLine, symbolLabel, nameLabel, priceLabel, statusLabel);
    }

    /**
     * Updates this panel for a selected pair.
     */
    public void updateForPair(TradePair pair) {
        if (pair == null) {
            clearStats();
            return;
        }

        currentPair = pair;
        currentSymbol = normalizeSymbol(pair.getSymbol());
        currentAssetClass = inferAssetClass(pair);
        currentStats = null;
        currentMetrics = null;

        applyLoadingState(pair);

        CompletableFuture<MarketStats> statsFuture = dataProvider.getMarketInfo(exchange, pair);

        statsFuture
                .thenAccept(stats -> Platform.runLater(() -> updateStats(stats)))
                .exceptionally(error -> {
                    log.warn("Failed to load market info for {}", pair, error);
                    Platform.runLater(() -> {
                        currentStats = null;
                        renderCurrentState("Market stats unavailable. Showing available pair metrics only.");
                    });
                    return null;
                });
    }

    /**
     * Update display with comprehensive market stats.
     */
    public void updateStats(MarketStats stats) {
        currentStats = stats;

        if (stats != null) {
            currentSymbol = normalizeSymbol(firstNonBlank(stats.getSymbol(), currentSymbol));
        }

        renderCurrentState(stats == null ? "Market stats unavailable." : "Market stats loaded.");
    }

    /**
     * Update display with market metrics / technical analysis data.
     * Safe to call repeatedly: this method rebuilds the panel instead of appending
     * duplicate Technical Metrics sections.
     */
    public void updateMetrics(MarketMetrics metrics) {
        currentMetrics = metrics;
        renderCurrentState(metrics == null ? "Technical metrics unavailable." : "Technical metrics loaded.");
    }

    private void applyLoadingState(TradePair pair) {
        symbolLabel.setText(displaySymbol(pair));
        nameLabel.setText(assetClassLabel(currentAssetClass) + " • Loading market information...");
        priceLabel.setText("Loading...");
        assetClassBadge.setText(assetClassLabel(currentAssetClass).toUpperCase(Locale.ROOT));
        providerBadge.setText("Provider: resolving");
        statusLabel.setText("Fetching market information from exchange/provider pipeline...");
        mainContent.getChildren().setAll(headerBox, createLoadingBox());
    }

    private void renderCurrentState(String status) {
        updateHeader(status);

        VBox sections = new VBox(12);
        sections.getChildren().add(createPairSnapshotSection());

        if (currentStats != null) {
            sections.getChildren().add(createMarketStatsSection(currentStats));
            sections.getChildren().add(createBenchmarkSection(currentStats));
            sections.getChildren().add(createAboutSection(currentStats));
        } else {
            sections.getChildren().add(createProviderHintSection());
        }

        if (currentMetrics != null) {
            sections.getChildren().add(createMetricsSection(currentMetrics));
        }

        sections.getChildren().add(createProviderRoutingSection());
        mainContent.getChildren().setAll(headerBox, sections);
    }

    private void updateHeader(String status) {
        String symbol = currentPair != null ? displaySymbol(currentPair) : currentSymbol;
        String name = currentStats == null ? "" : safe(currentStats.getName());

        symbolLabel.setText(firstNonBlank(symbol, "N/A"));
        nameLabel.setText(buildNameLine(name));
        priceLabel.setText(resolveDisplayPrice());
        priceLabel.setTextFill(resolvePriceColor());
        assetClassBadge.setText(assetClassLabel(currentAssetClass).toUpperCase(Locale.ROOT));
        providerBadge.setText("Provider: " + providerName(currentAssetClass));
        statusLabel.setText(status == null ? "Ready" : status);
    }

    private VBox createPairSnapshotSection() {
        VBox section = section("Selected Market");
        GridPane grid = grid();

        addGridRow(grid, 0, "Symbol", currentPair == null ? value(currentSymbol) : value(displaySymbol(currentPair), BLUE));
        addGridRow(grid, 1, "Asset class", value(assetClassLabel(currentAssetClass), CYAN));
        addGridRow(grid, 2, "Exchange / venue", value(exchange == null ? "N/A" : safe(exchange.getDisplayName()), PURPLE));

        if (currentPair != null) {
            addGridRow(grid, 3, "Bid", value(formatPrice(safeDouble(currentPair.getBid())), GREEN));
            addGridRow(grid, 4, "Ask", value(formatPrice(safeDouble(currentPair.getAsk())), RED));
            addGridRow(grid, 5, "Last", value(formatPrice(firstPositive(currentPair.getLast(), currentPair.getLastPrice())), TEXT));
            addGridRow(grid, 6, "Spread", value(formatSpread(currentPair), MUTED));
            addGridRow(grid, 7, "Volume", value(formatCompact(safeDouble(currentPair.getVolume())), BLUE));
        }

        section.getChildren().add(grid);
        return section;
    }

    private VBox createMarketStatsSection(MarketStats stats) {
        VBox section = section(sectionTitleForStats());
        GridPane grid = grid();

        addGridRow(grid, 0, capLabelForAsset(), valueOrNA(stats.getMarketCap(), stats.getFormattedMarketCap(), BLUE));
        addGridRow(grid, 1, volumeLabelForAsset(), valueOrNA(stats.getVolume24h(), stats.getFormattedVolume24h(), BLUE));
        addGridRow(grid, 2, "Volume change", coloredPercent(stats.getVolumeChange24h()));
        addGridRow(grid, 3, supplyLabelForAsset(), value(supplyText(stats), PURPLE));
        addGridRow(grid, 4, dilutedLabelForAsset(), valueOrNA(stats.getFullyDilutedMarketCap(), stats.getFormattedFDMC(), BLUE));
        addGridRow(grid, 5, highLabelForAsset(), value(allTimeHighText(stats), GREEN));
        addGridRow(grid, 6, "Distance from high", value(distanceFromHighText(stats), colorFromWeb(stats.getATHColor(), AMBER)));

        section.getChildren().add(grid);
        return section;
    }

    private VBox createBenchmarkSection(MarketStats stats) {
        VBox section = section("Performance & Benchmarks");
        GridPane grid = grid();

        addGridRow(grid, 0, "Past day", coloredPercent(stats.getPerformanceOneDay()));
        addGridRow(grid, 1, "Past week", coloredPercent(stats.getPerformanceOneWeek()));
        addGridRow(grid, 2, "Past month", coloredPercent(stats.getPerformanceOneMonth()));
        addGridRow(grid, 3, "Past year", coloredPercent(stats.getPerformanceOneYear()));

        String benchmark = getBenchmarkLabel();
        addGridRow(grid, 4, "vs. " + benchmark + " / primary benchmark", coloredPercent(stats.getVsEthOneYear()));
        addGridRow(grid, 5, "vs. broad market", coloredPercent(stats.getVsMarketOneYear()));
        addGridRow(grid, 6, rankingLabelForAsset(), value(firstNonBlank(stats.getCoinbasePopularityRank(), "N/A"), AMBER));

        section.getChildren().add(grid);
        return section;
    }

    private VBox createMetricsSection(MarketMetrics metrics) {
        VBox section = section("Technical Metrics");
        GridPane grid = grid();

        addGridRow(grid, 0, "Current price", value(formatPrice(metrics.getCurrentPrice()), TEXT));
        addGridRow(grid, 1, "Bid", value(formatPrice(metrics.getBid()), GREEN));
        addGridRow(grid, 2, "Ask", value(formatPrice(metrics.getAsk()), RED));
        addGridRow(grid, 3, "Spread", value(String.format("%.6f (%.4f%%)", metrics.getSpread(), metrics.getSpreadPercent()), MUTED));
        addGridRow(grid, 4, "High", value(formatPrice(metrics.getHigh24h()), GREEN));
        addGridRow(grid, 5, "Low", value(formatPrice(metrics.getLow24h()), RED));
        addGridRow(grid, 6, "Range", value(String.format("%.6f (%.2f%%)", metrics.getHighLowRange(), metrics.getHighLowRangePercent()), MUTED));
        addGridRow(grid, 7, "Volume", value(formatCompact(metrics.getVolume24h()), BLUE));
        addGridRow(grid, 8, "Change", coloredPercent(metrics.getChangePercent24h()));
        addGridRow(grid, 9, "Volatility", value(String.format("%.2f%% (%s)", metrics.getVolatility(), safe(metrics.getVolatilityLevel())), getVolatilityColor(metrics.getVolatilityLevel())));
        addGridRow(grid, 10, "Trend", value(String.format("%s (%.0f%%)", safe(metrics.getTrend()), metrics.getTrendStrength()), getTrendColor(metrics.getTrend())));
        addGridRow(grid, 11, "Technical signal", value(safe(metrics.getTechnicalSignal()), getTechnicalSignalColor(metrics.getTechnicalSignal())));
        addGridRow(grid, 12, "Technical score", value(String.format("%+.0f", metrics.getTechnicalScore()), metrics.getTechnicalScore() >= 0 ? GREEN : RED));
        addGridRow(grid, 13, "Below high", value(String.format("-%.2f%%", metrics.getPriceChangeFromHigh()), MUTED));
        addGridRow(grid, 14, "Above low", value(String.format("+%.2f%%", metrics.getPriceChangeFromLow()), MUTED));

        section.getChildren().add(grid);
        return section;
    }

    private VBox createAboutSection(MarketStats stats) {
        VBox section = section("About " + firstNonBlank(stats.getSymbol(), currentSymbol, "Market"));

        String description = safe(stats.getDescription());
        if (!description.isBlank()) {
            TextArea descArea = new TextArea(description);
            descArea.setWrapText(true);
            descArea.setEditable(false);
            descArea.setPrefRowCount(5);
            descArea.setStyle("-fx-control-inner-background: #0b1220; -fx-text-fill: #e5e7eb; -fx-font-size: 11; -fx-border-color: #263244;");
            section.getChildren().add(descArea);
        } else {
            Label empty = mutedLabel("No description available for this market yet.");
            section.getChildren().add(empty);
        }

        HBox resourcesBox = new HBox(10);
        resourcesBox.setAlignment(Pos.CENTER_LEFT);
        addLink(resourcesBox, "Website", stats.getWebsiteUrl());
        addLink(resourcesBox, "Whitepaper", stats.getWhitePaperUrl());
        addLink(resourcesBox, "GitHub", stats.getGithubUrl());
        addLink(resourcesBox, "Social", stats.getTwitterUrl());

        if (!resourcesBox.getChildren().isEmpty()) {
            section.getChildren().add(resourcesBox);
        }

        Label disclaimer = mutedLabel("Information is provider/broker supplied and may be delayed or incomplete. Execution prices may differ due to spread, liquidity, commissions, swaps, and broker rules. This is market information, not investment advice.");
        section.getChildren().add(disclaimer);

        return section;
    }

    private VBox createProviderHintSection() {
        VBox section = section("Provider Coverage Needed");
        Label text = mutedLabel("No MarketStats were returned for this symbol. Your provider layer should route the selected asset class to the best available source, then map the response into MarketStats / MarketMetrics.");
        section.getChildren().add(text);
        return section;
    }

    private VBox createProviderRoutingSection() {
        VBox section = section("Suggested Provider Routing");
        GridPane grid = grid();

        addGridRow(grid, 0, "Crypto", value("CoinGecko, exchange ticker, broker ticker", GREEN));
        addGridRow(grid, 1, "Stocks / ETFs", value("Yahoo-style quote, Alpha Vantage, Alpaca/IBKR", BLUE));
        addGridRow(grid, 2, "Forex", value("OANDA pricing, Alpha Vantage FX, broker feed", CYAN));
        addGridRow(grid, 3, "Indices", value("Yahoo-style quote, Alpha Vantage, broker feed", PURPLE));
        addGridRow(grid, 4, "Commodities / CFDs", value("Broker feed, Alpha Vantage commodities, Yahoo-style quote", AMBER));
        addGridRow(grid, 5, "Derivatives", value("Broker/exchange metadata first; fallback to underlying quote", RED));

        section.getChildren().add(grid);
        return section;
    }

    private VBox createLoadingBox() {
        VBox box = section("Loading");
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(36, 36);
        Label label = mutedLabel("Collecting market stats and pricing metrics...");
        HBox row = new HBox(12, indicator, label);
        row.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(row);
        return box;
    }

    private VBox createEmptyState() {
        VBox box = section("Market Info");
        box.getChildren().add(mutedLabel("Select a symbol to view market data, technical metrics, benchmarks, and asset details."));
        return box;
    }

    private VBox section(String title) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle(cardStyle("#111827", "#263244"));

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");
        box.getChildren().add(titleLabel);
        return box;
    }

    private GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(46);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(54);
        grid.getColumnConstraints().setAll(left, right);
        return grid;
    }

    private void addGridRow(GridPane grid, int row, String label, Label value) {
        Label labelNode = new Label(label);
        labelNode.setWrapText(true);
        labelNode.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");

        value.setWrapText(true);
        value.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(value, Priority.ALWAYS);

        grid.add(labelNode, 0, row);
        grid.add(value, 1, row);
    }

    private Label value(String text) {
        return value(text, TEXT);
    }

    private Label value(String text, Color color) {
        Label label = new Label(firstNonBlank(text, "N/A"));
        label.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        label.setTextFill(color == null ? TEXT : color);
        return label;
    }

    private Label coloredPercent(double value) {
        if (!Double.isFinite(value)) {
            return value("N/A", MUTED);
        }
        return value(String.format("%+.2f%%", value), value >= 0 ? GREEN : RED);
    }

    private Label valueOrNA(double raw, String formatted, Color color) {
        return value(raw > 0.0 && Double.isFinite(raw) ? firstNonBlank(formatted, formatCompact(raw)) : "N/A", color);
    }

    private Label mutedLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 11; -fx-text-fill: #94a3b8;");
        return label;
    }

    private void addLink(HBox box, String text, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        Hyperlink link = new Hyperlink(text);
        link.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11; -fx-font-weight: bold;");
        link.setOnAction(event -> openLink(url));
        box.getChildren().add(link);
    }

    private void styleBadge(Label label, Color color) {
        label.setPadding(new Insets(3, 8, 3, 8));
        label.setTextFill(color == null ? TEXT : color);
        label.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-background-color: #0b1220; -fx-background-radius: 999; -fx-border-color: #263244; -fx-border-radius: 999;");
    }

    private String cardStyle(String bg, String border) {
        return "-fx-background-color: " + bg + ";"
                + " -fx-background-radius: 10;"
                + " -fx-border-color: " + border + ";"
                + " -fx-border-radius: 10;"
                + " -fx-border-width: 1;";
    }

    private void clearStats() {
        currentPair = null;
        currentStats = null;
        currentMetrics = null;
        currentSymbol = "";
        currentAssetClass = AssetClass.UNKNOWN;

        symbolLabel.setText("N/A");
        nameLabel.setText("No selected market.");
        priceLabel.setText("N/A");
        assetClassBadge.setText("MARKET");
        providerBadge.setText("Provider: -");
        statusLabel.setText("Select a symbol to load market information.");
        mainContent.getChildren().setAll(headerBox, createEmptyState());
    }

    private String resolveDisplayPrice() {
        if (currentStats != null && currentStats.getCurrentPrice() > 0.0) {
            return formatPrice(currentStats.getCurrentPrice());
        }
        if (currentMetrics != null && currentMetrics.getCurrentPrice() > 0.0) {
            return formatPrice(currentMetrics.getCurrentPrice());
        }
        if (currentPair != null) {
            double price = firstPositive(currentPair.getLast(), currentPair.getLastPrice(), currentPair.getAsk(), currentPair.getBid());
            if (price > 0.0) {
                return formatPrice(price);
            }
        }
        return "N/A";
    }

    private Color resolvePriceColor() {
        if (currentMetrics != null && currentMetrics.getChangePercent24h() != 0.0) {
            return currentMetrics.getChangePercent24h() >= 0 ? GREEN : RED;
        }
        if (currentStats != null && currentStats.getPerformanceOneDay() != 0.0) {
            return currentStats.getPerformanceOneDay() >= 0 ? GREEN : RED;
        }
        return TEXT;
    }

    private String buildNameLine(String statsName) {
        String venue = exchange == null ? "No venue" : safe(exchange.getDisplayName());
        String name = firstNonBlank(statsName, assetClassLabel(currentAssetClass));
        return name + " • " + venue;
    }

    private String sectionTitleForStats() {
        return switch (currentAssetClass) {
            case STOCK, ETF -> "Equity Snapshot";
            case FOREX -> "FX Snapshot";
            case INDEX -> "Index Snapshot";
            case COMMODITY -> "Commodity Snapshot";
            case FUTURE, OPTION, CFD -> "Derivative Snapshot";
            case CRYPTO -> "Crypto Snapshot";
            default -> "Market Snapshot";
        };
    }

    private String capLabelForAsset() {
        return switch (currentAssetClass) {
            case STOCK, ETF -> "Market value / cap";
            case FOREX -> "Notional market size";
            case INDEX -> "Index market value";
            case COMMODITY -> "Market value";
            case FUTURE, OPTION, CFD -> "Underlying market value";
            default -> "Market cap";
        };
    }

    private String volumeLabelForAsset() {
        return switch (currentAssetClass) {
            case STOCK, ETF -> "Session / 24h volume";
            case FOREX -> "Quote activity / volume";
            case INDEX -> "Index volume";
            case COMMODITY -> "Contract / spot volume";
            default -> "Volume (24h)";
        };
    }

    private String supplyLabelForAsset() {
        return switch (currentAssetClass) {
            case STOCK, ETF -> "Shares outstanding / float";
            case FOREX -> "Base / quote pair";
            case INDEX -> "Constituents / units";
            case COMMODITY -> "Contract unit / supply";
            case FUTURE, OPTION, CFD -> "Contract size / exposure";
            default -> "Circulating supply";
        };
    }

    private String dilutedLabelForAsset() {
        return switch (currentAssetClass) {
            case STOCK, ETF -> "Enterprise / diluted value";
            case FOREX -> "Forward / implied value";
            case INDEX -> "Full market value";
            case COMMODITY -> "Fully valued exposure";
            case FUTURE, OPTION, CFD -> "Notional exposure";
            default -> "Fully diluted market cap";
        };
    }

    private String highLabelForAsset() {
        return switch (currentAssetClass) {
            case STOCK, ETF, INDEX -> "52w / all-time high";
            case FOREX -> "Range high";
            case COMMODITY -> "Contract / spot high";
            default -> "All-time high";
        };
    }

    private String rankingLabelForAsset() {
        return switch (currentAssetClass) {
            case STOCK, ETF -> "Watchlist / popularity rank";
            case FOREX -> "FX popularity rank";
            case INDEX -> "Index rank";
            case COMMODITY -> "Commodity rank";
            default -> "Provider popularity rank";
        };
    }

    private String providerName(AssetClass assetClass) {
        return switch (assetClass) {
            case CRYPTO -> "CoinGecko / Exchange";
            case STOCK, ETF, INDEX -> "Yahoo-style / Alpha Vantage / Broker";
            case FOREX -> "Broker FX / Alpha Vantage";
            case COMMODITY -> "Broker / Alpha Vantage / Yahoo-style";
            case FUTURE, OPTION, CFD -> "Broker metadata / underlying quote";
            default -> "Auto";
        };
    }

    private String getBenchmarkLabel() {
        return switch (currentAssetClass) {
            case CRYPTO -> {
                String symbol = currentSymbol.toUpperCase(Locale.ROOT);
                yield "BTC".equals(symbol) ? "ETH" : "BTC/ETH";
            }
            case STOCK, ETF -> "SPY / QQQ";
            case FOREX -> "DXY / USD";
            case INDEX -> "SPY / Global index";
            case COMMODITY -> "DXY / Commodity index";
            case FUTURE, OPTION, CFD -> "Underlying";
            default -> "Benchmark";
        };
    }

    private String supplyText(MarketStats stats) {
        if (currentAssetClass == AssetClass.FOREX && currentPair != null) {
            return displaySymbol(currentPair);
        }
        if (stats.getCirculating() > 0.0) {
            return String.format("%.0f %s", stats.getCirculating(), safe(stats.getCirculatingUnit()));
        }
        return "N/A";
    }

    private String allTimeHighText(MarketStats stats) {
        if (stats.getAllTimeHigh() <= 0.0) {
            return "N/A";
        }
        String date = safe(stats.getAllTimeHighDate());
        return formatPrice(stats.getAllTimeHigh()) + (date.isBlank() ? "" : " (" + date + ")");
    }

    private String distanceFromHighText(MarketStats stats) {
        if (!Double.isFinite(stats.getPercentDownFromATH()) || stats.getPercentDownFromATH() == 0.0) {
            return "N/A";
        }
        return String.format("-%.2f%%", Math.abs(stats.getPercentDownFromATH()));
    }

    private String formatSpread(TradePair pair) {
        if (pair == null || pair.getBid() <= 0 || pair.getAsk() <= 0 || pair.getAsk() < pair.getBid()) {
            return "N/A";
        }
        double spread = pair.getAsk() - pair.getBid();
        double spreadPercent = pair.getBid() > 0.0 ? (spread / pair.getBid()) * 100.0 : 0.0;
        return String.format("%s (%.4f%%)", formatPrice(spread), spreadPercent);
    }

    private AssetClass inferAssetClass(TradePair pair) {
        if (pair == null) {
            return AssetClass.UNKNOWN;
        }

        String symbol = normalizeSymbol(pair.getSymbol()).toUpperCase(Locale.ROOT);
        String base = safePairCode(pair::getBaseCode).toUpperCase(Locale.ROOT);
        String quote = inferQuoteCode(pair).toUpperCase(Locale.ROOT);
        String combined = symbol + " " + base + " " + quote;

        if (combined.contains("PERP") || combined.contains("FUT") || combined.contains("FUTURE")) {
            return AssetClass.FUTURE;
        }
        if (combined.contains("OPTION") || combined.contains("CALL") || combined.contains("PUT")) {
            return AssetClass.OPTION;
        }
        if (combined.contains("CFD")) {
            return AssetClass.CFD;
        }
        if (isCryptoCode(base) || isCryptoCode(symbol)) {
            return AssetClass.CRYPTO;
        }
        if (isForexPair(base, quote, symbol)) {
            return AssetClass.FOREX;
        }
        if (symbol.startsWith("^") || combined.contains("INDEX") || combined.contains("SPX") || combined.contains("NDX") || combined.contains("US30") || combined.contains("NAS100")) {
            return AssetClass.INDEX;
        }
        if (combined.contains("ETF") || symbol.endsWith(".ETF")) {
            return AssetClass.ETF;
        }
        if (isCommoditySymbol(symbol)) {
            return AssetClass.COMMODITY;
        }
        if (!symbol.isBlank()) {
            return AssetClass.STOCK;
        }
        return AssetClass.UNKNOWN;
    }

    private String inferQuoteCode(TradePair pair) {
        if (pair == null) {
            return "";
        }

        String symbol = normalizeSymbol(pair.getSymbol()).toUpperCase(Locale.ROOT);
        String display = displaySymbol(pair).toUpperCase(Locale.ROOT);
        String[] candidates = {display, symbol};

        for (String candidate : candidates) {
            if (candidate.contains("/")) {
                String[] parts = candidate.split("/", 2);
                if (parts.length == 2) {
                    return parts[1].replaceAll("[^A-Z]", "");
                }
            }
            if (candidate.contains("_")) {
                String[] parts = candidate.split("_", 2);
                if (parts.length == 2) {
                    return parts[1].replaceAll("[^A-Z]", "");
                }
            }
            if (candidate.contains("-")) {
                String[] parts = candidate.split("-", 2);
                if (parts.length == 2) {
                    return parts[1].replaceAll("[^A-Z]", "");
                }
            }
        }

        String compact = symbol.replaceAll("[^A-Z]", "");
        if (compact.length() == 6 && isFiatCode(compact.substring(3, 6))) {
            return compact.substring(3, 6);
        }
        return "";
    }

    private boolean isForexPair(String base, String quote, String symbol) {
        if (isFiatCode(base) && isFiatCode(quote)) {
            return true;
        }
        String compact = symbol.replace("/", "").replace("_", "").replace("-", "");
        return compact.length() == 6 && isFiatCode(compact.substring(0, 3)) && isFiatCode(compact.substring(3, 6));
    }

    private boolean isCryptoCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "AVAX", "DOT", "LTC", "BCH", "XLM", "MATIC", "LINK", "UNI", "ATOM", "BNB", "USDT", "USDC", "DAI" -> true;
            default -> false;
        };
    }

    private boolean isFiatCode(String code) {
        if (code == null || code.length() != 3) {
            return false;
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "SEK", "NOK", "DKK", "CNH", "CNY", "MXN", "ZAR", "TRY", "SGD", "HKD" -> true;
            default -> false;
        };
    }

    private boolean isCommoditySymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        String s = symbol.toUpperCase(Locale.ROOT);
        return s.contains("XAU") || s.contains("XAG") || s.contains("WTI") || s.contains("BRENT")
                || s.contains("OIL") || s.contains("GOLD") || s.contains("SILVER") || s.contains("COPPER")
                || s.contains("NATGAS") || s.contains("NG=") || s.contains("CL=") || s.contains("GC=") || s.contains("SI=");
    }

    private String assetClassLabel(AssetClass assetClass) {
        return switch (assetClass) {
            case CRYPTO -> "Crypto";
            case STOCK -> "Stock";
            case ETF -> "ETF";
            case FOREX -> "Forex";
            case INDEX -> "Index";
            case COMMODITY -> "Commodity";
            case FUTURE -> "Future / Perp";
            case OPTION -> "Option";
            case CFD -> "CFD";
            default -> "Market";
        };
    }

    private String displaySymbol(TradePair pair) {
        if (pair == null) {
            return "N/A";
        }
        try {
            String slash = pair.toSlashSymbol();
            if (slash != null && !slash.isBlank()) {
                return slash;
            }
        } catch (Exception ignored) {
            // Some TradePair implementations may not expose toSlashSymbol.
        }
        return firstNonBlank(pair.getSymbol(), pair.toString());
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }

    private String formatPrice(double value) {
        if (value <= 0.0 || !Double.isFinite(value)) {
            return "N/A";
        }
        if (value >= 1000.0) {
            return String.format("%,.2f", value);
        }
        if (value >= 1.0) {
            return String.format("%.5f", value);
        }
        return String.format("%.8f", value);
    }

    private String formatCompact(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return "N/A";
        }
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0) {
            return String.format("%.2fT", value / 1_000_000_000_000.0);
        }
        if (abs >= 1_000_000_000.0) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000.0) {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        if (abs >= 1_000.0) {
            return String.format("%.2fK", value / 1_000.0);
        }
        return String.format("%.2f", value);
    }

    private double safeDouble(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private double firstPositive(double... values) {
        if (values == null) {
            return 0.0;
        }
        for (double value : values) {
            if (Double.isFinite(value) && value > 0.0) {
                return value;
            }
        }
        return 0.0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private Color colorFromWeb(String color, Color fallback) {
        if (color == null || color.isBlank()) {
            return fallback;
        }
        try {
            return Color.web(color);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Color getVolatilityColor(String level) {
        return switch (safe(level).toUpperCase(Locale.ROOT)) {
            case "LOW" -> GREEN;
            case "NORMAL" -> BLUE;
            case "HIGH" -> AMBER;
            case "EXTREME" -> RED;
            default -> MUTED;
        };
    }

    private Color getTrendColor(String trend) {
        return switch (safe(trend).toUpperCase(Locale.ROOT)) {
            case "STRONG_UP" -> Color.web("#059669");
            case "UP" -> GREEN;
            case "SIDEWAYS" -> BLUE;
            case "DOWN" -> Color.web("#f97316");
            case "STRONG_DOWN" -> Color.web("#dc2626");
            default -> MUTED;
        };
    }

    private Color getTechnicalSignalColor(String signal) {
        return switch (safe(signal).toUpperCase(Locale.ROOT)) {
            case "STRONG_BUY" -> Color.web("#059669");
            case "BUY" -> GREEN;
            case "NEUTRAL" -> BLUE;
            case "SELL" -> Color.web("#f97316");
            case "STRONG_SELL" -> Color.web("#dc2626");
            default -> MUTED;
        };
    }

    private String safePairCode(CodeSupplier supplier) {
        try {
            return safe(supplier.get());
        } catch (Exception ignored) {
            return "";
        }
    }

    private void openLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                log.warn("Desktop browse is not supported. URL={}", url);
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception exception) {
            log.warn("Failed to open link: {}", url, exception);
        }
    }

    @FunctionalInterface
    private interface CodeSupplier {
        String get();
    }

    private enum AssetClass {
        CRYPTO,
        STOCK,
        ETF,
        FOREX,
        INDEX,
        COMMODITY,
        FUTURE,
        OPTION,
        CFD,
        UNKNOWN
    }
}
