package org.investpro.ui;

import lombok.extern.slf4j.Slf4j;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.Position;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Manager for receiving and updating position data from exchanges.
 * Handles real-time position updates and periodic refreshes.
 */
@Slf4j
public class PositionsDataManager {
    /**
     * -- GETTER --
     *  Get the observable list of positions for binding to UI.
     */
    @Getter
    private final ObservableList<Position> positions = FXCollections.observableArrayList();
    private Timer refreshTimer;
    private Consumer<String> statusCallback;
    private Exchange currentExchange;

    public PositionsDataManager() {}

    /**
     * Set callback for status messages.
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    /**
     * Update positions from exchange API.
     */
    public void refreshPositions(Exchange exchange) {
        if (exchange == null) {
            log.warn("Cannot refresh positions: exchange is null");
            return;
        }

        this.currentExchange = exchange;

        exchange.fetchAllPositions()
                .thenAccept(positionList -> Platform.runLater(() -> {
                    if (positionList != null && !positionList.isEmpty()) {
                        positions.setAll(positionList);
                        updatePositionMetrics();
                        notifyStatus("✅ Loaded %d positions".formatted(positionList.size()));
                        log.info("Positions refreshed: {} positions loaded", positionList.size());
                    } else {
                        positions.clear();
                        notifyStatus("ℹ️ No open positions");
                    }
                }))
                .exceptionally(exception -> {
                    notifyStatus("❌ Failed to load positions: %s".formatted(exception.getMessage()));
                    log.error("Position refresh failed", exception);
                    return null;
                });
    }

    /**
     * Update positions from streaming data (real-time updates).
     */
    public void updateFromStream(List<Position> streamPositions) {
        if (streamPositions == null || streamPositions.isEmpty()) {
            Platform.runLater(positions::clear);
            return;
        }

        Platform.runLater(() -> {
            positions.setAll(streamPositions);
            updatePositionMetrics();
            notifyStatus("🔄 Positions updated (%d total)".formatted(streamPositions.size()));
        });
    }

    /**
     * Update a single position from trade/update event.
     */
    public void updatePosition(Position updatedPosition) {
        if (updatedPosition == null) {
            return;
        }

        Platform.runLater(() -> {
            // Find and update existing position or add new one
            boolean found = false;
            for (int i = 0; i < positions.size(); i++) {
                Position existing = positions.get(i);
                if (existing.getPositionId() != null && existing.getPositionId().equals(updatedPosition.getPositionId())) {
                    positions.set(i, updatedPosition);
                    found = true;
                    break;
                }
            }

            if (!found && updatedPosition.isOpen()) {
                positions.add(updatedPosition);
            }

            updatePositionMetrics();
        });
    }

    /**
     * Start periodic position refresh.
     */
    public void startAutoRefresh(Exchange exchange, long intervalMs) {
        stopAutoRefresh(); // Stop existing timer if any

        // 5 seconds default
        this.currentExchange = exchange;

        refreshTimer = new Timer("PositionsAutoRefresh", true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentExchange != null) {
                    refreshPositions(currentExchange);
                }
            }
        }, intervalMs, intervalMs);

        notifyStatus("🔄 Auto-refresh enabled (%dms)".formatted(intervalMs));
        log.info("Position auto-refresh started: {} ms interval", intervalMs);
    }

    /**
     * Stop periodic position refresh.
     */
    public void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
            notifyStatus("⏸️ Auto-refresh disabled");
            log.info("Position auto-refresh stopped");
        }
    }

    /**
     * Clear all positions.
     */
    public void clear() {
        positions.clear();
    }

    /**
     * Get total P&L across all positions.
     */
    public double getTotalPnL() {
        return positions.stream()
                .mapToDouble(p -> p.getUnrealizedPnl() + p.getRealizedPnl())
                .sum();
    }

    /**
     * Get total unrealized P&L.
     */
    public double getTotalUnrealizedPnl() {
        return positions.stream()
                .mapToDouble(Position::getUnrealizedPnl)
                .sum();
    }

    /**
     * Get total realized P&L.
     */
    public double getTotalRealizedPnl() {
        return positions.stream()
                .mapToDouble(Position::getRealizedPnl)
                .sum();
    }

    /**
     * Get total notional value of all positions.
     */
    public double getTotalNotionalValue() {
        return positions.stream()
                .mapToDouble(Position::getCurrentValue)
                .sum();
    }

    /**
     * Get number of open positions.
     */
    public int getOpenPositionsCount() {
        return (int) positions.stream()
                .filter(Position::isOpen)
                .count();
    }

    /**
     * Get number of long positions.
     */
    public int getLongPositionsCount() {
        return (int) positions.stream()
                .filter(p -> p.isOpen() && p.getSide().name().equals("BUY"))
                .count();
    }

    /**
     * Get number of short positions.
     */
    public int getShortPositionsCount() {
        return (int) positions.stream()
                .filter(p -> p.isOpen() && p.getSide().name().equals("SELL"))
                .count();
    }

    /**
     * Update position metrics (P&L calculations).
     */
    private void updatePositionMetrics() {
        for (Position position : positions) {
            if (position.getCurrentPrice() > 0) {
                position.updateUnrealizedPnl();
            }
        }
    }

    /**
     * Notify status callback.
     */
    private void notifyStatus(String message) {
        if (statusCallback != null) {
            try {
                statusCallback.accept(message);
            } catch (Exception e) {
                log.warn("Error in status callback", e);
            }
        }
    }

    /**
     * Close this manager and cleanup resources.
     */
    public void close() {
        stopAutoRefresh();
        positions.clear();
    }
}
