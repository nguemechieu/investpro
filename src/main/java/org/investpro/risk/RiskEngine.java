package org.investpro.risk;

import org.investpro.decision.BotTradeDecision;
import org.investpro.execution.OrderIntent;
import org.investpro.market.MarketContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RiskEngine {
    private final boolean requireStopLoss;
    private final BigDecimal minRewardRiskRatio;

    public RiskEngine() {
        this(true, new BigDecimal("1.20"));
    }

    public RiskEngine(boolean requireStopLoss, BigDecimal minRewardRiskRatio) {
        this.requireStopLoss = requireStopLoss;
        this.minRewardRiskRatio = minRewardRiskRatio == null ? BigDecimal.ONE : minRewardRiskRatio;
    }

    public RiskDecision evaluate(OrderIntent intent, BotTradeDecision botDecision, MarketContext context) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (intent == null) {
            return RiskDecision.rejected("Order intent is required.");
        }
        if (botDecision == null || !botDecision.tradeAllowed()) {
            blockers.add("Bot decision did not allow trading.");
        }
        if (intent.quantity().signum() <= 0) {
            blockers.add("Order quantity must be positive.");
        }
        if (requireStopLoss && intent.stopLoss().signum() <= 0) {
            blockers.add("Stop loss is required by risk policy.");
        }
        if (context == null) {
            blockers.add("Market context is required.");
        } else {
            if (context.accountSnapshot().buyingPower().compareTo(BigDecimal.ZERO) <= 0) {
                blockers.add("No buying power is available.");
            }
            if (context.hasPendingOrder()) {
                blockers.add("Pending order conflict detected.");
            }
            if (context.dataFreshnessStatus() != MarketContext.DataFreshnessStatus.FRESH) {
                blockers.add("Market data is stale.");
            }
        }
        if (rewardRisk(intent).compareTo(minRewardRiskRatio) < 0) {
            warnings.add("Reward/risk ratio is below preferred threshold.");
        }

        if (!blockers.isEmpty()) {
            return RiskDecision.rejected(blockers);
        }
        return RiskDecision.approvedWithWarnings("Risk checks approved.", intent.quantity().doubleValue(), 1.0, warnings, List.of());
    }

    private BigDecimal rewardRisk(OrderIntent intent) {
        if (intent == null || intent.limitPrice().signum() <= 0 || intent.stopLoss().signum() <= 0 || intent.takeProfit().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal risk = intent.limitPrice().subtract(intent.stopLoss()).abs();
        BigDecimal reward = intent.takeProfit().subtract(intent.limitPrice()).abs();
        if (risk.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return reward.divide(risk, java.math.MathContext.DECIMAL64);
    }
}
