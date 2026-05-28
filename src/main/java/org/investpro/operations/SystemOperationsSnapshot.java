package org.investpro.operations;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.execution.ExecutionMode;
import org.investpro.reconciliation.ReconciliationStatus;

import java.time.Instant;
import java.util.List;


public record SystemOperationsSnapshot(
        PipelineComponentStatus marketDataStatus,
        PipelineComponentStatus strategyEngineStatus,
        PipelineComponentStatus botDecisionEngineStatus,
        PipelineComponentStatus riskEngineStatus,
        PipelineComponentStatus executionStatus,
        PipelineComponentStatus exchangeConnectionStatus,
        PipelineComponentStatus eventStoreStatus,
        ReconciliationStatus reconciliationStatus,
        ExecutionMode executionMode,
        BrokerActivityEvent latestBrokerActivityEvent,
        int pendingOrdersCount,
        int openPositionsCount,
        List<String> warnings,
        List<String> errors,
        Instant lastUpdatedAt) {

    public SystemOperationsSnapshot {
        marketDataStatus = status(marketDataStatus);
        strategyEngineStatus = status(strategyEngineStatus);
        botDecisionEngineStatus = status(botDecisionEngineStatus);
        riskEngineStatus = status(riskEngineStatus);
        executionStatus = status(executionStatus);
        exchangeConnectionStatus = status(exchangeConnectionStatus);
        eventStoreStatus = status(eventStoreStatus);
        reconciliationStatus = reconciliationStatus == null ? ReconciliationStatus.UNKNOWN : reconciliationStatus;
        executionMode = executionMode == null ? ExecutionMode.PAPER : executionMode;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
        lastUpdatedAt = lastUpdatedAt == null ? Instant.now() : lastUpdatedAt;
    }

    private static PipelineComponentStatus status(PipelineComponentStatus status) {
        return status == null ? PipelineComponentStatus.UNKNOWN : status;
    }
}
