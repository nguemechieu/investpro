package org.investpro;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

@Getter
@Setter
@ToString
public class UpdateCandleDataSupplierTask {
    private final CandleDataSupplier candleDataSupplier;
    private final Runnable onCandleDataUpdated;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final Logger logger = LoggerFactory.getLogger(UpdateCandleDataSupplierTask.class);

    public UpdateCandleDataSupplierTask(CandleDataSupplier candleDataSupplier, Runnable onCandleDataUpdated) {
        this.candleDataSupplier = candleDataSupplier;
        this.onCandleDataUpdated = onCandleDataUpdated;
    }

    public void run() throws ExecutionException, InterruptedException {
        CompletableFuture<List<CandleData>> futureCandles =CompletableFuture.completedFuture( candleDataSupplier.get().get());

        futureCandles.thenAccept(candles -> {
            if (candles != null && !candles.isEmpty()) {
                logger.info("✅ Candle data updated successfully. Processing...");
                onCandleDataUpdated.run();
            } else {
                logger.warn("⚠️ Candle data is empty. No updates applied.");
            }
        }).exceptionally(ex -> {
            logger.error("❌ Error updating candle data: {}", ex.getMessage(), ex);
            return null;
        });

        // Timeout Handling - Avoids indefinite blocking
        scheduler.schedule(() -> {
            if (!futureCandles.isDone()) {
                logger.warn("⏳ Timeout: Candle data retrieval taking too long. Cancelling...");
                futureCandles.completeExceptionally(new TimeoutException("Candle data fetch timed out."));
            }
        }, 5000, TimeUnit.MILLISECONDS); // 5-second timeout
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
