package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import org.investpro.core.SystemCore;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketWatchPanelTest {
    @Test
    void narrowWindowLaysOutAndRefreshControlsWork() throws Exception {
        new JFXPanel();
        SystemCore core = mock(SystemCore.class);
        SymbolAgentManager manager = mock(SymbolAgentManager.class);
        when(core.getSymbolAgentManager()).thenReturn(manager);
        when(manager.getAllStates()).thenReturn(List.of());
        CompletableFuture<Void> complete = new CompletableFuture<>();
        Platform.runLater(() -> {
            MarketWatchPanel panel = null;
            try {
                panel = new MarketWatchPanel(core);
                Scene scene = new Scene(panel, 600, 700);
                scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
                panel.applyCss();
                panel.resize(600, 700);
                panel.layout();
                assertNotNull(panel.lookup("FlowPane"));
                Button pause = panel.lookupAll(".button").stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).filter(b -> "Pause".equals(b.getText())).findFirst().orElseThrow();
                pause.fire();
                assertTrue(panel.isPaused());
                assertEquals("Resume", pause.getText());
                pause.fire();
                assertFalse(panel.isPaused());
                assertTrue(panel.getTable().getHeight() > 200);
                var first = org.investpro.ui.models.MarketWatchRow.builder()
                        .symbol(new org.investpro.models.trading.TradePair("BTC", "USD")).build();
                var second = org.investpro.ui.models.MarketWatchRow.builder()
                        .symbol(new org.investpro.models.trading.TradePair("ETH", "USD")).build();
                first.setTradable(true);
                second.setTradable(false);
                first.strategyScoreProperty().set(0.2);
                second.strategyScoreProperty().set(0.9);
                panel.getRowCache().put("BTC", first);
                panel.getRowCache().put("ETH", second);
                panel.getSortSelector().setValue("Strategy score");
                assertEquals(List.of(second, first), List.copyOf(panel.getTable().getItems()));
                panel.getFilterSelector().setValue(
                        org.investpro.trading.tradability.MarketWatchTradabilityFilter.TRADABLE_ONLY);
                assertEquals(List.of(first), List.copyOf(panel.getTable().getItems()));
                panel.getTable().getSelectionModel().select(first);
                panel.getTable().getContextMenu().getItems().getFirst().fire();
                assertTrue(first.isFavorite());
                panel.getFilterSelector().setValue(
                        org.investpro.trading.tradability.MarketWatchTradabilityFilter.FAVORITES);
                assertEquals(List.of(first), List.copyOf(panel.getTable().getItems()));
                panel.getTable().getSelectionModel().select(first);
                panel.getTable().getContextMenu().getItems().getFirst().fire();
                assertTrue(panel.getTable().getItems().isEmpty());
                complete.complete(null);
            } catch (Throwable failure) {
                complete.completeExceptionally(failure);
            } finally {
                if (panel != null) panel.shutdown();
            }
        });
        complete.get(30, TimeUnit.SECONDS);
    }
}
