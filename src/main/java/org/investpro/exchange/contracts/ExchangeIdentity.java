package org.investpro.exchange.contracts;

import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.utils.MARKET_TYPES;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

public interface ExchangeIdentity {

    String getName();

    String getSignal();

    String getExchangeId();

    String getDisplayName();

    boolean isSandbox();

    boolean isPaperTrading();

    String getTimestamp();

    Instant now();

    boolean supportsMarketType(MARKET_TYPES marketType);

    List<MARKET_TYPES> getSupportedMarketTypes();

    @NotNull ExchangeCapability getCapability();

    AuthCheckResult checkAuthentication();
}