package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.investpro.backtesting.InstitutionalBacktestMetrics;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BacktestReportPanelTest {
    @Test
    void refreshPreservesSectionsRejectsNullAndAlignsRiskValues() throws Exception {
        onFxThread(() -> {
            BacktestReportPanel panel = new BacktestReportPanel();
            panel.displayReport(metrics(100, -20));
            VBox content = (VBox) panel.getContent();
            assertEquals(7, content.getChildren().size());
            var sections = List.copyOf(content.getChildren());
            assertThrows(NullPointerException.class, () -> panel.displayReport(null));
            assertEquals(sections, content.getChildren());

            panel.displayReport(metrics(200, -40));
            assertEquals(7, content.getChildren().size());
            assertNotSame(sections.getFirst(), content.getChildren().getFirst());
            GridPane risk = grid(content, 3);
            assertEquals(12, risk.getChildren().size());
            for (int row = 0; row < 6; row++) {
                assertEquals(row, GridPane.getRowIndex(risk.getChildren().get(row * 2)));
                assertEquals(row, GridPane.getRowIndex(risk.getChildren().get(row * 2 + 1)));
                assertEquals(0, GridPane.getColumnIndex(risk.getChildren().get(row * 2)));
                assertEquals(1, GridPane.getColumnIndex(risk.getChildren().get(row * 2 + 1)));
            }
        });
    }

    @Test
    void confidenceProgressStaysWithinItsDeterminateRange() throws Exception {
        onFxThread(() -> {
            for (double score : new double[]{-50, 60, 150}) {
                InstitutionalBacktestMetrics metrics = new InstitutionalBacktestMetrics(List.of(), 1000) {
                    @Override
                    public double getConfidenceScore() {
                        return score;
                    }
                };
                BacktestReportPanel panel = new BacktestReportPanel();
                panel.displayReport(metrics);
                VBox confidence = (VBox) ((VBox) panel.getContent()).getChildren().get(5);
                ProgressBar progress = (ProgressBar) confidence.getChildren().get(1);
                assertEquals(score < 0 ? 0 : score > 100 ? 1 : 0.6, progress.getProgress());
            }
        });
    }

    @Test
    void signedTailLossesAreRedAndPositiveTailProfitsAreGreen() throws Exception {
        double[] profits = new double[40];
        java.util.Arrays.fill(profits, 5);
        profits[0] = -20;
        profits[1] = -10;
        InstitutionalBacktestMetrics losses = metrics(profits);
        assertEquals(-10, losses.getVar95());
        assertEquals(-15, losses.getCvar95());
        InstitutionalBacktestMetrics gains = metrics(10, 20);
        assertEquals(10, gains.getVar95());
        assertEquals(10, gains.getCvar95());
        onFxThread(() -> {
            BacktestReportPanel panel = new BacktestReportPanel();
            for (InstitutionalBacktestMetrics report : List.of(losses, gains)) {
                panel.displayReport(report);
                GridPane advanced = grid((VBox) panel.getContent(), 4);
                Color expected = Color.web(report == losses ? "#ef4444" : "#10b981");
                assertEquals(expected, ((Label) advanced.getChildren().get(11)).getTextFill());
                assertEquals(expected, ((Label) advanced.getChildren().get(13)).getTextFill());
            }
        });
    }

    @Test
    void percentageGettersAndDisplayUsePercentageUnits() throws Exception {
        InstitutionalBacktestMetrics report = metrics(100, -20);
        assertEquals(8, report.getAnnualizedReturn());
        assertEquals(50, report.getWinRate());
        assertEquals(20.0 / 1100 * 100, report.getMaxDrawdownPercent(), 1e-9);
        assertEquals(report.getMaxDrawdownPercent(), report.getAvgDrawdown(), 1e-9);
        onFxThread(() -> {
            BacktestReportPanel panel = new BacktestReportPanel();
            panel.displayReport(report);
            VBox content = (VBox) panel.getContent();
            assertEquals(String.format("%.2f%%", 8.0), ((Label) grid(content, 1).getChildren().get(7)).getText());
            assertEquals("1 (" + String.format("%.1f%%", 50.0) + ")", ((Label) grid(content, 2).getChildren().get(3)).getText());
            assertEquals(String.format("%.2f%%", report.getAvgDrawdown()), ((Label) grid(content, 3).getChildren().get(3)).getText());
        });
    }

    private static GridPane grid(VBox content, int section) {
        return (GridPane) ((VBox) content.getChildren().get(section)).getChildren().get(1);
    }

    private static InstitutionalBacktestMetrics metrics(double... profits) {
        List<BacktestingPanel.BacktestTrade> trades = new ArrayList<>();
        for (double profit : profits) {
            trades.add(new BacktestingPanel.BacktestTrade(LocalDate.of(2026, 1, 1), null, null,
                    100, 100 + profit, 1, profit, profit, "test", 0, 1));
        }
        return new InstitutionalBacktestMetrics(trades, 1000);
    }

    private static void onFxThread(Runnable action) throws Exception {
        new JFXPanel();
        CompletableFuture<Void> complete = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                complete.complete(null);
            } catch (Throwable failure) {
                complete.completeExceptionally(failure);
            }
        });
        complete.get(30, TimeUnit.SECONDS);
    }
}
