package org.investpro.investpro.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.FxLifecycle;
import org.investpro.investpro.models.Position;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class PositionsUI extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(PositionsUI.class);

    private final Exchange exchange;
    private final ScheduledExecutorService scheduler;
    private final List<Position> positionsList;
    private final ObservableList<PositionRow> tableRows;
    private final TableView<PositionRow> positionsTable;
    private final BarChart<String, Number> profitLossChart;
    private final LineChart<Number, Number> equityChart;
    private final Label riskInfoLabel;
    private final Label statusLabel;
    private final XYChart.Series<Number, Number> equitySeries;
    private final AtomicInteger timeCounter = new AtomicInteger(0);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public PositionsUI(Exchange exchange) {
        this.exchange = exchange;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.positionsList = new ArrayList<>();
        this.tableRows = FXCollections.observableArrayList();
        this.positionsTable = createPositionsTable();
        this.profitLossChart = createBarChart();
        this.equityChart = createLineChart();
        this.riskInfoLabel = new Label("Loading positions...");
        this.statusLabel = new Label("Waiting for first update...");
        this.equitySeries = new XYChart.Series<>();
        this.equitySeries.setName("Equity Trend");
        this.equityChart.getData().add(equitySeries);

        setSpacing(12);
        setPadding(new Insets(12));
        getStyleClass().add("desk-table-panel");

        Label titleLabel = new Label("Positions");
        titleLabel.getStyleClass().add("desk-section-title");
        statusLabel.getStyleClass().add("desk-section-status");
        riskInfoLabel.getStyleClass().add("desk-section-status");

        positionsTable.setItems(tableRows);

        VBox tablePane = new VBox(10, new Label("Open Exposure"), positionsTable);
        VBox.setVgrow(positionsTable, Priority.ALWAYS);

        VBox chartsPane = new VBox(12, profitLossChart, equityChart, riskInfoLabel);
        VBox.setVgrow(profitLossChart, Priority.ALWAYS);
        VBox.setVgrow(equityChart, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(tablePane, chartsPane);
        splitPane.setDividerPositions(0.5);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        getChildren().addAll(titleLabel, statusLabel, splitPane);

        startUpdating();
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                shutdown();
            }
        });
        logger.info("Positions UI initialized.");
    }

    private TableView<PositionRow> createPositionsTable() {
        TableView<PositionRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No positions available"));
        table.getColumns().addAll(
                textColumn("Instrument", PositionRow::instrument),
                textColumn("Side", PositionRow::side),
                textColumn("Units", row -> Integer.toString(row.units())),
                textColumn("P/L", row -> String.format("$%,.2f", row.profitLoss())),
                textColumn("Unrealized", row -> String.format("$%,.2f", row.unrealized())),
                textColumn("Financing", row -> String.format("$%,.2f", row.financing()))
        );
        return table;
    }

    private TableColumn<PositionRow, String> textColumn(String title, java.util.function.Function<PositionRow, String> mapper) {
        TableColumn<PositionRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        return column;
    }

    private @NotNull BarChart<String, Number> createBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Instrument");
        yAxis.setLabel("Profit/Loss ($)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("P/L by Instrument");
        chart.setAnimated(false);
        return chart;
    }

    private @NotNull LineChart<Number, Number> createLineChart() {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Update");
        yAxis.setLabel("Equity ($)");
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Equity Trend");
        chart.setAnimated(false);
        return chart;
    }

    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            if (disposed.get() || !FxLifecycle.isShowing(this)) {
                return;
            }
            try {
                List<Position> updatedPositions = exchange.getPositions();

                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this), () -> {
                    positionsList.clear();
                    positionsList.addAll(updatedPositions);
                    tableRows.setAll(toRows(updatedPositions));
                    updateProfitLossChart();
                    updateEquityChart();
                    updateRiskSummary();
                    statusLabel.setText(updatedPositions.isEmpty()
                            ? "No positions returned by " + exchange.getClass().getSimpleName() + "."
                            : "Updated " + java.time.LocalTime.now().withNano(0));
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Position refresh interrupted for {}", exchange.getClass().getSimpleName(), e);
            } catch (IOException | ExecutionException | RuntimeException e) {
                logger.warn("Unable to refresh positions for {}", exchange.getClass().getSimpleName(), e);
                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this), () -> {
                    statusLabel.setText("Positions unavailable right now.");
                    riskInfoLabel.setText("Positions unavailable for " + exchange.getClass().getSimpleName() + " right now.");
                });
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private List<PositionRow> toRows(List<Position> positions) {
        List<PositionRow> rows = new ArrayList<>();
        for (Position position : positions) {
            if (hasLongExposure(position)) {
                Position.SubPosition subPosition = position.getLongPosition();
                rows.add(new PositionRow(
                        instrumentName(position),
                        "Long",
                        Math.abs(subPosition.getUnits()),
                        subPosition.getPl(),
                        subPosition.getUnrealizedPL(),
                        subPosition.getFinancing()
                ));
            }
            if (hasShortExposure(position)) {
                Position.SubPosition subPosition = position.getShortPosition();
                rows.add(new PositionRow(
                        instrumentName(position),
                        "Short",
                        Math.abs(subPosition.getUnits()),
                        subPosition.getPl(),
                        subPosition.getUnrealizedPL(),
                        subPosition.getFinancing()
                ));
            }
        }
        return rows;
    }

    private void updateProfitLossChart() {
        profitLossChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Profit/Loss");

        for (PositionRow row : tableRows) {
            series.getData().add(new XYChart.Data<>(row.instrument() + " " + row.side(), row.profitLoss()));
        }

        profitLossChart.getData().add(series);
    }

    private void updateEquityChart() {
        double totalEquity = positionsList.stream().mapToDouble(Position::getValue).sum();
        equitySeries.getData().add(new XYChart.Data<>(timeCounter.incrementAndGet(), totalEquity));

        if (equitySeries.getData().size() > 100) {
            equitySeries.getData().removeFirst();
        }
    }

    private void updateRiskSummary() {
        if (positionsList.isEmpty()) {
            riskInfoLabel.setText("No open positions returned by " + exchange.getClass().getSimpleName() + ".");
            return;
        }

        double totalPl = positionsList.stream().mapToDouble(Position::getProfitOrLoss).sum();
        long longPositions = positionsList.stream().filter(this::hasLongExposure).count();
        long shortPositions = positionsList.stream().filter(this::hasShortExposure).count();
        riskInfoLabel.setText(String.format(
                "Open positions: %d | Long: %d | Short: %d | Net P/L: $%.2f",
                positionsList.size(),
                longPositions,
                shortPositions,
                totalPl
        ));
    }

    private String instrumentName(Position position) {
        return position.getInstrument() == null || position.getInstrument().isBlank()
                ? "Unknown"
                : position.getInstrument();
    }

    private boolean hasLongExposure(Position position) {
        return position.getLongPosition() != null && Math.abs(position.getLongPosition().getUnits()) > 0;
    }

    private boolean hasShortExposure(Position position) {
        return position.getShortPosition() != null && Math.abs(position.getShortPosition().getUnits()) > 0;
    }

    public void shutdown() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
    }

    private record PositionRow(
            String instrument,
            String side,
            int units,
            double profitLoss,
            double unrealized,
            double financing
    ) {
    }
}
