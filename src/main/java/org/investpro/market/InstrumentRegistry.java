package org.investpro.market;

import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.AssetClass;
import org.investpro.models.trading.InstrumentMetadata;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all tradable instruments and their metadata.
 * Resolves TradePair to InstrumentMetadata mapping.
 * Enables filtering by broker, venue, asset class, and contract type.
 * <p>
 * Sources:
 * - Exchange adapters (TradePair + symbol mapping)
 * - InstrumentMetadataService (enriched metadata)
 */
@Slf4j
public class InstrumentRegistry {

    /**
     * Primary key: TradePair compact symbol (e.g., "BTCUSD") -> InstrumentMetadata
     */
    private final Map<String, InstrumentMetadata> byPairSymbol = new ConcurrentHashMap<>();

    /**
     * Secondary key: Exchange symbol (e.g., "BTCUSDT@Binance") ->
     * InstrumentMetadata
     */
    private final Map<String, InstrumentMetadata> byExchangeSymbol = new ConcurrentHashMap<>();

    /**
     * Tertiary key: Broker name -> List of available metadata
     */
    private final Map<String, List<InstrumentMetadata>> byBroker = new ConcurrentHashMap<>();

    /**
     * Quaternary key: Asset class -> List of available metadata
     */
    private final Map<AssetClass, List<InstrumentMetadata>> byAssetClass = new ConcurrentHashMap<>();

    /**
     * Register an instrument with its metadata.
     */
    public void register(@NotNull InstrumentMetadata metadata) {
        if (!metadata.isComplete()) {
            log.warn("Attempting to register incomplete metadata: {}", metadata);
            return;
        }

        String pairKey = metadata.getTradePair().toCompactSymbol();
        String exchangeKey = metadata.getExchangeSymbol() + "@" + metadata.getBroker();

        byPairSymbol.put(pairKey, metadata);
        byExchangeSymbol.put(exchangeKey, metadata);

        byBroker.computeIfAbsent(metadata.getBroker(), k -> new ArrayList<>()).add(metadata);
        byAssetClass.computeIfAbsent(metadata.getAssetClass(), k -> new ArrayList<>()).add(metadata);

        log.info("Registered instrument: {} on {} ({})",
                metadata.getTradePair(), metadata.getBroker(), metadata.getVenue());
    }

    /**
     * Update existing instrument metadata.
     */
    public void update(@NotNull InstrumentMetadata metadata) {
        String pairKey = metadata.getTradePair().toCompactSymbol();
        InstrumentMetadata existing = byPairSymbol.get(pairKey);

        if (existing == null) {
            register(metadata);
            return;
        }

        byPairSymbol.put(pairKey, metadata);
        String exchangeKey = metadata.getExchangeSymbol() + "@" + metadata.getBroker();
        byExchangeSymbol.put(exchangeKey, metadata);

        log.info("Updated instrument metadata: {}", metadata.getTradePair());
    }

    /**
     * Get metadata for a TradePair.
     */
    @NotNull
    public Optional<InstrumentMetadata> get(@NotNull TradePair tradePair) {
        String key = tradePair.toCompactSymbol();
        return Optional.ofNullable(byPairSymbol.get(key));
    }

    /**
     * Get metadata by exchange symbol and broker.
     */
    @NotNull
    public Optional<InstrumentMetadata> getByExchangeSymbol(
            @NotNull String exchangeSymbol,
            @NotNull String broker) {
        String key = exchangeSymbol + "@" + broker;
        return Optional.ofNullable(byExchangeSymbol.get(key));
    }

    /**
     * Get all instruments for a broker.
     */
    @NotNull
    public List<InstrumentMetadata> getByBroker(@NotNull String broker) {
        return Collections.unmodifiableList(
                byBroker.getOrDefault(broker, Collections.emptyList()));
    }

    /**
     * Get all instruments in an asset class.
     */
    @NotNull
    public List<InstrumentMetadata> getByAssetClass(@NotNull AssetClass assetClass) {
        return Collections.unmodifiableList(
                byAssetClass.getOrDefault(assetClass, Collections.emptyList()));
    }

    /*
      Get all instruments for a broker in a specific asset class.
     */

    /**
     * Get all instruments for a broker in a specific venue.
     */
    @NotNull
    public List<InstrumentMetadata> getByBrokerAndVenue(
            @NotNull String broker,
            @NotNull String venue) {
        return byBroker.getOrDefault(broker, Collections.emptyList()).stream()
                .filter(m -> m.getVenue().equals(venue))
                .toList();
    }

    /**
     * Get all registered instruments.
     */
    @NotNull
    public List<InstrumentMetadata> getAll() {
        return List.copyOf(byPairSymbol.values());
    }

    /**
     * Check if instrument is registered.
     */
    public boolean contains(@NotNull TradePair tradePair) {
        String key = tradePair.toCompactSymbol();
        return byPairSymbol.containsKey(key);
    }

    /**
     * Remove instrument from registry.
     */
    public void remove(@NotNull TradePair tradePair) {
        String key = tradePair.toCompactSymbol();
        InstrumentMetadata metadata = byPairSymbol.remove(key);

        if (metadata != null) {
            String exchangeKey = metadata.getExchangeSymbol() + "@" + metadata.getBroker();
            byExchangeSymbol.remove(exchangeKey);

            List<InstrumentMetadata> brokerList = byBroker.get(metadata.getBroker());
            if (brokerList != null) {
                brokerList.remove(metadata);
            }

            List<InstrumentMetadata> assetClassList = byAssetClass.get(metadata.getAssetClass());
            if (assetClassList != null) {
                assetClassList.remove(metadata);
            }

            log.info("Removed instrument: {}", tradePair);
        }
    }

    /**
     * Clear all registrations.
     */
    public void clear() {
        byPairSymbol.clear();
        byExchangeSymbol.clear();
        byBroker.clear();
        byAssetClass.clear();
        log.info("Cleared instrument registry");
    }

    /**
     * Get registry size.
     */
    public int size() {
        return byPairSymbol.size();
    }

    /**
     * Get number of brokers in registry.
     */
    public int brokerCount() {
        return byBroker.size();
    }

    /**
     * Get all brokers in registry.
     */
    @NotNull
    public List<String> getAllBrokers() {
        return List.copyOf(byBroker.keySet());
    }


}
