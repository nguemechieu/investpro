package org.investpro.activity;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class BrokerActivityEvent {
    @NonNull String eventId;
    @NonNull String exchangeId;
    String accountId;
    String nativeEventType;
    @Builder.Default BrokerActivityType activityType = BrokerActivityType.UNKNOWN;
    String orderId;
    String tradeId;
    String positionId;
    TradePair tradePair;
    Side side;
    @Builder.Default BigDecimal requestedQuantity = BigDecimal.ZERO;
    @Builder.Default BigDecimal filledQuantity = BigDecimal.ZERO;
    @Builder.Default BigDecimal remainingQuantity = BigDecimal.ZERO;
    BigDecimal price;
    BigDecimal averageFillPrice;
    @Builder.Default BigDecimal realizedPnl = BigDecimal.ZERO;
    @Builder.Default BigDecimal unrealizedPnl = BigDecimal.ZERO;
    @Builder.Default BigDecimal fee = BigDecimal.ZERO;
    String feeCurrency;
    @Builder.Default BigDecimal financing = BigDecimal.ZERO;
    @Builder.Default BigDecimal fundingFee = BigDecimal.ZERO;
    @Builder.Default BigDecimal commission = BigDecimal.ZERO;
    BigDecimal balanceBefore;
    BigDecimal balanceAfter;
    String balanceCurrency;
    BigDecimal marginUsed;
    BigDecimal marginAvailable;
    @Builder.Default Instant eventTime = Instant.now();
    @Builder.Default Instant receivedAt = Instant.now();
    String cursor;
    String source;
    @Singular("metadataEntry") Map<String, Object> metadata;
    String rawJson;
    boolean terminalEvent;
    boolean errorEvent;
    boolean projected;
    String projectionError;
    String reason;

    public Map<String, Object> getMetadata() {
        return metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
