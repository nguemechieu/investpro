package org.investpro.investpro;

import org.investpro.investpro.components.ExchangeFactory;
import org.investpro.investpro.exchanges.BinanceUS;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.model.TradePair;

import java.util.List;

public class AppConfig {

    public static Exchange getBinanceExchange() {
        return new BinanceUS("your-api-key", "your-api-secret");
    }

    public static Exchange getCoinbaseExchange() {
        return new Coinbase("your-api-key", "your-api-secret");
    }

    public static ExchangeFactory getExchangeFactory() {
        return new ExchangeFactory(List.of(getBinanceExchange(), getCoinbaseExchange()));
    }

    public static void main(String[] args) {
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
