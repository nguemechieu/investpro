package org.investpro.ui.panels;

import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.management.StrategyAssignmentManager;

import java.util.List;

/**
 * Operational panel for startup assignment recovery actions.
 */
public class StartupRecoveryPanel extends BorderPane {

    private final StrategyAssignmentManager manager = StrategyAssignmentManager.getInstance();
    private final TableView<StrategyLifecycleRecord> table = new TableView<>();

    public StartupRecoveryPanel() {
        setCenter(table);
        setBottom(buildActions());
        configureColumns();
        refresh();
    }

    public void refresh() {
        List<StrategyLifecycleRecord> records = manager.getAllRecords();
        table.setItems(FXCollections.observableArrayList(records));
    }

    private void configureColumns() {
        TableColumn<StrategyLifecycleRecord, String> symbol = new TableColumn<>("Symbol");
        symbol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSymbol()));

        TableColumn<StrategyLifecycleRecord, String> strategy = new TableColumn<>("Strategy");
        strategy.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStrategyId()));

        TableColumn<StrategyLifecycleRecord, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getLifecycleStatus() == null ? "UNKNOWN"
                        : data.getValue().getLifecycleStatus().name()));

        table.getColumns().setAll(symbol, strategy, status);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private HBox buildActions() {
        Button resume = new Button("Resume");
        resume.setOnAction(event -> applyToSelection("resume"));

        Button pause = new Button("Pause");
        pause.setOnAction(event -> applyToSelection("pause"));

        Button archive = new Button("Archive");
        archive.setOnAction(event -> applyToSelection("archive"));

        Button reconcile = new Button("Reconcile Again");
        reconcile.setOnAction(event -> applyToSelection("reconcile"));

        Button review = new Button("Manual Review");
        review.setOnAction(event -> applyToSelection("review"));

        Button reevaluate = new Button("Re-evaluate Strategy");
        reevaluate.setOnAction(event -> applyToSelection("reevaluate"));

        Button closeViaRisk = new Button("Close Position via Risk Engine");
        closeViaRisk.setOnAction(event -> applyToSelection("close"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(8, resume, pause, archive, reconcile, review, reevaluate, closeViaRisk, spacer);
    }

    private void applyToSelection(String action) {
        StrategyLifecycleRecord record = table.getSelectionModel().getSelectedItem();
        if (record == null) {
            return;
        }

        switch (action) {
            case "resume" -> manager.resumeAssignment(record.getAssignmentId(), "Resumed from startup recovery panel");
            case "pause" -> manager.pause(record.getAssignmentId(), "Paused from startup recovery panel");
            case "archive" -> manager.archive(record.getAssignmentId(), "Archived from startup recovery panel");
            case "review" -> manager.markNeedsReview(record.getAssignmentId(),
                    "Manual review requested from startup recovery panel");
            case "reevaluate" -> manager.requestReevaluation(record.getAssignmentId(),
                    "Re-evaluation requested from startup recovery panel");
            case "reconcile" -> manager.markNeedsReview(record.getAssignmentId(),
                    "Manual reconcile retry requested from startup recovery panel");
            case "close" -> manager.pause(record.getAssignmentId(), "Position close requested via risk engine");
            default -> {
                return;
            }
        }

        refresh();
    }
}
