package org.investpro.terminal.persistence;

import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository {
    Instrument save(Instrument instrument);
    Optional<Instrument> findById(InstrumentId id);
    Optional<Instrument> findBySymbol(String providerId, String symbol);
    List<Instrument> findAll();
}
