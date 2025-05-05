package org.investpro.investpro;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.investpro.model.InProgressCandle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class InProgressCandleUpdater implements Consumer<JsonNode> {

    private final InProgressCandle candle;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<BigDecimal> high = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> low = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> volume = new AtomicReference<>(BigDecimal.ZERO);

    public InProgressCandleUpdater(InProgressCandle candle) {
        this.candle = candle;
    }

    @Override
    public void accept(JsonNode trade) {
        if (trade == null || trade.get("p") == null || trade.get("q") == null || trade.get("T") == null) {
            return; // or log a warning
        }

        BigDecimal price = new BigDecimal(trade.get("p").asText());
        BigDecimal qty = new BigDecimal(trade.get("q").asText());
        long timestamp = trade.get("T").asLong();

        if (initialized.compareAndSet(false, true)) {
            candle.setOpenPrice(price.doubleValue());
            candle.setHighPriceSoFar(price.doubleValue());
            candle.setLowPriceSoFar(price.doubleValue());
            candle.setLastPrice(price.doubleValue());
            candle.setVolumeSoFar(qty.doubleValue());
            candle.setCurrentTill((int) (timestamp / 1000));
            candle.setIsPlaceholder(false);
            high.set(price);
            low.set(price);
            volume.set(qty);
        } else {
            if (price.compareTo(high.get()) > 0) {
                high.set(price);
                candle.setHighPriceSoFar(price.doubleValue());
            }

            if (price.compareTo(low.get()) < 0) {
                low.set(price);
                candle.setLowPriceSoFar(price.doubleValue());
            }

            candle.setLastPrice(price.doubleValue());
            candle.setVolumeSoFar(volume.updateAndGet(v -> v.add(qty)).doubleValue());
            candle.setCurrentTill((int) (timestamp / 1000));
        }
    }

    // Optional: method for manual update without JsonNode
    public void update(double price, Instant now) {
        BigDecimal priceBD = BigDecimal.valueOf(price);
        long timestamp = now.getEpochSecond();

        if (initialized.compareAndSet(false, true)) {
            candle.setOpenPrice(price);
            candle.setHighPriceSoFar(price);
            candle.setLowPriceSoFar(price);
            candle.setLastPrice(price);
            candle.setVolumeSoFar(0.0);
            candle.setCurrentTill((int) timestamp);
            candle.setIsPlaceholder(false);
            high.set(priceBD);
            low.set(priceBD);
            volume.set(BigDecimal.ZERO);
        } else {
            if (priceBD.compareTo(high.get()) > 0) {
                high.set(priceBD);
                candle.setHighPriceSoFar(price);
            }
            if (priceBD.compareTo(low.get()) < 0) {
                low.set(priceBD);
                candle.setLowPriceSoFar(price);
            }
            candle.setLastPrice(price);
            candle.setCurrentTill((int) timestamp);
        }
    }
}
