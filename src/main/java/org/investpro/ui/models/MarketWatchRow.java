package org.investpro.ui.models;

import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableRow;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.core.agents.symbol.SymbolAgentState;
import org.investpro.core.agents.symbol.SymbolTradingMode;
import org.investpro.models.currency.CurrencyRegistry;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.Nullable;

/**
 * Model for a single row in the MarketWatch table.
 * 
 * Contains JavaFX properties for:
 * - Basic market data (symbol, bid, ask, spread)
 * - Agent/strategy state (agentState, tradingMode, activeStrategy,
 * activeTimeframe, strategyScore)
 * - Live readiness (liveReady, issue)
 */
@Getter
@Setter
@Slf4j
public class MarketWatchRow extends TableRow<MarketWatchRow> {

    // Basic market data
    private final ObjectProperty<TradePair> symbol = new SimpleObjectProperty<>();
    private final DoubleProperty bid = new SimpleDoubleProperty();
    private final DoubleProperty ask = new SimpleDoubleProperty();
    private final DoubleProperty spread = new SimpleDoubleProperty();
    private final DoubleProperty spreadPercent = new SimpleDoubleProperty();
    private final StringProperty session = new SimpleStringProperty();

    // Agent/strategy state
    private final StringProperty agentState = new SimpleStringProperty("Unknown");
    private final StringProperty tradingMode = new SimpleStringProperty("Unknown");
    private final StringProperty activeStrategy = new SimpleStringProperty();
    private final StringProperty activeTimeframe = new SimpleStringProperty();
    private final DoubleProperty strategyScore = new SimpleDoubleProperty();
    private final BooleanProperty liveReady = new SimpleBooleanProperty(false);
    private final StringProperty issue = new SimpleStringProperty();
    private final StringProperty lastSignal = new SimpleStringProperty();
    private final StringProperty assignedStrategy = new SimpleStringProperty();

    // Metadata
    private long lastUpdated;

    @Builder
    public MarketWatchRow(
            TradePair symbol,
            double bid,
            double ask,
            double spread,
            double spreadPercent,
            String session) {
        this.symbol.set(symbol);
        this.bid.set(bid);
        this.ask.set(ask);
        this.spread.set(spread);
        this.spreadPercent.set(spreadPercent);
        this.session.set(session);
        this.lastUpdated = System.currentTimeMillis();

    }

    // ===== Getters for properties =====

    public ObjectProperty<TradePair> symbolProperty() {
        return symbol;
    }

    public TradePair getSymbol() {
        return symbol.get();
    }

    public DoubleProperty bidProperty() {
        return bid;
    }

    public double getBid() {
        return bid.get();
    }

    public DoubleProperty askProperty() {
        return ask;
    }

    public double getAsk() {
        return ask.get();
    }

    public DoubleProperty spreadProperty() {
        return spread;
    }

    public double getSpread() {
        return spread.get();
    }

    public DoubleProperty spreadPercentProperty() {
        return spreadPercent;
    }

    public double getSpreadPercent() {
        return spreadPercent.get();
    }

    public StringProperty sessionProperty() {
        return session;
    }

    public String getSession() {
        return session.get();
    }

    public StringProperty agentStateProperty() {
        return agentState;
    }

    public String getAgentState() {
        return agentState.get();
    }

    public StringProperty tradingModeProperty() {
        return tradingMode;
    }

    public String getTradingMode() {
        return tradingMode.get();
    }

    public StringProperty activeStrategyProperty() {
        return activeStrategy;
    }

    public String getActiveStrategy() {
        return activeStrategy.get();
    }

    public StringProperty activeTimeframeProperty() {
        return activeTimeframe;
    }

    public String getActiveTimeframe() {
        return activeTimeframe.get();
    }

    public DoubleProperty strategyScoreProperty() {
        return strategyScore;
    }

    public double getStrategyScore() {
        return strategyScore.get();
    }

    public BooleanProperty liveReadyProperty() {
        return liveReady;
    }

    public boolean isLiveReady() {
        return liveReady.get();
    }

    public StringProperty issueProperty() {
        return issue;
    }

    public String getIssue() {
        return issue.get();
    }

    public StringProperty lastSignalProperty() {
        return lastSignal;
    }

    public String getLastSignal() {
        return lastSignal.get();
    }

    public StringProperty assignedStrategyProperty() {
        return assignedStrategy;
    }

    public String getAssignedStrategy() {
        return assignedStrategy.get();
    }

    public String getBaseCodeBadge() {
        TradePair pair = getSymbol();
        return pair == null ? "" : pair.getBaseCode();
    }

    public String getCounterCodeBadge() {
        TradePair pair = getSymbol();
        return pair == null ? "" : pair.getCounterCode();
    }

    public String getBaseIconPath() {
        TradePair pair = getSymbol();
        if (pair == null) {
            return CurrencyRegistry.global().iconPathOrDefault("UNKNOWN");
        }
        return CurrencyRegistry.global().iconPathOrDefault(pair.getBaseCode());
    }

    // ===== Setters for properties =====

    public void setBid(double value) {
        bid.set(value);
    }

    public void setAsk(double value) {
        ask.set(value);
    }

    public void setSpread(double value) {
        spread.set(value);
    }

    public void setSpreadPercent(double value) {
        spreadPercent.set(value);
    }

    public void setSession(String value) {
        session.set(value);
    }

    // ===== Update from SymbolAgentState =====

    /**
     * Update this row with data from a SymbolAgentState.
     */
    public void updateSymbolState(@Nullable SymbolAgentState state) {
        if (state == null) {
            agentState.set("Unknown");
            tradingMode.set("Unknown");
            session.set("UNKNOWN");
            activeStrategy.set("");
            assignedStrategy.set("");
            activeTimeframe.set("");
            strategyScore.set(0.0);
            liveReady.set(false);
            issue.set("No symbol agent state");
            return;
        }

        // Guard against uninitialised state field (Lombok @Builder does not enforce @NotNull)
        if (state.getState() != null) {
            agentState.set(state.getState().getDisplayName());
        } else {
            agentState.set("Unknown");
        }

        double resolvedBid = state.getBidPrice() > 0 ? state.getBidPrice() : getBid();
        double resolvedAsk = state.getAskPrice() > 0 ? state.getAskPrice() : getAsk();

        TradePair pair = state.getSymbol();
        if (pair != null) {
            if (resolvedBid <= 0 && pair.getBid() > 0) {
                resolvedBid = pair.getBid();
            }
            if (resolvedAsk <= 0 && pair.getAsk() > 0) {
                resolvedAsk = pair.getAsk();
            }
        }

        // Last-resort fallback keeps UI values non-null/non-empty.
        if (resolvedBid <= 0 && resolvedAsk > 0) {
            resolvedBid = resolvedAsk;
        }
        if (resolvedAsk <= 0 && resolvedBid > 0) {
            resolvedAsk = resolvedBid;
        }

        bid.set(Math.max(0.0, resolvedBid));
        ask.set(Math.max(0.0, resolvedAsk));

        if (resolvedBid > 0 && resolvedAsk > 0 && resolvedAsk >= resolvedBid) {
            spread.set(resolvedAsk - resolvedBid);
            spreadPercent.set(((resolvedAsk - resolvedBid) / resolvedAsk) * 100.0);
        } else if (state.getSpreadPercent() > 0) {
            spreadPercent.set(state.getSpreadPercent());
        } else {
            spread.set(0.0);
            spreadPercent.set(0.0);
        }

        SymbolTradingMode mode = state.getTradingMode();
        tradingMode.set(mode.getDisplayName());
        session.set(resolveSessionLabel(pair));

        activeStrategy.set(state.getActiveStrategyName() != null ? state.getActiveStrategyName() : "");
        assignedStrategy.set(state.getAssignedStrategyName() != null ? state.getAssignedStrategyName() : "");
        activeTimeframe.set(state.getActiveTimeframe() != null ? state.getActiveTimeframe().getCode() : "");

        strategyScore.set(state.getStrategyScore());
        liveReady.set(state.isLiveAllowed());

        // Show signal text (\u25b2 BUY 0.82 / \u25bc SELL 0.65)
        lastSignal.set(state.getSignalText());

        if (state.isLiveAllowed()) {
            issue.set("");
        } else {
            String blockReason = state.getLiveBlockedReason();
            issue.set(blockReason != null ? blockReason : "");
        }

        lastUpdated = System.currentTimeMillis();

    }

    private String resolveSessionLabel(@Nullable TradePair pair) {
        TradingSessionStatus status = pair == null
                ? TradingSessionStatus.UNKNOWN
                : pair.getTradingSessionStatus();

        return switch (status) {
            case OPEN -> "OPEN";
            case CLOSED -> "CLOSED";
            case BREAK -> "BREAK";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    /**
     * Check if this row's symbol state data is stale.
     */
    public boolean isStateStale(long maxAgeMs) {
        return System.currentTimeMillis() - lastUpdated > maxAgeMs;
    }

    /**
     * Get CSS style class for the trading mode.
     */
    public String getTradingModeCssClass() {
        String mode = tradingMode.get().toLowerCase();
        return switch (mode) {
            case "training / evaluating" -> "mode-training";
            case "paper trading" -> "mode-paper";
            case "live ready" -> "mode-live-ready";
            case "live trading" -> "mode-live";
            case "blocked" -> "mode-blocked";
            case "paused" -> "mode-paused";
            case "failed" -> "mode-failed";
            default -> "mode-unknown";
        };
    }
}
