package org.investpro;



import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

import static org.investpro.ui.TradingWindow.db1;

/**
 * A consumer that processes incoming candle data and updates the chart accordingly.
 */
@Getter
@Setter
public class CandelDataConsumer implements Consumer<List<CandleData>> {

    private static final Logger logger = LoggerFactory.getLogger(CandelDataConsumer.class);
    private  CandleStickChart candleStickChart;
    private double highestCandleValue;
    private double lowestCandleValue;
    private int candleIndexOfHighest;
    private int candleIndexOfLowest;

    public CandelDataConsumer(@NotNull CandleStickChart candleStickChart) {
        this.candleStickChart = candleStickChart;
        candleStickChart.getChart().getData().clear();
        candleStickChart.getChart().getYAxis().setAutoRanging(false);
        candleStickChart.getChart().getXAxis().setAutoRanging(false);
        candleStickChart.getChart().getYAxis().setAutoRanging(true);
        candleStickChart.getChart().getXAxis().setAnimated(false);

        // Initialize variables

    }

    /**
     * Processes the received list of candle data.
     *
     * @param candleDataList The list of candle data to process.
     */
    @Override
    public void accept(List<CandleData> candleDataList) {
        if (candleDataList == null || candleDataList.isEmpty()) {
            logger.warn("⚠️ Received empty candle data. No processing will occur.");
            return;
        }

        logger.info("📊 Processing {} new candles...", candleDataList.size());

        // Separate complete and in-progress candles
        CandleData inProgressCandle = null;
        for (CandleData candle : candleDataList) {
            if (!candle.isComplete()) {
                inProgressCandle = candle;
                continue;
            }
            processCompleteCandle(candle);
        }

        // Handle in-progress candle separately
        if (inProgressCandle != null) {
            processInProgressCandle(inProgressCandle);
        }

        // Update the chart after processing
      // getCandleStickChart().drawChartContents(true);
    }

    private void processCompleteCandle(@NotNull CandleData candle) {
        logger.info("�� Complete Candle: OpenTime={}, OpenPrice={}, ClosePrice={}, HighPrice={}, LowPrice={}",
                candle.getOpenTime(), candle.getOpenPrice(), candle.getClosePrice(), candle.getHighPrice(), candle.getLowPrice());
        db1.save(candle);

        // Update the highest and lowest candle values and their indices
        if (candle.getHighPrice() > highestCandleValue) {
            highestCandleValue = candle.getHighPrice();
            candleIndexOfHighest = candle.getOpenTime();
        }
        if (candle.getLowPrice() < lowestCandleValue) {
            lowestCandleValue = candle.getLowPrice();
            candleIndexOfLowest = candle.getOpenTime();
        }

    }


    /**
     * Processes an in-progress candle separately.
     *
     * @param candle The in-progress candle.
     */
    private void processInProgressCandle(@NotNull CandleData candle) {
        logger.info("🔄 In-Progress Candle: OpenTime={}, OpenPrice={}, ClosePrice={}",
                candle.getOpenTime(), candle.getOpenPrice(), candle.getClosePrice());

        // This can be used to update the chart dynamically without finalizing the candle
        candleStickChart.setInProgressCandle(candle.getSnapshot());
    }
}
