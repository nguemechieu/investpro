package org.investpro.strategy.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingSignalTest {
    @Test
    void factoriesClampConfidenceAndWeight() {
        TradingSignal signal = TradingSignal.buy("test", "Test", 2.0, -3.0, "reason");

        assertThat(signal.action()).isEqualTo(SignalAction.BUY);
        assertThat(signal.confidence()).isEqualTo(1.0);
        assertThat(signal.weight()).isZero();
        assertThat(signal.timestamp()).isNotNull();
        assertThat(signal.diagnostics()).isEmpty();
        assertThat(signal.tags()).isEmpty();
    }

    @Test
    void holdFactoryReturnsSafeHold() {
        TradingSignal signal = TradingSignal.hold("test", "Test", "No setup");

        assertThat(signal.action()).isEqualTo(SignalAction.HOLD);
        assertThat(signal.confidence()).isZero();
        assertThat(signal.weight()).isZero();
    }
}
