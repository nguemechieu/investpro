package org.investpro.exchange;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SolonaNetworkTest {

    @Test
    void providesSyntheticCandleDataForCharting() throws Exception {
        SolonaNetwork solona = new SolonaNetwork(new ExchangeCredentials(
                "solona-network",
                "",
                "",
                "",
                "",
                "",
                "",
                true));

        CandleDataSupplier supplier = solona.getCandleDataSupplier(3600, new TradePair("SOL", "USDC"));

        assertThat(supplier).isNotNull();
        assertThat(supplier.getCandleData()).isNotEmpty();
        assertThat(supplier.getCandleData()).allSatisfy(candle -> {
            assertThat(candle.openPrice()).isPositive();
            assertThat(candle.closePrice()).isPositive();
            assertThat(candle.highPrice()).isPositive();
            assertThat(candle.lowPrice()).isPositive();
        });
        List<Integer> openTimes = supplier.getCandleData().stream()
                .map(CandleData::openTime)
                .toList();
        assertThat(openTimes).isSorted();
    }
}
