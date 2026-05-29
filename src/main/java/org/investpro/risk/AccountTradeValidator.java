package org.investpro.risk;

import org.investpro.decision.TradePlan;
import org.investpro.models.Account;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates account constraints for a generated trade plan.
 */
public class AccountTradeValidator {

    @NotNull
    public AccountValidationResult validate(@Nullable Account account, @NotNull TradePlan tradePlan) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        if (account == null) {
            warnings.add("No account validation available (account is null)");
            return new AccountValidationResult(List.copyOf(reasons), List.copyOf(warnings), List.copyOf(blockers));
        }

        if (!account.isConnected()) {
            warnings.add("Account not connected");
            return new AccountValidationResult(List.copyOf(reasons), List.copyOf(warnings), List.copyOf(blockers));
        }

        reasons.add(String.format(
                "Account: %s | Equity: %.2f | Free Margin: %.2f",
                account.getAccountId(), account.getEquity(), account.getFreeMargin()));

        if (account.getEquity() <= 0) {
            blockers.add("Account equity is zero or negative");
            return new AccountValidationResult(List.copyOf(reasons), List.copyOf(warnings), List.copyOf(blockers));
        }

        double positionNotional = tradePlan.positionSize().doubleValue() * tradePlan.entryPrice().doubleValue();
        if (account.getFreeMargin() > 0 && account.getFreeMargin() < positionNotional * 0.1) {
            warnings.add(String.format(
                    "Low free margin: %.2f (position notional: %.2f)",
                    account.getFreeMargin(), positionNotional));
        }

        if (!account.isPaperTrading() && !account.isSandbox()) {
            if (account.getEquity() < tradePlan.riskAmount().doubleValue()) {
                blockers.add("Account equity insufficient for position risk in LIVE mode");
            }
        }

        return new AccountValidationResult(List.copyOf(reasons), List.copyOf(warnings), List.copyOf(blockers));
    }
}
