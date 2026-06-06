package org.investpro.trading;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Institutional-grade pre-trade validation engine.
 *
 * <p>
 * This engine validates a trade through 20 sequential gates before execution.
 *
 * <p>
 * Gate philosophy:
 * <ul>
 *     <li>No blockers = trade may proceed</li>
 *     <li>Warnings = reduce size proportionally</li>
 *     <li>Critical warning = reject</li>
 *     <li>Missing important data = reject</li>
 *     <li>When uncertain, do not trade</li>
 * </ul>
 *
 * <p>
 * This implementation is intentionally adapter-based. It does not directly depend
 * on SystemCore, Account, Position, InstrumentRegistry, or candle/quote classes.
 * That makes it safe to compile now while your platform APIs are still evolving.
 *
 * <p>
 * Later, wire your real SystemCore into {@link TradingContext}.
 *
 * @author InvestPro Trading System
 */
@Slf4j
public class PreTradeValidationEngine {

    private static final int TOTAL_GATES = 20;

    private final ValidationConfig config;

    public PreTradeValidationEngine() {
        this(ValidationConfig.defaultConfig());
    }

    public PreTradeValidationEngine(ValidationConfig config) {


        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Validate a trade before it reaches the execution layer.
     *
     * @param request proposed trade request
     * @param context current system/account/market context
     * @return validation result
     */
    public ValidationResult validate(TradeRequest request, TradingContext context) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");

        ValidationAccumulator acc = new ValidationAccumulator(request.requestedQuantity());

        runGate(acc, 1, "System state", () -> validateSystemState(context));
        runGate(acc, 2, "Kill switch", () -> validateKillSwitch(context));
        runGate(acc, 3, "Auto trading enabled", () -> validateAutoTrading(context));
        runGate(acc, 4, "Account available", () -> validateAccountAvailable(context));
        runGate(acc, 5, "Account trading enabled", () -> validateAccountTradingEnabled(context));
        runGate(acc, 6, "Broker connection", () -> validateBrokerConnection(context));
        runGate(acc, 7, "Venue selected", () -> validateVenue(context));
        runGate(acc, 8, "Instrument allowed", () -> validateInstrumentAllowed(request, context));
        runGate(acc, 9, "Market data freshness", () -> validateMarketDataFreshness(context));
        runGate(acc, 10, "Quote validity", () -> validateQuote(context));
        runGate(acc, 11, "Spread", () -> validateSpread(context));
        runGate(acc, 12, "Order side", () -> validateSide(request));
        runGate(acc, 13, "Order type", () -> validateOrderType(request));
        runGate(acc, 14, "Quantity", () -> validateQuantity(request));
        runGate(acc, 15, "Estimated notional", () -> validateNotional(request, context));
        runGate(acc, 16, "Free margin", () -> validateFreeMargin(request, context));
        runGate(acc, 17, "Portfolio exposure", () -> validatePortfolioExposure(request, context));
        runGate(acc, 18, "Symbol concentration", () -> validateSymbolConcentration(request, context));
        runGate(acc, 19, "Daily loss and drawdown", () -> validateLossLimits(context));
        runGate(acc, 20, "AI/live trading confirmation", () -> validateAiAndLiveTrading(request, context));

        boolean approved = acc.blockers.isEmpty() && acc.finalQuantity > 0.0;

        if (!approved) {
            acc.finalQuantity = 0.0;
        }

        ValidationResult result = new ValidationResult(
                approved,
                approved ? "Trade approved by pre-trade validation engine." : "Trade rejected by pre-trade validation engine.",
                request.requestedQuantity(),
                acc.finalQuantity,
                acc.sizeMultiplier,
                Collections.unmodifiableList(acc.gates),
                Collections.unmodifiableList(acc.blockers),
                Collections.unmodifiableList(acc.warnings),
                Collections.unmodifiableList(acc.recommendations),
                acc.gatesPassed,
                TOTAL_GATES
        );

        if (approved) {
            log.info("Pre-trade validation approved: symbol={}, side={}, requestedQty={}, finalQty={}, multiplier={}",
                    request.symbol(), request.side(), request.requestedQuantity(), result.finalQuantity(), result.sizeMultiplier());
        } else {
            log.warn("Pre-trade validation rejected: symbol={}, side={}, blockers={}",
                    request.symbol(), request.side(), result.blockers());
        }

        return result;
    }

    private void runGate(ValidationAccumulator acc, int gateNumber, String gateName, GateCheck check) {
        if (!acc.blockers.isEmpty() && config.stopAfterFirstBlocker()) {
            acc.gates.add(GateResult.blocked(
                    gateNumber,
                    gateName,
                    "Skipped because an earlier gate already blocked the trade."
            ));
            return;
        }

        try {
            GateResult result = check.run();

            if (result == null) {
                result = GateResult.blocked(gateNumber, gateName, "Gate returned no result.");
            } else {
                result = result.withGateInfo(gateNumber, gateName);
            }

            acc.gates.add(result);

            if (result.status() == GateStatus.PASSED) {
                acc.gatesPassed++;
            }

            if (result.status() == GateStatus.WARNING) {
                acc.warnings.add(result.message());

                double reduction = Math.max(0.0, Math.min(1.0, result.sizeReduction()));
                if (reduction > 0.0) {
                    acc.sizeMultiplier *= (1.0 - reduction);
                    acc.recalculateFinalQuantity();
                }
            }

            if (result.status() == GateStatus.CRITICAL_WARNING) {
                acc.warnings.add(result.message());
                acc.blockers.add("Critical warning: " + result.message());
            }

            if (result.status() == GateStatus.BLOCKED) {
                acc.blockers.add(result.message());
            }

            if (result.recommendation() != null && !result.recommendation().isBlank()) {
                acc.recommendations.add(result.recommendation());
            }

        } catch (Exception exception) {
            String message = "Gate " + gateNumber + " failed unexpectedly: " + exception.getMessage();
            acc.gates.add(GateResult.blocked(gateNumber, gateName, message));
            acc.blockers.add(message);
            log.debug("Pre-trade validation gate failed unexpectedly: gate={} name={}", gateNumber, gateName, exception);
        }
    }

    private GateResult validateSystemState(TradingContext context) {
        String state = safeUpper(context.systemState());

        if (state.isBlank()) {
            return GateResult.blocked("System state is missing.");
        }

        if (!"READY".equals(state) && !"RUNNING".equals(state) && !"LIVE".equals(state)) {
            return GateResult.blocked("System is not ready for trading. Current state: " + context.systemState());
        }

        return GateResult.passed("System state is valid: " + context.systemState());
    }

    private GateResult validateKillSwitch(TradingContext context) {
        if (context.killSwitchTriggered()) {
            return GateResult.blocked("Kill switch is active.");
        }

        return GateResult.passed("Kill switch is not active.");
    }

    private GateResult validateAutoTrading(TradingContext context) {
        if (!context.autoTradingEnabled()) {
            return GateResult.blocked("Auto trading is disabled.");
        }

        return GateResult.passed("Auto trading is enabled.");
    }

    private GateResult validateAccountAvailable(TradingContext context) {
        if (context.account() == null) {
            return GateResult.blocked("Account context is missing.");
        }

        return GateResult.passed("Account context is available.");
    }

    private GateResult validateAccountTradingEnabled(TradingContext context) {
        AccountView account = context.account();

        if (account == null) {
            return GateResult.blocked("Account is missing.");
        }

        if (!account.tradingEnabled()) {
            return GateResult.blocked("Trading is disabled on the account.");
        }

        if (account.equity() <= 0.0) {
            return GateResult.blocked("Account equity is invalid: " + account.equity());
        }

        return GateResult.passed("Account trading is enabled.");
    }

    private GateResult validateBrokerConnection(TradingContext context) {
        if (!context.brokerConnected()) {
            return GateResult.blocked("Broker is not connected.");
        }

        return GateResult.passed("Broker connection is active.");
    }

    private GateResult validateVenue(TradingContext context) {
        if (context.selectedVenue() == null || context.selectedVenue().isBlank()) {
            return GateResult.blocked("No trading venue selected.");
        }

        return GateResult.passed("Selected venue: " + context.selectedVenue());
    }

    private GateResult validateInstrumentAllowed(TradeRequest request, TradingContext context) {
        if (request.symbol() == null || request.symbol().isBlank()) {
            return GateResult.blocked("Trade symbol is missing.");
        }

        InstrumentRegistryView registry = context.instrumentRegistry();

        if (registry == null) {
            return GateResult.blocked("Instrument registry is missing.");
        }

        if (!registry.isKnownInstrument(request.symbol())) {
            return GateResult.blocked("Unknown instrument: " + request.symbol());
        }

        if (!registry.isTradable(request.symbol(), context.selectedVenue())) {
            return GateResult.blocked("Instrument is not tradable on selected venue: " + request.symbol());
        }

        return GateResult.passed("Instrument is allowed: " + request.symbol());
    }

    private GateResult validateMarketDataFreshness(TradingContext context) {
        MarketSnapshot snapshot = context.marketSnapshot();

        if (snapshot == null) {
            return GateResult.blocked("Market snapshot is missing.");
        }

        if (snapshot.updatedAt() == null) {
            return GateResult.blocked("Market snapshot update time is missing.");
        }

        Duration age = Duration.between(snapshot.updatedAt(), Instant.now());

        if (age.isNegative()) {
            return GateResult.warning(
                    "Market snapshot timestamp is in the future.",
                    0.10,
                    "Check system clock synchronization."
            );
        }

        if (age.compareTo(config.maxMarketDataAge()) > 0) {
            return GateResult.blocked("Market data is stale. Age=" + age.toSeconds() + " seconds.");
        }

        return GateResult.passed("Market data is fresh. Age=" + age.toSeconds() + " seconds.");
    }

    private GateResult validateQuote(TradingContext context) {
        MarketSnapshot snapshot = context.marketSnapshot();

        if (snapshot == null || snapshot.quote() == null) {
            return GateResult.blocked("Quote is missing.");
        }

        QuoteView quote = snapshot.quote();

        if (quote.bid() <= 0.0 || quote.ask() <= 0.0) {
            return GateResult.blocked("Invalid quote. Bid=" + quote.bid() + ", Ask=" + quote.ask());
        }

        if (quote.ask() < quote.bid()) {
            return GateResult.blocked("Invalid quote. Ask is below bid.");
        }

        if (quote.midPrice() <= 0.0) {
            return GateResult.blocked("Invalid quote. Mid price is not positive.");
        }

        return GateResult.passed("Quote is valid.");
    }

    private GateResult validateSpread(TradingContext context) {
        QuoteView quote = context.marketSnapshot().quote();

        double mid = quote.midPrice();
        double spread = quote.ask() - quote.bid();
        double spreadPercent = spread / mid;

        if (spreadPercent > config.maxSpreadPercent()) {
            return GateResult.blocked(
                    "Spread is too wide. SpreadPercent=" + formatPercent(spreadPercent)
            );
        }

        if (spreadPercent > config.warningSpreadPercent()) {
            return GateResult.warning(
                    "Spread is elevated. SpreadPercent=" + formatPercent(spreadPercent),
                    0.20,
                    "Reduce order size or wait for tighter spread."
            );
        }

        return GateResult.passed("Spread is acceptable: " + formatPercent(spreadPercent));
    }

    private GateResult validateSide(TradeRequest request) {
        if (request.side() == null) {
            return GateResult.blocked("Order side is missing.");
        }

        if (request.side() == OrderSide.UNKNOWN) {
            return GateResult.blocked("Order side is unknown.");
        }

        return GateResult.passed("Order side is valid: " + request.side());
    }

    private GateResult validateOrderType(TradeRequest request) {
        if (request.orderType() == null) {
            return GateResult.blocked("Order type is missing.");
        }

        if (request.orderType() == OrderType.UNKNOWN) {
            return GateResult.blocked("Order type is unknown.");
        }

        if (request.orderType() == OrderType.MARKET && config.warnMarketOrders()) {
            return GateResult.warning(
                    "Market order requested.",
                    0.10,
                    "Prefer limit or limit-with-protection orders when spread is unstable."
            );
        }

        return GateResult.passed("Order type is valid: " + request.orderType());
    }

    private GateResult validateQuantity(TradeRequest request) {
        if (request.requestedQuantity() <= 0.0) {
            return GateResult.blocked("Requested quantity must be greater than zero.");
        }

        if (request.requestedQuantity() < config.minQuantity()) {
            return GateResult.blocked("Requested quantity is below minimum: " + request.requestedQuantity());
        }

        if (request.requestedQuantity() > config.maxQuantity()) {
            return GateResult.blocked("Requested quantity exceeds maximum: " + request.requestedQuantity());
        }

        return GateResult.passed("Requested quantity is valid.");
    }

    private GateResult validateNotional(TradeRequest request, TradingContext context) {
        double mid = context.marketSnapshot().quote().midPrice();
        double notional = request.requestedQuantity() * mid;

        if (notional <= 0.0) {
            return GateResult.blocked("Estimated notional is invalid.");
        }

        if (notional < config.minNotional()) {
            return GateResult.blocked("Estimated notional is below minimum: " + notional);
        }

        AccountView account = context.account();

        double equity = account.equity();

        if (equity <= 0.0) {
            return GateResult.blocked("Cannot evaluate notional because equity is invalid.");
        }

        double notionalToEquity = notional / equity;

        if (notionalToEquity > config.maxSingleTradeNotionalToEquity()) {
            return GateResult.blocked(
                    "Trade notional is too large relative to equity: " + formatPercent(notionalToEquity)
            );
        }

        if (notionalToEquity > config.warningSingleTradeNotionalToEquity()) {
            return GateResult.warning(
                    "Trade notional is elevated relative to equity: " + formatPercent(notionalToEquity),
                    0.25,
                    "Reduce trade size."
            );
        }

        return GateResult.passed("Estimated notional is acceptable: " + notional);
    }

    private GateResult validateFreeMargin(TradeRequest request, TradingContext context) {
        AccountView account = context.account();

        if (account.freeMargin() <= 0.0) {
            return GateResult.blocked("Free margin is not available.");
        }

        double mid = context.marketSnapshot().quote().midPrice();
        double estimatedNotional = request.requestedQuantity() * mid;
        double requiredMargin = estimatedNotional / Math.max(1.0, request.leverage());

        if (requiredMargin > account.freeMargin()) {
            return GateResult.blocked(
                    "Insufficient free margin. Required=" + requiredMargin + ", freeMargin=" + account.freeMargin()
            );
        }

        double marginUsageAfterTrade = requiredMargin / account.freeMargin();

        if (marginUsageAfterTrade > config.warningFreeMarginUsage()) {
            return GateResult.warning(
                    "Trade would consume elevated free margin: " + formatPercent(marginUsageAfterTrade),
                    0.20,
                    "Reduce size or use lower leverage."
            );
        }

        return GateResult.passed("Free margin is sufficient.");
    }

    private GateResult validatePortfolioExposure(TradeRequest request, TradingContext context) {
        AccountView account = context.account();

        double equity = account.equity();
        double totalExposure = Math.max(0.0, account.totalExposure());
        double mid = context.marketSnapshot().quote().midPrice();
        double newExposure = request.requestedQuantity() * mid;
        double exposureAfterTrade = totalExposure + newExposure;
        double exposureToEquity = exposureAfterTrade / equity;

        if (exposureToEquity > config.maxPortfolioExposureToEquity()) {
            return GateResult.blocked(
                    "Portfolio exposure would exceed limit: " + formatPercent(exposureToEquity)
            );
        }

        if (exposureToEquity > config.warningPortfolioExposureToEquity()) {
            return GateResult.warning(
                    "Portfolio exposure is elevated: " + formatPercent(exposureToEquity),
                    0.20,
                    "Reduce trade size or close existing exposure."
            );
        }

        return GateResult.passed("Portfolio exposure is acceptable.");
    }

    private GateResult validateSymbolConcentration(TradeRequest request, TradingContext context) {
        AccountView account = context.account();

        double equity = account.equity();
        double currentSymbolExposure = Math.max(0.0, account.exposureForSymbol(request.symbol()));
        double mid = context.marketSnapshot().quote().midPrice();
        double newExposure = request.requestedQuantity() * mid;
        double symbolExposureAfterTrade = currentSymbolExposure + newExposure;
        double symbolExposureToEquity = symbolExposureAfterTrade / equity;

        if (symbolExposureToEquity > config.maxSymbolExposureToEquity()) {
            return GateResult.blocked(
                    "Symbol concentration would exceed limit: " + formatPercent(symbolExposureToEquity)
            );
        }

        if (symbolExposureToEquity > config.warningSymbolExposureToEquity()) {
            return GateResult.warning(
                    "Symbol concentration is elevated: " + formatPercent(symbolExposureToEquity),
                    0.25,
                    "Reduce trade size or diversify exposure."
            );
        }

        return GateResult.passed("Symbol concentration is acceptable.");
    }

    private GateResult validateLossLimits(TradingContext context) {
        AccountView account = context.account();

        if (account.dailyPnl() <= -Math.abs(config.maxDailyLoss())) {
            return GateResult.blocked(
                    "Daily loss limit reached. DailyPnL=" + account.dailyPnl()
            );
        }

        if (account.drawdownPercent() >= config.maxDrawdownPercent()) {
            return GateResult.blocked(
                    "Maximum drawdown limit reached: " + formatPercent(account.drawdownPercent())
            );
        }

        if (account.drawdownPercent() >= config.warningDrawdownPercent()) {
            return GateResult.warning(
                    "Drawdown is elevated: " + formatPercent(account.drawdownPercent()),
                    0.30,
                    "Reduce size until equity curve recovers."
            );
        }

        return GateResult.passed("Daily loss and drawdown limits are acceptable.");
    }

    private GateResult validateAiAndLiveTrading(TradeRequest request, TradingContext context) {
        if (context.liveTrading() && !request.userConfirmedLiveTrading()) {
            return GateResult.blocked("Live trading requires explicit user confirmation.");
        }

        if (context.aiReviewEnabled() && !request.aiApproved()) {
            return GateResult.blocked("AI review is enabled but trade was not approved by AI.");
        }

        return GateResult.passed("AI/live trading confirmation passed.");
    }

    private static String safeUpper(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.2f%%", value * 100.0);
    }

    @FunctionalInterface
    private interface GateCheck {
        GateResult run();
    }

    private static final class ValidationAccumulator {
        private final List<GateResult> gates = new ArrayList<>();
        private final List<String> blockers = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> recommendations = new ArrayList<>();

        private final double requestedQuantity;

        private int gatesPassed;
        private double sizeMultiplier = 1.0;
        private double finalQuantity;

        private ValidationAccumulator(double requestedQuantity) {
            this.requestedQuantity = requestedQuantity;
            this.finalQuantity = requestedQuantity;
        }

        private void recalculateFinalQuantity() {
            this.finalQuantity = Math.max(0.0, requestedQuantity * sizeMultiplier);
        }
    }

    public enum GateStatus {
        PASSED,
        WARNING,
        CRITICAL_WARNING,
        BLOCKED
    }

    public enum OrderSide {
        BUY,
        SELL,
        SHORT,
        COVER,
        UNKNOWN
    }

    public enum OrderType {
        MARKET,
        LIMIT,
        STOP,
        STOP_LIMIT,
        TAKE_PROFIT,
        UNKNOWN
    }

    public record TradeRequest(
            String symbol,
            OrderSide side,
            OrderType orderType,
            double requestedQuantity,
            double requestedPrice,
            double leverage,
            boolean aiApproved,
            boolean userConfirmedLiveTrading
    ) {
        public TradeRequest {
            if (side == null) {
                side = OrderSide.UNKNOWN;
            }

            if (orderType == null) {
                orderType = OrderType.UNKNOWN;
            }

            if (leverage <= 0.0) {
                leverage = 1.0;
            }
        }

        @Contract(value = "_, _ -> new", pure = true)
        public static @NonNull TradeRequest marketBuy(String symbol, double quantity) {
            return new TradeRequest(
                    symbol,
                    OrderSide.BUY,
                    OrderType.MARKET,
                    quantity,
                    0.0,
                    1.0,
                    false,
                    false
            );
        }

        @Contract(value = "_, _ -> new", pure = true)
        public static @NonNull TradeRequest marketSell(String symbol, double quantity) {
            return new TradeRequest(
                    symbol,
                    OrderSide.SELL,
                    OrderType.MARKET,
                    quantity,
                    0.0,
                    1.0,
                    false,
                    false
            );
        }
    }

    public record ValidationResult(
            boolean approved,
            String summary,
            double requestedQuantity,
            double finalQuantity,
            double sizeMultiplier,
            List<GateResult> gates,
            List<String> blockers,
            List<String> warnings,
            List<String> recommendations,
            int gatesPassed,
            int totalGates
    ) {
        public boolean rejected() {
            return !approved;
        }

        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

        public @NonNull String humanReadableSummary() {
            StringBuilder builder = new StringBuilder();

            builder.append(summary)
                    .append(System.lineSeparator())
                    .append("Gates passed: ")
                    .append(gatesPassed)
                    .append("/")
                    .append(totalGates)
                    .append(System.lineSeparator())
                    .append("Requested quantity: ")
                    .append(requestedQuantity)
                    .append(System.lineSeparator())
                    .append("Final quantity: ")
                    .append(finalQuantity)
                    .append(System.lineSeparator())
                    .append("Size multiplier: ")
                    .append(String.format(Locale.US, "%.4f", sizeMultiplier));

            if (blockers != null && !blockers.isEmpty()) {
                builder.append(System.lineSeparator()).append("Blockers:");
                for (String blocker : blockers) {
                    builder.append(System.lineSeparator()).append("- ").append(blocker);
                }
            }

            if (warnings != null && !warnings.isEmpty()) {
                builder.append(System.lineSeparator()).append("Warnings:");
                for (String warning : warnings) {
                    builder.append(System.lineSeparator()).append("- ").append(warning);
                }
            }

            if (recommendations != null && !recommendations.isEmpty()) {
                builder.append(System.lineSeparator()).append("Recommendations:");
                for (String recommendation : recommendations) {
                    builder.append(System.lineSeparator()).append("- ").append(recommendation);
                }
            }

            return builder.toString();
        }
    }

    public record GateResult(
            int gateNumber,
            String gateName,
            GateStatus status,
            String message,
            double sizeReduction,
            String recommendation
    ) {
        public static GateResult passed(String message) {
            return new GateResult(0, "", GateStatus.PASSED, message, 0.0, null);
        }

        public static GateResult warning(String message, double sizeReduction, String recommendation) {
            return new GateResult(0, "", GateStatus.WARNING, message, sizeReduction, recommendation);
        }

        public static GateResult criticalWarning(String message, String recommendation) {
            return new GateResult(0, "", GateStatus.CRITICAL_WARNING, message, 1.0, recommendation);
        }

        public static GateResult blocked(String message) {
            return new GateResult(0, "", GateStatus.BLOCKED, message, 1.0, null);
        }

        public static GateResult blocked(int gateNumber, String gateName, String message) {
            return new GateResult(gateNumber, gateName, GateStatus.BLOCKED, message, 1.0, null);
        }

        @Contract("_, _ -> new")
        public @NonNull GateResult withGateInfo(int gateNumber, String gateName) {
            return new GateResult(gateNumber, gateName, status, message, sizeReduction, recommendation);
        }

        public boolean passedGate() {
            return status == GateStatus.PASSED;
        }

        public boolean blockedGate() {
            return status == GateStatus.BLOCKED || status == GateStatus.CRITICAL_WARNING;
        }
    }

    /**
     * Adapter interface for your real SystemCore later.
     */
    public interface TradingContext {
        String systemState();

        boolean killSwitchTriggered();

        boolean autoTradingEnabled();

        AccountView account();

        boolean brokerConnected();

        String selectedVenue();

        InstrumentRegistryView instrumentRegistry();

        boolean aiReviewEnabled();

        boolean liveTrading();

        MarketSnapshot marketSnapshot();
    }

    /**
     * Adapter interface for your real Account model.
     */
    public interface AccountView {
        boolean tradingEnabled();

        double equity();

        double freeMargin();

        double totalExposure();

        double exposureForSymbol(String symbol);

        double dailyPnl();

        double drawdownPercent();

        int openPositionCount();
    }

    /**
     * Adapter interface for your real InstrumentRegistry.
     */
    public interface InstrumentRegistryView {
        boolean isKnownInstrument(String symbol);

        boolean isTradable(String symbol, String venue);
    }

    /**
     * Adapter interface for your market snapshot.
     */
    public interface MarketSnapshot {
        QuoteView quote();

        Instant updatedAt();
    }

    /**
     * Adapter interface for your quote object.
     *
     * <p>
     * In your real system, this can wrap:
     * snapshot.getQuote().bid
     * snapshot.getQuote().ask
     * snapshot.getQuote().midPrice()
     */
    public interface QuoteView {
        double bid();

        double ask();

        double midPrice();
    }

    public record ValidationConfig(
            boolean stopAfterFirstBlocker,
            Duration maxMarketDataAge,
            double warningSpreadPercent,
            double maxSpreadPercent,
            double minQuantity,
            double maxQuantity,
            double minNotional,
            double warningSingleTradeNotionalToEquity,
            double maxSingleTradeNotionalToEquity,
            double warningFreeMarginUsage,
            double warningPortfolioExposureToEquity,
            double maxPortfolioExposureToEquity,
            double warningSymbolExposureToEquity,
            double maxSymbolExposureToEquity,
            double maxDailyLoss,
            double warningDrawdownPercent,
            double maxDrawdownPercent,
            boolean warnMarketOrders
    ) {
        public static ValidationConfig defaultConfig() {
            return new ValidationConfig(
                    false,
                    Duration.ofSeconds(20),
                    0.0030,
                    0.0100,
                    0.00000001,
                    1_000_000_000.0,
                    1.00,
                    0.05,
                    0.15,
                    0.50,
                    0.50,
                    2.00,
                    0.25,
                    0.75,
                    250.00,
                    0.10,
                    0.20,
                    true
            );
        }
    }
}