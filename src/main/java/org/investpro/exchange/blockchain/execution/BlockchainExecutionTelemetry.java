package org.investpro.exchange.blockchain.execution;

import lombok.Data;
import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Aggregated blockchain execution telemetry.
 */
@Data
public class BlockchainExecutionTelemetry {

    private final LongAdder submittedTransactions = new LongAdder();
    private final LongAdder confirmedTransactions = new LongAdder();
    private final LongAdder failedTransactions = new LongAdder();
    private final LongAdder totalConfirmationTimeMs = new LongAdder();
    private final LongAdder confirmationSamples = new LongAdder();
    private final LongAdder totalFeeUnits = new LongAdder();
    private final LongAdder feeSamples = new LongAdder();
    private final ConcurrentMap<String, Long> networkLatencyMs = new ConcurrentHashMap<>();

    public void recordSubmitted() {
        submittedTransactions.increment();
    }

    public void recordResult(BlockchainTransactionResult result) {
        if (result == null) {
            return;
        }

        if (result.isConfirmed()) {
            confirmedTransactions.increment();
            Instant confirmedAt = result.confirmedAt();
            if (confirmedAt != null) {
                long millis = Duration.between(result.submittedAt(), confirmedAt).toMillis();
                totalConfirmationTimeMs.add(Math.max(0L, millis));
                confirmationSamples.increment();
            }
            if (result.feeUnitsConsumed() != null) {
                totalFeeUnits.add(Math.max(0L, result.feeUnitsConsumed()));
                feeSamples.increment();
            }
        } else if (result.isFailed()) {
            failedTransactions.increment();
        }
    }

    public void recordNetworkLatency(String networkId, long latencyMs) {
        if (networkId == null || networkId.isBlank()) {
            return;
        }
        networkLatencyMs.put(networkId.toUpperCase(), Math.max(0L, latencyMs));
    }

    public long getSubmittedTransactions() {
        return submittedTransactions.sum();
    }

    public long getConfirmedTransactions() {
        return confirmedTransactions.sum();
    }

    public long getFailedTransactions() {
        return failedTransactions.sum();
    }

    public double getAverageConfirmationTimeMs() {
        long samples = confirmationSamples.sum();
        return samples == 0L ? 0.0 : ((double) totalConfirmationTimeMs.sum()) / samples;
    }

    public double getAverageFeeUnits() {
        long samples = feeSamples.sum();
        return samples == 0L ? 0.0 : ((double) totalFeeUnits.sum()) / samples;
    }


}
