package org.investpro.exchange.normalization;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NormalizedMarketSnapshot} and normalization helpers.
 */
class NormalizationLayerTest {

    @Test
    void staleSnapshotIsNotFresh() {
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.stale("Coinbase", "BTC-USD");
        assertThat(snap.isDataFresh()).isFalse();
    }

    @Test
    void fromTickerProducesValidSnapshot() {
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.fromTicker(
                "Coinbase",
                "BTC-USD",
                new BigDecimal("40000.00"),   // bid
                new BigDecimal("40001.00"),   // ask
                new BigDecimal("40000.50"),   // last
                new BigDecimal("12345.67"),   // volume24h
                Instant.now()
        );

        assertThat(snap.bidPrice()).isEqualByComparingTo("40000.00");
        assertThat(snap.askPrice()).isEqualByComparingTo("40001.00");
        assertThat(snap.spreadBps()).isGreaterThan(0);
        assertThat(snap.isDataFresh()).isTrue();
        assertThat(snap.dataSource()).isEqualTo("Coinbase");
    }

    @Test
    void spreadBpsCalculation() {
        // spread = (ask - bid) / mid * 10_000
        // bid=100, ask=101, mid=100.5, spread = 1/100.5 * 10000 ≈ 99.5
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.fromTicker(
                "TestExchange", "ETH-USD",
                new BigDecimal("100"),
                new BigDecimal("101"),
                new BigDecimal("100.5"),
                BigDecimal.TEN,
                Instant.now()
        );
        assertThat(snap.spreadBps()).isGreaterThan(90).isLessThan(110);
    }

    @Test
    void midPriceIsAverageOfBidAsk() {
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.fromTicker(
                "OANDA", "EUR/USD",
                new BigDecimal("1.0800"),
                new BigDecimal("1.0802"),
                new BigDecimal("1.0801"),
                new BigDecimal("500000"),
                Instant.now()
        );
        // midPrice should be (1.0800 + 1.0802) / 2 = 1.0801
        assertThat(snap.midPrice()).isEqualByComparingTo("1.08010");
    }

    @Test
    void staleSnapshotHasCorrectSource() {
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.stale("OANDA", "GBP/USD");
        assertThat(snap.dataSource()).isEqualTo("OANDA");
        assertThat(snap.symbol()).isEqualTo("GBP/USD");
    }
}
