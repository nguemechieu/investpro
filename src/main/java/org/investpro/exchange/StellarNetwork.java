package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.Account;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.models.trading.*;
import org.investpro.service.AuthResult;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import javafx.beans.property.SimpleIntegerProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Getter
@Setter
@Slf4j
@SuppressWarnings("SpellCheckingInspection")
public class StellarNetwork extends Exchange {
    private static final String STELLAR_API_URL = "https://horizon.stellar.org";
    private static final String STELLAR_TEST_URL = "https://horizon-testnet.stellar.org";
    private static final String MAINNET_USDC_ISSUER = "GA5ZSEJYB37DFWGY4OZE3NV5QOWI6Q5Y4M7JN2K2ZZMZB6PZNTSMZQ5P";
    private static final String TESTNET_USDC_ISSUER = "GBBD47IFM4TEQ4EMSN77QWJVRLN4W4RGTM3ZQWUCNC7L7DVQHHITGZJW";
    // EURC issuer (Circle) — same on mainnet and testnet approximation
    private static final String MAINNET_EURC_ISSUER = "GAP5LETOV6YIE62YAM56STDANPRDO7ZFDBGSNHJQIYGGKSMOZAHOOS2S";
    private static final String TESTNET_EURC_ISSUER  = "GAP5LETOV6YIE62YAM56STDANPRDO7ZFDBGSNHJQIYGGKSMOZAHOOS2S";
    private static final double DEFAULT_XLM_USDC_PRICE = 0.50;

    // Paper trading state
    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final Map<String, OpenOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, Order> orderHistory = new ConcurrentHashMap<>();
    private final List<Position> positions = new CopyOnWriteArrayList<>();
    private final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();
    private long nextOrderId = 1000;

    private String apiKey;
    private String apiSecret;
    private String accountId;
    private final HttpClient httpClient;
    private boolean websocketAvailable;
    private ExchangeWebSocketClient websocketClient;

    public StellarNetwork(@NotNull ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
        this.apiKey = exchangeCredentials.apiKey();
        this.apiSecret = exchangeCredentials.apiSecret();
        this.accountId = exchangeCredentials.accountId();
        this.httpClient = HttpClient.newHttpClient();
        this.websocketAvailable = false;
        initializePaperTradingAccount();
    }

    private void initializePaperTradingAccount() {
        // Initialize with $10,000 USDC for paper trading
        balances.put("USDC", 10000.0);
        balances.put("XLM", 0.0);
        balances.put("USD", 10000.0);
        log.info("Stellar Network paper trading account initialized with $10,000 USDC");
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
            double takeProfit, double slippage) {
        createMarketOrder(tradePair, Side.BUY, size);
    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
            double takeProfit, double slippage) {
        createMarketOrder(tradePair, Side.SELL, size);
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        return AuthResult.success("Stellar Network authenticated");
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return CompletableFuture.supplyAsync(() -> {
            Account account = new Account();
            account.setAccountId(accountId);
            account.setBalances(new LinkedHashMap<>(balances));
            account.setBalance("USDC", balances.getOrDefault("USDC", 0.0));
            account.setTotalBalance(balances.getOrDefault("USDC", 0.0));
            account.setAvailableBalance(balances.getOrDefault("USDC", 0.0));
            return account;
        });
    }

