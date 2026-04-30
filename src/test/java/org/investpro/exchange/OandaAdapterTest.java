package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.data.CandleData;
import org.investpro.models.trading.Order;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OandaAdapterTest {

    @Test
    void parsesCompletedOandaMidCandlesForChartDisplay() throws Exception {
        String json = """
                {
                  "instrument": "EUR_USD",
                  "granularity": "M1",
                  "candles": [
                    {
                      "complete": false,
                      "volume": 4,
                      "time": "2026-04-29T10:01:00.000000000Z",
                      "mid": {"o": "1.10000", "h": "1.10040", "l": "1.09990", "c": "1.10020"}
                    },
                    {
                      "complete": true,
                      "volume": 12,
                      "time": "2026-04-29T10:00:00.000000000Z",
                      "mid": {"o": "1.09910", "h": "1.10010", "l": "1.09870", "c": "1.09990"}
                    }
                  ]
                }
                """;

        JsonNode candles = Oanda.OBJECT_MAPPER.readTree(json).path("candles");

        List<CandleData> parsed = OandaCandleDataSupplier.parseCandles(candles);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().getOpenPrice()).isEqualTo(1.09910);
        assertThat(parsed.getFirst().getHighPrice()).isEqualTo(1.10010);
        assertThat(parsed.getFirst().getLowPrice()).isEqualTo(1.09870);
        assertThat(parsed.getFirst().getClosePrice()).isEqualTo(1.09990);
        assertThat(parsed.getFirst().getVolume()).isEqualTo(12.0);
    }

    @Test
    void createOrderPreservesSideForLiveOrderPayloadConstruction() throws Exception {
        Oanda oanda = new Oanda("token", "account");

        Order sellOrder = oanda.createOrder(0, null, "MARKET", 0, 1_000, Side.SELL, 1.0900, 1.0800, 0);

        assertThat(sellOrder.getSymbol()).isBlank();
        assertThat(sellOrder.getSide()).isEqualTo(Side.SELL);
        assertThat(sellOrder.getType()).isEqualTo("SELL");
        assertThat(sellOrder.getQuantity()).isEqualTo(1_000);
        assertThat(oanda.normalizeAmount(null, 0.2)).isEqualTo(1.0);
    }
}
