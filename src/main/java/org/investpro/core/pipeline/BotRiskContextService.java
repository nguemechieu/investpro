package org.investpro.core.pipeline;

import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.enums.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Builds bot risk inputs from the selected execution account; unavailable state blocks entries. */
public final class BotRiskContextService {
    private BotRiskContextService() { }

    public static TradeRiskContext fromSignal(Exchange exchange, TradePair pair, StrategySignal signal) {
        Objects.requireNonNull(pair, "The signal instrument must be resolved by the exchange");
        double quantity = signal.getAmount();
        boolean automaticSizing = quantity == 0 && (signal.getMetadata() == null
                || !signal.getMetadata().containsKey("requested_units"));
        if (!(quantity > 0) && signal.getMetadata() != null && signal.getMetadata().containsKey("requested_units")) {
            Object units = signal.getMetadata().get("requested_units");
            try {
                quantity = units instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(units));
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Requested quantity is invalid", exception);
            }
        }
        if (!automaticSizing && (!(quantity > 0) || !Double.isFinite(quantity))) {
            throw new IllegalStateException("Strategy must specify a positive requested quantity");
        }
        // Missing prices/protection are not replaced by arbitrary entry or stop levels.
        TradeRiskContext context = TradeRiskContext.builder()
                .symbol(pair).broker(exchange.getName())
                .assetClass(String.valueOf(pair.getAssetClass()))
                .contractType(String.valueOf(pair.getContractType()))
                .requestedPositionSize(quantity).requestedLeverage(1)
                .entryPrice(signal.getEntryPrice()).currentPrice(signal.getEntryPrice())
                .bidPrice(number(signal, "bid")).askPrice(number(signal, "ask"))
                .stopLossPrice(signal.getStopLossPrice()).takeProfitPrice(signal.getTakeProfitPrice())
                .expectedWinRate(signal.getWinProbability() > 0 ? signal.getWinProbability() : signal.getConfidence())
                .expectedRewardRiskRatio(signal.getRiskRewardRatio())
                .riskProfile(RiskProfile.CONSERVATIVE)
                .marketBehavior(signal.getMarketBehavior() == null ? MarketBehavior.UNKNOWN : signal.getMarketBehavior())
                .executionStrategy(ExecutionStrategy.MARKET_ORDER)
                .liquidityProfile(pair.getLiquidityProfile() == null ? LiquidityProfile.NORMAL : pair.getLiquidityProfile())
                .psychologyProfile(PsychologyProfile.CAUTIOUS)
                .probabilityLevel(probability(signal.getConfidence()))
                .capitalProtection(CapitalProtection.STRICT_STOPS)
                .systemDesign(SystemDesign.TECHNICAL_ANALYSIS)
                .tradingSessionStatus(pair.getTradingSessionStatus())
                .volatility(number(signal, "volatility"))
                .build();
        TradeRiskContext snapshot = refresh(exchange, context);
        if (!automaticSizing) return snapshot;
        // Signal APIs intentionally permit amount=0: risk owns sizing in that case.
        double entry = snapshot.getEntryPrice();
        double riskSized = org.investpro.risk.PositionSizingEngine.calculateFixedFractionSize(
                snapshot.getAccountEquity(), snapshot.getMaxRiskPerTrade(), entry,
                snapshot.getStopLossPrice(), snapshot.getRiskProfile());
        double profileUnits = snapshot.getAccountEquity()
                * snapshot.getRiskProfile().getMaxPositionSizePercent() / 100.0 / entry;
        double requested = Math.min(riskSized, Math.min(profileUnits, snapshot.getAvailableCash() / entry));
        if (!(requested > 0) || !Double.isFinite(requested)) {
            throw new IllegalStateException("Cannot size the signal from the execution account and explicit protective prices");
        }
        return snapshot.toBuilder().requestedPositionSize(requested).build();
    }

    public static TradeRiskContext refresh(Exchange exchange, TradeRiskContext context) {
        Objects.requireNonNull(exchange, "Execution exchange is unavailable");
        try {
            var quote = exchange.fetchTicker(context.getSymbol()).get(10, TimeUnit.SECONDS);
            long now = System.currentTimeMillis();
            if (quote == null || quote.getTimestamp() < now - 30_000 || quote.getTimestamp() > now + 1_000
                    || !(quote.getBidPrice() > 0) || !(quote.getAskPrice() > quote.getBidPrice())
                    || !Double.isFinite(quote.getBidPrice()) || !Double.isFinite(quote.getAskPrice())) {
                throw new IllegalStateException("Fresh observed bid/ask quote is unavailable");
            }
            if (exchange.isBotPaperTrading()) exchange.updateLocalPaperMarketPrice(context.getSymbol(), quote.getLastPrice());
            Account account = exchange.isBotPaperTrading() ? exchange.localPaperAccount()
                    : exchange.fetchAccount().get(10, TimeUnit.SECONDS);
            Objects.requireNonNull(account, "Execution account snapshot is unavailable");
            if (!exchange.isBotPaperTrading() && !account.isTradingEnabled()) {
                throw new IllegalStateException("Account trading is disabled");
            }
            double equity = positive(account.getEquity(), account.getNav(), account.getPortfolioValue(), account.getTotalBalance());
            double cash = account.getAvailableBalance();
            if (!(equity > 0) || !Double.isFinite(cash) || cash < 0) {
                throw new IllegalStateException("Account equity or available balance is unavailable");
            }
            List<Position> positions = exchange.isBotPaperTrading() ? exchange.localPaperPositions()
                    : exchange.fetchAllPositions().get(10, TimeUnit.SECONDS);
            List<OpenOrder> orders = exchange.botOrderExecution().fetchAllOpenOrders().get(10, TimeUnit.SECONDS);
            double risk = openRisk(Objects.requireNonNull(positions, "Position snapshot is unavailable"));
            for (OpenOrder order : Objects.requireNonNull(orders, "Order snapshot is unavailable")) {
                if (order == null) throw new IllegalStateException("Invalid open order snapshot");
                if (order.getStatus() == OpenOrder.OrderStatus.FILLED || order.getStatus() == OpenOrder.OrderStatus.CANCELLED
                        || order.getStatus() == OpenOrder.OrderStatus.REJECTED || order.getStatus() == OpenOrder.OrderStatus.EXPIRED) continue;
                // Broker cash may already reserve pending orders. Include their full notional in
                // portfolio risk without subtracting that cash a second time.
                double remaining = order.getRemainingSize();
                if (remaining == 0) remaining = order.getSize() - order.getFilledSize();
                double price = order.getPrice();
                if (!(remaining > 0) || !Double.isFinite(remaining) || !(price > 0) || !Double.isFinite(price)) {
                    throw new IllegalStateException("Pending order exposure is unknown");
                }
                risk += Math.abs(remaining * price);
            }
            if (!Double.isFinite(risk)) throw new IllegalStateException("Portfolio risk is invalid");
            if (account.getAccountId() == null || account.getAccountId().isBlank()) {
                throw new IllegalStateException("Execution account identity is unavailable");
            }
            return context.toBuilder().executionAccountId(account.getAccountId())
                    .accountEquity(equity).accountBalance(account.getTotalBalance())
                    .bidPrice(quote.getBidPrice()).askPrice(quote.getAskPrice()).currentPrice(quote.getLastPrice())
                    .availableCash(cash).currentOpenRisk(risk).usedMargin(account.getMarginUsed())
                    .freeMargin(positive(account.getFreeMargin(), account.getMarginAvailable(), cash)).build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Account reconciliation interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("Account/position/order reconciliation failed; entry blocked", exception);
        }
    }

    static double openRisk(List<Position> positions) {
        double result = 0;
        for (Position position : positions) {
            if (position == null) throw new IllegalStateException("Invalid position snapshot");
            if (!position.isOpen()) continue;
            double price = positive(position.getCurrentPrice(), position.getEntryPrice());
            double quantity = position.getQuantity();
            if (!(price > 0) || !(quantity > 0) || !Double.isFinite(quantity)) {
                throw new IllegalStateException("Open position exposure is unknown");
            }
            double stop = position.getStopLoss();
            // Positions without verified protection consume full notional risk.
            result += (stop > 0 && Double.isFinite(stop) ? Math.abs(price - stop) : price) * quantity;
        }
        return result;
    }

    private static double positive(double... values) {
        for (double value : values) if (Double.isFinite(value) && value > 0) return value;
        return 0;
    }

    private static double number(StrategySignal signal, String key) {
        return signal.getMetadata() != null && signal.getMetadata().get(key) instanceof Number value
                ? value.doubleValue() : 0;
    }

    private static ProbabilityLevel probability(double confidence) {
        return confidence >= .9 ? ProbabilityLevel.VERY_HIGH : confidence >= .7 ? ProbabilityLevel.HIGH
                : confidence >= .5 ? ProbabilityLevel.MODERATE : confidence >= .3 ? ProbabilityLevel.LOW : ProbabilityLevel.VERY_LOW;
    }
}
