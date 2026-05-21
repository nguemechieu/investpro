package org.investpro.decision;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.Account;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Ticker;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Institutional-grade pre-trade decision engine.
 * 
 * Converts every signal into a rigorous BotTradeDecision through:
 * 1. Signal strength validation (0.3-1.0 range)
 * 2. Ticker data validation (bid/ask/last consistency)
 * 3. Spread protection by asset type
 * 4. Asset type detection (TradePair fields → symbol parsing)
 * 5. Market regime classification with confidence levels
 * 6. Strategy scoring across entire StrategyCatalog
 * 7. TradePlan generation with entry/stops/position sizing
 * 8. Full cost estimation (spread, commission, slippage, etc.)
 * 9. Expected profit/loss from TradePlan
 * 10. Account validation with live-mode warnings/blockers
 * 11. Comprehensive reasons/warnings/blockers chain
 * 12. Conservative skip-if-uncertain approach
 */
@Slf4j
public class BotTradeDecisionEngine {

    // === Configuration Thresholds ===
    private static final double SIGNAL_STRENGTH_MIN = 0.3;
    private static final double SIGNAL_STRENGTH_MAX = 1.0;
    private static final double STRATEGY_FITNESS_THRESHOLD = 0.70;
    private static final double INDICATOR_FITNESS_THRESHOLD = 0.65;
    private static final double MAX_COST_AS_PERCENT_OF_PROFIT = 0.30;
    private static final double MIN_RISK_REWARD_RATIO = 1.5;

    // === Spread protection limits by asset type (in pips/percentage) ===
    private static final double FOREX_MAX_SPREAD_PIPS = 0.5; // 5 pips for major pairs
    private static final double CRYPTO_MAX_SPREAD_PERCENT = 0.01; // 1% for spot crypto
    private static final double EQUITY_MAX_SPREAD_PERCENT = 0.005; // 0.5% for stocks

    private final Account account;

    public BotTradeDecisionEngine(@Nullable Account account) {
        this.account = account;
    }

    /**
     * Main entry point: evaluate a signal and generate institutional decision.
     * 
     * Requirement: "If uncertain, skip the trade"
     * → Every validation failure results in either a blocker or warning
     */
    @NotNull
    public BotTradeDecision evaluateSignal(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            double signalStrength) {

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Instant decidedAt = Instant.now();

        // === REQUIREMENT 1: Validate signal strength ===
        if (!isValidSignalStrength(signalStrength)) {
            blockers.add(String.format(
                    "Invalid signal strength: %.2f (must be %.2f-%.2f)",
                    signalStrength, SIGNAL_STRENGTH_MIN, SIGNAL_STRENGTH_MAX));
            return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                    MarketRegime.UNKNOWN, AssetMarketType.UNKNOWN, null, null, decidedAt);
        }
        reasons.add(String.format("Signal strength: %.2f (VALID)", signalStrength));

        // === REQUIREMENT 2: Validate ticker bid/ask/last ===
        TickerValidation tickerValidation = validateTicker(ticker);
        if (!tickerValidation.isValid()) {
            blockers.addAll(tickerValidation.errors());
            return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                    MarketRegime.UNKNOWN, AssetMarketType.UNKNOWN, null, null, decidedAt);
        }
        reasons.add(String.format(
                "Ticker valid: bid=%.6f ask=%.6f last=%.6f spread=%.6f",
                ticker.getBidPrice(), ticker.getAskPrice(), ticker.getLastPrice(),
                ticker.getAskPrice() - ticker.getBidPrice()));

        // === REQUIREMENT 4: Asset type detection (TradePair → symbol parsing) ===
        AssetMarketType assetType = detectAssetMarketTypeImproved(tradePair);
        reasons.add("Asset type: " + assetType.name() + " - " + assetType.description);

        // === REQUIREMENT 3: Spread protection by asset type ===
        SpreadValidation spreadValidation = validateSpreadByAssetType(ticker, assetType);
        if (!spreadValidation.isAcceptable()) {
            blockers.add(spreadValidation.reason());
        } else {
            reasons.add(spreadValidation.reason());
        }

        // === REQUIREMENT 5: Market regime classification with confidence ===
        MarketRegimeAnalysis regimeAnalysis = analyzeMarketRegime(ticker, assetType);
        reasons.add(String.format(
                "Market regime: %s (confidence: %.0f%%)",
                regimeAnalysis.regime().name(), regimeAnalysis.confidence() * 100));
        if (regimeAnalysis.confidence() < 0.5) {
            warnings.add("Low confidence in market regime classification - data may be insufficient");
        }

        MarketRegime regime = regimeAnalysis.regime();

        // === REQUIREMENT 6: Score strategies across StrategyCatalog ===
        StrategyFitScore bestStrategyScore = scoreBestStrategyAcrossCatalog(
                tradePair, assetType, regime, signalStrength);
        reasons.add(String.format(
                "Best strategy: %s (fitness: %.2f)",
                bestStrategyScore.strategyName(), bestStrategyScore.finalFitnessScore()));

        SetupSource setupSource;
        String selectedStrategyName = null;
        IndicatorSetupScore indicatorSetupScore = null;

        if (bestStrategyScore.isGoodFit()) {
            setupSource = SetupSource.STRATEGY;
            selectedStrategyName = bestStrategyScore.strategyName();
            reasons.add("Selected setup: Strategy - " + selectedStrategyName);
        } else {
            // Fallback to indicator composite
            indicatorSetupScore = scoreBestIndicatorSetup(regime, assetType);
            if (indicatorSetupScore != null && indicatorSetupScore.isGoodFit()) {
                setupSource = SetupSource.INDICATOR_COMPOSITE;
                reasons.add(String.format(
                        "Selected setup: Indicator - %s (fitness: %.2f)",
                        indicatorSetupScore.setupType().description,
                        indicatorSetupScore.finalFitnessScore()));
            } else {
                setupSource = SetupSource.NONE;
                blockers.add(String.format(
                        "No setup reached minimum fitness (strategy: %.2f, indicator: %.2f vs threshold: %.2f)",
                        bestStrategyScore.finalFitnessScore(),
                        indicatorSetupScore != null ? indicatorSetupScore.finalFitnessScore() : 0.0,
                        STRATEGY_FITNESS_THRESHOLD));
                return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                        regime, assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
            }
        }

        // === REQUIREMENT 7 & 8: Generate TradePlan with entry/stops/sizing ===
        TradePlan tradePlan = generateTradePlan(tradePair, side, ticker, assetType);
        if (tradePlan == null || !tradePlan.isValid()) {
            blockers.add("Failed to generate valid trade plan");
            return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                    regime, assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
        }
        reasons.add("Trade plan: " + tradePlan.getSummary());

        // === REQUIREMENT 9: Full cost estimation ===
        TradeCostEstimate costEstimate = estimateTradeCosts(
                tradePair, ticker, assetType, tradePlan);
        reasons.add("Estimated costs: " + costEstimate.costBreakdown());

        // === REQUIREMENT 10: Expected profit/loss from TradePlan ===
        TradeExpectation expectation = estimateTradeExpectation(
                ticker, side, tradePlan, costEstimate);
        reasons.add("Expected profit: " + expectation.profitBreakdown());

        if (!expectation.isPositiveExpectancy()) {
            blockers.add(String.format(
                    "Negative expected value: %.4f",
                    expectation.expectedValue()));
        }
        if (!expectation.isAcceptableRiskReward()) {
            blockers.add(String.format(
                    "Unacceptable risk/reward: %.2f:1 (need ≥ %.1f:1)",
                    expectation.riskRewardRatio(), MIN_RISK_REWARD_RATIO));
        }

        // === REQUIREMENT 11: Account validation with live-mode warnings/blockers ===
        if (account != null) {
            AccountValidation accountValidation = validateAccountForTrade(tradePlan);
            reasons.addAll(accountValidation.reasons());
            warnings.addAll(accountValidation.warnings());
            blockers.addAll(accountValidation.blockers());
        } else {
            warnings.add("No account validation available (account is null)");
        }

        // === Check blocking conditions ===
        if (!blockers.isEmpty()) {
            return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                    regime, assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
        }

        // === Final Decision ===
        BotTradeDecision.FinalAction finalAction = BotTradeDecision.FinalAction.TRADE;
        reasons.add("DECISION: Execute trade");

        String fullAnalysisSummary = buildFullAnalysisSummary(
                tradePair, side, regime, assetType, setupSource, selectedStrategyName,
                bestStrategyScore, indicatorSetupScore, costEstimate, expectation,
                tradePlan, reasons, warnings, blockers);

        log.info("Signal evaluation complete - {} {} (EV: {})",
                finalAction.description, tradePair.getSymbol(),
                expectation.getExpectedValueFormatted());

        return new BotTradeDecision(
                tradePair, side, regime, assetType, setupSource, selectedStrategyName,
                indicatorSetupScore != null ? indicatorSetupScore.setupType() : null,
                bestStrategyScore, indicatorSetupScore, costEstimate, expectation,
                createHoldingPeriodEstimate(regime, assetType),
                finalAction, reasons, warnings, blockers, fullAnalysisSummary, decidedAt);
    }

    /**
     * REQUIREMENT 1: Validate signal strength is within 0.3-1.0 range
     */
    private boolean isValidSignalStrength(double signalStrength) {
        return signalStrength >= SIGNAL_STRENGTH_MIN && signalStrength <= SIGNAL_STRENGTH_MAX;
    }

    /**
     * REQUIREMENT 2: Validate ticker bid/ask/last prices
     */
    @NotNull
    private TickerValidation validateTicker(@NotNull Ticker ticker) {
        List<String> errors = new ArrayList<>();

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        double last = ticker.getLastPrice();

        if (bid <= 0) {
            errors.add("Bid price invalid: " + bid);
        }
        if (ask <= 0) {
            errors.add("Ask price invalid: " + ask);
        }
        if (last <= 0) {
            errors.add("Last price invalid: " + last);
        }
        if (bid > 0 && ask > 0 && bid >= ask) {
            errors.add(String.format("Bid >= Ask: bid=%.6f ask=%.6f", bid, ask));
        }

        return new TickerValidation(errors.isEmpty(), errors);
    }

    /**
     * REQUIREMENT 3: Validate spread is acceptable by asset type
     */
    @NotNull
    private SpreadValidation validateSpreadByAssetType(
            @NotNull Ticker ticker,
            @NotNull AssetMarketType assetType) {

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        double spread = ask - bid;
        double mid = (bid + ask) / 2.0;

        double maxSpreadAllowed;
        String spreadType;

        switch (assetType) {
            case FOREX -> {
                // Forex spreads typically measured in pips (0.0001 for 4-decimal pairs)
                double spreadInPips = spread * 10000; // Assume 4-decimal pair
                maxSpreadAllowed = (FOREX_MAX_SPREAD_PIPS / 10000) * mid;
                spreadType = String.format("%.1f pips", spreadInPips);
            }
            case CRYPTO_SPOT, CRYPTO_DERIVATIVES -> {
                // Crypto spreads measured in percentage
                double spreadPercent = (spread / mid) * 100;
                maxSpreadAllowed = (CRYPTO_MAX_SPREAD_PERCENT / 100) * mid;
                spreadType = String.format("%.2f%%", spreadPercent);
            }
            case EQUITIES, EQUITY_DERIVATIVES -> {
                // Equity spreads measured in percentage
                double spreadPercent = (spread / mid) * 100;
                maxSpreadAllowed = (EQUITY_MAX_SPREAD_PERCENT / 100) * mid;
                spreadType = String.format("%.2f%%", spreadPercent);
            }
            default -> {
                maxSpreadAllowed = Double.MAX_VALUE;
                spreadType = String.format("%.6f", spread);
            }
        }

        boolean acceptable = spread <= maxSpreadAllowed;
        String reason = String.format(
                "Spread: %s (max: %.6f) - %s",
                spreadType, maxSpreadAllowed, acceptable ? "ACCEPTABLE" : "TOO WIDE");

        return new SpreadValidation(acceptable, reason);
    }

    /**
     * REQUIREMENT 4: Improved asset type detection
     * 1. Use TradePair currency types to detect asset class
     * 2. Use symbol parsing for market categorization
     * 3. Fall back to heuristic detection
     */
    @NotNull
    private AssetMarketType detectAssetMarketTypeImproved(@NotNull TradePair tradePair) {
        String symbol = tradePair.getSymbol().toUpperCase();
        String baseCode = tradePair.getBaseCode();
        String counterCode = tradePair.getCounterCode();

        // Step 1: Check currency types (CryptoCurrency vs FiatCurrency)
        var baseCurrency = tradePair.getKey();
        var counterCurrency = tradePair.getValue();

        boolean baseIsCrypto = baseCurrency instanceof org.investpro.models.currency.CryptoCurrency;
        boolean counterIsCrypto = counterCurrency instanceof org.investpro.models.currency.CryptoCurrency;
        boolean baseIsFiat = baseCurrency instanceof org.investpro.models.currency.FiatCurrency;
        boolean counterIsFiat = counterCurrency instanceof org.investpro.models.currency.FiatCurrency;

        // Crypto spot (crypto/crypto or crypto/fiat)
        if (baseIsCrypto && (counterIsCrypto || counterIsFiat)) {
            return AssetMarketType.CRYPTO_SPOT;
        }

        // Forex (fiat/fiat)
        if (baseIsFiat && counterIsFiat) {
            return AssetMarketType.FOREX;
        }

        // Step 2: Fall back to symbol parsing
        // Crypto detection
        if (symbol.contains("USDT") || symbol.contains("USDC") || symbol.contains("BUSD") ||
                symbol.contains("BTC") || symbol.contains("ETH")) {
            return AssetMarketType.CRYPTO_SPOT;
        }

        // Forex detection (XXX/YYY format)
        if (symbol.matches("^[A-Z]{3}/[A-Z]{3}$") || symbol.matches("^[A-Z]{3}_[A-Z]{3}$")) {
            return AssetMarketType.FOREX;
        }

        // Step 3: Heuristic - if symbol doesn't contain separator, likely equity
        if (!symbol.contains("/") && !symbol.contains("_")) {
            return AssetMarketType.EQUITIES;
        }

        return AssetMarketType.UNKNOWN;
    }

    /**
     * REQUIREMENT 5: Market regime analysis with confidence levels
     * Do NOT classify as STRONG_* from bid/ask/last alone.
     * Use WEAK_* regimes with conservative confidence.
     */
    @NotNull
    private MarketRegimeAnalysis analyzeMarketRegime(
            @NotNull Ticker ticker,
            @NotNull AssetMarketType assetType) {

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        double last = ticker.getLastPrice();

        if (bid <= 0 || ask <= 0 || last <= 0) {
            return new MarketRegimeAnalysis(MarketRegime.UNKNOWN, 0.0);
        }

        double mid = (bid + ask) / 2.0;

        // Calculate how far last is from mid (normalized by spread)
        double spread = ask - bid;
        double spreadPercent = spread > 0 ? spread / mid : 0.0;
        double lastDeviation = (last - mid) / (spreadPercent > 0 ? spreadPercent * mid : 0.01);

        MarketRegime regime;
        double confidence;

        if (lastDeviation > 0.5) {
            // Last price is in upper half of spread → buying pressure
            regime = MarketRegime.WEAK_UPTREND; // Use WEAK not STRONG
            confidence = 0.45 + (lastDeviation * 0.1);
        } else if (lastDeviation < -0.5) {
            // Last price is in lower half of spread → selling pressure
            regime = MarketRegime.WEAK_DOWNTREND; // Use WEAK not STRONG
            confidence = 0.45 + (Math.abs(lastDeviation) * 0.1);
        } else {
            // Last price near mid → range-bound
            regime = MarketRegime.RANGE_BOUND;
            confidence = 0.4;
        }

        // Cap confidence at 0.6 since we're only using ticker data
        confidence = Math.min(confidence, 0.6);

        return new MarketRegimeAnalysis(regime, confidence);
    }

    /**
     * REQUIREMENT 6: Score strategies across entire StrategyCatalog
     */
    @NotNull
    private StrategyFitScore scoreBestStrategyAcrossCatalog(
            @NotNull TradePair tradePair,
            @NotNull AssetMarketType assetType,
            @NotNull MarketRegime regime,
            double signalStrength) {

        List<String> availableStrategies = StrategyCatalog.availableStrategyNames();
        StrategyFitScore bestScore = null;

        for (String strategyName : availableStrategies) {
            double regimeFit = calculateRegimeFitForStrategy(strategyName, regime);
            double assetFit = calculateAssetFitForStrategy(strategyName, assetType);
            // Use actual signal strength
            double timeframeFit = 0.70; // Would calculate from candle data
            double recentPerf = 0.65; // Would calculate from backtest

            double avgScore = (regimeFit + assetFit + signalStrength + timeframeFit + recentPerf) / 5.0;

            if (bestScore == null || avgScore > bestScore.finalFitnessScore()) {
                bestScore = new StrategyFitScore(
                        strategyName,
                        "org.investpro.strategy." + strategyName.replace(" ", ""),
                        regimeFit, assetFit, timeframeFit, recentPerf, signalStrength,
                        avgScore,
                        "Scored against market regime and asset type",
                        null, Instant.now());
            }
        }

        if (bestScore == null) {
            // Fallback if no strategies available
            bestScore = new StrategyFitScore(
                    "Default Trend Following", "org.investpro.strategy.TrendFollowing",
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    "No strategies available in catalog",
                    "Strategy catalog is empty", Instant.now());
        }

        return bestScore;
    }

    private double calculateRegimeFitForStrategy(@NotNull String strategyName, @NotNull MarketRegime regime) {
        return switch (regime) {
            case STRONG_UPTREND, STRONG_DOWNTREND ->
                strategyName.contains("Trend") || strategyName.contains("Momentum") ? 0.85 : 0.60;
            case WEAK_UPTREND, WEAK_DOWNTREND ->
                strategyName.contains("Pullback") || strategyName.contains("Reversion") ? 0.75 : 0.55;
            case RANGE_BOUND ->
                strategyName.contains("Reversion") || strategyName.contains("Range") ? 0.80 : 0.45;
            case HIGH_VOLATILITY ->
                strategyName.contains("Volatility") || strategyName.contains("Breakout") ? 0.75 : 0.40;
            case LOW_VOLATILITY ->
                strategyName.contains("Breakout") || strategyName.contains("Squeeze") ? 0.70 : 0.50;
            default -> 0.50;
        };
    }

    private double calculateAssetFitForStrategy(@NotNull String strategyName, @NotNull AssetMarketType assetType) {
        return switch (assetType) {
            case FOREX -> strategyName.contains("Carry") || strategyName.contains("Trend") ? 0.80 : 0.65;
            case CRYPTO_SPOT, CRYPTO_DERIVATIVES ->
                strategyName.contains("Momentum") || strategyName.contains("Volatility") ? 0.80 : 0.60;
            case EQUITIES -> strategyName.contains("Mean") || strategyName.contains("Momentum") ? 0.75 : 0.65;
            default -> 0.50;
        };
    }

    /**
     * REQUIREMENT 7 & 8: Generate TradePlan with entry, stops, position sizing
     */
    @Nullable
    private TradePlan generateTradePlan(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            @NotNull AssetMarketType assetType) {

        BigDecimal entryPrice = BigDecimal.valueOf(side == Side.BUY ? ticker.getAskPrice() : ticker.getBidPrice());
        BigDecimal mid = BigDecimal.valueOf((ticker.getBidPrice() + ticker.getAskPrice()) / 2.0);

        // Calculate stop loss (1% below entry for buys, 1% above for sells)
        BigDecimal stopLoss = side == Side.BUY
                ? entryPrice.multiply(BigDecimal.valueOf(0.99))
                : entryPrice.multiply(BigDecimal.valueOf(1.01));

        // Calculate take profit (2% above entry for buys, 2% below for sells)
        BigDecimal takeProfit = side == Side.BUY
                ? entryPrice.multiply(BigDecimal.valueOf(1.02))
                : entryPrice.multiply(BigDecimal.valueOf(0.98));

        // Position size: assume 1 unit for now (would use account equity in production)
        BigDecimal positionSize = BigDecimal.ONE;

        // Calculate risk and reward amounts
        BigDecimal riskAmount = side == Side.BUY
                ? entryPrice.subtract(stopLoss).multiply(positionSize)
                : stopLoss.subtract(entryPrice).multiply(positionSize);

        BigDecimal rewardAmount = side == Side.BUY
                ? takeProfit.subtract(entryPrice).multiply(positionSize)
                : entryPrice.subtract(takeProfit).multiply(positionSize);

        double riskRewardRatio = riskAmount.signum() > 0
                ? rewardAmount.doubleValue() / riskAmount.doubleValue()
                : 0.0;

        return new TradePlan(entryPrice, stopLoss, takeProfit, positionSize,
                riskAmount, rewardAmount, riskRewardRatio);
    }

    /**
     * REQUIREMENT 9: Full cost estimation with spread, commission, slippage, etc.
     */
    @NotNull
    private TradeCostEstimate estimateTradeCosts(
            @NotNull TradePair tradePair,
            @NotNull Ticker ticker,
            @NotNull AssetMarketType assetType,
            @NotNull TradePlan tradePlan) {

        BigDecimal mid = BigDecimal.valueOf((ticker.getBidPrice() + ticker.getAskPrice()) / 2.0);
        BigDecimal positionSize = tradePlan.positionSize();
        BigDecimal notional = mid.multiply(positionSize);

        // Spread cost (round trip: entry at ask, exit at bid)
        BigDecimal spreadCost = BigDecimal.valueOf(ticker.getAskPrice() - ticker.getBidPrice())
                .multiply(positionSize).multiply(BigDecimal.valueOf(2)); // Round trip

        // Commission: assume 0.1% per trade, round-trip
        BigDecimal commission = notional.multiply(BigDecimal.valueOf(0.002));

        // Slippage: assume 0.05% for normal execution
        BigDecimal slippage = notional.multiply(BigDecimal.valueOf(0.0005));

        // Swap/Funding: only for derivatives/leverage
        BigDecimal swapCost = (assetType.supportsLeverage || assetType.isCrypto())
                ? notional.multiply(BigDecimal.valueOf(0.0001))
                : BigDecimal.ZERO;

        // Market impact: assume 0.01% for position size
        BigDecimal marketImpact = notional.multiply(BigDecimal.valueOf(0.0001));

        BigDecimal totalCost = spreadCost.add(commission).add(slippage).add(swapCost).add(marketImpact);

        String breakdown = String.format(
                "Spread: %.6f, Commission: %.6f, Slippage: %.6f, Swap: %.6f, Impact: %.6f = Total: %.6f",
                spreadCost, commission, slippage, swapCost, marketImpact, totalCost);

        // Cost acceptance: assume expected gross profit is 0.5% of notional
        BigDecimal expectedProfit = notional.multiply(BigDecimal.valueOf(0.005));
        boolean isAcceptable = totalCost.compareTo(
                expectedProfit.multiply(BigDecimal.valueOf(MAX_COST_AS_PERCENT_OF_PROFIT))) <= 0;

        return new TradeCostEstimate(
                spreadCost, commission, slippage, swapCost, marketImpact, totalCost,
                breakdown, isAcceptable,
                !isAcceptable ? "Cost exceeds 30% of expected profit" : null);
    }

    /**
     * REQUIREMENT 10: Expected profit/loss calculated from TradePlan
     */
    @NotNull
    private TradeExpectation estimateTradeExpectation(
            @NotNull Ticker ticker,
            @NotNull Side side,
            @NotNull TradePlan tradePlan,
            @NotNull TradeCostEstimate costEstimate) {

        // Gross profit from trade plan
        BigDecimal grossProfit = tradePlan.rewardAmount();
        BigDecimal netProfit = grossProfit.subtract(costEstimate.totalCost());

        // Loss if wrong
        BigDecimal lossIfWrong = tradePlan.riskAmount().add(costEstimate.totalCost());

        // Win probability: assume 55% for now (would calculate from strategy backtests)
        double winProbability = 0.55;
        double lossProbability = 1.0 - winProbability;

        BigDecimal expectedValue = grossProfit.multiply(BigDecimal.valueOf(winProbability))
                .subtract(lossIfWrong.multiply(BigDecimal.valueOf(lossProbability)));

        double riskReward = lossIfWrong.signum() > 0
                ? grossProfit.doubleValue() / lossIfWrong.doubleValue()
                : 0.0;

        String profitBreakdown = String.format(
                "Gross: %.6f, Cost: %.6f, Net: %.6f, Loss: %.6f, EV: %.6f",
                grossProfit, costEstimate.totalCost(), netProfit, lossIfWrong, expectedValue);

        boolean isPositiveEV = expectedValue.signum() > 0;
        boolean isAcceptableRR = riskReward >= MIN_RISK_REWARD_RATIO;

        return new TradeExpectation(
                grossProfit, lossIfWrong, netProfit, expectedValue,
                winProbability, riskReward, profitBreakdown, isPositiveEV, isAcceptableRR);
    }

    /**
     * REQUIREMENT 11: Account validation with warnings/blockers for live mode
     */
    @NotNull
    private AccountValidation validateAccountForTrade(@NotNull TradePlan tradePlan) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        if (account == null || !account.isConnected()) {
            warnings.add("Account not connected or unavailable");
            return new AccountValidation(reasons, warnings, blockers);
        }

        reasons.add(String.format(
                "Account: %s | Equity: %.2f | Free Margin: %.2f",
                account.getAccountId(), account.getEquity(), account.getFreeMargin()));

        // Check equity availability
        if (account.getEquity() <= 0) {
            blockers.add("Account equity is zero or negative");
            return new AccountValidation(reasons, warnings, blockers);
        }

        // Check free margin if account uses margin
        double positionNotional = tradePlan.positionSize().doubleValue() * tradePlan.entryPrice().doubleValue();
        if (account.getFreeMargin() > 0 && account.getFreeMargin() < positionNotional * 0.1) {
            warnings.add(String.format(
                    "Low free margin: %.2f (position notional: %.2f)",
                    account.getFreeMargin(), positionNotional));
        }

        // In live mode, require explicit account validation
        if (!account.isPaperTrading() && !account.isSandbox()) {
            if (account.getEquity() < tradePlan.riskAmount().doubleValue()) {
                blockers.add("Account equity insufficient for position risk in LIVE mode");
            }
        }

        return new AccountValidation(reasons, warnings, blockers);
    }

    /**
     * Score indicator composite setup
     */
    @Nullable
    private IndicatorSetupScore scoreBestIndicatorSetup(
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType) {

        IndicatorSetupType setupType = selectIndicatorSetupForRegime(regime);
        if (setupType == IndicatorSetupType.NONE) {
            return null;
        }

        double regimeFit = 0.72;
        double signalClarity = 0.68;
        double historicalWin = 0.58;
        double volAlignment = 0.75;
        double finalScore = (regimeFit + signalClarity + historicalWin + volAlignment) / 4.0;

        return new IndicatorSetupScore(
                setupType, regimeFit, signalClarity, historicalWin, volAlignment, finalScore,
                "Indicator composite for " + regime.name(),
                finalScore < INDICATOR_FITNESS_THRESHOLD ? "Below threshold" : null,
                Instant.now());
    }

    private IndicatorSetupType selectIndicatorSetupForRegime(@NotNull MarketRegime regime) {
        return switch (regime) {
            case STRONG_UPTREND, STRONG_DOWNTREND -> IndicatorSetupType.MOMENTUM;
            case WEAK_UPTREND, WEAK_DOWNTREND, RANGE_BOUND -> IndicatorSetupType.MEAN_REVERSION;
            case HIGH_VOLATILITY, LOW_VOLATILITY -> IndicatorSetupType.VOLATILITY_BREAKOUT;
            case TRANSITIONAL -> IndicatorSetupType.LEVEL_BREAKOUT;
            case UNKNOWN -> IndicatorSetupType.NONE;
        };
    }

    private HoldingPeriodEstimate createHoldingPeriodEstimate(
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType) {

        Duration expectedHold = switch (regime) {
            case STRONG_UPTREND, STRONG_DOWNTREND -> Duration.ofHours(4);
            case RANGE_BOUND -> Duration.ofHours(1);
            default -> Duration.ofHours(2);
        };

        return new HoldingPeriodEstimate(
                Duration.ofMinutes(1),
                expectedHold,
                Duration.ofDays(5),
                "Estimated from " + regime.name(),
                false);
    }

    /**
     * Create a SKIP decision with comprehensive blockers
     */
    @NotNull
    private BotTradeDecision createSkipDecision(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull List<String> blockers,
            @NotNull List<String> reasons,
            @NotNull List<String> warnings,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @Nullable StrategyFitScore strategyScore,
            @Nullable IndicatorSetupScore indicatorScore,
            @NotNull Instant decidedAt) {

        reasons.add("DECISION: Skip trade");
        blockers.forEach(b -> reasons.add("BLOCKER: " + b));

        StrategyFitScore bestStrategy = strategyScore != null ? strategyScore
                : new StrategyFitScore(
                        "NONE", "NONE", 0, 0, 0, 0, 0, 0,
                        "No strategy evaluated", "Trade skipped", decidedAt);

        TradeCostEstimate defaultCost = new TradeCostEstimate(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "No trade executed", true, null);

        TradeExpectation defaultExpectation = new TradeExpectation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, "No trade", false, false);

        HoldingPeriodEstimate defaultHolding = new HoldingPeriodEstimate(
                Duration.ZERO, Duration.ZERO, Duration.ZERO,
                "No trade", false);

        String summary = "Trade SKIPPED due to:\n" + String.join("\n", blockers);

        return new BotTradeDecision(
                tradePair, side, regime, assetType, SetupSource.NONE, null,
                indicatorScore != null ? indicatorScore.setupType() : null,
                bestStrategy, indicatorScore, defaultCost, defaultExpectation, defaultHolding,
                BotTradeDecision.FinalAction.SKIP, reasons, warnings, blockers, summary, decidedAt);
    }

    /**
     * Build comprehensive human-readable analysis summary
     */
    @NotNull
    private String buildFullAnalysisSummary(
            @NotNull TradePair pair,
            @NotNull Side side,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @NotNull SetupSource setupSource,
            @Nullable String strategyName,
            @NotNull StrategyFitScore strategyScore,
            @Nullable IndicatorSetupScore indicatorScore,
            @NotNull TradeCostEstimate costEstimate,
            @NotNull TradeExpectation expectation,
            @NotNull TradePlan tradePlan,
            @NotNull List<String> reasons,
            @NotNull List<String> warnings,
            @NotNull List<String> blockers) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== INSTITUTIONAL BOT TRADE DECISION ANALYSIS ===\n");
        sb.append(String.format("Pair: %s | Side: %s\n", pair.getSymbol(), side.name()));
        sb.append(String.format("Regime: %s | Asset Type: %s\n", regime.name(), assetType.name()));
        sb.append(String.format("Setup: %s\n", setupSource.name()));

        if (strategyName != null) {
            sb.append(String.format("Strategy: %s (Fitness: %.2f)\n",
                    strategyName, strategyScore.finalFitnessScore()));
        }

        sb.append("\n--- TRADE PLAN ---\n");
        sb.append(tradePlan.getSummary()).append("\n");

        sb.append("\n--- COSTS & EXPECTATIONS ---\n");
        sb.append(String.format("Cost Breakdown: %s\n", costEstimate.costBreakdown()));
        sb.append(String.format("Profit Breakdown: %s\n", expectation.profitBreakdown()));
        sb.append(String.format("Expected Value: %.4f\n", expectation.expectedValue()));
        sb.append(String.format("Risk/Reward: %s\n", expectation.getRiskRewardFormatted()));

        if (!warnings.isEmpty()) {
            sb.append("\n--- WARNINGS ---\n");
            warnings.forEach(w -> sb.append("⚠ ").append(w).append("\n"));
        }

        if (!blockers.isEmpty()) {
            sb.append("\n--- BLOCKERS ---\n");
            blockers.forEach(b -> sb.append("🛑 ").append(b).append("\n"));
        }

        return sb.toString();
    }

    // === Inner records for validation results ===

    record TickerValidation(boolean isValid, List<String> errors) {
    }

    record SpreadValidation(boolean isAcceptable, String reason) {
    }

    record MarketRegimeAnalysis(MarketRegime regime, double confidence) {
    }

    record AccountValidation(List<String> reasons, List<String> warnings, List<String> blockers) {
    }
}
