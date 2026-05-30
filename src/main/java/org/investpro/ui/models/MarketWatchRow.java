package org.investpro.ui.models;

import javafx.beans.property.*;
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
import org.investpro.trading.tradability.ProductTradabilityStatus;
import org.investpro.trading.tradability.SymbolTradability;
import org.jetbrains.annotations.Nullable;

/**
 * Model for a single row in the MarketWatch table.
 * <p>
 * Contains JavaFX properties for:
 * - Basic market data (symbol, bid, ask, spread)
 * - Agent/strategy state (agentState, tradingMode, activeStrategy,
 * activeTimeframe, strategy Score)
 * - Live readiness (liveReady, issue)
 *
 * <p>
 * Property accessor methods (e.g. {@code symbolProperty()},
 * {@code spreadProperty()})
 * are intentionally public — they are bound by JavaFX
 * {@code PropertyValueFactory}
 * via reflection for table column cell values and must not be removed.
 */
@Getter
@Setter
@Slf4j
// property/badge methods used via JavaFX reflection & cell factories
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
    private final StringProperty tradabilityStatus = new SimpleStringProperty("UNKNOWN");
    private final BooleanProperty tradable = new SimpleBooleanProperty(false);
    private final StringProperty orderType = new SimpleStringProperty("No Orders");
    private final StringProperty restrictions = new SimpleStringProperty("");
    private final StringProperty productHealth = new SimpleStringProperty("Unknown");
    private final StringProperty exchangeCapability = new SimpleStringProperty("Unknown");
    private final BooleanProperty favorite = new SimpleBooleanProperty(false);
    private final StringProperty lastUpdatedText = new SimpleStringProperty("");

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

    public StringProperty tradabilityStatusProperty() {
        return tradabilityStatus;
    }

    public String getTradabilityStatus() {
        return tradabilityStatus.get();
    }

    public BooleanProperty tradableProperty() {
        return tradable;
    }

    public boolean isTradable() {
        return tradable.get();
    }

    public StringProperty orderTypeProperty() {
        return orderType;
    }

    public String getOrderType() {
        return orderType.get();
    }

    public StringProperty restrictionsProperty() {
        return restrictions;
    }

    public String getRestrictions() {
        return restrictions.get();
    }

    public StringProperty productHealthProperty() {
        return productHealth;
    }

    public String getProductHealth() {
        return productHealth.get();
    }

    public StringProperty exchangeCapabilityProperty() {
        return exchangeCapability;
    }

    public String getExchangeCapability() {
        return exchangeCapability.get();
    }

    public BooleanProperty favoriteProperty() {
        return favorite;
    }

    public boolean isFavorite() {
        return favorite.get();
    }

    public void setFavorite(boolean value) {
        favorite.set(value);
    }

    public StringProperty lastUpdatedTextProperty() {
        return lastUpdatedText;
    }

    public String getLastUpdatedText() {
        return lastUpdatedText.get();
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

    public void setTradabilityStatus(String value) {
        tradabilityStatus.set(value == null || value.isBlank() ? "UNKNOWN" : value);
    }

    public void setTradable(boolean value) {
        tradable.set(value);
    }

    public void setOrderType(String value) {
        orderType.set(value == null || value.isBlank() ? "No Orders" : value);
    }

    public void setRestrictions(String value) {
        restrictions.set(value == null ? "" : value);
    }

    public void setProductHealth(String value) {
        productHealth.set(value == null || value.isBlank() ? "Unknown" : value);
    }

    public void setExchangeCapability(String value) {
        exchangeCapability.set(value == null || value.isBlank() ? "Unknown" : value);
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

        // Guard against uninitialized state field (Lombok @Builder does not enforce
        // @NotNull)
        if (state.getState() != null) {
            agentState.set(state.getState().getDisplayName());
        } else {
            agentState.set("Unknown");
        }

        double resolvedBid = state.getBidPrice() > 0 ? state.getBidPrice() : getBid();
        double resolvedAsk = state.getAskPrice() > 0 ? state.getAskPrice() : getAsk();

        TradePair pair = state.getSymbol();
        if (resolvedBid <= 0 && pair.getBid() > 0) {
            resolvedBid = pair.getBid();
        }
        if (resolvedAsk <= 0 && pair.getAsk() > 0) {
            resolvedAsk = pair.getAsk();
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

        // Show signal text (▲ BUY 0.82 / ▼ SELL 0.65)
        lastSignal.set(state.getSignalText());

        if (state.isLiveAllowed()) {
            issue.set("");
        } else {
            String blockReason = state.getLiveBlockedReason();
            issue.set(blockReason != null ? blockReason : "");
        }

        lastUpdated = System.currentTimeMillis();
        lastUpdatedText.set(java.time.Instant.ofEpochMilli(lastUpdated).toString());

    }

    public void updateTradability(@Nullable ProductTradabilityStatus productStatus,
            @Nullable SymbolTradability exchangeStatus,
            @Nullable String exchangeCapabilityStatus) {
        if (productStatus == null && exchangeStatus == null) {
            setTradabilityStatus("UNKNOWN");
            setTradable(false);
            setOrderType("No Orders");
            setRestrictions("Loading tradability status");
            setProductHealth("Loading");
            setExchangeCapability(exchangeCapabilityStatus);
            return;
        }

        String status = productStatus != null ? productStatus.status()
                : exchangeStatus == null || exchangeStatus.status() == null ? "UNKNOWN"
                        : exchangeStatus.status().name();
        boolean allowed = productStatus != null ? productStatus.isTradeable()
                : exchangeStatus != null && exchangeStatus.isFullyTradable();

        setTradabilityStatus(status);
        setTradable(allowed);

        if (productStatus != null) {
            if (productStatus.isReadOnly()) {
                setOrderType(productStatus.isLimitOnly() ? "Limit Only" : "View Only");
            } else if (productStatus.postOnly()) {
                setOrderType("Post Only");
            } else if (productStatus.auctionMode()) {
                setOrderType("Auction Mode");
            } else {
                setOrderType(productStatus.canPlaceMarketOrder() ? "Market + Limit" : "Limit Only");
            }

            setRestrictions(buildRestrictionSummary(productStatus));
            setProductHealth(
                    productStatus.statusMessage().isBlank() ? productStatus.status() : productStatus.statusMessage());
        } else {
            setOrderType(exchangeStatus != null && exchangeStatus.limitOrderAllowed()
                    ? (exchangeStatus.marketOrderAllowed() ? "Market + Limit" : "Limit Only")
                    : "No Orders");
            setRestrictions(exchangeStatus == null ? "" : exchangeStatus.reason());
            setProductHealth(exchangeStatus == null ? "Unknown" : exchangeStatus.reason());
        }

        setExchangeCapability(exchangeCapabilityStatus);
        lastUpdated = System.currentTimeMillis();
        lastUpdatedText.set(java.time.Instant.ofEpochMilli(lastUpdated).toString());
    }

    private String buildRestrictionSummary(@Nullable ProductTradabilityStatus status) {
        if (status == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        appendRestriction(builder, status.viewOnly(), "View Only");
        appendRestriction(builder, status.tradingDisabled(), "Trading Disabled");
        appendRestriction(builder, status.isDisabled(), "Disabled");
        appendRestriction(builder, status.cancelOnly(), "Cancel Only");
        appendRestriction(builder, status.limitOnly(), "Limit Only");
        appendRestriction(builder, status.postOnly(), "Post Only");
        appendRestriction(builder, status.auctionMode(), "Auction Mode");
        return builder.length() == 0 ? status.statusMessage() : builder.toString();
    }

    private void appendRestriction(StringBuilder builder, boolean enabled, String label) {
        if (!enabled) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(label);
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
