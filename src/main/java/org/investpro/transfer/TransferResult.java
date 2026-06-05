package org.investpro.transfer;

import java.math.BigDecimal;
import java.time.Instant;

public class TransferResult {

    private final String transactionId;
    private final TransferRequest request;
    private final BigDecimal fee;
    private final BigDecimal netAmount;
    private final String estimatedArrival;
    private final Instant createdAt;

    private volatile TransferStatus status;
    private volatile String message;
    private volatile Instant lastUpdated;
    private volatile double progress;

    public TransferResult(String transactionId,
            TransferRequest request,
            TransferStatus status,
            String message,
            BigDecimal fee,
            BigDecimal netAmount,
            String estimatedArrival) {
        this.transactionId = transactionId;
        this.request = request;
        this.status = status;
        this.message = message;
        this.fee = fee == null ? BigDecimal.ZERO : fee;
        this.netAmount = netAmount == null ? BigDecimal.ZERO : netAmount;
        this.estimatedArrival = estimatedArrival == null ? "Unknown" : estimatedArrival;
        this.createdAt = Instant.now();
        this.lastUpdated = this.createdAt;
        this.progress = status == TransferStatus.COMPLETED ? 1.0 : 0.0;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public TransferRequest getRequest() {
        return request;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public String getEstimatedArrival() {
        return estimatedArrival;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public double getProgress() {
        return progress;
    }

    public void updateStatus(TransferStatus nextStatus, String nextMessage, double nextProgress) {
        this.status = nextStatus;
        this.message = nextMessage == null ? "" : nextMessage;
        this.progress = Math.max(0.0, Math.min(1.0, nextProgress));
        this.lastUpdated = Instant.now();
    }
}
