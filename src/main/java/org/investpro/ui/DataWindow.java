package org.investpro.ui;

import lombok.extern.slf4j.Slf4j;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.TradePair;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MT4-style Data Window.
 * Shows the currently selected symbol/candle information:
 * - symbol
 * - timeframe
 * - timestamp
 * - OHLCV
 * - custom indicator/key-value rows
 */
@Getter
@Setter
@Slf4j
public class DataWindow extends VBox {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Label titleLabel = new Label("Data Window");
    private final Label symbolLabel = new Label("Symbol: -");
    private final Label timeframeLabel = new Label("Timeframe: -");

    private final ObservableList<DataRow> rows = FXCollections.observableArrayList();
    private final TableView<DataRow> tableView = new TableView<>(rows);

    TradePair currentTradePair;
    private String currentTimeframe = "";

    public DataWindow() {
        configureLayout();
        configureTable();
        clearData();
    }

    private void configureLayout() {
        getStyleClass().add("data-window");
        setSpacing(8);
        setPadding(new Insets(8));
        setMinWidth(220);
        setPrefWidth(280);

        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        symbolLabel.setStyle("-fx-font-size: 12px;");
        timeframeLabel.setStyle("-fx-font-size: 12px;");

        getChildren().setAll(
                titleLabel,
                symbolLabel,
                timeframeLabel,
                tableView);

        VBox.setVgrow(tableView, Priority.ALWAYS);
    }

    private void configureTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tableView.setFocusTraversable(false);

        TableColumn<DataRow, String> nameColumn = new TableColumn<>("Field");
        nameColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue() == null ? "" : cell.getValue().name()));
        nameColumn.setPrefWidth(120);

        TableColumn<DataRow, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue() == null ? "" : cell.getValue().value()));
        valueColumn.setPrefWidth(140);

        tableView.getColumns().setAll(nameColumn, valueColumn);
    }

    public void setSymbol(TradePair tradePair) {
        runOnFx(() -> {
            this.currentTradePair = tradePair;
            symbolLabel.setText("Symbol: %s".formatted(symbolText(tradePair)));
        });
    }

    public void setTimeframe(String timeframe) {
        runOnFx(() -> {
            this.currentTimeframe = safe(timeframe);
            timeframeLabel.setText("Timeframe: %s".formatted(currentTimeframe.isBlank() ? "-" : currentTimeframe));
        });
    }

    public void updateCandle(
            TradePair tradePair,
            String timeframe,
            Instant timestamp,
            double open,
            double high,
            double low,
            double close,
            double volume) {
        runOnFx(() -> {
            this.currentTradePair = tradePair;
            this.currentTimeframe = safe(timeframe);

            symbolLabel.setText("Symbol: %s".formatted(symbolText(tradePair)));
            timeframeLabel.setText("Timeframe: %s".formatted(currentTimeframe.isBlank() ? "-" : currentTimeframe));

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("Time", timestamp == null ? "-" : TIME_FORMATTER.format(timestamp));
            values.put("Open", formatPrice(open));
            values.put("High", formatPrice(high));
            values.put("Low", formatPrice(low));
            values.put("Close", formatPrice(close));
            values.put("Volume", formatVolume(volume));

            setRows(values);
        });
    }

    public void updateQuote(
            TradePair tradePair,
            double bid,
            double ask,
            double last,
            Instant timestamp) {
        runOnFx(() -> {
            this.currentTradePair = tradePair;
            symbolLabel.setText("Symbol: " + symbolText(tradePair));

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("Time", timestamp == null ? "-" : TIME_FORMATTER.format(timestamp));
            values.put("Bid", formatPrice(bid));
            values.put("Ask", formatPrice(ask));
            values.put("Last", formatPrice(last));
            values.put("Spread", bid > 0 && ask > 0 ? formatPrice(Math.abs(ask - bid)) : "-");

            setRows(values);
        });
    }

    public void updateValues(Map<String, ?> values) {
        runOnFx(() -> setRows(values));
    }

    public void putValue(String name, Object value) {
        if (name == null || name.isBlank()) {
            return;
        }

        runOnFx(() -> {
            for (int i = 0; i < rows.size(); i++) {
                DataRow row = rows.get(i);

                if (Objects.equals(row.name(), name)) {
                    rows.set(i, new DataRow(name, stringify(value)));
                    return;
                }
            }

            rows.add(new DataRow(name, stringify(value)));
        });
    }

    public void clearData() {
        runOnFx(() -> {
            rows.clear();

            rows.add(new DataRow("Time", "-"));
            rows.add(new DataRow("Open", "-"));
            rows.add(new DataRow("High", "-"));
            rows.add(new DataRow("Low", "-"));
            rows.add(new DataRow("Close", "-"));
            rows.add(new DataRow("Volume", "-"));
        });
    }

    private void setRows(Map<String, ?> values) {
        rows.clear();

        if (values == null || values.isEmpty()) {
            clearData();
            return;
        }

        for (Map.Entry<String, ?> entry : values.entrySet()) {
            rows.add(new DataRow(
                    safe(entry.getKey()),
                    stringify(entry.getValue())));
        }
    }

    private String symbolText(TradePair tradePair) {
        if (tradePair == null) {
            return "-";
        }

        try {
            return tradePair.toString('/');
        } catch (Exception exception) {
            log.debug("Unable to format trade pair", exception);
            return String.valueOf(tradePair);
        }
    }

    private String formatPrice(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return "-";
        }

        return String.format("%.8f", value);
    }

    private String formatVolume(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return "-";
        }

        return String.format("%.4f", value);
    }

    private String stringify(Object value) {
        switch (value) {
            case null -> {
                return "-";
            }
            case Double number -> {
                return formatPrice(number);
            }
            case Float number -> {
                return formatPrice(number.doubleValue());
            }
            case Number number -> {
                return String.valueOf(number);
            }
            case Instant instant -> {
                return TIME_FORMATTER.format(instant);
            }
            default -> {
            }
        }

        String text = String.valueOf(value).trim();
        return text.isBlank() ? "-" : text;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void runOnFx(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    public record DataRow(String name, String value) {
    }
}