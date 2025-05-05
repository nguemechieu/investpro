package org.investpro.investpro;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.investpro.model.InProgressCandle;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.math.BigDecimal;
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
        BigDecimal price = new BigDecimal(trade.get("p").asText()); // trade price
        BigDecimal qty = new BigDecimal(trade.get("q").asText());   // quantity
        long timestamp = trade.get("T").asLong();                   // trade time

        if (!initialized.get()) {
            candle.setOpenPrice(price.doubleValue());
            candle.setHighPriceSoFar(price.doubleValue());
            candle.setLowPriceSoFar(price.doubleValue());
            candle.setLastPrice(price.doubleValue());
            candle.setVolumeSoFar(qty.doubleValue());
            candle.setCurrentTill((int) (timestamp / 1000));
            candle.setIsPlaceholder(false);
            initialized.set(true);
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
            volume.updateAndGet(v -> v.add(qty));
            candle.setVolumeSoFar(volume.get().doubleValue());
            candle.setCurrentTill((int) (timestamp / 1000));
        }
    }
}
