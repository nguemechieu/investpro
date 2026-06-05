package org.investpro.transfer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TransferStatusMonitor {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "transfer-status-monitor");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, TransferResult> tracked = new ConcurrentHashMap<>();

    public TransferStatusMonitor() {
        scheduler.scheduleAtFixedRate(this::tick, 1L, 1L, TimeUnit.SECONDS);
    }

    public void track(TransferResult result) {
        if (result != null) {
            tracked.put(result.getTransactionId(), result);
        }
    }

    public void tickWithCallback(Consumer<TransferResult> callback) {
        tracked.values().forEach(callback);
    }

    private void tick() {
        for (TransferResult result : tracked.values()) {
            if (result.getStatus() == TransferStatus.COMPLETED
                    || result.getStatus() == TransferStatus.FAILED
                    || result.getStatus() == TransferStatus.CANCELLED) {
                continue;
            }

            double progress = result.getProgress();
            if (progress < 0.35) {
                result.updateStatus(TransferStatus.PENDING, "Pending broker acceptance", 0.35);
            } else if (progress < 0.75) {
                result.updateStatus(TransferStatus.PROCESSING, "Processing settlement", 0.75);
            } else {
                result.updateStatus(TransferStatus.COMPLETED, "Transfer completed", 1.0);
            }
        }
    }
}
