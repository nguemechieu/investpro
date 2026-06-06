package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.InstrumentId;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SymbolBlocklistService {

    private final Set<String> blockedSymbols = ConcurrentHashMap.newKeySet();

    public SymbolBlocklistService() {
    }

    public SymbolBlocklistService(Set<String> initialSymbols) {
        if (initialSymbols != null) {
            initialSymbols.forEach(this::block);
        }
    }

    public void block(InstrumentId instrumentId) {
        if (instrumentId != null) {
            block(instrumentId.symbol());
        }
    }

    public void block(String symbol) {
        String normalized = normalize(symbol);
        if (!normalized.isBlank()) {
            blockedSymbols.add(normalized);
        }
    }

    public void unblock(InstrumentId instrumentId) {
        if (instrumentId != null) {
            unblock(instrumentId.symbol());
        }
    }

    public void unblock(String symbol) {
        blockedSymbols.remove(normalize(symbol));
    }

    public boolean isBlocked(InstrumentId instrumentId) {
        return instrumentId != null && isBlocked(instrumentId.symbol());
    }

    public boolean isBlocked(String symbol) {
        return blockedSymbols.contains(normalize(symbol));
    }

    public Set<String> snapshot() {
        return Set.copyOf(blockedSymbols);
    }

    public SymbolEligibility markBlocked(SymbolEligibility eligibility) {
        if (eligibility == null) {
            return null;
        }
        return new SymbolEligibility(
                eligibility.instrument(),
                false,
                TradabilityFailureReason.SYMBOL_BLOCKLISTED,
                AutoTradeSymbolState.DISABLED_BY_USER,
                eligibility.assignedStrategy(),
                eligibility.strategyScore(),
                eligibility.latestSignal(),
                eligibility.spreadPercent(),
                eligibility.liquidityScore(),
                eligibility.volume24h(),
                eligibility.marketDataStatus(),
                eligibility.openOrders(),
                eligibility.openPositions(),
                Instant.now(),
                eligibility.metadata());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '/').replace('_', '/');
    }
}
