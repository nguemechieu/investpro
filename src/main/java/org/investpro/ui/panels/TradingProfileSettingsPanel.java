package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.CapitalProtection;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.ProbabilityLevel;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.RiskProfile;
import org.investpro.enums.SystemDesign;

/**
 * Trading Profile Settings Panel - User trading preferences and risk parameters
 */
@Slf4j
public class TradingProfileSettingsPanel extends VBox {

    private TextField traderNameField;
    private ComboBox<RiskProfile> riskLevelCombo;
    private ComboBox<String> tradingStyleCombo;
    private Spinner<Double> dailyLossLimitSpinner;
    private Spinner<Double> maxPositionSizeSpinner;
    private Spinner<Integer> maxOpenPositionsSpinner;
    private CheckBox enableAutoTradingCheckbox;
    private CheckBox enableAdvancedOrdersCheckbox;
    private ComboBox<MarketBehavior> marketBehaviorCombo;
    private ComboBox<ExecutionStrategy> executionStrategyCombo;
    private ComboBox<LiquidityProfile> liquidityProfileCombo;
    private ComboBox<PsychologyProfile> psychologyProfileCombo;
    private ComboBox<ProbabilityLevel> probabilityLevelCombo;
    private ComboBox<CapitalProtection> capitalProtectionCombo;
    private ComboBox<SystemDesign> systemDesignCombo;
    private TextArea profileDescriptionArea;
    private ComboBox<String> streamingModeCombo;
    private ComboBox<String> telegramStreamingModeCombo;
    private CheckBox drawNewsEventsCheckbox;
    private CheckBox tradeOnNewsEventsCheckbox;
    private Spinner<Integer> newsFilterMinutesSpinner;
    private PasswordField openaiApiKeyField;
    private ComboBox<String> openaiModelCombo;
    private Spinner<Double> openaiTemperatureSpinner;
    private CheckBox enableOpenaiIntegrationCheckbox;

    public TradingProfileSettingsPanel() {
        this.setStyle("-fx-background-color: #1a1a2e; -fx-padding: 16;");
        this.setSpacing(12);

        // Title
        Label titleLabel = new Label("Trading Profile Settings");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Profile Information Section
        VBox profileSection = createProfileSection();

        // Risk Management Section
        VBox riskSection = createRiskManagementSection();

        // Trading Preferences Section
        VBox preferencesSection = createTradingPreferencesSection();

        // Streaming Settings Section
        VBox streamingSection = createStreamingSettingsSection();

        // News Event Settings Section
        VBox newsSection = createNewsEventSettingsSection();

        // OpenAI Settings Section
        VBox openaiSection = createOpenAISettingsSection();

        // Profile Description
        VBox descriptionSection = createProfileDescriptionSection();

        // Buttons
        HBox buttonBox = createButtonBox();

        this.getChildren().addAll(
                titleLabel,
                new Separator(),
                profileSection,
                riskSection,
                preferencesSection,
                streamingSection,
                newsSection,
                openaiSection,
                descriptionSection,
                new Separator(),
                buttonBox);

        VBox.setVgrow(descriptionSection, Priority.ALWAYS);
    }

    private VBox createProfileSection() {
        Label sectionTitle = new Label("Profile Information");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        traderNameField = new TextField();
        traderNameField.setPromptText("Enter your trader name");
        traderNameField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-padding: 6;");

        Label nameLabel = new Label("Trader Name:");
        nameLabel.setStyle("-fx-text-fill: #a0aec0;");

        grid.addRow(0, nameLabel, traderNameField);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createRiskManagementSection() {
        Label sectionTitle = new Label("Risk Management");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        // Daily Loss Limit
        dailyLossLimitSpinner = new Spinner<>(0.0, 100000.0, 1000.0, 100.0);
        dailyLossLimitSpinner.setEditable(true);
        dailyLossLimitSpinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label lossLimitLabel = new Label("Daily Loss Limit ($):");
        lossLimitLabel.setStyle("-fx-text-fill: #a0aec0;");

        // Max Position Size
        maxPositionSizeSpinner = new Spinner<>(0.0, 1000000.0, 5000.0, 500.0);
        maxPositionSizeSpinner.setEditable(true);
        maxPositionSizeSpinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label positionSizeLabel = new Label("Max Position Size ($):");
        positionSizeLabel.setStyle("-fx-text-fill: #a0aec0;");

        // Max Open Positions
        maxOpenPositionsSpinner = new Spinner<>(1, 100, 10, 1);
        maxOpenPositionsSpinner.setEditable(true);
        maxOpenPositionsSpinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label openPosLabel = new Label("Max Open Positions:");
        openPosLabel.setStyle("-fx-text-fill: #a0aec0;");

        grid.addRow(0, lossLimitLabel, dailyLossLimitSpinner);
        grid.addRow(1, positionSizeLabel, maxPositionSizeSpinner);
        grid.addRow(2, openPosLabel, maxOpenPositionsSpinner);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createTradingPreferencesSection() {
        Label sectionTitle = new Label("Trading Preferences");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        // Risk Level
        riskLevelCombo = new ComboBox<>();
        riskLevelCombo.getItems().addAll(RiskProfile.values());
        riskLevelCombo.setValue(RiskProfile.MODERATE);
        riskLevelCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label riskLabel = new Label("Risk Level:");
        riskLabel.setStyle("-fx-text-fill: #a0aec0;");

        // Trading Style
        tradingStyleCombo = new ComboBox<>();
        tradingStyleCombo.getItems().addAll("Day Trading", "Swing Trading", "Scalping", "Position Trading");
        tradingStyleCombo.setValue("Swing Trading");
        tradingStyleCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label styleLabel = new Label("Trading Style:");
        styleLabel.setStyle("-fx-text-fill: #a0aec0;");

        // Enable Auto Trading
        enableAutoTradingCheckbox = new CheckBox("Enable Automated Trading");
        enableAutoTradingCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        // Enable Advanced Orders
        enableAdvancedOrdersCheckbox = new CheckBox("Enable Advanced Order Types");
        enableAdvancedOrdersCheckbox.setStyle("-fx-text-fill: #a0aec0;");

        grid.addRow(0, riskLabel, riskLevelCombo);
        grid.addRow(1, styleLabel, tradingStyleCombo);
        marketBehaviorCombo = new ComboBox<>();
        marketBehaviorCombo.getItems().addAll(MarketBehavior.values());
        marketBehaviorCombo.setValue(MarketBehavior.TRENDING_UP);

        executionStrategyCombo = new ComboBox<>();
        executionStrategyCombo.getItems().addAll(ExecutionStrategy.values());
        executionStrategyCombo.setValue(ExecutionStrategy.LIMIT_ORDER);

        liquidityProfileCombo = new ComboBox<>();
        liquidityProfileCombo.getItems().addAll(LiquidityProfile.values());
        liquidityProfileCombo.setValue(LiquidityProfile.NORMAL);

        psychologyProfileCombo = new ComboBox<>();
        psychologyProfileCombo.getItems().addAll(PsychologyProfile.values());
        psychologyProfileCombo.setValue(PsychologyProfile.DISCIPLINED);

        probabilityLevelCombo = new ComboBox<>();
        probabilityLevelCombo.getItems().addAll(ProbabilityLevel.values());
        probabilityLevelCombo.setValue(ProbabilityLevel.HIGH);

        capitalProtectionCombo = new ComboBox<>();
        capitalProtectionCombo.getItems().addAll(CapitalProtection.values());
        capitalProtectionCombo.setValue(CapitalProtection.STRICT_STOPS);

        systemDesignCombo = new ComboBox<>();
        systemDesignCombo.getItems().addAll(SystemDesign.values());
        systemDesignCombo.setValue(SystemDesign.HYBRID_SYSTEM);

        grid.addRow(2, new Label("Market Behavior:"), marketBehaviorCombo);
        grid.addRow(3, new Label("Execution Strategy:"), executionStrategyCombo);
        grid.addRow(4, new Label("Liquidity:"), liquidityProfileCombo);
        grid.addRow(5, new Label("Psychology:"), psychologyProfileCombo);
        grid.addRow(6, new Label("Probability:"), probabilityLevelCombo);
        grid.addRow(7, new Label("Capital Protection:"), capitalProtectionCombo);
        grid.addRow(8, new Label("System Design:"), systemDesignCombo);
        grid.addRow(9, enableAutoTradingCheckbox);
        grid.addRow(10, enableAdvancedOrdersCheckbox);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createStreamingSettingsSection() {
        Label sectionTitle = new Label("Streaming Settings");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #8b5cf6;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        // Streaming Mode
        streamingModeCombo = new ComboBox<>();
        streamingModeCombo.getItems().addAll("POLLING", "WEBSOCKET", "HYBRID");
        streamingModeCombo.setValue("WEBSOCKET");
        streamingModeCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label streamingLabel = new Label("Streaming Mode:");
        streamingLabel.setStyle("-fx-text-fill: #a0aec0;");
        streamingLabel
                .setTooltip(new Tooltip("POLLING: Regular updates | WEBSOCKET: Real-time | HYBRID: Fallback support"));

        // Telegram Streaming Mode
        telegramStreamingModeCombo = new ComboBox<>();
        telegramStreamingModeCombo.getItems().addAll(
                "DISABLED",
                "TEXT_ONLY",
                "TEXT_WITH_CHARTS",
                "REAL_TIME_ALERTS",
                "SUMMARY_UPDATES");
        telegramStreamingModeCombo.setValue("REAL_TIME_ALERTS");
        telegramStreamingModeCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label telegramLabel = new Label("Telegram Streaming:");
        telegramLabel.setStyle("-fx-text-fill: #a0aec0;");
        telegramLabel.setTooltip(new Tooltip(
                "DISABLED: No Telegram updates | TEXT_ONLY: Messages only | TEXT_WITH_CHARTS: Include chart images | REAL_TIME_ALERTS: Instant notifications | SUMMARY_UPDATES: Periodic summaries"));

        grid.addRow(0, streamingLabel, streamingModeCombo);
        grid.addRow(1, telegramLabel, telegramStreamingModeCombo);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createNewsEventSettingsSection() {
        Label sectionTitle = new Label("News Event Settings");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f97316;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        // Draw News Events on Chart
        drawNewsEventsCheckbox = new CheckBox("Draw News Events on Chart");
        drawNewsEventsCheckbox.setSelected(true);
        drawNewsEventsCheckbox.setStyle("-fx-text-fill: #a0aec0;");
        drawNewsEventsCheckbox.setTooltip(new Tooltip(
                "Display economic events and news markers on candlestick chart for visual reference"));

        // Trade Based on News Events
        tradeOnNewsEventsCheckbox = new CheckBox("Generate Trading Signals from News Events");
        tradeOnNewsEventsCheckbox.setSelected(false);
        tradeOnNewsEventsCheckbox.setStyle("-fx-text-fill: #a0aec0;");
        tradeOnNewsEventsCheckbox.setTooltip(new Tooltip(
                "Automatically generate buy/sell signals based on news sentiment and importance"));

        // News Event Filter
        newsFilterMinutesSpinner = new Spinner<>(0, 180, 30, 5);
        newsFilterMinutesSpinner.setEditable(true);
        newsFilterMinutesSpinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label filterLabel = new Label("News Filter Window (minutes):");
        filterLabel.setStyle("-fx-text-fill: #a0aec0;");
        filterLabel.setTooltip(new Tooltip(
                "Avoid trading within this many minutes BEFORE and AFTER news events (default: 30 min before/after)"));

        HBox filterBox = new HBox(8, filterLabel, newsFilterMinutesSpinner);
        filterBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        grid.addRow(0, drawNewsEventsCheckbox);
        grid.addRow(1, tradeOnNewsEventsCheckbox);
        grid.addRow(2, filterBox);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createOpenAISettingsSection() {
        Label sectionTitle = new Label("OpenAI Configuration");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #06b6d4;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 4;");

        // Enable OpenAI Integration
        enableOpenaiIntegrationCheckbox = new CheckBox("Enable OpenAI Integration");
        enableOpenaiIntegrationCheckbox.setSelected(false);
        enableOpenaiIntegrationCheckbox.setStyle("-fx-text-fill: #a0aec0;");
        enableOpenaiIntegrationCheckbox.setTooltip(new Tooltip(
                "Enable AI-powered trading analysis and signal generation using OpenAI models"));

        // OpenAI API Key
        openaiApiKeyField = new PasswordField();
        openaiApiKeyField.setPromptText("Enter your OpenAI API key");
        openaiApiKeyField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-padding: 6;");

        Label apiKeyLabel = new Label("API Key:");
        apiKeyLabel.setStyle("-fx-text-fill: #a0aec0;");
        apiKeyLabel.setTooltip(new Tooltip("Your OpenAI API key (kept secure)"));

        // OpenAI Model Selection
        openaiModelCombo = new ComboBox<>();
        openaiModelCombo.getItems().addAll(
                "gpt-4",
                "gpt-4-turbo",
                "gpt-3.5-turbo",
                "gpt-4-vision");
        openaiModelCombo.setValue("gpt-3.5-turbo");
        openaiModelCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label modelLabel = new Label("Model:");
        modelLabel.setStyle("-fx-text-fill: #a0aec0;");
        modelLabel.setTooltip(new Tooltip(
                "Select OpenAI model for trading analysis (gpt-4 recommended for accuracy)"));

        // Temperature Setting
        openaiTemperatureSpinner = new Spinner<>(0.0, 2.0, 0.7, 0.1);
        openaiTemperatureSpinner.setEditable(true);
        openaiTemperatureSpinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label temperatureLabel = new Label("Temperature:");
        temperatureLabel.setStyle("-fx-text-fill: #a0aec0;");
        temperatureLabel.setTooltip(new Tooltip(
                "Control randomness/creativity: 0.0=deterministic, 0.7=balanced, 2.0=creative"));

        grid.addRow(0, enableOpenaiIntegrationCheckbox);
        grid.addRow(1, apiKeyLabel, openaiApiKeyField);
        grid.addRow(2, modelLabel, openaiModelCombo);
        grid.addRow(3, temperatureLabel, openaiTemperatureSpinner);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createProfileDescriptionSection() {
        Label sectionTitle = new Label("Profile Description & Goals");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #ec4899;");

        profileDescriptionArea = new TextArea();
        profileDescriptionArea.setPromptText("Enter your trading goals, strategy notes, and profile description...");
        profileDescriptionArea.setWrapText(true);
        profileDescriptionArea.setStyle(
                "-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 11; -fx-padding: 8;");

        VBox box = new VBox(8, sectionTitle, profileDescriptionArea);
        VBox.setVgrow(profileDescriptionArea, Priority.ALWAYS);
        return box;
    }

    private HBox createButtonBox() {
        Button saveButton = new Button("Save Profile");
        saveButton.setStyle(
                "-fx-padding: 8 24; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 12; -fx-cursor: hand;");
        saveButton.setOnAction(e -> saveProfile());

        Button resetButton = new Button("Reset to Defaults");
        resetButton.setStyle(
                "-fx-padding: 8 24; -fx-background-color: #6366f1; -fx-text-fill: white; -fx-font-size: 12; -fx-cursor: hand;");
        resetButton.setOnAction(e -> resetProfile());

        Button closeButton = new Button("Close");
        closeButton.setStyle(
                "-fx-padding: 8 24; -fx-background-color: #374151; -fx-text-fill: white; -fx-font-size: 12; -fx-cursor: hand;");
        closeButton.setOnAction(e -> closeProfilePanel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, spacer, saveButton, resetButton, closeButton);
    }

    private void saveProfile() {
        String traderName = traderNameField.getText();
        TradingProfile profile = new TradingProfile(
                traderNameField.getText(),
                riskLevelCombo.getValue(),
                tradingStyleCombo.getValue(),
                dailyLossLimitSpinner.getValue(),
                maxPositionSizeSpinner.getValue(),
                maxOpenPositionsSpinner.getValue(),
                enableAutoTradingCheckbox.isSelected(),
                enableAdvancedOrdersCheckbox.isSelected(),
                marketBehaviorCombo.getValue(),
                executionStrategyCombo.getValue(),
                liquidityProfileCombo.getValue(),
                psychologyProfileCombo.getValue(),
                probabilityLevelCombo.getValue(),
                capitalProtectionCombo.getValue(),
                systemDesignCombo.getValue(),
                profileDescriptionArea.getText());
        profile.save();

        log.info(
                "Trading Profile Saved: trader={}, risk={}, style={}, dailyLoss={}, maxPos={}, maxOpen={}, autoTrade={}, advanced={}",
                profile.traderName(), profile.riskProfile(), profile.tradingStyle(), profile.dailyLossLimit(),
                profile.maxPositionSize(), profile.maxOpenPositions(), profile.autoTradingEnabled(),
                profile.advancedOrdersEnabled());
    }

    private void resetProfile() {
        TradingProfile.reset();
        applyProfile(TradingProfile.defaults());

        log.info("Trading Profile reset to defaults");
    }

    private void closeProfilePanel() {
        // Get the parent Stage and close it, or remove from parent container
        javafx.scene.Node node = this;
        while (node != null) {
            javafx.scene.Parent parent = node.getParent();
            if (parent instanceof javafx.scene.layout.VBox || parent instanceof javafx.scene.layout.HBox) {
                ((javafx.scene.layout.Pane) parent).getChildren().remove(node);
                log.info("Trading Profile Settings panel closed");
                return;
            }
            node = parent;
        }
        log.info("Trading Profile Settings panel closed");
    }

    public void loadProfile() {
        applyProfile(TradingProfile.load());
        log.info("Loading trading profile...");
    }

    private void applyProfile(TradingProfile profile) {
        traderNameField.setText(profile.traderName());
        riskLevelCombo.setValue(profile.riskProfile());
        tradingStyleCombo.setValue(profile.tradingStyle());
        dailyLossLimitSpinner.getValueFactory().setValue(profile.dailyLossLimit());
        maxPositionSizeSpinner.getValueFactory().setValue(profile.maxPositionSize());
        maxOpenPositionsSpinner.getValueFactory().setValue(profile.maxOpenPositions());
        enableAutoTradingCheckbox.setSelected(profile.autoTradingEnabled());
        enableAdvancedOrdersCheckbox.setSelected(profile.advancedOrdersEnabled());
        marketBehaviorCombo.setValue(profile.marketBehavior());
        executionStrategyCombo.setValue(profile.executionStrategy());
        liquidityProfileCombo.setValue(profile.liquidityProfile());
        psychologyProfileCombo.setValue(profile.psychologyProfile());
        probabilityLevelCombo.setValue(profile.probabilityLevel());
        capitalProtectionCombo.setValue(profile.capitalProtection());
        systemDesignCombo.setValue(profile.systemDesign());
        profileDescriptionArea.setText(profile.description());
    }

    // Getter methods for streaming settings
    public String getStreamingMode() {
        return streamingModeCombo.getValue() != null ? streamingModeCombo.getValue() : "WEBSOCKET";
    }

    public String getTelegramStreamingMode() {
        return telegramStreamingModeCombo.getValue() != null ? telegramStreamingModeCombo.getValue()
                : "REAL_TIME_ALERTS";
    }

    public void setStreamingMode(String mode) {
        if (mode != null && streamingModeCombo.getItems().contains(mode)) {
            streamingModeCombo.setValue(mode);
        }
    }

    public void setTelegramStreamingMode(String mode) {
        if (mode != null && telegramStreamingModeCombo.getItems().contains(mode)) {
            telegramStreamingModeCombo.setValue(mode);
        }
    }

    // Getter methods for news event settings
    public boolean isDrawNewsEventsEnabled() {
        return drawNewsEventsCheckbox.isSelected();
    }

    public boolean isTradeOnNewsEventsEnabled() {
        return tradeOnNewsEventsCheckbox.isSelected();
    }

    public int getNewsFilterMinutes() {
        Integer value = newsFilterMinutesSpinner.getValue();
        return value != null ? value : 30;
    }

    // Setter methods for news event settings
    public void setDrawNewsEvents(boolean enabled) {
        drawNewsEventsCheckbox.setSelected(enabled);
    }

    public void setTradeOnNewsEvents(boolean enabled) {
        tradeOnNewsEventsCheckbox.setSelected(enabled);
    }

    public void setNewsFilterMinutes(int minutes) {
        newsFilterMinutesSpinner.getValueFactory().setValue(Math.max(0, Math.min(180, minutes)));
    }

    // Getter methods for OpenAI settings
    public boolean isOpenaiIntegrationEnabled() {
        return enableOpenaiIntegrationCheckbox.isSelected();
    }

    public String getOpenaiApiKey() {
        return openaiApiKeyField.getText() != null ? openaiApiKeyField.getText() : "";
    }

    public String getOpenaiModel() {
        return openaiModelCombo.getValue() != null ? openaiModelCombo.getValue() : "gpt-3.5-turbo";
    }

    public double getOpenaiTemperature() {
        Double value = openaiTemperatureSpinner.getValue();
        return value != null ? value : 0.7;
    }

    // Setter methods for OpenAI settings
    public void setOpenaiIntegrationEnabled(boolean enabled) {
        enableOpenaiIntegrationCheckbox.setSelected(enabled);
    }

    public void setOpenaiApiKey(String apiKey) {
        if (apiKey != null) {
            openaiApiKeyField.setText(apiKey);
        }
    }

    public void setOpenaiModel(String model) {
        if (model != null && openaiModelCombo.getItems().contains(model)) {
            openaiModelCombo.setValue(model);
        }
    }

    public void setOpenaiTemperature(double temperature) {
        openaiTemperatureSpinner.getValueFactory().setValue(Math.max(0.0, Math.min(2.0, temperature)));
    }
}
