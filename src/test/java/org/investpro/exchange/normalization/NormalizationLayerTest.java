package org.investpro.exchange.normalization;

import org.junit.jupiter.api.Test;

import java.time.Instant;

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
    void fromRawPricesProducesValidSnapshot() {
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.fromRawPrices(
                "Coinbase",
                "BTC-USD",
                40000.00,   // bid
                40001.00,   // ask
                40000.50,   // last
                12345.67,   // volume24h
                Instant.now()
        );

        assertThat(snap.getBidPrice()).isEqualTo(40000.00);
        assertThat(snap.getAskPrice()).isEqualTo(40001.00);
        assertThat(snap.getSpreadBps()).isGreaterThan(0);
        assertThat(snap.isDataFresh()).isTrue();
        assertThat(snap.getExchangeName()).isEqualTo("Coinbase");
    }

    @Test
    void spreadBpsCalculation() {
        // spread = (ask - bid) / mid * 10_000
        // bid=100, ask=101, mid=100.5, spread = 1/100.5 * 10000 ≈ 99.5
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.fromRawPrices(
                "TestExchange", "ETH-USD",
                100.0, 101.0, 100.5, 10.0,
                Instant.now()
        );
        assertThat(snap.getSpreadBps()).isGreaterThan(90).isLessThan(110);
    }

    @Test
    void midPriceIsAverageOfBidAsk() {
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.fromRawPrices(
                "OANDA", "EUR/USD",
                1.0800, 1.0802, 1.0801, 500000.0,
                Instant.now()
        );
        // midPrice should be (1.0800 + 1.0802) / 2 = 1.0801
        assertThat(snap.getMidPrice()).isEqualTo(1.0801);
    }

    @Test
    void staleSnapshotHasCorrectSource() {
        NormalizedMarketSnapshot snap = NormalizedMarketSnapshot.stale("OANDA", "GBP/USD");
        assertThat(snap.getDataSource()).isEqualTo("STALE_CACHE");
        assertThat(snap.symbol()).isEqualTo("GBP/USD");
    }
}
