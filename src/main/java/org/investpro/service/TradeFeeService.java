package org.investpro.service;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.ExchangeFeeSchedule;
import org.investpro.models.trading.TradeFee;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.TradeFeeRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application-level service for trade fee recording, querying, and reporting.
 * <p>
 * Usage pattern:
 * <pre>
 *   // On every fill:
 *   TradeFeeService.getInstance().record(
 *       "Binance", tradePair, orderId,
 *       feeAmount, "BNB", TradeFee.FeeType.TAKER,
 *       notionalValue, Instant.now());
 *
 *   // Dashboard:
 *   double todayFees = TradeFeeService.getInstance().totalFeeToday("Binance");
 *   FeesSummary summary = TradeFeeService.getInstance().summary();
 * </pre>
 */
@Slf4j
public class TradeFeeService {

    private static volatile TradeFeeService instance;

    private final TradeFeeRepository repository;

    private TradeFeeService() {
        this.repository = new TradeFeeRepository();
        log.info("TradeFeeService initialised");
    }

    public static TradeFeeService getInstance() {
        if (instance == null) {
            synchronized (TradeFeeService.class) {
                if (instance == null) instance = new TradeFeeService();
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    /**
     * Record a fee from a completed trade fill.
     *
     * @param exchange       exchange name (e.g. "Binance")
     * @param tradePair      the instrument traded
     * @param orderId        exchange-assigned order / fill ID
     * @param feeAmount      absolute fee amount paid
     * @param feeCurrency    currency the fee was charged in
     * @param feeType        MAKER or TAKER
     * @param notionalValue  trade value in quote currency
     * @param timestamp      fill timestamp
     * @return the persisted {@link TradeFee}
     */
    public TradeFee record(String exchange, TradePair tradePair, String orderId,
                           double feeAmount, String feeCurrency,
                           TradeFee.FeeType feeType, double notionalValue,
                           Instant timestamp) {
        TradeFee fee = TradeFee.builder()
                .exchange(exchange)
                .tradePair(tradePair)
                .orderId(orderId)
                .amount(feeAmount)
                .feeCurrency(feeCurrency)
                .feeType(feeType)
                .notionalValue(notionalValue)
                .timestamp(timestamp != null ? timestamp : Instant.now())
                .build();

        repository.save(fee);
        log.info("Fee recorded: {}", fee);
        return fee;
    }

    /**
     * Convenience overload — derives fee from {@link ExchangeFeeSchedule} when the
     * exchange is known. Useful before the exchange confirms the exact fee.
     */
    public TradeFee recordEstimated(String exchange, TradePair tradePair, String orderId,
                                    double notionalValue, double units,
                                    TradeFee.FeeType feeType, Instant timestamp) {
        ExchangeFeeSchedule schedule = ExchangeFeeSchedule.forExchange(exchange);
        double estimatedFee = (schedule != null)
                ? schedule.computeFee(notionalValue, units, feeType)
                : 0.0;
        String feeCurrency = (schedule != null) ? schedule.getDefaultFeeCurrency() : "USD";

        log.debug("Estimated fee for {} on {}: {} {}", exchange, tradePair, estimatedFee, feeCurrency);
        return record(exchange, tradePair, orderId, estimatedFee, feeCurrency,
                feeType, notionalValue, timestamp);
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<TradeFee> getFeesForExchange(String exchange) {
        return repository.findByExchange(exchange);
    }

    public List<TradeFee> getFeesForPair(String exchange, TradePair pair) {
        return repository.findByExchangeAndPair(exchange, pair);
    }

    public List<TradeFee> getFeesInRange(Instant from, Instant to) {
        return repository.findByDateRange(from, to);
    }

    /** All fees from today (UTC). */
    public List<TradeFee> getFeesToday() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return repository.findByDateRange(startOfDay, Instant.now());
    }

    // ------------------------------------------------------------------
    // Aggregation
    // ------------------------------------------------------------------

    public double totalFeeByExchange(String exchange) {
        return repository.totalFeeByExchange(exchange);
    }

    public double totalFeeToday(String exchange) {
        return repository.totalFeeToday(exchange);
    }

    public double totalFeesAllTime() {
        return repository.totalFeesAllExchanges();
    }

    public long tradeCountByExchange(String exchange) {
        return repository.countByExchange(exchange);
    }

    // ------------------------------------------------------------------
    // Summary report
    // ------------------------------------------------------------------

    /**
     * Builds a per-exchange fee summary from all stored records.
     * Useful for a dashboard or Telegram report.
     */
    public FeesSummary summary() {
        double grandTotal = repository.totalFeesAllExchanges();
        Map<String, Double> byExchange = List.of(ExchangeFeeSchedule.values()).stream()
                .map(ExchangeFeeSchedule::getExchangeName)
                .distinct()
                .collect(Collectors.toMap(
                        name -> name,
                        repository::totalFeeByExchange
                ));
        return new FeesSummary(grandTotal, byExchange);
    }

    /**
     * Formatted text summary — suitable for Telegram or log output.
     */
    public String summaryText() {
        FeesSummary s = summary();
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Trade Fee Summary*\n\n");
        s.byExchange().forEach((exchange, total) -> {
            if (total > 0) {
                ExchangeFeeSchedule schedule = ExchangeFeeSchedule.forExchange(exchange);
                String currency = schedule != null ? schedule.getDefaultFeeCurrency() : "";
                sb.append(String.format("• %-22s %.6f %s%n", exchange, total, currency));
            }
        });
        sb.append(String.format("%n*Grand total (all exchanges):* %.6f\n", s.grandTotal()));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Inner types
    // ------------------------------------------------------------------

    /**
     * Snapshot of aggregated fees, grouped by exchange name.
     */
    public record FeesSummary(double grandTotal, Map<String, Double> byExchange) {

        /** Total fees for a specific exchange, or 0 if not found. */
        public double forExchange(String exchange) {
            return byExchange.getOrDefault(exchange, 0.0);
        }
    }
}
