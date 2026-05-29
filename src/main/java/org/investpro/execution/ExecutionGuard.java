package org.investpro.execution;

import org.investpro.decision.BotTradeDecision;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Last pre-submission guard that confirms a decision can produce an order
 * intent.
 */
public class ExecutionGuard {

    @NotNull
    public ExecutionGuardDecision evaluate(@NotNull BotTradeDecision decision) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>(decision.warnings());

        if (decision.finalAction() != BotTradeDecision.FinalAction.TRADE
                && decision.finalAction() != BotTradeDecision.FinalAction.REDUCE_SIZE
                && decision.finalAction() != BotTradeDecision.FinalAction.CLOSE) {
            blockers.add("Decision is not executable: " + decision.finalAction());
        }

        blockers.addAll(decision.blockers());
        boolean approved = blockers.isEmpty();
        String reason = approved ? "Execution guard approved" : "Execution guard blocked due to constraints";

        return new ExecutionGuardDecision(approved, List.copyOf(blockers), List.copyOf(warnings), reason);
    }
}
