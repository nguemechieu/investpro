package org.investpro.persistence.repository;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.Db1;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default implementation of TradeRepository.
 *
 * Current behavior:
 * - Uses an in-memory store so TradeService works immediately.
 * - Keeps Db1 available for future SQL/JDBC persistence.
 * - Supports CRUD plus trade-specific queries by pair and timestamp.
 *
 * Why in-memory fallback exists:
 * Your Db1 class does not yet expose concrete trade table methods. This class is
 * intentionally safe and usable now, then can later be upgraded to real SQL without
 * changing TradeService.
 */
@Slf4j
@Getter
@Setter
public class TradeRepositoryImpl implements TradeRepository {

    private final AtomicLong idSequence = new AtomicLong(1L);

    /**
     * Stable in-memory store keyed by repository trade id.
     */
    private final Map<String, Trade> store = new LinkedHashMap<>();

    /**
     * Optional database handle for future real persistence.
     */
    private Db1 db;

    public TradeRepositoryImpl(Db1 db) {
        this.db = db;
    }

    public TradeRepositoryImpl() {
        this(null);
    }

    @Override
    public synchronized Trade save(Trade entity) throws SQLException {
        validateTrade(entity);

        if (entity.getLocalTradeId() <= 0L) {
            entity.setLocalTradeId(idSequence.getAndIncrement());
        }

        String id = idOf(entity);
        store.put(id, copy(entity));

        log.debug("Trade saved. id={} pair={} price={} amount={}",
                id,
                entity.getTradePair(),
                entity.getPrice(),
                entity.getAmount()
        );

        return copy(entity);
    }

    @Override
    public synchronized List<Trade> saveAll(List<Trade> entities) throws SQLException {
        if (entities == null) {
            throw new IllegalArgumentException("entities must not be null");
        }

        List<Trade> saved = new ArrayList<>();

        for (Trade trade : entities) {
            if (trade == null) {
                continue;
            }

            saved.add(save(trade));
        }

        return saved;
    }

    @Override
    public synchronized Optional<Trade> findById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        Trade trade = store.get(id.trim());
        return trade == null ? Optional.empty() : Optional.of(copy(trade));
    }

    @Override
    public synchronized List<Trade> findAll() throws SQLException {
        return store.values()
                .stream()
                .map(this::copy)
                .sorted(Comparator.comparing(Trade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized boolean deleteById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return false;
        }

        return store.remove(id.trim()) != null;
    }

    @Override
    public synchronized boolean delete(Trade entity) throws SQLException {
        if (entity == null) {
            return false;
        }

        return deleteById(idOf(entity));
    }

    @Override
    public synchronized void deleteAll()  {
        store.clear();
    }

    @Override
    public synchronized boolean existsById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        return store.containsKey(id.trim());
    }

    @Override
    public synchronized long count() throws SQLException {
        return store.size();
    }

    @Override
    public synchronized List<Trade> findByTradePair(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            return new ArrayList<>();
        }

        return store.values()
                .stream()
                .filter(trade -> sameTradePair(trade.getTradePair(), tradePair))
                .map(this::copy)
                .sorted(Comparator.comparing(Trade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Trade> findByTimeRange(Instant startTime, Instant endTime) throws SQLException {
        if (startTime == null || endTime == null) {
            return new ArrayList<>();
        }

        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }

        return store.values()
                .stream()
                .filter(trade -> isInsideRange(trade.getTimestamp(), startTime, endTime))
                .map(this::copy)
                .sorted(Comparator.comparing(Trade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Trade> findByTradePairAndTimeRange(
            TradePair tradePair,
            Instant startTime,
            Instant endTime
    ) throws SQLException {
        if (tradePair == null || startTime == null || endTime == null) {
            return new ArrayList<>();
        }

        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }

        return store.values()
                .stream()
                .filter(trade -> sameTradePair(trade.getTradePair(), tradePair))
                .filter(trade -> isInsideRange(trade.getTimestamp(), startTime, endTime))
                .map(this::copy)
                .sorted(Comparator.comparing(Trade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized Trade findLatestByTradePair(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            return null;
        }

        return store.values()
                .stream()
                .filter(trade -> sameTradePair(trade.getTradePair(), tradePair))
                .filter(trade -> trade.getTimestamp() != null)
                .max(Comparator.comparing(Trade::getTimestamp))
                .map(this::copy)
                .orElse(null);
    }

    @Override
    public synchronized long countByTradePair(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            return 0L;
        }

        return store.values()
                .stream()
                .filter(trade -> sameTradePair(trade.getTradePair(), tradePair))
                .count();
    }

    /**
     * Utility method for tests/debugging.
     */
    public synchronized boolean isEmpty() {
        return store.isEmpty();
    }

    /**
     * Utility method for tests/debugging.
     */
    public synchronized void clearInMemoryStore() {
        store.clear();
    }

    private void validateTrade(Trade trade) {
        if (trade == null) {
            throw new IllegalArgumentException("entity must not be null");
        }

        if (trade.getTradePair() == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }

        if (trade.getPrice() <= 0.0 || !Double.isFinite(trade.getPrice())) {
            throw new IllegalArgumentException("price must be greater than zero and finite");
        }

        if (trade.getAmount() <= 0.0 || !Double.isFinite(trade.getAmount())) {
            throw new IllegalArgumentException("amount must be greater than zero and finite");
        }

        if (trade.getTransactionType() == null) {
            throw new IllegalArgumentException("transactionType must not be null");
        }

        if (trade.getTimestamp() == null) {
            trade.setTimestamp(Instant.now());
        }

        if (!Double.isFinite(trade.getFee())) {
            throw new IllegalArgumentException("fee must be finite");
        }

        if (!Double.isFinite(trade.getStopLoss())) {
            throw new IllegalArgumentException("stopLoss must be finite");
        }

        if (!Double.isFinite(trade.getTakeProfit())) {
            throw new IllegalArgumentException("takeProfit must be finite");
        }

        if (!Double.isFinite(trade.getSwap())) {
            throw new IllegalArgumentException("swap must be finite");
        }

        if (!Double.isFinite(trade.getProfit())) {
            throw new IllegalArgumentException("profit must be finite");
        }
    }

    private String idOf(Trade trade) {
        if (trade == null) {
            return "";
        }

        if (trade.getLocalTradeId() > 0L) {
            return String.valueOf(trade.getLocalTradeId());
        }

        return naturalKey(trade);
    }

    private String naturalKey(Trade trade) {
        String pair = trade.getTradePair() == null ? "UNKNOWN" : trade.getTradePair().toString('/');
        String timestamp = trade.getTimestamp() == null ? "NO_TIME" : String.valueOf(trade.getTimestamp().toEpochMilli());
        return pair + ":" + timestamp + ":" + trade.getPrice() + ":" + trade.getAmount() + ":" + trade.getTransactionType();
    }

    private boolean isInsideRange(Instant timestamp, Instant startTime, Instant endTime) {
        if (timestamp == null) {
            return false;
        }

        return !timestamp.isBefore(startTime) && !timestamp.isAfter(endTime);
    }

    private boolean sameTradePair(TradePair left, TradePair right) {
        if (left == right) {
            return true;
        }

        if (left == null || right == null) {
            return false;
        }

        if (Objects.equals(left, right)) {
            return true;
        }

        try {
            return Objects.equals(left.toString('/'), right.toString('/'));
        } catch (Exception ignored) {
            return Objects.equals(String.valueOf(left), String.valueOf(right));
        }
    }

    /**
     * Defensive copy so callers cannot mutate repository internals.
     */
    private Trade copy(Trade source) {
        if (source == null) {
            return null;
        }

        Trade copy = new Trade();
        copy.setTradePair(source.getTradePair());
        copy.setPrice(source.getPrice());
        copy.setAmount(source.getAmount());
        copy.setTransactionType(source.getTransactionType());
        copy.setLocalTradeId(source.getLocalTradeId());
        copy.setTimestamp(source.getTimestamp());
        copy.setFee(source.getFee());
        copy.setStopLoss(source.getStopLoss());
        copy.setTakeProfit(source.getTakeProfit());
        copy.setSwap(source.getSwap());
        copy.setProfit(source.getProfit());
        return copy;
    }
}
