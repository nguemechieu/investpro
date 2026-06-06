package org.investpro.terminal.instrument;

import org.investpro.terminal.domain.Asset;
import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.TradingStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InstrumentMasterService {

    private final Map<String, Instrument> instrumentsByKey = new ConcurrentHashMap<>();
    private final Map<String, String> displaySymbolToKey = new ConcurrentHashMap<>();
    private final Map<String, PairRelationship> reversibleRelationships = new ConcurrentHashMap<>();

    public Instrument register(Instrument instrument) {
        if (instrument == null) {
            throw new IllegalArgumentException("instrument is required");
        }

        String key = instrument.id().key();
        instrumentsByKey.put(key, instrument);
        displaySymbolToKey.put(normalize(instrument.id().symbol()), key);
        displaySymbolToKey.put(normalize(instrument.id().nativeSymbol()), key);
        if (instrument.baseAsset() != null && instrument.quoteAsset() != null) {
            displaySymbolToKey.put(normalize(instrument.baseAsset().code() + "/" + instrument.quoteAsset().code()), key);
        }
        return instrument;
    }

    public void registerAll(List<Instrument> instruments) {
        if (instruments == null) {
            return;
        }
        instruments.forEach(this::register);
    }

    public Optional<Instrument> resolve(String symbol) {
        String key = displaySymbolToKey.get(normalize(symbol));
        return key == null ? Optional.empty() : Optional.ofNullable(instrumentsByKey.get(key));
    }

    public Optional<Instrument> resolve(InstrumentId id) {
        return id == null ? Optional.empty() : Optional.ofNullable(instrumentsByKey.get(id.key()));
    }

    public boolean isTradable(InstrumentId id) {
        return resolve(id).map(Instrument::tradableNow).orElse(false);
    }

    public void markTradingStatus(InstrumentId id, TradingStatus status) {
        resolve(id).ifPresent(existing -> register(new Instrument(
                existing.id(),
                existing.baseAsset(),
                existing.quoteAsset(),
                existing.displayName(),
                existing.assetClass(),
                existing.venue(),
                existing.tickSize(),
                existing.lotSize(),
                existing.minOrderSize(),
                existing.quoteIncrement(),
                existing.baseIncrement(),
                status,
                existing.marginable(),
                existing.shortable(),
                existing.active(),
                existing.reversible(),
                existing.metadata())));
    }

    public Optional<PairRelationship> resolveEquivalentOrReversed(String displaySymbol) {
        Optional<Instrument> direct = resolve(displaySymbol);
        if (direct.isPresent()) {
            Instrument instrument = direct.get();
            return Optional.of(new PairRelationship(instrument.id(), instrument.id(), false, "DIRECT"));
        }

        String normalized = normalize(displaySymbol);
        PairRelationship registered = reversibleRelationships.get(normalized);
        if (registered != null) {
            return Optional.of(registered);
        }

        String[] parts = normalized.split("/", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        String reversedSymbol = parts[1] + "/" + parts[0];
        Optional<Instrument> reversed = resolve(reversedSymbol);
        if (reversed.isEmpty() || !reversed.get().reversible()) {
            return Optional.empty();
        }

        Instrument reversedInstrument = reversed.get();
        InstrumentId displayId = new InstrumentId(
                reversedInstrument.id().providerId(),
                normalized,
                normalized);
        PairRelationship relationship = new PairRelationship(displayId, reversedInstrument.id(), true, "REVERSED_PAIR");
        reversibleRelationships.put(normalized, relationship);
        return Optional.of(relationship);
    }

    public void registerReversibleRelationship(PairRelationship relationship) {
        if (relationship == null) {
            return;
        }
        reversibleRelationships.put(normalize(relationship.displayInstrument().symbol()), relationship);
    }

    public List<Instrument> allInstruments() {
        return instrumentsByKey.values().stream()
                .sorted(Comparator.comparing(instrument -> instrument.id().key()))
                .toList();
    }

    public List<Instrument> findByBaseOrQuoteAsset(Asset asset) {
        if (asset == null) {
            return List.of();
        }
        List<Instrument> matches = new ArrayList<>();
        for (Instrument instrument : instrumentsByKey.values()) {
            if (sameAsset(asset, instrument.baseAsset()) || sameAsset(asset, instrument.quoteAsset())) {
                matches.add(instrument);
            }
        }
        return matches.stream()
                .sorted(Comparator.comparing(instrument -> instrument.id().key()))
                .toList();
    }

    public Map<String, Instrument> snapshot() {
        return new LinkedHashMap<>(instrumentsByKey);
    }

    private static boolean sameAsset(Asset left, Asset right) {
        return left != null && right != null && left.canonicalKey().equals(right.canonicalKey());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('_', '/').replace('-', '/');
    }
}
