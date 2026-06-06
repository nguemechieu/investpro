package org.investpro.ui.tradingdesk;

import org.investpro.core.SystemCore;
import org.investpro.exchange.Exchange;
import org.investpro.market.MarketDataEngine;
import org.investpro.service.NotificationService;
import org.investpro.service.OrderService;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;
import org.investpro.strategy.StrategyEngine;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.investpro.ui.tradingdesk.services.TradingDeskFundingService;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class TradingDeskContext {

    private final Supplier<SystemCore> systemCoreSupplier;
    private final Supplier<Exchange> exchangeSupplier;
    private final Supplier<MarketDataEngine> marketDataEngineSupplier;
    private final Supplier<UniversalTradabilityService> tradabilityServiceSupplier;
    private final TradingService tradingService;
    private final TradeService tradeService;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final TradingDeskFundingService fundingService;

    public TradingDeskContext(
            Supplier<SystemCore> systemCoreSupplier,
            Supplier<Exchange> exchangeSupplier,
            Supplier<MarketDataEngine> marketDataEngineSupplier,
            Supplier<UniversalTradabilityService> tradabilityServiceSupplier,
            TradingService tradingService,
            TradeService tradeService,
            OrderService orderService,
            NotificationService notificationService,
            TradingDeskFundingService fundingService) {
        this.systemCoreSupplier = Objects.requireNonNull(systemCoreSupplier, "systemCoreSupplier must not be null");
        this.exchangeSupplier = Objects.requireNonNull(exchangeSupplier, "exchangeSupplier must not be null");
        this.marketDataEngineSupplier = Objects.requireNonNull(
                marketDataEngineSupplier,
                "marketDataEngineSupplier must not be null");
        this.tradabilityServiceSupplier = Objects.requireNonNull(
                tradabilityServiceSupplier,
                "tradabilityServiceSupplier must not be null");
        this.tradingService = tradingService;
        this.tradeService = tradeService;
        this.orderService = orderService;
        this.notificationService = notificationService;
        this.fundingService = Objects.requireNonNull(fundingService, "fundingService must not be null");
    }

    public SystemCore systemCore() {
        return systemCoreSupplier.get();
    }

    public Exchange exchange() {
        return exchangeSupplier.get();
    }

    public MarketDataEngine marketDataEngine() {
        return marketDataEngineSupplier.get();
    }

    public UniversalTradabilityService tradabilityService() {
        return tradabilityServiceSupplier.get();
    }

    public Optional<StrategyEngine> strategyEngine() {
        SystemCore core = systemCore();
        return core == null ? Optional.empty() : Optional.ofNullable(core.getStrategyEngine());
    }

    public TradingService tradingService() {
        return tradingService;
    }

    public TradeService tradeService() {
        return tradeService;
    }

    public OrderService orderService() {
        return orderService;
    }

    public NotificationService notificationService() {
        return notificationService;
    }

    public TradingDeskFundingService fundingService() {
        return fundingService;
    }
}
