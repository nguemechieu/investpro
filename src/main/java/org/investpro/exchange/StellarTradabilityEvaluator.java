package org.investpro.exchange;

import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityScope;
import org.investpro.trading.tradability.TradabilityStatus;
import org.investpro.trading.tradability.session.ExchangeSessionService;
import org.investpro.trading.tradability.session.SessionState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class StellarTradabilityEvaluator {

    private final StellarNetwork exchange;

    public StellarTradabilityEvaluator(StellarNetwork exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
    }

    public CompletableFuture<SymbolTradability> evaluate(
            TradePair pair,
            TradabilityScope scope,
            boolean forceRefresh
    ) {
        TradabilityScope safeScope = scope == null ? TradabilityScope.LIVE_TRADING : scope;
        return exchange.evaluateStellarTradability(pair, safeScope, forceRefresh);
    }

    static SymbolTradability buildStatus(
            StellarNetwork exchange,
            StellarPairIdentity pair,
            TradabilityScope scope,
            PairQuality quality,
            boolean baseTrustlineExists,
            boolean quoteTrustlineExists,
            String reason
    ) {
        boolean backtestingAllowed = quality != null && quality.tradeable();
        boolean userAdded = pair.base().userAdded() || pair.quote().userAdded();
        boolean trustedIssuerPair = (pair.base().trusted() || pair.base().isNative())
                && (pair.quote().trusted() || pair.quote().isNative());
        boolean trustedOrUserApproved = (pair.base().trusted() || pair.base().userAdded() || pair.base().isNative())
                && (pair.quote().trusted() || pair.quote().userAdded() || pair.quote().isNative());

        boolean requiresBaseTrustline = !pair.base().isNative();
        boolean requiresQuoteTrustline = !pair.quote().isNative();
        boolean trustlinesOk = (!requiresBaseTrustline || baseTrustlineExists)
                && (!requiresQuoteTrustline || quoteTrustlineExists);

        boolean marketDataAllowed = backtestingAllowed || trustedIssuerPair;
        boolean watchlistAllowed = backtestingAllowed || trustedIssuerPair;
        boolean paperAllowed = backtestingAllowed && trustedOrUserApproved;
        boolean liveAllowed = backtestingAllowed && trustedOrUserApproved && trustlinesOk && !exchange.isPaperTrading();
        boolean botAllowed = liveAllowed && !userAdded && quality.spreadPercent() <= exchange.getStellarBotMaxSpreadPercent();
        boolean orderAllowed = scope == TradabilityScope.ORDER_SUBMISSION
                ? liveAllowed
                : backtestingAllowed && trustedOrUserApproved && trustlinesOk;

        if (scope == TradabilityScope.MARKET_DATA || scope == TradabilityScope.WATCHLIST) {
            orderAllowed = false;
        } else if (scope == TradabilityScope.PAPER_TRADING) {
            orderAllowed = paperAllowed;
        }

        TradabilityStatus status = backtestingAllowed
                ? (orderAllowed || paperAllowed || watchlistAllowed
                ? TradabilityStatus.FULLY_TRADABLE
                : TradabilityStatus.VIEW_ONLY)
                : TradabilityStatus.LIQUIDITY_UNAVAILABLE;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "stellar-issuer-aware");
        metadata.put("stellar.baseIssuer", pair.base().issuer());
        metadata.put("stellar.quoteIssuer", pair.quote().issuer());
        metadata.put("stellar.baseCanonicalKey", pair.base().canonicalKey());
        metadata.put("stellar.quoteCanonicalKey", pair.quote().canonicalKey());
        metadata.put("stellar.displayPair", pair.displaySymbol());
        metadata.put("stellar.executionPair", quality != null && quality.inverted()
                ? pair.inverted().displaySymbol()
                : pair.displaySymbol());
        metadata.put("stellar.usingInvertedOrderBook", String.valueOf(quality != null && quality.inverted()));
        metadata.put("stellar.spreadPercent", String.valueOf(quality == null ? 0.0 : quality.spreadPercent()));
        metadata.put("stellar.liquidityReason", reason);
        metadata.put("stellar.userAddedAsset", String.valueOf(userAdded));
        metadata.put("stellar.trustedIssuerPair", String.valueOf(trustedIssuerPair));
        metadata.put("stellar.baseTrustlineExists", String.valueOf(baseTrustlineExists));
        metadata.put("stellar.quoteTrustlineExists", String.valueOf(quoteTrustlineExists));

        SessionState sessionState = new ExchangeSessionService()
                .sessionState(exchange, pair.toTradePair(), backtestingAllowed ? TradabilityStatus.FULLY_TRADABLE : TradabilityStatus.LIQUIDITY_UNAVAILABLE, metadata);
        if (scope == TradabilityScope.ORDER_SUBMISSION) {
            orderAllowed = orderAllowed && sessionState.orderSubmissionOpen();
        }
        metadata.put("accountPermission", exchange.canSubmitOrders());
        metadata.put("connected", Boolean.TRUE.equals(exchange.isConnected()));
        metadata.put("requiresActiveSession", sessionState.requiresActiveSession());
        metadata.put("session.marketDataAvailable", sessionState.marketDataAvailable());
        metadata.put("session.orderSubmissionOpen", sessionState.orderSubmissionOpen());
        metadata.put("session.cancelAllowed", sessionState.cancelAllowed());
        metadata.put("session.reduceOnly", sessionState.reduceOnly());
        metadata.put("session.openNewPositionsAllowed", sessionState.openNewPositionsAllowed());
        metadata.put("session.reason", sessionState.reason());

        StellarTradabilityDetails details = new StellarTradabilityDetails(
                pair.base().code(),
                pair.base().issuer(),
                pair.quote().code(),
                pair.quote().issuer(),
                pair.base().isNative(),
                pair.quote().isNative(),
                true,
                true,
                requiresBaseTrustline,
                requiresQuoteTrustline,
                baseTrustlineExists,
                quoteTrustlineExists,
                quality != null && !quality.inverted(),
                quality != null && quality.inverted(),
                quality != null && quality.inverted(),
                quality == null ? 0.0 : quality.bestBid(),
                quality == null ? 0.0 : quality.bestAsk(),
                quality == null ? 0.0 : quality.spreadPercent(),
                quality == null ? 0.0 : quality.bidDepth(),
                quality == null ? 0.0 : quality.askDepth(),
                reason);
        metadata.put("stellar.details", details);

        return new SymbolTradability(
                exchange.getExchangeId(),
                pair.toTradePair(),
                pair.displaySymbol(),
                status,
                marketDataAllowed,
                watchlistAllowed,
                backtestingAllowed,
                paperAllowed,
                liveAllowed,
                botAllowed,
                orderAllowed,
                false,
                true,
                false,
                false,
                false,
                false,
                orderAllowed && scope == TradabilityScope.ORDER_SUBMISSION ? sessionState.reason() : reason,
                Instant.now(),
                metadata);
    }
}
