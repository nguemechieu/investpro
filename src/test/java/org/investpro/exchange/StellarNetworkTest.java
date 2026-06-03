package org.investpro.exchange;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import org.stellar.sdk.Network;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StellarNetworkTest {

    @Test
    void usesTestnetInPaperTradingMode() throws Exception {
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
        assertThat(horizonUrl).isEqualTo("https://horizon-testnet.stellar.org");
        assertThat(network).isEqualTo(Network.TESTNET);
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
