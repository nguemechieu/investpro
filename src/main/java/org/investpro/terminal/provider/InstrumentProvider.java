package org.investpro.terminal.provider;

import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface InstrumentProvider extends ProviderCapabilities {
    CompletableFuture<List<Instrument>> discoverInstruments();

    CompletableFuture<Optional<Instrument>> resolveInstrument(String symbol);

    default CompletableFuture<Optional<Instrument>> resolveInstrument(InstrumentId instrumentId) {
        return resolveInstrument(instrumentId == null ? "" : instrumentId.symbol());
    }
}
