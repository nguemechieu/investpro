package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Dark-themed panel showing AI reasoning, decisions, and confidence for
 * the most recently reviewed strategy or signal.
 *
 * <p>Subscribes to lifecycle events and updates in real time on the JavaFX
 * Application Thread. Intended to be embedded in a larger dashboard layout.</p>
 */
public class AIReasoningPanel extends VBox {

    private static final String DARK_BG = "-fx-background-color: #1a1a2e;";
    private static final String CARD_BG = "-fx-background-color: #16213e; -fx-background-radius: 8;";
    private static final String TEXT_WHITE = "-fx-text-fill: #e0e0e0;";
    private static final String TEXT_MUTED = "-fx-text-fill: #888888;";
    private static final String TEXT_GREEN = "-fx-text-fill: #00e676;";
    private static final String TEXT_RED = "-fx-text-fill: #ff5252;";
    private static final String TEXT_AMBER = "-fx-text-fill: #ffab40;";
    private static final String TEXT_BLUE = "-fx-text-fill: #40c4ff;";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Label statusLabel;
    private final Label confidenceLabel;
    private final Label decisionLabel;
    private final VBox reasoningBox;
    private final VBox signalBox;
    private final VBox healthBox;
    private final Label lastUpdatedLabel;

    private final List<String> eventLog = new ArrayList<>();
    private static final int MAX_LOG_ENTRIES = 50;

    /**
     * Constructs the AI Reasoning Panel and subscribes to lifecycle events.
     */
    public AIReasoningPanel() {
        setStyle(DARK_BG);
        setPadding(new Insets(16));
        setSpacing(12);

        // ---- Header ----
        Label titleLabel = new Label("AI Reasoning & Decisions");
        titleLabel.setStyle("-fx-text-fill: #40c4ff; -fx-font-size: 16px; -fx-font-weight: bold;");

        lastUpdatedLabel = new Label("Last updated: —");
        lastUpdatedLabel.setStyle(TEXT_MUTED + " -fx-font-size: 11px;");

        HBox header = new HBox(titleLabel);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        header.getChildren().add(lastUpdatedLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        // ---- Decision card ----
        VBox decisionCard = createCard("AI Decision");
        statusLabel = new Label("—");
        statusLabel.setStyle(TEXT_WHITE + " -fx-font-size: 14px; -fx-font-weight: bold;");
        confidenceLabel = new Label("Confidence: —");
        confidenceLabel.setStyle(TEXT_MUTED);
        decisionLabel = new Label("Decision: —");
        decisionLabel.setStyle(TEXT_BLUE);
        decisionCard.getChildren().addAll(statusLabel, confidenceLabel, decisionLabel);

        // ---- Reasoning details ----
        reasoningBox = createCard("Strategy Review Reasoning");

        // ---- Signal review ----
        signalBox = createCard("Signal Review");

        // ---- Health summary ----
        healthBox = createCard("Health Summary");

        // ---- Event log ----
        VBox eventLogBox = createCard("Recent AI Events");
        ScrollPane eventScroll = new ScrollPane(eventLogBox);
        eventScroll.setFitToWidth(true);
        eventScroll.setMaxHeight(200);
        eventScroll.setStyle(DARK_BG + " -fx-background: #1a1a2e;");

        getChildren().addAll(
                header,
                new Separator(),
                decisionCard,
                reasoningBox,
                signalBox,
                healthBox,
                new Label("Event Log") {{ setStyle(TEXT_MUTED); }},
                eventScroll
        );

        subscribeToEvents();
    }

    // =========================================================================
    // Event subscriptions
    // =========================================================================

    private void subscribeToEvents() {
        EventBusManager bus = EventBusManager.getInstance();

        bus.subscribe(AgentEvent.AI_STRATEGY_BACKTEST_REVIEWED, event -> {
            if (event.payload() instanceof AIStrategyReview review) {
                Platform.runLater(() -> updateStrategyReview(review));
            }
        });

        bus.subscribe(AgentEvent.AI_SIGNAL_REVIEWED, event -> {
            if (event.payload() instanceof AISignalReview review) {
                Platform.runLater(() -> updateSignalReview(review));
            }
        });

        bus.subscribe(AgentEvent.STRATEGY_HEALTH_CHANGED, event -> {
            if (event.payload() instanceof StrategyHealthReport report) {
                Platform.runLater(() -> updateHealthReport(report));
            }
        });

        bus.subscribe(AgentEvent.AI_REPLACEMENT_RECOMMENDED, event -> {
            if (event.payload() instanceof AIReplacementReport report) {
                Platform.runLater(() -> appendLog(
                        "REPLACEMENT RECOMMENDED: " + report.getAssignmentId()
                        + " — " + report.getReplacementReasoning()));
            }
        });

        bus.subscribe(AgentEvent.SIGNAL_APPROVED, event ->
                Platform.runLater(() -> appendLog("Signal APPROVED by AI: " + event.source())));

        bus.subscribe(AgentEvent.SIGNAL_REJECTED, event ->
                Platform.runLater(() -> appendLog("Signal REJECTED by AI: " + event.source())));
    }

    // =========================================================================
    // UI update methods
    // =========================================================================

    private void updateStrategyReview(AIStrategyReview review) {
        statusLabel.setText(review.isApproved() ? "✓ APPROVED" : "✗ REJECTED");
        statusLabel.setStyle((review.isApproved() ? TEXT_GREEN : TEXT_RED)
                + " -fx-font-size: 14px; -fx-font-weight: bold;");
        confidenceLabel.setText(String.format("Confidence: %.0f%%", review.getAiConfidence() * 100));
        decisionLabel.setText("Decision: " + review.getDecision());

        reasoningBox.getChildren().clear();
        reasoningBox.getChildren().add(sectionTitle("Strategy Review Reasoning"));
        for (String reason : review.getReasoningPoints()) {
            reasoningBox.getChildren().add(bulletLabel(reason, TEXT_WHITE));
        }
        if (!review.getWarnings().isEmpty()) {
            reasoningBox.getChildren().add(sectionTitle("Warnings"));
            for (String w : review.getWarnings()) {
                reasoningBox.getChildren().add(bulletLabel(w, TEXT_AMBER));
            }
        }
        touchUpdated();
    }

    private void updateSignalReview(AISignalReview review) {
        signalBox.getChildren().clear();
        signalBox.getChildren().add(sectionTitle("Signal Review"));
        signalBox.getChildren().add(kv("Approved", review.isApproved() ? "YES" : "NO",
                review.isApproved() ? TEXT_GREEN : TEXT_RED));
        signalBox.getChildren().add(kv("Confidence",
                String.format("%.0f%%", review.getConfidence() * 100), TEXT_BLUE));
        signalBox.getChildren().add(kv("Decision", review.getDecision().name(), TEXT_WHITE));
        if (review.getReasoningSummary() != null && !review.getReasoningSummary().isBlank()) {
            signalBox.getChildren().add(bulletLabel(review.getReasoningSummary(), TEXT_MUTED));
        }
        touchUpdated();
    }

    private void updateHealthReport(StrategyHealthReport report) {
        healthBox.getChildren().clear();
        healthBox.getChildren().add(sectionTitle("Health Summary"));
        healthBox.getChildren().add(kv("Health Level",
                report.getHealthLevel().name(), healthLevelStyle(report.getHealthLevel())));
        healthBox.getChildren().add(kv("Health Score",
                String.format("%.1f / 100", report.getCompositeHealthScore()), TEXT_BLUE));
        healthBox.getChildren().add(kv("Win Rate",
                String.format("%.1f%%", report.getWinRate() * 100), TEXT_WHITE));
        healthBox.getChildren().add(kv("Max Drawdown",
                String.format("%.1f%%", report.getMaxDrawdown() * 100), TEXT_AMBER));
        touchUpdated();
    }

    private void appendLog(String message) {
        eventLog.add(0, FORMATTER.format(java.time.Instant.now()) + "  " + message);
        if (eventLog.size() > MAX_LOG_ENTRIES) eventLog.subList(MAX_LOG_ENTRIES, eventLog.size()).clear();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private VBox createCard(String title) {
        VBox card = new VBox(6);
        card.setStyle(CARD_BG);
        card.setPadding(new Insets(12));
        if (title != null) card.getChildren().add(sectionTitle(title));
        return card;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #90caf9; -fx-font-weight: bold; -fx-font-size: 12px;");
        return l;
    }

    private Label bulletLabel(String text, String style) {
        Label l = new Label("• " + text);
        l.setStyle(style + " -fx-font-size: 12px;");
        l.setWrapText(true);
        return l;
    }

    private HBox kv(String key, String value, String valueStyle) {
        Label k = new Label(key + ": ");
        k.setStyle(TEXT_MUTED + " -fx-font-size: 12px;");
        Label v = new Label(value);
        v.setStyle(valueStyle + " -fx-font-size: 12px; -fx-font-weight: bold;");
        HBox row = new HBox(k, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String healthLevelStyle(StrategyHealthLevel level) {
        return switch (level) {
            case EXCELLENT, VERY_GOOD -> TEXT_GREEN;
            case GOOD -> TEXT_BLUE;
            case FAIR -> TEXT_AMBER;
            case POOR, CRITICAL, FAILING -> TEXT_RED;
            default -> TEXT_WHITE;
        };
    }

    private void touchUpdated() {
        lastUpdatedLabel.setText("Last updated: " + FORMATTER.format(java.time.Instant.now()));
    }
}
