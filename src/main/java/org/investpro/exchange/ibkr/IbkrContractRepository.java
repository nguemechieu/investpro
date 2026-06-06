package org.investpro.exchange.ibkr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
public final class IbkrContractRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<List<IbkrResolvedContract>> CONTRACT_LIST = new TypeReference<>() {
    };

    private final Path path;

    public IbkrContractRepository() {
        this(Path.of("data", "ibkr", "contracts.json"));
    }

    public IbkrContractRepository(Path path) {
        this.path = path;
        ensureParent(path);
    }

    public synchronized List<IbkrResolvedContract> findAll() {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<IbkrResolvedContract> loaded = MAPPER.readValue(path.toFile(), CONTRACT_LIST);
            return loaded == null ? List.of() : List.copyOf(loaded);
        } catch (IOException exception) {
            log.warn("Unable to read IBKR contract cache from {}", path, exception);
            return List.of();
        }
    }

    public synchronized IbkrResolvedContract save(IbkrResolvedContract contract) {
        Map<String, IbkrResolvedContract> byKey = new LinkedHashMap<>();
        for (IbkrResolvedContract existing : findAll()) {
            byKey.put(existing.uniqueKey(), existing);
        }
        byKey.put(contract.uniqueKey(), contract);
        List<IbkrResolvedContract> contracts = new ArrayList<>(byKey.values());
        contracts.sort(Comparator.comparing(IbkrResolvedContract::symbol)
                .thenComparing(IbkrResolvedContract::secType)
                .thenComparingLong(IbkrResolvedContract::conId));
        writeAll(contracts);
        return contract;
    }

    public synchronized Optional<IbkrResolvedContract> findByConIdExchange(long conId, String exchange) {
        String normalizedExchange = normalize(exchange);
        return findAll().stream()
                .filter(contract -> contract.conId() == conId)
                .filter(contract -> normalizedExchange.isBlank()
                        || normalize(contract.exchange()).equals(normalizedExchange))
                .findFirst();
    }

    public synchronized Optional<IbkrResolvedContract> findByTradePair(TradePair pair) {
        if (pair == null) {
            return Optional.empty();
        }
        String symbol = normalize(pair.getBaseCode());
        String currency = normalize(pair.getCounterCode());
        return findAll().stream()
                .filter(contract -> normalize(contract.symbol()).equals(symbol))
                .filter(contract -> currency.isBlank() || normalize(contract.currency()).equals(currency))
                .findFirst();
    }

    public synchronized Optional<IbkrResolvedContract> findByDisplaySymbol(String displaySymbol) {
        String normalized = normalizeDisplay(displaySymbol);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(contract -> normalizeDisplay(contract.userFriendlySymbol()).equals(normalized)
                        || normalizeDisplay(contract.symbol()).equals(normalized)
                        || normalizeDisplay(contract.localSymbol()).equals(normalized))
                .findFirst();
    }

    private void writeAll(List<IbkrResolvedContract> contracts) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), contracts);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist IBKR contract cache to " + path, exception);
        }
    }

    private void ensureParent(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create IBKR contract cache directory.", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeDisplay(String value) {
        return normalize(value).replace("_", "/").replace(".", "/").replace("-", "/");
    }
}
