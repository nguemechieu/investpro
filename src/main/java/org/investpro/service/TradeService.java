package org.investpro.service;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.repository.TradeRepository;
import org.investpro.utils.Side;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for Trade domain operations.
 *
 * Responsibilities:
 * - validate completed trades before persistence
 * - delegate CRUD operations to TradeRepository
 * - provide trading-specific read/query helpers
 * - calculate trade analytics such as profit, fees, win rate, volume, and summaries
 *
 * Trade model fields used:
 * - tradePair
 * - price
 * - amount
 * - transactionType
 * - localTradeId
 * - timestamp
 * - fee
 * - stopLoss
 * - takeProfit
 * - swap
 * - profit
 */
@Slf4j
public class TradeService implements CrudService<Trade, String> {

    private final TradeRepository repository;
    private final StrategySelectionService strategySelectionService;

    /**
     * Initialize the service with a trade repository.
     *
     * @param repository the trade repository
     */
    public TradeService(TradeRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }

        this.repository = repository;
        this.strategySelectionService = new StrategySelectionService();
    }

    @Override
    public Trade save(Trade trade) throws SQLException, ClassNotFoundException {
        validateTrade(trade);
        normalizeTrade(trade);
        return repository.save(trade);
    }

    @Override
    public List<Trade> saveAll(List<Trade> trades) throws SQLException {
        if (trades == null || trades.isEmpty()) {
            throw new IllegalArgumentException("trades list must not be null or empty");
        }

        trades.forEach(trade -> {
            validateTrade(trade);
            normalizeTrade(trade);
        });

        return repository.saveAll(trades);
    }

    @Override
    public Optional<Trade> findById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return repository.findById(id.trim());
    }

    @Override
    public List<Trade> findAll() throws SQLException {
        List<Trade> trades = repository.findAll();
        return trades == null ? List.of() : trades;
    }

    @Override
    public boolean delete(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return false;
        }

        return repository.deleteById(id.trim());
    }

    @Override
    public boolean exists(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return false;
        }

        return repository.existsById(id.trim());
    }

    @Override
    public long count() throws SQLException {
        return repository.count();
    }

    /**
     * Find all trades for a specific trading pair.
     *
     * @param tradePair the trading pair
     * @return list of trades for the pair
     * @throws SQLException if database operation fails
     */
    public List<Trade> findByTradePair(TradePair tradePair) throws SQLException {
        requireTradePair(tradePair);

        List<Trade> trades = repository.findByTradePair(tradePair);
        return trades == null ? List.of() : trades;
    }

    /**
     * Find trades within a time range.
     *
     * @param startTime the start instant
     * @param endTime the end instant
     * @return list of trades in the time range
     * @throws SQLException if database operation fails
     */
    public List<Trade> findByTimeRange(Instant startTime, Instant endTime) throws SQLException {
        validateTimeRange(startTime, endTime);

        List<Trade> trades = repository.findByTimeRange(startTime, endTime);
        return trades == null ? List.of() : trades;
    }

    /**
     * Find trades for a specific pair within a time range.
     *
     * @param tradePair the trading pair
     * @param startTime the start instant
     * @param endTime the end instant
     * @return list of matching trades
     * @throws SQLException if database operation fails
     */
    public List<Trade> findByTradePairAndTimeRange(
            TradePair tradePair,
            Instant startTime,
            Instant endTime
    ) throws SQLException {
        requireTradePair(tradePair);
        validateTimeRange(startTime, endTime);

        List<Trade> trades = repository.findByTradePairAndTimeRange(tradePair, startTime, endTime);
        return trades == null ? List.of() : trades;
    }

    /**
     * Get the most recent trade for a pair.
     *
     * @param tradePair the trading pair
     * @return the most recent trade, or Optional.empty() if none exist
     * @throws SQLException if database operation fails
     */
    public Optional<Trade> getLatestTrade(TradePair tradePair) throws SQLException {
        requireTradePair(tradePair);

        Trade latest = repository.findLatestByTradePair(tradePair);
        return Optional.ofNullable(latest);
    }

    /**
     * Count trades for a specific pair.
     *
     * @param tradePair the trading pair
     * @return the count of trades
     * @throws SQLException if database operation fails
     */
    public long countByTradePair(TradePair tradePair) throws SQLException {
        requireTradePair(tradePair);
        return repository.countByTradePair(tradePair);
    }

    /**
     * Return latest trades across all symbols.
     */
    public List<Trade> latestTrades(int limit) throws SQLException {
        if (limit <= 0) {
            return List.of();
        }

        return findAll()
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Trade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Return latest trades for one symbol.
     */
    public List<Trade> latestTrades(TradePair tradePair, int limit) throws SQLException {
        if (limit <= 0) {
            return List.of();
        }

        return findByTradePair(tradePair)
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Trade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Calculate gross traded notional for a trade.
     */
    public double calculateNotional(Trade trade) {
        validateTrade(trade);
        return trade.getPrice() * trade.getAmount();
    }

    /**
     * Calculate net profit for a trade.
     *
     * Formula:
     * netProfit = profit - fee + swap
     *
     * Swap can be positive or negative depending on broker/market.
     */
    public double calculateNetProfit(Trade trade) {
        validateTrade(trade);
        return trade.getProfit() - trade.getFee() + trade.getSwap();
    }

    /**
     * Calculate total gross notional across trades.
     */
    public double totalNotional(List<Trade> trades) {
        return safeTrades(trades)
                .stream()
                .mapToDouble(this::calculateNotional)
                .sum();
    }

    /**
     * Calculate total fees across trades.
     */
    public double totalFees(List<Trade> trades) {
        return safeTrades(trades)
                .stream()
                .mapToDouble(Trade::getFee)
                .sum();
    }

    /**
     * Calculate total swap across trades.
     */
    public double totalSwap(List<Trade> trades) {
        return safeTrades(trades)
                .stream()
                .mapToDouble(Trade::getSwap)
                .sum();
    }

    /**
     * Calculate total gross profit, before fee/swap adjustment.
     */
    public double totalGrossProfit(List<Trade> trades) {
        return safeTrades(trades)
                .stream()
                .mapToDouble(Trade::getProfit)
                .sum();
    }

    /**
     * Calculate total net profit, after fee/swap adjustment.
     */
    public double totalNetProfit(List<Trade> trades) {
        return safeTrades(trades)
                .stream()
                .mapToDouble(this::calculateNetProfit)
                .sum();
    }

    /**
     * Calculate win rate as a value from 0.0 to 1.0.
     */
    public double winRate(List<Trade> trades) {
        List<Trade> safeTrades = safeTrades(trades);

        if (safeTrades.isEmpty()) {
            return 0.0;
        }

        long wins = safeTrades
                .stream()
                .filter(trade -> calculateNetProfit(trade) > 0.0)
                .count();

        return wins / (double) safeTrades.size();
    }

    /**
     * Calculate loss rate as a value from 0.0 to 1.0.
     */
    public double lossRate(List<Trade> trades) {
        List<Trade> safeTrades = safeTrades(trades);

        if (safeTrades.isEmpty()) {
            return 0.0;
        }

        long losses = safeTrades
                .stream()
                .filter(trade -> calculateNetProfit(trade) < 0.0)
                .count();

        return losses / (double) safeTrades.size();
    }

    /**
     * Average net profit per trade.
     */
    public double averageNetProfit(List<Trade> trades) {
        List<Trade> safeTrades = safeTrades(trades);

        if (safeTrades.isEmpty()) {
            return 0.0;
        }

        return totalNetProfit(safeTrades) / safeTrades.size();
    }

    /**
     * Group trades by trading pair.
     */
    public Map<TradePair, List<Trade>> groupByTradePair(List<Trade> trades) {
        return safeTrades(trades)
                .stream()
                .filter(trade -> trade.getTradePair() != null)
                .collect(Collectors.groupingBy(Trade::getTradePair));
    }

    /**
     * Group trades by side/transaction type.
     */
    public Map<Side, List<Trade>> groupBySide(List<Trade> trades) {
        return safeTrades(trades)
                .stream()
                .filter(trade -> trade.getTransactionType() != null)
                .collect(Collectors.groupingBy(Trade::getTransactionType));
    }

    /**
     * Build a summary for all trades.
     */
    public TradePerformanceSummary summarizeAll() throws SQLException {
        return summarize(findAll());
    }

    /**
     * Build a summary for a symbol.
     */
    public TradePerformanceSummary summarizeByTradePair(TradePair tradePair) throws SQLException {
        return summarize(findByTradePair(tradePair));
    }

    /**
     * Build a summary for a time range.
     */
    public TradePerformanceSummary summarizeByTimeRange(
            Instant startTime,
            Instant endTime
    ) throws SQLException {
        return summarize(findByTimeRange(startTime, endTime));
    }

    /**
     * Build a summary for a symbol and time range.
     */
    public TradePerformanceSummary summarizeByTradePairAndTimeRange(
            TradePair tradePair,
            Instant startTime,
            Instant endTime
    ) throws SQLException {
        return summarize(findByTradePairAndTimeRange(tradePair, startTime, endTime));
    }

    /**
     * Build a summary from a trade list.
     */
    public TradePerformanceSummary summarize(List<Trade> trades) {
        List<Trade> safeTrades = safeTrades(trades);

        if (safeTrades.isEmpty()) {
            return TradePerformanceSummary.empty();
        }

        double grossProfit = totalGrossProfit(safeTrades);
        double netProfit = totalNetProfit(safeTrades);
        double totalFees = totalFees(safeTrades);
        double totalSwap = totalSwap(safeTrades);
        double notional = totalNotional(safeTrades);
        double winRate = winRate(safeTrades);
        double lossRate = lossRate(safeTrades);
        double averageNetProfit = averageNetProfit(safeTrades);

        long wins = safeTrades.stream().filter(trade -> calculateNetProfit(trade) > 0.0).count();
        long losses = safeTrades.stream().filter(trade -> calculateNetProfit(trade) < 0.0).count();
        long breakeven = safeTrades.size() - wins - losses;

        Optional<Instant> firstTimestamp = safeTrades.stream()
                .map(Trade::getTimestamp)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());

        Optional<Instant> lastTimestamp = safeTrades.stream()
                .map(Trade::getTimestamp)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());

        return new TradePerformanceSummary(
                safeTrades.size(),
                wins,
                losses,
                breakeven,
                grossProfit,
                netProfit,
                totalFees,
                totalSwap,
                notional,
                winRate,
                lossRate,
                averageNetProfit,
                firstTimestamp.orElse(null),
                lastTimestamp.orElse(null)
        );
    }

    /**
     * Validate a trade before saving or analytics.
     *
     * @param trade the trade to validate
     * @throws IllegalArgumentException if trade is invalid
     */
    private void validateTrade(Trade trade) {
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }

        if (trade.getTradePair() == null) {
            throw new IllegalArgumentException("trade pair must not be null");
        }

        if (trade.getTransactionType() == null) {
            throw new IllegalArgumentException("trade transactionType must not be null");
        }

        if (trade.getPrice() <= 0.0 || !Double.isFinite(trade.getPrice())) {
            throw new IllegalArgumentException("trade price must be greater than zero and finite");
        }

        if (trade.getAmount() <= 0.0 || !Double.isFinite(trade.getAmount())) {
            throw new IllegalArgumentException("trade amount must be greater than zero and finite");
        }

        if (!Double.isFinite(trade.getFee())) {
            throw new IllegalArgumentException("trade fee must be finite");
        }

        if (!Double.isFinite(trade.getStopLoss())) {
            throw new IllegalArgumentException("trade stopLoss must be finite");
        }

        if (!Double.isFinite(trade.getTakeProfit())) {
            throw new IllegalArgumentException("trade takeProfit must be finite");
        }

        if (!Double.isFinite(trade.getSwap())) {
            throw new IllegalArgumentException("trade swap must be finite");
        }

        if (!Double.isFinite(trade.getProfit())) {
            throw new IllegalArgumentException("trade profit must be finite");
        }

        if (trade.getTimestamp() == null) {
            throw new IllegalArgumentException("trade timestamp must not be null");
        }
    }

    /**
     * Apply safe defaults to optional trade fields.
     */
    private void normalizeTrade(Trade trade) {
        if (trade.getTimestamp() == null) {
            trade.setTimestamp(Instant.now());
        }

        if (trade.getLocalTradeId() < 0) {
            trade.setLocalTradeId(0L);
        }
    }

    private void requireTradePair(TradePair tradePair) {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
    }

    private void validateTimeRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("time range must not be null");
        }

        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
    }

    private List<Trade> safeTrades(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return List.of();
        }

        List<Trade> validTrades = new ArrayList<>();

        for (Trade trade : trades) {
            if (trade == null) {
                continue;
            }

            try {
                validateTrade(trade);
                validTrades.add(trade);
            } catch (IllegalArgumentException exception) {
                log.debug("Skipping invalid trade in analytics: {}", exception.getMessage());
            }
        }

        return validTrades;
    }

    /**
     * Lightweight immutable performance summary.
     */
    public record TradePerformanceSummary(
            long totalTrades,
            long winningTrades,
            long losingTrades,
            long breakevenTrades,
            double grossProfit,
            double netProfit,
            double totalFees,
            double totalSwap,
            double totalNotional,
            double winRate,
            double lossRate,
            double averageNetProfit,
            Instant firstTradeTime,
            Instant lastTradeTime
    ) {
        public static TradePerformanceSummary empty() {
            return new TradePerformanceSummary(
                    0,
                    0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    null,
                    null
            );
        }

        public double winRatePercent() {
            return winRate * 100.0;
        }

        public double lossRatePercent() {
            return lossRate * 100.0;
        }

        public boolean profitable() {
            return netProfit > 0.0;
        }
    }
}
