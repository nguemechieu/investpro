package org.investpro.exchange;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.stellar.StellarNetwork;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;
import org.stellar.sdk.requests.OrderBookRequestBuilder;
import org.stellar.sdk.responses.OrderBookResponse;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StellarNetworkTest {

    @Test
    void usesMainnetMarketDataInLocalPaperTradingMode() throws Exception {
        StellarNetwork stellar = new StellarNetwork(new ExchangeCredentials(
                "stellar",
                "",
                "",
                "",
                "",
                "",
                "paper-account",
                true));

        Method horizonUrlMethod = StellarNetwork.class.getDeclaredMethod("horizonUrl");
        horizonUrlMethod.setAccessible(true);
        String horizonUrl = (String) horizonUrlMethod.invoke(stellar);

        Method networkMethod = StellarNetwork.class.getDeclaredMethod("stellarNetwork");
        networkMethod.setAccessible(true);
        Network network = (Network) networkMethod.invoke(stellar);

        assertThat(stellar.isPaperTrading()).isTrue();
        assertThat(horizonUrl).isEqualTo("https://horizon.stellar.org");
        assertThat(network).isEqualTo(Network.PUBLIC);
    }

    @Test
    void tracksAndCancelsPaperLimitOrders() throws Exception {
        StellarNetwork stellar = new StellarNetwork(new ExchangeCredentials(
                "stellar",
                "",
                "",
                "",
                "",
                "",
                "paper-account",
                true));
        TradePair pair = new TradePair("XLM", "USDC");

        // Local execution still consumes real market quotes; isolate Horizon in this unit test.
        Server server = mock(Server.class);
        stellar.setMarketDataServer(server);
        stellar.setMarketDataServerPaperMode(true);
        OrderBookRequestBuilder request = mock(OrderBookRequestBuilder.class, RETURNS_SELF);
        when(server.orderBook()).thenReturn(request);
        OrderBookResponse response = mock(OrderBookResponse.class);
        OrderBookResponse.Row bid = mock(OrderBookResponse.Row.class);
        OrderBookResponse.Row ask = mock(OrderBookResponse.Row.class);
        when(bid.getPrice()).thenReturn("0.49");
        when(bid.getAmount()).thenReturn("100");
        when(ask.getPrice()).thenReturn("0.51");
        when(ask.getAmount()).thenReturn("100");
        when(response.getBids()).thenReturn(List.of(bid));
        when(response.getAsks()).thenReturn(List.of(ask));
        when(request.execute()).thenReturn(response);

        String orderId = stellar.createLimitOrder(pair, Side.BUY, 25.5, 0.12).get();

        List<OpenOrder> openOrders = stellar.fetchOpenOrders(pair).get();
        assertThat(openOrders).hasSize(1);
        assertThat(openOrders.getFirst().getOrderId()).isEqualTo(orderId);
        assertThat(stellar.fetchOrder(orderId).get()).isPresent();

        stellar.cancelOrder(orderId).get();

        assertThat(stellar.fetchOpenOrders(pair).get()).isEmpty();
        assertThat(stellar.fetchOrder(orderId).get()).isPresent();
        assertThat(stellar.fetchOrder(orderId).get().orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }
}
