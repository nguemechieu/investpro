package org.investpro.investpro;

import org.investpro.investpro.components.ExchangeFactory;
import org.investpro.investpro.exchanges.BinanceUS;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.models.TradePair;

import java.util.List;

public class AppConfig {

    public static Exchange getBinanceExchange() {
        return new BinanceUS("your-api-key", "your-api-secret");
    }

    public static Exchange getCoinbaseExchange() {
        return new Coinbase(
                "organizations/your-org-id/apiKeys/your-key-id",
                "-----BEGIN EC PRIVATE KEY-----\n...\n-----END EC PRIVATE KEY-----\n",
                ""
        );
    }

    public static ExchangeFactory getExchangeFactory() {
        return new ExchangeFactory(List.of(getBinanceExchange(), getCoinbaseExchange()));
    }

    static void main() {
        ExchangeFactory factory = AppConfig.getExchangeFactory();
        Exchange exchange = factory.getExchange("Coinbase");

        try {
            List<TradePair> pairs = exchange.getTradePairs();


            System.out.println("Available pairs: " + pairs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
