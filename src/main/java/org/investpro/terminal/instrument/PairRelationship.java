package org.investpro.terminal.instrument;

import org.investpro.terminal.domain.InstrumentId;

public record PairRelationship(
        InstrumentId displayInstrument,
        InstrumentId executionInstrument,
        boolean reversed,
        String reason
) {
    public PairRelationship {
        if (displayInstrument == null || executionInstrument == null) {
            throw new IllegalArgumentException("display and execution instruments are required");
        }
        reason = reason == null ? "" : reason.trim();
    }
}
