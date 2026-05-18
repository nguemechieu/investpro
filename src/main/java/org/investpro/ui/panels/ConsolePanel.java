package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight detached console panel used by TradingDesk when the embedded
 * console is unavailable.
 */
@Slf4j
public class ConsolePanel extends BorderPane {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final TextArea outputArea = new TextArea();

    public ConsolePanel() {
        initializeUi();
    }

    private void initializeUi() {
        setPadding(new Insets(12));
        setStyle("-fx-background-color: linear-gradient(to bottom, #0f172a, #111827);");

        Label title = new Label("System Console");
        title.setStyle("-fx-text-fill: #e5edf7; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitle = new Label("Detached runtime log and operator messages");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        VBox header = new VBox(3, title, subtitle, new Separator());
        header.setPadding(new Insets(0, 0, 10, 0));
        setTop(header);

        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setStyle(
                "-fx-control-inner-background: #0b1220;"
                        + "-fx-font-family: 'Consolas', 'Monaco', monospace;"
                        + "-fx-font-size: 12px;"
                        + "-fx-text-fill: #dbeafe;");
        setCenter(outputArea);

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(event -> outputArea.clear());

        Button copyButton = new Button("Copy All");
        copyButton.setOnAction(event -> {
            outputArea.selectAll();
            outputArea.copy();
            outputArea.deselect();
        });

        HBox footer = new HBox(8, clearButton, copyButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 0, 0, 0));
        HBox.setHgrow(clearButton, Priority.NEVER);
        HBox.setHgrow(copyButton, Priority.NEVER);
        setBottom(footer);
    }

    public void info(String message) {
        append("INFO", message);
    }

    public void warn(String message) {
        append("WARN", message);
    }

    public void error(String message) {
        append("ERROR", message);
    }

    public void debug(String message) {
        append("DEBUG", message);
    }

    public void appendLine(String message) {
        append("INFO", message);
    }

    public void clear() {
        outputArea.clear();
    }

    private void append(String level, String message) {
        String safeMessage = message == null ? "" : message;
        String line = "[%s] [%s] %s%n".formatted(TIME_FORMATTER.format(Instant.now()), level, safeMessage);

        if (Platform.isFxApplicationThread()) {
            outputArea.appendText(line);
            outputArea.setScrollTop(Double.MAX_VALUE);
        } else {
            Platform.runLater(() -> {
                outputArea.appendText(line);
                outputArea.setScrollTop(Double.MAX_VALUE);
            });
        }

        log.info("{}", safeMessage);
    }
}
