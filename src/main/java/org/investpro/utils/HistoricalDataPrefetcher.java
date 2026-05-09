package org.investpro.utils;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.binance.BinanceCandleDataSupplier;
import org.investpro.exchange.bitfinex.BitfinexCandleDataSupplier;
import org.investpro.exchange.coinbase.CoinbaseCandleDataSupplier;
import org.investpro.models.trading.TradePair;
import org.investpro.repository.HistoricalDataRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Utility to pre-fetch and cache historical candle data from exchanges before backtesting.
 *
 * Supported default exchanges:
 * - Binance
 * - Coinbase
 * - Bitfinex
 *
 * Important:
 * The current CandleDataSupplier contract only exposes get().
 * It does not accept start/end range parameters directly.
 * Because of that, this class fetches from the supplier once, validates the result,
 * removes duplicates when possible, and saves the candles to HistoricalDataRepository.
 *
 * Usage:
 *
 * HistoricalDataPrefetcher prefetcher =
 *         HistoricalDataPrefetcher.forCurrentExchange(exchange, repository);
 *
 * List<CandleData> candles = prefetcher.fetchAndCacheDataSync(
 *         tradePair,
 *         LocalDateTime.now().minusDays(60),
 *         LocalDateTime.now(),
 *         "1h",
 *         progress -> System.out.println("Progress: " + progress + "%")
 * );
 */
@Slf4j
public class HistoricalDataPrefetcher {

    private final HistoricalDataRepository repository;
    private final DataSupplierFactory supplierFactory;

    /**
     * Minimum candles usually needed before a backtest becomes useful.
     * You can tune these numbers later depending on your strategy requirements.
     */
    private static final int MIN_CANDLES_FOR_BASIC_TEST = 100;
    private static final int MIN_CANDLES_FOR_GOOD_TEST = 300;
    private static final int MIN_CANDLES_FOR_STRONG_TEST = 1_000;

    /**
     * Factory interface for creating CandleDataSupplier instances.
     */
    @FunctionalInterface
    public interface DataSupplierFactory {
        CandleDataSupplier create(int secondsPerCandle, TradePair tradePair);
    }

    public HistoricalDataPrefetcher(
            @NotNull HistoricalDataRepository repository,
            @NotNull DataSupplierFactory supplierFactory
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.supplierFactory = Objects.requireNonNull(supplierFactory, "supplierFactory must not be null");
    }

    /**
     * Creates a HistoricalDataPrefetcher using the currently selected exchange.
     */
    public static HistoricalDataPrefetcher forCurrentExchange(
            @NotNull Exchange exchange,
            @NotNull HistoricalDataRepository historicalDataRepository
    ) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(historicalDataRepository, "historicalDataRepository must not be null");

        String exchangeName = exchange.getClass().getSimpleName().toLowerCase();
        String packageName = exchange.getClass().getPackageName().toLowerCase();
        String identity = exchangeName + " " + packageName;

        DataSupplierFactory factory;

        if (identity.contains("binance")) {
            factory = BinanceCandleDataSupplier::new;
        } else if (identity.contains("coinbase")) {
            factory = CoinbaseCandleDataSupplier::new;
        } else if (identity.contains("bitfinex")) {
            factory = BitfinexCandleDataSupplier::new;
        } else {
            throw new UnsupportedOperationException(
                    "No historical candle data supplier configured for exchange: "
                            + exchange.getClass().getName()
                            + ". Add a supplier mapping in HistoricalDataPrefetcher.forCurrentExchange()."
            );
        }

        return new HistoricalDataPrefetcher(historicalDataRepository, factory);
    }

    /**
     * Fetch historical data asynchronously and cache it to the repository.
     *
     * @param pair             trading pair to fetch
     * @param startTime        requested start time
     * @param endTime          requested end time
     * @param timeframeCode    timeframe code such as 1m, 5m, 15m, 1h, 4h, 1d
     * @param progressCallback optional progress callback from 0 to 100, or -1 on failure
     * @return future with fetched candle list
     */
    public CompletableFuture<List<CandleData>> fetchAndCacheData(
            @NotNull TradePair pair,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @NotNull String timeframeCode,
            @Nullable Consumer<Integer> progressCallback
    ) {
        return CompletableFuture.supplyAsync(() -> fetchAndCacheDataSync(
                pair,
                startTime,
                endTime,
                timeframeCode,
                progressCallback
        ));
    }

    /**
     * Fetch historical data synchronously and cache it to the repository.
     */
    public List<CandleData> fetchAndCacheDataSync(
            @NotNull TradePair pair,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @NotNull String timeframeCode,
            @Nullable Consumer<Integer> progressCallback
    ) {
        try {
            return performDataFetch(pair, startTime, endTime, timeframeCode, progressCallback);
        } catch (Exception e) {
            log.error("Failed to prefetch historical data for {} {}: {}", pair, timeframeCode, e.getMessage(), e);
            notifyProgress(progressCallback, -1);
            return List.of();
        }
    }

    private List<CandleData> performDataFetch(
            @NotNull TradePair pair,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @NotNull String timeframeCode,
            @Nullable Consumer<Integer> progressCallback
    ) throws Exception {

        validateRequest(pair, startTime, endTime, timeframeCode);

        int secondsPerCandle = parseTimeframe(timeframeCode);
        int expectedCandles = estimateExpectedCandles(startTime, endTime, secondsPerCandle);

        log.info(
                "Starting historical data prefetch. pair={}, timeframe={}, start={}, end={}, expectedCandles={}",
                pair,
                timeframeCode,
                startTime,
                endTime,
                expectedCandles
        );

        notifyProgress(progressCallback, 5);

        CandleDataSupplier supplier = supplierFactory.create(secondsPerCandle, pair);

        notifyProgress(progressCallback, 15);

        Future<List<CandleData>> future = supplier.get();

        notifyProgress(progressCallback, 45);

        List<CandleData> fetchedCandles = future.get();

        notifyProgress(progressCallback, 75);

        List<CandleData> cleanedCandles = cleanCandles(fetchedCandles);

        if (cleanedCandles.isEmpty()) {
            log.warn("No historical candles returned for {} {}", pair, timeframeCode);
            notifyProgress(progressCallback, 100);
            return List.of();
        }

        DataReadiness readiness = evaluateDataReadiness(cleanedCandles.size(), expectedCandles);

        log.info(
                "Historical data fetched. pair={}, timeframe={}, candles={}, expected={}, readiness={}",
                pair,
                timeframeCode,
                cleanedCandles.size(),
                expectedCandles,
                readiness
        );

        repository.saveHistoricalData(pair, startTime, endTime, timeframeCode, cleanedCandles);

        log.info(
                "Cached {} historical candles for {} {}",
                cleanedCandles.size(),
                pair,
                timeframeCode
        );

        notifyProgress(progressCallback, 100);

        return cleanedCandles;
    }

    private void validateRequest(
            @NotNull TradePair pair,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @NotNull String timeframeCode
    ) {
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        Objects.requireNonNull(timeframeCode, "timeframeCode must not be null");

        if (timeframeCode.isBlank()) {
            throw new IllegalArgumentException("timeframeCode must not be blank");
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "startTime must be before endTime. startTime=" + startTime + ", endTime=" + endTime
            );
        }
    }

    /**
     * Removes nulls and duplicate candles if CandleData implements equals/hashCode.
     * If CandleData does not implement equals/hashCode, this still safely removes nulls.
     */
    private List<CandleData> cleanCandles(@Nullable List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        Set<CandleData> unique = new LinkedHashSet<>();

        for (CandleData candle : candles) {
            if (candle != null) {
                unique.add(candle);
            }
        }

        return new ArrayList<>(unique);
    }

    /**
     * Estimate how many candles should exist between startTime and endTime.
     */
    public int estimateExpectedCandles(
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            int secondsPerCandle
    ) {
        long seconds = Duration.between(startTime, endTime).getSeconds();

        if (seconds <= 0 || secondsPerCandle <= 0) {
            return 0;
        }

        long expected = seconds / secondsPerCandle;

        if (expected > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return Math.max(1, (int) expected);
    }

    /**
     * Helps the backtest engine know if enough candles exist for useful testing.
     */
    public DataReadiness evaluateDataReadiness(int actualCandles, int expectedCandles) {
        if (actualCandles <= 0) {
            return DataReadiness.EMPTY;
        }

        if (actualCandles < MIN_CANDLES_FOR_BASIC_TEST) {
            return DataReadiness.NOT_ENOUGH;
        }

        if (actualCandles < MIN_CANDLES_FOR_GOOD_TEST) {
            return DataReadiness.BASIC;
        }

        if (actualCandles < MIN_CANDLES_FOR_STRONG_TEST) {
            return DataReadiness.GOOD;
        }

        if (expectedCandles > 0) {
            double coverage = actualCandles / (double) expectedCandles;

            if (coverage < 0.50) {
                return DataReadiness.PARTIAL;
            }
        }

        return DataReadiness.STRONG;
    }

    public boolean hasEnoughDataForBasicTesting(int candleCount) {
        return candleCount >= MIN_CANDLES_FOR_BASIC_TEST;
    }

    public boolean hasEnoughDataForGoodTesting(int candleCount) {
        return candleCount >= MIN_CANDLES_FOR_GOOD_TEST;
    }

    public boolean hasEnoughDataForStrongTesting(int candleCount) {
        return candleCount >= MIN_CANDLES_FOR_STRONG_TEST;
    }

    /**
     * Convert timeframe code to seconds.
     */
    public int parseTimeframe(@NotNull String timeframeCode) {
        String code = timeframeCode.toLowerCase().trim();

        return switch (code) {
            case "1m", "m1" -> 60;
            case "3m", "m3" -> 180;
            case "5m", "m5" -> 300;
            case "15m", "m15" -> 900;
            case "30m", "m30" -> 1_800;
            case "1h", "h1" -> 3_600;
            case "2h", "h2" -> 7_200;
            case "4h", "h4" -> 14_400;
            case "6h", "h6" -> 21_600;
            case "8h", "h8" -> 28_800;
            case "12h", "h12" -> 43_200;
            case "1d", "d1" -> 86_400;
            case "1w", "w1" -> 604_800;
            default -> throw new IllegalArgumentException(
                    "Unsupported timeframe code: " + timeframeCode
                            + ". Supported values: 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 1w"
            );
        };
    }

    private void notifyProgress(@Nullable Consumer<Integer> callback, int progress) {
        if (callback == null) {
            return;
        }

        try {
            callback.accept(progress);
        } catch (Exception e) {
            log.warn("Historical data progress callback failed: {}", e.getMessage());
        }
    }

    public enum DataReadiness {
        EMPTY,
        NOT_ENOUGH,
        BASIC,
        GOOD,
        PARTIAL,
        STRONG
    }
}